package com.melox.player.data.playlist

import com.melox.player.model.LocalPlaylist
import com.melox.player.model.MusicTrack
import com.melox.player.model.PlaylistTrackEntry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream

/** Compact, checksummed storage for local playlists and unavailable-track metadata. */
internal object PlaylistSnapshotCodec {
    private const val MAGIC = 0x4D455850
    private const val VERSION = 1
    private const val MAX_PLAYLIST_COUNT = 10_000
    private const val MAX_TOTAL_ENTRY_COUNT = 100_000
    private const val MAX_STRING_BYTES = 1_048_576

    fun write(
        outputStream: OutputStream,
        playlists: List<LocalPlaylist>,
    ) {
        require(playlists.size <= MAX_PLAYLIST_COUNT) {
            "Playlist snapshot contains too many playlists: ${playlists.size}"
        }
        require(playlists.sumOf { it.entries.size } <= MAX_TOTAL_ENTRY_COUNT) {
            "Playlist snapshot contains too many entries"
        }

        val checksum = CRC32()
        val output = DataOutputStream(
            CheckedOutputStream(BufferedOutputStream(outputStream), checksum),
        )
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(playlists.size)
        playlists.forEach { playlist ->
            output.writeSizedString(playlist.id)
            output.writeSizedString(playlist.name)
            output.writeLong(playlist.createdAtEpochMillis)
            output.writeLong(playlist.updatedAtEpochMillis)
            output.writeInt(playlist.entries.size)
            playlist.entries.forEach { entry ->
                output.writeSizedString(entry.id)
                output.writeLong(entry.addedAtEpochMillis)
                output.writeMusicTrack(entry.trackSnapshot)
            }
        }
        output.writeLong(checksum.value)
        output.flush()
    }

    fun read(inputStream: InputStream): List<LocalPlaylist> {
        val checksum = CRC32()
        val input = DataInputStream(
            CheckedInputStream(BufferedInputStream(inputStream), checksum),
        )
        if (input.readInt() != MAGIC) throw IOException("Unrecognized playlist snapshot")
        val version = input.readInt()
        if (version != VERSION) {
            throw IOException("Unsupported playlist snapshot version: $version")
        }
        val playlistCount = input.readInt()
        if (playlistCount !in 0..MAX_PLAYLIST_COUNT) {
            throw IOException("Invalid playlist count: $playlistCount")
        }

        var totalEntryCount = 0
        val playlistIds = HashSet<String>(playlistCount)
        val playlists = List(playlistCount) {
            val playlistId = input.readSizedString().requireNotBlank("playlist ID")
            if (!playlistIds.add(playlistId)) {
                throw IOException("Duplicate playlist ID: $playlistId")
            }
            val name = input.readSizedString().requireNotBlank("playlist name")
            val createdAt = input.readLong().requireNonNegative("playlist creation time")
            val updatedAt = input.readLong().requireNonNegative("playlist update time")
            val entryCount = input.readInt()
            if (entryCount !in 0..MAX_TOTAL_ENTRY_COUNT) {
                throw IOException("Invalid playlist entry count: $entryCount")
            }
            totalEntryCount += entryCount
            if (totalEntryCount > MAX_TOTAL_ENTRY_COUNT) {
                throw IOException("Playlist snapshot contains too many entries")
            }
            val entryIds = HashSet<String>(entryCount)
            val contentUris = HashSet<String>(entryCount)
            val entries = List(entryCount) {
                val entryId = input.readSizedString().requireNotBlank("playlist entry ID")
                if (!entryIds.add(entryId)) {
                    throw IOException("Duplicate playlist entry ID: $entryId")
                }
                val addedAt = input.readLong().requireNonNegative("playlist entry time")
                val track = input.readMusicTrack()
                if (!contentUris.add(track.contentUri)) {
                    throw IOException("Duplicate playlist content URI")
                }
                PlaylistTrackEntry(
                    id = entryId,
                    addedAtEpochMillis = addedAt,
                    trackSnapshot = track,
                )
            }
            LocalPlaylist(
                id = playlistId,
                name = name,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = updatedAt,
                entries = entries,
            )
        }
        val actualChecksum = checksum.value
        val expectedChecksum = input.readLong()
        if (actualChecksum != expectedChecksum) {
            throw IOException("Playlist snapshot checksum mismatch")
        }
        return playlists
    }

