# Doomscroll Android — Architecture & Code Review

Nov 19, 2026 · @Harshit

## Executive summary

The structure is sound, but the app cannot yet keep its core promise that users can't get around their limits. Four P0 defects mean limits can be silently loosened, overrun mid-session, mis-measured, or skipped on the most common entry paths to Reels and Shorts.

The layering (domain, data, UI), the Strategy pattern for per-app interception and the hot in-memory settings cache in the service are the right foundations. They should be kept.

The gaps fall into four groups:

- **Enforcement is unreliable.** Limits are checked only when an app opens, against usage numbers that can be wrong, and a startup routine raises any limit under 60 minutes back to 60.
- **Bypass costs one tap.** The block screen offers unlimited 5-minute emergency breaks, and the dashboard pause switches off protection for every app.
- **The UI promises features that don't exist.** Strict Lockdown, per-app block toggles and downtime aren't connected to anything, and the Limits screen ships a hard-coded weekly chart.
- **The valuable logic is untested.** The three unit tests cover formatting and enum lookups. The interception rules, which break whenever Meta or Google rename a view, have no tests at all.

The companion **Remediation Design** doc sets out the fixes. Together they come to about three focused milestones of work.

## Scope and method

This is a static review of the whole Kotlin source tree (about 5,000 lines in 40 files), the Gradle build, the manifest and the XML resources. Nothing was run on a device.

- **Read in full:** the accessibility service, both interception strategies, the limit enforcer, the break manager, the repositories, the DataStore wrapper, the models, the ViewModels and both activities.
- **Read for logic and state:** the Compose screens (Dashboard, Limits, Settings/Permissions, Block screen) and the shared components. Visual design was out of scope.
- **Also looked at:** the repo-root artefacts (third-party binaries and notes, `app/release.keystore`) and the three unit tests.

Findings marked *verify* are inferred from the code and depend on how Instagram and YouTube lay out their screens at runtime. Confirm them with a view-tree dump on a device before relying on them.

Severity scale:

| Level | Meaning |
| --- | --- |
| P0 | Breaks the core promise or silently corrupts user settings. Fix before any release. |
| P1 | A visible gap between what the product says and what it does, or an easy bypass. |
| P2 | Design, performance or maintainability debt that will slow future work. |
| P3 | Hygiene and polish. |

## System as built

One accessibility service makes every runtime decision. It receives events from the target apps, runs the per-app interception strategy, and on a window change checks the daily limit against UsageStatsManager.

&#91;embedded content: Doomscroll as built · one service, two strategies, two system data sources\]

The UI only writes settings and reads usage. It plays no part in enforcement, so anything that fails inside the service fails silently.

## Strengths worth keeping

These choices are right, and the remediation design builds on them rather than replacing them.

- **Strategy pattern for interception.** `ContentInterceptionStrategy` with one class per app is the right seam. Adding TikTok or Facebook should mean adding a class, not editing the service.
- **Hot settings cache in the service.** The service collects the shield flags into `@Volatile` fields, so no event reads DataStore. That keeps the per-event path synchronous.
- **Cooldowns on automated actions.** The 1-second cooldown in each strategy and the 2-second debounce in `UsageLimitEnforcer` stop click and back loops.
- **Careful exemptions.** Stories, the camera and DMs are explicitly exempt, which shows good product judgement about what not to break.
- **Unidirectional UI state.** The `StateFlow<UiState>` and `DashboardIntent` contract is idiomatic and easy to test once dependencies are injectable.
- **Isolated block screen.** `singleInstance`, an empty `taskAffinity` and `excludeFromRecents` keep the block screen out of the target app's task and the recents list.
- **Modern toolchain.** Version catalog, Compose BOM, Kotlin 2.0, targetSdk 35 and Java 17.

## P0: Correctness findings

Eight defects, each of which either lets a user overrun a limit without being stopped or shows them wrong data. Fixes are in the design doc under the section named in brackets.

### R1. Every app start raises user limits to at least 60 minutes \[D1\]

**Evidence.** `PreferencesDataStore.initializeDefaultLimits()` (lines 116–124) is launched from `DoomscrollApp.onCreate`. For each monitored app it writes 60 whenever the stored value is missing **or below 60**.

