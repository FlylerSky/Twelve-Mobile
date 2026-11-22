package com.example.data.repository

import com.example.core.network.AppError
import com.example.core.network.BackendErrorParser
import com.example.data.local.SessionIdStore
import com.example.data.model.CommentMutationRequest
import com.example.data.model.ContentCard
import com.example.data.model.ContentType
import com.example.data.model.CreateContentRequest
import com.example.data.model.FullContent
import com.example.data.model.ListCommentsResponse
import com.example.data.model.PinPostResponse
import com.example.data.model.ReactRequest
import com.example.data.model.ReactionType
import com.example.data.model.ShareResponse
import com.example.data.model.UpdateContentRequest
import com.example.data.remote.TwelveContentApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

data class FeedPage(
    val items: List<ContentCard>,
    val nextCursor: String?,
)

data class ContentDetailResult(
    val content: FullContent,
    val myReaction: ReactionType?,
)

/**
 * Kho dữ liệu đọc & tương tác bài viết, blog và bình luận.
 * Lưu ý quan trọng: `getContentDetail` CHỈ gọi khi người dùng thực sự mở màn hình chi tiết
 * (vì endpoint này ghi nhận view/share). Tuyệt đối không prefetch hay gọi từ màn hình danh sách.
 * Nguồn đối chiếu: backend/src/controllers/posts.controller.js, blog.controller.js,
 * comments.controller.js, reactions.controller.js, share.controller.js.
 */
@Singleton
class ContentRepository @Inject constructor(
    private val contentApi: TwelveContentApi,
    private val sessionIdStore: SessionIdStore,
) {
    suspend fun listPosts(cursor: String? = null, limit: Int = 10): Result<FeedPage> = safeApiCall {
        val res = contentApi.listPosts(cursor = cursor, limit = limit)
        FeedPage(items = res.posts, nextCursor = res.nextCursor)
    }

    suspend fun listBlogs(cursor: String? = null, limit: Int = 10): Result<FeedPage> = safeApiCall {
        val res = contentApi.listBlogs(cursor = cursor, limit = limit)
        FeedPage(
            items = res.blogs.map { it.copy(type = "blog", detailPath = "/blog") },
            nextCursor = res.nextCursor,
        )
    }

    suspend fun getContentDetail(
        type: ContentType,
        id: String,
        sharerUid: String? = null,
        shareSignature: String? = null,
        sharedFlag: Boolean = false,
    ): Result<ContentDetailResult> = safeApiCall {
        val sid = sessionIdStore.getOrCreateSessionId()
        val idu = sharerUid?.takeIf { it.isNotBlank() }
        val sig = shareSignature?.takeIf { it.isNotBlank() }
        val shared = if (sharedFlag && idu == null) "1" else null
        when (type) {
            ContentType.POST -> {
                val res = contentApi.getPost(id = id, idu = idu, signature = sig, shared = shared, sid = sid)
                ContentDetailResult(res.post, ReactionType.fromRaw(res.myReaction))
            }
            ContentType.BLOG -> {
                val res = contentApi.getBlog(id = id, idu = idu, signature = sig, shared = shared, sid = sid)
                ContentDetailResult(res.blog, ReactionType.fromRaw(res.myReaction))
            }
        }
    }

    suspend fun listComments(
        type: ContentType,
        id: String,
        cursor: String? = null,
        limit: Int = 20,
    ): Result<ListCommentsResponse> = safeApiCall {
        when (type) {
            ContentType.POST -> contentApi.listPostComments(id = id, cursor = cursor, limit = limit)
            ContentType.BLOG -> contentApi.listBlogComments(id = id, cursor = cursor, limit = limit)
        }
    }

    suspend fun react(type: ContentType, id: String, reaction: ReactionType): Result<ReactionType?> = safeApiCall {
        val req = ReactRequest(reaction.raw)
        val res = when (type) {
            ContentType.POST -> contentApi.reactPost(id, req)
            ContentType.BLOG -> contentApi.reactBlog(id, req)
        }
        ReactionType.fromRaw(res.myReaction)
    }

    suspend fun createShareLink(type: ContentType, id: String): Result<ShareResponse> = safeApiCall {
        when (type) {
            ContentType.POST -> contentApi.sharePost(id)
            ContentType.BLOG -> contentApi.shareBlog(id)
        }
    }

    suspend fun addComment(type: ContentType, id: String, content: String): Result<String> = safeApiCall {
        val req = CommentMutationRequest(content = content)
        when (type) {
            ContentType.POST -> contentApi.addPostComment(id, req).commentId
            ContentType.BLOG -> contentApi.addBlogComment(id, req).commentId
        }
    }

    suspend fun editComment(type: ContentType, id: String, commentId: String, content: String): Result<Unit> = safeApiCall {
        val req = CommentMutationRequest(content = content)
        when (type) {
            ContentType.POST -> contentApi.editPostComment(id, commentId, req)
            ContentType.BLOG -> contentApi.editBlogComment(id, commentId, req)
        }
        Unit
    }

    suspend fun deleteComment(type: ContentType, id: String, commentId: String): Result<Unit> = safeApiCall {
        when (type) {
            ContentType.POST -> contentApi.deletePostComment(id, commentId)
            ContentType.BLOG -> contentApi.deleteBlogComment(id, commentId)
        }
        Unit
    }

    suspend fun createPost(
        title: String,
        contentHtml: String,
        coverImageUrl: String?,
        hashtags: List<String>,
    ): Result<String> = safeApiCall {
        contentApi.createPost(
            CreateContentRequest(
                title = title,
                contentHtml = contentHtml,
                coverImageUrl = coverImageUrl?.takeIf { it.isNotBlank() },
                hashtags = hashtags,
            ),
        ).postId
    }

    suspend fun createBlog(
        title: String,
        contentHtml: String,
        coverImageUrl: String?,
        hashtags: List<String>,
    ): Result<String> = safeApiCall {
        contentApi.createBlog(
            CreateContentRequest(
                title = title,
                contentHtml = contentHtml,
                coverImageUrl = coverImageUrl?.takeIf { it.isNotBlank() },
                hashtags = hashtags,
            ),
        ).blogId
    }

    suspend fun updateContent(
        type: ContentType,
        id: String,
        title: String,
        contentHtml: String,
        coverImageUrl: String?,
        hashtags: List<String>,
    ): Result<Unit> = safeApiCall {
        // Khi sửa bài/blog, gửi `coverImageUrl = ""` nếu muốn xoá ảnh bìa hiện tại (khớp updatePost/updateBlog).
        val req = UpdateContentRequest(
            title = title,
            contentHtml = contentHtml,
            coverImageUrl = coverImageUrl?.trim().orEmpty(),
            hashtags = hashtags,
        )
        when (type) {
            ContentType.POST -> contentApi.updatePost(id, req)
            ContentType.BLOG -> contentApi.updateBlog(id, req)
        }
        Unit
    }

    suspend fun deleteContent(type: ContentType, id: String): Result<Unit> = safeApiCall {
        when (type) {
            ContentType.POST -> contentApi.deletePost(id)
            ContentType.BLOG -> contentApi.deleteBlog(id)
        }
        Unit
    }

    suspend fun setPostPinned(id: String, pinned: Boolean): Result<PinPostResponse> = safeApiCall {
        if (pinned) contentApi.pinPost(id) else contentApi.unpinPost(id)
    }
}

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        val mapped: AppError = BackendErrorParser.fromThrowable(t)
        Result.failure(mapped)
    }
}
