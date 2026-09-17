package com.melox.player

import com.melox.player.ui.component.playback.PlayerInputOwner
import com.melox.player.ui.component.playback.playerSheetInputOwner
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerInputOwnerTest {
    @Test
    fun collapsedEndpointBelongsToMiniPlayer() {
        assertEquals(
            PlayerInputOwner.MINI,
            playerSheetInputOwner(
                targetOpen = false,
                isDragging = false,
                dragStartedFromMiniPlayer = false,
                progress = 0f,
                sharedLayersReady = false,
            ),
        )
    }

    @Test
    fun unreadyIntermediateFrameBlocksBothEndpoints() {
        assertEquals(
            PlayerInputOwner.NONE,
            playerSheetInputOwner(
                targetOpen = true,
                isDragging = false,
                dragStartedFromMiniPlayer = false,
                progress = 0.5f,
                sharedLayersReady = false,
            ),
        )
    }

    @Test
    fun expandedEndpointBelongsToFullPlayerWithoutRecordedLayers() {
        assertEquals(
            PlayerInputOwner.FULL,
            playerSheetInputOwner(
                targetOpen = true,
                isDragging = false,
                dragStartedFromMiniPlayer = false,
                progress = 1f,
                sharedLayersReady = false,
            ),
        )
    }

    @Test
    fun dragKeepsTheOriginatingSurfaceAsInputOwner() {
        assertEquals(
            PlayerInputOwner.MINI,
            playerSheetInputOwner(
                targetOpen = true,
                isDragging = true,
                dragStartedFromMiniPlayer = true,
                progress = 0.8f,
                sharedLayersReady = true,
            ),
        )
        assertEquals(
            PlayerInputOwner.FULL,
            playerSheetInputOwner(
                targetOpen = false,
                isDragging = true,
                dragStartedFromMiniPlayer = false,
                progress = 0.2f,
                sharedLayersReady = true,
            ),
        )
    }
}
