package com.melox.player.ui.component.playback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.melox.player.model.PlaybackUiState
import com.melox.player.model.BottomBarStyle
import com.melox.player.ui.MiniPlayerChrome
import com.melox.player.ui.NORMAL_BAR_STROKE_ALPHA
import com.melox.player.ui.component.library.PlaybackArtworkFrame
import com.melox.player.ui.component.library.playbackArtworkShadow
import com.melox.player.ui.component.library.playbackArtworkCornerRadius
import com.melox.player.ui.component.library.rememberArtworkBitmap
import com.melox.player.ui.component.liquid.miuixFloatingBarShadow
import com.melox.player.ui.component.liquid.miniPlayerSurface
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.saveable.Saver
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.basic.DividerDefaults
import top.yukonga.miuix.kmp.utils.getRoundedCorner

// Duration of the artwork crossfade when the current track changes.
internal const val PLAYER_TRACK_ARTWORK_CROSSFADE_DURATION_MILLIS = 500
// Keeps the playback-background transition aligned with the artwork crossfade.
internal const val PLAYBACK_BACKGROUND_TRANSITION_DURATION_MILLIS =
    PLAYER_TRACK_ARTWORK_CROSSFADE_DURATION_MILLIS
// Progress at which the mini-player layers have fully handed off to the full player.
internal const val PLAYER_LAYER_HANDOFF_END_PROGRESS = 0.2f
// Progress at which the mini-player's recorded content finishes fading out.
internal const val PLAYER_MINI_CONTENT_FADE_END_PROGRESS = 0.3f
// Progress at which full-player content begins to appear.
internal const val PLAYER_CONTENT_APPEAR_START_PROGRESS = 0.1f
// Progress at which full-player content is fully visible.
internal const val PLAYER_CONTENT_APPEAR_END_PROGRESS = 0.6f
// Linear-motion share used to soften the artwork's vertical travel curve.
private const val PLAYER_ARTWORK_VERTICAL_LINEAR_WEIGHT = 0.4f

// Requested square size for the full-player artwork bitmap.
internal val PLAYER_FULL_ARTWORK_REQUEST_SIZE = 420.dp
// Corner radius of the full-player artwork frame.
internal val PLAYER_FULL_ARTWORK_CORNER_RADIUS = 12.dp
// Corner-radius reduction when the mini player uses rectangular artwork.
internal val MINI_PLAYER_RECTANGULAR_ARTWORK_CORNER_REDUCTION = 2.dp
internal val PLAYER_TRACK_ARTWORK_CROSSFADE_EASING = androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Owns the one progress value shared by the mini player, full player, and
 * artwork overlay. Its endpoint-driven spring allows a close gesture to
 * reverse from the current frame.
 */
@Stable
internal class PlayerSheetTransitionState(initialProgress: Float = 0f) {
    private val restoredProgress = initialProgress.coerceIn(0f, 1f)
    private val progressAnimation = Animatable(restoredProgress, visibilityThreshold = 0.001f)
    private var renderedProgress by mutableFloatStateOf(restoredProgress)
    private var dragStartProgress = 0f
    private var dragDistanceY = 0f
    private var lastDragAmountY = 0f
    private var dragOriginOpen = false
    private var dragStartedFromMiniPlayer = false
    private var requestedInitialVelocity = 0f

    companion object {
        val Saver: Saver<PlayerSheetTransitionState, List<Any>> = Saver(
            save = { state ->
                listOf(
                    if (state.targetOpen) 1f else 0f,
                    state.fullPlayerArtworkPageSelected,
                )
            },
            restore = { savedState ->
                PlayerSheetTransitionState(savedState[0] as Float).apply {
                    updateFullPlayerArtworkPageSelected(savedState[1] as Boolean)
                }
            },
        )
    }

