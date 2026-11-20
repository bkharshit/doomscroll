package com.doomscroll.android.domain.interceptor

import java.util.concurrent.ConcurrentHashMap

class InterceptionEngine(
    val rules: List<InterceptionRule> = listOf(
        InstagramReelsRule(),
        YouTubeShortsRule()
    )
) {
    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()
    private val backActionHistory = ConcurrentHashMap<String, MutableList<Long>>()

    private val actionCooldownMillis = 1000L
    private val loopWindowMillis = 10_000L
    private val loopThresholdCount = 3

    fun shouldProcessPackage(packageName: String): Boolean {
        return rules.any { it.canHandle(packageName) }
    }

    fun ruleFor(packageName: String): InterceptionRule? = rules.firstOrNull { it.canHandle(packageName) }

    /** Rule id -> the package it intercepts, for attributing metrics. */
    val rulePackages: Map<String, String> get() = rules.associate { it.id to it.targetPackage }

    /**
     * Never returns [Decision.Pass] while the rule detects short-form content: repeated attempts
     * (tapping Reels again and again) must not wear the blocker down.
     *
     * - Within the cooldown after an action, returns [Decision.RetryAfter] so the caller re-checks.
     * - If system Back has been used [loopThresholdCount] times within [loopWindowMillis] and the
     *   content is still there, Back isn't working on this screen, so escalate to Home rather than
     *   pressing Back again (which could also exit the app repeatedly). Tab clicks never escalate.
     */
    fun evaluate(screen: Screen, currentTimeMillis: Long = System.currentTimeMillis()): Decision {
        val rule = ruleFor(screen.packageName) ?: return Decision.Pass

        val decision = rule.evaluate(screen)
        if (decision !is Decision.Act) return decision

        val sinceLast = currentTimeMillis - (lastActionTimestamps[rule.id] ?: Long.MIN_VALUE / 2)
        if (sinceLast < actionCooldownMillis) {
            return Decision.RetryAfter(actionCooldownMillis - sinceLast)
        }
        lastActionTimestamps[rule.id] = currentTimeMillis

        if (!decision.action.usesSystemBack()) return decision

        val history = backActionHistory.getOrPut(rule.id) { mutableListOf() }
        val loopDetected = synchronized(history) {
            history.removeAll { currentTimeMillis - it > loopWindowMillis }
            history.add(currentTimeMillis)
            (history.size >= loopThresholdCount).also { if (it) history.clear() }
        }
        return if (loopDetected) Decision.Act("${decision.reason} (Back loop: going Home)", Action.Home) else decision
    }

    private fun Action.usesSystemBack(): Boolean = this is Action.Back || this is Action.ClickInAppBack
}
