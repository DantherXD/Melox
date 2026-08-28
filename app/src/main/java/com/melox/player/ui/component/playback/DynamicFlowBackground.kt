package com.melox.player.ui.component.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DYNAMIC_FLOW_FRAME_INTERVAL_MILLIS = 42L
private const val DYNAMIC_FLOW_DEFAULT_SPEED_TENTHS = 10
private const val DYNAMIC_FLOW_DEFAULT_BLUR = 60f
internal const val DYNAMIC_FLOW_ARTWORK_SATURATION = 2f
internal const val DYNAMIC_FLOW_BACKGROUND_DARKEN_AMOUNT = 0.25f
private val DynamicFlowFallbackColor = Color(0xFF242424)

/**
 * Halcyon Apple Music-style multi-layer cover background.
 * Adapted from https://github.com/Kifranei/Halcyon under Apache-2.0.
 */
@Composable
internal fun DynamicFlowBackground(
    artwork: Bitmap?,
    animate: Boolean,
    modifier: Modifier = Modifier,
    onStatusBarBackgroundDarkChanged: (Boolean) -> Unit = {},
) {
    val densityDpi = LocalContext.current.resources.displayMetrics.densityDpi
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val animationEnabled = animate && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val backgroundColor by produceState(
        initialValue = if (artwork == null) {
            DynamicFlowFallbackColor
        } else {
            defaultDynamicFlowBackgroundColor()
        },
        artwork,
    ) {
        value = withContext(Dispatchers.Default) {
            dynamicFlowBackgroundColor(artwork)
        }
    }
    val sourceBitmap = remember(artwork) { artwork?.scaledForDynamicFlowSource() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val sharedClockMillis = rememberDynamicFlowTimeMillis(animationEnabled)
    val scaledTimeMillis = scaledDynamicFlowTimeMs(
        elapsedMillis = sharedClockMillis,
        speedTenths = DYNAMIC_FLOW_DEFAULT_SPEED_TENTHS,
    )
    val frameTimeMillis =
        (scaledTimeMillis / DYNAMIC_FLOW_FRAME_INTERVAL_MILLIS) *
            DYNAMIC_FLOW_FRAME_INTERVAL_MILLIS
    val normalizedBlur = DYNAMIC_FLOW_DEFAULT_BLUR.coerceIn(30f, 100f)
    val washPrimary = remember(backgroundColor) {
        blendDynamicFlowColors(backgroundColor, Color.Black, 0.28f).copy(alpha = 0.34f)
    }.toArgb()
    val washSecondary = Color.Black.copy(alpha = 0.18f).toArgb()
    val frameBitmap by produceState<Bitmap?>(
        initialValue = null,
        sourceBitmap,
        viewportSize,
        frameTimeMillis,
        normalizedBlur,
        densityDpi,
        washPrimary,
        washSecondary,
    ) {
        val cover = sourceBitmap
        val width = viewportSize.width
        val height = viewportSize.height
        if (cover == null || width <= 0 || height <= 0) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            createDynamicFlowFrameBitmap(
                cover = cover,
                viewportWidth = width,
                viewportHeight = height,
                timeMillis = frameTimeMillis,
                densityDpi = densityDpi,
                blur = normalizedBlur,
                washPrimaryArgb = washPrimary,
                washSecondaryArgb = washSecondary,
            )
        }
    }

    LaunchedEffect(artwork) {
        onStatusBarBackgroundDarkChanged(true)
    }

    Box(modifier = modifier.background(backgroundColor)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewportSize = it },
        ) {
            val ready = frameBitmap
            val source = sourceBitmap
            when {
                ready != null -> Image(
                    bitmap = ready.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )

                source != null -> Image(
                    bitmap = source.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur((normalizedBlur * 0.45f).dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.72f,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.30f),
                        ),
                    ),
                ),
        )
    }
}

internal fun scaledDynamicFlowTimeMs(elapsedMillis: Long, speedTenths: Int): Long =
    elapsedMillis.coerceAtLeast(0L) * speedTenths.coerceIn(5, 60) / 10L

