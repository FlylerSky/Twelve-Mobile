package com.example.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.data.model.ContentCard
import com.example.data.model.ContentType
import com.example.data.model.ReactionType
import com.example.data.repository.ContentActionHelper
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

sealed interface FeedSideEffect {
    data class ShowMessage(val message: String) : FeedSideEffect
    data class OpenShareSheet(val title: String, val shareUrl: String) : FeedSideEffect
}

data class FeedUiState(
    val contentType: ContentType = ContentType.POST,
    val items: List<ContentCard> = emptyList(),
    val nextCursor: String? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: AppError? = null,
    val loadMoreError: AppError? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    savedStateHandle: SavedStateHandle,
    private val actionHelper: ContentActionHelper? = null,
    private val eventsBus: ContentEventsBus? = null,
) : ViewModel() {

    private val initialType = if (savedStateHandle.get<String>("type") == "blog") {
        ContentType.BLOG
    } else {
        ContentType.POST
    }

    private val _uiState = MutableStateFlow(FeedUiState(contentType = initialType))
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<FeedSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<FeedSideEffect> = _sideEffects.asSharedFlow()

    init {
        loadInitial()
        eventsBus?.let { bus ->
            viewModelScope.launch {
                bus.events.collect { event ->
                    when (event) {
                        is ContentEvent.PostCreated -> {
                            if (_uiState.value.contentType == ContentType.POST) refresh()
                        }
                        is ContentEvent.BlogCreated -> {
                            if (_uiState.value.contentType == ContentType.BLOG) refresh()
                        }
                        else -> {
                            actionHelper?.let { helper ->
                                _uiState.update { it.copy(items = helper.applyEvent(it.items, event)) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setContentType(type: ContentType) {
        if (_uiState.value.contentType == type && !_uiState.value.isInitialLoading) return
        _uiState.value = FeedUiState(contentType = type)
        loadInitial()
    }

    fun loadInitial() {
        val type = _uiState.value.contentType
        _uiState.update { it.copy(isInitialLoading = true, error = null) }
        viewModelScope.launch {
            fetchPage(type = type, cursor = null)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            items = page.items,
                            nextCursor = page.nextCursor,
                            isInitialLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            error = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        val type = _uiState.value.contentType
        _uiState.update { it.copy(isRefreshing = true, loadMoreError = null) }
        viewModelScope.launch {
            fetchPage(type = type, cursor = null)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            items = page.items,
                            nextCursor = page.nextCursor,
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    val appErr = err as? AppError ?: AppError.Unknown(cause = err)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = if (it.items.isEmpty()) appErr else null,
                            loadMoreError = if (it.items.isNotEmpty()) appErr else null,
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore || state.isInitialLoading || state.isRefreshing) return

        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            fetchPage(type = state.contentType, cursor = cursor)
                .onSuccess { page ->
                    _uiState.update { current ->
                        val existingIds = current.items.asSequence().map { it.id }.toHashSet()
                        val merged = current.items + page.items.filterNot { it.id in existingIds }
                        current.copy(
                            items = merged,
                            nextCursor = page.nextCursor,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            loadMoreError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun react(card: ContentCard, reaction: ReactionType) {
        val helper = actionHelper ?: return
        viewModelScope.launch {
            helper.toggleReactionOnList(
                card = card,
                target = reaction,
                getCurrentList = { _uiState.value.items },
                updateList = { next -> _uiState.update { it.copy(items = next) } },
                onError = { msg -> _sideEffects.tryEmit(FeedSideEffect.ShowMessage(msg)) },
            )
        }
    }

    fun share(card: ContentCard) {
        val helper = actionHelper ?: return
        viewModelScope.launch {
            helper.createShareLink(
                type = card.contentType,
                id = card.id,
                onShareReady = { url -> _sideEffects.tryEmit(FeedSideEffect.OpenShareSheet(card.title, url)) },
                onError = { msg -> _sideEffects.tryEmit(FeedSideEffect.ShowMessage(msg)) },
            )
        }
    }

    private suspend fun fetchPage(type: ContentType, cursor: String?) = when (type) {
        ContentType.POST -> contentRepository.listPosts(cursor = cursor)
        ContentType.BLOG -> contentRepository.listBlogs(cursor = cursor)
    }
}
