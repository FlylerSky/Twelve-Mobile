package com.example.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.core.util.ContentLimits
import com.example.core.util.PostScoreCalculator
import com.example.data.model.CommentItem
import com.example.data.model.ContentType
import com.example.data.model.FullContent
import com.example.data.model.PublicAuthor
import com.example.data.model.ReactionType
import com.example.data.remote.FirestoreContentObserver
import com.example.data.remote.LiveCommentChange
import com.example.data.repository.AuthRepository
import com.example.data.repository.ContentEvent
import com.example.data.repository.ContentEventsBus
import com.example.data.repository.ContentRepository
import com.example.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailSideEffect {
    data class ShowMessage(val message: String) : DetailSideEffect
    data class OpenShareSheet(val title: String, val shareUrl: String) : DetailSideEffect
    data object ContentDeleted : DetailSideEffect
}

data class ContentDetailUiState(
    val contentType: ContentType = ContentType.POST,
    val contentId: String = "",
    val content: FullContent? = null,
    val myReaction: ReactionType? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val isReacting: Boolean = false,
    val isSharing: Boolean = false,
    val isPinning: Boolean = false,
    val isDeletingContent: Boolean = false,
    val comments: List<CommentItem> = emptyList(),
    val commentsNextCursor: String? = null,
    val isLoadingComments: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val commentsError: AppError? = null,
    val commentInput: String = "",
    val isSendingComment: Boolean = false,
    val editingCommentId: String? = null,
    val isSavingCommentEdit: Boolean = false,
    val deletingCommentId: String? = null,
) {
    val hasMoreComments: Boolean get() = commentsNextCursor != null
}

