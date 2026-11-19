package com.doomscroll.android

import com.doomscroll.android.domain.interceptor.InstagramReelsRule
import com.doomscroll.android.domain.interceptor.InterceptionEngine
import com.doomscroll.android.domain.interceptor.YouTubeShortsRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterceptionEngineTest {

    @Test
    fun testPackageFiltering() {
        val engine = InterceptionEngine(
            listOf(InstagramReelsRule(), YouTubeShortsRule())
        )

        assertTrue(engine.shouldProcessPackage("com.instagram.android"))
        assertTrue(engine.shouldProcessPackage("com.google.android.youtube"))
        assertFalse(engine.shouldProcessPackage("com.whatsapp"))
        assertFalse(engine.shouldProcessPackage("com.doomscroll.android"))
    }
}