@Composable
private fun rememberDynamicFlowTimeMillis(animate: Boolean): Long {
    var sharedClockMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var previousFrameNanos: Long? = null
        while (true) {
            val frameNanos = withFrameNanos { it }
            sharedClockMillis = advanceDynamicFlowClockMillis(
                elapsedMillis = sharedClockMillis,
                previousFrameNanos = previousFrameNanos,
                frameNanos = frameNanos,
            )
            previousFrameNanos = frameNanos
            delay(DYNAMIC_FLOW_FRAME_INTERVAL_MILLIS)
        }
    }
    return sharedClockMillis
}

internal fun advanceDynamicFlowClockMillis(
    elapsedMillis: Long,
    previousFrameNanos: Long?,
    frameNanos: Long,
): Long {
    if (previousFrameNanos == null) return elapsedMillis.coerceAtLeast(0L)
    val deltaMillis = ((frameNanos - previousFrameNanos).coerceAtLeast(0L)) / 1_000_000L
    return elapsedMillis.coerceAtLeast(0L) + deltaMillis
}

private fun Bitmap.scaledForDynamicFlowSource(maxDimension: Int = 256): Bitmap {
    val longest = max(width, height)
    if (longest <= maxDimension || longest <= 0) return this
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}

internal fun dynamicFlowDownsampleFactor(densityDpi: Int): Float =
    if (densityDpi >= 420) 24f else 16f

private fun createDynamicFlowFrameBitmap(
    cover: Bitmap,
    viewportWidth: Int,
    viewportHeight: Int,
    timeMillis: Long,
    densityDpi: Int,
    blur: Float,
    washPrimaryArgb: Int,
    washSecondaryArgb: Int,
): Bitmap {
    val downsample = dynamicFlowDownsampleFactor(densityDpi)
    val width = ((viewportWidth * 1.3f) / downsample).roundToInt().coerceAtLeast(1)
    val height = ((viewportHeight * 1.3f) / downsample).roundToInt().coerceAtLeast(1)
    val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(frame)
    val diagonal = (max(width, height) * 1.3f).roundToInt().coerceAtLeast(1).toFloat()
    val coverScale = diagonal / max(cover.height, 1)
    val translateX = -(diagonal - width) / 2f
    val translateY = -(diagonal - height) / 2f
    val rotatePivot = diagonal / 2f
    val centerX = width / 2f
    val centerY = height / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix().apply { setSaturation(DYNAMIC_FLOW_ARTWORK_SATURATION) },
        )
    }

    val rotation70 = (timeMillis % 70_000L) / 70_000f * 360f
    drawDynamicFlowLayer(
        canvas = canvas,
        cover = cover,
        paint = paint,
        scale = coverScale,
        rotatePivot = rotatePivot,
        translateX = translateX,
        translateY = translateY,
        viewWidth = width.toFloat(),
        viewHeight = height.toFloat(),
        centerX = centerX,
        centerY = centerY,
        rotation = (timeMillis % 120_000L) / 120_000f * -360f,
    )
    drawDynamicFlowLayer(
        canvas = canvas,
        cover = cover,
        paint = paint,
        scale = coverScale,
        rotatePivot = rotatePivot,
        translateX = translateX,
        translateY = translateY,
        viewWidth = width.toFloat(),
        viewHeight = height.toFloat(),
        centerX = centerX,
        centerY = centerY,
        rotation = (timeMillis % 90_000L) / 90_000f * 360f,
        offsetXFactor = -0.95f,
        offsetYFactor = -0.7f,
    )
    drawDynamicFlowLayer(
        canvas = canvas,
        cover = cover,
        paint = paint,
        scale = coverScale,
        rotatePivot = rotatePivot,
        translateX = translateX,
        translateY = translateY,
        viewWidth = width.toFloat(),
        viewHeight = height.toFloat(),
        centerX = centerX,
        centerY = centerY,
        rotation = rotation70,
        offsetXFactor = -0.5f,
        offsetYFactor = 0.7f,
        extraRotation = rotation70,
    )

    canvas.drawColor(washPrimaryArgb)
    canvas.drawColor(washSecondaryArgb)
    val blurRadius = (
        ((blur.coerceIn(30f, 100f) - 30f) / 70f) * 17f + 8f
        ).roundToInt().coerceIn(8, 25)
    val blurred = blurDynamicFlowBitmap(frame, blurRadius)
    val cropWidth = (blurred.width / 1.3f).roundToInt().coerceIn(1, blurred.width)
    val cropHeight = (blurred.height / 1.3f).roundToInt().coerceIn(1, blurred.height)
    return Bitmap.createBitmap(
        blurred,
        ((blurred.width - cropWidth) / 2).coerceAtLeast(0),
        ((blurred.height - cropHeight) / 2).coerceAtLeast(0),
        cropWidth,
        cropHeight,
    )
}

