package com.melox.player.ui.screen.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
internal fun WidePlayerContent(
    topPadding: Dp,
    bottomPadding: Dp,
    playbackPaneWidth: Dp,
    lyricsPaneWidth: Dp,
    paneSpacing: Dp,
    playbackContent: @Composable ColumnScope.() -> Unit,
    lyricsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(PLAYER_WIDE_FIXED_START_INSET))
        Column(
            modifier = Modifier
                .width(playbackPaneWidth)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            playbackContent()
        }
        Spacer(Modifier.width(paneSpacing))
        Box(
            modifier = Modifier
                .width(lyricsPaneWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            lyricsContent()
        }
    }
}
