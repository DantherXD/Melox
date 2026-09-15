package com.melox.player.ui.component.playback

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.melox.player.ui.component.library.createArtworkCacheKey
import com.melox.player.ui.component.library.loadArtworkBitmap
import com.melox.player.ui.component.library.loadCachedArtworkDerivative
import com.melox.player.ui.component.library.normalizeArtworkTargetSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PlaybackArtworkResource(
    val cacheKey: String,
    val blurredCacheKey: String,
    val artwork: Bitmap?,
    val blurredArtwork: Bitmap?,
    val backgroundColor: Color,
    val isLoading: Boolean,
)

@Composable
internal fun rememberPlaybackArtworkResource(
    contentUri: String,
    dateModifiedEpochSeconds: Long,
    fileSizeBytes: Long,
    requestSize: Dp,
): PlaybackArtworkResource {
    val context = LocalContext.current.applicationContext
    val targetSizePx = normalizeArtworkTargetSize(
        with(LocalDensity.current) { requestSize.roundToPx() },
    )
    val cacheKey = remember(contentUri, dateModifiedEpochSeconds, fileSizeBytes, targetSizePx) {
        createArtworkCacheKey(
            contentUri = contentUri,
            dateModifiedEpochSeconds = dateModifiedEpochSeconds,
            fileSizeBytes = fileSizeBytes,
            targetSizePx = targetSizePx,
        )
    }
    val blurredCacheKey = remember(contentUri, dateModifiedEpochSeconds, fileSizeBytes) {
        createBlurredArtworkLayerKey(
            contentUri = contentUri,
            dateModifiedEpochSeconds = dateModifiedEpochSeconds,
            fileSizeBytes = fileSizeBytes,
        )
    }
    var resource by remember {
        mutableStateOf(
            PlaybackArtworkResource(
                cacheKey = cacheKey,
                blurredCacheKey = blurredCacheKey,
                artwork = null,
                blurredArtwork = null,
                backgroundColor = PlaybackArtworkFallbackColor,
                isLoading = contentUri.isNotBlank(),
            ),
        )
    }

    LaunchedEffect(cacheKey, blurredCacheKey) {
        if (contentUri.isBlank()) {
            resource = PlaybackArtworkResource(
                cacheKey = cacheKey,
                blurredCacheKey = blurredCacheKey,
                artwork = null,
                blurredArtwork = null,
                backgroundColor = PlaybackArtworkFallbackColor,
                isLoading = false,
            )
            return@LaunchedEffect
        }
        resource = resource.copy(isLoading = true)
        resource = loadPlaybackArtworkResource(
            context = context,
            contentUri = contentUri,
            dateModifiedEpochSeconds = dateModifiedEpochSeconds,
            fileSizeBytes = fileSizeBytes,
            targetSizePx = targetSizePx,
        )
    }

    return resource
}

internal suspend fun prefetchPlaybackArtworkResource(
    context: Context,
    contentUri: String,
    dateModifiedEpochSeconds: Long,
    fileSizeBytes: Long,
    targetSizePx: Int,
): PlaybackArtworkResource = loadPlaybackArtworkResource(
    context = context.applicationContext,
    contentUri = contentUri,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    fileSizeBytes = fileSizeBytes,
    targetSizePx = targetSizePx,
)

private suspend fun loadPlaybackArtworkResource(
    context: Context,
    contentUri: String,
    dateModifiedEpochSeconds: Long,
    fileSizeBytes: Long,
    targetSizePx: Int,
): PlaybackArtworkResource {
    val normalizedTargetSizePx = normalizeArtworkTargetSize(targetSizePx)
    val cacheKey = createArtworkCacheKey(
        contentUri = contentUri,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        fileSizeBytes = fileSizeBytes,
        targetSizePx = normalizedTargetSizePx,
    )
    val blurredCacheKey = createBlurredArtworkLayerKey(
        contentUri = contentUri,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        fileSizeBytes = fileSizeBytes,
    )
    val artwork = loadArtworkBitmap(
        context = context,
        contentUri = contentUri,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        fileSizeBytes = fileSizeBytes,
        targetSizePx = normalizedTargetSizePx,
    )
    val backgroundColor = withContext(Dispatchers.Default) {
        dynamicFlowBackgroundColor(artwork)
    }
    val blurredArtwork = artwork?.let { source ->
        loadCachedArtworkDerivative(
            context = context,
            cacheKey = blurredCacheKey,
        ) {
            createBlurredArtwork(source)
        }
    }
    return PlaybackArtworkResource(
        cacheKey = cacheKey,
        blurredCacheKey = blurredCacheKey,
        artwork = artwork,
        blurredArtwork = blurredArtwork,
        backgroundColor = backgroundColor,
        isLoading = false,
    )
}

private val PlaybackArtworkFallbackColor = Color(0xFF242424)
