package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.core.util.AccountValidators
import com.example.core.util.BioValidator
import com.example.core.util.ProfileExtrasCatalog
import com.example.core.util.TagNameNormalizer
import com.example.data.model.CountryItem
import com.example.data.model.CoverCrop
import com.example.data.model.CoverResponse
import com.example.data.model.UserProfile
import com.example.data.repository.AccountRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.SessionState
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

data class EditableProfileForm(
    val displayName: String = "",
    val birthday: String = "",
    val gender: String = "male",
    val country: String = "",
    val bio: String = "",
) {
    fun dirtyMapAgainst(baseline: EditableProfileForm): Map<String, String> {
        val dirty = linkedMapOf<String, String>()
        if (displayName != baseline.displayName) dirty["displayName"] = AccountValidators.cleanDisplayText(displayName)
        if (birthday != baseline.birthday) dirty["birthday"] = birthday.trim()
        if (gender != baseline.gender) dirty["gender"] = gender
        if (country != baseline.country) dirty["country"] = country.trim().uppercase()
        if (bio != baseline.bio) dirty["bio"] = BioValidator.normalizeBio(bio)
        return dirty
    }
}

sealed interface TagCheckStatus {
    data object Idle : TagCheckStatus
    data object Invalid : TagCheckStatus
    data object SameAsCurrent : TagCheckStatus
    data object Checking : TagCheckStatus
    data object Available : TagCheckStatus
    data object Taken : TagCheckStatus
    data object Reserved : TagCheckStatus
    data class Error(val message: String) : TagCheckStatus
}

data class AccountInfoUiState(
    val profile: UserProfile? = null,
    val countries: List<CountryItem> = emptyList(),
    val countriesError: String? = null,
    val baseline: EditableProfileForm = EditableProfileForm(),
    val form: EditableProfileForm = EditableProfileForm(),
    val isSavingProfile: Boolean = false,
    val profileSaveError: String? = null,
    val extras: Map<String, Boolean> = ProfileExtrasCatalog.resolveExtras(null),
    val busyExtraKeys: Set<String> = emptySet(),
    val revealedRealName: String? = null,
    val isUploadingAvatar: Boolean = false,
    val isUpdatingCover: Boolean = false,
    val tagInput: String = "",
    val tagCheckStatus: TagCheckStatus = TagCheckStatus.Idle,
) {
    val dirtyFields: Map<String, String>
        get() = form.dirtyMapAgainst(baseline)

    val bioStats: BioValidator.BioStats
        get() = BioValidator.bioStats(form.bio)

    val displayNameError: String?
        get() = if (form.displayName != baseline.displayName) AccountValidators.validateDisplayName(form.displayName) else null

    val birthdayError: String?
        get() = if (form.birthday != baseline.birthday && form.birthday.isNotBlank()) {
            AccountValidators.validateBirthday(form.birthday)
        } else {
            null
        }

    val canSaveProfile: Boolean
        get() = !isSavingProfile &&
            dirtyFields.isNotEmpty() &&
            bioStats.valid &&
            AccountValidators.validateDisplayName(form.displayName) == null &&
            (form.birthday.isBlank() || AccountValidators.validateBirthday(form.birthday) == null)
}

sealed interface AccountInfoSideEffect {
    data class ShowMessage(val message: String) : AccountInfoSideEffect
}

