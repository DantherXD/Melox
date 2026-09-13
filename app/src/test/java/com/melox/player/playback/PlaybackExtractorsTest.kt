package com.melox.player.playback

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.FlacStreamMetadata
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.melox.player.data.lyrics.LyricsParser
import com.melox.player.model.LyricsDocument
import com.melox.player.model.LyricsSource
import java.io.File
import org.junit.Assume.assumeNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class PlaybackExtractorsTest {
    @Test
    fun extractorRead_localRegressionFileEmitsCompleteAudioAndLyrics() {
        val path = System.getenv("MELOX_FLAC_REGRESSION_FILE")
        assumeNotNull(path)
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(false)
        try {
            val file = File(requireNotNull(path))
            file.inputStream().use { stream ->
                val input = DefaultExtractorInput(
                    DataReader { target, offset, length -> stream.read(target, offset, length) },
                    0L,
                    file.length(),
                )
                val extractor = ResynchronizingFlacExtractor(FlacExtractor())
                var samples = 0
                var lastTimeUs = -1L
                var lyrics: LyricsDocument? = null
                val output = object : ForwardingTrackOutput(DiscardingTrackOutput()) {
                    override fun format(format: Format) {
                        val metadata = format.metadata ?: return
                        for (index in 0 until metadata.length()) {
                            val entry = metadata[index] as? VorbisComment ?: continue
                            if (entry.key == "LYRICS") {
                                lyrics = LyricsParser.parse(
                                    entry.value,
                                    LyricsSource.EMBEDDED,
                                    durationMs = 207_937L,
                                )
                            }
                        }
                    }

                    override fun sampleMetadata(
                        timeUs: Long, flags: Int, size: Int, offset: Int,
                        cryptoData: TrackOutput.CryptoData?,
                    ) {
                        samples++
                        lastTimeUs = timeUs
                    }
                }
                extractor.init(object : ExtractorOutput {
                    override fun track(id: Int, type: Int) = output
                    override fun endTracks() = Unit
                    override fun seekMap(seekMap: SeekMap) = Unit
                })
                assertTrue(extractor.sniff(input))
                input.resetPeekPosition()
                val position = PositionHolder()
                var result = Extractor.RESULT_CONTINUE
                var reads = 0
                while (result != Extractor.RESULT_END_OF_INPUT && reads++ < 100_000) {
                    result = extractor.read(input, position)
                    assertTrue(result != Extractor.RESULT_SEEK)
                }
                assertEquals(Extractor.RESULT_END_OF_INPUT, result)
                assertEquals(8956, samples)
                assertEquals(207_934_693L, lastTimeUs)
                val document = requireNotNull(lyrics)
                assertEquals(LyricsSource.EMBEDDED, document.source)
                assertEquals(0L, document.lines.first().startTimeMs)
                assertEquals(199_230L, document.lines.last().startTimeMs)
                assertTrue(document.lines.size > 60)
                extractor.release()
            }
        } finally {
            ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
        }
    }

    @Test
    fun extractorRead_recoversJunkBeforeFirstFrame() {
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(false)
        try {
            val streamInfo =
                "664c6143800000220400040000006c000d8a0ac442f0008bec67b41e462155d7c513fdc7236dcc96ccc6"
                    .hexBytes()
            val frame = "fff8a9a8004840000094c3a00ea5131328".hexBytes()
            val data = streamInfo + ByteArray(128_000) { 0x57 } + frame
            val stream = data.inputStream()
            val input = DefaultExtractorInput(
                DataReader { target, offset, length -> stream.read(target, offset, length) },
                0L,
                data.size.toLong(),
            )
            val extractor = ResynchronizingFlacExtractor(FlacExtractor())
            var seekMapReceived = false
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int) = DiscardingTrackOutput()
                override fun endTracks() = Unit
                override fun seekMap(seekMap: SeekMap) { seekMapReceived = true }
            })
            assertTrue(extractor.sniff(input))
            input.resetPeekPosition()
            val position = PositionHolder()
            var result = Extractor.RESULT_CONTINUE
            var reads = 0
            while (result != Extractor.RESULT_END_OF_INPUT && reads++ < 100) {
                result = extractor.read(input, position)
                assertTrue(result != Extractor.RESULT_SEEK)
            }
            assertEquals(Extractor.RESULT_END_OF_INPUT, result)
            assertTrue(seekMapReceived)
            extractor.release()
        } finally {
            ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
        }
    }

    @Test
    fun resynchronizeFlacFrame_skipsJunkAndRejectsFalseSyncWords() {
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(false)
        try {
            val streamInfo =
                "664c6143000000220400040000006c000d8a0ac442f0008bec67b41e462155d7c513fdc7236dcc96ccc6"
                    .hexBytes()
            val validFrameHeader = "fff8a9a8004840000094c3a00ea5131328".hexBytes()
            val junk = ByteArray(73) { index -> (index * 31).toByte() }.apply {
                this[11] = 0xFF.toByte()
                this[12] = 0xF8.toByte()
            }
            val data = junk + validFrameHeader
            var readPosition = 0
            val input = DefaultExtractorInput(
                DataReader { target, offset, length ->
                    if (readPosition == data.size) {
                        C.RESULT_END_OF_INPUT
                    } else {
                        val bytesRead = minOf(length, data.size - readPosition)
                        data.copyInto(target, offset, readPosition, readPosition + bytesRead)
                        readPosition += bytesRead
                        bytesRead
                    }
                },
                /* position = */ 0L,
                data.size.toLong(),
            )

            assertTrue(
                resynchronizeFlacFrame(
                    input,
                    FlacStreamMetadata(streamInfo, /* offset = */ 8),
                ),
            )
            assertEquals(junk.size.toLong(), input.position)
        } finally {
            ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
        }
    }
}

private fun String.hexBytes(): ByteArray =
    chunked(2).map { byte -> byte.toInt(16).toByte() }.toByteArray()
