package com.melox.player.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.melox.player.model.NavigationTransitionStyle
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Keeps one Miuix navigation host while predictive back is toggled at runtime.
 */
@Composable
fun PredictiveNavDisplay(
    backStack: NavBackStack,
    predictiveBackEnabled: Boolean,
    transitionStyle: NavigationTransitionStyle,
    isDark: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: NavEntryBuilder.() -> Unit,
) {
    val hasPreviousEntries = backStack.size > 1
    val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
    val cornerRadius = rememberNavSystemCornerRadius()
    val useAospTransition = transitionStyle == NavigationTransitionStyle.AOSP
    val transition = if (useAospTransition) {
        AospNavigationTransition
    } else {
        NavTransitions.MiuixDefault
    }
    val effects = NavDisplayEffects(
        cornerClipRadius = if (useAospTransition && cornerRadius <= 0.dp) {
            32.dp
        } else {
            cornerRadius
        },
        cornerClipMode = if (useAospTransition) {
            NavCornerClipMode.All
        } else {
            NavCornerClipMode.Leading
        },
        dimAmount = if (useAospTransition) {
            if (isDark) 0.8f else 0.2f
        } else {
            0.5f
        },
        backdropColor = MiuixTheme.colorScheme.surface,
    )
    val disabledDispatcherOwner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher =
                NavigationEventDispatcher().also { it.isEnabled = false }
        }
    }
    val activeDispatcherOwner =
        dispatcherOwner.takeIf { predictiveBackEnabled } ?: disabledDispatcherOwner
    BackHandler(
        enabled = ordinaryBackHandlerEnabled(
            predictiveBackEnabled = predictiveBackEnabled,
            hasPreviousEntries = hasPreviousEntries,
        ),
        onBack = onBack,
    )
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides activeDispatcherOwner,
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = onBack,
            transition = transition,
            effects = effects,
            content = content,
        )
    }
}

internal fun predictiveBackHandlerEnabled(
    predictiveBackEnabled: Boolean,
    hasPreviousEntries: Boolean,
): Boolean = predictiveBackEnabled && hasPreviousEntries

internal fun ordinaryBackHandlerEnabled(
    predictiveBackEnabled: Boolean,
    hasPreviousEntries: Boolean,
): Boolean = !predictiveBackEnabled && hasPreviousEntries
