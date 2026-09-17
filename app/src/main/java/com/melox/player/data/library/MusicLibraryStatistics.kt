package com.melox.player.data.library

import com.melox.player.model.AudioQuality
import com.melox.player.model.MusicTrack
import com.melox.player.model.resolveAudioQuality
import java.util.Locale

internal data class QualityStatistic(
    val quality: AudioQuality?,
    val trackCount: Int,
    val totalBytes: Long,
)

internal data class FormatStatistic(
    val format: String?,
    val trackCount: Int,
    val totalBytes: Long,
)

internal data class MusicLibraryStatistics(
    val totalTrackCount: Int,
    val totalBytes: Long,
    val quality: List<QualityStatistic>,
    val formats: List<FormatStatistic>,
    val formatsBySize: List<FormatStatistic>,
)

internal fun buildMusicLibraryStatistics(
    tracks: List<MusicTrack>,
): MusicLibraryStatistics {
    val qualityTotals = linkedMapOf<AudioQuality?, MutableStatistic>()
    val formatTotals = linkedMapOf<String?, MutableStatistic>()

    tracks.forEach { track ->
        val safeBytes = track.fileSizeBytes.coerceAtLeast(0L)
        qualityTotals.getOrPut(track.resolveAudioQuality(), ::MutableStatistic)
            .add(safeBytes)
        formatTotals.getOrPut(track.statisticsFormat(), ::MutableStatistic)
            .add(safeBytes)
    }

    val qualityOrder = listOf(
        AudioQuality.RAW,
        AudioQuality.HI_RES,
        AudioQuality.SQ,
        AudioQuality.HQ,
        null,
    )
    val quality = qualityOrder.mapNotNull { key ->
        qualityTotals[key]?.let { total ->
            QualityStatistic(
                quality = key,
                trackCount = total.trackCount,
                totalBytes = total.totalBytes,
            )
        }
    }
    val formats = buildFormatStatistics(
        totals = formatTotals,
        comparator = compareByDescending<FormatStatistic>(FormatStatistic::trackCount)
            .thenByDescending(FormatStatistic::totalBytes)
            .thenBy { it.format ?: "" },
    )
    val formatsBySize = buildFormatStatistics(
        totals = formatTotals,
        comparator = compareByDescending<FormatStatistic>(FormatStatistic::totalBytes)
            .thenByDescending(FormatStatistic::trackCount)
            .thenBy { it.format ?: "" },
    )

    return MusicLibraryStatistics(
        totalTrackCount = tracks.size,
        totalBytes = tracks.sumOf { it.fileSizeBytes.coerceAtLeast(0L) },
        quality = quality,
        formats = formats,
        formatsBySize = formatsBySize,
    )
}

private fun buildFormatStatistics(
    totals: Map<String?, MutableStatistic>,
    comparator: Comparator<FormatStatistic>,
): List<FormatStatistic> {
    val sortedFormats = totals
        .filterKeys { it != null }
        .map { (format, total) ->
            FormatStatistic(
                format = format,
                trackCount = total.trackCount,
                totalBytes = total.totalBytes,
            )
        }
        .sortedWith(comparator)
    val unknownFormat = totals[null]
    val otherTrackCount = unknownFormat?.trackCount ?: 0
    val otherBytes = unknownFormat?.totalBytes ?: 0L
    return if (otherTrackCount == 0) {
        sortedFormats
    } else {
        sortedFormats + FormatStatistic(
            format = null,
            trackCount = otherTrackCount,
            totalBytes = otherBytes,
        )
    }
}

private fun MusicTrack.statisticsFormat(): String? {
    val extension = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (extension != null) return extension.uppercase(Locale.ROOT)

    return mimeType
        ?.substringAfter('/', missingDelimiterValue = "")
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.uppercase(Locale.ROOT)
}

private data class MutableStatistic(
    var trackCount: Int = 0,
    var totalBytes: Long = 0L,
) {
    fun add(bytes: Long) {
        trackCount += 1
        totalBytes += bytes
    }
}
