package com.music.bitchord.ui.components.floatingtabbar

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class FitInsideBarTest {

    // A bar 300 wide and 57 tall: a 45 tall tab row with a 6 inset all round.
    private val left = -6f
    private val right = 294f
    private val bar = 57f

    @Test
    fun aRestingPillIsLeftAsItIs() {
        val size = Size(70f, 45f)
        assertEquals(size, fitInsideBar(size, centerX = 35f, left = left, right = right, barHeight = bar))
    }

    @Test
    fun aPillTallerThanTheBarIsCutToTheBarsHeight() {
        val fitted = fitInsideBar(Size(90f, 66f), centerX = 150f, left = left, right = right, barHeight = bar)
        assertEquals(bar, fitted.height, 0f)
        assertEquals(90f, fitted.width, 0f)
    }

    @Test
    fun aPillAtTheFirstTabEndsAtTheBarsEnd() {
        // The first tab's centre is 35 from the row start, 41 from the bar's end.
        val fitted = fitInsideBar(Size(95f, 57f), centerX = 35f, left = left, right = right, barHeight = bar)
        assertEquals(82f, fitted.width, 0.001f)
    }

    @Test
    fun aPillAtTheLastTabEndsAtTheBarsEnd() {
        val fitted = fitInsideBar(Size(95f, 57f), centerX = 253f, left = left, right = right, barHeight = bar)
        assertEquals(82f, fitted.width, 0.001f)
    }

    @Test
    fun anUnmeasuredBarChangesNothing() {
        val size = Size(95f, 66f)
        assertEquals(size, fitInsideBar(size, centerX = 0f, left = 0f, right = 0f, barHeight = 0f))
    }
}