    var targetOpen by mutableStateOf(restoredProgress > 0.5f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    var animationRequest by mutableIntStateOf(0)
        private set

    var miniPlayerBounds by mutableStateOf(Rect.Zero)
        private set

    var miniPlayerContentBounds by mutableStateOf(Rect.Zero)
        private set

    var miniPlayerControlsBounds by mutableStateOf(Rect.Zero)
        private set

    var fullPlayerBounds by mutableStateOf(Rect.Zero)
        private set

    var miniArtworkBounds by mutableStateOf(Rect.Zero)
        private set

    var fullArtworkBounds by mutableStateOf(Rect.Zero)
        private set

    var fullPlayerArtworkPageSelected by mutableStateOf(true)
        private set

    private var layoutWindowSize by mutableStateOf(IntSize.Zero)
    private var miniFrameReady by mutableStateOf(false)
    private var fullBackgroundFrameReady by mutableStateOf(false)
    private var fullContentFrameReady by mutableStateOf(false)
    private var frameRecordingGeneration by mutableIntStateOf(0)
    private var frozenArtworkBounds: Pair<Rect, Rect>? = null

    val currentFrameRecordingGeneration: Int
        get() = frameRecordingGeneration

    val overlayMiniArtworkBounds: Rect
        get() = frozenArtworkBounds?.first ?: miniArtworkBounds
    val overlayFullArtworkBounds: Rect
        get() = frozenArtworkBounds?.second ?: fullArtworkBounds

    fun updateWindowSize(size: IntSize) {
        if (size == layoutWindowSize || size.width <= 0 || size.height <= 0) return
        if (isTransitionActive) {
            isDragging = false
            requestedInitialVelocity = 0f
            animationRequest += 1
        }
        layoutWindowSize = size
        miniPlayerBounds = Rect.Zero
        miniPlayerContentBounds = Rect.Zero
        miniPlayerControlsBounds = Rect.Zero
        fullPlayerBounds = Rect.Zero
        miniArtworkBounds = Rect.Zero
        fullArtworkBounds = Rect.Zero
        miniFrameReady = false
        fullBackgroundFrameReady = false
        fullContentFrameReady = false
        frameRecordingGeneration += 1
        frozenArtworkBounds = null
    }

    fun invalidateCollapsedFullPlayerEndpoint() {
        if (isTransitionActive) return
        fullPlayerBounds = Rect.Zero
        fullArtworkBounds = Rect.Zero
        fullBackgroundFrameReady = false
        fullContentFrameReady = false
        frozenArtworkBounds = null
    }

    fun markMiniFrameRecorded(
        windowSize: IntSize,
        generation: Int,
        size: IntSize,
    ) {
        if (acceptsRecordedFrame(windowSize, generation, size, miniPlayerBounds)) {
            miniFrameReady = true
        }
    }

    fun markFullBackgroundFrameRecorded(
        windowSize: IntSize,
        generation: Int,
        size: IntSize,
    ) {
        if (acceptsRecordedFrame(windowSize, generation, size, fullPlayerBounds)) {
            fullBackgroundFrameReady = true
        }
    }

    fun markFullContentFrameRecorded(
        windowSize: IntSize,
        generation: Int,
        size: IntSize,
    ) {
        if (acceptsRecordedFrame(windowSize, generation, size, fullPlayerBounds)) {
            fullContentFrameReady = true
        }
    }

    fun markFullFrameRecorded(windowSize: IntSize, generation: Int, size: IntSize) {
        markFullBackgroundFrameRecorded(windowSize, generation, size)
        markFullContentFrameRecorded(windowSize, generation, size)
    }

    private fun acceptsRecordedFrame(
        windowSize: IntSize,
        generation: Int,
        size: IntSize,
        bounds: Rect,
    ): Boolean = windowSize == layoutWindowSize &&
        generation == frameRecordingGeneration &&
        size.matches(bounds)

    private fun IntSize.matches(bounds: Rect): Boolean = bounds.isUsable() &&
        abs(width - bounds.width.roundToInt()) <= 1 &&
        abs(height - bounds.height.roundToInt()) <= 1

    private fun freezeArtworkBounds() {
        if (frozenArtworkBounds == null && hasArtworkBounds) {
            frozenArtworkBounds = miniArtworkBounds to fullArtworkBounds
        }
    }

    val progress: Float
        get() = renderedProgress.coerceIn(0f, 1f)

    val hasArtworkBounds: Boolean
        get() = miniArtworkBounds.isUsable() && fullArtworkBounds.isUsable()

    val hasContainerBounds: Boolean
        get() = miniPlayerBounds.isUsable() && fullPlayerBounds.isUsable()

    val sharedLayersReady: Boolean
        get() = hasContainerBounds &&
            (layoutWindowSize == IntSize.Zero ||
                (miniFrameReady && fullBackgroundFrameReady && fullContentFrameReady))

    val separateArtworkOverlayReady: Boolean
        get() = sharedLayersReady && sharedArtworkEnabled && hasArtworkBounds

    val canSettle: Boolean
        get() = hasContainerBounds

    val isMounted: Boolean
        get() = isDragging || targetOpen || progress > 0f

    val isInProgress: Boolean
        get() = isDragging || progress > 0f && progress < 1f

    val isFullyExpanded: Boolean
        get() = !isDragging && progress >= 1f

    val isTransitionActive: Boolean
        get() = isDragging || if (targetOpen) progress < 1f else progress > 0f

    val sharedArtworkEnabled: Boolean
        get() = fullPlayerArtworkPageSelected

    val miniPlayerAcceptsInput: Boolean
        get() = playerSheetMiniPlayerAcceptsInput(
            targetOpen = targetOpen,
            isDragging = isDragging,
            dragStartedFromMiniPlayer = dragStartedFromMiniPlayer,
            progress = progress,
        )

    val fullPlayerHostMounted: Boolean
        get() = isMounted && (isDragging || !sharedLayersReady || !miniPlayerAcceptsInput)

    val fullPlayerDrawsInPlace: Boolean
        get() = if (sharedLayersReady) {
            targetOpen && !isTransitionActive
        } else {
            progress > PLAYER_LAYER_HANDOFF_END_PROGRESS
        }

    val fullPlayerAcceptsInput: Boolean
        get() = !miniPlayerAcceptsInput &&
            (sharedLayersReady || (!isDragging && targetOpen && progress >= 1f))

    val fullPlayerDrawsAboveRoot: Boolean
        get() = sharedLayersReady || progress > PLAYER_LAYER_HANDOFF_END_PROGRESS

    fun open() {
        releaseDragForProgrammaticSettle()
        if (!sharedLayersReady) requestFreshFrameRecording()
        requestSettle(open = true)
    }

    fun close() {
        releaseDragForProgrammaticSettle()
        requestSettle(open = false)
        if (!sharedLayersReady && progress <= 0f) {
            renderedProgress = 0f
            frozenArtworkBounds = null
        }
    }

    fun beginMiniPlayerDrag() {
        beginDrag(startedFromMiniPlayer = true)
    }

    fun beginFullPlayerDrag() {
        beginDrag(startedFromMiniPlayer = false)
    }

    private fun beginDrag(startedFromMiniPlayer: Boolean) {
        if (isDragging) return
        if (!isTransitionActive) frozenArtworkBounds = null
        freezeArtworkBounds()
        val currentProgress = progress
        dragStartProgress = currentProgress
        renderedProgress = dragStartProgress
        dragDistanceY = 0f
        lastDragAmountY = 0f
        dragOriginOpen = targetOpen
        dragStartedFromMiniPlayer = startedFromMiniPlayer
        isDragging = true
        animationRequest += 1
    }

    fun dragBy(dragAmountY: Float) {
        if (!isDragging) return
        dragDistanceY += dragAmountY
        if (dragAmountY != 0f) lastDragAmountY = dragAmountY
        updateDragProgress()
    }

    fun endDrag(velocityY: Float) {
        if (!isDragging) return
        val verticalTravel = playerSheetVerticalTravel(
            source = miniPlayerBounds,
            target = fullPlayerBounds,
        )
        val open = playerSheetDragTarget(
            velocityY = velocityY,
            lastDragAmountY = lastDragAmountY,
            originOpen = dragOriginOpen,
        )
        isDragging = false
        requestSettle(
            open = open,
            initialVelocity = -velocityY / verticalTravel,
        )
    }

    fun cancelDrag() {
        if (!isDragging) return
        isDragging = false
        requestSettle(open = dragOriginOpen)
    }

    fun updateMiniPlayerBounds(bounds: Rect, windowSize: IntSize = layoutWindowSize) {
        if (windowSize != layoutWindowSize) return
        if (bounds.isUsable()) {
            if (miniPlayerBounds.size != bounds.size) miniFrameReady = false
            miniPlayerBounds = bounds
            updateDragProgress()
        }
    }

    fun updateMiniPlayerContentBounds(bounds: Rect, windowSize: IntSize = layoutWindowSize) {
        if (windowSize != layoutWindowSize) return
        if (bounds.isUsable()) miniPlayerContentBounds = bounds
    }

    fun updateMiniPlayerControlsBounds(bounds: Rect, windowSize: IntSize = layoutWindowSize) {
        if (windowSize != layoutWindowSize) return
        if (bounds.isUsable()) miniPlayerControlsBounds = bounds
    }

    fun updateFullPlayerBounds(bounds: Rect, windowSize: IntSize = layoutWindowSize) {
        if (windowSize != layoutWindowSize) return
        if (bounds.isUsable()) {
            if (fullPlayerBounds.size != bounds.size) {
                fullBackgroundFrameReady = false
                fullContentFrameReady = false
            }
            fullPlayerBounds = bounds
            updateDragProgress()
        }
    }

    fun updateMiniArtworkBounds(bounds: Rect, windowSize: IntSize = layoutWindowSize) {
        if (windowSize != layoutWindowSize) return
        if (bounds.isUsable()) {
            miniArtworkBounds = bounds
            frozenArtworkBounds = frozenArtworkBounds?.let { (_, target) ->
                bounds to target
            }
        }
    }

    fun updateFullArtworkBounds(bounds: Rect, windowSize: IntSize = layoutWindowSize) {
        if (windowSize != layoutWindowSize) return
        if (bounds.isUsable()) {
            fullArtworkBounds = bounds
            frozenArtworkBounds = frozenArtworkBounds?.let { (source, _) ->
                source to bounds
            }
        }
    }

    fun updateFullPlayerArtworkPageSelected(selected: Boolean) {
        fullPlayerArtworkPageSelected = selected
    }

    internal suspend fun animateToTarget() {
        freezeArtworkBounds()
        progressAnimation.snapTo(renderedProgress)
        val visibilityThreshold = 0.5f / fullPlayerBounds.height.coerceAtLeast(1f)
        progressAnimation.animateTo(
            targetValue = if (targetOpen) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 500f,
                visibilityThreshold = visibilityThreshold,
            ),
            initialVelocity = requestedInitialVelocity,
        ) {
            if (!isDragging) renderedProgress = value
        }
    }

