package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.util.DeepLinkParser
import com.example.core.util.TwelveDeepLink
import com.example.data.repository.SessionState
import com.example.ui.auth.AccountBlockedScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.MissingFirebaseConfigScreen
import com.example.ui.auth.PendingVerificationScreen
import com.example.ui.navigation.TwelveAppShell
import com.example.ui.theme.TwelveTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<TwelveDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            pendingDeepLink = parseViewIntent(intent)
        }
        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()
            val themeMode by authViewModel.themeMode.collectAsStateWithLifecycle()
            val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
            val serverWakeState by authViewModel.serverWakeState.collectAsStateWithLifecycle()
            val loginForm by authViewModel.loginForm.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, sessionState) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> authViewModel.onUiStart()
                        Lifecycle.Event.ON_STOP -> authViewModel.onUiStop()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    authViewModel.onUiStart()
                }
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    authViewModel.onUiStop()
                }
            }

            TwelveTheme(themeMode = themeMode) {
                when (val state = sessionState) {
                    SessionState.MissingFirebaseConfig -> {
                        MissingFirebaseConfigScreen()
                    }
                    SessionState.Loading -> {
                        SessionLoadingScreen(isWaking = serverWakeState is com.example.data.repository.ServerWakeState.Waking)
                    }
                    SessionState.SignedOut -> {
                        LoginScreen(
                            formState = loginForm,
                            serverWakeState = serverWakeState,
                            onSignIn = authViewModel::signIn,
                            onClearError = authViewModel::clearLoginError,
                            onContinueAsGuest = authViewModel::continueAsGuest,
                        )
                    }
                    is SessionState.PendingVerification -> {
                        PendingVerificationScreen(
                            pendingState = state,
                            formState = loginForm,
                            onCheckVerified = authViewModel::checkVerified,
                            onResendEmail = authViewModel::resendVerificationEmail,
                            onSignOut = authViewModel::signOut,
                        )
                    }
                    is SessionState.Blocked -> {
                        AccountBlockedScreen(
                            message = state.reason,
                            onAcknowledge = authViewModel::signOut,
                        )
                    }
                    is SessionState.Error -> {
                        SessionErrorScreen(
                            message = state.error.userMessage,
                            onRetry = authViewModel::retrySessionLoad,
                            onSignOut = authViewModel::signOut,
                        )
                    }
                    is SessionState.Active -> {
                        TwelveAppShell(
                            profile = state.profile,
                            serverWakeState = serverWakeState,
                            themeMode = themeMode,
                            onSetThemeMode = authViewModel::setThemeMode,
                            onCheckHealth = authViewModel::pingHealth,
                            onSignOut = authViewModel::signOut,
                            onOpenLogin = authViewModel::openLoginFromGuest,
                            pendingDeepLink = pendingDeepLink,
                            onDeepLinkConsumed = { pendingDeepLink = null },
                        )
                    }
                    is SessionState.Guest -> {
                        TwelveAppShell(
                            profile = null,
                            serverWakeState = serverWakeState,
                            themeMode = themeMode,
                            onSetThemeMode = authViewModel::setThemeMode,
                            onCheckHealth = authViewModel::pingHealth,
                            onSignOut = authViewModel::signOut,
                            onOpenLogin = authViewModel::openLoginFromGuest,
                            pendingDeepLink = pendingDeepLink,
                            onDeepLinkConsumed = { pendingDeepLink = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseViewIntent(intent)?.let { deepLink ->
            pendingDeepLink = deepLink
        }
    }

    private fun parseViewIntent(intent: Intent?): TwelveDeepLink? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return DeepLinkParser.parse(intent.dataString)
    }
}

@Composable
private fun SessionLoadingScreen(isWaking: Boolean) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(if (isWaking) R.string.status_waking_server else R.string.status_loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun SessionErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = onRetry,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_sign_out))
                }
            }
        }
    }
}
