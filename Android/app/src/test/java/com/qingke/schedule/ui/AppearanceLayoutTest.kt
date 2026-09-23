package com.qingke.schedule.ui

import com.qingke.schedule.preferences.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3-09／A11: the appearance option copy and the responsive rule are plain values, so the row-to-column switch is
 * pinned by fast JVM tests instead of only by screenshots.
 */
class AppearanceLayoutTest {
    @Test
    fun theCopyMatchesTheIosBaselineInOrder() {
        assertEquals(
            listOf(AppearanceMode.SYSTEM, AppearanceMode.LIGHT, AppearanceMode.DARK),
            appearanceOptions.map { it.mode },
        )
        assertEquals(listOf("system", "light", "dark"), appearanceOptions.map { it.mode.storageValue })
        assertEquals(listOf("AUTO", "LIGHT", "DARK"), appearanceOptions.map { it.code })
        assertEquals(listOf("跟随系统", "浅色", "深色"), appearanceOptions.map { it.title })
    }

    @Test
    fun theThreeOptionsShareOneRowWhileThereIsRoom() {
        assertFalse(shouldStackAppearanceOptions(343f, 1f))
        assertFalse(shouldStackAppearanceOptions(300f, 1f))
        assertFalse(shouldStackAppearanceOptions(420f, 1.29f))
    }

    @Test
    fun narrowScreensStackInsteadOfClippingTheLabels() {
        assertTrue("320dp screen inside the panel", shouldStackAppearanceOptions(252f, 1f))
        assertTrue(shouldStackAppearanceOptions(299.9f, 1f))
    }

    @Test
    fun largeFontsStackAtTheAccessibilityThresholdAndAbove() {
        assertTrue("130% font", shouldStackAppearanceOptions(343f, 1.3f))
        assertTrue("200% font", shouldStackAppearanceOptions(343f, 2f))
        assertTrue("320dp screen with a large font", shouldStackAppearanceOptions(252f, 2f))
    }
}