    private fun updateDragProgress() {
        if (!isDragging || !fullPlayerBounds.isUsable()) return
        renderedProgress = playerSheetDragProgress(
            startProgress = dragStartProgress,
            dragDistanceY = dragDistanceY,
            travelDistance = playerSheetVerticalTravel(
                source = miniPlayerBounds,
                target = fullPlayerBounds,
            ),
        )
    }

    private fun releaseDragForProgrammaticSettle() {
        if (!isDragging) return
        isDragging = false
    }

    private fun requestSettle(open: Boolean, initialVelocity: Float = 0f) {
        if (!isTransitionActive) frozenArtworkBounds = null
        freezeArtworkBounds()
        targetOpen = open
        requestedInitialVelocity = initialVelocity
        animationRequest += 1
    }

    private fun requestFreshFrameRecording() {
        miniFrameReady = false
        fullBackgroundFrameReady = false
        fullContentFrameReady = false
        frameRecordingGeneration += 1
    }

}

@Composable
internal fun rememberPlayerSheetTransitionState(): PlayerSheetTransitionState = rememberSaveable(
    saver = PlayerSheetTransitionState.Saver,
) {
    PlayerSheetTransitionState()
}

@Composable
internal fun rememberPlayerSheetVerticalDragModifier(
    enabled: Boolean,
    hasItem: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    if (!enabled || !hasItem) return Modifier

    return Modifier.pointerInput(enabled, hasItem) {
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                currentOnDragStart()
            },
            onVerticalDrag = { change, dragAmount ->
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                currentOnDrag(dragAmount)
                change.consume()
            },
            onDragEnd = {
                currentOnDragEnd(velocityTracker.calculateVelocity().y)
            },
            onDragCancel = currentOnDragCancel,
        )
    }
}

