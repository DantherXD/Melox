package com.melox.player

import com.melox.player.data.library.buildMusicLibraryStatistics
import com.melox.player.model.AudioQuality
import com.melox.player.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicLibraryStatisticsTest {
    @Test
    fun statisticsIncludesEveryQualityAndClampsNegativeBytes() {
        val statistics = buildMusicLibraryStatistics(
            listOf(
                track(1L, "raw.flac", 40L, "audio/flac", 192_000, 32),
                track(2L, "hires.flac", 30L, "audio/flac", 96_000, 24),
                track(3L, "lossless.flac", 20L, "audio/flac", 48_000, 16),
                track(4L, "high.mp3", 10L, "audio/mpeg", bitrate = 320_000),
                track(5L, "other.mp3", -10L, "audio/mpeg", bitrate = 128_000),
            ),
        )

        assertEquals(5, statistics.totalTrackCount)
        assertEquals(100L, statistics.totalBytes)
        assertEquals(
            listOf(AudioQuality.RAW, AudioQuality.HI_RES, AudioQuality.SQ, AudioQuality.HQ, null),
            statistics.quality.map { it.quality },
        )
        assertEquals(listOf(40L, 30L, 20L, 10L, 0L), statistics.quality.map { it.totalBytes })
    }

    @Test
    fun statisticsKeepsEveryResolvedFormatAndOnlyMergesUnknown() {
        val statistics = buildMusicLibraryStatistics(
            listOf(
                track(1L, "one.flac", 60L, "audio/flac"),
                track(2L, "two.mp3", 50L, "audio/mpeg"),
                track(3L, "three.aac", 40L, "audio/aac"),
                track(4L, "four.wav", 30L, "audio/wav"),
                track(5L, "five.alac", 20L, "audio/alac"),
                track(6L, "six.ape", 10L, "audio/ape"),
                track(7L, null, 5L, "audio/ogg; codecs=vorbis"),
                track(8L, null, 4L, null),
            ),
        )

        assertEquals(
            listOf("FLAC", "MP3", "AAC", "WAV", "ALAC", "APE", "OGG", null),
            statistics.formats.map { it.format },
        )
        assertEquals(1, statistics.formats.last().trackCount)
        assertEquals(4L, statistics.formats.last().totalBytes)
    }

    @Test
    fun statisticsCanExposeMoreThanNineFormats() {
        val statistics = buildMusicLibraryStatistics(
            (0 until 10).map { index ->
                track(
                    id = index.toLong(),
                    fileName = "track.f$index",
                    fileSizeBytes = (10 - index).toLong(),
                    mimeType = "audio/f$index",
                )
            },
        )

        assertEquals(10, statistics.formats.size)
        assertEquals((0 until 10).map { "F$it" }, statistics.formats.map { it.format })
    }

    @Test
    fun filenameExtensionTakesPrecedenceOverMimeSubtype() {
        val statistics = buildMusicLibraryStatistics(
            listOf(track(1L, "track.m4a", 10L, "audio/mp4")),
        )

        assertEquals("M4A", statistics.formats.single().format)
    }

    @Test
    fun formatsSortBySongCountBeforeStoredBytes() {
        val statistics = buildMusicLibraryStatistics(
            listOf(
                track(1L, "large.flac", 1_000L, "audio/flac"),
                track(2L, "small-one.mp3", 1L, "audio/mpeg"),
                track(3L, "small-two.mp3", 1L, "audio/mpeg"),
            ),
        )

        assertEquals(listOf("MP3", "FLAC"), statistics.formats.map { it.format })
    }

    @Test
    fun formatsBySizeSortBeforeSongCount() {
        val statistics = buildMusicLibraryStatistics(
            listOf(
                track(1L, "large.flac", 1_000L, "audio/flac"),
                track(2L, "small-one.mp3", 1L, "audio/mpeg"),
                track(3L, "small-two.mp3", 1L, "audio/mpeg"),
            ),
        )

        assertEquals(listOf("FLAC", "MP3"), statistics.formatsBySize.map { it.format })
    }

    private fun track(
        id: Long,
        fileName: String?,
        fileSizeBytes: Long,
        mimeType: String?,
        sampleRate: Int? = null,
        bitDepth: Int? = null,
        bitrate: Int? = null,
    ) = MusicTrack(
        id = id,
        title = null,
        artist = null,
        album = null,
        durationMs = 180_000L,
        dateAddedEpochSeconds = 0L,
        dateModifiedEpochSeconds = 0L,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        contentUri = "content://track/$id",
        titleSectionKey = "#",
        titleSortKey = id.toString(),
        mimeType = mimeType,
        bitrateBitsPerSecond = bitrate,
        sampleRateHz = sampleRate,
        bitDepth = bitDepth,
    )
}