**Impact.** A user sets Instagram to 30 minutes. The next time the process starts (reboot, low-memory kill, update), the limit is 60 again with no sign anything changed. It also overrides the per-app defaults in `MonitoredApp` (20–45 minutes), so those defaults never take effect.

### R2. Limits are only checked when an app's window changes \[D3\]

**Evidence.** `DoomscrollAccessibilityService.onAccessibilityEvent` only calls `checkAndEnforceAppLimit` for `TYPE_WINDOW_STATE_CHANGED` (line 150). Nothing re-checks while the user stays in the app.

**Impact.** Open Instagram with 1 minute left and keep scrolling, and the block screen never appears. This is the most common way a time limit gets overrun.

### R3. Daily usage figures are unreliable \[D2\]

**Evidence.** `UsageRepositoryImpl` calls `queryUsageStats(INTERVAL_DAILY, startOfToday, now)` and takes `firstOrNull { it.packageName == pkg }`.

**Impact.** That API returns whole daily buckets that *overlap* the range, so the result can include yesterday's bucket, or several buckets for one package with an arbitrary one picked. Bucket boundaries follow the system's rollover rather than local midnight, and on some OEMs a bucket's foreground time lags by minutes. Limits fire late, early or not at all, and the dashboard shows the wrong totals.

### R4. Reels and Shorts opened from the home feed are probably not blocked \[D4\] *verify*

**Evidence.** `InstagramReelsStrategy` returns `PASS` as soon as `feed_tab` is selected (lines 64–68). `YouTubeShortsStrategy` does the same for `pivot_home_item` (lines 31–35). Both checks run *before* the full-screen player detection.

**Impact.** Tapping a Reel in the Instagram home feed or a Short on the YouTube home shelf opens the player while the bottom bar's Home item may still be selected. The guard exists to stop the strategy re-clicking Home, but its placement short-circuits detection altogether.

### R5. Exemptions are easier to trigger than blocks \[D4\]

**Evidence.** The Stories and camera exemptions use `isNotEmpty()`, but Reel detection requires `isVisibleToUser`. The DM exemption also matches the text "Reply privately".

**Impact.** A story viewer kept in the view tree off-screen (which fragment back stacks do) exempts any Reel opened afterwards. "Reply privately" can also appear on Reel comment sheets, so opening comments may switch off blocking for that Reel.

### R6. Text matching only works in English \[D4\]

**Evidence.** `findAccessibilityNodeInfosByText("Back")`, `"Back to chat"` and `"Reply privately"`.

**Impact.** In any other UI language the in-app back fallback never matches, so the strategy falls through to global Back, which can exit the app. The DM exemption also never fires, so shared Reels in chats get blocked.

### R7. The break countdown never ticks \[D8\]

**Evidence.** `BreakSession.remainingSeconds` reads `System.currentTimeMillis()` in a getter. The dashboard displays it (`DashboardScreen.kt` lines 517 and 547), but nothing causes a recomposition each second.

**Impact.** The "N minutes remaining" label freezes at the value shown when the break started, until something unrelated recomposes the screen.

### R8. The "blocked today" count only resets when the Dashboard opens \[D7\]

**Evidence.** `MetricsRepository.ensureDailyReset()` is called only from `DashboardViewModel.init`. Rollover is detected by comparing `DAY_OF_YEAR`.

**Impact.** If the user doesn't open Doomscroll on a given day, every block that day is added to yesterday's count. A process that stays alive past midnight keeps the stale value too. Comparing day-of-year also collides across years, although rarely.

## P1: Product and behaviour gaps

These don't crash anything, but each one either undermines a self-control app (an easy bypass) or tells the user something that isn't true.

### R9. Emergency breaks are unlimited \[D6\]

The block screen's "emergency break" (`BlockedAppActivity`, line 36) grants 5 minutes, and nothing limits how often. Tapping it after each expiry gives unlimited time, so the daily limit only costs one tap every 5 minutes.

### R10. A dashboard pause switches off protection for every app \[D6\]

`DashboardIntent.StartBreak` calls `startBreak` without a `targetPackage`, so `allowedPackage` is `null` and `isBreakActiveForPackage` returns true for every package. Interception and limit checks both stop everywhere.

