package com.doomscroll.android.domain.limiter

import java.net.URLDecoder
import java.net.URLEncoder

/** Persistence port for break state. Kept in its own file so backups can exclude it. */
interface BreakStateStore {
    suspend fun load(): String?
    suspend fun save(encoded: String)
}

data class BreakSnapshot(
    val isActive: Boolean,
    val endTimeMillis: Long,
    val allowedPackage: String?,
    val intention: String?,
    val epochDay: Long,
    val used: Map<String, Int>
)

/** Small, dependency-free line format so it runs in plain JVM tests (org.json is an Android stub there). */
object BreakStateCodec {
    private const val VERSION = "1"

    fun encode(s: BreakSnapshot): String = buildString {
        appendLine("v=$VERSION")
        appendLine("active=${s.isActive}")
        appendLine("end=${s.endTimeMillis}")
        appendLine("pkg=${enc(s.allowedPackage ?: "")}")
        appendLine("intention=${enc(s.intention ?: "")}")
        appendLine("day=${s.epochDay}")
        append("used=" + s.used.entries.joinToString(",") { "${enc(it.key)}:${it.value}" })
    }

    fun decode(raw: String?): BreakSnapshot? {
        if (raw.isNullOrBlank()) return null
        return try {
            val fields = raw.lineSequence()
                .mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
                .toMap()
            if (fields["v"] != VERSION) return null
            BreakSnapshot(
                isActive = fields["active"] == "true",
                endTimeMillis = fields["end"]?.toLongOrNull() ?: 0L,
                allowedPackage = fields["pkg"]?.let(::dec)?.ifEmpty { null },
                intention = fields["intention"]?.let(::dec)?.ifEmpty { null },
                epochDay = fields["day"]?.toLongOrNull() ?: -1L,
                used = fields["used"].orEmpty().split(',').filter { it.isNotBlank() }.mapNotNull { entry ->
                    val i = entry.lastIndexOf(':')
                    if (i <= 0) null else dec(entry.substring(0, i)) to (entry.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
                }.toMap()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun dec(v: String) = URLDecoder.decode(v, "UTF-8")
}
