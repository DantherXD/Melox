package com.melox.player.model

import java.util.Locale

enum class AudioQuality {
    RAW,
    HI_RES,
    SQ,
    HQ,
}

internal fun MusicTrack.resolveAudioQuality(): AudioQuality? {
    val extension = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    val normalizedMimeType = mimeType?.lowercase(Locale.ROOT).orEmpty()
    val isLossless = normalizedMimeType in LOSSLESS_MIME_TYPES ||
        extension in LOSSLESS_FILE_EXTENSIONS

    return when {
        isLossless &&
            bitDepth?.let { it >= RAW_BIT_DEPTH } == true &&
            sampleRateHz?.let { it >= RAW_SAMPLE_RATE_HZ } == true -> AudioQuality.RAW
        isLossless &&
            bitDepth?.let { it >= HIGH_RESOLUTION_BIT_DEPTH } == true &&
            sampleRateHz?.let { it >= HIGH_RESOLUTION_SAMPLE_RATE_HZ } == true ->
            AudioQuality.HI_RES
        isLossless -> AudioQuality.SQ
        bitrateBitsPerSecond?.let { it >= HIGH_QUALITY_BITRATE_BITS_PER_SECOND } == true ->
            AudioQuality.HQ
        else -> null
    }
}

private const val RAW_BIT_DEPTH = 32
private const val RAW_SAMPLE_RATE_HZ = 192_000
private const val HIGH_RESOLUTION_BIT_DEPTH = 24
private const val HIGH_RESOLUTION_SAMPLE_RATE_HZ = 48_000
private const val HIGH_QUALITY_BITRATE_BITS_PER_SECOND = 320_000

private val LOSSLESS_MIME_TYPES = setOf(
    "audio/aiff",
    "audio/alac",
    "audio/ape",
    "audio/flac",
    "audio/wav",
    "audio/x-aiff",
    "audio/x-alac",
    "audio/x-ape",
    "audio/x-flac",
    "audio/x-wav",
)

private val LOSSLESS_FILE_EXTENSIONS = setOf(
    "aif",
    "aiff",
    "ape",
    "flac",
    "wav",
    "wave",
)
