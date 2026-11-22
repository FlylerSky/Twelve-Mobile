package com.example.data.model

import com.example.core.util.TolerantTimestampSerializer
import kotlinx.serialization.Serializable

enum class ContentType {
    POST,
    BLOG,
}

enum class ReactionType(val raw: String) {
    LIKE("like"),
    DISLIKE("dislike");

    companion object {
        fun fromRaw(raw: String?): ReactionType? = when (raw?.trim()?.lowercase()) {
            "like" -> LIKE
            "dislike" -> DISLIKE
            else -> null
        }
    }
}

/**
 * Tác giả rút gọn đính kèm trên thẻ bài viết, chi tiết và bình luận.
 * Nguồn đối chiếu: backend/src/utils/helpers.js#toPublicUser.
 */
@Serializable
data class PublicAuthor(
    val uid: String = "",
    val displayName: String? = null,
    val tagName: String? = null,
    val avatarUrl: String? = null,
    val accountGroup: String? = null,
    val accountRole: String? = null,
    val country: String? = null,
    val isLegacyMember: Boolean = false,
    val legacySince: String? = null,
    val badges: List<String> = emptyList(),
    @Serializable(with = TolerantTimestampSerializer::class)
    val createdAt: String? = null,
)

/**
 * Dữ liệu thẻ bài viết / blog dùng chung trên Feed, Blog, Profile, Search, HashtagPosts.
 * Nguồn đối chiếu: backend/src/utils/contentFormat.js#formatCardContent,
 * backend/src/controllers/trending.controller.js#formatContentCards,
 * backend/src/utils/profilePosts.js#buildProfileCards.
 */
@Serializable
data class ContentCard(
    val id: String = "",
    val type: String? = null,
    val detailPath: String? = null,
    val author: PublicAuthor? = null,
    val title: String = "",
    val excerpt: String = "",
    val coverImageUrl: String? = null,
    val hashtags: List<String> = emptyList(),
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    val aiViewCount: Int = 0,
    val shareCount: Int = 0,
    val postScore: Int = 0,
    val myReaction: String? = null,
    val isPinned: Boolean = false,
    @Serializable(with = TolerantTimestampSerializer::class)
    val createdAt: String? = null,
) {
    val contentType: ContentType
        get() = if (type == "blog" || detailPath == "/blog") ContentType.BLOG else ContentType.POST

    val reaction: ReactionType?
        get() = ReactionType.fromRaw(myReaction)
}

/**
 * Chi tiết đầy đủ bài viết hoặc blog trả về từ GET /api/posts/:id hoặc GET /api/blogs/:id.
 * Nguồn đối chiếu: backend/src/utils/contentFormat.js#formatFullContent,
 * posts.controller.js#getPost, blog.controller.js#getBlog.
 */
@Serializable
data class FullContent(
    val id: String = "",
    val author: PublicAuthor? = null,
    val title: String = "",
    val contentHtml: String = "",
    val excerpt: String = "",
    val coverImageUrl: String? = null,
    val hashtags: List<String> = emptyList(),
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    val aiViewCount: Int = 0,
    val shareCount: Int = 0,
    val postScore: Int = 0,
    val isPublished: Boolean = true,
    @Serializable(with = TolerantTimestampSerializer::class)
    val createdAt: String? = null,
    @Serializable(with = TolerantTimestampSerializer::class)
    val updatedAt: String? = null,
)

@Serializable
data class ListPostsResponse(
    val posts: List<ContentCard> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ListBlogsResponse(
    val blogs: List<ContentCard> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class GetPostResponse(
    val post: FullContent,
    val myReaction: String? = null,
)

@Serializable
data class GetBlogResponse(
    val blog: FullContent,
    val myReaction: String? = null,
)

@Serializable
data class CommentItem(
    val id: String = "",
    val content: String = "",
    @Serializable(with = TolerantTimestampSerializer::class)
    val createdAt: String? = null,
    @Serializable(with = TolerantTimestampSerializer::class)
    val updatedAt: String? = null,
    val isEdited: Boolean = false,
    val author: PublicAuthor? = null,
)

@Serializable
data class ListCommentsResponse(
    val comments: List<CommentItem> = emptyList(),
    val nextCursor: String? = null,
    val serverTimeMs: Long? = null,
)

@Serializable
data class ReactRequest(
    val type: String,
)

@Serializable
data class ReactResponse(
    val myReaction: String? = null,
)

@Serializable
data class ShareResponse(
    val shareUrl: String,
    val note: String? = null,
)

@Serializable
data class CommentMutationRequest(
    val content: String,
)

@Serializable
data class AddCommentResponse(
    val message: String? = null,
    val commentId: String,
)

@Serializable
data class MessageResponse(
    val message: String? = null,
)

@Serializable
data class CreateContentRequest(
    val title: String,
    val contentHtml: String,
    val coverImageUrl: String? = null,
    val hashtags: List<String> = emptyList(),
)

@Serializable
data class UpdateContentRequest(
    val title: String? = null,
    val contentHtml: String? = null,
    val coverImageUrl: String? = null,
    val hashtags: List<String>? = null,
    val isPublished: Boolean? = null,
)

@Serializable
data class CreatePostResponse(
    val message: String? = null,
    val postId: String,
)

@Serializable
data class CreateBlogResponse(
    val message: String? = null,
    val blogId: String,
)

@Serializable
data class PinPostResponse(
    val message: String? = null,
    val pinnedPostIds: List<String> = emptyList(),
    val changed: Boolean = false,
)
