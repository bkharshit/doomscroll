package com.doomscroll.android.core.util

data class UsageEvt(
    val packageName: String,
    val className: String?,
    val eventType: Int,
    val timestampMillis: Long
) {
    companion object {
        const val TYPE_ACTIVITY_RESUMED = 1
        const val TYPE_ACTIVITY_PAUSED = 2
        const val TYPE_SCREEN_NON_INTERACTIVE = 16
        const val TYPE_KEYGUARD_SHOWN = 18
        const val TYPE_ACTIVITY_STOPPED = 23
        const val TYPE_DEVICE_SHUTDOWN = 26
    }
}

object ForegroundCalculator {

    /**
     * Computes today's foreground duration in seconds for each package in [packages].
     *
     * @param events List of raw usage events occurring between (fromMillis - lookback) and nowMillis
     * @param fromMillis Start of today in epoch millis (local midnight)
     * @param nowMillis Current time in epoch millis
     * @param packages The set of package names to compute foreground time for
     * @return Map of packageName to foreground duration in SECONDS
     */
    fun computeSeconds(
        events: List<UsageEvt>,
        fromMillis: Long,
        nowMillis: Long,
        packages: Set<String>
    ): Map<String, Long> {
        val sortedEvents = events.sortedBy { it.timestampMillis }

        val activeActivitiesPerPackage = mutableMapOf<String, MutableSet<String>>()
        val sessionStartPerPackage = mutableMapOf<String, Long?>()
        val totalMillisPerPackage = mutableMapOf<String, Long>()

        packages.forEach { pkg ->
            activeActivitiesPerPackage[pkg] = mutableSetOf()
            sessionStartPerPackage[pkg] = null
            totalMillisPerPackage[pkg] = 0L
        }

        fun closePackageSession(pkg: String, eventTimestamp: Long) {
            val start = sessionStartPerPackage[pkg] ?: return
            val end = eventTimestamp.coerceIn(fromMillis, nowMillis)
            if (end > start) {
                totalMillisPerPackage[pkg] = (totalMillisPerPackage[pkg] ?: 0L) + (end - start)
            }
            sessionStartPerPackage[pkg] = null
            activeActivitiesPerPackage[pkg]?.clear()
        }

        for (event in sortedEvents) {
            when (event.eventType) {
                UsageEvt.TYPE_ACTIVITY_RESUMED -> {
                    val pkg = event.packageName
                    if (pkg in packages) {
                        val activeActs = activeActivitiesPerPackage.getOrPut(pkg) { mutableSetOf() }
                        if (activeActs.isEmpty()) {
                            // Package just entered foreground
                            val effectiveStart = event.timestampMillis.coerceAtLeast(fromMillis)
                            sessionStartPerPackage[pkg] = effectiveStart
                        }
                        activeActs.add(event.className ?: "default_activity")
                    }
                }

                UsageEvt.TYPE_ACTIVITY_PAUSED, UsageEvt.TYPE_ACTIVITY_STOPPED -> {
                    val pkg = event.packageName
                    if (pkg in packages) {
                        val activeActs = activeActivitiesPerPackage[pkg]
                        if (activeActs != null) {
                            activeActs.remove(event.className ?: "default_activity")
                            if (activeActs.isEmpty()) {
                                closePackageSession(pkg, event.timestampMillis)
                            }
                        }
                    }
                }

                UsageEvt.TYPE_SCREEN_NON_INTERACTIVE,
                UsageEvt.TYPE_KEYGUARD_SHOWN,
                UsageEvt.TYPE_DEVICE_SHUTDOWN -> {
                    // System-wide interrupt: screen turned off or keyguard shown
                    packages.forEach { pkg ->
                        closePackageSession(pkg, event.timestampMillis)
                    }
                }
            }
        }

        // Add duration for any session still active at nowMillis
        packages.forEach { pkg ->
            val start = sessionStartPerPackage[pkg]
            if (start != null && nowMillis > start) {
                val end = nowMillis.coerceAtLeast(fromMillis)
                if (end > start) {
                    totalMillisPerPackage[pkg] = (totalMillisPerPackage[pkg] ?: 0L) + (end - start)
                }
            }
        }

        return packages.associateWith { pkg ->
            (totalMillisPerPackage[pkg] ?: 0L) / 1000L
        }
    }
}
