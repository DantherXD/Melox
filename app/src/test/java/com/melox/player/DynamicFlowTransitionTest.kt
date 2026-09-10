package com.melox.player

import com.melox.player.ui.component.playback.interpolateDynamicFlowPixels
import com.melox.player.ui.component.playback.interpolateDynamicFlowPixel
import com.melox.player.ui.component.playback.fillDynamicFlowMeshVertices
import com.melox.player.ui.component.playback.dynamicFlowBlurRadius
import com.melox.player.ui.component.playback.dynamicFlowDownsampleFactor
import com.melox.player.ui.component.playback.dynamicFlowGaussianBoxSizes
import com.melox.player.ui.component.playback.PLAYBACK_BACKGROUND_TRANSITION_DURATION_MILLIS
import com.melox.player.ui.component.playback.PLAYER_TRACK_ARTWORK_CROSSFADE_DURATION_MILLIS
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicFlowTransitionTest {
    @Test
    fun blurredAndDynamicBackgroundsUseTheArtworkCrossfadeDuration() {
        assertEquals(320, PLAYER_TRACK_ARTWORK_CROSSFADE_DURATION_MILLIS)
        assertEquals(
            PLAYER_TRACK_ARTWORK_CROSSFADE_DURATION_MILLIS,
            PLAYBACK_BACKGROUND_TRANSITION_DURATION_MILLIS,
        )
    }

    @Test
    fun reusedOutputBufferIsFullyOverwrittenWithoutChangingEndpoints() {
        val from = intArrayOf(0xffff0000.toInt(), 0xffaaaaaa.toInt())
        val to = intArrayOf(0xff0000ff.toInt(), 0xffaaaaaa.toInt())
        val originalFrom = from.copyOf()
        val originalTo = to.copyOf()
        val output = IntArray(2)
        interpolateDynamicFlowPixels(from, to, output, 0.5f)
        assertArrayEquals(intArrayOf(0xff800080.toInt(), 0xffaaaaaa.toInt()), output)
        interpolateDynamicFlowPixels(from, to, output, 1f)
        assertArrayEquals(to, output)
        interpolateDynamicFlowPixels(from, to, output, 0f)
        assertArrayEquals(from, output)
        assertArrayEquals(originalFrom, from)
        assertArrayEquals(originalTo, to)
    }

    @Test
    fun transitionRetainsOpaqueEndpointsAndDoesNotExposeFallbackAtMidpoint() {
        val red = 0xffff0000.toInt()
        val blue = 0xff0000ff.toInt()
        assertEquals(red, interpolateDynamicFlowPixel(red, blue, 0f))
        assertEquals(blue, interpolateDynamicFlowPixel(red, blue, 1f))
        assertEquals(0xff800080.toInt(), interpolateDynamicFlowPixel(red, blue, 0.5f))
        for (step in 0..100) {
            val pixel = interpolateDynamicFlowPixel(red, blue, step / 100f)
            assertEquals(255, pixel ushr 24)
            assertEquals(0, pixel ushr 8 and 0xff)
        }
    }

    @Test
    fun identicalFramesKeepTheirBrightnessThroughoutTransition() {
        val gray = 0xffaaaaaa.toInt()
        for (step in 0..100) {
            assertEquals(gray, interpolateDynamicFlowPixel(gray, gray, step / 100f))
        }
    }

    @Test
    fun rapidRetargetStartsAtVisibleMixtureAndCanReachMissingArtworkFallback() {
        val mixture = interpolateDynamicFlowPixel(
            0xffff0000.toInt(), 0xff0000ff.toInt(), 0.25f,
        )
        val fallback = 0xff242424.toInt()
        assertEquals(mixture, interpolateDynamicFlowPixel(mixture, fallback, 0f))
        assertEquals(fallback, interpolateDynamicFlowPixel(mixture, fallback, 1f))
    }

    @Test
    fun meshPerturbsEdgesWithinTheOverscanSafeArea() {
        val width = 100
        val height = 80
        val vertices = FloatArray(72)
        fillDynamicFlowMeshVertices(vertices, width, height, timeMillis = 12_000L, seed = 123)

        fun value(column: Int, row: Int, axis: Int): Float =
            vertices[(row * 6 + column) * 2 + axis]

        var edgeHasMoved = false
        for (index in 0..5) {
            assertTrue(value(index, 0, 1) in -height * 0.15f..height * 0.15f)
            assertTrue(value(index, 5, 1) in height * 0.85f..height * 1.15f)
            assertTrue(value(0, index, 0) in -width * 0.15f..width * 0.15f)
            assertTrue(value(5, index, 0) in width * 0.85f..width * 1.15f)
            edgeHasMoved = edgeHasMoved ||
                value(index, 0, 1) != 0f || value(index, 5, 1) != height.toFloat() ||
                value(0, index, 0) != 0f || value(5, index, 0) != width.toFloat()
        }
        assertTrue(edgeHasMoved)
    }

    @Test
    fun meshIsDeterministicSeedSpecificAndMovesItsInteriorOverTime() {
        val initial = FloatArray(72)
        val repeated = FloatArray(72)
        val later = FloatArray(72)
        val differentSeed = FloatArray(72)
        fillDynamicFlowMeshVertices(initial, 100, 80, timeMillis = 0L, seed = 456)
        fillDynamicFlowMeshVertices(repeated, 100, 80, timeMillis = 0L, seed = 456)
        fillDynamicFlowMeshVertices(later, 100, 80, timeMillis = 9_000L, seed = 456)
        fillDynamicFlowMeshVertices(differentSeed, 100, 80, timeMillis = 0L, seed = 457)

        assertArrayEquals(initial, repeated, 0f)
        assertNotEquals(initial[42], later[42])
        assertTrue(initial.indices.any { initial[it] != differentSeed[it] })
        assertTrue(initial.indices.any { index ->
            val axis = index % 2
            val base = if (axis == 0) 100f * ((index / 2) % 6) / 5f
            else 80f * ((index / 2) / 6) / 5f
            kotlin.math.abs(initial[index] - base) > if (axis == 0) 6f else 4.8f
        })
    }

    @Test
    fun highDensityFramesUseMoreSamplesWithoutChangingTheirOnScreenBlurWidth() {
        assertEquals(20f, dynamicFlowDownsampleFactor(densityDpi = 420), 0f)
        assertEquals(16f, dynamicFlowDownsampleFactor(densityDpi = 419), 0f)
        assertEquals(18, dynamicFlowBlurRadius(blur = 60f, densityDpi = 420))
        assertEquals(15, dynamicFlowBlurRadius(blur = 60f, densityDpi = 419))
    }

    @Test
    fun highPrecisionDynamicFlowBlurUsesThreeGaussianApproximationPasses() {
        val boxSizes = dynamicFlowGaussianBoxSizes(radius = 18)

        assertArrayEquals(intArrayOf(21, 21, 23), boxSizes)
        assertTrue(boxSizes.all { size -> size > 0 && size % 2 == 1 })
    }
}
