package com.melox.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.melox.player.ui.component.playback.PlayerSheetTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResizeTransitionTest {
    @Test
    fun missingFramesDoNotBlockLogicalSettlement() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)
        reportContainerBounds(state, portrait)

        assertTrue(state.canSettle)
        assertFalse(state.sharedLayersReady)
        state.open()
        assertTrue(state.targetOpen)
    }

    @Test
    fun lyricsPageWithoutArtworkBoundsKeepsSharedAnimationReadyAcrossRequests() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)
        state.updateFullPlayerArtworkPageSelected(false)
        reportContainerBounds(state, portrait)
        reportFrames(state, portrait)

        assertTrue(state.sharedLayersReady)
        assertFalse(state.separateArtworkOverlayReady)

        state.open()
        reportFrames(state, portrait)
        assertTrue(state.sharedLayersReady)
        state.close()
        assertTrue(state.canSettle)
        assertTrue(state.sharedLayersReady)

        state.open()
        assertTrue(state.sharedLayersReady)
        assertFalse(state.separateArtworkOverlayReady)
    }

    @Test
    fun missingFullArtworkBoundsDisableOnlyTheArtworkOverlay() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        state.updateMiniPlayerBounds(Rect(0f, 0f, 300f, 64f), portrait)
        state.updateMiniArtworkBounds(Rect(8f, 8f, 56f, 56f), portrait)
        state.updateFullPlayerBounds(
            Rect(0f, 0f, portrait.width.toFloat(), portrait.height.toFloat()),
            portrait,
        )
        reportFrames(state, portrait)

        assertTrue(state.sharedLayersReady)
        assertFalse(state.hasArtworkBounds)
        assertFalse(state.separateArtworkOverlayReady)
        assertTrue(state.fullPlayerAcceptsInput)
    }

    @Test
    fun closingWithoutRecordedFramesKeepsVisibleProgressUntilFramesRecover() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(IntSize(400, 800))
        assertFalse(state.sharedLayersReady)
        state.close()
        assertTrue(state.isMounted)
        assertTrue(state.fullPlayerHostMounted)
        assertEquals(1f, state.progress, 0f)
    }

    @Test
    fun unreadyClosingHostYieldsToTheMiniPlayerBelowTheHandoff() {
        val state = PlayerSheetTransitionState(initialProgress = 0.2f)
        state.updateWindowSize(portrait)
        state.close()

        assertTrue(state.isMounted)
        assertTrue(state.fullPlayerHostMounted)
        assertFalse(state.fullPlayerDrawsAboveRoot)
        assertFalse(state.fullPlayerDrawsInPlace)
        assertTrue(state.miniPlayerAcceptsInput)
    }

    @Test
    fun expandedPlayerUsesTheLiveEndpointWithoutRecordedFrames() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        reportContainerBounds(state, portrait)

        assertFalse(state.sharedLayersReady)
        assertTrue(state.fullPlayerDrawsAboveRoot)
        assertTrue(state.fullPlayerDrawsInPlace)
        assertTrue(state.fullPlayerAcceptsInput)
        assertTrue(state.isFullyExpanded)
    }

    @Test
    fun collapsedEndpointUnmountsWithoutRecordedFrames() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)

        assertEquals(0f, state.progress, 0f)
        assertFalse(state.isMounted)
        assertFalse(state.fullPlayerHostMounted)
        assertTrue(state.miniPlayerAcceptsInput)
    }

    @Test
    fun openedPlayerHostStaysResidentWhenTheSheetIsCollapsed() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)
        reportContainerBounds(state, portrait)

        state.open()
        state.close()

        assertTrue(state.fullPlayerHostMounted)
    }

    private val portrait = IntSize(400, 800)
    private val landscape = IntSize(800, 400)

    private fun reportBounds(state: PlayerSheetTransitionState, window: IntSize) {
        reportContainerBounds(state, window)
        state.updateMiniArtworkBounds(Rect(8f, 8f, 56f, 56f), window)
        state.updateFullArtworkBounds(Rect(32f, 64f, 232f, 264f), window)
    }

    private fun reportContainerBounds(state: PlayerSheetTransitionState, window: IntSize) {
        state.updateMiniPlayerBounds(Rect(0f, 0f, 300f, 64f), window)
        state.updateFullPlayerBounds(Rect(0f, 0f, window.width.toFloat(), window.height.toFloat()), window)
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
        assertTrue(state.sharedLayersReady)
        state.updateWindowSize(landscape)
        assertFalse(state.sharedLayersReady)
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        assertFalse(state.hasArtworkBounds)
        reportBounds(state, landscape)
        assertFalse(state.sharedLayersReady)
        val generation = state.currentFrameRecordingGeneration
        state.markFullFrameRecorded(landscape, generation, portrait)
        state.markMiniFrameRecorded(landscape, generation, IntSize(300, 64))
        assertFalse(state.sharedLayersReady)
        state.markFullFrameRecorded(landscape, generation, landscape)
        assertTrue(state.sharedLayersReady)
    }

    @Test
    fun activeTransitionUsesLatestArtworkEndpoints() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        reportFrames(state, portrait)

        state.beginFullPlayerDrag()
        state.dragBy(120f)
        state.updateMiniPlayerBounds(Rect(0f, 704f, 300f, 768f), portrait)
        assertEquals(Rect(8f, 8f, 56f, 56f), state.overlayMiniArtworkBounds)
        state.updateMiniArtworkBounds(Rect(20f, 720f, 68f, 768f), portrait)
        state.updateFullArtworkBounds(Rect(32f, 80f, 232f, 280f), portrait)

        assertEquals(Rect(20f, 720f, 68f, 768f), state.overlayMiniArtworkBounds)
        assertEquals(Rect(32f, 80f, 232f, 280f), state.overlayFullArtworkBounds)
    }

    @Test
    fun resizingAnExpandedPlayerKeepsItsLiveEndpointInteractive() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(landscape)
        reportBounds(state, landscape)
        reportFrames(state, landscape)
        assertTrue(state.sharedLayersReady)
        assertTrue(state.targetOpen)
        assertTrue(state.fullPlayerHostMounted)
        assertTrue(state.fullPlayerAcceptsInput)

        state.updateWindowSize(portrait)

        assertTrue(state.targetOpen)
        assertTrue(state.fullPlayerHostMounted)
        assertFalse(state.sharedLayersReady)
        assertTrue(state.fullPlayerAcceptsInput)
        assertTrue(state.fullPlayerDrawsAboveRoot)

        reportBounds(state, portrait)
        reportFrames(state, portrait)

        assertTrue(state.sharedLayersReady)
        assertTrue(state.fullPlayerHostMounted)
        assertTrue(state.fullPlayerAcceptsInput)
    }

    @Test
    fun hiddenPreparationHostStaysBehindTheRootUntilFramesAreReady() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)
        state.open()

        assertTrue(state.fullPlayerHostMounted)
        assertFalse(state.fullPlayerDrawsAboveRoot)

        reportBounds(state, portrait)
        reportFrames(state, portrait)

        assertTrue(state.fullPlayerDrawsAboveRoot)
    }

    @Test
    fun resizeKeepsClosingProgressUntilCurrentGeometryIsReady() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        state.beginFullPlayerDrag()
        state.dragBy(184f)
        state.endDrag(velocityY = 1f)
        val progressBeforeResize = state.progress
        state.updateMiniArtworkBounds(Rect(20f, 20f, 68f, 68f), portrait)
        state.updateFullArtworkBounds(Rect(40f, 40f, 340f, 340f), portrait)
        assertEquals(Rect(20f, 20f, 68f, 68f), state.overlayMiniArtworkBounds)
        assertEquals(Rect(40f, 40f, 340f, 340f), state.overlayFullArtworkBounds)
        state.updateWindowSize(landscape)
        assertEquals(progressBeforeResize, state.progress)
        assertFalse(state.targetOpen)
        assertFalse(state.sharedLayersReady)
        assertEquals(Rect.Zero, state.overlayFullArtworkBounds)
    }

    @Test
    fun collapsedPlayerRetainsPreparedFullArtworkAndFrames() {
        val state = PlayerSheetTransitionState()
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        assertTrue(state.sharedLayersReady)

        state.open()
        state.close()

        assertTrue(state.sharedLayersReady)
        assertTrue(state.fullPlayerBounds != Rect.Zero)
        assertTrue(state.fullArtworkBounds != Rect.Zero)
        assertTrue(state.miniArtworkBounds != Rect.Zero)
    }

    @Test
    fun resizeRejectsAnOldGenerationWhenTheWindowReturnsToTheSameSize() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        val firstPortraitGeneration = state.currentFrameRecordingGeneration
        reportBounds(state, portrait)
        reportFrames(state, portrait)
        assertTrue(state.sharedLayersReady)

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

        assertFalse(state.sharedLayersReady)
        reportFrames(state, portrait)
        assertTrue(state.sharedLayersReady)
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
        assertFalse(state.sharedLayersReady)

        state.markMiniFrameRecorded(portrait, retryGeneration, IntSize(300, 64))
        state.markFullFrameRecorded(portrait, retryGeneration, portrait)
        assertTrue(state.sharedLayersReady)
    }

    @Test
    fun currentGenerationAllowsOnePixelLayerRoundingDifference() {
        val state = PlayerSheetTransitionState(initialProgress = 1f)
        state.updateWindowSize(portrait)
        reportBounds(state, portrait)
        val generation = state.currentFrameRecordingGeneration

        state.markMiniFrameRecorded(portrait, generation, IntSize(299, 63))
        state.markFullFrameRecorded(portrait, generation, IntSize(399, 799))

        assertTrue(state.sharedLayersReady)
    }
}