private fun drawDynamicFlowLayer(
    canvas: Canvas,
    cover: Bitmap,
    paint: Paint,
    scale: Float,
    rotatePivot: Float,
    translateX: Float,
    translateY: Float,
    viewWidth: Float,
    viewHeight: Float,
    centerX: Float,
    centerY: Float,
    rotation: Float,
    offsetXFactor: Float = 0f,
    offsetYFactor: Float = 0f,
    extraRotation: Float? = null,
) {
    val matrix = Matrix()
    matrix.setScale(scale, scale)
    matrix.postRotate(rotation, rotatePivot, rotatePivot)
    matrix.postTranslate(translateX, translateY)
    if (offsetXFactor != 0f || offsetYFactor != 0f) {
        matrix.postTranslate(viewWidth * offsetXFactor, viewHeight * offsetYFactor)
    }
    if (extraRotation != null) {
        matrix.postRotate(extraRotation, centerX, centerY)
    }
    canvas.drawBitmap(cover, matrix, paint)
}

private fun blurDynamicFlowBitmap(bitmap: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return bitmap
    val blurRadius = radius.coerceIn(1, 25)
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return bitmap

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val window = blurRadius * 2 + 1
    val temporary = IntArray(width * height)
    for (y in 0 until height) {
        val rowStart = y * width
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -blurRadius..blurRadius) {
            val pixel = pixels[rowStart + offset.coerceIn(0, width - 1)]
            alpha += pixel ushr 24 and 0xff
            red += pixel ushr 16 and 0xff
            green += pixel ushr 8 and 0xff
            blue += pixel and 0xff
        }
        for (x in 0 until width) {
            temporary[rowStart + x] =
                (alpha / window shl 24) or
                    (red / window shl 16) or
                    (green / window shl 8) or
                    (blue / window)
            val outgoing = pixels[rowStart + (x - blurRadius).coerceIn(0, width - 1)]
            val incoming = pixels[rowStart + (x + blurRadius + 1).coerceIn(0, width - 1)]
            alpha += (incoming ushr 24 and 0xff) - (outgoing ushr 24 and 0xff)
            red += (incoming ushr 16 and 0xff) - (outgoing ushr 16 and 0xff)
            green += (incoming ushr 8 and 0xff) - (outgoing ushr 8 and 0xff)
            blue += (incoming and 0xff) - (outgoing and 0xff)
        }
    }

    for (x in 0 until width) {
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -blurRadius..blurRadius) {
            val pixel = temporary[offset.coerceIn(0, height - 1) * width + x]
            alpha += pixel ushr 24 and 0xff
            red += pixel ushr 16 and 0xff
            green += pixel ushr 8 and 0xff
            blue += pixel and 0xff
        }
        for (y in 0 until height) {
            pixels[y * width + x] =
                (alpha / window shl 24) or
                    (red / window shl 16) or
                    (green / window shl 8) or
                    (blue / window)
            val outgoing = temporary[(y - blurRadius).coerceIn(0, height - 1) * width + x]
            val incoming = temporary[(y + blurRadius + 1).coerceIn(0, height - 1) * width + x]
            alpha += (incoming ushr 24 and 0xff) - (outgoing ushr 24 and 0xff)
            red += (incoming ushr 16 and 0xff) - (outgoing ushr 16 and 0xff)
            green += (incoming ushr 8 and 0xff) - (outgoing ushr 8 and 0xff)
            blue += (incoming and 0xff) - (outgoing and 0xff)
        }
    }

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun defaultDynamicFlowBackgroundColor(): Color = Color(0xFF0B0B0D)

