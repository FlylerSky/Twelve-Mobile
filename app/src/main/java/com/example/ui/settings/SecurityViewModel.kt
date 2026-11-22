package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.core.util.AccountValidators
import com.example.data.repository.AccountRepository
import com.example.data.repository.AuthRepository
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

data class SecurityUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isChangingPassword: Boolean = false,
    val changePasswordError: String? = null,
    val isDeletingAccount: Boolean = false,
    val deleteError: String? = null,
) {
    val canSubmitPassword: Boolean
        get() = !isChangingPassword &&
            currentPassword.isNotEmpty() &&
            newPassword.isNotEmpty() &&
            confirmPassword.isNotEmpty()
}

sealed interface SecuritySideEffect {
    data class ShowMessage(val message: String) : SecuritySideEffect
}

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    private val _sideEffects = MutableSharedFlow<SecuritySideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<SecuritySideEffect> = _sideEffects.asSharedFlow()

    fun onCurrentPasswordChanged(value: String) {
        _uiState.update { it.copy(currentPassword = value, changePasswordError = null) }
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update { it.copy(newPassword = value, changePasswordError = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, changePasswordError = null) }
    }

    fun submitChangePassword(successMessage: String) {
        val state = _uiState.value
        val validationError = AccountValidators.validateNewPassword(
            current = state.currentPassword,
            next = state.newPassword,
            confirm = state.confirmPassword,
        )
        if (validationError != null) {
            _uiState.update { it.copy(changePasswordError = validationError) }
            return
        }

        _uiState.update { it.copy(isChangingPassword = true, changePasswordError = null) }
        viewModelScope.launch {
            authRepository.changePassword(state.currentPassword, state.newPassword)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            isChangingPassword = false,
                            changePasswordError = null,
                        )
                    }
                    _sideEffects.tryEmit(SecuritySideEffect.ShowMessage(successMessage))
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            changePasswordError = err.message ?: "",
                        )
                    }
                }
        }
    }

    suspend fun confirmDeleteAccount(
        password: String,
        acceptedMessageFallback: String,
    ): Result<String> {
        _uiState.update { it.copy(isDeletingAccount = true, deleteError = null) }
        authRepository.reauthenticateWithPassword(password).onFailure { err ->
            val msg = err.message.orEmpty()
            _uiState.update { it.copy(isDeletingAccount = false, deleteError = msg) }
            return Result.failure(err)
        }

        val deletionResult = accountRepository.requestDeletion()
        return deletionResult.fold(
            onSuccess = { res ->
                val message = res.message?.takeIf { it.isNotBlank() } ?: acceptedMessageFallback
                _uiState.update { it.copy(isDeletingAccount = false, deleteError = null) }
                authRepository.signOut()
                _sideEffects.tryEmit(SecuritySideEffect.ShowMessage(message))
                Result.success(message)
            },
            onFailure = { err ->
                if (err is AppError.AccountDeleting) {
                    val message = err.serverMessage.ifBlank { acceptedMessageFallback }
                    _uiState.update { it.copy(isDeletingAccount = false, deleteError = null) }
                    authRepository.signOut()
                    _sideEffects.tryEmit(SecuritySideEffect.ShowMessage(message))
                    Result.success(message)
                } else {
                    val msg = (err as? AppError)?.userMessage ?: err.message.orEmpty()
                    _uiState.update { it.copy(isDeletingAccount = false, deleteError = msg) }
                    Result.failure(err)
                }
            },
        )
    }
}