### R11. Break state is only held in memory \[D6\]

`MindfulBreakManager` keeps the session in a `MutableStateFlow` and expires it with a main-thread `Handler`. If the process dies, the break disappears. That fails safe, but break history can't be counted (which R9 needs), and the `onExpire` callback is never used.

### R12. Settings that do nothing

| UI or code | What actually happens |
| --- | --- |
| Strict Lockdown Mode switch (Settings screen) | `remember { mutableStateOf(true) }`. Nothing reads it, and it resets when you leave the screen. |
| Per-app block toggle | `LimitsViewModel.toggleAppBlock` exists, but no UI calls it. The Limits card shows `isBlocked` read-only. |
| Downtime schedule | Five DataStore keys and the `isSleepDowntime` parameter exist, but nothing reads or sets them. |
| Mindful Break dialog | `ShowBreakDialog` and `isBreakDialogVisible` are handled, but nothing sends the intent or renders `MindfulBreakDialog`. |
| Reels/Shorts toggles on the Limits screen | `toggleReelsBlocker` and `toggleShortsBlocker` exist in `LimitsViewModel`, but the screen never calls them. |

### R13. Hard-coded fake data in production screens \[D8\]

- The Limits weekly chart is a fixed list ("Monday 1h 20m", "Tuesday 2h 32m" and so on), and the last bar is always labelled "Sunday (Today)".
- Total usage falls back to the string "41m" when there's no data.
- Three placeholder quota cards (Instagram, YouTube, Reddit) appear whenever `appLimits` is empty, including during the first load.

A user with no usage permission sees invented numbers presented as their own.

### R14. Misleading metrics \[D7\]

- "Time saved" is `reelsBlockedToday × 45 seconds`, an unstated assumption.
- Every block, including YouTube Shorts, is credited to Instagram (`DashboardViewModel` line 135).
- A single attempt to open a Reel can count several times if the user retries straight after the 1-second cooldown.

### R15. Limit ranges contradict each other \[D1, D8\]

| Place | Range |
| --- | --- |
| Limits screen slider | 15–300 min, 5-minute steps |
| `AppLimitRow` (unused) | 5–120 min |
| README | 5–120 min |
| `initializeDefaultLimits` | Forces a minimum of 60 |
| `MonitoredApp` defaults | 20–45 min |

## Architecture findings (P2)

The README describes Clean Architecture, but the domain layer depends on Android framework types. That one fact explains most of the testing gap.

### R16. The domain layer depends on Android

- `ContentInterceptionStrategy.evaluateAndIntercept` takes `AccessibilityNodeInfo` and `AccessibilityService`, and performs clicks and global actions itself.
- `UsageLimitEnforcer` takes a `Context` and starts `BlockedAppActivity`, so the domain package imports a UI class.
- `MindfulBreakManager` uses `Handler(Looper.getMainLooper())` and the wall clock directly.

**Consequence.** None of the decision logic can run in a plain JVM unit test, and the rules can't be replayed against recorded screens. Detection and action are also fused, so a strategy can't be asked what it *would* do without it doing it.

### R17. The Strategy abstraction leaks

- The service hard-codes package names to pick each strategy's enable flag (`when (packageName) { "com.instagram.android" -> … }`, line 116). Adding a strategy means editing the service.
- `clickNodeOrParent` and `isVisibleNodePresent` are copied into both strategies.
- The `allowSentReelsInChat` parameter is threaded through the engine but is always `true`, and YouTube ignores it.
- Package names appear as string literals in four places: `MonitoredApp`, both strategies, the service and `DashboardViewModel`.

### R18. Dependency wiring is a service locator

`DoomscrollApp` exposes seven `lateinit` singletons that activities and the service pull through `applicationContext as DoomscrollApp`. A hand-written `ViewModelProvider.Factory` per ViewModel duplicates the wiring. `Application.onCreate` also launches work on an unscoped `CoroutineScope(Dispatchers.IO)`.

This is fine at five screens, but it makes test substitution awkward and hides the dependency graph.

### R19. A ViewModel and a composable do platform work

