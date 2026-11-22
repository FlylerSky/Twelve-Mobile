package com.example.data.model

import com.example.core.util.TolerantTimestampSerializer
import kotlinx.serialization.Serializable

/**
 * Trạng thái tài khoản trong `users/{uid}.status`.
 * Nguồn đối chiếu: backend/src/utils/constants.js#ACCOUNT_STATUS,
 * backend/src/controllers/users.controller.js#getMe, backend/src/middleware/auth.js.
 */
enum class AccountStatus(val raw: String) {
    PENDING("pending"),
    ACTIVE("active"),
    SUSPENDED("suspended"),
    DELETING("deleting"),
    UNKNOWN("unknown");

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.raw.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
data class CoverCrop(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 100.0,
    val height: Double = 100.0,
)

/**
 * Hồ sơ người dùng đang đăng nhập trả về từ GET /api/users/me.
 * Lưu ý từ users.controller.js#getMe: `tagName` tại đây KHÔNG có dấu "@",
 * trong khi author / public profile CÓ dấu "@". Luôn chuẩn hoá bằng TagNameNormalizer.
 */
@Serializable
data class UserProfile(
    val uid: String = "",
    val displayName: String? = null,
    val tagName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val coverCrop: CoverCrop? = null,
    val bio: String? = null,
    val birthday: String? = null,
    val gender: String? = null,
    val country: String? = null,
    val accountRole: String? = null,
    val accountGroup: String? = null,
    val status: String? = null,
    val isFirstLogin: Boolean = false,
    val isLegacyMember: Boolean = false,
    val accountScore: Int = 0,
    val postsScoreSum: Int = 0,
    val positiveScore: Int = 0,
    val pinnedPostIds: List<String> = emptyList(),
    val profileExtras: Map<String, Boolean> = emptyMap(),
    val badges: List<String> = emptyList(),
    @Serializable(with = TolerantTimestampSerializer::class)
    val createdAt: String? = null,
) {
    val accountStatus: AccountStatus
        get() = AccountStatus.fromRaw(status)

    val isSA: Boolean
        get() = accountStatus == AccountStatus.ACTIVE && accountGroup == "SA"
}

@Serializable
data class GetMeResponse(
    val user: UserProfile,
)

@Serializable
data class SyncActivationResponse(
    val message: String? = null,
    val status: String? = null,
)

@Serializable
data class HealthResponse(
    val status: String = "",
    val version: String? = null,
)
