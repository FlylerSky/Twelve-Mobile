package com.example.data.repository

import com.example.core.util.TagNameNormalizer
import com.example.data.model.ContentCard
import com.example.data.model.ExtraInfoResponse
import com.example.data.model.PublicProfile
import com.example.data.remote.TwelveUsersApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kho dữ liệu hồ sơ công khai người dùng (hỗ trợ cả `@tag` lẫn `uid`).
 * Nguồn đối chiếu: backend/src/controllers/users.controller.js, frontend/src/api/users.api.js.
 */
@Singleton
class UserRepository @Inject constructor(
    private val usersApi: TwelveUsersApi,
) {
    suspend fun getPublicProfileByUid(uid: String): Result<PublicProfile> = safeApiCall {
        usersApi.getPublicProfile(uid).user
    }

    suspend fun getPublicProfileByTag(rawTag: String): Result<PublicProfile> = safeApiCall {
        val bare = TagNameNormalizer.toBareTag(rawTag)
        require(bare.isNotBlank()) { "Tag Name không hợp lệ." }
        usersApi.getPublicProfileByTag(bare).user
    }

    suspend fun listPinnedPosts(uid: String): Result<List<ContentCard>> = safeApiCall {
        usersApi.listPinnedPosts(uid).posts
    }

    suspend fun listUserPosts(uid: String, cursor: String? = null, limit: Int = 10): Result<FeedPage> = safeApiCall {
        val res = usersApi.listUserPosts(uid = uid, cursor = cursor, limit = limit)
        FeedPage(items = res.posts, nextCursor = res.nextCursor)
    }

    suspend fun listUserBlogs(uid: String, cursor: String? = null, limit: Int = 10): Result<FeedPage> = safeApiCall {
        val res = usersApi.listUserBlogs(uid = uid, cursor = cursor, limit = limit)
        FeedPage(
            items = res.blogs.map { it.copy(type = "blog", detailPath = "/blog") },
            nextCursor = res.nextCursor,
        )
    }

    suspend fun getExtraInfo(uid: String): Result<ExtraInfoResponse> = safeApiCall {
        usersApi.getExtraInfo(uid)
    }

    /** Ghi nhận lượt ghé thăm hồ sơ (fire-and-forget khi đã đăng nhập xem người khác). */
    suspend fun recordVisitBestEffort(uid: String) {
        runCatching { usersApi.recordVisit(uid) }
    }
}
