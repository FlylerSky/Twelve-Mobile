package com.example.ui.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.data.model.ContentCard
import com.example.data.model.HashtagRankingResponse
import com.example.data.model.ReactionType
import com.example.data.model.SearchTrendingResponse
import com.example.data.model.UsersRankingResponse
import com.example.data.repository.ContentActionHelper
import com.example.data.repository.ContentEventsBus
import com.example.data.repository.ScoreMode
import com.example.data.repository.SearchSort
import com.example.data.repository.SearchType
import com.example.data.repository.TrendingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TrendingSideEffect {
    data class ShowMessage(val message: String) : TrendingSideEffect
    data class OpenShareSheet(val title: String, val shareUrl: String) : TrendingSideEffect
}

enum class TrendingMainTab {
    USERS,
    HASHTAGS,
}

enum class UsersPeriodTab {
    WEEKLY,
    ALLTIME,
}

enum class HashtagSectionTab {
    WEEKLY,
    EXPLORE,
    SEARCH,
}

data class TrendingUiState(
    val mainTab: TrendingMainTab = TrendingMainTab.USERS,
    val usersPeriod: UsersPeriodTab = UsersPeriodTab.WEEKLY,
    val weeklyUsers: UsersRankingResponse? = null,
    val isLoadingWeeklyUsers: Boolean = false,
    val weeklyUsersError: AppError? = null,
    val allTimeUsers: UsersRankingResponse? = null,
    val isLoadingAllTimeUsers: Boolean = false,
    val allTimeUsersError: AppError? = null,
    val hashtagMode: ScoreMode = ScoreMode.CLASSIC,
    val hashtagSection: HashtagSectionTab = HashtagSectionTab.WEEKLY,
    val hashtagWeeklyByMode: Map<ScoreMode, HashtagRankingResponse> = emptyMap(),
    val isLoadingHashtagWeekly: Boolean = false,
    val hashtagWeeklyError: AppError? = null,
    val hashtagAllTimeByMode: Map<ScoreMode, HashtagRankingResponse> = emptyMap(),
    val isLoadingHashtagAllTime: Boolean = false,
    val hashtagAllTimeError: AppError? = null,
    val searchQuery: String = "",
    val searchType: SearchType = SearchType.POST,
    val searchSort: SearchSort = SearchSort.NEWEST,
    val searchResult: SearchTrendingResponse? = null,
    val isSearching: Boolean = false,
    val searchError: AppError? = null,
)

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val trendingRepository: TrendingRepository,
    private val actionHelper: ContentActionHelper? = null,
    private val eventsBus: ContentEventsBus? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendingUiState())
    val uiState: StateFlow<TrendingUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<TrendingSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<TrendingSideEffect> = _sideEffects.asSharedFlow()

    private var searchJob: Job? = null

    init {
        loadWeeklyUsers()
        eventsBus?.let { bus ->
            viewModelScope.launch {
                bus.events.collect { event ->
                    val helper = actionHelper ?: return@collect
                    _uiState.update { cur ->
                        val sr = cur.searchResult ?: return@update cur
                        cur.copy(searchResult = sr.copy(posts = helper.applyEvent(sr.posts, event)))
                    }
                }
            }
        }
    }

    fun selectMainTab(tab: TrendingMainTab) {
        _uiState.update { it.copy(mainTab = tab) }
        ensureDataLoaded()
    }

    fun selectUsersPeriod(period: UsersPeriodTab) {
        _uiState.update { it.copy(usersPeriod = period) }
        ensureDataLoaded()
    }

    fun selectHashtagMode(mode: ScoreMode) {
        _uiState.update { it.copy(hashtagMode = mode) }
        val s = _uiState.value
        if (s.hashtagSection == HashtagSectionTab.SEARCH && s.searchQuery.isNotBlank()) {
            triggerSearch(debounceMs = 0L)
        } else {
            ensureDataLoaded()
        }
    }

    fun selectHashtagSection(section: HashtagSectionTab) {
        _uiState.update { it.copy(hashtagSection = section) }
        ensureDataLoaded()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchError = null) }
        if (query.isBlank()) {
            searchJob?.cancel()
            _uiState.update { it.copy(searchResult = null, isSearching = false, searchError = null) }
        } else {
            triggerSearch(debounceMs = 400L)
        }
    }

    fun onSearchTypeChanged(type: SearchType) {
        _uiState.update { it.copy(searchType = type) }
        if (_uiState.value.searchQuery.isNotBlank()) triggerSearch(debounceMs = 0L)
    }

    fun onSearchSortChanged(sort: SearchSort) {
        _uiState.update { it.copy(searchSort = sort) }
        if (_uiState.value.searchQuery.isNotBlank()) triggerSearch(debounceMs = 0L)
    }

    fun retryCurrent() {
        val state = _uiState.value
        when (state.mainTab) {
            TrendingMainTab.USERS -> when (state.usersPeriod) {
                UsersPeriodTab.WEEKLY -> loadWeeklyUsers(force = true)
                UsersPeriodTab.ALLTIME -> loadAllTimeUsers(force = true)
            }
            TrendingMainTab.HASHTAGS -> when (state.hashtagSection) {
                HashtagSectionTab.WEEKLY -> loadHashtagWeekly(state.hashtagMode, force = true)
                HashtagSectionTab.EXPLORE -> loadHashtagAllTime(state.hashtagMode, force = true)
                HashtagSectionTab.SEARCH -> triggerSearch(debounceMs = 0L)
            }
        }
    }

    private fun ensureDataLoaded() {
        val state = _uiState.value
        when (state.mainTab) {
            TrendingMainTab.USERS -> when (state.usersPeriod) {
                UsersPeriodTab.WEEKLY -> if (state.weeklyUsers == null && !state.isLoadingWeeklyUsers) loadWeeklyUsers()
                UsersPeriodTab.ALLTIME -> if (state.allTimeUsers == null && !state.isLoadingAllTimeUsers) loadAllTimeUsers()
            }
            TrendingMainTab.HASHTAGS -> when (state.hashtagSection) {
                HashtagSectionTab.WEEKLY ->
                    if (!state.hashtagWeeklyByMode.containsKey(state.hashtagMode) && !state.isLoadingHashtagWeekly) {
                        loadHashtagWeekly(state.hashtagMode)
                    }
                HashtagSectionTab.EXPLORE ->
                    if (!state.hashtagAllTimeByMode.containsKey(state.hashtagMode) && !state.isLoadingHashtagAllTime) {
                        loadHashtagAllTime(state.hashtagMode)
                    }
                HashtagSectionTab.SEARCH -> Unit
            }
        }
    }

    fun loadWeeklyUsers(force: Boolean = false) {
        if (!force && _uiState.value.isLoadingWeeklyUsers) return
        _uiState.update { it.copy(isLoadingWeeklyUsers = true, weeklyUsersError = null) }
        viewModelScope.launch {
            trendingRepository.getWeeklyUsers()
                .onSuccess { res ->
                    _uiState.update { it.copy(weeklyUsers = res, isLoadingWeeklyUsers = false, weeklyUsersError = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingWeeklyUsers = false,
                            weeklyUsersError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadAllTimeUsers(force: Boolean = false) {
        if (!force && _uiState.value.isLoadingAllTimeUsers) return
        _uiState.update { it.copy(isLoadingAllTimeUsers = true, allTimeUsersError = null) }
        viewModelScope.launch {
            trendingRepository.getAllTimeUsers()
                .onSuccess { res ->
                    _uiState.update { it.copy(allTimeUsers = res, isLoadingAllTimeUsers = false, allTimeUsersError = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingAllTimeUsers = false,
                            allTimeUsersError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadHashtagWeekly(mode: ScoreMode, force: Boolean = false) {
        if (!force && _uiState.value.isLoadingHashtagWeekly) return
        _uiState.update { it.copy(isLoadingHashtagWeekly = true, hashtagWeeklyError = null) }
        viewModelScope.launch {
            trendingRepository.getHashtagWeekly(mode)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            hashtagWeeklyByMode = it.hashtagWeeklyByMode + (mode to res),
                            isLoadingHashtagWeekly = false,
                            hashtagWeeklyError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingHashtagWeekly = false,
                            hashtagWeeklyError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadHashtagAllTime(mode: ScoreMode, force: Boolean = false) {
        if (!force && _uiState.value.isLoadingHashtagAllTime) return
        _uiState.update { it.copy(isLoadingHashtagAllTime = true, hashtagAllTimeError = null) }
        viewModelScope.launch {
            trendingRepository.getHashtagAllTime(mode)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            hashtagAllTimeByMode = it.hashtagAllTimeByMode + (mode to res),
                            isLoadingHashtagAllTime = false,
                            hashtagAllTimeError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingHashtagAllTime = false,
                            hashtagAllTimeError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    private fun triggerSearch(debounceMs: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snap = _uiState.value
            val query = snap.searchQuery.trim()
            if (query.isEmpty()) return@launch
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            trendingRepository.search(
                query = query,
                type = snap.searchType,
                sort = snap.searchSort,
                mode = snap.hashtagMode,
            ).onSuccess { res ->
                _uiState.update { it.copy(searchResult = res, isSearching = false, searchError = null) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = err as? AppError ?: AppError.Unknown(cause = err),
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
                getCurrentList = { _uiState.value.searchResult?.posts.orEmpty() },
                updateList = { updated ->
                    _uiState.update { cur ->
                        val sr = cur.searchResult ?: return@update cur
                        cur.copy(searchResult = sr.copy(posts = updated))
                    }
                },
                onError = { msg -> _sideEffects.tryEmit(TrendingSideEffect.ShowMessage(msg)) },
            )
        }
    }

    fun share(card: ContentCard) {
        val helper = actionHelper ?: return
        viewModelScope.launch {
            helper.createShareLink(
                type = card.contentType,
                id = card.id,
                onShareReady = { url -> _sideEffects.tryEmit(TrendingSideEffect.OpenShareSheet(card.title, url)) },
                onError = { msg -> _sideEffects.tryEmit(TrendingSideEffect.ShowMessage(msg)) },
            )
        }
    }
}
