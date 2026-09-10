package com.melox.player

import com.melox.player.ui.component.playback.WeightedCrossfadeFrame
import com.melox.player.ui.component.playback.weightedCrossfadeFrames
import org.junit.Assert.assertEquals
import org.junit.Test

class InterruptibleCrossfadeTest {
    @Test
    fun ordinaryTransitionKeepsTheExistingTwoFrameCurve() {
        val frames = weightedCrossfadeFrames(
            startingFrames = listOf(WeightedCrossfadeFrame("a", 1f)),
            currentValue = "b",
            progress = 0.4f,
        )

        assertEquals(listOf("a", "b"), frames.map { it.value })
        assertEquals(0.6f, frames[0].alpha, 0.0001f)
        assertEquals(0.4f, frames[1].alpha, 0.0001f)
    }

    @Test
    fun interruptedTransitionKeepsTheExactVisibleMixture() {
        val firstTransition = weightedCrossfadeFrames(
            startingFrames = listOf(WeightedCrossfadeFrame("a", 1f)),
            currentValue = "b",
            progress = 0.3f,
        )

        val interruptedTransition = weightedCrossfadeFrames(
            startingFrames = firstTransition,
            currentValue = "c",
            progress = 0f,
        )

        assertEquals(listOf("a", "b"), interruptedTransition.map { it.value })
        assertEquals(listOf(0.7f, 0.3f), interruptedTransition.map { it.alpha })
    }

    @Test
    fun returningToAnExistingFrameMergesItsWeight() {
        val frames = weightedCrossfadeFrames(
            startingFrames = listOf(
                WeightedCrossfadeFrame("a", 0.7f),
                WeightedCrossfadeFrame("b", 0.3f),
            ),
            currentValue = "a",
            progress = 0.5f,
        )

        assertEquals(listOf("a", "b"), frames.map { it.value })
        assertEquals(0.85f, frames[0].alpha, 0.0001f)
        assertEquals(0.15f, frames[1].alpha, 0.0001f)
    }

    @Test
    fun missingTargetFadesTheVisibleFramesIntoTheFallback() {
        val frames = weightedCrossfadeFrames(
            startingFrames = listOf(WeightedCrossfadeFrame("a", 1f)),
            currentValue = null,
            progress = 0.4f,
        )

        assertEquals(1, frames.size)
        assertEquals(0.6f, frames.single().alpha, 0.0001f)
    }
}
