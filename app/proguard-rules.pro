# Doomscroll Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

-keep class com.doomscroll.android.service.DoomscrollAccessibilityService { *; }
-keep class com.doomscroll.android.core.model.** { *; }
-keep class com.doomscroll.android.domain.** { *; }
