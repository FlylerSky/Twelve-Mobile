package com.example.core.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Định dạng thời gian tương đối & tuyệt đối theo chuẩn Twelve Web.
 * Nguồn đối chiếu: frontend/src/utils/time.js, frontend/src/utils/profileExtras.js.
 */
object TimeFormatter {
    private val absoluteFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d/M/yyyy · HH:mm", Locale.forLanguageTag("vi-VN"))
            .withZone(ZoneId.systemDefault())

    private val joinDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.forLanguageTag("vi-VN"))
            .withZone(ZoneId.systemDefault())

    private val birthdayRegex = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun formatRelativeTime(isoOrNull: String?, now: Instant = Instant.now()): String {
        val instant = CreatedAtParser.parseIsoToInstant(isoOrNull) ?: return ""
        return formatRelativeTime(instant, now)
    }

    fun formatRelativeTime(instant: Instant, now: Instant = Instant.now()): String {
        val diffSec = Duration.between(instant, now).seconds
        if (diffSec < 0L) return absoluteFormatter.format(instant)
        if (diffSec < 60L) return "Vài giây trước"
        val diffMin = diffSec / 60L
        if (diffMin < 60L) return "$diffMin phút trước"
        val diffHour = diffSec / 3600L
        if (diffHour < 24L) return "$diffHour giờ trước"
        return absoluteFormatter.format(instant)
    }

    fun formatBirthday(raw: String?): String {
        val value = raw.orEmpty()
        val match = birthdayRegex.matchEntire(value) ?: return value
        val (year, month, day) = match.destructured
        return "$day/$month/$year"
    }

    fun formatJoinDate(isoOrNull: String?): String {
        val instant = CreatedAtParser.parseIsoToInstant(isoOrNull) ?: return ""
        return joinDateFormatter.format(instant)
    }

    fun formatWeekLabel(weekCode: String): String = "Tuần $weekCode"
}
