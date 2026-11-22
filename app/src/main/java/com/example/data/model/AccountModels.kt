package com.example.data.model

import com.example.core.util.TolerantTimestampSerializer
import kotlinx.serialization.Serializable

/**
 * Mô hình cho M3: cài đặt hồ sơ, quốc gia, tag name, ảnh đại diện / ảnh bìa, khách thăm, bảo mật & xoá tài khoản.
 * Nguồn đối chiếu: backend/src/controllers/{countries,auth,users,account}.controller.js & services/profileVisitors.js.
 */
@Serializable
data class CountryItem(
    val code: String = "",
    val name: String = "",
)

@Serializable
data class CountriesResponse(
    val countries: List<CountryItem> = emptyList(),
)

@Serializable
data class CheckTagNameResponse(
    val available: Boolean = false,
    val reserved: Boolean = false,
    val error: String? = null,
)

@Serializable
data class UpdateTagNameRequest(
    val tagName: String,
)

@Serializable
data class UpdateTagNameResponse(
    val tagName: String = "",
    val message: String? = null,
)

@Serializable
data class AvatarUploadResponse(
    val avatarUrl: String = "",
)

@Serializable
data class CoverResponse(
    val coverUrl: String? = null,
    val coverCrop: CoverCrop? = null,
)

@Serializable
data class SetCoverUrlRequest(
    val url: String,
    val crop: CoverCrop? = null,
)

@Serializable
data class UpdateExtrasRequest(
    val extras: Map<String, Boolean>,
)

@Serializable
data class UpdateExtrasResponse(
    val profileExtras: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class ProfileVisitorItem(
    val uid: String = "",
    val displayName: String? = null,
    val tagName: String? = null,
    val avatarUrl: String? = null,
    val accountGroup: String? = null,
    val accountRole: String? = null,
    val isLegacyMember: Boolean = false,
    val badges: List<String> = emptyList(),
    @Serializable(with = TolerantTimestampSerializer::class)
    val visitedAt: String? = null,
)

@Serializable
data class VisitorsResponse(
    val visitors: List<ProfileVisitorItem> = emptyList(),
    val max: Int = 50,
)

@Serializable
data class RealNameResponse(
    val fullName: String? = null,
)

@Serializable
data class UpdateRealNameRequest(
    val fullName: String,
)

@Serializable
data class DeletionRequest(
    val confirm: Boolean = true,
)

@Serializable
data class DeletionResponse(
    val status: String = "",
    val message: String? = null,
)
