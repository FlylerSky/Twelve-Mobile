package com.example.core.util

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Định dạng số đếm và điểm có thể âm.
 * Nguồn đối chiếu: frontend/src/utils/number.js (formatCount, formatScore).
 */
object NumberFormatters {

    fun formatCount(value: Number?): String {
        val n = value?.toDouble() ?: return "0"
        if (!n.isFinite() || n <= 0.0) return "0"
        if (n < 1000.0) return floor(n).toLong().toString()

        val units = listOf(
            1e12 to "T",
            1e9 to "B",
            1e6 to "M",
            1e3 to "K",
        )

        for ((threshold, suffix) in units) {
            if (n >= threshold) {
                val scaled = n / threshold
                val text = if (scaled >= 100.0) {
                    floor(scaled).toLong().toString()
                } else {
                    val rounded = round(scaled * 10.0) / 10.0
                    if (rounded % 1.0 == 0.0) {
                        rounded.toLong().toString()
                    } else {
                        rounded.toString()
                    }
                }
                return text + suffix
            }
        }

        return floor(n).toLong().toString()
    }

    /**
     * Định dạng `postScore` hoặc điểm tăng trưởng có thể âm (ví dụ dislike = -2).
     * Tuyệt đối KHÔNG dùng formatCount() cho postScore vì sẽ clamp số âm về "0".
     */
    fun formatScore(value: Number?): String {
        val n = value?.toDouble() ?: return "0"
        if (!n.isFinite() || n == 0.0) return "0"
        val sign = if (n < 0.0) "-" else ""
        return sign + formatCount(abs(n))
    }

    fun formatIntegerVi(value: Number?): String {
        val v = value?.toLong() ?: 0L
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN")).format(v)
    }
}
