package com.example.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Bộ chuyển đổi chịu lỗi cho `createdAt` / `updatedAt` có thể đến dưới dạng chuỗi ISO
 * hoặc đối tượng Timestamp của Firestore ({ _seconds, _nanoseconds } hoặc { seconds, nanoseconds }).
 */
object CreatedAtParser {
    private val viFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))
            .withZone(ZoneId.systemDefault())

    fun parseIsoToInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw.trim()) }.getOrNull()
    }

    fun formatInstant(instant: Instant?): String {
        if (instant == null) return ""
        return viFormatter.format(instant)
    }

    fun formatRelativeVi(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return ""
        val diffSec = ((now.toEpochMilli() - instant.toEpochMilli()) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 -> "Vừa xong"
            diffSec < 3600 -> "${diffSec / 60} phút trước"
            diffSec < 86_400 -> "${diffSec / 3600} giờ trước"
            diffSec < 7 * 86_400 -> "${diffSec / 86_400} ngày trước"
            else -> formatInstant(instant)
        }
    }

    fun parseAny(raw: Any?): String? = when (raw) {
        null -> null
        is com.google.firebase.Timestamp -> raw.toDate().toInstant().toString()
        is String -> raw.takeIf { it.isNotBlank() }
        is Map<*, *> -> {
            val sec = (raw["_seconds"] as? Number)?.toLong() ?: (raw["seconds"] as? Number)?.toLong()
            val nano = (raw["_nanoseconds"] as? Number)?.toLong() ?: (raw["nanoseconds"] as? Number)?.toLong() ?: 0L
            sec?.let { Instant.ofEpochSecond(it, nano).toString() }
        }
        else -> null
    }
}

object TolerantTimestampSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TolerantTimestamp", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> if (element.isString) element.content else null
            is JsonObject -> {
                val sec = (element["_seconds"] as? JsonPrimitive)?.longOrNull
                    ?: (element["seconds"] as? JsonPrimitive)?.longOrNull
                val nano = (element["_nanoseconds"] as? JsonPrimitive)?.longOrNull
                    ?: (element["nanoseconds"] as? JsonPrimitive)?.longOrNull
                    ?: 0L
                sec?.let { Instant.ofEpochSecond(it, nano).toString() }
            }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeString("") else encoder.encodeString(value)
    }
}
