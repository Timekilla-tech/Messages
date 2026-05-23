package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldableHelperTest {
    @Test
    fun isUnfoldedState_returnsTrueForUltraWideScreens() {
        val state = FoldableHelper.FoldStateInfo(
            screenWidth = 2400,
            screenHeight = 1000,
            isLargeScreen = true,
            aspectRatio = 2.4f,
        )

        assertTrue(FoldableHelper.isUnfoldedState(state))
    }

    @Test
    fun isUnfoldedState_returnsFalseForNormalScreens() {
        val state = FoldableHelper.FoldStateInfo(
            screenWidth = 1080,
            screenHeight = 2400,
            isLargeScreen = false,
            aspectRatio = 0.45f,
        )

        assertFalse(FoldableHelper.isUnfoldedState(state))
    }

    @Test
    fun getOptimalLayoutMetrics_returnsSplitLayoutForLargeScreens() {
        val state = FoldableHelper.FoldStateInfo(
            screenWidth = 2000,
            screenHeight = 1200,
            isLargeScreen = true,
            aspectRatio = 1.66f,
        )

        val metrics = FoldableHelper.getOptimalLayoutMetrics(state)

        assertEquals(1000, metrics.panelWidth)
        assertEquals(600, metrics.panelHeight)
        assertEquals(1600, metrics.maxContentWidth)
        assertTrue(metrics.isLargeLayout)
    }

    @Test
    fun getOptimalLayoutMetrics_returnsSingleLayoutForSmallScreens() {
        val state = FoldableHelper.FoldStateInfo(
            screenWidth = 1080,
            screenHeight = 2400,
            isLargeScreen = false,
            aspectRatio = 0.45f,
        )

        val metrics = FoldableHelper.getOptimalLayoutMetrics(state)

        assertEquals(1080, metrics.panelWidth)
        assertEquals(2400, metrics.panelHeight)
        assertEquals(1080, metrics.maxContentWidth)
        assertFalse(metrics.isLargeLayout)
    }
}

