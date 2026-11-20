package com.doomscroll.android.domain.interceptor

import java.util.concurrent.ConcurrentHashMap

class InterceptionEngine(
    val rules: List<InterceptionRule> = listOf(
        InstagramReelsRule(),
        YouTubeShortsRule()
    )
) {
    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()

    private val actionCooldownMillis = 1000L

    fun shouldProcessPackage(packageName: String): Boolean {
        return rules.any { it.canHandle(packageName) }
    }

    fun ruleFor(packageName: String): InterceptionRule? = rules.firstOrNull { it.canHandle(packageName) }

    /** Rule id -> the package it intercepts, for attributing metrics. */
    val rulePackages: Map<String, String> get() = rules.associate { it.id to it.targetPackage }

    /**
     * Never returns [Decision.Pass] while the rule detects short-form content: repeated attempts
     * (tapping Reels again and again) must not wear the blocker down. Within the cooldown after an
     * action it returns [Decision.RetryAfter] so the caller re-checks once the cooldown ends.
     *
     * There is deliberately no escalation (e.g. to Home) after repeated actions: opening one Reel
     * after another means each Back worked, and leaving Instagram for that is wrong. If Back ever
     * fails to close the content, the retry simply presses it again a second later.
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
        return decision
    }
}
