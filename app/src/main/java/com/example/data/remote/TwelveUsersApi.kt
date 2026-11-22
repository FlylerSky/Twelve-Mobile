package com.example.data.remote

import com.example.data.model.AvatarUploadResponse
import com.example.data.model.CountriesResponse
import com.example.data.model.CoverResponse
import com.example.data.model.DeletionRequest
import com.example.data.model.DeletionResponse
import com.example.data.model.ExtraInfoResponse
import com.example.data.model.ListBlogsResponse
import com.example.data.model.ListPostsResponse
import com.example.data.model.MessageResponse
import com.example.data.model.PinnedPostsResponse
import com.example.data.model.PublicProfileResponse
import com.example.data.model.RealNameResponse
import com.example.data.model.RecordVisitResponse
import com.example.data.model.SetCoverUrlRequest
import com.example.data.model.UpdateExtrasRequest
import com.example.data.model.UpdateExtrasResponse
import com.example.data.model.UpdateRealNameRequest
import com.example.data.model.UpdateTagNameRequest
import com.example.data.model.UpdateTagNameResponse
import com.example.data.model.VisitorsResponse
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API hồ sơ người dùng, quản lý tài khoản cá nhân & quốc gia.
 * Nguồn đối chiếu: backend/src/routes/{users,countries}.routes.js, frontend/src/api/{users,countries}.api.js.
 */
interface TwelveUsersApi {
    @GET("countries")
    suspend fun listCountries(): CountriesResponse

    @PATCH("users/me")
    suspend fun updateMe(@Body updates: JsonObject): MessageResponse

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): AvatarUploadResponse

    @Multipart
    @POST("users/me/cover")
    suspend fun uploadCover(
        @Part cover: MultipartBody.Part,
        @Part("crop") cropJson: RequestBody? = null,
    ): CoverResponse

    @PUT("users/me/cover")
    suspend fun setCoverUrl(@Body body: SetCoverUrlRequest): CoverResponse

    @PUT("users/me/cover/crop")
    suspend fun setCoverCrop(@Body body: JsonObject): CoverResponse

    @DELETE("users/me/cover")
    suspend fun removeCover(): CoverResponse

    @PUT("users/me/extras")
    suspend fun updateExtras(@Body body: UpdateExtrasRequest): UpdateExtrasResponse

    @GET("users/me/visitors")
    suspend fun listMyVisitors(): VisitorsResponse

    @GET("users/me/real-name")
    suspend fun getRealName(): RealNameResponse

    @PUT("users/me/real-name")
    suspend fun updateRealName(@Body body: UpdateRealNameRequest): MessageResponse

    @PUT("users/me/tag-name")
    suspend fun updateTagName(@Body body: UpdateTagNameRequest): UpdateTagNameResponse

    @POST("users/me/deletion")
    suspend fun requestDeletion(@Body body: DeletionRequest = DeletionRequest(confirm = true)): DeletionResponse

    @GET("users/{uid}")
    suspend fun getPublicProfile(@Path("uid") uid: String): PublicProfileResponse

    @GET("users/by-tag/{tag}")
    suspend fun getPublicProfileByTag(@Path("tag") tag: String): PublicProfileResponse

    @GET("users/{uid}/pinned")
    suspend fun listPinnedPosts(@Path("uid") uid: String): PinnedPostsResponse

    @GET("users/{uid}/posts")
    suspend fun listUserPosts(
        @Path("uid") uid: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 10,
    ): ListPostsResponse

    @GET("users/{uid}/blogs")
    suspend fun listUserBlogs(
        @Path("uid") uid: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 10,
    ): ListBlogsResponse

    @GET("users/{uid}/extra-info")
    suspend fun getExtraInfo(@Path("uid") uid: String): ExtraInfoResponse

    @POST("users/{uid}/visit")
    suspend fun recordVisit(@Path("uid") uid: String): RecordVisitResponse
}
