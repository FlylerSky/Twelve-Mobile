package com.example.core.network

import android.os.Looper
import com.example.core.config.FirebaseAvailability
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Trừu tượng cung cấp ID token tươi và đăng xuất khi phiên không hợp lệ.
 * Chỉ được phép gọi trên luồng nền của OkHttp, tuyệt đối không gọi trên main thread.
 */
interface FirebaseTokenProvider {
    fun getIdTokenSync(forceRefresh: Boolean): String?
    fun signOutOnUnauthorized()
}

@Singleton
class DefaultFirebaseTokenProvider @Inject constructor(
    private val firebaseAvailability: FirebaseAvailability,
) : FirebaseTokenProvider {

    private var sessionInvalidatedListener: (() -> Unit)? = null

    fun setOnSessionInvalidatedListener(listener: (() -> Unit)?) {
        sessionInvalidatedListener = listener
    }

    override fun getIdTokenSync(forceRefresh: Boolean): String? {
        // Kiểm tra bảo vệ: không bao giờ block main thread để chờ Firebase Task.
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "AuthTokenInterceptor không được chạy trên main thread."
        }
        if (!firebaseAvailability.isConfigured) return null
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() ?: return null
        return runCatching {
            Tasks.await(user.getIdToken(forceRefresh)).token
        }.getOrNull()
    }

    override fun signOutOnUnauthorized() {
        if (firebaseAvailability.isConfigured) {
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
        sessionInvalidatedListener?.invoke()
    }
}

/**
 * Gắn `Authorization: Bearer <idToken>` vào mọi yêu cầu REST (trừ /health).
 * Nếu gặp 401, làm mới token cưỡng bức ĐÚNG 1 LẦN rồi thử lại đúng 1 lần;
 * nếu vẫn 401 thì đăng xuất sạch sẽ.
 * Nguồn đối chiếu: frontend/src/api/client.js.
 */
class AuthTokenInterceptor @Inject constructor(
    private val tokenProvider: FirebaseTokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.encodedPath == "/health") {
            return chain.proceed(original)
        }

        val token = tokenProvider.getIdTokenSync(forceRefresh = false)
        val authedRequest = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header(HEADER_AUTHORIZATION, "Bearer $token")
                .build()
        } else {
            original
        }

        val response = chain.proceed(authedRequest)
        if (response.code != 401 || token.isNullOrBlank()) {
            return response
        }

        // Thử làm mới cưỡng bức 1 lần trên đúng request này; không lặp vô hạn.
        val refreshedToken = tokenProvider.getIdTokenSync(forceRefresh = true)
        if (refreshedToken.isNullOrBlank() || refreshedToken == token) {
            tokenProvider.signOutOnUnauthorized()
            return response
        }

        response.close()
        val retryRequest = original.newBuilder()
            .header(HEADER_AUTHORIZATION, "Bearer $refreshedToken")
            .build()
        val retryResponse = chain.proceed(retryRequest)
        if (retryResponse.code == 401) {
            tokenProvider.signOutOnUnauthorized()
        }
        return retryResponse
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