internal fun playerSheetBarAlpha(progress: Float): Float {
    val handoff = (progress.coerceIn(0f, 1f) / PLAYER_LAYER_HANDOFF_END_PROGRESS)
        .coerceIn(0f, 1f)
    return 1f - easeOutCubic(handoff)
}

internal fun playerSheetMiniContentAlpha(progress: Float): Float {
    val fade = (progress.coerceIn(0f, 1f) / PLAYER_MINI_CONTENT_FADE_END_PROGRESS)
        .coerceIn(0f, 1f)
    return 1f - easeOutCubic(fade)
}

internal fun playerSheetPageAlpha(progress: Float): Float {
    return playerSheetContentAlpha(progress)
}

internal fun playerSheetBackgroundAlpha(progress: Float): Float {
    val handoff = (progress.coerceIn(0f, 1f) / PLAYER_LAYER_HANDOFF_END_PROGRESS)
        .coerceIn(0f, 1f)
    return easeInCubic(handoff)
}

internal fun playerSheetContentAlpha(progress: Float): Float {
    val fraction = (
        (progress.coerceIn(0f, 1f) - PLAYER_CONTENT_APPEAR_START_PROGRESS) /
            (PLAYER_CONTENT_APPEAR_END_PROGRESS - PLAYER_CONTENT_APPEAR_START_PROGRESS)
        ).coerceIn(0f, 1f)
    return easeInCubic(fraction)
}

internal fun playerSheetGlassVisible(progress: Float): Boolean =
    progress.coerceIn(0f, 1f) < PLAYER_LAYER_HANDOFF_END_PROGRESS

internal fun playerSheetUsesFullPlayerStatusBar(
    progress: Float,
): Boolean = progress > PLAYER_LAYER_HANDOFF_END_PROGRESS

internal fun playerSheetMiniPlayerAcceptsInput(
    targetOpen: Boolean,
    isDragging: Boolean,
    dragStartedFromMiniPlayer: Boolean,
    progress: Float,
): Boolean = if (isDragging) {
    dragStartedFromMiniPlayer
} else {
    !targetOpen && progress.coerceIn(0f, 1f) <= PLAYER_LAYER_HANDOFF_END_PROGRESS
}

internal fun Modifier.recordPlayerLayer(
    layer: GraphicsLayer,
    drawInPlace: Boolean,
    recordingGeneration: Int = 0,
    onRecorded: (generation: Int, size: IntSize) -> Unit = { _, _ -> },
): Modifier = drawWithContent {
    layer.record {
        this@drawWithContent.drawContent()
    }
    onRecorded(recordingGeneration, layer.size)
    if (drawInPlace) {
        this@drawWithContent.drawContent()
    }
}

internal fun Modifier.recordPlayerContentLayer(
    layer: GraphicsLayer,
): Modifier = drawWithContent {
    layer.record {
        this@drawWithContent.drawContent()
    }
    this@drawWithContent.drawContent()
}

