package com.melox.player.data.library

import android.content.Context
import android.net.Uri
import com.kyant.taglib.TagLib

/** Reads embedded artwork through the same TagLib backend used for WAV metadata. */
internal fun readEmbeddedArtworkData(
    context: Context,
    contentUri: String,
): ByteArray? = runCatching {
    context.contentResolver.openFileDescriptor(Uri.parse(contentUri), "r")?.use { descriptor ->
        TagLib.getFrontCover(descriptor.dup().detachFd())
            ?.data
            ?.takeIf(ByteArray::isNotEmpty)
    }
}.getOrNull() ?: context.contentResolver.readWavMetadata(contentUri)?.artwork
