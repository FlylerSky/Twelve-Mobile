package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.core.config.FirebaseAvailability
import com.example.core.network.AppError
import com.example.core.network.BackendErrorParser
import com.example.core.network.DefaultFirebaseTokenProvider
import com.example.core.util.BadgeCatalog
import com.example.core.util.EmailCompat
import com.example.data.model.AccountStatus
import com.example.data.model.UserProfile
import com.example.data.remote.TwelveAuthApi
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface ServerWakeState {
    data object Idle : ServerWakeState
    data object Waking : ServerWakeState
    data class Ready(val version: String?) : ServerWakeState
    data class Unreachable(val message: String) : ServerWakeState
}

sealed interface SessionState {
    data object Loading : SessionState
    data object MissingFirebaseConfig : SessionState
    data object SignedOut : SessionState
    data class Guest(val profileNotice: String? = null) : SessionState
    data class PendingVerification(
        val email: String,
        val emailVerifiedInFirebase: Boolean,
        val profile: UserProfile?,
        val statusMessage: String? = null,
    ) : SessionState
    data class Active(val profile: UserProfile) : SessionState
    data class Blocked(
        val reason: String,
        val isDeleting: Boolean,
    ) : SessionState
    data class Error(
        val error: AppError,
        val hasFirebaseSession: Boolean,
    ) : SessionState
}

