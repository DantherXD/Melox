package com.melox.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.melox.player.ui.component.playback.PlayerSheetTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResizeTransitionTest {
    private val portrait = IntSize(400, 800)
    private val landscape = IntSize(800, 400)

    private fun reportBounds(state: PlayerSheetTransitionState, window: IntSize) {
        state.updateMiniPlayerBounds(Rect(0f, 0f, 300f, 64f), window)
        state.updateMiniArtworkBounds(Rect(8f, 8f, 56f, 56f), window)
        state.updateFullPlayerBounds(Rect(0f, 0f, window.width.toFloat(), window.height.toFloat()), window)
        state.updateFullArtworkBounds(Rect(32f, 64f, 232f, 264f), window)
    }

    private fun reportFrames(state: PlayerSheetTransitionState, window: IntSize) {
        val generation = state.currentFrameRecordingGeneration
        state.markMiniFrameRecorded(window, generation, IntSize(300, 64))
        state.markFullFrameRecorded(window, generation, window)
    }

    @Test
    fun resizeRejectsOldGeometryAndWaitsForBothCurrentFrames() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        assertTrue(state.isReady)
        state.updateWindowSize(landscape)
        assertFalse(state.isReady)
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        assertFalse(state.hasArtworkBounds)
        reportBounds(state, landscape)
        assertFalse(state.isReady)
        val generation = state.currentFrameRecordingGeneration
        state.markFullFrameRecorded(landscape, generation, portrait)
        state.markMiniFrameRecorded(landscape, generation, IntSize(300, 64))
        assertFalse(state.isReady)
        state.markFullFrameRecorded(landscape, generation, landscape)
        assertTrue(state.isReady)
    }

    @Test
    fun resizingAnExpandedPlayerKeepsItMountedAndRestoresInputAfterRelayout() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(landscape)
        reportBounds(state, landscape)
        reportFrames(state, landscape)
        assertTrue(state.isReady)
        assertTrue(state.targetOpen)
        assertTrue(state.fullPlayerHostMounted)
        assertTrue(state.fullPlayerAcceptsInput)

        state.updateWindowSize(portrait)

        assertTrue(state.targetOpen)
        assertTrue(state.fullPlayerHostMounted)
        assertFalse(state.isReady)
        assertFalse(state.fullPlayerAcceptsInput)

        reportBounds(state, portrait)
        reportFrames(state, portrait)

        assertTrue(state.isReady)
        assertTrue(state.fullPlayerHostMounted)
        assertTrue(state.fullPlayerAcceptsInput)
    }

    @Test
    fun resizeKeepsClosingProgressUntilCurrentGeometryIsReady() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        val source = state.miniArtworkBounds
        val target = state.fullArtworkBounds
        state.beginFullPlayerDrag()
        state.dragBy(184f)
        state.endDrag(velocityY = 1f)
        val progressBeforeResize = state.progress
        state.updateMiniArtworkBounds(Rect(20f, 20f, 68f, 68f), portrait)
        state.updateFullArtworkBounds(Rect(40f, 40f, 340f, 340f), portrait)
        assertEquals(source, state.overlayMiniArtworkBounds)
        assertEquals(target, state.overlayFullArtworkBounds)
        state.updateWindowSize(landscape)
        assertEquals(progressBeforeResize, state.progress)
        assertFalse(state.targetOpen)
        assertFalse(state.isReady)
        assertEquals(Rect.Zero, state.overlayFullArtworkBounds)
    }

    @Test
    fun collapsedEndpointInvalidationRejectsStaleFullArtworkAndFrames() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        assertTrue(state.isReady)

        state.invalidateCollapsedFullPlayerEndpoint()

        assertFalse(state.isReady)
        assertEquals(Rect.Zero, state.fullPlayerBounds)
        assertEquals(Rect.Zero, state.fullArtworkBounds)
        assertTrue(state.miniArtworkBounds != Rect.Zero)

        state.updateFullPlayerBounds(
            Rect(0f, 0f, portrait.width.toFloat(), portrait.height.toFloat()),
            portrait,
        )
        state.updateFullArtworkBounds(Rect(32f, 64f, 260f, 292f), portrait)
        val generation = state.currentFrameRecordingGeneration
        state.markFullFrameRecorded(portrait, generation, portrait)

        assertTrue(state.isReady)
    }

    @Test
    fun resizeRejectsAnOldGenerationWhenTheWindowReturnsToTheSameSize() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        val firstPortraitGeneration = state.currentFrameRecordingGeneration
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        assertTrue(state.isReady)

        state.updateWindowSize(landscape)
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        state.markMiniFrameRecorded(
            portrait,
            firstPortraitGeneration,
            IntSize(300, 64),
        )
        state.markFullFrameRecorded(
            portrait,
            firstPortraitGeneration,
            portrait,
        )

        assertFalse(state.isReady)
        reportFrames(state, portrait)
        assertTrue(state.isReady)
    }

    @Test
    fun openingAnUnreadyPlayerRequestsFreshFrameRecording() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        val staleGeneration = state.currentFrameRecordingGeneration

        state.open()

        val retryGeneration = state.currentFrameRecordingGeneration
        assertTrue(retryGeneration > staleGeneration)
        state.markMiniFrameRecorded(portrait, staleGeneration, IntSize(300, 64))
        state.markFullFrameRecorded(portrait, staleGeneration, portrait)
        assertFalse(state.isReady)

        state.markMiniFrameRecorded(portrait, retryGeneration, IntSize(300, 64))
        state.markFullFrameRecorded(portrait, retryGeneration, portrait)
        assertTrue(state.isReady)
    }

    @Test
    fun currentGenerationAllowsOnePixelLayerRoundingDifference() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        val generation = state.currentFrameRecordingGeneration

        state.markMiniFrameRecorded(portrait, generation, IntSize(299, 63))
        state.markFullFrameRecorded(portrait, generation, IntSize(399, 799))

        assertTrue(state.isReady)
    }
}
