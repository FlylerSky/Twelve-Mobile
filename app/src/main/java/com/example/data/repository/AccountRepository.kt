package com.example.data.repository

import com.example.core.network.AppError
import com.example.core.util.TagNameNormalizer
import com.example.data.model.CheckTagNameResponse
import com.example.data.model.CountryItem
import com.example.data.model.CoverCrop
import com.example.data.model.CoverResponse
import com.example.data.model.DeletionRequest
import com.example.data.model.DeletionResponse
import com.example.data.model.ProfileVisitorItem
import com.example.data.model.SetCoverUrlRequest
import com.example.data.model.UpdateExtrasRequest
import com.example.data.model.UpdateRealNameRequest
import com.example.data.model.UpdateTagNameRequest
import com.example.data.model.UpdateTagNameResponse
import com.example.data.model.VisitorsResponse
import com.example.data.remote.TwelveAuthApi
import com.example.data.remote.TwelveUsersApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Kho dữ liệu quản lý tài khoản chính chủ ở M3:
 * thông tin cá nhân, tag name, ảnh đại diện / ảnh bìa, thông tin mở rộng, khách thăm, tên thật & xoá tài khoản.
 * Nguồn đối chiếu: backend/src/controllers/{users,account,countries,auth}.controller.js & frontend/src/api/users.api.js.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val usersApi: TwelveUsersApi,
    private val authApi: TwelveAuthApi,
    private val authRepository: AuthRepository,
    private val json: Json,
) {
    private fun <T> checkBlocked(result: Result<T>): Result<T> {
        val err = result.exceptionOrNull() as? AppError
        if (err != null) authRepository.notifyHardBlocked(err)
        return result
    }

    suspend fun listCountries(): Result<List<CountryItem>> = checkBlocked(
        safeApiCall { usersApi.listCountries().countries },
    )

    /** Chỉ gửi các trường đã thay đổi theo hợp đồng PATCH /users/me. */
    suspend fun updateMe(dirtyFields: Map<String, String>): Result<Unit> = checkBlocked(
        safeApiCall {
            val body = buildJsonObject {
                dirtyFields.forEach { (k, v) -> put(k, v) }
            }
            usersApi.updateMe(body)
            authRepository.patchActiveProfile { current ->
                current.copy(
                    displayName = dirtyFields["displayName"] ?: current.displayName,
                    birthday = dirtyFields["birthday"] ?: current.birthday,
                    gender = dirtyFields["gender"] ?: current.gender,
                    country = dirtyFields["country"] ?: current.country,
                    bio = dirtyFields["bio"] ?: current.bio,
                    isFirstLogin = false,
                )
            }
            Unit
        },
    )

    suspend fun uploadAvatar(jpegBytes: ByteArray): Result<String> = checkBlocked(
        safeApiCall {
            val part = MultipartBody.Part.createFormData(
                name = "avatar",
                filename = "avatar.jpg",
                body = jpegBytes.toRequestBody("image/jpeg".toMediaType()),
            )
            val url = usersApi.uploadAvatar(part).avatarUrl
            authRepository.patchActiveProfile { it.copy(avatarUrl = url) }
            url
        },
    )

    suspend fun uploadCover(jpegBytes: ByteArray, crop: CoverCrop?): Result<CoverResponse> = checkBlocked(
        safeApiCall {
            val filePart = MultipartBody.Part.createFormData(
                name = "cover",
                filename = "cover.jpg",
                body = jpegBytes.toRequestBody("image/jpeg".toMediaType()),
            )
            val cropBody = crop?.let {
                json.encodeToString(CoverCrop.serializer(), it)
                    .toRequestBody("text/plain".toMediaType())
            }
            val res = usersApi.uploadCover(filePart, cropBody)
            authRepository.patchActiveProfile {
                it.copy(coverUrl = res.coverUrl, coverCrop = res.coverCrop ?: crop)
            }
            res
        },
    )

    suspend fun setCoverUrl(url: String, crop: CoverCrop? = null): Result<CoverResponse> = checkBlocked(
        safeApiCall {
            val res = usersApi.setCoverUrl(SetCoverUrlRequest(url = url.trim(), crop = crop))
            authRepository.patchActiveProfile {
                it.copy(coverUrl = res.coverUrl, coverCrop = res.coverCrop)
            }
            res
        },
    )

    suspend fun setCoverCrop(crop: CoverCrop?): Result<CoverResponse> = checkBlocked(
        safeApiCall {
            // Đóng gói bằng buildJsonObject để giữ nguyên `"crop": null` khi đặt lại khung mặc định (kể cả khi explicitNulls = false).
            val body = buildJsonObject {
                put("crop", if (crop != null) json.encodeToJsonElement(CoverCrop.serializer(), crop) else JsonNull)
            }
            val res = usersApi.setCoverCrop(body)
            authRepository.patchActiveProfile {
                it.copy(coverUrl = res.coverUrl ?: it.coverUrl, coverCrop = res.coverCrop)
            }
            res
        },
    )

    suspend fun removeCover(): Result<Unit> = checkBlocked(
        safeApiCall {
            usersApi.removeCover()
            authRepository.patchActiveProfile { it.copy(coverUrl = null, coverCrop = null) }
            Unit
        },
    )

    suspend fun updateExtras(updates: Map<String, Boolean>): Result<Map<String, Boolean>> = checkBlocked(
        safeApiCall {
            val resolved = usersApi.updateExtras(UpdateExtrasRequest(updates)).profileExtras
            authRepository.patchActiveProfile { it.copy(profileExtras = resolved) }
            resolved
        },
    )

    suspend fun listMyVisitors(): Result<VisitorsResponse> = checkBlocked(
        safeApiCall { usersApi.listMyVisitors() },
    )

    suspend fun checkTagName(rawTag: String): Result<CheckTagNameResponse> = checkBlocked(
        safeApiCall { authApi.checkTagName(TagNameNormalizer.toBareTag(rawTag)) },
    )

    suspend fun updateTagName(rawTag: String): Result<UpdateTagNameResponse> = checkBlocked(
        safeApiCall {
            val bare = TagNameNormalizer.toBareTag(rawTag)
            val res = usersApi.updateTagName(UpdateTagNameRequest(bare))
            authRepository.patchActiveProfile {
                it.copy(tagName = TagNameNormalizer.toBareTag(res.tagName).ifBlank { bare })
            }
            res
        },
    )

    suspend fun getRealName(): Result<String> = checkBlocked(
        safeApiCall { usersApi.getRealName().fullName.orEmpty() },
    )

    suspend fun updateRealName(fullName: String): Result<Unit> = checkBlocked(
        safeApiCall {
            usersApi.updateRealName(UpdateRealNameRequest(fullName.trim()))
            Unit
        },
    )

    suspend fun requestDeletion(): Result<DeletionResponse> = checkBlocked(
        safeApiCall { usersApi.requestDeletion(DeletionRequest(confirm = true)) },
    )
}
