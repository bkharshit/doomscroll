package com.doomscroll.android.domain.interceptor

interface InterceptionRule {
    val id: String
    val targetPackage: String

    fun canHandle(packageName: String): Boolean = targetPackage == packageName

    fun evaluate(screen: Screen): Decision
}
