package com.doomscroll.android

import com.doomscroll.android.domain.interceptor.InstagramReelsStrategy
import com.doomscroll.android.domain.interceptor.InterceptionEngine
import com.doomscroll.android.domain.interceptor.YouTubeShortsStrategy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterceptionEngineTest {

    @Test
    fun testPackageFiltering() {
        val engine = InterceptionEngine(
            listOf(InstagramReelsStrategy(), YouTubeShortsStrategy())
        )

        assertTrue(engine.shouldProcessPackage("com.instagram.android"))
        assertTrue(engine.shouldProcessPackage("com.google.android.youtube"))
        assertFalse(engine.shouldProcessPackage("com.whatsapp"))
        assertFalse(engine.shouldProcessPackage("com.doomscroll.android"))
    }
}