/**
 * Shared container overlay. Mini-player and full-player content are recorded
 * independently, then drawn inside one expanding squircle instead of fading a
 * fixed bar under a fixed screen.
 */
@Composable
internal fun PlayerSheetContentOverlay(
    transition: PlayerSheetTransitionState,
    miniPlayerContentLayer: GraphicsLayer,
    fullPlayerBackgroundLayer: GraphicsLayer,
    fullPlayerContentLayer: GraphicsLayer,
    miniPlayerChrome: MiniPlayerChrome?,
    collapsedCornerRadius: Dp,
    floatingMiniPlayer: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!transition.sharedLayersReady || !transition.isTransitionActive) return

    val progress = transition.progress
    val density = LocalDensity.current
    val contentBounds = sharedContainerRect(
        source = transition.miniPlayerBounds,
        target = transition.fullPlayerBounds,
        progress = progress,
    )
    val bounds = sharedContainerRenderRect(
        source = transition.miniPlayerBounds,
        target = transition.fullPlayerBounds,
        progress = progress,
    )
    val deviceCornerRadius = getRoundedCorner()
    val expandedCornerRadius = if (rememberPlayerWindowUsesPhysicalScreenCorners()) {
        deviceCornerRadius
    } else {
        0.dp
    }
    val cornerRadii = sharedContainerCornerRadii(
        source = SharedContainerCornerRadii.uniform(collapsedCornerRadius.value),
        target = SharedContainerCornerRadii.uniform(expandedCornerRadius.value),
        progress = progress,
    )
    val cornerRadius = cornerRadii.topStart.dp
    val miniChromeAlpha = playerSheetBarAlpha(progress)

    val glassChrome = miniPlayerChrome?.takeIf {
        playerSheetGlassVisible(progress)
    }
    val glassSurfaceModifier = if (glassChrome != null) {
        Modifier
            .miniPlayerSurface(
                cornerRadius = cornerRadius,
                backdrop = glassChrome.backdrop,
                blurActive = glassChrome.blurActive,
                liquidGlassActive = glassChrome.liquidGlassActive,
                isDark = glassChrome.isDark,
                followsNavigationBar = glassChrome.style == BottomBarStyle.NORMAL,
                floatingHighlight = glassChrome.floatingHighlight,
                highlightAlpha = miniChromeAlpha,
            )
            .then(
                if (glassChrome.style == BottomBarStyle.NORMAL) {
                    Modifier.squircleBorder(
                        width = DividerDefaults.Thickness,
                        color = DividerDefaults.DividerColor.copy(
                            alpha = NORMAL_BAR_STROKE_ALPHA,
                        ),
                        cornerRadius = cornerRadius,
                    )
                } else {
                    Modifier
                },
            )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                )
            }
            .size(
                width = with(density) { bounds.width.coerceAtLeast(1f).toDp() },
                height = with(density) { bounds.height.coerceAtLeast(1f).toDp() },
            )
            .then(
                if (floatingMiniPlayer) {
                    Modifier.miuixFloatingBarShadow(
                        cornerRadius = cornerRadius,
                        isDark = isDark,
                        alpha = miniChromeAlpha,
                    )
                } else {
                    Modifier
                },
            )
            .squircleClip(
                topStart = cornerRadii.topStart.dp,
                topEnd = cornerRadii.topEnd.dp,
                bottomEnd = cornerRadii.bottomEnd.dp,
                bottomStart = cornerRadii.bottomStart.dp,
            )
            .then(glassSurfaceModifier),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (
                miniPlayerContentLayer.size.width > 0 &&
                    progress < PLAYER_MINI_CONTENT_FADE_END_PROGRESS
            ) {
                    miniPlayerContentLayer.alpha = playerSheetMiniContentAlpha(progress)
                    val sourceBounds = transition.miniPlayerBounds
                    val contentLayerBounds = transition.miniPlayerContentBounds
                    val contentOffset = if (contentLayerBounds.isUsable()) {
                        sharedMiniPlayerContentOffset(
                            sourcePlayerBounds = sourceBounds,
                            animatedPlayerBounds = contentBounds,
                            contentBounds = contentLayerBounds,
                        )
                    } else {
                        Offset.Zero
                    }
                    val controlsBounds = transition.miniPlayerControlsBounds
                    if (sourceBounds.isUsable() && controlsBounds.isUsable()) {
                        val controlsLeft = controlsBounds.left - sourceBounds.left
                        val controlsTranslationX = sharedMiniPlayerControlsTranslationX(
                            sourcePlayerBounds = sourceBounds,
                            animatedPlayerBounds = contentBounds,
                            controlsBounds = controlsBounds,
                        )
                        clipRect(right = controlsLeft) {
                            withTransform({
                                translate(
                                    left = contentOffset.x,
                                    top = contentOffset.y,
                                )
                            }) {
                                drawLayer(miniPlayerContentLayer)
                            }
                        }
                        clipRect(left = controlsLeft + controlsTranslationX) {
                            withTransform({
                                translate(
                                    left = contentOffset.x + controlsTranslationX,
                                    top = contentOffset.y,
                                )
                            }) {
                                drawLayer(miniPlayerContentLayer)
                            }
                        }
                    } else {
                        withTransform({
                            translate(
                                left = contentOffset.x,
                                top = contentOffset.y,
                            )
                        }) {
                            drawLayer(miniPlayerContentLayer)
                        }
                    }
            }
            if (fullPlayerBackgroundLayer.size.width > 0 && progress > 0f) {
                val contentOffset = sharedContainerContentOffset(
                    renderBounds = bounds,
                    contentBounds = contentBounds,
                )
                val scale = contentBounds.width / fullPlayerBackgroundLayer.size.width
                fullPlayerBackgroundLayer.alpha = playerSheetBackgroundAlpha(progress)
                withTransform({
                    translate(left = contentOffset.x, top = contentOffset.y)
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                }) {
                    drawLayer(fullPlayerBackgroundLayer)
                }
            }
            if (fullPlayerContentLayer.size.width > 0 && progress > PLAYER_CONTENT_APPEAR_START_PROGRESS) {
                val contentOffset = sharedContainerContentOffset(
                    renderBounds = bounds,
                    contentBounds = contentBounds,
                )
                val scale = contentBounds.width / fullPlayerContentLayer.size.width
                fullPlayerContentLayer.alpha = playerSheetContentAlpha(progress)
                withTransform({
                    translate(left = contentOffset.x, top = contentOffset.y)
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                }) {
                    drawLayer(fullPlayerContentLayer)
                }
            }
        }
    }
}

