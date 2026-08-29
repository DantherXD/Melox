package com.melox.player.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class TopBarBlurSettings(
    val blurEnabled: Boolean,
    val progressiveEnabled: Boolean,
)

internal val LocalTopBarBlurSettings = compositionLocalOf<TopBarBlurSettings> {
    error("No TopBarBlurSettings provided")
}

@Composable
internal fun rememberBlurBackdrop(): LayerBackdrop? {
    val currentSettings = LocalTopBarBlurSettings.current
    if (!currentSettings.blurEnabled || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
internal fun LayerBackdrop?.miuixBarColor(): Color =
    if (this == null) MiuixTheme.colorScheme.surface else Color.Transparent

@Composable
internal fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    scrollBehavior: ScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    val progressive = LocalTopBarBlurSettings.current.progressiveEnabled
    val blurActive = blurEnabled && backdrop != null
    Box(
        modifier = if (blurActive && !progressive) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = barBlurColors(),
            )
        } else {
            Modifier
        },
    ) {
        if (blurActive && progressive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        gradient = ProgressiveBlur.Top.copy(
                            startFraction = 0f,
                            endFraction = 1f,
                            curve = 10f,
                        ),
                        blurRadius = 10f,
                        colors = barBlurColors(progressive = true),
                    ),
            )
        }
        content()
    }
}

@Composable
internal fun GaussianBlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = barBlurColors(),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

@Composable
private fun barBlurColors(progressive: Boolean = false): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(if (progressive) 0.3f else 0.8f)),
    ),
)
