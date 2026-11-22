package com.example.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.data.model.ContentCard
import com.example.data.model.ExtraInfoResponse
import com.example.data.model.PublicProfile
import com.example.data.model.ReactionType
import com.example.data.repository.AuthRepository
import com.example.data.repository.ContentActionHelper
import com.example.data.repository.ContentEvent
import com.example.data.repository.ContentEventsBus
import com.example.data.repository.SessionState
import com.example.data.repository.UserRepository
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

sealed interface ProfileSideEffect {
    data class ShowMessage(val message: String) : ProfileSideEffect
    data class OpenShareSheet(val title: String, val shareUrl: String) : ProfileSideEffect
}

enum class ProfileTab {
    HOME,
    POSTS,
    BLOG,
}

sealed interface ProfileLookupKey {
    data class ByUid(val uid: String) : ProfileLookupKey
    data class ByTag(val tag: String) : ProfileLookupKey
}

data class ExtraInfoDialogState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val data: ExtraInfoResponse? = null,
    val error: AppError? = null,
)

data class PublicProfileUiState(
    val profile: PublicProfile? = null,
    val isOwnProfile: Boolean = false,
    val isLoadingProfile: Boolean = true,
    val profileError: AppError? = null,
    val selectedTab: ProfileTab = ProfileTab.HOME,
    val pinnedPosts: List<ContentCard> = emptyList(),
    val isLoadingPinned: Boolean = false,
    val pinnedError: AppError? = null,
    val userPosts: List<ContentCard> = emptyList(),
    val userPostsNextCursor: String? = null,
    val isLoadingUserPosts: Boolean = false,
    val isLoadingMorePosts: Boolean = false,
    val userPostsError: AppError? = null,
    val userBlogs: List<ContentCard> = emptyList(),
    val userBlogsNextCursor: String? = null,
    val isLoadingUserBlogs: Boolean = false,
    val isLoadingMoreBlogs: Boolean = false,
    val userBlogsError: AppError? = null,
    val extraDialog: ExtraInfoDialogState = ExtraInfoDialogState(),
)

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val actionHelper: ContentActionHelper? = null,
    private val eventsBus: ContentEventsBus? = null,
    private val authRepository: AuthRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublicProfileUiState())
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<ProfileSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<ProfileSideEffect> = _sideEffects.asSharedFlow()

    private var currentKey: ProfileLookupKey? = null
    private var currentViewerUid: String? = null
    private var visitRecordedForUid: String? = null
    private var loadedUserPostsForUid: String? = null
    private var loadedUserBlogsForUid: String? = null
    private var lastKnownOwnPinnedIds: List<String>? = null

    init {
        eventsBus?.let { bus ->
            viewModelScope.launch {
                bus.events.collect { event ->
                    val state = _uiState.value
                    val profile = state.profile
                    when (event) {
                        is ContentEvent.PostCreated -> {
                            if (state.isOwnProfile && profile != null && loadedUserPostsForUid == profile.uid) {
                                loadUserPostsInitial(profile.uid)
                            }
                        }
                        is ContentEvent.BlogCreated -> {
                            if (state.isOwnProfile && profile != null && loadedUserBlogsForUid == profile.uid) {
                                loadUserBlogsInitial(profile.uid)
                            }
                        }
                        else -> {
                            actionHelper?.let { helper ->
                                _uiState.update { cur ->
                                    cur.copy(
                                        pinnedPosts = helper.applyEvent(cur.pinnedPosts, event),
                                        userPosts = helper.applyEvent(cur.userPosts, event),
                                        userBlogs = helper.applyEvent(cur.userBlogs, event),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        authRepository?.let { auth ->
            viewModelScope.launch {
                auth.sessionState.collect { session ->
                    val active = (session as? SessionState.Active)?.profile ?: return@collect
                    val state = _uiState.value
                    val shown = state.profile
                    if (state.isOwnProfile && shown != null && shown.uid == active.uid) {
                        if (lastKnownOwnPinnedIds != null && lastKnownOwnPinnedIds != active.pinnedPostIds) {
                            lastKnownOwnPinnedIds = active.pinnedPostIds
                            loadPinned(shown.uid)
                        } else if (lastKnownOwnPinnedIds == null) {
                            lastKnownOwnPinnedIds = active.pinnedPostIds
                        }
                    }
                }
            }
        }
    }

    fun load(key: ProfileLookupKey, viewerUid: String?) {
        if (currentKey == key && currentViewerUid == viewerUid && _uiState.value.profile != null) {
            return
        }
        currentKey = key
        currentViewerUid = viewerUid
        loadedUserPostsForUid = null
        loadedUserBlogsForUid = null
        fetchProfile(key, viewerUid)
    }

    fun retryProfile() {
        val key = currentKey ?: return
        fetchProfile(key, currentViewerUid)
    }

    private fun fetchProfile(key: ProfileLookupKey, viewerUid: String?) {
        _uiState.update {
            PublicProfileUiState(
                isLoadingProfile = true,
                profileError = null,
            )
        }
        viewModelScope.launch {
            val result = when (key) {
                is ProfileLookupKey.ByUid -> userRepository.getPublicProfileByUid(key.uid)
                is ProfileLookupKey.ByTag -> userRepository.getPublicProfileByTag(key.tag)
            }
            result.onSuccess { profile ->
                val isOwn = !viewerUid.isNullOrBlank() && viewerUid == profile.uid
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isOwnProfile = isOwn,
                        isLoadingProfile = false,
                        profileError = null,
                    )
                }
                loadPinned(profile.uid)
                if (!isOwn && !viewerUid.isNullOrBlank() && visitRecordedForUid != profile.uid) {
                    visitRecordedForUid = profile.uid
                    launch { userRepository.recordVisitBestEffort(profile.uid) }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingProfile = false,
                        profileError = err as? AppError ?: AppError.Unknown(cause = err),
                    )
                }
            }
        }
    }

    fun selectTab(tab: ProfileTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val uid = _uiState.value.profile?.uid ?: return
        when (tab) {
            ProfileTab.HOME -> Unit
            ProfileTab.POSTS -> if (loadedUserPostsForUid != uid) loadUserPostsInitial(uid)
            ProfileTab.BLOG -> if (loadedUserBlogsForUid != uid) loadUserBlogsInitial(uid)
        }
    }

    fun loadPinned(uid: String? = _uiState.value.profile?.uid) {
        if (uid.isNullOrBlank()) return
        _uiState.update { it.copy(isLoadingPinned = true, pinnedError = null) }
        viewModelScope.launch {
            userRepository.listPinnedPosts(uid)
                .onSuccess { posts ->
                    _uiState.update {
                        it.copy(
                            pinnedPosts = posts,
                            isLoadingPinned = false,
                            pinnedError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingPinned = false,
                            pinnedError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadUserPostsInitial(uid: String? = _uiState.value.profile?.uid) {
        if (uid.isNullOrBlank()) return
        loadedUserPostsForUid = uid
        _uiState.update { it.copy(isLoadingUserPosts = true, userPostsError = null) }
        viewModelScope.launch {
            userRepository.listUserPosts(uid, cursor = null)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            userPosts = page.items,
                            userPostsNextCursor = page.nextCursor,
                            isLoadingUserPosts = false,
                            userPostsError = null,
                        )
                    }
                }
                .onFailure { err ->
                    loadedUserPostsForUid = null
                    _uiState.update {
                        it.copy(
                            isLoadingUserPosts = false,
                            userPostsError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadMoreUserPosts() {
        val state = _uiState.value
        val uid = state.profile?.uid ?: return
        val cursor = state.userPostsNextCursor ?: return
        if (state.isLoadingUserPosts || state.isLoadingMorePosts) return

        _uiState.update { it.copy(isLoadingMorePosts = true, userPostsError = null) }
        viewModelScope.launch {
            userRepository.listUserPosts(uid, cursor = cursor)
                .onSuccess { page ->
                    _uiState.update { current ->
                        val seen = current.userPosts.asSequence().map { it.id }.toHashSet()
                        current.copy(
                            userPosts = current.userPosts + page.items.filterNot { it.id in seen },
                            userPostsNextCursor = page.nextCursor,
                            isLoadingMorePosts = false,
                            userPostsError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingMorePosts = false,
                            userPostsError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadUserBlogsInitial(uid: String? = _uiState.value.profile?.uid) {
        if (uid.isNullOrBlank()) return
        loadedUserBlogsForUid = uid
        _uiState.update { it.copy(isLoadingUserBlogs = true, userBlogsError = null) }
        viewModelScope.launch {
            userRepository.listUserBlogs(uid, cursor = null)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            userBlogs = page.items,
                            userBlogsNextCursor = page.nextCursor,
                            isLoadingUserBlogs = false,
                            userBlogsError = null,
                        )
                    }
                }
                .onFailure { err ->
                    loadedUserBlogsForUid = null
                    _uiState.update {
                        it.copy(
                            isLoadingUserBlogs = false,
                            userBlogsError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun loadMoreUserBlogs() {
        val state = _uiState.value
        val uid = state.profile?.uid ?: return
        val cursor = state.userBlogsNextCursor ?: return
        if (state.isLoadingUserBlogs || state.isLoadingMoreBlogs) return

        _uiState.update { it.copy(isLoadingMoreBlogs = true, userBlogsError = null) }
        viewModelScope.launch {
            userRepository.listUserBlogs(uid, cursor = cursor)
                .onSuccess { page ->
                    _uiState.update { current ->
                        val seen = current.userBlogs.asSequence().map { it.id }.toHashSet()
                        current.copy(
                            userBlogs = current.userBlogs + page.items.filterNot { it.id in seen },
                            userBlogsNextCursor = page.nextCursor,
                            isLoadingMoreBlogs = false,
                            userBlogsError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingMoreBlogs = false,
                            userBlogsError = err as? AppError ?: AppError.Unknown(cause = err),
                        )
                    }
                }
        }
    }

    fun openExtraInfoDialog() {
        val uid = _uiState.value.profile?.uid ?: return
        _uiState.update {
            it.copy(
                extraDialog = ExtraInfoDialogState(
                    isOpen = true,
                    isLoading = true,
                    data = null,
                    error = null,
                ),
            )
        }
        viewModelScope.launch {
            userRepository.getExtraInfo(uid)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            extraDialog = ExtraInfoDialogState(
                                isOpen = true,
                                isLoading = false,
                                data = res,
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            extraDialog = ExtraInfoDialogState(
                                isOpen = true,
                                isLoading = false,
                                data = null,
                                error = err as? AppError ?: AppError.Unknown(cause = err),
                            ),
                        )
                    }
                }
        }
    }

    fun closeExtraInfoDialog() {
        _uiState.update { it.copy(extraDialog = ExtraInfoDialogState(isOpen = false)) }
    }

    fun react(card: ContentCard, target: ReactionType) {
        val helper = actionHelper ?: return
        viewModelScope.launch {
            helper.toggleReactionOnList(
                card = card,
                target = target,
                getCurrentList = {
                    val s = _uiState.value
                    (s.pinnedPosts + s.userPosts + s.userBlogs).distinctBy { "${it.type}_${it.id}" }
                },
                updateList = { updatedAll ->
                    val map = updatedAll.associateBy { "${it.type}_${it.id}" }
                    _uiState.update { cur ->
                        cur.copy(
                            pinnedPosts = cur.pinnedPosts.map { map["${it.type}_${it.id}"] ?: it },
                            userPosts = cur.userPosts.map { map["${it.type}_${it.id}"] ?: it },
                            userBlogs = cur.userBlogs.map { map["${it.type}_${it.id}"] ?: it },
                        )
                    }
                },
                onError = { msg -> _sideEffects.tryEmit(ProfileSideEffect.ShowMessage(msg)) },
            )
        }
    }

    fun share(card: ContentCard) {
        val helper = actionHelper ?: return
        viewModelScope.launch {
            helper.createShareLink(
                type = card.contentType,
                id = card.id,
                onShareReady = { url -> _sideEffects.tryEmit(ProfileSideEffect.OpenShareSheet(card.title, url)) },
                onError = { msg -> _sideEffects.tryEmit(ProfileSideEffect.ShowMessage(msg)) },
            )
        }
    }
}
