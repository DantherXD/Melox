package com.melox.player

import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import com.melox.player.data.library.MusicLibrarySnapshotCodec
import com.melox.player.data.library.readWavMetadata
import com.melox.player.model.MusicTrack
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

@UnstableApi
class WavMetadataReaderTest {
    @Test
    fun suppliedWavFilesExposeOnlyTheirActualTagsAndCover() {
        val directory = System.getenv("MELOX_WAV_REGRESSION_DIR")
        assumeNotNull(directory)
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(false)
        try {
            val first = File(directory, "\u4e0d\u8bf4 - AI\u4e1c\u96ea\u83b2.wav")
                .inputStream().use(::readWavMetadata)!!
            assertEquals("\u4e0d\u8bf4", first.tags.title)
            assertEquals("\u4e1c\u6d0b\u96ea\u83b2", first.tags.artist)
            assertEquals("AI\u4e1c\u96ea\u83b2\uff08\u7cbe\u9009\uff09", first.tags.album)
            assertEquals(137380, first.artwork!!.size)
            val image = javax.imageio.ImageIO.read(first.artwork.inputStream())
            assertTrue(image.width > 0 && image.height > 0)
            val second = File(directory, "\u5fc3\u5899.wav")
                .inputStream().use(::readWavMetadata)!!
            assertEquals("\u5fc3\u5899", second.tags.title)
            assertEquals("AI\u4e1c\u96ea\u83b2", second.tags.artist)
            assertEquals(2, second.tags.discNumber)
            assertNull(second.tags.album)
            assertNull(second.artwork)
        } finally {
            ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
        }
    }

    @Test
    fun malformedAndNonWavInputsReturnNoMetadata() {
        assertNull(readWavMetadata(byteArrayOf(1, 2).inputStream()))
        assertNull(readWavMetadata(ByteArray(32).inputStream()))
        val malformed = "52494646140000005741564569643320ffffffff".hexBytes()
        assertNull(readWavMetadata(malformed.inputStream()))
    }

    @Test
    fun oddSizedChunksAreSkippedBeforeId3() {
        val title = byteArrayOf(3) + "WAV title".toByteArray()
        val frame = "TIT2".toByteArray() + ByteBuffer.allocate(4).putInt(title.size).array() +
            byteArrayOf(0, 0) + title
        val id3 = byteArrayOf(73, 68, 51, 3, 0, 0, 0, 0, 0, frame.size.toByte()) + frame
        val body = "WAVE".toByteArray() + chunk("JUNK", byteArrayOf(1)) + chunk("id3 ", id3)
        val wav = "RIFF".toByteArray() + leInt(body.size) + body
        ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(false)
        try {
            assertEquals("WAV title", readWavMetadata(wav.inputStream())!!.tags.title)
            assertNull(readWavMetadata(wav.copyOf(wav.size - 5).inputStream()))
        } finally {
            ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
        }
    }

    @Test
    fun oldSnapshotInvalidatesOnlyWavMetadataAndNewSnapshotRetainsIt() {
        val wav = MusicTrack(
            id = 1, title = "Old", artist = null, album = null, durationMs = 1,
            dateAddedEpochSeconds = 0, dateModifiedEpochSeconds = 0,
            fileName = "sample.wav", fileSizeBytes = 10, contentUri = "content://audio/1",
            titleSectionKey = "O", titleSortKey = "Old", audioPropertiesScanned = true,
        )
        val tracks = listOf(wav, wav.copy(id = 2, fileName = "sample.mp3"))
        val bytes = ByteArrayOutputStream().also { MusicLibrarySnapshotCodec.write(it, tracks) }.toByteArray()
        assertTrue(MusicLibrarySnapshotCodec.read(bytes.inputStream()).first().audioPropertiesScanned)
        ByteBuffer.wrap(bytes).putInt(4, 8)
        val crc = CRC32().apply { update(bytes, 0, bytes.size - 8) }
        ByteBuffer.wrap(bytes).putLong(bytes.size - 8, crc.value)
        val restored = MusicLibrarySnapshotCodec.read(bytes.inputStream())
        assertFalse(restored[0].audioPropertiesScanned)
        assertTrue(restored[1].audioPropertiesScanned)
    }

    private fun chunk(id: String, data: ByteArray) =
        id.toByteArray() + leInt(data.size) + data + ByteArray(data.size % 2)

    private fun leInt(value: Int) = ByteBuffer.allocate(4)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun String.hexBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