private fun dynamicFlowBackgroundColor(bitmap: Bitmap?): Color {
    val representative = representativeDynamicFlowAccent(bitmap)
        ?: return if (bitmap == null) {
            DynamicFlowFallbackColor
        } else {
            defaultDynamicFlowBackgroundColor()
        }
    val accent = representative.toDynamicFlowAccent()
    return accent.darken(DYNAMIC_FLOW_BACKGROUND_DARKEN_AMOUNT)
}

private fun representativeDynamicFlowAccent(bitmap: Bitmap?): Color? {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
    val sampleStep = (min(bitmap.width, bitmap.height) / 36).coerceAtLeast(1)
    val buckets = linkedMapOf<Int, LongArray>()
    val fallback = LongArray(4)
    val hsv = FloatArray(3)
    var sampled = 0
    var brightNeutral = 0
    var eligible = 0

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(pixel) > 24) {
                val red = AndroidColor.red(pixel)
                val green = AndroidColor.green(pixel)
                val blue = AndroidColor.blue(pixel)
                AndroidColor.RGBToHSV(red, green, blue, hsv)
                val saturation = hsv[1]
                val value = hsv[2]

                sampled += 1
                fallback[0] += 1L
                fallback[1] += red.toLong()
                fallback[2] += green.toLong()
                fallback[3] += blue.toLong()
                if (value > 0.78f && saturation < 0.18f) brightNeutral += 1

                if (value > 0.08f && !(value > 0.94f && saturation < 0.20f)) {
                    eligible += 1
                    val key = ((red ushr 4) shl 8) or
                        ((green ushr 4) shl 4) or
                        (blue ushr 4)
                    val bucket = buckets.getOrPut(key) { LongArray(4) }
                    bucket[0] += 1L
                    bucket[1] += red.toLong()
                    bucket[2] += green.toLong()
                    bucket[3] += blue.toLong()
                }
            }
            x += sampleStep
        }
        y += sampleStep
    }
    if (fallback[0] == 0L) return null
    if (
        sampled > 0 &&
        brightNeutral.toFloat() / sampled > 0.56f &&
        eligible.toFloat() / sampled < 0.24f
    ) {
        val count = fallback[0].coerceAtLeast(1L)
        return Color(
            (fallback[1] / count).toInt(),
            (fallback[2] / count).toInt(),
            (fallback[3] / count).toInt(),
        )
    }

    val best = buckets.values.maxByOrNull { bucket ->
        val count = bucket[0].coerceAtLeast(1L)
        val red = (bucket[1] / count).toInt()
        val green = (bucket[2] / count).toInt()
        val blue = (bucket[3] / count).toInt()
        AndroidColor.RGBToHSV(red, green, blue, hsv)
        val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
        val balance = 1f - abs(luminance - 0.50f).coerceIn(0f, 0.50f) * 1.25f
        count.toFloat() * (0.55f + hsv[1] * 1.65f) * (0.75f + balance * 0.55f)
    } ?: fallback
    val count = best[0].coerceAtLeast(1L)
    return Color(
        (best[1] / count).toInt(),
        (best[2] / count).toInt(),
        (best[3] / count).toInt(),
    )
}

private fun Color.toDynamicFlowAccent(): Color {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
        (red * 255f).toInt().coerceIn(0, 255),
        (green * 255f).toInt().coerceIn(0, 255),
        (blue * 255f).toInt().coerceIn(0, 255),
        hsv,
    )
    if (hsv[1] < 0.12f) return Color(0xFF4D72B8)
    hsv[1] = hsv[1].coerceAtLeast(0.34f)
    hsv[2] = hsv[2].coerceIn(0.46f, 0.88f)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = 1f,
)

private fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private fun blendDynamicFlowColors(first: Color, second: Color, amount: Float): Color {
    val fraction = amount.coerceIn(0f, 1f)
    return Color(
        red = first.red + (second.red - first.red) * fraction,
        green = first.green + (second.green - first.green) * fraction,
        blue = first.blue + (second.blue - first.blue) * fraction,
        alpha = first.alpha + (second.alpha - first.alpha) * fraction,
    )
}
