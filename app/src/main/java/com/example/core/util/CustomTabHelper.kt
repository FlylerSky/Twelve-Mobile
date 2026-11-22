package com.example.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Mở liên kết http/https an toàn bằng Chrome Custom Tab.
 * Từ chối tuyệt đối mọi scheme khác (javascript:, intent:, file:, content:).
 */
object CustomTabHelper {
    fun isSafeWebUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
    }

    /** Trả về true nếu mở thành công, false nếu URL không hợp lệ hoặc không có trình duyệt. */
    fun openUrl(context: Context, rawUrl: String?): Boolean {
        if (!isSafeWebUrl(rawUrl)) return false
        val uri = Uri.parse(rawUrl!!.trim())
        return runCatching {
            val customTabs = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabs.launchUrl(context, uri)
            true
        }.getOrElse {
            runCatching {
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            }.getOrDefault(false)
        }
    }
}
