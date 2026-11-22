package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ThemePreferences
import com.example.data.repository.AuthRepository
import com.example.data.repository.ServerWakeState
import com.example.data.repository.SessionState
import com.example.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoginFormState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val verificationFeedback: String? = null,
    val isCheckingVerification: Boolean = false,
    val isResendingEmail: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.sessionState
    val serverWakeState: StateFlow<ServerWakeState> = authRepository.serverWakeState
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    private val _loginForm = MutableStateFlow(LoginFormState())
    val loginForm: StateFlow<LoginFormState> = _loginForm.asStateFlow()

    init {
        viewModelScope.launch {
            val firebaseReady = authRepository.initialize()
            if (firebaseReady) {
                authRepository.pingHealth()
            }
        }
    }

    fun pingHealth() {
        viewModelScope.launch {
            authRepository.pingHealth()
        }
    }

    fun signIn(email: String, password: String) {
        if (_loginForm.value.isSubmitting) return
        _loginForm.value = _loginForm.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.signIn(email, password)
            _loginForm.value = _loginForm.value.copy(
                isSubmitting = false,
                errorMessage = result.exceptionOrNull()?.message,
            )
        }
    }

    fun clearLoginError() {
        _loginForm.value = _loginForm.value.copy(errorMessage = null)
    }

    fun checkVerified() {
        if (_loginForm.value.isCheckingVerification) return
        _loginForm.value = _loginForm.value.copy(isCheckingVerification = true, verificationFeedback = null)
        viewModelScope.launch {
            val feedback = authRepository.checkEmailVerifiedAndActivate()
            _loginForm.value = _loginForm.value.copy(
                isCheckingVerification = false,
                verificationFeedback = feedback,
            )
        }
    }

    fun resendVerificationEmail() {
        if (_loginForm.value.isResendingEmail) return
        _loginForm.value = _loginForm.value.copy(isResendingEmail = true, verificationFeedback = null)
        viewModelScope.launch {
            val res = authRepository.resendVerificationEmail()
            _loginForm.value = _loginForm.value.copy(
                isResendingEmail = false,
                verificationFeedback = res.getOrElse { it.message },
            )
        }
    }

    fun retrySessionLoad() {
        viewModelScope.launch {
            authRepository.refreshSession()
        }
    }

    fun continueAsGuest() {
        authRepository.continueAsGuest()
    }

    fun openLoginFromGuest() {
        authRepository.enterLoginFromGuest()
    }

    fun signOut() {
        _loginForm.value = LoginFormState()
        authRepository.signOut()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun onUiStart() {
        authRepository.startProfileListener()
    }

    fun onUiStop() {
        authRepository.stopProfileListener()
    }
}
