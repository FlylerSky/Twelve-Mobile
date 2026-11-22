package com.example

import com.example.core.network.AppError
import com.example.core.network.AuthTokenInterceptor
import com.example.core.network.BackendErrorParser
import com.example.core.network.FirebaseTokenProvider
import com.example.core.network.GetRetryInterceptor
import com.example.core.util.CreatedAtParser
import com.example.core.util.EmailCompat
import com.example.core.util.TagNameNormalizer
import com.example.data.model.AccountStatus
import com.example.data.model.GetMeResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M0AuthAndNetworkTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `TagNameNormalizer handles bare and prefixed tag names`() {
        assertEquals("alice_12", TagNameNormalizer.toBareTag("@Alice_12"))
        assertEquals("alice_12", TagNameNormalizer.toBareTag("  alice_12 "))
        assertEquals("@alice_12", TagNameNormalizer.toDisplayTag("alice_12"))
        assertEquals("@alice_12", TagNameNormalizer.toDisplayTag("@ALICE_12"))
        assertNull(TagNameNormalizer.toDisplayTag("   "))
        assertTrue(TagNameNormalizer.isValid("alice_12"))
        assertTrue(TagNameNormalizer.isValid("@alice_12"))
        assertFalse(TagNameNormalizer.isValid("ab"))
        assertFalse(TagNameNormalizer.isValid("bad-tag"))
    }

    @Test
    fun `EmailCompat matches frontend legacy normalization rules`() {
        assertEquals("johndoe@gmail.com", EmailCompat.normalizeLegacyEmail("John.Doe+promo@googlemail.com"))
        assertEquals("jane@outlook.com", EmailCompat.normalizeLegacyEmail("Jane+test@outlook.com"))
        assertEquals(listOf("j.doe@gmail.com", "jdoe@gmail.com"), EmailCompat.loginCandidates("j.doe@gmail.com"))
        assertEquals(listOf("user@company.vn"), EmailCompat.loginCandidates("user@company.vn"))
    }

    @Test
    fun `GetMeResponse parses real backend shape with bare tagName and Firestore timestamp`() {
        // Fixture đối chiếu backend/src/controllers/users.controller.js#getMe
        val fixture = """
            {
              "user": {
                "uid": "uid_abc123",
                "displayName": "Minh Anh",
                "tagName": "minh_anh",
                "email": "minh@example.com",
                "status": "pending",
                "accountGroup": "RA",
                "accountRole": "user",
                "isFirstLogin": true,
                "isLegacyMember": true,
                "accountScore": 42,
                "badges": ["legacy"],
                "createdAt": { "_seconds": 1700000000, "_nanoseconds": 0 }
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString(GetMeResponse.serializer(), fixture)
        assertEquals("uid_abc123", parsed.user.uid)
        assertEquals(AccountStatus.PENDING, parsed.user.accountStatus)
        assertEquals("@minh_anh", TagNameNormalizer.toDisplayTag(parsed.user.tagName))
        assertEquals(listOf("legacy"), parsed.user.badges)
        assertEquals(Instant.ofEpochSecond(1700000000), CreatedAtParser.parseIsoToInstant(parsed.user.createdAt))
    }

    @Test
    fun `BackendErrorParser distinguishes suspended deleting and not active codes`() {
        val suspended = BackendErrorParser.fromHttp(
            statusCode = 403,
            rawBody = """{"error":"Tài khoản đã bị tước quyền truy cập.","code":"ACCOUNT_SUSPENDED"}""",
        )
        assertTrue(suspended is AppError.AccountSuspended)
        assertTrue(suspended.isAccountBlocked)
        assertEquals("Tài khoản đã bị tước quyền truy cập.", suspended.userMessage)

        val deleting = BackendErrorParser.fromHttp(
            statusCode = 403,
            rawBody = """{"error":"Tài khoản đang được xoá.","code":"ACCOUNT_DELETING"}""",
        )
        assertTrue(deleting is AppError.AccountDeleting)
        assertTrue(deleting.isAccountBlocked)

        val pendingNotActive = BackendErrorParser.fromHttp(
            statusCode = 403,
            rawBody = """{"error":"Tài khoản chưa được kích hoạt.","code":"ACCOUNT_NOT_ACTIVE","status":"pending"}""",
        )
        assertTrue(pendingNotActive is AppError.AccountNotActive)
        assertFalse(pendingNotActive.isAccountBlocked)
        assertEquals("pending", (pendingNotActive as AppError.AccountNotActive).accountStatus)

        val reauth = BackendErrorParser.fromHttp(
            statusCode = 403,
            rawBody = """{"error":"Cần nhập lại mật khẩu để thực hiện thao tác này.","code":"REAUTH_REQUIRED"}""",
        )
        assertTrue(reauth is AppError.ReauthRequired)

        val rateLimited = BackendErrorParser.fromHttp(
            statusCode = 429,
            rawBody = """{"error":"Quá nhiều yêu cầu."}""",
            headers = Headers.headersOf("Retry-After", "45"),
        )
        assertTrue(rateLimited is AppError.RateLimited)
        assertEquals(45L, (rateLimited as AppError.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `AuthTokenInterceptor refreshes once on 401 and signs out when retry still fails`() {
        val server = MockWebServer()
        server.start()
        try {
            var signOutCalled = 0
            var currentToken = "old-token"
            val provider = object : FirebaseTokenProvider {
                override fun getIdTokenSync(forceRefresh: Boolean): String {
                    if (forceRefresh) currentToken = "fresh-token"
                    return currentToken
                }

                override fun signOutOnUnauthorized() {
                    signOutCalled += 1
                }
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(AuthTokenInterceptor(provider))
                .build()

            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

            val req = Request.Builder().url(server.url("/api/users/me")).build()
            client.newCall(req).execute().use { res ->
                assertEquals(200, res.code)
            }
            assertEquals("Bearer old-token", server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer fresh-token", server.takeRequest().getHeader("Authorization"))
            assertEquals(0, signOutCalled)

            // Nếu retry vẫn 401 thì gọi đăng xuất ngay
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(401))
            client.newCall(req).execute().use { res ->
                assertEquals(401, res.code)
            }
            assertEquals(1, signOutCalled)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `GetRetryInterceptor retries GET on 5xx but never retries 429 or POST`() {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(GetRetryInterceptor(maxRetries = 2, initialDelayMs = 1L, sleeper = {}))
                .build()

            // GET gặp 503 hai lần rồi thành công ở lần thứ 3
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(502))
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

            val getReq = Request.Builder().url(server.url("/api/posts")).get().build()
            client.newCall(getReq).execute().use { assertEquals(200, it.code) }
            assertEquals(3, server.requestCount)

            // GET gặp 429 không được thử lại
            server.enqueue(MockResponse().setResponseCode(429))
            client.newCall(getReq).execute().use { assertEquals(429, it.code) }
            assertEquals(4, server.requestCount)

            // POST gặp 500 không được thử lại
            server.enqueue(MockResponse().setResponseCode(500))
            val postReq = Request.Builder()
                .url(server.url("/api/posts"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(postReq).execute().use { assertEquals(500, it.code) }
            assertEquals(5, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
