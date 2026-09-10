package com.melox.player.data.repository

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.melox.player.data.playlist.PlaylistSnapshotCodec
import com.melox.player.model.LocalPlaylist
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns local-playlist persistence and read-access checks, independently from playback state. */
internal class PlaylistRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val atomicFile = AtomicFile(
        File(applicationContext.noBackupFilesDir, FILE_NAME),
    )

    suspend fun load(): List<LocalPlaylist> = withContext(Dispatchers.IO) {
        if (!atomicFile.baseFile.exists()) return@withContext emptyList()
        if (atomicFile.baseFile.length() > MAX_SNAPSHOT_BYTES) {
            Log.w(TAG, "Ignoring oversized playlist snapshot")
            atomicFile.delete()
            return@withContext emptyList()
        }
        try {
            atomicFile.openRead().use(PlaylistSnapshotCodec::read)
        } catch (exception: IOException) {
            Log.w(TAG, "Ignoring unreadable playlist snapshot", exception)
            atomicFile.delete()
            emptyList()
        }
    }

    suspend fun save(playlists: List<LocalPlaylist>) = withContext(Dispatchers.IO) {
        if (playlists.isEmpty()) {
            atomicFile.delete()
            return@withContext
        }
        val output = atomicFile.startWrite()
        try {
            PlaylistSnapshotCodec.write(output, playlists)
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
    }

    suspend fun readableContentUris(contentUris: Collection<String>): Set<String> =
        withContext(Dispatchers.IO) {
            contentUris.asSequence()
                .distinct()
                .filterTo(mutableSetOf()) { contentUri ->
                    runCatching {
                        applicationContext.contentResolver
                            .openFileDescriptor(Uri.parse(contentUri), "r")
                            ?.use { true }
                            ?: false
                    }.getOrDefault(false)
                }
        }

    private companion object {
        const val TAG = "PlaylistRepository"
        const val FILE_NAME = "local_playlists.bin"
        const val MAX_SNAPSHOT_BYTES = 64L * 1024L * 1024L
    }
}