/**
 * Shared cover overlay. Landscape uses direct edge interpolation so horizontal
 * and vertical movement follow their measured start-to-end distances; portrait
 * keeps the established eased center path.
 */
@Composable
internal fun PlayerSheetArtworkOverlay(
    playback: PlaybackUiState,
    transition: PlayerSheetTransitionState,
    collapsedCornerRadius: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val item = playback.currentItem ?: return
    val progress = transition.progress
    val bitmap = rememberArtworkBitmap(
        contentUri = item.contentUri,
        dateModifiedEpochSeconds = item.dateModifiedEpochSeconds,
        fileSizeBytes = item.fileSizeBytes,
        size = PLAYER_FULL_ARTWORK_REQUEST_SIZE,
    )
    val density = LocalDensity.current
    if (!enabled || !transition.separateArtworkOverlayReady || !transition.isTransitionActive) return
    val source = transition.overlayMiniArtworkBounds
    val target = transition.overlayFullArtworkBounds
    val sourceArtworkBounds = bitmap?.let {
        fittedArtworkRect(source, it.width, it.height)
    }
    val sourceBounds = sourceArtworkBounds ?: source
    if (!sharedArtworkTargetIsOnscreen(
        artworkBounds = target,
        viewportBounds = transition.fullPlayerBounds,
    )) return
    val collapsedArtworkCornerRadius = bitmap?.let {
        playbackArtworkCornerRadius(
            cornerRadius = collapsedCornerRadius,
            bitmapWidth = it.width,
            bitmapHeight = it.height,
            rectangularReduction = MINI_PLAYER_RECTANGULAR_ARTWORK_CORNER_REDUCTION,
        )
    } ?: collapsedCornerRadius
    val artworkCornerRadius = lerp(
        collapsedArtworkCornerRadius.value,
        PLAYER_FULL_ARTWORK_CORNER_RADIUS.value,
        progress,
    )

    if (bitmap == null) {
        val renderedBounds = sharedArtworkRect(sourceBounds, target, progress)
        val scaleX = renderedBounds.width / source.width.coerceAtLeast(1f)
        val localCornerRadius = (artworkCornerRadius / scaleX.coerceAtLeast(1f)).dp
        Box(
            modifier = modifier
                .offset {
                    IntOffset(
                        source.left.roundToInt(),
                        source.top.roundToInt(),
                    )
                }
                .size(
                    with(density) { source.width.coerceAtLeast(1f).toDp() },
                )
                .graphicsLayer {
                    val frameProgress = transition.progress
                    val frameBounds = sharedArtworkRect(
                        source = sourceBounds,
                        target = target,
                        progress = frameProgress,
                    )
                    transformOrigin = TransformOrigin(0f, 0f)
                    this.scaleX = frameBounds.width / source.width.coerceAtLeast(1f)
                    this.scaleY = frameBounds.height / source.height.coerceAtLeast(1f)
                    translationX = frameBounds.left - source.left
                    translationY = frameBounds.top - source.top
                },
        ) {
            PlaybackArtworkFrame(
                bitmap = null,
                size = with(density) { source.width.coerceAtLeast(1f).toDp() },
                cornerRadius = localCornerRadius,
                modifier = Modifier,
                contentScale = ContentScale.Fit,
                useSquircleClip = true,
                drawArtworkShadow = true,
                artworkShadowAlpha = progress,
            )
        }
    } else {
        val targetBounds = target
        val targetArtworkWidth = targetBounds.width.coerceAtLeast(1f)
        val targetArtworkHeight = targetBounds.height.coerceAtLeast(1f)
        val renderedBounds = sharedArtworkRect(sourceBounds, target, progress)
        val scaleX = renderedBounds.width / targetArtworkWidth
        val localCornerRadius = (artworkCornerRadius / scaleX.coerceAtLeast(0.001f)).dp
        Box(
            modifier = modifier
                .offset {
                    IntOffset(
                        targetBounds.left.roundToInt(),
                        targetBounds.top.roundToInt(),
                    )
                }
                .size(
                    width = with(density) { targetArtworkWidth.toDp() },
                    height = with(density) { targetArtworkHeight.toDp() },
                )
                .graphicsLayer {
                    val frameProgress = transition.progress
                    val frameBounds = sharedArtworkRect(
                        source = sourceBounds,
                        target = target,
                        progress = frameProgress,
                    )
                    transformOrigin = TransformOrigin(0f, 0f)
                    this.scaleX = frameBounds.width / targetArtworkWidth
                    this.scaleY = frameBounds.height / targetArtworkHeight
                    translationX = frameBounds.left - targetBounds.left
                    translationY = frameBounds.top - targetBounds.top
                },
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .playbackArtworkShadow(
                        cornerRadius = localCornerRadius,
                        alpha = progress,
                    )
                    .squircleClip(localCornerRadius),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
            )
        }
    }
}