- `DashboardViewModel` holds a `Context` only to call `PermissionHelper`.
- `PermissionsAssistantScreen` calls four permission checks (binder calls) **during composition**, on every recomposition, and doesn't refresh on resume. After granting a permission in system Settings, the screen still shows it as missing until something else recomposes it.
- `DashboardViewModel.loadUsageStats` reads `_uiState.value.appLimits` during `init`, before the limits flow has emitted, so the first load uses defaults.

### R20. One flow collector per setting

The service starts three coroutines, one per boolean, each collecting `dataStore.data.map {}`. Every DataStore write therefore wakes all three, and the Dashboard and Limits ViewModels both collect `getAppLimits()` as well, which combines 10 flows. A single `Settings` snapshot flow, mapped and `distinctUntilChanged`, would be simpler and cheaper.

## Performance findings (P2)

The biggest cost is the accessibility event path. It runs on the main thread, makes many cross-process calls per event, and fires during every scroll in Instagram and YouTube.

### R21. Heavy main-thread work for every accessibility event \[D5\]

The service subscribes to scroll, content-change, state-change, selection and click events with a 50 ms `notificationTimeout`. For each Instagram event that isn't exempted, the strategy calls `findAccessibilityNodeInfosByViewId` or `ByText` up to **13 times**: 5 camera IDs, 2 story IDs, the composer ID, 2 text searches, the feed tab, the clips tab and 3 player IDs, plus parent walks on a click.

Each call is a binder round trip into the target app's process, which walks the view tree. All of this runs on the service's main thread. `flagIncludeNotImportantViews` also makes every tree larger than it needs to be.

**Expected effect:** extra battery use and scroll jank inside Instagram and YouTube, the apps the user is in. This should be measured with a Perfetto trace before and after the fix.

### R22. Slider drags write to disk on every frame \[D8\]

`AppQuotaCard`'s `TactileProgressSlider.onValueChange` calls `viewModel.updateLimit` directly, which calls `dataStore.edit`. A 1-second drag causes around 60 disk writes, and each write re-emits every settings flow (R20).

### R23. Neumorphic shadows allocate on every draw \[D8\]

`Modifier.neoExtruded` creates two `android.graphics.Paint` objects and two `BlurMaskFilter` objects inside every `drawBehind`. It's used on every card and on the animated tab indicator, so allocations happen on every animation frame. Blur mask filters are also expensive to rasterise on low-end GPUs.

### R24. Node lists aren't recycled on older Android versions

With `minSdk` 26, `AccessibilityNodeInfo` objects come from a pool until API 33. The service recycles the root, but the lists returned by `find…` calls and the parent walk in `clickNodeOrParent` aren't recycled. This creates garbage-collection churn on Android 8–12 devices.

### R25. Cross-package debounce in the enforcer

`UsageLimitEnforcer` keeps one global `lastLaunchTimestamp`. Switching from a blocked Instagram to a blocked TikTok within 2 seconds skips TikTok's block screen. That's minor, but the debounce should be per package.

## Security, Play policy and repo hygiene

The two most urgent items aren't in the code. The release signing key sits in the source tree, and third-party binaries sit beside it, in a project the README calls open source.

### R26. Release keystore in the source tree (P0 before publishing)

`app/release.keystore` sits beside `build.gradle.kts`, and there's no `.gitignore`. The folder isn't a git repository yet, so the first `git add .` and push would publish the signing key. Anyone holding it can ship updates that Android accepts as yours.

### R27. Third-party material in the repo (P1)

The repo root holds a third-party APK (64 MB), a 42 MB tool jar and research notes. Publishing third-party binaries alongside the code invites a takedown or licence dispute. Keep them out of version control.

### R28. Over-broad permissions and capabilities (P2)

| Declared | Used? | Action |
| --- | --- | --- |
| `canRequestFilterKeyEvents` | No | Remove |
| `canPerformGestures` | No (only `performGlobalAction` and `ACTION_CLICK`) | Remove |
| `flagIncludeNotImportantViews` | Only partly needed | Remove; re-add only if a view ID needs it |
| `SYSTEM_ALERT_WINDOW` | No overlay is drawn | Remove, or use it for D3's overlay option |
| `FOREGROUND_SERVICE` | No foreground service | Remove |
| `POST_NOTIFICATIONS` | A channel is created but nothing posts | Keep only if D6 adds break notifications, and request it at runtime |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Used | Keep, but Play restricts it; justify it or use the settings-list intent |

