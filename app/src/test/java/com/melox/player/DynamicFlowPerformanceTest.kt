package com.melox.player

import com.melox.player.ui.component.playback.blurDynamicFlowHorizontal
import com.melox.player.ui.component.playback.blurDynamicFlowVertical
import com.melox.player.ui.component.playback.shouldRenderDynamicFlowFrame
import com.melox.player.ui.component.playback.DYNAMIC_FLOW_DEFAULT_SPEED_TENTHS
import com.melox.player.ui.component.playback.scaledDynamicFlowTimeMs
import com.melox.player.ui.component.playback.dynamicFlowLayerMotion
import com.melox.player.ui.component.playback.fillDynamicFlowMeshVertices
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DynamicFlowPerformanceTest {
    @Test
    fun layerMotionIsBoundedAndChangesWithoutMinuteResets() {
        for (layer in 0..2) for (axis in 0..2) {
            val samples = (0L..120_000L step 1000L).map {
                dynamicFlowLayerMotion(it, layer, axis)
            }
            assertTrue(samples.all { it in -1f..1f })
            assertTrue(samples.max() - samples.min() > 0.5f)
            assertTrue(kotlin.math.abs(dynamicFlowLayerMotion(59_999L, layer, axis) -
                dynamicFlowLayerMotion(60_001L, layer, axis)) < 0.01f)
        }
    }

    @Test
    fun meshMorphIsContinuousAtTemplateAndMinuteBoundaries() {
        for (time in listOf(24_000L, 48_000L, 60_000L, 144_000L)) {
            val before = FloatArray(72)
            val after = FloatArray(72)
            fillDynamicFlowMeshVertices(before, 100, 100, time - 1, 456)
            fillDynamicFlowMeshVertices(after, 100, 100, time + 1, 456)
            assertTrue(before.indices.all { kotlin.math.abs(before[it] - after[it]) < 0.02f })
        }
    }

    @Test
    fun pacingIncludesRenderingTimeAndDoesNotCatchUpAfterSlowFrames() {
        assertTrue(shouldRenderDynamicFlowFrame(null, 0L))
        assertFalse(shouldRenderDynamicFlowFrame(0L, 16_666_666L))
        assertTrue(shouldRenderDynamicFlowFrame(0L, 33_333_333L))
        assertTrue(shouldRenderDynamicFlowFrame(0L, 100_000_000L))
        assertFalse(shouldRenderDynamicFlowFrame(100_000_000L, 116_666_666L))
        assertTrue(shouldRenderDynamicFlowFrame(100_000_000L, 133_333_333L))
    }

    @Test
    fun pacingSupportsCommonDisplayRefreshRates() {
        for (refreshRate in listOf(60, 90, 120)) {
            var previous: Long? = null
            var rendered = 0
            repeat(refreshRate) { tick ->
                val timestamp = tick * 1_000_000_000L / refreshRate
                if (shouldRenderDynamicFlowFrame(previous, timestamp)) {
                    previous = timestamp
                    rendered++
                }
            }
            org.junit.Assert.assertEquals(30, rendered)
        }
    }

    @Test
    fun defaultMotionRunsAtOneSpeed() {
        org.junit.Assert.assertEquals(10, DYNAMIC_FLOW_DEFAULT_SPEED_TENTHS)
        org.junit.Assert.assertEquals(
            1000L, scaledDynamicFlowTimeMs(1000L, DYNAMIC_FLOW_DEFAULT_SPEED_TENTHS),
        )
    }

    @Test
    fun optimizedBlurMatchesClampedReferenceIncludingEdgesAndFractionalChannels() {
        val random = Random(42)
        for ((width, height) in listOf(1 to 1, 2 to 3, 19 to 11)) {
            val source = LongArray(width * height) { index ->
                var pixel = 0L
                for (shift in listOf(0, 16, 32, 48)) {
                    val channel = when (index % 3) {
                        0 -> 0
                        1 -> 65280
                        else -> random.nextInt(65281)
                    }
                    pixel = pixel or (channel.toLong() shl shift)
                }
                pixel
            }
            for (radius in listOf(0, 1, 10, 25)) {
                for (horizontal in listOf(true, false)) {
                    val actual = LongArray(source.size)
                    if (horizontal) {
                        blurDynamicFlowHorizontal(source, actual, width, height, radius)
                    } else {
                        blurDynamicFlowVertical(source, actual, width, height, radius)
                    }
                    val window = radius * 2 + 1
                    val expected = LongArray(source.size) { index ->
                        var pixel = 0L
                        for (shift in listOf(0, 16, 32, 48)) {
                            var sum = 0
                            for (offset in -radius..radius) {
                                val x = (index % width + if (horizontal) offset else 0)
                                    .coerceIn(0, width - 1)
                                val y = (index / width + if (horizontal) 0 else offset)
                                    .coerceIn(0, height - 1)
                                sum += ((source[y * width + x] ushr shift) and 0xffff).toInt()
                            }
                            val channel = ((sum + window / 2) / window).coerceIn(0, 65535)
                            pixel = pixel or (channel.toLong() shl shift)
                        }
                        pixel
                    }
                    assertArrayEquals(expected, actual)
                }
            }
        }
    }
}