    private fun DataOutputStream.writeMusicTrack(track: MusicTrack) {
        writeLong(track.id)
        writeNullableString(track.title)
        writeNullableString(track.artist)
        writeNullableString(track.album)
        writeNullableString(track.albumArtist)
        writeInt(track.year ?: 0)
        writeInt(track.trackNumber ?: 0)
        writeInt(track.discNumber ?: 0)
        writeLong(track.durationMs)
        writeLong(track.dateAddedEpochSeconds)
        writeLong(track.dateModifiedEpochSeconds)
        writeNullableString(track.fileName)
        writeLong(track.fileSizeBytes)
        writeSizedString(track.contentUri)
        writeSizedString(track.titleSectionKey)
        writeSizedString(track.titleSortKey)
        writeNullableString(track.folderPath)
        writeLong(track.albumId ?: 0L)
        writeLong(track.mediaStoreId ?: 0L)
        writeNullableString(track.mimeType)
        writeInt(track.bitrateBitsPerSecond ?: 0)
        writeInt(track.sampleRateHz ?: 0)
        writeInt(track.channelCount ?: 0)
        writeInt(track.bitDepth ?: 0)
        writeBoolean(track.audioPropertiesScanned)
    }

    private fun DataInputStream.readMusicTrack(): MusicTrack {
        val id = readLong()
        val title = readNullableString()
        val artist = readNullableString()
        val album = readNullableString()
        val albumArtist = readNullableString()
        val year = readInt().takeIf { it > 0 }
        val trackNumber = readInt().takeIf { it > 0 }
        val discNumber = readInt().takeIf { it > 0 }
        val durationMs = readLong().requireNonNegative("track duration")
        val dateAdded = readLong().requireNonNegative("track added time")
        val dateModified = readLong().requireNonNegative("track modified time")
        val fileName = readNullableString()
        val fileSize = readLong().requireNonNegative("track file size")
        val contentUri = readSizedString().requireNotBlank("track content URI")
        val titleSectionKey = readSizedString()
        val titleSortKey = readSizedString()
        val folderPath = readNullableString()
        val albumId = readLong().takeIf { it > 0L }
        val mediaStoreId = readLong().takeIf { it > 0L }
        val mimeType = readNullableString()
        val bitrate = readInt().takeIf { it > 0 }
        val sampleRate = readInt().takeIf { it > 0 }
        val channelCount = readInt().takeIf { it > 0 }
        val bitDepth = readInt().takeIf { it > 0 }
        val audioPropertiesScanned = readBoolean()
        return MusicTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            durationMs = durationMs,
            dateAddedEpochSeconds = dateAdded,
            dateModifiedEpochSeconds = dateModified,
            fileName = fileName,
            fileSizeBytes = fileSize,
            contentUri = contentUri,
            titleSectionKey = titleSectionKey,
            titleSortKey = titleSortKey,
            folderPath = folderPath,
            albumId = albumId,
            mediaStoreId = mediaStoreId,
            mimeType = mimeType,
            bitrateBitsPerSecond = bitrate,
            sampleRateHz = sampleRate,
            channelCount = channelCount,
            bitDepth = bitDepth,
            audioPropertiesScanned = audioPropertiesScanned,
        )
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeSizedString(value)
    }

    private fun DataOutputStream.writeSizedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) {
            "Playlist snapshot string is too large: ${bytes.size} bytes"
        }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readSizedString() else null

    private fun DataInputStream.readSizedString(): String {
        val byteCount = readInt()
        if (byteCount !in 0..MAX_STRING_BYTES) {
            throw IOException("Invalid playlist string size: $byteCount")
        }
        return ByteArray(byteCount).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun String.requireNotBlank(label: String): String {
        if (isBlank()) throw IOException("Invalid blank $label")
        return this
    }

    private fun Long.requireNonNegative(label: String): Long {
        if (this < 0L) throw IOException("Invalid $label: $this")
        return this
    }
}

