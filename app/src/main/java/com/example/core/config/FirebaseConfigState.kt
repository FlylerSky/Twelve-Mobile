package com.example.core.config

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kiểm tra trạng thái cấu hình Firebase an toàn trước khi đụng tới FirebaseAuth hoặc Firestore.
 * Không bao giờ gọi FirebaseAuth.getInstance() khi google-services.json chưa được cung cấp.
 */
interface FirebaseAvailability {
    val isConfigured: Boolean
}

@Singleton
class DefaultFirebaseAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : FirebaseAvailability {

    override val isConfigured: Boolean by lazy {
        if (!BuildConfig.HAS_GOOGLE_SERVICES) {
            return@lazy false
        }
        val existing = runCatching { FirebaseApp.getApps(context) }.getOrDefault(emptyList())
        val app = existing.firstOrNull() ?: runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
        val options = app?.options ?: return@lazy false
        options.applicationId.isNotBlank() &&
            options.apiKey.isNotBlank() &&
            !options.projectId.isNullOrBlank()
    }
}
