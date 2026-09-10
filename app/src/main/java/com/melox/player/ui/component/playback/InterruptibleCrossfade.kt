package com.melox.player.ui.component.playback

internal data class WeightedCrossfadeFrame<T>(
    val value: T,
    val alpha: Float,
)

internal fun <T> weightedCrossfadeFrames(
    startingFrames: List<WeightedCrossfadeFrame<T>>,
    currentValue: T?,
    progress: Float,
    sameValue: (T, T) -> Boolean = { first, second -> first == second },
): List<WeightedCrossfadeFrame<T>> {
    val targetProgress = progress.coerceIn(0f, 1f)
    val frames = mutableListOf<WeightedCrossfadeFrame<T>>()

    fun add(value: T, alpha: Float) {
        if (alpha <= 0.001f) return
        val existingIndex = frames.indexOfFirst { sameValue(it.value, value) }
        if (existingIndex >= 0) {
            val existing = frames[existingIndex]
            frames[existingIndex] = existing.copy(alpha = existing.alpha + alpha)
        } else {
            frames += WeightedCrossfadeFrame(value = value, alpha = alpha)
        }
    }

    startingFrames.forEach { frame ->
        add(frame.value, frame.alpha * (1f - targetProgress))
    }
    currentValue?.let { add(it, targetProgress) }
    return frames
}
