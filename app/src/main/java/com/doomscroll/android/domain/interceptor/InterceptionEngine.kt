package com.doomscroll.android.domain.interceptor

import java.util.concurrent.ConcurrentHashMap

class InterceptionEngine(
    val rules: List<InterceptionRule> = listOf(
        InstagramReelsRule(),
        YouTubeShortsRule()
    )
) {
    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()
    private val loopHistory = ConcurrentHashMap<String, MutableList<Long>>()
    private val loopBackoffUntil = ConcurrentHashMap<String, Long>()

    private val actionCooldownMillis = 1000L
    private val loopWindowMillis = 10_000L
    private val loopBackoffMillis = 30_000L
    private val loopThresholdCount = 3

    fun shouldProcessPackage(packageName: String): Boolean {
        return rules.any { it.canHandle(packageName) }
    }

    fun ruleFor(packageName: String): InterceptionRule? = rules.firstOrNull { it.canHandle(packageName) }

    /** Rule id -> the package it intercepts, for attributing metrics. */
    val rulePackages: Map<String, String> get() = rules.associate { it.id to it.targetPackage }

    fun evaluate(screen: Screen, currentTimeMillis: Long = System.currentTimeMillis()): Decision {
        val rule = rules.firstOrNull { it.canHandle(screen.packageName) } ?: return Decision.Pass

        // Check if rule is in loop backoff
        val backoffUntil = loopBackoffUntil[rule.id] ?: 0L
        if (currentTimeMillis < backoffUntil) {
            return Decision.Pass
        }

        // Check cooldown
        val lastAction = lastActionTimestamps[rule.id] ?: 0L
        if (currentTimeMillis - lastAction < actionCooldownMillis) {
            return Decision.Pass
        }

        val decision = rule.evaluate(screen)
        if (decision is Decision.Act) {
            // Check for rapid loop
            val history = loopHistory.getOrPut(rule.id) { mutableListOf() }
            synchronized(history) {
                history.removeAll { currentTimeMillis - it > loopWindowMillis }
                history.add(currentTimeMillis)
                if (history.size >= loopThresholdCount) {
                    loopBackoffUntil[rule.id] = currentTimeMillis + loopBackoffMillis
                    history.clear()
                }
            }

            lastActionTimestamps[rule.id] = currentTimeMillis
        }

        return decision
    }
}