internal fun sharedContainerRect(
    source: Rect,
    target: Rect,
    progress: Float,
): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    return Rect(
        left = lerp(source.left, target.left, fraction),
        top = lerp(source.top, target.top, fraction),
        right = lerp(source.right, target.right, fraction),
        bottom = lerp(source.bottom, target.bottom, fraction),
    )
}

internal fun sharedContainerRenderRect(
    source: Rect,
    target: Rect,
    progress: Float,
    endpointOverscanPx: Float = 0f,
): Rect {
    val bounds = sharedContainerRect(source, target, progress)
    if (progress.coerceIn(0f, 1f) < 1f) return bounds

    val overscan = endpointOverscanPx.coerceAtLeast(0f)
    return Rect(
        left = minOf(bounds.left, target.left) - overscan,
        top = minOf(bounds.top, target.top) - overscan,
        right = maxOf(bounds.right, target.right) + overscan,
        bottom = maxOf(bounds.bottom, target.bottom) + overscan,
    )
}

internal fun sharedContainerContentOffset(
    renderBounds: Rect,
    contentBounds: Rect,
): Offset = Offset(
    x = contentBounds.left - renderBounds.left,
    y = contentBounds.top - renderBounds.top,
)

internal data class SharedContainerCornerRadii(
    val topStart: Float,
    val topEnd: Float,
    val bottomEnd: Float,
    val bottomStart: Float,
) {
    companion object {
        fun uniform(cornerRadius: Float): SharedContainerCornerRadii =
            SharedContainerCornerRadii(
                topStart = cornerRadius,
                topEnd = cornerRadius,
                bottomEnd = cornerRadius,
                bottomStart = cornerRadius,
            )
    }
}

internal fun sharedContainerCornerRadii(
    source: SharedContainerCornerRadii,
    target: SharedContainerCornerRadii,
    progress: Float,
): SharedContainerCornerRadii {
    val fraction = progress.coerceIn(0f, 1f)
    fun interpolate(sourceRadius: Float, targetRadius: Float): Float = if (fraction < 1f) {
        lerp(sourceRadius, targetRadius, fraction)
    } else {
        0f
    }
    return SharedContainerCornerRadii(
        topStart = interpolate(source.topStart, target.topStart),
        topEnd = interpolate(source.topEnd, target.topEnd),
        bottomEnd = interpolate(source.bottomEnd, target.bottomEnd),
        bottomStart = interpolate(source.bottomStart, target.bottomStart),
    )
}

internal fun sharedContainerCornerRadius(
    collapsedCornerRadius: Float,
    expandedCornerRadius: Float,
    progress: Float,
): Float = sharedContainerCornerRadii(
    source = SharedContainerCornerRadii.uniform(collapsedCornerRadius),
    target = SharedContainerCornerRadii.uniform(expandedCornerRadius),
    progress = progress,
).topStart

internal fun playerWindowUsesPhysicalScreenCorners(
    currentWidth: Int,
    currentHeight: Int,
    maximumWidth: Int,
    maximumHeight: Int,
    isInMultiWindowMode: Boolean,
    isInPictureInPictureMode: Boolean,
): Boolean = !isInMultiWindowMode &&
    !isInPictureInPictureMode &&
    currentWidth >= maximumWidth &&
    currentHeight >= maximumHeight

