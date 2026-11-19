package com.doomscroll.android

import com.doomscroll.android.domain.interceptor.BackLabels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackLabelsTest {

    // e.g. an app running in German
    private val german = mapOf("back" to "Zurück", "abc_action_bar_up_description" to "Nach oben")

    @Test
    fun usesTheAppsOwnTranslations_plusEnglishFallback() {
        val labels = BackLabels.resolve { german[it] }
        assertTrue(BackLabels.matches(labels, null, "Zurück"))
        assertTrue(BackLabels.matches(labels, "nach oben", null))
        assertTrue("English still works", BackLabels.matches(labels, null, "Back"))
    }

    @Test
    fun matchesWholeLabelsOnly() {
        val labels = BackLabels.resolve { null }
        assertFalse(BackLabels.matches(labels, "Back to the 90s with this track", null))
        assertFalse(BackLabels.matches(labels, null, "Background music"))
        assertTrue(BackLabels.matches(labels, "  back ", null))
    }

    @Test
    fun lookupFailures_areIgnored() {
        val labels = BackLabels.resolve { throw IllegalStateException("no resources") }
        assertTrue(BackLabels.matches(labels, null, "Navigate up"))
    }
}
