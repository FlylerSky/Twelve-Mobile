package com.example.ui.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.data.model.ContentCard
import com.example.data.model.ReactionType
import com.example.data.repository.ContentActionHelper
import com.example.data.repository.ContentEventsBus
import com.example.data.repository.ScoreMode
import com.example.data.repository.TrendingRepository
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

sealed interface HashtagSideEffect {
    data class ShowMessage(val message: String) : HashtagSideEffect
    data class OpenShareSheet(val title: String, val shareUrl: String) : HashtagSideEffect
}

data class HashtagPostsUiState(
    val tag: String = "",
    val mode: ScoreMode = ScoreMode.CLASSIC,
    val displayTag: String = "",
    val posts: List<ContentCard> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

@HiltViewModel
class HashtagPostsViewModel @Inject constructor(
    private val trendingRepository: TrendingRepository,
    private val actionHelper: ContentActionHelper? = null,
    private val eventsBus: ContentEventsBus? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HashtagPostsUiState())
    val uiState: StateFlow<HashtagPostsUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<HashtagSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<HashtagSideEffect> = _sideEffects.asSharedFlow()

    private var loadedKey: String? = null

    init {
        eventsBus?.let { bus ->
            viewModelScope.launch {
                bus.events.collect { event ->
                    actionHelper?.let { helper ->
                        _uiState.update { it.copy(posts = helper.applyEvent(it.posts, event)) }
                    }
                }
            }
        }
    }

    fun load(tag: String, mode: ScoreMode) {
        val key = "${tag.lowercase()}:${mode.apiValue}"
        if (loadedKey == key) return
        loadedKey = key
        fetch(tag, mode)
    }

    fun selectMode(mode: ScoreMode) {
        val tag = _uiState.value.tag
        if (tag.isBlank() || _uiState.value.mode == mode) return
        loadedKey = "${tag.lowercase()}:${mode.apiValue}"
        fetch(tag, mode)
    }

    fun retry() {
        val s = _uiState.value
        if (s.tag.isNotBlank()) fetch(s.tag, s.mode)
    }

    private fun fetch(tag: String, mode: ScoreMode) {
        _uiState.update {
            it.copy(
                tag = tag,
                mode = mode,
                displayTag = if (tag.startsWith("#")) tag else "#$tag",
                isLoading = true,
                error = null,
            )
        }
        viewModelScope.launch {
            trendingRepository.getHashtagPosts(tag, mode)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            displayTag = res.tag.ifBlank { it.displayTag },
                            posts = res.posts,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun react(card: ContentCard, target: ReactionType) {
        val helper = actionHelper ?: return
        viewModelScope.launch {
            helper.toggleReactionOnList(
                card = card,
                target = target,
                getCurrentList = { _uiState.value.posts },
                updateList = { next -> _uiState.update { it.copy(posts = next) } },
                onError = { msg -> _sideEffects.tryEmit(HashtagSideEffect.ShowMessage(msg)) },
            )
        }
    }

    fun share(card: ContentCard) {
        val helper = actionHelper ?: return
        viewModelScope.launch {
            helper.createShareLink(
                type = card.contentType,
                id = card.id,
                onShareReady = { url -> _sideEffects.tryEmit(HashtagSideEffect.OpenShareSheet(card.title, url)) },
                onError = { msg -> _sideEffects.tryEmit(HashtagSideEffect.ShowMessage(msg)) },
            )
        }
    }
}
