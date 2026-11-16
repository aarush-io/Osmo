package com.music.bitchord.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveBackShrinkTest {
    @Test
    fun fullSizeBeforeTheSwipe() {
        assertEquals(1f, backShrinkScaleX(1080f, 144f, 0f), 0.0001f)
        assertEquals(1f, backShrinkScaleY(1200f, 72f, 0f), 0.0001f)
    }

    @Test
    fun shrinksByTheFixedAmountAtTheEnd() {
        // 1080 wide, 144px narrower = 936.
        assertEquals(936f / 1080f, backShrinkScaleX(1080f, 144f, 1f), 0.0001f)
        // 1200 tall, 72px shorter = 1128.
        assertEquals(1128f / 1200f, backShrinkScaleY(1200f, 72f, 1f), 0.0001f)
    }

    @Test
    fun followsTheSwipeInBetween() {
        assertEquals(1f - 72f / 1080f, backShrinkScaleX(1080f, 144f, 0.5f), 0.0001f)
    }

    @Test
    fun aTinyMenuNeverTurnsInsideOut() {
        assertEquals(0f, backShrinkScaleX(100f, 144f, 1f), 0.0001f)
        assertEquals(1f, backShrinkScaleX(0f, 144f, 1f), 0.0001f)
    }
}
