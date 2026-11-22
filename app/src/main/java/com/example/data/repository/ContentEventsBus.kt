package com.example.data.repository

import com.example.data.model.ContentType
import com.example.data.model.ReactionType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface ContentEvent {
    data class PostCreated(val postId: String) : ContentEvent
    data class BlogCreated(val blogId: String) : ContentEvent
    data class ContentUpdated(
        val type: ContentType,
        val id: String,
        val title: String,
        val coverImageUrl: String?,
        val hashtags: List<String>,
        val excerpt: String? = null,
    ) : ContentEvent

    data class ContentDeleted(
        val type: ContentType,
        val id: String,
    ) : ContentEvent

    data class EngagementChanged(
        val type: ContentType,
        val id: String,
        val myReaction: ReactionType?,
        val likeCount: Int,
        val dislikeCount: Int,
        val postScore: Int,
    ) : ContentEvent

    data class CommentCountChanged(
        val type: ContentType,
        val id: String,
        val commentCount: Int,
        val postScore: Int,
    ) : ContentEvent
}

/**
 * Kênh sự kiện đồng bộ state giữa Feed, Profile, Trending và Chi tiết bài viết
 * khi người dùng tương tác, tạo/sửa/xoá bài viết mà không cần tải lại toàn danh sách.
 */
@Singleton
class ContentEventsBus @Inject constructor() {
    private val _events = MutableSharedFlow<ContentEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ContentEvent> = _events.asSharedFlow()

    fun emit(event: ContentEvent) {
        _events.tryEmit(event)
    }
}
