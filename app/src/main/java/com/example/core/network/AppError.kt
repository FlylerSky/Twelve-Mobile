package com.example.core.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import retrofit2.HttpException

/**
 * Thân lỗi chuẩn của Twelve backend: { "error": "<tiếng Việt>", "code"?: "<mã máy>", "status"?: "<trạng thái>" }.
 * Nguồn đối chiếu: backend/server.js, backend/src/middleware/auth.js, backend/src/controllers/auth.controller.js.
 */
@Serializable
data class BackendErrorPayload(
    val error: String? = null,
    val code: String? = null,
    val status: String? = null,
)

sealed class AppError(open val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {
    /** 403 code = ACCOUNT_SUSPENDED: chặn mọi endpoint kể cả /users/me và /auth/activation/sync -> đăng xuất, không thử lại. */
    data class AccountSuspended(
        val serverMessage: String,
    ) : AppError(serverMessage)

    /** 403 code = ACCOUNT_DELETING: chặn mọi endpoint kể cả /users/me và /auth/activation/sync -> đăng xuất, không thử lại. */
    data class AccountDeleting(
        val serverMessage: String,
    ) : AppError(serverMessage)

    /** 403 code = ACCOUNT_NOT_ACTIVE: tài khoản chưa active gọi endpoint thường -> thông báo kích hoạt, không thử lại. */
    data class AccountNotActive(
        val serverMessage: String,
        val accountStatus: String? = null,
    ) : AppError(serverMessage)

    /** 403 code = REAUTH_REQUIRED: yêu cầu nhập lại mật khẩu (auth_time > 300s). */
    data class ReauthRequired(
        val serverMessage: String,
    ) : AppError(serverMessage)

    /** 429 Too Many Requests: tôn trọng Retry-After / RateLimit-Reset, tuyệt đối không tự retry. */
    data class RateLimited(
        val serverMessage: String,
        val retryAfterSeconds: Long? = null,
    ) : AppError(serverMessage)

    /** 401 Unauthorized sau khi đã thử làm mới token. */
    data class Unauthorized(
        val serverMessage: String,
    ) : AppError(serverMessage)

    /** Lỗi nghiệp vụ khác từ backend kèm HTTP status và machine code nếu có. */
    data class ApiError(
        val httpStatus: Int,
        val code: String?,
        val serverMessage: String,
    ) : AppError(serverMessage)

    data class NetworkOffline(
        val fallbackMessage: String = "Không có kết nối mạng. Vui lòng kiểm tra Wi-Fi hoặc dữ liệu di động.",
        override val cause: Throwable? = null,
    ) : AppError(fallbackMessage, cause)

    data class Timeout(
        val fallbackMessage: String = "Máy chủ phản hồi chậm. Vui lòng thử lại sau giây lát.",
        override val cause: Throwable? = null,
    ) : AppError(fallbackMessage, cause)

    data class Unknown(
        val fallbackMessage: String = "Đã xảy ra lỗi không xác định. Vui lòng thử lại.",
        override val cause: Throwable? = null,
    ) : AppError(fallbackMessage, cause)

    val isAccountBlocked: Boolean
        get() = this is AccountSuspended || this is AccountDeleting
}

object BackendErrorParser {
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fromThrowable(throwable: Throwable): AppError = when (throwable) {
        is AppError -> throwable
        is HttpException -> fromHttp(
            statusCode = throwable.code(),
            rawBody = runCatching { throwable.response()?.errorBody()?.string() }.getOrNull(),
            headers = throwable.response()?.headers(),
            cause = throwable,
        )
        is SocketTimeoutException -> AppError.Timeout(cause = throwable)
        is UnknownHostException -> AppError.NetworkOffline(cause = throwable)
        is IOException -> AppError.NetworkOffline(cause = throwable)
        else -> AppError.Unknown(cause = throwable)
    }

    fun fromHttp(
        statusCode: Int,
        rawBody: String?,
        headers: Headers? = null,
        cause: Throwable? = null,
    ): AppError {
        val payload = rawBody
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { lenientJson.decodeFromString(BackendErrorPayload.serializer(), it) }.getOrNull() }

        val serverMsg = payload?.error?.takeIf { it.isNotBlank() }
        val code = payload?.code?.takeIf { it.isNotBlank() }

        if (statusCode == 429) {
            val retryAfter = parseRetryAfterSeconds(headers)
            val msg = serverMsg ?: if (retryAfter != null) {
                "Bạn thao tác quá nhanh. Vui lòng đợi $retryAfter giây rồi thử lại."
            } else {
                "Bạn thao tác quá nhanh. Vui lòng đợi một lát rồi thử lại."
            }
            return AppError.RateLimited(msg, retryAfter)
        }

        if (statusCode == 401) {
            return AppError.Unauthorized(serverMsg ?: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        }

        if (statusCode == 403) {
            when (code) {
                "ACCOUNT_SUSPENDED" -> return AppError.AccountSuspended(
                    serverMsg ?: "Tài khoản đã bị tước quyền truy cập."
                )
                "ACCOUNT_DELETING" -> return AppError.AccountDeleting(
                    serverMsg ?: "Tài khoản đang được xoá."
                )
                "ACCOUNT_NOT_ACTIVE" -> return AppError.AccountNotActive(
                    serverMessage = serverMsg ?: "Tài khoản chưa được kích hoạt. Vui lòng xác minh email trước khi đăng nhập.",
                    accountStatus = payload?.status,
                )
                "REAUTH_REQUIRED" -> return AppError.ReauthRequired(
                    serverMsg ?: "Cần nhập lại mật khẩu để thực hiện thao tác này."
                )
            }
        }

        return AppError.ApiError(
            httpStatus = statusCode,
            code = code,
            serverMessage = serverMsg ?: "Đã có lỗi từ máy chủ (mã $statusCode). Vui lòng thử lại.",
        )
    }

    private fun parseRetryAfterSeconds(headers: Headers?): Long? {
        if (headers == null) return null
        headers["Retry-After"]?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        // express-rate-limit v7 với standardHeaders: true trả RateLimit-Reset tính theo số giây còn lại.
        headers["RateLimit-Reset"]?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        return null
    }
}
