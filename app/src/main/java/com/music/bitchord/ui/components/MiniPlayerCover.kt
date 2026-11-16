package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The mini player's cover, which fades from one track's cover into the next.
 *
 * A plain image cut on a skip: to the empty tile, and then to the new cover
 * once it had loaded. Here the last cover stays over the new one until the
 * new one has drawn (up to [COVER_WAIT_MS]), then fades out, as the full
 * player's sleeve does (see OutgoingArtwork).
 *
 * [hold] keeps the last cover in place of [model]: the track is a music video
 * whose song version is still being looked up, so its thumbnail is not the
 * cover to show (see ShownTrack).
 */
@Composable
fun MiniPlayerCover(
    model: String?,
    hold: Boolean,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val history = remember { CoverHistory() }
    val shown = if (hold && history.lastModel != null) history.lastModel else model
    SideEffect { if (!hold) history.lastModel = model }

    // Decided in the pass the cover changes: a new cover already in memory
    // draws in that same frame, so an overlay that waited for an effect would
    // come in a frame late, over a new cover already seen whole.
    val fade = remember(shown) {
        // A skip during a fade fades from whichever cover is most on screen.
        val previous = history.fade
        val from = previous?.from?.takeIf { !previous.done && previous.alpha.value > 0.5f }
            ?: history.drawn
        history.drawn = null
        CoverFade(from?.takeIf { !reduceAnimation }).also { history.fade = it }
    }
    LaunchedEffect(fade) {
        if (fade.from == null) return@LaunchedEffect
        withTimeoutOrNull(COVER_WAIT_MS) { snapshotFlow { fade.arrived }.first { it } }
        fade.alpha.animateTo(0f, tween(COVER_FADE_MS, easing = FastOutSlowInEasing))
        fade.done = true
    }

    Box(modifier) {
        AsyncImage(
            model = shown,
            contentDescription = null,
            contentScale = contentScale,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    history.drawn = state.painter
                    fade.arrived = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        val leaving = fade.from?.takeIf { !fade.done }
        if (leaving != null) {
            Image(
                painter = leaving,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade.alpha.value },
            )
        }
    }
}

/** One cover's fade out: [from] is null when there is nothing to fade. */
@Stable
private class CoverFade(val from: Painter?) {
    val alpha = Animatable(1f)
    var done by mutableStateOf(false)

    /** The next cover has drawn. */
    var arrived by mutableStateOf(false)
}

/** What [MiniPlayerCover] remembers across covers. Not state: nothing draws from it. */
private class CoverHistory {
    /** The picture the current cover last drew. */
    var drawn: Painter? = null
    var fade: CoverFade? = null

    /** The cover last asked for while nothing was held. */
    var lastModel: String? = null
}

/** How long the last cover waits for the next one to draw before it fades anyway. */
private const val COVER_WAIT_MS = 1_200L

private const val COVER_FADE_MS = 300
