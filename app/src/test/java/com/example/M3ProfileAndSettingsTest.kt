package com.example

import com.example.core.config.FirebaseAvailability
import com.example.core.network.AppError
import com.example.core.network.DefaultFirebaseTokenProvider
import com.example.core.util.AccountValidators
import com.example.core.util.BioValidator
import com.example.core.util.ImageUploadProcessor
import com.example.data.local.SessionIdStore
import com.example.data.model.ContentType
import com.example.data.model.CoverCrop
import com.example.data.model.UserProfile
import com.example.data.remote.TwelveAuthApi
import com.example.data.remote.TwelveContentApi
import com.example.data.remote.TwelveUsersApi
import com.example.data.repository.AccountRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ContentEventsBus
import com.example.data.repository.ContentRepository
import com.example.data.repository.SessionState
import com.example.ui.composer.ComposerResultEvent
import com.example.ui.composer.PostComposerViewModel
import com.example.ui.settings.AccountInfoViewModel
import com.example.ui.settings.SecurityViewModel
import com.example.ui.settings.TagCheckStatus
import com.example.ui.settings.VisitorsViewModel
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class M3ProfileAndSettingsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var json: Json
    private lateinit var authRepository: AuthRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var contentRepository: ContentRepository
    private lateinit var eventsBus: ContentEventsBus

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val app = RuntimeEnvironment.getApplication()
        val unconfiguredFirebase = object : FirebaseAvailability {
            override val isConfigured: Boolean = false
        }
        val tokenProvider = DefaultFirebaseTokenProvider(unconfiguredFirebase)
        val authApi = retrofit.create(TwelveAuthApi::class.java)
        val usersApi = retrofit.create(TwelveUsersApi::class.java)
        val contentApi = retrofit.create(TwelveContentApi::class.java)

        authRepository = AuthRepository(app, unconfiguredFirebase, tokenProvider, authApi)
        accountRepository = AccountRepository(usersApi, authApi, authRepository, json)
        contentRepository = ContentRepository(contentApi, SessionIdStore(app))
        eventsBus = ContentEventsBus()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) break
            kotlinx.coroutines.yield()
            Thread.sleep(10L)
        }
    }

    @Test
    fun `BioValidator and AccountValidators enforce Twelve backend limits`() {
        // Tiểu sử: chuẩn hoá line breaks, khoảng trắng và đếm code points + giới hạn 6 dòng / 300 ký tự
        val rawBio = "  Dòng 1  \r\n\r\n\r\nDòng\u200B 2  "
        assertEquals("Dòng 1\n\nDòng 2", BioValidator.normalizeBio(rawBio))
        assertTrue(BioValidator.bioStats("a\nb\nc\nd\ne\nf").valid)
        val sevenLines = BioValidator.bioStats("1\n2\n3\n4\n5\n6\n7")
        assertTrue(sevenLines.tooManyLines)
        assertFalse(sevenLines.valid)
        val emojiBio = "🔥".repeat(300)
        assertTrue(BioValidator.bioStats(emojiBio).valid)
        assertTrue(BioValidator.bioStats("🔥".repeat(301)).tooLong)

        // Tên hiển thị & Tên thật
        assertEquals("Nguyễn Văn A", AccountValidators.cleanDisplayText("  Nguyễn\u200B\u2800  Văn   A "))
        assertNotNull(AccountValidators.validateDisplayName("   "))
        assertNull(AccountValidators.validateDisplayName("a".repeat(50)))
        assertNotNull(AccountValidators.validateDisplayName("a".repeat(51)))
        assertNull(AccountValidators.validateRealName("Trần Thị B"))
        assertNotNull(AccountValidators.validateRealName("b".repeat(101)))

        // Ngày sinh: năm nhuận, ngày không tồn tại, tương lai
        val today = LocalDate.of(2026, 6, 1)
        assertNull(AccountValidators.validateBirthday("2000-02-29", today))
        assertNotNull(AccountValidators.validateBirthday("2001-02-29", today))
        assertNotNull(AccountValidators.validateBirthday("1899-12-31", today))
        assertNotNull(AccountValidators.validateBirthday("2026-06-02", today))

        // Mật khẩu mới & URL ảnh bìa
        assertNotNull(AccountValidators.validateNewPassword("", "12345678", "12345678"))
        assertNotNull(AccountValidators.validateNewPassword("oldpass1", "short", "short"))
        assertNotNull(AccountValidators.validateNewPassword("samepass", "samepass", "samepass"))
        assertNotNull(AccountValidators.validateNewPassword("oldpass1", "newpass12", "mismatch"))
        assertNull(AccountValidators.validateNewPassword("oldpass1", "newpass12", "newpass12"))
        assertTrue(AccountValidators.isValidCoverUrlInput("https://cdn.example.com/c.jpg"))
        assertFalse(AccountValidators.isValidCoverUrlInput("http://cdn.example.com/c.jpg"))
    }

    @Test
    fun `ImageUploadProcessor cover crop round-trips with estimateControlsFromCrop`() {
        val crop = ImageUploadProcessor.computeCoverCropFromViewport(
            imageAspect = 1.5f,
            panX = 0.5f,
            panY = -0.5f,
            zoom = 1.5f,
        )
        assertEquals(66.67, crop.width, 0.05)
        assertEquals(33.33, crop.height, 0.05)
        assertTrue(crop.x in 0.0..(100.0 - crop.width + 0.05))
        assertTrue(crop.y in 0.0..(100.0 - crop.height + 0.05))

        val (panX, panY, zoom) = ImageUploadProcessor.estimateControlsFromCrop(crop, imageAspect = 1.5f)
        assertTrue(abs(panX - 0.5f) < 0.05f)
        assertTrue(abs(panY - (-0.5f)) < 0.05f)
        assertTrue(abs(zoom - 1.5f) < 0.05f)
    }

    @Test
    fun `AccountRepository handles dirty updateMe, explicit JsonNull cover crop, multipart upload and deletion states`() =
        runTest(mainDispatcherRule.dispatcher) {
            authRepository.setActiveProfileForTesting(
                UserProfile(
                    uid = "u_self",
                    displayName = "Tên cũ",
                    tagName = "old_tag",
                    bio = "Bio cũ",
                    status = "active",
                    accountGroup = "RA",
                ),
            )

            val recordedBodies = mutableListOf<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    val body = request.body.readUtf8()
                    recordedBodies.add("${request.method} $path => $body")
                    return when {
                        path == "/api/users/me" && request.method == "PATCH" ->
                            MockResponse().setBody("""{"message":"OK"}""")
                        path == "/api/users/me/cover/crop" && request.method == "PUT" ->
                            MockResponse().setBody("""{"coverUrl":"https://cdn.example.com/cov.jpg","coverCrop":null}""")
                        path == "/api/users/me/avatar" && request.method == "POST" ->
                            MockResponse().setBody("""{"avatarUrl":"https://cdn.example.com/av_new.jpg"}""")
                        path == "/api/users/me/cover" && request.method == "POST" ->
                            MockResponse().setBody(
                                """{"coverUrl":"https://cdn.example.com/cov2.jpg","coverCrop":{"x":10.0,"y":5.0,"width":80.0,"height":80.0}}""",
                            )
                        path == "/api/users/me/deletion" && request.method == "POST" ->
                            MockResponse()
                                .setResponseCode(202)
                                .setBody("""{"status":"deleting","message":"Đã tiếp nhận yêu cầu xoá."}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }

            // 1. Chỉ gửi dirty fields (kể cả bio = "" để xoá trắng bio)
            val patchRes = accountRepository.updateMe(mapOf("displayName" to "Tên mới", "bio" to ""))
            assertTrue(patchRes.isSuccess)
            val patchRecord = recordedBodies[0]
            assertTrue(patchRecord.contains(""""displayName":"Tên mới""""))
            assertTrue(patchRecord.contains(""""bio":"""))
            assertFalse(patchRecord.contains("birthday"))
            assertEquals("Tên mới", authRepository.currentActiveProfile()?.displayName)
            assertEquals("", authRepository.currentActiveProfile()?.bio)

            // 2. PUT /users/me/cover/crop với null PHẢI gửi tường minh `"crop":null`
            val resetCropRes = accountRepository.setCoverCrop(null)
            assertTrue(resetCropRes.isSuccess)
            assertTrue(recordedBodies[1].contains(""""crop":null"""))
            assertNull(authRepository.currentActiveProfile()?.coverCrop)

            // 3. Multipart upload avatar & cover+crop
            val avatarRes = accountRepository.uploadAvatar(byteArrayOf(1, 2, 3))
            assertEquals("https://cdn.example.com/av_new.jpg", avatarRes.getOrNull())
            assertEquals("https://cdn.example.com/av_new.jpg", authRepository.currentActiveProfile()?.avatarUrl)
            assertTrue(recordedBodies[2].contains("""name="avatar""""))

            val coverRes = accountRepository.uploadCover(byteArrayOf(4, 5, 6), CoverCrop(10.0, 5.0, 80.0, 80.0))
            assertTrue(coverRes.isSuccess)
            assertTrue(recordedBodies[3].contains("""name="cover""""))
            assertTrue(recordedBodies[3].contains("""name="crop""""))
            assertEquals("https://cdn.example.com/cov2.jpg", authRepository.currentActiveProfile()?.coverUrl)

            // 4. POST /users/me/deletion 202
            val delRes = accountRepository.requestDeletion()
            assertEquals("deleting", delRes.getOrNull()?.status)
        }

    @Test
    fun `AccountRepository propagates ACCOUNT_SUSPENDED and SA_CANNOT_SELF_DELETE`() =
        runTest(mainDispatcherRule.dispatcher) {
            authRepository.setActiveProfileForTesting(
                UserProfile(uid = "u_sa", status = "active", accountGroup = "SA"),
            )

            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody("""{"error":"Tài khoản quản trị (SA) không thể tự xoá.","code":"SA_CANNOT_SELF_DELETE"}"""),
            )
            val saDel = accountRepository.requestDeletion()
            assertTrue(saDel.isFailure)
            assertTrue(saDel.exceptionOrNull()?.message?.contains("SA") == true)
            assertTrue(authRepository.sessionState.value is SessionState.Active)

            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody("""{"error":"Tài khoản đã bị tước quyền truy cập.","code":"ACCOUNT_SUSPENDED"}"""),
            )
            val blocked = accountRepository.listMyVisitors()
            assertTrue(blocked.isFailure)
            assertTrue(blocked.exceptionOrNull() is AppError.AccountSuspended)
            assertTrue(authRepository.sessionState.value is SessionState.Blocked)
        }

    @Test
    fun `AccountInfoViewModel manages dirty profile save, tag check debounce, and optimistic extras rollback`() =
        runTest(mainDispatcherRule.dispatcher) {
            authRepository.setActiveProfileForTesting(
                UserProfile(
                    uid = "u_1",
                    displayName = "Minh",
                    tagName = "minh_cu",
                    bio = "Giới thiệu",
                    country = "VN",
                    gender = "male",
                    status = "active",
                    profileExtras = mapOf("birthday" to true),
                ),
            )

            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/api/countries") ->
                            MockResponse().setBody("""{"countries":[{"code":"VN","name":"Việt Nam"},{"code":"JP","name":"Nhật Bản"}]}""")
                        path.startsWith("/api/users/me") && request.method == "PATCH" ->
                            MockResponse().setBody("""{"message":"OK"}""")
                        path.startsWith("/api/users/me/extras") && request.method == "PUT" ->
                            MockResponse().setResponseCode(500).setBody("""{"error":"Lỗi máy chủ"}""")
                        path.startsWith("/api/auth/check-tagname?tag=minh_moi") ->
                            MockResponse().setBody("""{"available":true,"reserved":false}""")
                        path.startsWith("/api/users/me/tag-name") && request.method == "PUT" ->
                            MockResponse().setResponseCode(409).setBody("""{"error":"Tag đã tồn tại","code":"TAGNAME_TAKEN"}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }

            val vm = AccountInfoViewModel(accountRepository, authRepository)
            awaitCondition { vm.uiState.value.countries.size == 2 && vm.uiState.value.profile != null }

            assertFalse(vm.uiState.value.canSaveProfile)
            vm.onBioChanged("Bio mới")
            assertTrue(vm.uiState.value.canSaveProfile)
            vm.saveProfileChanges("Đã lưu")
            awaitCondition { !vm.uiState.value.isSavingProfile && !vm.uiState.value.canSaveProfile }
            assertEquals("Bio mới", vm.uiState.value.baseline.bio)

            // Extras optimistic toggle + rollback on 500
            vm.toggleExtra("birthday", false, "Lỗi")
            awaitCondition { vm.uiState.value.busyExtraKeys.isEmpty() }
            assertEquals(true, vm.uiState.value.extras["birthday"])

            // Tag check debounce + 409 conflict
            vm.onTagInputChanged("@Minh_Moi")
            assertEquals(TagCheckStatus.Checking, vm.uiState.value.tagCheckStatus)
            testScheduler.advanceTimeBy(600L)
            testScheduler.runCurrent()
            awaitCondition { vm.uiState.value.tagCheckStatus == TagCheckStatus.Available }
            val tagSave = vm.confirmUpdateTagName("OK")
            assertTrue(tagSave.isFailure)
            val tagErr = tagSave.exceptionOrNull() as? AppError.ApiError
            assertNotNull(tagErr)
            assertEquals("TAGNAME_TAKEN", tagErr?.code)
            assertEquals(409, tagErr?.httpStatus)
        }

    @Test
    fun `VisitorsViewModel loads visitors list and SecurityViewModel validates password change`() =
        runTest(mainDispatcherRule.dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "visitors": [
                        {
                          "uid": "v1",
                          "displayName": "Lan",
                          "tagName": "@lan_99",
                          "badges": ["pro"],
                          "visitedAt": "2026-05-01T10:00:00Z"
                        }
                      ],
                      "max": 50
                    }
                    """.trimIndent(),
                ),
            )
            val visitorsVm = VisitorsViewModel(accountRepository)
            awaitCondition { !visitorsVm.uiState.value.isLoading }
            assertNull(visitorsVm.uiState.value.error)
            assertEquals(1, visitorsVm.uiState.value.visitors.size)
            assertEquals("v1", visitorsVm.uiState.value.visitors.first().uid)

            val secVm = SecurityViewModel(authRepository, accountRepository)
            secVm.onCurrentPasswordChanged("oldpass123")
            secVm.onNewPasswordChanged("short")
            secVm.onConfirmPasswordChanged("short")
            secVm.submitChangePassword("OK")
            assertNotNull(secVm.uiState.value.changePasswordError)
        }

    @Test
    fun `SA Blog CRUD works through ContentRepository and PostComposerViewModel and honors isSA gate`() =
        runTest(mainDispatcherRule.dispatcher) {
            val saProfile = UserProfile(uid = "sa1", status = "active", accountGroup = "SA")
            val raProfile = UserProfile(uid = "ra1", status = "active", accountGroup = "RA")
            assertTrue(saProfile.isSA)
            assertFalse(raProfile.isSA)

            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path == "/api/blogs" && request.method == "POST" ->
                            MockResponse().setResponseCode(201).setBody("""{"message":"OK","blogId":"blog_101"}""")
                        path == "/api/blogs/blog_101" && request.method == "PATCH" ->
                            MockResponse().setBody("""{"message":"OK"}""")
                        path == "/api/blogs/blog_101" && request.method == "DELETE" ->
                            MockResponse().setBody("""{"message":"OK"}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }

            val composer = PostComposerViewModel(contentRepository, authRepository, eventsBus)
            composer.init(ContentType.BLOG, null)
            composer.onTitleChanged("Thông báo phát hành Twelve Mobile")
            composer.onHashtagsChanged("#release #twelve")
            composer.onBodyChanged("Bản cập nhật đầu tiên.")

            var createdBlogId: String? = null
            val job = launch {
                composer.events.collect { ev ->
                    if (ev is ComposerResultEvent.Created && ev.contentType == ContentType.BLOG) {
                        createdBlogId = ev.id
                    }
                }
            }
            composer.submit("Thiếu nội dung")
            awaitCondition { createdBlogId != null }
            assertEquals("blog_101", createdBlogId)
            job.cancel()

            val updateBlogRes = contentRepository.updateContent(
                type = ContentType.BLOG,
                id = "blog_101",
                title = "Tiêu đề đã sửa",
                contentHtml = "<p>Nội dung sửa</p>",
                coverImageUrl = "",
                hashtags = listOf("#release"),
            )
            assertTrue(updateBlogRes.isSuccess)

            val deleteBlogRes = contentRepository.deleteContent(ContentType.BLOG, "blog_101")
            assertTrue(deleteBlogRes.isSuccess)
        }
}