### R29. Play accessibility policy (P1 for a Play release)

Play requires a prominent in-app disclosure, shown before the user is sent to Accessibility settings, that explains what data is read and why. It also needs a Play Console declaration and a description in `accessibility_service_description` that matches the behaviour. The permissions assistant goes straight to the settings screen with no disclosure.

### R30. Backups miss the settings (P3)

`backup_rules.xml` and `data_extraction_rules.xml` include only the `sharedpref` domain. DataStore writes to `files/datastore/doomscroll_prefs.preferences_pb`, so limits and toggles aren't backed up or transferred. Either include `<include domain="file" path="datastore/"/>` or, if a reinstall *should* reset everything, set `allowBackup=false`.

### R31. Release build settings (P3)

- `isMinifyEnabled = false` and no `signingConfig`. Enable R8 with resource shrinking, and keep the accessibility service and model classes if needed.
- `onBackPressed` is deprecated. Use `OnBackPressedDispatcher`, which also works with predictive back.
- `versionCode` is 1 with no CI. Add a build that runs tests and lint on every push.

### R32. Smaller items (P3)

- The README says "OLED dark-mode first", but the theme is a light neumorphic `lightColorScheme`, and `BgDark` is set to `TactileBg` (`#F7F9FF`).
- Four components are never used: `AppLimitRow`, `StatCard`, `PermissionBanner` and `MindfulBreakDialog`.
- `DoomscrollAccessibilityService.isServiceRunning` is never read.
- `PermissionHelper.isAccessibilityServiceEnabled` has a redundant `|| packageName` check.
- The accessibility-button toast strings are hard-coded and contain emoji, rather than coming from string resources.

## Testing assessment

There's effectively no test coverage of the behaviour that matters. The three test classes cover string formatting, enum lookup and a percentage getter, and there are no instrumented or UI tests.

| Area | Tests today | Risk if it breaks |
| --- | --- | --- |
| Instagram and YouTube detection rules | None | High: Meta and Google rename views with no notice, and blocking silently stops |
| Exemptions (Stories, DMs, camera) | None | High: users are blocked from things they should be allowed |
| Limit decisions and usage maths | None | High: the core promise |
| Break lifecycle and expiry | None | Medium |
| Settings persistence and migration | None (R1 would have been caught) | High |
| Daily rollover of metrics | None | Medium |
| ViewModels | None | Medium |
| `TimeUtils`, `UsageStat`, `InterceptionEngine.shouldProcessPackage` | 3 classes, 5 tests | Low |

**Root cause.** The rules are written against `AccessibilityNodeInfo`, which can't be constructed in a JVM test, and decisions are fused with side effects (R16). Robolectric could create nodes, but building realistic trees by hand is laborious. The design doc's approach is to record real view-tree dumps from devices as JSON fixtures and run the rules against those. It makes the app's biggest maintenance risk, upstream UI changes, into a failing test rather than a user complaint.

## Remediation roadmap

Fix in four milestones, in this order. Clean up the repo before the first commit, then restore trust in enforcement, then close the bypasses, then pay down the debt. The full designs are in Doc, and the D-numbers below refer to its sections.

0. **M0: Repo safety, before `git init` (half a day).** Move the keystore out, add `.gitignore`, and keep third-party binaries and notes out of version control (R26, R27).
1. **M1: Enforcement you can trust (about 1 week).** Fix the migration (D1), switch usage accounting to UsageEvents (D2), and add the foreground session timer with in-session limit checks (D3). Add unit tests for each. Closes R1–R3 and R25.
2. **M2: Interception correctness and speed (about 1 week).** Add the `UiNode` abstraction, the action model and the reordered rules (D4), the event pipeline throttling (D5), and the fixture-based test suite. Closes R4–R6, R16, R17, R21 and R24.
3. **M3: Honest product (about 1 week).** Add the break policy with persistence and friction (D6), metrics rollover and attribution (D7), and the UI fixes: remove fake data, wire up or hide unfinished settings, ticking countdown, slider debounce and permission refresh (D8). Closes R7–R15, R19, R22 and R23.
4. **M4: Platform and release (2–3 days).** Move to Hilt (D9), trim the manifest, add the Play disclosure, fix backup rules, enable R8 and add CI (D10). Closes R18, R20 and R28–R32.

