package org.fossify.messages.helpers

import android.content.Context

/**
 * Helper class for handling foldable phone configurations.
 * Detects screen metrics and dimensions for proper layout adaptation.
 */
class FoldableHelper(private val context: Context) {

    data class FoldStateInfo(
        val screenWidth: Int,
        val screenHeight: Int,
        val isLargeScreen: Boolean,
        val aspectRatio: Float
    )

    companion object {
        const val LARGE_SCREEN_WIDTH_DP = 500
        const val ASPECT_RATIO_ULTRA_WIDE = 2.0f // Typical for foldable unfolded

        fun isUnfoldedState(state: FoldStateInfo): Boolean {
            return state.aspectRatio > ASPECT_RATIO_ULTRA_WIDE
        }

        fun getOptimalLayoutMetrics(state: FoldStateInfo): LayoutMetrics {
            return if (state.isLargeScreen) {
                val panelWidth = state.screenWidth / 2
                val panelHeight = state.screenHeight / 2

                LayoutMetrics(
                    panelWidth = panelWidth,
                    panelHeight = panelHeight,
                    maxContentWidth = (state.screenWidth * 0.8).toInt(),
                    isLargeLayout = true
                )
            } else {
                LayoutMetrics(
                    panelWidth = state.screenWidth,
                    panelHeight = state.screenHeight,
                    maxContentWidth = state.screenWidth,
                    isLargeLayout = false
                )
            }
        }
    }

    /**
     * Get current screen dimensions
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        val displayMetrics = context.resources.displayMetrics
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    /**
     * Detect if the device has a large screen (tablet/foldable)
     */
    fun detectScreenState(): FoldStateInfo {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val densityDpi = displayMetrics.densityDpi

        // Convert pixels to dp
        val screenWidthDp = (screenWidth / (densityDpi / 160f)).toInt()
        val screenHeightDp = (screenHeight / (densityDpi / 160f)).toInt()

        val isLargeScreen = screenWidthDp >= LARGE_SCREEN_WIDTH_DP || screenHeightDp >= LARGE_SCREEN_WIDTH_DP
        val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

        return FoldStateInfo(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isLargeScreen = isLargeScreen,
            aspectRatio = aspectRatio
        )
    }

    /**
     * Check if device is in ultra-wide aspect ratio (typical for unfolded foldable)
     */

    data class LayoutMetrics(
        val panelWidth: Int = 0,
        val panelHeight: Int = 0,
        val maxContentWidth: Int = 0,
        val isLargeLayout: Boolean = false
    )
}

