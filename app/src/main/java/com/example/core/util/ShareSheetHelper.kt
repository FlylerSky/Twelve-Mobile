package com.example.core.util

import android.content.Context
import android.content.Intent

/**
 * Mở bảng chia sẻ hệ thống Android (`Intent.ACTION_SEND`) với liên kết do server tạo (`POST .../share`).
 * Tuyệt đối không tự lắp URL hay tham số chữ ký `&s=` ở client.
 */
object ShareSheetHelper {
    fun shareLink(
        context: Context,
        title: String,
        shareUrl: String,
        chooserTitle: String,
    ) {
        val text = if (title.isNotBlank()) "$title\n$shareUrl" else shareUrl
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
