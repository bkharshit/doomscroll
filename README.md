# Doomscroll 🌀
**Break the Infinite Scroll • Surgical Distraction Interceptor & Mindful Screen Time**

Doomscroll is an open-source Android application engineered to help users break out of compulsive short-form video consumption (Instagram Reels, YouTube Shorts, TikTok) and enforce healthy daily screen-time budgets.

<p align="center">
  <img src="app/src/main/res/drawable/ic_doomscroll_logo.png" width="160" height="160" alt="Doomscroll Logo" />
</p>

---

## 🌟 Key Features

1. **Surgical Content Interception (50ms Latency)**
   - Leverages an Android `AccessibilityService` (`DoomscrollAccessibilityService`) to detect short-form video view node signatures (such as Instagram Reels tabs and fullscreen viewer layouts) in real-time.
   - Automatically redirects users back to their safe feed or executes a global back gesture before the dopamine hook takes effect.
   - **Smart DM Context Exemption**: Detects conversation threads and text composers (`row_thread_composer_edittext`) so friends sending video links via Direct Message won't break messaging.

2. **Daily App Screen Time Budgets**
   - Monitors foreground app duration via Android's `UsageStatsManager`.
   - Allows fine-grained per-app daily time limits (5–120 minutes) across Instagram, YouTube, TikTok, Reddit, and X/Twitter.

3. **Isolated Lockout Enforcer (`BlockedAppActivity`)**
   - Single-instance (`launchMode="singleInstance"`, `taskAffinity=""`, `excludeFromRecents="true"`) lockout screen that safely interrupts distracting apps once a daily limit expires.
   - One-tap exit back to Android's Home Launcher (`ACTION_MAIN` + `CATEGORY_HOME`).

4. **"Post Mode" / Mindful Breaks**
   - Conscious Pause from the dashboard: 3, 5 or 10 minutes for every app, 10 per day, after a 10-second breathe and a typed intention.
   - Need more? Settings → "Add 5 pauses for today" unlocks after a 60-second wait. Extra pauses reset at midnight.
   - Emergency breaks from the block screen: 2 per app per day, with a growing wait (10 s, then 30 s) and a typed intention from the second one.
   - Strict Lockdown turns all pauses and breaks off. Turning it off takes a 60-second wait.
   - Ongoing countdown notification during a break (tap it to end early) and automatic re-enforcement when the break ends.

5. **Modern Jetpack Compose & Material 3 UI**
   - OLED dark-mode first design matching modern digital wellbeing aesthetics.
   - Step-by-step permission onboarding assistant with deep links to system settings.
   - Real-time impact metrics (reels blocked today, hours reclaimed).

---

## 🏗️ Architecture & Design Patterns

Designed under **Clean Architecture** and **Unidirectional Data Flow (MVI)**:

- **Strategy Pattern (`ContentInterceptionStrategy`)**:
  - `InstagramReelsStrategy`: Surgical detection of `clips_tab`, `clips_viewer_container`, `direct_tab`, etc.
  - `YouTubeShortsStrategy`: Surgical detection of `pivot_shorts_item`, `shorts_container`.
  - Extensible to support additional platforms without altering core service code.
- **Chain of Responsibility**:
  - Event pipeline verifying device interactive state, keyguards, mindful breaks, surgical view inspectors, and quota limits.
- **Repository Pattern**:
  - `SettingsRepository`: Thread-safe persistence with Jetpack DataStore.
  - `UsageRepository`: Queries `UsageStatsManager` daily intervals starting from midnight.
  - `MetricsRepository`: Tracks blocked counts and daily resets.
- **Unidirectional Data Flow**:
  - State flows down (`StateFlow<UiState>`), intents flow up (`UiIntent`).

---

## 📁 Project Structure

```
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── res/
│   │   │   ├── drawable/               # App logo & vector graphics
│   │   │   ├── mipmap-*/               # Multi-density launcher icons
│   │   │   ├── values/                 # Strings, themes, colors
│   │   │   └── xml/
│   │   │       └── accessibility_service_config.xml
│   │   └── java/com/doomscroll/android/
│   │       ├── DoomscrollApp.kt        # Application context & manual DI container
│   │       ├── core/
│   │       │   ├── model/              # MonitoredApp, AppLimit, UsageStat, BreakSession
│   │       │   └── util/               # PermissionHelper, TimeUtils
│   │       ├── data/
│   │       │   ├── local/              # PreferencesDataStore
│   │       │   └── repository/         # SettingsRepository, UsageRepository, MetricsRepository
│   │       ├── domain/
│   │       │   ├── interceptor/        # ContentInterceptionStrategy, InstagramReelsStrategy, YouTubeShortsStrategy, InterceptionEngine
│   │       │   └── limiter/            # UsageLimitEnforcer, MindfulBreakManager
│   │       ├── service/
│   │       │   └── DoomscrollAccessibilityService.kt
│   │       └── ui/
│   │           ├── MainActivity.kt     # Bottom Navigation & App Scaffold
│   │           ├── theme/              # Color, Type, Theme
│   │           ├── components/         # StatCard, AppLimitRow, PermissionBanner, MindfulBreakDialog
│   │           ├── dashboard/          # DashboardScreen, ViewModel, UiState
│   │           ├── limits/             # LimitsScreen, ViewModel
│   │           ├── permissions/        # PermissionsAssistantScreen
│   │           └── blockscreen/        # BlockedAppActivity, BlockedScreen
│   └── src/test/java/com/doomscroll/android/  # Unit tests
├── gradle/
│   └── libs.versions.toml             # Version Catalog
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🔒 Required Permissions & Rationale

| Permission | Purpose |
| :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Real-time surgical detection of Reels/Shorts elements and tab eviction. |
| `PACKAGE_USAGE_STATS` | Measuring daily foreground screen time for configured apps. |
| `SYSTEM_ALERT_WINDOW` | Displaying lockout enforcer over monitored apps when quotas expire. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ensuring the background interceptor is not terminated by aggressive OEM task-killers. |

---

## 🛠️ Building & Running

### Prerequisites
- JDK 17+
- Android SDK (API 35, Min SDK 26)

### Build Commands
```bash
# Run unit tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug
```
The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