@HiltViewModel
class ContentDetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val firestoreObserver: FirestoreContentObserver,
    private val eventsBus: ContentEventsBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentDetailUiState())
    val uiState: StateFlow<ContentDetailUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<DetailSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<DetailSideEffect> = _sideEffects.asSharedFlow()

    private var loadedKey: String? = null
    private var pendingSharerUid: String? = null
    private var pendingShareSig: String? = null
    private var pendingSharedFlag: Boolean = false

    private var commentsServerBaselineMs: Long? = null
    private var isUiStarted: Boolean = false
    private var docObserverJob: Job? = null
    private var commentsObserverJob: Job? = null
    private val fetchedAuthorCache = HashMap<String, PublicAuthor>()
    private val commentAuthorUids = HashMap<String, String>()

    init {
        viewModelScope.launch {
            eventsBus.events.collect { event ->
                val state = _uiState.value
                if (state.contentId.isBlank()) return@collect
                if (event is ContentEvent.ContentUpdated &&
                    event.type == state.contentType &&
                    event.id == state.contentId
                ) {
                    // Tải lại chi tiết nhẹ hoặc cập nhật tiêu đề/ảnh bìa/hashtag ngay (không ghi view lặp nếu reload không gọi,
                    // nhưng ở đây ta cập nhật các trường hiển thị cơ bản rồi người dùng có thể kéo làm mới nếu muốn HTML mới nhất;
                    // hoặc gọi getContentDetail một lần vì chính chủ vừa sửa bài).
                    loadContentOnly(state.contentType, state.contentId, null, null, false)
                }
            }
        }
    }

    fun openContent(
        type: ContentType,
        id: String,
        sharerUid: String? = null,
        shareSignature: String? = null,
        sharedFlag: Boolean = false,
    ) {
        val key = "${type.name}:$id"
        if (loadedKey == key) return
        loadedKey = key
        commentsServerBaselineMs = null
        pendingSharerUid = sharerUid
        pendingShareSig = shareSignature
        pendingSharedFlag = sharedFlag
        loadAll(type, id)
    }

    fun onUiStart() {
        isUiStarted = true
        attachRealtimeListenersIfReady()
    }

    fun onUiStop() {
        isUiStarted = false
        detachRealtimeListeners()
    }

    fun retry() {
        val state = _uiState.value
        if (state.contentId.isBlank()) return
        loadAll(state.contentType, state.contentId)
    }

    private fun loadAll(type: ContentType, id: String) {
        val idu = pendingSharerUid
        val sig = pendingShareSig
        val shared = pendingSharedFlag
        pendingSharerUid = null
        pendingShareSig = null
        pendingSharedFlag = false

        _uiState.update {
            it.copy(
                contentType = type,
                contentId = id,
                isLoading = true,
                error = null,
                isLoadingComments = true,
                commentsError = null,
            )
        }

        loadContentOnly(type, id, idu, sig, shared)
        loadCommentsInitial(type, id)
    }

    private fun loadContentOnly(
        type: ContentType,
        id: String,
        idu: String?,
        sig: String?,
        shared: Boolean,
    ) {
        viewModelScope.launch {
            contentRepository.getContentDetail(
                type = type,
                id = id,
                sharerUid = idu,
                shareSignature = sig,
                sharedFlag = shared,
            ).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        content = res.content,
                        myReaction = res.myReaction,
                        isLoading = false,
                        error = null,
                    )
                }
                attachRealtimeListenersIfReady()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = err as? AppError ?: AppError.Unknown(cause = err),
                    )
                }
            }
        }
    }

    private fun loadCommentsInitial(type: ContentType, id: String) {
        viewModelScope.launch {
            contentRepository.listComments(type = type, id = id, cursor = null)
                .onSuccess { res ->
                    commentsServerBaselineMs = res.serverTimeMs ?: System.currentTimeMillis()
                    _uiState.update {
                        it.copy(
                            comments = res.comments,
                            commentsNextCursor = res.nextCursor,
                            isLoadingComments = false,
                            commentsError = null,
                        )
                    }
                    attachRealtimeListenersIfReady()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingComments = false,
                            commentsError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadMoreComments() {
        val state = _uiState.value
        val cursor = state.commentsNextCursor ?: return
        if (state.isLoadingComments || state.isLoadingMoreComments) return

        _uiState.update { it.copy(isLoadingMoreComments = true, commentsError = null) }
        viewModelScope.launch {
            contentRepository.listComments(
                type = state.contentType,
                id = state.contentId,
                cursor = cursor,
            ).onSuccess { res ->
                _uiState.update { current ->
                    val seen = current.comments.asSequence().map { it.id }.toHashSet()
                    val fresh = res.comments.filterNot { it.id in seen }
                    val exhausted = res.comments.isNotEmpty() && fresh.isEmpty()
                    current.copy(
                        comments = current.comments + fresh,
                        commentsNextCursor = if (exhausted) null else res.nextCursor,
                        isLoadingMoreComments = false,
                        commentsError = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingMoreComments = false,
                        commentsError = err as? AppError ?: AppError.Unknown(cause = err),
                    )
                }
            }
        }
    }

    fun react(target: ReactionType) {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.isReacting) return

        val prevReaction = state.myReaction
        val nextReaction = if (prevReaction == target) null else target
        val likeDelta = (if (prevReaction == ReactionType.LIKE) -1 else 0) +
            (if (nextReaction == ReactionType.LIKE) 1 else 0)
        val dislikeDelta = (if (prevReaction == ReactionType.DISLIKE) -1 else 0) +
            (if (nextReaction == ReactionType.DISLIKE) 1 else 0)

        val optLikes = (content.likeCount + likeDelta).coerceAtLeast(0)
        val optDislikes = (content.dislikeCount + dislikeDelta).coerceAtLeast(0)
        val optScore = PostScoreCalculator.computePostScore(
            viewCount = content.viewCount,
            likeCount = optLikes,
            dislikeCount = optDislikes,
            commentCount = content.commentCount,
            shareCount = content.shareCount,
        )

        _uiState.update {
            it.copy(
                isReacting = true,
                myReaction = nextReaction,
                content = content.copy(
                    likeCount = optLikes,
                    dislikeCount = optDislikes,
                    postScore = optScore,
                ),
            )
        }

        viewModelScope.launch {
            contentRepository.react(state.contentType, content.id, target)
                .onSuccess { serverReaction ->
                    val curContent = _uiState.value.content ?: content
                    val finalLikeDelta = (if (prevReaction == ReactionType.LIKE) -1 else 0) +
                        (if (serverReaction == ReactionType.LIKE) 1 else 0)
                    val finalDislikeDelta = (if (prevReaction == ReactionType.DISLIKE) -1 else 0) +
                        (if (serverReaction == ReactionType.DISLIKE) 1 else 0)
                    val finalLikes = (content.likeCount + finalLikeDelta).coerceAtLeast(0)
                    val finalDislikes = (content.dislikeCount + finalDislikeDelta).coerceAtLeast(0)
                    val finalScore = PostScoreCalculator.computePostScore(
                        viewCount = curContent.viewCount,
                        likeCount = finalLikes,
                        dislikeCount = finalDislikes,
                        commentCount = curContent.commentCount,
                        shareCount = curContent.shareCount,
                    )
                    _uiState.update {
                        it.copy(
                            isReacting = false,
                            myReaction = serverReaction,
                            content = curContent.copy(
                                likeCount = finalLikes,
                                dislikeCount = finalDislikes,
                                postScore = finalScore,
                            ),
                        )
                    }
                    eventsBus.emit(
                        ContentEvent.EngagementChanged(
                            type = state.contentType,
                            id = content.id,
                            myReaction = serverReaction,
                            likeCount = finalLikes,
                            dislikeCount = finalDislikes,
                            postScore = finalScore,
                        ),
                    )
                    authRepository.refreshProfileBestEffort()
                }
                .onFailure { err ->
                    // Hoàn tác về trạng thái trước khi bấm nếu API lỗi (429 / offline / ACCOUNT_NOT_ACTIVE)
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { cur ->
                        cur.copy(
                            isReacting = false,
                            myReaction = prevReaction,
                            content = cur.content?.copy(
                                likeCount = content.likeCount,
                                dislikeCount = content.dislikeCount,
                                postScore = content.postScore,
                            ),
                        )
                    }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(appErr.userMessage))
                }
        }
    }

    fun share() {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.isSharing) return

        _uiState.update { it.copy(isSharing = true) }
        viewModelScope.launch {
            contentRepository.createShareLink(state.contentType, content.id)
                .onSuccess { res ->
                    _uiState.update { it.copy(isSharing = false) }
                    _sideEffects.tryEmit(DetailSideEffect.OpenShareSheet(content.title, res.shareUrl))
                }
                .onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { it.copy(isSharing = false) }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(appErr.userMessage))
                }
        }
    }

    fun onCommentInputChanged(value: String) {
        _uiState.update { it.copy(commentInput = value) }
    }

    fun sendComment() {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.isSendingComment) return

        val raw = state.commentInput
        val validationError = ContentLimits.validateComment(raw)
        if (validationError != null) {
            _sideEffects.tryEmit(DetailSideEffect.ShowMessage(validationError))
            return
        }
        val cleanText = raw.trim()
        _uiState.update { it.copy(isSendingComment = true) }

        viewModelScope.launch {
            contentRepository.addComment(state.contentType, content.id, cleanText)
                .onSuccess { newCommentId ->
                    val ownAuthor = authRepository.currentActiveProfile()?.let { me ->
                        PublicAuthor(
                            uid = me.uid,
                            displayName = me.displayName,
                            tagName = me.tagName?.let { "@${it.removePrefix("@")}" },
                            avatarUrl = me.avatarUrl,
                            accountGroup = me.accountGroup,
                            accountRole = me.accountRole,
                            country = me.country,
                            isLegacyMember = me.isLegacyMember,
                            badges = me.badges,
                        )
                    }
                    val nowIso = java.time.Instant.now().toString()
                    val newItem = CommentItem(
                        id = newCommentId,
                        content = cleanText,
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        isEdited = false,
                        author = ownAuthor,
                    )
                    _uiState.update { cur ->
                        val curContent = cur.content
                        val nextCount = (curContent?.commentCount ?: 0) + 1
                        val nextScore = if (curContent != null) {
                            PostScoreCalculator.computePostScore(
                                viewCount = curContent.viewCount,
                                likeCount = curContent.likeCount,
                                dislikeCount = curContent.dislikeCount,
                                commentCount = nextCount,
                                shareCount = curContent.shareCount,
                            )
                        } else {
                            0
                        }
                        val alreadyPresent = cur.comments.any { it.id == newCommentId }
                        val nextComments = if (alreadyPresent || cur.hasMoreComments) {
                            cur.comments
                        } else {
                            cur.comments + newItem
                        }
                        cur.copy(
                            commentInput = "",
                            isSendingComment = false,
                            comments = nextComments,
                            content = curContent?.copy(commentCount = nextCount, postScore = nextScore),
                        )
                    }
                    val updated = _uiState.value.content
                    if (updated != null) {
                        eventsBus.emit(
                            ContentEvent.CommentCountChanged(
                                type = state.contentType,
                                id = updated.id,
                                commentCount = updated.commentCount,
                                postScore = updated.postScore,
                            ),
                        )
                    }
                    authRepository.refreshProfileBestEffort()
                }
                .onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { it.copy(isSendingComment = false) }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(appErr.userMessage))
                }
        }
    }

    fun editComment(commentId: String, newText: String, successMessage: String) {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.isSavingCommentEdit) return

        val validationError = ContentLimits.validateComment(newText)
        if (validationError != null) {
            _sideEffects.tryEmit(DetailSideEffect.ShowMessage(validationError))
            return
        }
        val cleanText = newText.trim()
        _uiState.update { it.copy(isSavingCommentEdit = true, editingCommentId = commentId) }
        viewModelScope.launch {
            contentRepository.editComment(state.contentType, content.id, commentId, cleanText)
                .onSuccess {
                    _uiState.update { cur ->
                        cur.copy(
                            isSavingCommentEdit = false,
                            editingCommentId = null,
                            comments = cur.comments.map { item ->
                                if (item.id == commentId) {
                                    item.copy(
                                        content = cleanText,
                                        isEdited = true,
                                        updatedAt = java.time.Instant.now().toString(),
                                    )
                                } else {
                                    item
                                }
                            },
                        )
                    }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(successMessage))
                }
                .onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { it.copy(isSavingCommentEdit = false, editingCommentId = null) }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(appErr.userMessage))
                }
        }
    }

    fun deleteComment(commentId: String, successMessage: String) {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.deletingCommentId != null) return

        _uiState.update { it.copy(deletingCommentId = commentId) }
        viewModelScope.launch {
            contentRepository.deleteComment(state.contentType, content.id, commentId)
                .onSuccess {
                    _uiState.update { cur ->
                        val curContent = cur.content
                        val nextCount = ((curContent?.commentCount ?: 1) - 1).coerceAtLeast(0)
                        val nextScore = if (curContent != null) {
                            PostScoreCalculator.computePostScore(
                                viewCount = curContent.viewCount,
                                likeCount = curContent.likeCount,
                                dislikeCount = curContent.dislikeCount,
                                commentCount = nextCount,
                                shareCount = curContent.shareCount,
                            )
                        } else {
                            0
                        }
                        cur.copy(
                            deletingCommentId = null,
                            comments = cur.comments.filterNot { it.id == commentId },
                            content = curContent?.copy(commentCount = nextCount, postScore = nextScore),
                        )
                    }
                    val updated = _uiState.value.content
                    if (updated != null) {
                        eventsBus.emit(
                            ContentEvent.CommentCountChanged(
                                type = state.contentType,
                                id = updated.id,
                                commentCount = updated.commentCount,
                                postScore = updated.postScore,
                            ),
                        )
                    }
                    authRepository.refreshProfileBestEffort()
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(successMessage))
                }
                .onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { it.copy(deletingCommentId = null) }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(appErr.userMessage))
                }
        }
    }

    fun togglePin(currentlyPinned: Boolean, pinnedOkMsg: String, unpinnedOkMsg: String) {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.contentType != ContentType.POST || state.isPinning) return

        val targetPin = !currentlyPinned
        _uiState.update { it.copy(isPinning = true) }
        viewModelScope.launch {
            contentRepository.setPostPinned(content.id, targetPin)
                .onSuccess { res ->
                    _uiState.update { it.copy(isPinning = false) }
                    authRepository.updatePinnedPostIds(res.pinnedPostIds)
                    _sideEffects.tryEmit(
                        DetailSideEffect.ShowMessage(if (targetPin) pinnedOkMsg else unpinnedOkMsg),
                    )
                }
                .onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { it.copy(isPinning = false) }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(appErr.userMessage))
                }
        }
    }

    fun deleteContent(deletedOkMsg: String) {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.isDeletingContent) return

        _uiState.update { it.copy(isDeletingContent = true) }
        viewModelScope.launch {
            contentRepository.deleteContent(state.contentType, content.id)
                .onSuccess {
                    _uiState.update { it.copy(isDeletingContent = false) }
                    eventsBus.emit(ContentEvent.ContentDeleted(state.contentType, content.id))
                    authRepository.refreshProfileBestEffort()
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(deletedOkMsg))
                    _sideEffects.tryEmit(DetailSideEffect.ContentDeleted)
                }
                .onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { it.copy(isDeletingContent = false) }
                    _sideEffects.tryEmit(DetailSideEffect.ShowMessage(appErr.userMessage))
                }
        }
    }

    private fun attachRealtimeListenersIfReady() {
        if (!isUiStarted) return
        val state = _uiState.value
        val id = state.contentId.takeIf { it.isNotBlank() } ?: return
        val loadedContent = state.content ?: return

        if (docObserverJob == null && loadedContent.isPublished) {
            docObserverJob = viewModelScope.launch {
                firestoreObserver.observeContentDoc(state.contentType, id).collect { live ->
                    _uiState.update { cur ->
                        val c = cur.content ?: return@update cur
                        // Không bao giờ ghi đè `myReaction` từ snapshot document công khai.
                        cur.copy(
                            content = c.copy(
                                likeCount = live.likeCount ?: c.likeCount,
                                dislikeCount = live.dislikeCount ?: c.dislikeCount,
                                commentCount = live.commentCount ?: c.commentCount,
                                shareCount = live.shareCount ?: c.shareCount,
                                viewCount = live.viewCount ?: c.viewCount,
                                aiViewCount = live.aiViewCount ?: c.aiViewCount,
                                postScore = live.postScore ?: c.postScore,
                            ),
                        )
                    }
                }
            }
        }

        val baseline = commentsServerBaselineMs
        if (commentsObserverJob == null && baseline != null && loadedContent.isPublished) {
            commentsObserverJob = viewModelScope.launch {
                firestoreObserver.observeRecentComments(state.contentType, id, baseline).collect { batch ->
                    val unseenAuthorUids = HashSet<String>()
                    _uiState.update { cur ->
                        val currentMap = cur.comments.associateBy { it.id }.toMutableMap()
                        var changed = false

                        val removals = HashSet<String>()
                        val upserts = mutableListOf<LiveCommentChange.Upsert>()
                        batch.changes.forEach { change ->
                            when (change) {
                                is LiveCommentChange.Removed -> if (!batch.windowFull) removals.add(change.id)
                                is LiveCommentChange.Upsert -> upserts.add(change)
                            }
                        }

                        var updatedList = if (removals.isNotEmpty()) {
                            changed = true
                            cur.comments.filterNot { it.id in removals }
                        } else {
                            cur.comments
                        }

                        // 1. Vá các bình luận đã có trên màn hình
                        val toAppend = mutableListOf<CommentItem>()
                        for (upsert in upserts) {
                            val existing = currentMap[upsert.id]
                            if (existing != null) {
                                if (existing.content != upsert.content || existing.isEdited != upsert.isEdited) {
                                    changed = true
                                    updatedList = updatedList.map { item ->
                                        if (item.id == upsert.id) {
                                            item.copy(
                                                content = upsert.content,
                                                isEdited = upsert.isEdited,
                                                updatedAt = upsert.updatedAtIso ?: item.updatedAt,
                                            )
                                        } else {
                                            item
                                        }
                                    }
                                }
                            } else if (!cur.hasMoreComments) {
                                // Chỉ chèn bình luận mới khi đã tải tới trang cuối để tránh khoảng trống
                                changed = true
                                val uid = upsert.authorUid.orEmpty()
                                val cachedAuthor = resolveKnownAuthor(uid)
                                if (cachedAuthor == null && uid.isNotBlank()) {
                                    commentAuthorUids[upsert.id] = uid
                                    unseenAuthorUids.add(uid)
                                }
                                toAppend.add(
                                    CommentItem(
                                        id = upsert.id,
                                        content = upsert.content,
                                        createdAt = upsert.createdAtIso,
                                        updatedAt = upsert.updatedAtIso,
                                        isEdited = upsert.isEdited,
                                        author = cachedAuthor,
                                    ),
                                )
                            }
                        }

                        if (toAppend.isNotEmpty()) {
                            val sortedAppends = toAppend.sortedBy { it.createdAt.orEmpty() }
                            updatedList = updatedList + sortedAppends
                        }

                        if (changed) cur.copy(comments = updatedList) else cur
                    }

                    // Bù hồ sơ tác giả công khai cho các bình luận mới xuất hiện từ người dùng khác
                    for (uid in unseenAuthorUids) {
                        launch {
                            userRepository.getPublicProfileByUid(uid).onSuccess { pub ->
                                val author = PublicAuthor(
                                    uid = pub.uid,
                                    displayName = pub.displayName,
                                    tagName = pub.tagName,
                                    avatarUrl = pub.avatarUrl,
                                    accountGroup = pub.accountGroup,
                                    accountRole = pub.accountRole,
                                    country = pub.country,
                                    isLegacyMember = pub.isLegacyMember,
                                    legacySince = pub.legacySince,
                                    badges = pub.badges,
                                )
                                fetchedAuthorCache[uid] = author
                                _uiState.update { cur ->
                                    cur.copy(
                                        comments = cur.comments.map { item ->
                                            if (item.author == null && commentAuthorUids[item.id] == uid) {
                                                item.copy(author = author)
                                            } else {
                                                item
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveKnownAuthor(uid: String): PublicAuthor? {
        if (uid.isBlank()) return null
        val own = authRepository.currentActiveProfile()
        if (own != null && own.uid == uid) {
            return PublicAuthor(
                uid = own.uid,
                displayName = own.displayName,
                tagName = own.tagName?.let { "@${it.removePrefix("@")}" },
                avatarUrl = own.avatarUrl,
                accountGroup = own.accountGroup,
                accountRole = own.accountRole,
                country = own.country,
                isLegacyMember = own.isLegacyMember,
                badges = own.badges,
            )
        }
        return fetchedAuthorCache[uid]
    }

    private fun detachRealtimeListeners() {
        docObserverJob?.cancel()
        docObserverJob = null
        commentsObserverJob?.cancel()
        commentsObserverJob = null
    }

    override fun onCleared() {
        detachRealtimeListeners()
        super.onCleared()
    }
}