internal fun sharedArtworkRect(
    source: Rect,
    target: Rect,
    progress: Float,
): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    val centerX = lerp(source.center.x, target.center.x, easeOutCubic(fraction))
    val verticalFraction = lerp(
        easeInCubic(fraction),
        fraction,
        PLAYER_ARTWORK_VERTICAL_LINEAR_WEIGHT,
    )
    val centerY = lerp(source.center.y, target.center.y, verticalFraction)
    val sourceWidth = source.width.coerceAtLeast(1f)
    val targetWidth = target.width.coerceAtLeast(sourceWidth)
    val scale = lerp(1f, targetWidth / sourceWidth, fraction)
    val width = sourceWidth * scale
    val height = source.height.coerceAtLeast(1f) * scale
    return Rect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f,
    )
}

internal fun sharedArtworkTargetIsOnscreen(
    artworkBounds: Rect,
    viewportBounds: Rect,
): Boolean = !artworkBounds.isUsable() ||
    !viewportBounds.isUsable() ||
    artworkBounds.center.x in viewportBounds.left..viewportBounds.right

internal fun fittedArtworkRect(
    bounds: Rect,
    bitmapWidth: Int,
    bitmapHeight: Int,
): Rect {
    val width = bitmapWidth.coerceAtLeast(1).toFloat()
    val height = bitmapHeight.coerceAtLeast(1).toFloat()
    val scale = minOf(bounds.width / width, bounds.height / height)
    val fittedWidth = width * scale
    val fittedHeight = height * scale
    return Rect(
        left = bounds.center.x - fittedWidth / 2f,
        top = bounds.center.y - fittedHeight / 2f,
        right = bounds.center.x + fittedWidth / 2f,
        bottom = bounds.center.y + fittedHeight / 2f,
    )
}

internal fun artworkInsetRect(
    bounds: Rect,
    inset: Float,
): Rect {
    val horizontalInset = inset.coerceIn(0f, bounds.width / 2f)
    val verticalInset = inset.coerceIn(0f, bounds.height / 2f)
    return Rect(
        left = bounds.left + horizontalInset,
        top = bounds.top + verticalInset,
        right = bounds.right - horizontalInset,
        bottom = bounds.bottom - verticalInset,
    )
}

internal fun playerSheetDragProgress(
    startProgress: Float,
    dragDistanceY: Float,
    travelDistance: Float,
): Float {
    if (travelDistance <= 0f) return startProgress.coerceIn(0f, 1f)
    return (startProgress - dragDistanceY / travelDistance).coerceIn(0f, 1f)
}

internal fun playerSheetVerticalTravel(
    source: Rect,
    target: Rect,
): Float = maxOf(
    abs(target.top - source.top),
    abs(target.bottom - source.bottom),
).coerceAtLeast(1f)

internal fun playerSheetDragTarget(
    velocityY: Float,
    lastDragAmountY: Float,
    originOpen: Boolean,
): Boolean = when {
    velocityY < 0f -> true
    velocityY > 0f -> false
    lastDragAmountY < 0f -> true
    lastDragAmountY > 0f -> false
    else -> originOpen
}

internal fun sharedMiniPlayerControlsTranslationX(
    sourcePlayerBounds: Rect,
    animatedPlayerBounds: Rect,
    controlsBounds: Rect,
): Float {
    val sourceRightInset = sourcePlayerBounds.right - controlsBounds.right
    val targetControlsLeft = animatedPlayerBounds.right - sourceRightInset - controlsBounds.width
    return targetControlsLeft - controlsBounds.left
}

internal fun sharedMiniPlayerContentOffset(
    sourcePlayerBounds: Rect,
    animatedPlayerBounds: Rect,
    contentBounds: Rect,
): Offset = Offset(
    x = contentBounds.left - animatedPlayerBounds.left,
    y = contentBounds.top - sourcePlayerBounds.top,
)

private fun Rect.isUsable(): Boolean = width > 0f && height > 0f

@Composable
private fun rememberPlayerWindowUsesPhysicalScreenCorners(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(
        context,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        context.playerWindowUsesPhysicalScreenCorners()
    }
}

private fun Context.playerWindowUsesPhysicalScreenCorners(): Boolean {
    val activity = findActivity() ?: return false
    if (activity.isInMultiWindowMode || activity.isInPictureInPictureMode) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true

    val currentBounds = activity.windowManager.currentWindowMetrics.bounds
    val maximumBounds = activity.windowManager.maximumWindowMetrics.bounds
    return playerWindowUsesPhysicalScreenCorners(
        currentWidth = currentBounds.width(),
        currentHeight = currentBounds.height(),
        maximumWidth = maximumBounds.width(),
        maximumHeight = maximumBounds.height(),
        isInMultiWindowMode = false,
        isInPictureInPictureMode = false,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

private fun easeInCubic(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * clamped
}

private fun easeOutCubic(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    return 1f - inverse * inverse * inverse
}
