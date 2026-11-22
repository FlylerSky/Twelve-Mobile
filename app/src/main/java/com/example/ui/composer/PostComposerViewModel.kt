package com.example.ui.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.core.util.ContentLimits
import com.example.core.util.QuillHtmlCodec
import com.example.data.model.ContentType
import com.example.data.repository.AuthRepository
import com.example.data.repository.ContentEvent
import com.example.data.repository.ContentEventsBus
import com.example.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ComposerResultEvent {
    data class Created(val contentType: ContentType, val id: String) : ComposerResultEvent
    data class Updated(val contentType: ContentType, val id: String) : ComposerResultEvent
}

data class PostComposerUiState(
    val contentType: ContentType = ContentType.POST,
    val editContentId: String? = null,
    val isLoadingInitial: Boolean = false,
    val initialLoadError: AppError? = null,
    val title: String = "",
    val coverImageUrl: String = "",
    val hashtagsInput: String = "",
    val bodyText: String = "",
    val isPreviewMode: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val hasUserEdited: Boolean = false,
) {
    val isEditMode: Boolean get() = !editContentId.isNullOrBlank()
    val compiledHtml: String get() = QuillHtmlCodec.toQuillHtml(bodyText)
    val htmlAssessment: ContentLimits.HtmlAssessment get() = ContentLimits.assessContentHtml(compiledHtml)
    val hashtagValidation: ContentLimits.HashtagsValidation get() = ContentLimits.parseAndValidateHashtags(hashtagsInput)
    val titleError: String? get() = if (title.isEmpty() && !hasUserEdited) null else ContentLimits.validateTitle(title)
    val coverError: String? get() = ContentLimits.validateCoverUrl(coverImageUrl)
}

@HiltViewModel
class PostComposerViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository,
    private val eventsBus: ContentEventsBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostComposerUiState())
    val uiState: StateFlow<PostComposerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ComposerResultEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ComposerResultEvent> = _events.asSharedFlow()

    private var initializedKey: String? = null

    fun init(type: ContentType, editId: String?) {
        val key = "${type.name}:${editId.orEmpty()}"
        if (initializedKey == key) return
        initializedKey = key

        if (editId.isNullOrBlank()) {
            _uiState.value = PostComposerUiState(contentType = type, editContentId = null)
        } else {
            _uiState.value = PostComposerUiState(
                contentType = type,
                editContentId = editId,
                isLoadingInitial = true,
            )
            loadExisting(type, editId)
        }
    }

    fun retryLoadExisting() {
        val s = _uiState.value
        val id = s.editContentId ?: return
        loadExisting(s.contentType, id)
    }

    private fun loadExisting(type: ContentType, id: String) {
        _uiState.update { it.copy(isLoadingInitial = true, initialLoadError = null) }
        viewModelScope.launch {
            contentRepository.getContentDetail(type = type, id = id)
                .onSuccess { res ->
                    val content = res.content
                    _uiState.update {
                        it.copy(
                            isLoadingInitial = false,
                            initialLoadError = null,
                            title = content.title,
                            coverImageUrl = content.coverImageUrl.orEmpty(),
                            hashtagsInput = content.hashtags.joinToString(" "),
                            bodyText = QuillHtmlCodec.fromHtmlToEditorText(content.contentHtml),
                            hasUserEdited = false,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingInitial = false,
                            initialLoadError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun onTitleChanged(value: String) {
        _uiState.update { it.copy(title = value, hasUserEdited = true, submitError = null) }
    }

    fun onCoverImageUrlChanged(value: String) {
        _uiState.update { it.copy(coverImageUrl = value, hasUserEdited = true, submitError = null) }
    }

    fun onHashtagsChanged(value: String) {
        _uiState.update { it.copy(hashtagsInput = value, hasUserEdited = true, submitError = null) }
    }

    fun onBodyChanged(value: String) {
        _uiState.update { it.copy(bodyText = value, hasUserEdited = true, submitError = null) }
    }

    fun togglePreview() {
        _uiState.update { it.copy(isPreviewMode = !it.isPreviewMode) }
    }

    fun clearSubmitError() {
        _uiState.update { it.copy(submitError = null) }
    }

    fun submit(emptyContentMessage: String) {
        val state = _uiState.value
        if (state.isSubmitting) return

        val titleErr = ContentLimits.validateTitle(state.title)
        if (titleErr != null) {
            _uiState.update { it.copy(hasUserEdited = true, submitError = titleErr) }
            return
        }
        val coverErr = ContentLimits.validateCoverUrl(state.coverImageUrl)
        if (coverErr != null) {
            _uiState.update { it.copy(submitError = coverErr) }
            return
        }
        val tagValidation = ContentLimits.parseAndValidateHashtags(state.hashtagsInput)
        if (tagValidation.error != null) {
            _uiState.update { it.copy(submitError = tagValidation.error) }
            return
        }
        val html = QuillHtmlCodec.toQuillHtml(state.bodyText)
        if (QuillHtmlCodec.isHtmlVisuallyEmpty(html)) {
            _uiState.update { it.copy(submitError = emptyContentMessage) }
            return
        }
        val assess = ContentLimits.assessContentHtml(html)
        if (assess.severity == ContentLimits.Severity.ERROR) {
            _uiState.update { it.copy(submitError = assess.message) }
            return
        }

        val cleanTitle = state.title.trim()
        val cleanCover = state.coverImageUrl.trim().takeIf { it.isNotEmpty() }

        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            if (state.editContentId.isNullOrBlank()) {
                val createRes = when (state.contentType) {
                    ContentType.POST -> contentRepository.createPost(cleanTitle, html, cleanCover, tagValidation.hashtags)
                    ContentType.BLOG -> contentRepository.createBlog(cleanTitle, html, cleanCover, tagValidation.hashtags)
                }
                createRes
                    .onSuccess { newId ->
                        _uiState.update { it.copy(isSubmitting = false, hasUserEdited = false) }
                        if (state.contentType == ContentType.POST) {
                            eventsBus.emit(ContentEvent.PostCreated(newId))
                        } else {
                            eventsBus.emit(ContentEvent.BlogCreated(newId))
                        }
                        authRepository.refreshProfileBestEffort()
                        _events.tryEmit(ComposerResultEvent.Created(state.contentType, newId))
                    }
                    .onFailure { err ->
                        val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                        _uiState.update { it.copy(isSubmitting = false, submitError = appErr.userMessage) }
                    }
            } else {
                val id = state.editContentId
                contentRepository.updateContent(
                    type = state.contentType,
                    id = id,
                    title = cleanTitle,
                    contentHtml = html,
                    coverImageUrl = cleanCover,
                    hashtags = tagValidation.hashtags,
                ).onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, hasUserEdited = false) }
                    eventsBus.emit(
                        ContentEvent.ContentUpdated(
                            type = state.contentType,
                            id = id,
                            title = cleanTitle,
                            coverImageUrl = cleanCover,
                            hashtags = tagValidation.hashtags,
                        ),
                    )
                    _events.tryEmit(ComposerResultEvent.Updated(state.contentType, id))
                }.onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update { it.copy(isSubmitting = false, submitError = appErr.userMessage) }
                }
            }
        }
    }
}