@Singleton
class AuthRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firebaseAvailability: FirebaseAvailability,
    private val tokenProvider: DefaultFirebaseTokenProvider,
    private val authApi: TwelveAuthApi,
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _serverWakeState = MutableStateFlow<ServerWakeState>(ServerWakeState.Idle)
    val serverWakeState: StateFlow<ServerWakeState> = _serverWakeState.asStateFlow()

    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var profileListener: ListenerRegistration? = null
    private var activeProfileCache: UserProfile? = null

    init {
        tokenProvider.setOnSessionInvalidatedListener {
            stopProfileListener()
            activeProfileCache = null
            _sessionState.value = SessionState.SignedOut
        }
    }

    /** Kiểm tra cấu hình Firebase khi khởi động; tuyệt đối không gọi FirebaseAuth khi chưa có google-services.json. */
    suspend fun initialize(): Boolean {
        if (!firebaseAvailability.isConfigured) {
            _sessionState.value = SessionState.MissingFirebaseConfig
            return false
        }

        val auth = FirebaseAuth.getInstance()
        if (authStateListener == null) {
            authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                if (firebaseAuth.currentUser == null) {
                    stopProfileListener()
                    activeProfileCache = null
                    val current = _sessionState.value
                    if (current !is SessionState.Blocked && current !is SessionState.Guest) {
                        _sessionState.value = SessionState.SignedOut
                    }
                }
            }
            auth.addAuthStateListener(authStateListener!!)
        }

        if (auth.currentUser == null) {
            _sessionState.value = SessionState.SignedOut
        } else {
            refreshSession()
        }
        return true
    }

    /** Đánh thức backend Render gói free ngay khi mở app (timeout 60s). */
    suspend fun pingHealth() {
        _serverWakeState.value = ServerWakeState.Waking
        runCatching {
            authApi.pingHealth(BuildConfig.HEALTH_URL)
        }.onSuccess { res ->
            _serverWakeState.value = ServerWakeState.Ready(res.version)
        }.onFailure { err ->
            val mapped = BackendErrorParser.fromThrowable(err)
            _serverWakeState.value = ServerWakeState.Unreachable(mapped.userMessage)
        }
    }

    /**
     * Đăng nhập Firebase bằng email + mật khẩu (có thử email chuẩn hoá legacy như frontend/src/utils/email.js).
     * Không log email, mật khẩu hoặc token.
     */
    suspend fun signIn(emailInput: String, password: String): Result<Unit> {
        if (!firebaseAvailability.isConfigured) {
            _sessionState.value = SessionState.MissingFirebaseConfig
            return Result.failure(IllegalStateException(context.getString(R.string.firebase_missing_title)))
        }
        val candidates = EmailCompat.loginCandidates(emailInput)
        if (candidates.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException(context.getString(R.string.auth_empty_credentials)))
        }

        val auth = FirebaseAuth.getInstance()
        var firstError: Throwable? = null
        var signedIn = false

        for ((index, candidate) in candidates.withIndex()) {
            try {
                auth.signInWithEmailAndPassword(candidate, password).await()
                signedIn = true
                break
            } catch (t: Throwable) {
                if (index == 0) firstError = t
                val code = (t as? FirebaseAuthException)?.errorCode
                val canTryLegacy = index == 0 && candidates.size > 1 && code in LEGACY_RETRY_CODES
                if (!canTryLegacy) {
                    return Result.failure(IllegalStateException(mapFirebaseAuthError(firstError ?: t)))
                }
            }
        }

        if (!signedIn) {
            return Result.failure(IllegalStateException(mapFirebaseAuthError(firstError)))
        }

        refreshSession()
        return Result.success(Unit)
    }

    /** Tải lại trạng thái emailVerified từ Firebase, đồng bộ /auth/activation/sync nếu cần, rồi tải /users/me. */
    suspend fun refreshSession() {
        if (!firebaseAvailability.isConfigured) {
            _sessionState.value = SessionState.MissingFirebaseConfig
            return
        }
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            _sessionState.value = SessionState.SignedOut
            return
        }

        _sessionState.value = SessionState.Loading
        runCatching { user.reload().await() }
        val emailVerified = user.isEmailVerified

        // Nếu Firebase đã xác minh email -> đồng bộ pending -> active (backend tự giữ nguyên nếu đã active).
        // Nếu trả ACCOUNT_SUSPENDED hoặc ACCOUNT_DELETING -> chặn & đăng xuất ngay, KHÔNG thử lại.
        if (emailVerified) {
            val syncResult = runCatching { authApi.syncActivation() }
            syncResult.exceptionOrNull()?.let { err ->
                val appError = BackendErrorParser.fromThrowable(err)
                if (handleHardBlockedError(appError)) return
            }
        }

        // Tối đa 3 lần thử lại cho lỗi mạng/5xx khi backend Render khởi động nguội (AuthContext.jsx),
        // nhưng tuyệt đối dừng ngay nếu bị 403 chặn tài khoản hoặc 429 hoặc 401.
        var lastError: AppError? = null
        for (attempt in 0 until 3) {
            try {
                val me = authApi.getMe().user
                applyLoadedProfile(me, emailVerified)
                return
            } catch (t: Throwable) {
                val mapped = BackendErrorParser.fromThrowable(t)
                if (handleHardBlockedError(mapped)) return
                if (mapped is AppError.Unauthorized) {
                    signOutInternal(preserveBlocked = false)
                    _sessionState.value = SessionState.Error(mapped, hasFirebaseSession = false)
                    return
                }
                if (mapped is AppError.RateLimited) {
                    _sessionState.value = SessionState.Error(mapped, hasFirebaseSession = true)
                    return
                }
                lastError = mapped
                if (attempt < 2 && (mapped is AppError.NetworkOffline || mapped is AppError.Timeout || (mapped is AppError.ApiError && mapped.httpStatus in 500..599))) {
                    delay(1_000L * (1 shl attempt))
                } else {
                    break
                }
            }
        }

        _sessionState.value = SessionState.Error(
            error = lastError ?: AppError.Unknown(),
            hasFirebaseSession = true,
        )
    }

    suspend fun resendVerificationEmail(): Result<String> {
        if (!firebaseAvailability.isConfigured) {
            return Result.failure(IllegalStateException(context.getString(R.string.firebase_missing_title)))
        }
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result.failure(IllegalStateException(context.getString(R.string.auth_session_expired)))
        return runCatching {
            user.sendEmailVerification().await()
            context.getString(R.string.auth_verify_resent_ok)
        }.recoverCatching { err ->
            throw IllegalStateException(mapFirebaseAuthError(err))
        }
    }

    suspend fun checkEmailVerifiedAndActivate(): String? {
        if (!firebaseAvailability.isConfigured) return null
        val user = FirebaseAuth.getInstance().currentUser ?: return context.getString(R.string.auth_session_expired)
        runCatching { user.reload().await() }
        if (!user.isEmailVerified) {
            return context.getString(R.string.auth_verify_not_yet)
        }
        refreshSession()
        return null
    }

    fun currentActiveProfile(): UserProfile? =
        (sessionState.value as? SessionState.Active)?.profile ?: activeProfileCache?.takeIf { it.accountStatus == AccountStatus.ACTIVE }

    fun patchActiveProfile(transform: (UserProfile) -> UserProfile) {
        val current = currentActiveProfile() ?: return
        val updated = transform(current)
        activeProfileCache = updated
        if (_sessionState.value is SessionState.Active) {
            _sessionState.value = SessionState.Active(updated)
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun setActiveProfileForTesting(profile: UserProfile) {
        activeProfileCache = profile
        _sessionState.value = SessionState.Active(profile)
    }

    fun updatePinnedPostIds(ids: List<String>) {
        patchActiveProfile { it.copy(pinnedPostIds = ids) }
    }

    /** Đồng bộ nhẹ hồ sơ (/users/me) sau tương tác đổi điểm mà không làm màn hình chớp Loading. */
    suspend fun refreshProfileBestEffort() {
        if (!firebaseAvailability.isConfigured) return
        val current = currentActiveProfile() ?: return
        runCatching { authApi.getMe().user }
            .onSuccess { me ->
                if (me.uid == current.uid && me.accountStatus == AccountStatus.ACTIVE) {
                    activeProfileCache = me
                    _sessionState.value = SessionState.Active(me)
                } else {
                    applyLoadedProfile(me, FirebaseAuth.getInstance().currentUser?.isEmailVerified ?: true)
                }
            }
            .onFailure { err ->
                val mapped = BackendErrorParser.fromThrowable(err)
                handleHardBlockedError(mapped)
            }
    }

    fun continueAsGuest() {
        if (!firebaseAvailability.isConfigured) {
            _sessionState.value = SessionState.MissingFirebaseConfig
            return
        }
        _sessionState.value = SessionState.Guest()
    }

    fun enterLoginFromGuest() {
        if (!firebaseAvailability.isConfigured) {
            _sessionState.value = SessionState.MissingFirebaseConfig
            return
        }
        _sessionState.value = SessionState.SignedOut
    }

    fun signOut() {
        stopProfileListener()
        activeProfileCache = null
        if (firebaseAvailability.isConfigured) {
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
        _sessionState.value = SessionState.SignedOut
    }

    /** Cho phép lớp Repository khác báo khi API trả ACCOUNT_SUSPENDED / ACCOUNT_DELETING. */
    fun notifyHardBlocked(error: AppError): Boolean = handleHardBlockedError(error)

    /**
     * Xác thực lại bằng mật khẩu hiện tại (cấp ID token có `auth_time` mới cho thao tác nhạy cảm).
     * Nguồn đối chiếu: frontend/src/utils/reauth.js#reauthWithPassword.
     */
    suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        if (!firebaseAvailability.isConfigured) {
            return Result.failure(IllegalStateException(context.getString(R.string.firebase_missing_title)))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException(context.getString(R.string.reauth_empty_password)))
        }
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException(context.getString(R.string.auth_session_expired)))

        return runCatching {
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            user.getIdToken(true).await()
            Unit
        }.recoverCatching { err ->
            throw IllegalStateException(mapFirebaseAuthError(err))
        }
    }

    /** Đổi mật khẩu qua Firebase Auth sau khi xác thực lại mật khẩu cũ (ChangePasswordPanel.jsx). */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        reauthenticateWithPassword(currentPassword).onFailure { return Result.failure(it) }
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result.failure(IllegalStateException(context.getString(R.string.auth_session_expired)))
        return runCatching {
            user.updatePassword(newPassword).await()
            Unit
        }.recoverCatching { err ->
            throw IllegalStateException(mapFirebaseAuthError(err))
        }
    }

    /**
     * Gắn listener Firestore CHỈ ĐỌC cho `users/{uid}` trong vòng đời onStart..onStop.
     * Bỏ qua `fullName` (nếu dữ liệu cũ còn sót), tự tính lại `badges`, và tự đăng xuất nếu `status` chuyển sang suspended/deleting.
     * Nguồn đối chiếu: backend/firestore.rules & frontend/src/context/AuthContext.jsx.
     */
    fun startProfileListener() {
        if (!firebaseAvailability.isConfigured) return
        val currentState = _sessionState.value
        val uid = when (currentState) {
            is SessionState.Active -> currentState.profile.uid
            is SessionState.PendingVerification -> currentState.profile?.uid
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return

        stopProfileListener()
        val db = FirebaseFirestore.getInstance()
        profileListener = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val data = snapshot.data ?: return@addSnapshotListener

            // Không bao giờ đọc/đưa `fullName` (tên thật cũ nếu còn) vào state UI.
            val freshStatus = data["status"] as? String
            val accountStatus = AccountStatus.fromRaw(freshStatus)
            if (accountStatus == AccountStatus.SUSPENDED) {
                signOutInternal(preserveBlocked = true)
                _sessionState.value = SessionState.Blocked(
                    reason = context.getString(R.string.auth_account_suspended_default),
                    isDeleting = false,
                )
                return@addSnapshotListener
            }
            if (accountStatus == AccountStatus.DELETING) {
                signOutInternal(preserveBlocked = true)
                _sessionState.value = SessionState.Blocked(
                    reason = context.getString(R.string.auth_account_deleting_default),
                    isDeleting = true,
                )
                return@addSnapshotListener
            }

            val base = activeProfileCache ?: UserProfile(uid = uid)
            val accountGroup = (data["accountGroup"] as? String) ?: base.accountGroup
            val accountRole = (data["accountRole"] as? String) ?: base.accountRole
            val isLegacy = (data["isLegacyMember"] as? Boolean) ?: base.isLegacyMember
            val pinned = (data["pinnedPostIds"] as? List<*>)?.filterIsInstance<String>() ?: base.pinnedPostIds
            @Suppress("UNCHECKED_CAST")
            val extras = (data["profileExtras"] as? Map<String, Boolean>) ?: base.profileExtras

            val merged = base.copy(
                displayName = (data["displayName"] as? String) ?: base.displayName,
                tagName = (data["tagName"] as? String) ?: base.tagName,
                avatarUrl = data["avatarUrl"] as? String ?: base.avatarUrl,
                coverUrl = data["coverUrl"] as? String ?: base.coverUrl,
                bio = data["bio"] as? String ?: base.bio,
                status = freshStatus ?: base.status,
                accountGroup = accountGroup,
                accountRole = accountRole,
                isLegacyMember = isLegacy,
                accountScore = (data["accountScore"] as? Number)?.toInt() ?: base.accountScore,
                postsScoreSum = (data["postsScoreSum"] as? Number)?.toInt() ?: base.postsScoreSum,
                positiveScore = (data["positiveScore"] as? Number)?.toInt() ?: base.positiveScore,
                pinnedPostIds = pinned,
                profileExtras = extras,
                badges = BadgeCatalog.deriveBadgeIds(accountGroup, accountRole, isLegacy),
            )
            activeProfileCache = merged
            if (merged.accountStatus == AccountStatus.ACTIVE) {
                _sessionState.value = SessionState.Active(merged)
            }
        }
    }

    fun stopProfileListener() {
        profileListener?.remove()
        profileListener = null
    }

    private fun applyLoadedProfile(me: UserProfile, emailVerified: Boolean) {
        activeProfileCache = me
        when (me.accountStatus) {
            AccountStatus.SUSPENDED -> {
                signOutInternal(preserveBlocked = true)
                _sessionState.value = SessionState.Blocked(
                    reason = context.getString(R.string.auth_account_suspended_default),
                    isDeleting = false,
                )
            }
            AccountStatus.DELETING -> {
                signOutInternal(preserveBlocked = true)
                _sessionState.value = SessionState.Blocked(
                    reason = context.getString(R.string.auth_account_deleting_default),
                    isDeleting = true,
                )
            }
            AccountStatus.ACTIVE -> {
                _sessionState.value = SessionState.Active(me)
            }
            AccountStatus.PENDING, AccountStatus.UNKNOWN -> {
                _sessionState.value = SessionState.PendingVerification(
                    email = me.email.orEmpty(),
                    emailVerifiedInFirebase = emailVerified,
                    profile = me,
                )
            }
        }
    }

    private fun handleHardBlockedError(error: AppError): Boolean {
        return when (error) {
            is AppError.AccountSuspended -> {
                signOutInternal(preserveBlocked = true)
                _sessionState.value = SessionState.Blocked(error.serverMessage, isDeleting = false)
                true
            }
            is AppError.AccountDeleting -> {
                signOutInternal(preserveBlocked = true)
                _sessionState.value = SessionState.Blocked(error.serverMessage, isDeleting = true)
                true
            }
            else -> false
        }
    }

    private fun signOutInternal(preserveBlocked: Boolean) {
        stopProfileListener()
        activeProfileCache = null
        if (firebaseAvailability.isConfigured) {
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
        if (!preserveBlocked) {
            _sessionState.value = SessionState.SignedOut
        }
    }

    private fun mapFirebaseAuthError(error: Throwable?): String {
        val code = (error as? FirebaseAuthException)?.errorCode
        return when (code) {
            "ERROR_INVALID_EMAIL" -> context.getString(R.string.auth_invalid_email)
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "INVALID_LOGIN_CREDENTIALS" -> context.getString(R.string.auth_error_invalid_credentials)
            "ERROR_TOO_MANY_REQUESTS" -> context.getString(R.string.auth_error_too_many_requests)
            "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.auth_error_network)
            "ERROR_WEAK_PASSWORD" -> context.getString(R.string.security_password_weak_error)
            "ERROR_USER_DISABLED" -> context.getString(R.string.auth_account_deleting_default)
            "ERROR_REQUIRES_RECENT_LOGIN" -> context.getString(R.string.auth_reauth_description)
            else -> context.getString(R.string.auth_error_generic)
        }
    }

    private companion object {
        val LEGACY_RETRY_CODES = setOf(
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_USER_NOT_FOUND",
            "INVALID_LOGIN_CREDENTIALS",
        )
    }
}
