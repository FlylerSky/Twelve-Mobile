package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.FirebaseAvailability
import com.example.core.network.AppError
import com.example.core.network.DefaultFirebaseTokenProvider
import com.example.data.remote.TwelveAuthApi
import com.example.data.repository.AuthRepository
import com.example.data.repository.SessionState
import com.example.ui.auth.MissingFirebaseConfigScreen
import com.example.ui.theme.TwelveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `app_name string is Twelve`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Twelve", context.getString(R.string.app_name))
    }

    @Test
    fun `missing google services emits MissingFirebaseConfig without touching FirebaseAuth`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val unavailable = object : FirebaseAvailability {
            override val isConfigured: Boolean = false
        }
        val tokenProvider = DefaultFirebaseTokenProvider(unavailable)
        // Không gọi FirebaseAuth.getInstance() và trả null an toàn trên luồng nền OkHttp
        val token = withContext(Dispatchers.IO) {
            tokenProvider.getIdTokenSync(forceRefresh = false)
        }
        assertNull(token)

        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TwelveAuthApi::class.java)

            val repo = AuthRepository(
                context = context,
                firebaseAvailability = unavailable,
                tokenProvider = tokenProvider,
                authApi = api,
            )

            val initialized = repo.initialize()
            assertFalse(initialized)
            assertEquals(SessionState.MissingFirebaseConfig, repo.sessionState.value)

            val signInResult = repo.signIn("test@example.com", "secret123")
            assertTrue(signInResult.isFailure)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `missing firebase config screen renders blocking guidance`() {
        composeRule.setContent {
            TwelveTheme {
                MissingFirebaseConfigScreen(applicationId = "com.twelve.mobile")
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("missing_firebase_config_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("missing_firebase_title").assertIsDisplayed()
    }
}