@HiltViewModel
class AccountInfoViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountInfoUiState())
    val uiState: StateFlow<AccountInfoUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<AccountInfoSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<AccountInfoSideEffect> = _sideEffects.asSharedFlow()

    private var formInitializedFromProfile = false
    private var tagCheckJob: Job? = null

    init {
        loadCountries()
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                val activeProfile = (session as? SessionState.Active)?.profile
                if (activeProfile != null) {
                    applyProfileSnapshot(activeProfile)
                }
            }
        }
    }

    private fun applyProfileSnapshot(profile: UserProfile) {
        val picked = EditableProfileForm(
            displayName = profile.displayName.orEmpty(),
            birthday = profile.birthday.orEmpty(),
            gender = profile.gender?.takeIf { it.isNotBlank() } ?: "male",
            country = profile.country.orEmpty(),
            bio = profile.bio.orEmpty(),
        )
        _uiState.update { cur ->
            val resolvedExtras = if (cur.busyExtraKeys.isEmpty()) {
                ProfileExtrasCatalog.resolveExtras(profile.profileExtras)
            } else {
                cur.extras
            }
            if (!formInitializedFromProfile) {
                formInitializedFromProfile = true
                cur.copy(
                    profile = profile,
                    baseline = picked,
                    form = picked,
                    extras = resolvedExtras,
                )
            } else {
                cur.copy(
                    profile = profile,
                    extras = resolvedExtras,
                )
            }
        }
    }

    fun loadCountries() {
        viewModelScope.launch {
            accountRepository.listCountries()
                .onSuccess { list ->
                    _uiState.update { it.copy(countries = list, countriesError = null) }
                }
                .onFailure { err ->
                    val msg = (err as? AppError)?.userMessage ?: err.message
                    _uiState.update { it.copy(countriesError = msg) }
                }
        }
    }

    fun onDisplayNameChanged(value: String) {
        _uiState.update { it.copy(form = it.form.copy(displayName = value), profileSaveError = null) }
    }

    fun onBirthdayChanged(value: String) {
        _uiState.update { it.copy(form = it.form.copy(birthday = value), profileSaveError = null) }
    }

    fun onGenderChanged(value: String) {
        _uiState.update { it.copy(form = it.form.copy(gender = value), profileSaveError = null) }
    }

    fun onCountryChanged(value: String) {
        _uiState.update { it.copy(form = it.form.copy(country = value), profileSaveError = null) }
    }

    fun onBioChanged(value: String) {
        _uiState.update { it.copy(form = it.form.copy(bio = value), profileSaveError = null) }
    }

    fun saveProfileChanges(successMessage: String) {
        val cur = _uiState.value
        val dirty = cur.dirtyFields
        if (dirty.isEmpty() || !cur.canSaveProfile) return
        val formAfterClean = cur.form.copy(
            displayName = AccountValidators.cleanDisplayText(cur.form.displayName),
            bio = BioValidator.normalizeBio(cur.form.bio),
        )
        _uiState.update { it.copy(isSavingProfile = true, profileSaveError = null) }
        viewModelScope.launch {
            accountRepository.updateMe(dirty)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            baseline = formAfterClean,
                            form = formAfterClean,
                            profileSaveError = null,
                        )
                    }
                    _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage))
                }
                .onFailure { err ->
                    val msg = (err as? AppError)?.userMessage ?: err.message ?: ""
                    _uiState.update { it.copy(isSavingProfile = false, profileSaveError = msg) }
                    if (msg.isNotBlank()) {
                        _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(msg))
                    }
                }
        }
    }

    /** Bật/tắt mục hiển thị mở rộng ngay lập tức với UI lạc quan & hoàn tác khi lỗi. */
    fun toggleExtra(key: String, newValue: Boolean, fallbackErrorMessage: String) {
        val cur = _uiState.value
        if (key in cur.busyExtraKeys) return
        val previousMap = cur.extras
        _uiState.update {
            it.copy(
                extras = it.extras + (key to newValue),
                busyExtraKeys = it.busyExtraKeys + key,
            )
        }
        viewModelScope.launch {
            accountRepository.updateExtras(mapOf(key to newValue))
                .onSuccess { resolved ->
                    _uiState.update {
                        it.copy(
                            extras = resolved,
                            busyExtraKeys = it.busyExtraKeys - key,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            extras = previousMap,
                            busyExtraKeys = it.busyExtraKeys - key,
                        )
                    }
                    val msg = (err as? AppError)?.userMessage ?: err.message ?: fallbackErrorMessage
                    _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(msg))
                }
        }
    }

    suspend fun uploadAvatarBytes(jpegBytes: ByteArray, successMessage: String): Result<String> {
        _uiState.update { it.copy(isUploadingAvatar = true) }
        val res = accountRepository.uploadAvatar(jpegBytes)
        _uiState.update { it.copy(isUploadingAvatar = false) }
        res.onSuccess { _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage)) }
        return res
    }

    suspend fun uploadCoverWithCrop(
        jpegBytes: ByteArray,
        crop: CoverCrop?,
        successMessage: String,
    ): Result<CoverResponse> {
        _uiState.update { it.copy(isUpdatingCover = true) }
        val res = accountRepository.uploadCover(jpegBytes, crop)
        _uiState.update { it.copy(isUpdatingCover = false) }
        res.onSuccess { _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage)) }
        return res
    }

    suspend fun setCoverUrl(url: String, successMessage: String): Result<CoverResponse> {
        _uiState.update { it.copy(isUpdatingCover = true) }
        val res = accountRepository.setCoverUrl(url)
        _uiState.update { it.copy(isUpdatingCover = false) }
        res.onSuccess { _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage)) }
        return res
    }

    suspend fun setCoverCrop(crop: CoverCrop?, successMessage: String): Result<CoverResponse> {
        _uiState.update { it.copy(isUpdatingCover = true) }
        val res = accountRepository.setCoverCrop(crop)
        _uiState.update { it.copy(isUpdatingCover = false) }
        res.onSuccess { _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage)) }
        return res
    }

    fun removeCover(successMessage: String) {
        if (_uiState.value.isUpdatingCover) return
        _uiState.update { it.copy(isUpdatingCover = true) }
        viewModelScope.launch {
            accountRepository.removeCover()
                .onSuccess {
                    _uiState.update { it.copy(isUpdatingCover = false) }
                    _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage))
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isUpdatingCover = false) }
                    val msg = (err as? AppError)?.userMessage ?: err.message.orEmpty()
                    if (msg.isNotBlank()) _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(msg))
                }
        }
    }

    fun onTagInputChanged(raw: String) {
        tagCheckJob?.cancel()
        val bare = TagNameNormalizer.toBareTag(raw)
        val currentBare = TagNameNormalizer.toBareTag(_uiState.value.profile?.tagName)
        if (bare.isEmpty()) {
            _uiState.update { it.copy(tagInput = raw, tagCheckStatus = TagCheckStatus.Idle) }
            return
        }
        if (!TagNameNormalizer.isValid(bare)) {
            _uiState.update { it.copy(tagInput = raw, tagCheckStatus = TagCheckStatus.Invalid) }
            return
        }
        if (currentBare.isNotEmpty() && bare == currentBare) {
            _uiState.update { it.copy(tagInput = raw, tagCheckStatus = TagCheckStatus.SameAsCurrent) }
            return
        }

        _uiState.update { it.copy(tagInput = raw, tagCheckStatus = TagCheckStatus.Checking) }
        tagCheckJob = viewModelScope.launch {
            delay(500L)
            accountRepository.checkTagName(bare)
                .onSuccess { res ->
                    val status = when {
                        res.available -> TagCheckStatus.Available
                        res.reserved -> TagCheckStatus.Reserved
                        else -> TagCheckStatus.Taken
                    }
                    _uiState.update { it.copy(tagCheckStatus = status) }
                }
                .onFailure { err ->
                    val msg = (err as? AppError)?.userMessage ?: err.message.orEmpty()
                    _uiState.update { it.copy(tagCheckStatus = TagCheckStatus.Error(msg)) }
                }
        }
    }

    fun resetTagInput() {
        tagCheckJob?.cancel()
        _uiState.update { it.copy(tagInput = "", tagCheckStatus = TagCheckStatus.Idle) }
    }

    suspend fun confirmUpdateTagName(successMessage: String): Result<Unit> {
        val raw = _uiState.value.tagInput
        return accountRepository.updateTagName(raw)
            .map {
                _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage))
                Unit
            }
    }

    suspend fun revealRealNameWithPassword(
        password: String,
        emptyFallback: String,
    ): Result<String> {
        authRepository.reauthenticateWithPassword(password).onFailure { return Result.failure(it) }
        val result = accountRepository.getRealName()
        result.onSuccess { name ->
            val valueToShow = name.ifBlank { emptyFallback }
            _uiState.update { it.copy(revealedRealName = valueToShow) }
        }
        return result
    }

    fun hideRealName() {
        _uiState.update { it.copy(revealedRealName = null) }
    }

    suspend fun updateRealNameWithPassword(
        rawNewName: String,
        password: String,
        successMessage: String,
    ): Result<Unit> {
        val cleaned = AccountValidators.cleanDisplayText(rawNewName)
        AccountValidators.validateRealName(cleaned)?.let { errMsg ->
            return Result.failure(IllegalArgumentException(errMsg))
        }
        authRepository.reauthenticateWithPassword(password).onFailure { return Result.failure(it) }
        val res = accountRepository.updateRealName(cleaned)
        res.onSuccess {
            _uiState.update { it.copy(revealedRealName = null) }
            _sideEffects.tryEmit(AccountInfoSideEffect.ShowMessage(successMessage))
        }
        return res
    }
}
