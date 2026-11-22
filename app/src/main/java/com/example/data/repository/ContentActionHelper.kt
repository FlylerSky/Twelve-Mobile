package com.example.data.repository

import com.example.core.network.AppError
import com.example.core.util.PostScoreCalculator
import com.example.data.model.ContentCard
import com.example.data.model.ContentType
import com.example.data.model.ReactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trung tâm xử lý lạc quan + hoàn tác (rollback) cho reaction trên thẻ bài viết/blog,
 * tạo link chia sẻ và đồng bộ sự kiện sang các danh sách đang mở.
 *
 * Nguồn đối chiếu: frontend/src/hooks/useContentEngagement.js.
 */
@Singleton
class ContentActionHelper @Inject constructor(
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository,
    private val eventsBus: ContentEventsBus,
) {
    fun isSignedIn(): Boolean = authRepository.currentActiveProfile() != null

    suspend fun toggleReactionOnList(
        card: ContentCard,
        target: ReactionType,
        getCurrentList: () -> List<ContentCard>,
        updateList: (List<ContentCard>) -> Unit,
        onError: (String) -> Unit,
    ) {
        val prevReaction = card.reaction
        val nextReaction = if (prevReaction == target) null else target
        val likeDelta = (if (prevReaction == ReactionType.LIKE) -1 else 0) +
            (if (nextReaction == ReactionType.LIKE) 1 else 0)
        val dislikeDelta = (if (prevReaction == ReactionType.DISLIKE) -1 else 0) +
            (if (nextReaction == ReactionType.DISLIKE) 1 else 0)

        val optLikes = (card.likeCount + likeDelta).coerceAtLeast(0)
        val optDislikes = (card.dislikeCount + dislikeDelta).coerceAtLeast(0)
        val optScore = PostScoreCalculator.computePostScore(
            viewCount = card.viewCount,
            likeCount = optLikes,
            dislikeCount = optDislikes,
            commentCount = card.commentCount,
            shareCount = card.shareCount,
        )

        updateList(
            getCurrentList().map { item ->
                if (item.id == card.id && item.contentType == card.contentType) {
                    item.copy(
                        myReaction = nextReaction?.raw,
                        likeCount = optLikes,
                        dislikeCount = optDislikes,
                        postScore = optScore,
                    )
                } else {
                    item
                }
            },
        )

        contentRepository.react(card.contentType, card.id, target)
            .onSuccess { serverReaction ->
                val finalLikeDelta = (if (prevReaction == ReactionType.LIKE) -1 else 0) +
                    (if (serverReaction == ReactionType.LIKE) 1 else 0)
                val finalDislikeDelta = (if (prevReaction == ReactionType.DISLIKE) -1 else 0) +
                    (if (serverReaction == ReactionType.DISLIKE) 1 else 0)
                val finalLikes = (card.likeCount + finalLikeDelta).coerceAtLeast(0)
                val finalDislikes = (card.dislikeCount + finalDislikeDelta).coerceAtLeast(0)
                val finalScore = PostScoreCalculator.computePostScore(
                    viewCount = card.viewCount,
                    likeCount = finalLikes,
                    dislikeCount = finalDislikes,
                    commentCount = card.commentCount,
                    shareCount = card.shareCount,
                )
                updateList(
                    getCurrentList().map { item ->
                        if (item.id == card.id && item.contentType == card.contentType) {
                            item.copy(
                                myReaction = serverReaction?.raw,
                                likeCount = finalLikes,
                                dislikeCount = finalDislikes,
                                postScore = finalScore,
                            )
                        } else {
                            item
                        }
                    },
                )
                eventsBus.emit(
                    ContentEvent.EngagementChanged(
                        type = card.contentType,
                        id = card.id,
                        myReaction = serverReaction,
                        likeCount = finalLikes,
                        dislikeCount = finalDislikes,
                        postScore = finalScore,
                    ),
                )
                authRepository.refreshProfileBestEffort()
            }
            .onFailure { err ->
                // Hoàn tác về trạng thái trước khi bấm
                updateList(
                    getCurrentList().map { item ->
                        if (item.id == card.id && item.contentType == card.contentType) {
                            card
                        } else {
                            item
                        }
                    },
                )
                val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                onError(appErr.userMessage)
            }
    }

    suspend fun createShareLink(
        type: ContentType,
        id: String,
        onShareReady: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        contentRepository.createShareLink(type, id)
            .onSuccess { res -> onShareReady(res.shareUrl) }
            .onFailure { err ->
                val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                onError(appErr.userMessage)
            }
    }

    fun applyEvent(list: List<ContentCard>, event: ContentEvent): List<ContentCard> = when (event) {
        is ContentEvent.EngagementChanged -> list.map { item ->
            if (item.id == event.id && item.contentType == event.type) {
                item.copy(
                    myReaction = event.myReaction?.raw,
                    likeCount = event.likeCount,
                    dislikeCount = event.dislikeCount,
                    postScore = event.postScore,
                )
            } else {
                item
            }
        }
        is ContentEvent.CommentCountChanged -> list.map { item ->
            if (item.id == event.id && item.contentType == event.type) {
                item.copy(commentCount = event.commentCount, postScore = event.postScore)
            } else {
                item
            }
        }
        is ContentEvent.ContentUpdated -> list.map { item ->
            if (item.id == event.id && item.contentType == event.type) {
                item.copy(
                    title = event.title,
                    coverImageUrl = event.coverImageUrl,
                    hashtags = event.hashtags,
                    excerpt = event.excerpt ?: item.excerpt,
                )
            } else {
                item
            }
        }
        is ContentEvent.ContentDeleted -> list.filterNot { it.id == event.id && it.contentType == event.type }
        is ContentEvent.PostCreated, is ContentEvent.BlogCreated -> list
    }
}