M1 and M2 can run in parallel if two people are available, because they touch disjoint files. M3 depends on M1's session tracker.

## Appendix: Findings index

| ID | Finding | Severity | Primary location | Fix | Milestone |
| --- | --- | --- | --- | --- | --- |
| R1 | Startup raises limits to 60 min or more | P0 | `PreferencesDataStore.kt:116` | D1 | M1 |
| R2 | Limits checked only when the window changes | P0 | `DoomscrollAccessibilityService.kt:150` | D3 | M1 |
| R3 | `queryUsageStats` bucket inaccuracy | P0 | `UsageRepository.kt:30` | D2 | M1 |
| R4 | Home-tab guard skips feed Reels/Shorts (verify) | P0 | `InstagramReelsStrategy.kt:64`, `YouTubeShortsStrategy.kt:31` | D4 | M2 |
| R5 | Exemptions ignore visibility; "Reply privately" | P0 | `InstagramReelsStrategy.kt:140–184` | D4 | M2 |
| R6 | English-only text matching | P0 | `InstagramReelsStrategy.kt:128,177,180` | D4 | M2 |
| R7 | Break countdown frozen | P0 | `BreakSession.kt`, `DashboardScreen.kt:517` | D8 | M3 |
| R8 | Daily count resets only when Dashboard opens | P0 | `MetricsRepository.kt`, `DashboardViewModel.kt:37` | D7 | M3 |
| R9 | Unlimited emergency breaks | P1 | `BlockedAppActivity.kt:36` | D6 | M3 |
| R10 | Dashboard pause disables every app | P1 | `DashboardViewModel.kt:103` | D6 | M3 |
| R11 | Break held only in memory | P1 | `MindfulBreakManager.kt` | D6 | M3 |
| R12 | Settings that do nothing | P1 | Settings and Limits screens | D8 | M3 |
| R13 | Fake data in production screens | P1 | `LimitsScreen.kt:94–108,330–365` | D8 | M3 |
| R14 | Misleading metrics and attribution | P1 | `DashboardViewModel.kt:48,135` | D7 | M3 |
| R15 | Contradictory limit ranges | P1 | several | D1, D8 | M3 |
| R16 | Domain layer depends on Android | P2 | `domain/` | D4 | M2 |
| R17 | Strategy abstraction leaks | P2 | `DoomscrollAccessibilityService.kt:116` | D4 | M2 |
| R18 | Service-locator wiring | P2 | `DoomscrollApp.kt` | D9 | M4 |
| R19 | Platform work in ViewModel and composable | P2 | `DashboardViewModel.kt`, `PermissionsAssistantScreen.kt:76` | D8 | M3 |
| R20 | One flow collector per setting | P2 | service, repositories | D9 | M4 |
| R21 | Heavy main-thread work per event | P2 | strategies, `accessibility_service_config.xml` | D5 | M2 |
| R22 | Slider writes to disk every frame | P2 | `LimitsScreen.kt:584` | D8 | M3 |
| R23 | Shadow allocations on every draw | P2 | `Neumorphic.kt:67` | D8 | M3 |
| R24 | Node lists not recycled before API 33 | P2 | strategies | D5 | M2 |
| R25 | Global block-screen debounce | P2 | `UsageLimitEnforcer.kt:12` | D3 | M1 |
| R26 | Keystore in source tree | P0 (publish) | `app/release.keystore` | D10 | M0 |
| R27 | Third-party material in repo | P1 | repo root | D10 | M0 |
| R28 | Over-broad permissions | P2 | manifest, a11y config | D10 | M4 |
| R29 | Missing Play accessibility disclosure | P1 | permissions flow | D10 | M4 |
| R30 | Backups miss the DataStore file | P3 | `res/xml/*rules.xml` | D10 | M4 |
| R31 | Release build settings | P3 | `app/build.gradle.kts` | D10 | M4 |
| R32 | Smaller items | P3 | several | D10 | M4 |
