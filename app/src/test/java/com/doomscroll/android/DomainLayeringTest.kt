package com.doomscroll.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the domain layer free of Android, UI and service code (D9's single-module alternative to
 * Gradle modules), so its rules stay runnable as plain JVM tests.
 */
class DomainLayeringTest {

    private val forbidden = listOf(
        "import android.",
        "import androidx.",
        "import com.doomscroll.android.ui.",
        "import com.doomscroll.android.service.",
        "import com.doomscroll.android.data.local.",
        "import com.doomscroll.android.data.db.",
        "import org.json."
    )

    @Test
    fun domainHasNoPlatformImports() {
        val domain = File("src/main/java/com/doomscroll/android/domain")
        assertTrue("domain sources not found at ${domain.absolutePath}", domain.isDirectory)
        val violations = domain.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (forbidden.any { line.trim().startsWith(it) }) "${file.name}:${i + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue("Domain layer must not depend on Android/UI/service code:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
