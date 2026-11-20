package com.music.bitchord.ui.components

import com.music.bitchord.ui.screens.ARTIST_DIAL_PAGE_KEY
import com.music.bitchord.ui.screens.SPEED_DIAL_PAGE_KEYS
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** The Osmo icon is square. Kept under its existing name for call-site compatibility. */
const val SQUARE_LOGO_ASPECT = 1f

/** Pull feedback uses three gentle bumps while the mark grows. */
private const val LOGO_PULL_STEPS = 3

/**
 * The app's mark: the circular Osmo icon. Pulling gently grows the mark, and
 * refreshing turns the open ring while the play symbol stays still.
 */
@Composable
fun SquareLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    refreshing: Boolean = false,
    pullFraction: () -> Float = { 0f },
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val degrees = (now - last) / 1_000_000f * 0.45f
                rotation.snapTo((rotation.value + degrees) % 360f)
                last = now
            }
        } else if (rotation.value != 0f) {
            rotation.animateTo(0f, tween(durationMillis = 220))
        }
    }

    Canvas(modifier = modifier.aspectRatio(SQUARE_LOGO_ASPECT)) {
        val unit = min(size.width, size.height) / 100f * (1f + pullFraction().coerceIn(0f, 1f) * 0.02f)
        val left = (size.width - 100f * unit) / 2f
        val top = (size.height - 100f * unit) / 2f
        val center = Offset(left + 50f * unit, top + 50f * unit)

        rotate(rotation.value, center) {
            drawArc(
                color = color,
                startAngle = -5f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(left + 13f * unit, top + 13f * unit),
                size = Size(74f * unit, 74f * unit),
                style = Stroke(width = 6.5f * unit, cap = StrokeCap.Butt),
            )
            drawCircle(color, radius = 3.5f * unit, center = Offset(left + 80.3f * unit, top + 28.8f * unit))
        }

        val triangle = Path().apply {
            moveTo(left + 43.5f * unit, top + 39.5f * unit)
            lineTo(left + 62f * unit, top + 50f * unit)
            lineTo(left + 43.5f * unit, top + 60.5f * unit)
            close()
        }
        drawPath(triangle, color)
    }
}

/** The mark's height at rest, in the list's first row. */
val HOME_LOGO_LARGE = 60.dp

/** And pinned, once the list has been scrolled. */
val HOME_LOGO_SMALL = 34.dp

/** The mark's width at rest: [HOME_LOGO_LARGE] high, matching the square icon. */
val HOME_LOGO_WIDTH = HOME_LOGO_LARGE * SQUARE_LOGO_ASPECT

/** Space the list reserves at its top for the mark, so the first section clears it. */
val HOME_LOGO_RESERVE = 72.dp

/**
 * The mark on Home: in the list's first row at rest, and there for both
 * dials; scrolled on past them, it rides up to the top left, level with the
 * account button, at the same size.
 *
 * Meant to be drawn above the top bar, which is why it is not part of the list:
 * the fade the bar lays over scrolled content would otherwise wash it out.
 * [topPadding] is the list's own top content padding, from which the mark's
 * two resting places are measured.
 */
/**
 * Three bumps as the page is pulled down, matching the mark's pull feedback.
 * Only on the way down: letting go, or the
 * pull springing back, stays quiet. Shared by Home's mark and Library's, so a
 * pull feels the same on both tabs.
 */
@Composable
fun LogoPullHaptics(refreshing: Boolean, pullFraction: () -> Float) {
    val haptics = rememberHaptics()
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestPullFraction by rememberUpdatedState(pullFraction)
    LaunchedEffect(Unit) {
        var filled = 0
        snapshotFlow { (latestPullFraction().coerceIn(0f, 1f) * LOGO_PULL_STEPS).toInt() }
            .collect { now ->
                if (now > filled && !latestRefreshing) haptics.play(Haptic.Bump)
                filled = now
            }
    }
}

@Composable
fun HomeStickyLogo(
    listState: LazyListState,
    topPadding: Dp,
    refreshing: Boolean,
    pullFraction: () -> Float,
    modifier: Modifier = Modifier,
    /**
     * A tap on the mark, or null for none. Home's mark takes one only while a
     * category filters the page: the tap goes back to the whole of Home.
     */
    onLogoClick: (() -> Unit)? = null,
    /** Drawn beside the mark and moving with it, e.g. the app's name. */
    trailing: @Composable RowScope.() -> Unit = {},
) {
    // Where the mark sits at rest, and where it ends up pinned: centred on the
    // top bar, whose content is 52dp high with TopBarContentGap (12dp) below it.
    // It rides up there with the page but never shrinks.
    LogoPullHaptics(refreshing = refreshing, pullFraction = pullFraction)
    val restTop = topPadding - 20.dp
    val pinnedTop = topPadding - 12.dp - 26.dp - HOME_LOGO_LARGE / 2
    // As tall as the mark's lowest place reaches, so the mark is inside its
    // parent wherever it rides: a tap on it lands only there. Moved in
    // placement rather than in a graphics layer, so where it takes taps is
    // where it is drawn.
    Box(modifier.fillMaxWidth().height(restTop + HOME_LOGO_LARGE)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .offset {
                val travel = (restTop - pinnedTop).toPx()
                // Measured from the last dial page showing, so the mark stays
                // put while the dials turn (both rest in the same place under
                // it) and only rides up once the list goes on past them.
                val rest = HOME_LOGO_RESERVE.toPx()
                val dial = listState.layoutInfo.visibleItemsInfo.lastOrNull {
                    it.index <= 2 && (it.key == ARTIST_DIAL_PAGE_KEY || it.key in SPEED_DIAL_PAGE_KEYS)
                }
                val scrolled = when {
                    dial != null -> rest - dial.offset
                    listState.firstVisibleItemIndex == 0 -> listState.firstVisibleItemScrollOffset.toFloat()
                    else -> travel
                }
                IntOffset(0, (restTop.toPx() - scrolled.coerceIn(0f, travel)).roundToInt())
            }
            // Clear of the account button on the right.
            .padding(start = PAGE_GUTTER, end = 84.dp)
            .height(HOME_LOGO_LARGE),
    ) {
        SquareLogo(
            // Wears the playing song's colour; its own while nothing plays.
            color = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.onBackground,
            refreshing = refreshing,
            pullFraction = pullFraction,
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    if (onLogoClick != null) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onLogoClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        trailing()
    }
    }
}
