package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "twelve_session_prefs")

/**
 * Lưu mã phiên cục bộ `sid` bền vững trong DataStore để truyền kèm khi mở liên kết chia sẻ.
 * Nguồn đối chiếu: frontend/src/utils/session.js & backend/src/utils/helpers.js.
 */
@Singleton
class SessionIdStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val sidKey = stringPreferencesKey("client_session_id")
    @Volatile
    private var cachedSid: String? = null

    suspend fun getOrCreateSessionId(): String {
        cachedSid?.let { return it }
        val existing = context.sessionDataStore.data.map { it[sidKey] }.first()
        if (!existing.isNullOrBlank()) {
            cachedSid = existing
            return existing
        }
        val created = UUID.randomUUID().toString()
        context.sessionDataStore.edit { prefs ->
            prefs[sidKey] = created
        }
        cachedSid = created
        return created
    }
}
