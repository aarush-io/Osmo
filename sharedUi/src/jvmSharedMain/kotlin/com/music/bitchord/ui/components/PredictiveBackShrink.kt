package com.music.bitchord.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** How much narrower a sheet gets at the end of a predictive back swipe (Material's figure). */
private val BACK_SHRINK_X = 48.dp

/** How much shorter it gets by then (Material's figure). */
private val BACK_SHRINK_Y = 24.dp

/**
 * A slide-up menu following a predictive back swipe: it shrinks toward the
 * middle of its bottom edge as the swipe goes, the way Material's sheets do,
 * and the exit that follows a committed swipe slides it off from there. Every
 * slide-up menu in the app answers back this way, rather than sliding down.
 *
 * [progress] is the swipe, 0..1, read only in draw. [pivotY] is where the
 * menu's visible bottom edge is, as a fraction of its own height: 1 for one
 * that rests on the bottom of the screen, more for a half-open sheet whose
 * rest is below the screen's edge.
 *
 * Pair with [predictiveBackShrinkContent] on what is inside, so the rows keep
 * their shape while the menu is squeezed more across than down.
 */
fun Modifier.predictiveBackShrink(
    progress: () -> Float,
    pivotY: () -> Float = { 1f },
): Modifier = graphicsLayer {
    val p = progress().coerceIn(0f, 1f)
    if (p > 0f && size.width > 0f && size.height > 0f) {
        scaleX = backShrinkScaleX(size.width, BACK_SHRINK_X.toPx(), p)
        scaleY = backShrinkScaleY(size.height, BACK_SHRINK_Y.toPx(), p)
        transformOrigin = TransformOrigin(0.5f, pivotY())
    }
}

/**
 * The inside of a menu under [predictiveBackShrink]: stretched back up by as
 * much as the menu was squeezed down more than across, from the top, so the
 * rows shrink evenly. [menuWidth] and [menuHeight] are the menu's own size in
 * pixels.
 */
fun Modifier.predictiveBackShrinkContent(
    progress: () -> Float,
    menuWidth: () -> Int,
    menuHeight: () -> Int,
): Modifier = graphicsLayer {
    val p = progress().coerceIn(0f, 1f)
    val w = menuWidth().toFloat()
    val h = menuHeight().toFloat()
    if (p > 0f && w > 0f && h > 0f) {
        val sx = backShrinkScaleX(w, BACK_SHRINK_X.toPx(), p)
        val sy = backShrinkScaleY(h, BACK_SHRINK_Y.toPx(), p)
        scaleY = sx / sy
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

/** The menu's horizontal scale at [progress] of a back swipe, for a menu [width] wide. */
internal fun backShrinkScaleX(width: Float, maxShrink: Float, progress: Float): Float =
    if (width <= 0f) 1f else 1f - min(maxShrink, width) * progress.coerceIn(0f, 1f) / width

/** The menu's vertical scale at [progress] of a back swipe, for a menu [height] tall. */
internal fun backShrinkScaleY(height: Float, maxShrink: Float, progress: Float): Float =
    if (height <= 0f) 1f else 1f - min(maxShrink, height) * progress.coerceIn(0f, 1f) / height
