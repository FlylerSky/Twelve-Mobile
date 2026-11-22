package com.example.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Chỉ thử lại các yêu cầu GET an toàn khi gặp lỗi mạng hoặc 5xx (tối đa 2 lần, backoff cấp số nhân).
 * Tuyệt đối KHÔNG thử lại POST/PUT/PATCH/DELETE hoặc mã 429/401/403/4xx.
 */
class GetRetryInterceptor(
    private val maxRetries: Int = 2,
    private val initialDelayMs: Long = 1_000L,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.method.equals("GET", ignoreCase = true)) {
            return chain.proceed(request)
        }

        var attempt = 0
        var lastException: IOException? = null
        while (true) {
            try {
                val response = chain.proceed(request)
                if (response.code !in 500..599 || attempt >= maxRetries) {
                    return response
                }
                response.close()
            } catch (io: IOException) {
                lastException = io
                if (attempt >= maxRetries) {
                    throw io
                }
            }

            val delayMs = initialDelayMs * (1L shl attempt)
            attempt += 1
            try {
                sleeper(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastException ?: IOException("Đã bị ngắt trong lúc chờ thử lại.")
            }
        }
    }
}
