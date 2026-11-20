# Doomscroll Android — Remediation Design

Nov 19, 2026 · @Harshit

## Context, goals and non-goals

This design fixes the 32 findings in Doc (cited below as R1–R32) in four milestones. It keeps the current module layout and the Strategy pattern, and splits decisions from side effects so the rules can be tested.

### Goals

1. **Limits hold.** A user who reaches a limit is blocked within 5 seconds, whether they opened the app or were already in it. Their configured limit is never changed without them.
2. **Usage is measured, not estimated.** On-screen usage is within ±1 minute of Android's Digital Wellbeing for the same day.
3. **Interception covers every entry path** to Reels and Shorts (tab, home feed, Explore, profile, deep link), and exemptions apply only when the exempt screen is actually visible.
4. **Bypass costs effort.** A limited number of breaks per day, with friction, and no single tap that switches off everything.
5. **The rules are testable.** Every rule runs in a JVM unit test against recorded screen fixtures, and CI flags a broken view ID before users do.
6. **The event path is cheap.** At most one tree traversal per handled event, no main-thread disk reads, and no measurable scroll jank in the target apps.
7. **The UI shows only real data,** and every visible control works.

### Non-goals

- Uninstall protection or Device Admin. It's out of scope until the basics hold, and Play treats it as high-risk.
- New platforms (TikTok in-app interception, Facebook, Snapchat). The new engine makes them cheap, but they're follow-ups.
- A server or account sync. Everything stays on the device.
- A visual redesign. The neumorphic look stays; only its performance is fixed.

### Constraints

- `minSdk` 26 and `targetSdk` 35. No root, Shizuku or Device Owner.
- Must pass Play's AccessibilityService policy (disclosure plus declaration).
- One or two engineers, and roughly 3–4 weeks in total.

## Target architecture

Every runtime decision moves into a domain layer with no Android dependencies, which the compiler enforces. The accessibility service shrinks to three jobs: filter events, track the foreground app, and carry out decisions.

&#91;embedded content: Target architecture · four layers, decisions in the domain\]

The service passes a read-only `Screen` snapshot down and receives a `Decision` back. The session tracker asks the policy layer how much budget remains. The data layer implements the domain's interfaces, so the domain can be tested with fakes and recorded fixtures.

| Today | Target | Design |
| --- | --- | --- |
| Strategies take `AccessibilityNodeInfo` and click directly | Rules take a `Screen` and return a `Decision`; an executor clicks | D4 |
| Limit checked on window change only | A session tracker schedules a deadline at the end of the budget | D3 |
| `queryUsageStats` daily buckets | Foreground time from UsageEvents | D2 |
| Startup routine overwrites limits | Versioned DataStore migration | D1 |
| In-memory break, unlimited | Persisted, rationed break with friction | D6 |
| Service locator in `Application` | Hilt plus module boundaries | D9 |

## D1. Settings schema and limits migration

**Fixes R1, R15 and R20.** Replace the startup routine that overwrites limits with a versioned DataStore migration that only ever fills in *missing* keys. All settings are exposed as one typed snapshot.

### Design

1. **Schema version.** Add `KEY_SCHEMA_VERSION` (int). Migrations are a list of `DataMigration<Preferences>` passed to `preferencesDataStore(produceMigrations = …)`, so DataStore runs them before the first read and writes atomically. Nothing runs on an unscoped `CoroutineScope` in `Application.onCreate`.
2. **Migration v0 → v1.**
   - For each `MonitoredApp`, write `defaultDailyLimitMinutes` **only if the key is absent**.
   - Users already damaged by R1 (stored value exactly 60, version absent) can't be told apart from users who chose 60. Leave 60 alone, and show a one-time "Check your limits" banner. See open question Q1.
3. **One settings model.** A `Settings` data class (master shield, per-feature toggles, per-app `AppPolicy(limitMinutes, blockEnabled)`, break policy, downtime) built in a single `dataStore.data.map { it.toSettings() }`. Consumers use `settings.map { it.x }.distinctUntilChanged()`, which replaces about 15 separate `map {}` flows.
4. **One limit range.** `LimitPolicy.MIN = 5`, `MAX = 300`, `STEP = 5` in the domain layer. The slider, the migration and validation all read these constants, and the README is updated to match. Writes clamp with `coerceIn(MIN, MAX)`.
5. **Setter API.** `SettingsRepository.update { settings -> settings.copy(…) }` maps to a single `dataStore.edit`, so related fields change atomically. That matters for D6's break counters.

```kotlin
val Context.dataStore by preferencesDataStore(
    name = "doomscroll_prefs",
    produceMigrations = { listOf(SeedMissingLimitsMigration) }
)

object SeedMissingLimitsMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(p: Preferences) = (p[KEY_SCHEMA_VERSION] ?: 0) < 1
    override suspend fun migrate(p: Preferences) = p.toMutablePreferences().apply {
        MonitoredApp.entries.forEach { app ->
            if (this[limitKey(app.packageName)] == null) this[limitKey(app.packageName)] = app.defaultDailyLimitMinutes
        }
        this[KEY_SCHEMA_VERSION] = 1
    }.toPreferences()
    override suspend fun cleanUp() = Unit
}
```

### Tests

- The migration on empty preferences seeds the defaults.
- It never overwrites an existing value. Property test: any stored value between 5 and 300 survives.
- It's idempotent: a second run is a no-op.
- `toSettings()` round-trips.

## D2. Accurate usage accounting

**Fixes R3.** Calculate today's foreground time from the raw resume and pause event stream (`UsageStatsManager.queryEvents`) between local midnight and now, instead of from the overlapping daily buckets. This is the method Digital Wellbeing-style apps use. It's exact to the event, respects local midnight, and counts a session that crosses midnight correctly.

### Algorithm

1. `start = startOfToday(zone)` using `java.time` (`LocalDate.now(zone).atStartOfDay(zone)`), so DST days are 23 or 25 hours and still correct.
2. Query events from `start − lookback` (3 hours), so an app that was already in the foreground at midnight has its resume event.
3. For each monitored package, walk events in time order:
   - `ACTIVITY_RESUMED` (`MOVE_TO_FOREGROUND` before API 29) opens a session at `max(t, start)`.
   - `ACTIVITY_PAUSED` / `ACTIVITY_STOPPED` / `MOVE_TO_BACKGROUND` closes it and adds `t − open`.
   - `SCREEN_NON_INTERACTIVE`, `KEYGUARD_SHOWN` or `DEVICE_SHUTDOWN` closes every open session.
   - Track open sessions per activity class, because one app can resume a second activity before pausing the first. The package is in the foreground while any of its activities is.
4. A session still open at `now` contributes `now − open`. This is the live portion that D3's timer relies on.
5. Return `Map<String, Duration>`.

### Interface

```kotlin
interface UsageSource {                         // data layer, Android
    suspend fun foregroundToday(packages: Set<String>, now: Instant): Map<String, Duration>
}

object ForegroundCalculator {                   // pure Kotlin, unit-tested
    fun compute(events: List<UsageEvt>, from: Instant, now: Instant, packages: Set<String>): Map<String, Duration>
}
```

The Android class only converts `UsageEvents` into `UsageEvt(pkg, cls, type, t)` and calls `ForegroundCalculator`. All the logic is pure.

### Cost and caching

`queryEvents` over about 27 hours costs roughly 5–30 ms on mid-range devices. D3 avoids calling it per event by keeping a *baseline plus live session* model. It queries once when a monitored app comes to the foreground, then adds elapsed time from its own clock. The dashboard queries on resume and every 60 seconds while visible.

### Fallback

If usage access is revoked, `UsageSource` returns `null`, never `0`. Limit enforcement then falls back to D3's own session clock for the current session only, and the UI shows a "Usage access needed" state instead of invented numbers (R13).

### Tests

Fixtures for:

- a plain resume and pause
- a session crossing midnight
- the screen turning off mid-session
- two activities overlapping
- a missing pause, closed at `now`
- the DST spring-forward day
- the API 26–28 event constants

## D3. Foreground session tracking and in-session enforcement

**Fixes R2 and R25.** A new `ForegroundSessionTracker` in the service records which monitored app is in the foreground and since when. When that app comes forward, it schedules one check for the moment its remaining budget runs out. That's enough to block a user mid-scroll within a few seconds of the limit, without polling.

### Foreground detection

The source is `TYPE_WINDOW_STATE_CHANGED` events, filtered to real app windows:

- Ignore the IME, the system UI (`com.android.systemui`), our own package, and windows whose `AccessibilityWindowInfo.type` isn't `TYPE_APPLICATION`.
- When the event can't be trusted (for example a notification shade over the app), use `windows.firstOrNull { it.isActive }?.root?.packageName`.
- Screen off or keyguard (from `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT` broadcasts registered by the service) ends the current session.

### State and the scheduled check

```kotlin
sealed interface Fg { object None : Fg; data class App(val pkg: String, val since: Instant, val baseline: Duration) : Fg }

class ForegroundSessionTracker(
    private val usage: UsageSource, private val policy: LimitDecider,
    private val clock: Clock, private val scope: CoroutineScope, private val enforcer: Enforcer
) {
    private var fg: Fg = Fg.None
    private var deadlineJob: Job? = null

    fun onForeground(pkg: String?) { /* close previous, open new, (re)schedule */ }
    fun onScreenOff() { onForeground(null) }
    private fun schedule(app: Fg.App) {
        deadlineJob?.cancel()
        val remaining = policy.remaining(app.pkg, app.baseline) ?: return   // null = no limit / break active
        deadlineJob = scope.launch { delay(remaining.coerceAtLeast(Duration.ZERO)); recheck(app.pkg) }
    }
}
```

1. **On entering an app.** Read the baseline once with `usage.foregroundToday(pkg)` (D2). If `baseline >= limit`, call `enforcer.block(pkg)` straight away. Otherwise schedule a check at `remaining = limit − baseline`.
2. **At the deadline.** Re-query usage (cheap, once), because the user may have used the app on another profile and D2 is the source of truth. If it's over the limit, block. If it's under (clock drift), reschedule for the new remaining time, with a 15-second minimum so a bad reading can't cause a tight loop.
3. **Reschedule when anything changes:** the limit, a block toggle, master shield, or a break starting or ending. The tracker collects `settings` and `breakState` and calls `schedule` again.
4. **On leaving the app or screen off:** cancel the deadline job.

### Why a coroutine `delay` and not AlarmManager

The accessibility service is a bound, long-lived service that the system keeps running while it's enabled, so the process stays alive while a monitored app is in the foreground. That's the only time a deadline matters. Doze doesn't apply while the screen is on and in use. `delay` on a scope tied to the service is therefore accurate enough, with no exact-alarm permission. Screen-off cancels it, so nothing runs in the background.

### Enforcer changes

- The debounce becomes per package: `Map<String, Instant>` (R25).
- `Enforcer` is an interface in the domain layer. The Android implementation launches `BlockedAppActivity` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`, and also calls `performGlobalAction(GLOBAL_ACTION_HOME)` first, so the target app isn't left running under the block screen.
- While the block screen is showing, a return to the blocked app (from recents, a notification or a deep link) re-triggers the block through `onForeground`.

### Tests

These run with `TestScope` and a fake `Clock` and `UsageSource`:

- Entering an app under the limit schedules a check at exactly the remaining time.
- The deadline fires and blocks.
- Leaving the app cancels the check.
- Changing the limit while in the app reschedules.
- A break pauses the check; when it ends, the check resumes with the correct remaining time.
- Two apps in quick succession each get their own debounce.

## D4. Interception engine redesign

**Fixes R4–R6, R16 and R17.** Strategies become pure functions from a read-only screen snapshot to a decision. The service turns the decision into clicks. Rules are reordered so that detecting a short-form player always comes first, and the Home-tab guard only prevents a pointless click.

### Abstractions (domain, pure Kotlin)

```kotlin
interface UiNode {
    val viewId: String?; val text: String?; val contentDescription: String?; val className: String?
    val isVisible: Boolean; val isSelected: Boolean; val isClickable: Boolean
    val boundsInScreen: Rect; val children: List<UiNode>; val parent: UiNode?
}

data class Screen(val pkg: String, val root: UiNode, val windowBounds: Rect) {
    // one traversal builds this index; every rule reads it instead of calling find… again
    val byId: Map<String, List<UiNode>> by lazy { index(root) }
    fun visible(id: String) = byId[id].orEmpty().filter { it.isVisible && coversMeaningfulArea(it) }
    fun selected(id: String) = byId[id].orEmpty().any { it.isSelected && it.isVisible }
}

sealed interface Decision {
    object Pass : Decision
    data class Act(val reason: Reason, val action: Action) : Decision
}
sealed interface Action {
    data class Click(val target: NodeRef) : Action      // NodeRef = path/viewId, resolved by the executor
    object Back : Action
    object Home : Action
}

interface InterceptionRule {
    val id: String                          // "instagram.reels", used for settings + metrics
    val packages: Set<String>
    val settingKey: FeatureKey              // fixes R17: the rule owns its toggle
    fun evaluate(screen: Screen): Decision
}
```

Two adapters implement `UiNode`. `A11yUiNode` wraps `AccessibilityNodeInfo` in production, and `JsonUiNode` loads a recorded fixture in tests. `ActionExecutor` (in the service) resolves a `NodeRef` to a live node, walks up to the nearest clickable ancestor (the current `clickNodeOrParent`, written once) and performs the click. It recycles nodes on API levels below 33.

### Rule order

&#91;embedded content: Rule evaluation order · detect, exempt, guard, act\]

Today the Home-tab check comes first and skips detection. In the new order it only chooses between clicking Home and pressing Back.

The Instagram rule follows this order. YouTube is the same shape, with `pivot_shorts_item`, `reel_recycler` / `shorts_container`, `pivot_home_item` and `up_button`.

1. **Detect.** `isShortForm = selected(clips_tab) || visible(clips_viewer_container | clips_viewer_video_layout | clips_viewer_view_pager)`. If false, `Pass`. Most events stop here, after one lookup in the index.
2. **Exempt**, using visible checks only (R5):
   - camera or creation screen is visible
   - story viewer is visible **and** covers more of the window than the Reels player
   - DM thread composer is visible, or the Reel is hosted inside the DM thread container (by view ID, not by text)
3. **Act**, in order of preference:
   1. Clips tab selected and feed tab present: `Click(feed_tab)`.
   2. Player visible and feed tab selected underneath (the R4 case): `Back`. The Home guard is now only used to *avoid clicking Home*. It no longer skips detection.
   3. An in-app back control is found by view ID, or by a `contentDescription` match that works across languages: `Click(back)`.
   4. Otherwise `Back`.
4. **Cool down.** A per-rule cooldown of 1 second, held in the engine rather than the rule, so rules stay stateless. If the same screen signature is acted on 3 times within 10 seconds, the engine backs off for 30 seconds and logs `LoopDetected`. That stops a global Back from repeatedly backing the user out of the app.

### Localisation (R6)

Prefer view IDs throughout. Where text is unavoidable, read Instagram's own localised strings: `packageManager.getResourcesForApplication(pkg).getIdentifier("back", "string", pkg)`. Cache the result per app version, and fall back to an English list. Under the new rule order, text is only used in step 3.3, and failing there degrades gracefully to `Back`.

### Rule definitions as data

View IDs live in one `RuleSpec` object per app, with a `minVersionCode` and `maxVersionCode`. When Instagram renames an ID, the fix is a one-line spec change plus a new fixture, and the service logs the target app's `versionCode` with every decision to make diagnosis easier.

## D5. Accessibility event pipeline performance

**Fixes R21 and R24.** Handle at most one evaluation per target window every 150 ms, build one indexed snapshot per evaluation, and keep the per-event work small. Target: under 2 ms median and under 8 ms p95 for `onAccessibilityEvent` on a Pixel 6a, measured with Perfetto before and after.

### Configuration changes

| Setting | Today | Proposed | Why |
| --- | --- | --- | --- |
| `accessibilityEventTypes` | scrolled, contentChanged, stateChanged, selected, clicked | stateChanged, contentChanged, selected | Scroll and click add volume, not information. Opening the player always raises a state or content change. |
| `packageNames` | (all apps) | the monitored packages | The system filters events before IPC, so other apps cost nothing. Updated at runtime with `setServiceInfo` when the monitored set changes. |
| `notificationTimeout` | 50 ms | 100 ms | The system coalesces events, and the engine also throttles (below). |
| `accessibilityFlags` | + `flagIncludeNotImportantViews` | remove it | Smaller trees. The IDs used are all on important views. Verify with fixtures. |
| `canRequestFilterKeyEvents`, `canPerformGestures` | true | removed | Unused (R28). |

### Event handling

1. **Cheap gate on the main thread.** Ignore the event unless the package is in the hot set, the shield is on and the screen is interactive. These are already volatile fields.
2. **Coalesce.** Record `(pkg, windowId)` as dirty and post one evaluation to a single-thread `HandlerThread` ("a11y-eval") after 150 ms. `TYPE_WINDOW_STATE_CHANGED` evaluates immediately, so tab switches are handled at once.
3. **Snapshot once.** On the eval thread, `getRootInActiveWindow()`, then traverse **once**, with a depth limit of 40 and a node cap of 2,500, to build `Screen.byId` for only the IDs the rule's spec mentions. Every rule check then reads the map, so the 13 binder lookups become one traversal.
4. **Early exit.** The detection step (D4 step 1) reads one or two map entries. On roughly 95% of events, the feed and DMs, it returns `Pass` without touching the rest.
5. **Act on the main thread.** Post the `Decision` back. `ActionExecutor` re-resolves the target node, because the live tree may have changed, and performs the action.
6. **Recycle** every node obtained, on API levels below 33, through a `use {}` extension on the adapter.

### Budget guardrails

- A debug-only `TraceSection("a11y.eval")` around evaluation, plus a counter for events received, evaluated and acted on, shown on a hidden diagnostics screen.
- If an evaluation takes longer than 50 ms three times in a row, the engine doubles the coalescing window, up to 600 ms, and logs `SlowTree`.

## D6. Break policy, bypass friction and persistence

**Fixes R9–R11.** Breaks become a persisted, rationed resource with friction that grows with use. A break always covers one app, unless the user explicitly pauses everything from the dashboard, which costs a cooldown.

### Policy

| Rule | Default | Configurable |
| --- | --- | --- |
| Emergency breaks per app per day | 2 | 0–5 (Strict mode locks it at 0) |
| Break length | 5 min | 3 / 5 / 10 |
| Wait before a break starts | 10 s for the first, then +20 s for each later break that day | No |
| Typed intention before a break | Required from the second break | No |
| Global pause (dashboard) | 10 per day, 3 / 5 / 10 min each, after a 10 s breathe and a typed intention. *(Revised from "once per day, then a 60-min cooldown".)* | Length only |
| Extra global pauses | Settings → "Add 5 pauses for today", behind a 60 s wait; repeatable, today only, reset at midnight; refused under Strict Lockdown | No |
| Turning off master shield | 10 s wait; refused under Strict Lockdown; re-enables itself after 60 min | No |

### Model and persistence

```kotlin
data class BreakState(
    val active: ActiveBreak?,                 // pkg (null = global), startedAt, endsAt, intention
    val usedToday: Map<String, Int>,          // pkg -> count, plus "*" for global
    val day: LocalDate                        // rollover key (see D7)
)
```

- Stored in DataStore as a small proto-like JSON string under `KEY_BREAK_STATE`, written in the same transaction that checks the allowance. That prevents double-spending from two quick taps.
- `BreakManager` becomes a domain class taking `Clock`, `SettingsRepository` and `CoroutineScope`. Expiry is a coroutine `delay` until `endsAt`, as in D3. On process restart, it reads `active`, and either resumes the timer or clears an expired break.
- `isBreakActiveFor(pkg)` checks `active.pkg == pkg || active.pkg == null`, so a global pause stays explicit and visible.
- When a break ends, `ForegroundSessionTracker.recheck(pkg)` runs, so the block reappears at once if the user is still in the app.

### UX

- The block screen shows "2 of 2 breaks left today" and starts a visible countdown before enabling the button. When no breaks are left, the button is replaced with "Come back tomorrow" and "Open Doomscroll".
- A low-importance ongoing notification during a break (using the existing channel) shows the time left. Tapping it ends the break early. That gives `POST_NOTIFICATIONS` a real purpose (R28).
- The unused `MindfulBreakDialog` is reused as the intention prompt, and the unused `ShowBreakDialog` intent is either wired to it or deleted.

## D7. Metrics and daily rollover

**Fixes R8 and R14.** Count blocked attempts per rule per local day, and roll the day over inside the same write that increments. A count can then never land on the wrong day, wherever the increment comes from.

### Storage

A small Room table is preferred over more DataStore keys, because D8's weekly chart needs history:

```kotlin
@Entity(primaryKeys = ["day", "ruleId"])
data class DailyBlockCount(val day: LocalDate, val ruleId: String, val count: Int, val minutesSaved: Int)

@Dao interface MetricsDao {
    @Query("INSERT INTO DailyBlockCount(day, ruleId, count, minutesSaved) VALUES(:d, :r, 1, :m) " +
           "ON CONFLICT(day, ruleId) DO UPDATE SET count = count + 1, minutesSaved = minutesSaved + :m")
    suspend fun increment(d: LocalDate, r: String, m: Int)
    @Query("SELECT * FROM DailyBlockCount WHERE day >= :from") fun since(from: LocalDate): Flow<List<DailyBlockCount>>
}
```

The `day` is computed at increment time as `LocalDate.now(clock)`, so no separate reset step is needed. "Today" in the UI is a query for `day = today`, which re-runs at midnight through a `tickerFlow` that emits on day change. Daily usage totals from D2 are also written to a `DailyUsage(day, pkg, minutes)` table once a day, plus on each dashboard refresh, so the weekly chart has real history.

If Room is judged too heavy, the minimum fix is to store `KEY_STATS_EPOCH_DAY` and do the comparison and reset inside `incrementReelsBlocked`'s `edit {}`.

### What counts as a block

- One count for each **distinct entry** into a short-form screen. The engine increments when a rule first acts on a new screen signature, not on retries within the 10-second loop window from D4.
- Attributed to the rule that acted (`instagram.reels` or `youtube.shorts`), and to the package for limit blocks (`limit.<pkg>`).

### "Time saved"

This is an estimate and is labelled as one. Each intercepted entry saves a configurable *average session* (default 3 minutes, editable in Settings, with an "i" tooltip explaining the assumption). Limit blocks don't add time saved. The previous hard-coded 45 seconds per Reel is removed.

## D8. UI fixes

**Fixes R7, R12, R13, R15, R19, R22 and R23.** Every number on screen comes from a repository, every visible control does something, and platform checks move out of composition.

### Ticking countdown (R7)

The ViewModel exposes `remaining: StateFlow<Duration?>`, built by `breakState.flatMapLatest { b -> if (b.active == null) flowOf(null) else tickerFlow(1.seconds).map { b.active.endsAt - clock.now() } }`. The UI shows `m:ss`. `BreakSession.remainingSeconds` (a getter reading the wall clock) is deleted.

### Slider writes (R22)

`AppQuotaCard` holds `var draft by remember(limit) { mutableIntStateOf(limit) }`. `onValueChange` updates only `draft`, and `onValueChangeFinished` calls `viewModel.updateLimit(pkg, draft)`. The range comes from `LimitPolicy` (D1). Presets write immediately.

### Permissions (R19)

- A `PermissionChecker` interface in the domain layer, implemented by today's `PermissionHelper`, is injected into a new `SettingsViewModel` and into `DashboardViewModel`. Neither holds a `Context` any more.
- Permissions refresh on `Lifecycle.Event.ON_RESUME` through `LifecycleResumeEffect` in the root composable, which sends a `RefreshPermissions` intent to the active screen's ViewModel. `PermissionsAssistantScreen` no longer calls system services during composition.
- Before the Accessibility step, a full-screen **disclosure** (D10) is shown and must be accepted before `getAccessibilitySettingsIntent()` is launched.

### Remove fake data (R13)

| Today | Replacement |
| --- | --- |
| Hard-coded weekly chart (Mon–Sun) | The last 7 `DailyUsage` rows (D7), day labels from `LocalDate`, and "vs average" computed from those rows. Days before install show as empty bars. |
| `"41m"` fallback | An empty state: "No usage yet today", or "Grant usage access" when D2 returns `null`. |
| Placeholder Instagram/YouTube/Reddit cards when the list is empty | A loading skeleton while `appLimits` hasn't emitted (`UiState.Loading`), then the real list. |
| "Sunday (Today)" | `today.dayOfWeek.getDisplayName(FULL, locale)`. |

### Wire up or hide unfinished settings (R12)

| Control | Decision |
| --- | --- |
| Per-app block toggle | **Wire it.** A switch on each `AppQuotaCard` calls `toggleAppBlock`. D3 reschedules on change. |
| Reels and Shorts toggles on the Limits screen | **Wire them** to the existing ViewModel functions, or remove them from the Limits screen if the Dashboard is their only home. |
| Strict Lockdown Mode | **Wire it to D6.** When on: no emergency breaks, a global pause takes 60 s of friction, and turning off master shield needs a 24-hour delay (scheduled, cancellable). Stored in `Settings`. |
| Downtime | **Hide it** until a later milestone, and delete the unused keys. If it's kept, it becomes a `LimitDecider` input (`isInDowntime(now)`), not a separate path. |
| Unused components (`AppLimitRow`, `StatCard`, `PermissionBanner`) | Delete. `MindfulBreakDialog` is reused (D6). |

### Shadow drawing (R23)

Rewrite `neoExtruded` with `drawWithCache`. The two `Paint` objects and their `BlurMaskFilter` are created once for each size and elevation, and `onDrawBehind` only draws. The animated tab pill uses a pre-rendered `ImageBitmap` shadow, or `Modifier.shadow` with a tinted `spotColor` on API 28 and above. Check with the Layout Inspector's recomposition counts and a GPU profile on a low-end device.

## D9. Dependency injection and module structure

**Fixes R18 and R20.** Adopt Hilt, and split the code into Gradle modules so that the domain layer *cannot* import Android. The compiler then enforces the layering the README describes.

### Modules

| Module | Type | Contains | May depend on |
| --- | --- | --- | --- |
| `:core:domain` | Kotlin JVM library | `UiNode`, `Screen`, `InterceptionRule` and the rules, `RuleSpec`, `LimitDecider`, `ForegroundCalculator`, `BreakPolicy`, the `Settings` model, and interfaces (`UsageSource`, `Enforcer`, `PermissionChecker`, `SettingsRepository`, `MetricsRepository`) | kotlinx-coroutines-core, kotlinx-datetime |
| `:core:data` | Android library | DataStore plus migrations, Room (metrics and usage history), `AndroidUsageSource`, repository implementations | `:core:domain` |
| `:service` | Android library | `DoomscrollAccessibilityService`, `A11yUiNode`, `ActionExecutor`, `ForegroundSessionTracker` wiring, `AndroidEnforcer` | `:core:domain`, `:core:data` |
| `:feature:dashboard`, `:feature:limits`, `:feature:settings`, `:feature:block` | Android library (Compose) | Screens, ViewModels, previews | `:core:domain`, `:core:ui` |
| `:core:ui` | Android library | Theme, neumorphic modifiers, shared components | none |
| `:app` | Application | `@HiltAndroidApp`, navigation, manifest | all of the above |
| `:core:testing` | Kotlin JVM library | `JsonUiNode`, fixture loader, fakes (`FakeClock`, `FakeUsageSource`) | `:core:domain` |

If modules feel heavy for the team size, keep one `:app` module but add a Konsist or ArchUnit test that fails if anything under `domain/` imports `android.*`. That gives the same guarantee for less effort.

### Hilt wiring

- `@Singleton` providers for `DataStore`, `Clock` (`Clock.systemDefaultZone()`), `@ApplicationScope CoroutineScope` (`SupervisorJob() + Dispatchers.Default`), and the repositories.
- The accessibility service uses `@AndroidEntryPoint` (supported for `Service` subclasses). Hilt injects the engine, tracker, break manager and executor, replacing `applicationContext as DoomscrollApp`.
- ViewModels use `@HiltViewModel` with `hiltViewModel()` in Compose. The two hand-written `Factory` classes are deleted.
- `InterceptionEngine` receives `Set<InterceptionRule>` through `@IntoSet` multibindings. Adding a platform means one new rule class and one binding, with no edit to the service (R17).

### Navigation

Replace the index-based `selectedTabIndex` with `navigation-compose` (already a dependency, but unused), using type-safe routes. That gives back-stack handling, deep links (`doomscroll://limits/{pkg}` from the block screen) and state saved across process death.

## D10. Manifest, Play policy and repo hygiene

**Fixes R26–R32.** The repo steps must happen before `git init`. The rest ships with M4.

### Repo safety (M0)

- [ ] Move `app/release.keystore` to `~/.android/keys/doomscroll-release.jks`. Read its path and passwords from `~/.gradle/gradle.properties` (`DOOMSCROLL_STORE_FILE`, `DOOMSCROLL_STORE_PASSWORD`, `DOOMSCROLL_KEY_ALIAS`, `DOOMSCROLL_KEY_PASSWORD`) through a `signingConfigs.release` block that's skipped when they're absent.
- [ ] If the keystore was ever copied elsewhere or shared, generate a new one now, before the first Play upload. After upload, enrol in Play App Signing so the upload key can be rotated.
- [ ] Add a `.gitignore` covering `*.jks`, `*.keystore`, `local.properties`, `.gradle/`, `build/`, `*.apk`, `*.aab`, `.rea/` and `.idea/`.
- [ ] Move third-party APKs, tool caches and notes out of the project directory, to a private folder.
- [ ] Rewrite `accessibility_service_description` and the README in your own words, describing Doomscroll's behaviour rather than mirroring the analysed app.

### Manifest and accessibility config

- Remove `SYSTEM_ALERT_WINDOW` and `FOREGROUND_SERVICE`, and `canRequestFilterKeyEvents`, `canPerformGestures` and `flagIncludeNotImportantViews` from the config (see D5).
- Keep `POST_NOTIFICATIONS` for D6's break notification, and request it at runtime on API 33 and above when the first break starts.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Play only allows the direct request intent for a narrow set of use cases. Switch to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (the list screen) and explain why. Because the accessibility service is a bound service, this is usually optional.
- Set `android:enableOnBackInvokedCallback="true"`, and replace `onBackPressed` with an `OnBackPressedCallback` that sends the user home.

### Play accessibility disclosure

Show a full-screen disclosure before sending the user to Accessibility settings, with:

1. what is read: on-screen content in Instagram and YouTube only
2. why: to detect Reels and Shorts screens
3. what is done: automated tap on Home or Back, and nothing else
4. that nothing leaves the device
5. Accept and Decline buttons

Save the acceptance with a timestamp, and match it in the Play Console declaration and the store listing.

### Backups

Include DataStore and Room in `data_extraction_rules.xml` and `backup_rules.xml` (`<include domain="file" path="datastore/"/>`, `<include domain="database" path="."/>`). Exclude the break state (`KEY_BREAK_STATE`), so a restore can't grant a fresh allowance.

### Release build and CI

- `isMinifyEnabled = true` and `isShrinkResources = true`. Keep rules aren't needed for the service, because the manifest references it, but add `-keep` for Room entities if KSP doesn't generate them.
- Add a GitHub Actions workflow with `./gradlew lint testDebugUnitTest assembleRelease` on every push. Fixture tests (see Testing strategy) run in the same job.
- Bump `versionCode` from CI (run number), and keep `versionName` manual.
- Small items: align the README's "OLED dark" wording with the actual light theme (or add a dark scheme), delete `isServiceRunning`, simplify the `PermissionHelper` service check, and move toast strings to resources.

## Testing strategy

The centrepiece is a **fixture-replay suite**. Real Instagram and YouTube screens are recorded as JSON once, then every rule runs against them in milliseconds on the JVM. Everything else is ordinary unit tests over the pure domain classes.

### Fixture capture

1. A debug-only "Record screen" action, either a long-press on the accessibility button or a hidden menu, serialises the active window's tree to JSON. Each node records `viewId`, `text`, `contentDescription`, `className`, `isVisible`, `isSelected`, `isClickable`, bounds and children. Files are written to `Download/doomscroll-fixtures/{pkg}-{versionCode}-{label}.json`.
2. A script strips any text that isn't a UI label (usernames, captions, DM content) before the file is committed. This protects privacy. The fixture keeps only structure, plus a small allow-list of label strings.
3. Fixtures are committed under `core/testing/src/main/resources/fixtures/{app}/{versionCode}/`.

### Required fixture matrix (per app version)

| Screen | Instagram expectation | YouTube expectation |
| --- | --- | --- |
| Home feed, no video | Pass | Pass |
| Reels/Shorts tab | Click Home | Click Home |
| Reel/Short opened from home feed | Back | Back |
| Opened from Explore/Search | Click in-app back | Click up button |
| Opened from a profile grid | Click in-app back | Click up button |
| Shared Reel inside a DM thread | Pass | n/a |
| Story viewer (with a Reels player behind it in the tree) | Pass | n/a |
| Camera / create | Pass | Pass (Shorts camera) |
| Comments sheet over a Reel | Act (no "Reply privately" exemption) | Act |
| Non-English UI (one locale) | Same as English | Same as English |

### Test layers

| Layer | Tooling | Covers | Approximate count |
| --- | --- | --- | --- |
| Rule replay | JUnit 5 parameterised over the fixture folder | D4 rules against the matrix above | 20 per app version |
| Pure domain | JUnit, kotlinx-coroutines-test, `FakeClock` | `ForegroundCalculator` (D2), `LimitDecider` and the tracker (D3), `BreakPolicy` (D6), rollover (D7), migration (D1) | about 60 |
| ViewModels | Turbine + fakes | State mapping, countdown ticker, slider save, permission refresh | about 25 |
| Data | Robolectric (DataStore) + in-memory Room | Migrations, transactions, `toSettings()` | about 15 |
| Device smoke | Manual checklist per release, 2 devices (Pixel, Samsung) | Real Instagram and YouTube builds; open each fixture screen and confirm the behaviour | 10 checks |

### Catching upstream changes

A weekly manual run of the smoke checklist on the latest Play builds of Instagram and YouTube. When a check fails: record a new fixture, update `RuleSpec`, and commit both together. The fixture folder is versioned by `versionCode`, so old versions stay covered.

## Rollout plan and milestones

The work ships in about 3 weeks with two engineers, or 4 with one. Each milestone ends at a gate that must pass before the next one merges.

&#91;embedded content: Rollout · five milestones, three gates\]

Each gate is a checkable condition: no secret or third-party binary in git; the fixture suite green plus an in-app limit block seen on a device; and no hard-coded data on screen plus a Perfetto p95 under 8 ms for event handling.

### Release safety

- Ship M1 and M2 behind a debug-menu switch (`engine = legacy | v2`) for one week of personal use, comparing decisions in logs, then remove the legacy path.
- D1's migration is one-way. Test it against a copy of a real `doomscroll_prefs.preferences_pb` before release.
- Each milestone is its own PR with tests, so any one can be reverted without the others.

## Risks and open questions

### Risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Instagram or YouTube rename view IDs | High (several times a year) | Blocking silently stops | `RuleSpec` per version, fixture suite, weekly smoke run, and a logged `versionCode` on every decision |
| OEMs kill the accessibility service (Xiaomi, Oppo, some Samsung builds) | Medium | No enforcement until re-enabled | A watchdog: the dashboard shows "Protection off" prominently, plus guidance links per OEM (dontkillmyapp.com) |
| `queryEvents` behaves differently on some OEMs | Low–medium | Usage off by minutes | Cross-check with `queryAndAggregateUsageStats` in debug builds, and log differences over 2 minutes |
| Play rejects the accessibility use | Medium | No Play distribution | Disclosure plus declaration (D10). Narrow `packageNames`. Distribute through GitHub releases or F-Droid as a fallback. |
| Stricter break policy frustrates users and they uninstall | Medium | Churn | Defaults are moderate (2 breaks a day), Strict mode is opt-in, and copy explains the reason |
| Moving to Hilt and modules slows M4 | Low | Delay | Optional. The single-module ArchUnit route (D9) gives the layering guarantee without the move |

### Open questions

- [ ] **Q1.** Users already affected by R1 have 60 stored and can't be told apart. Show a one-time "Review your limits" prompt, or silently reset to the per-app defaults?
- [ ] **Q2.** Default emergency breaks per app per day: 2, or 1?
- [ ] **Q3.** Room for metrics and usage history, or the minimal DataStore epoch-day fix and a dropped weekly chart?
- [ ] **Q4.** Is Play Store distribution a goal? It decides whether the D10 disclosure work is on the critical path.
- [ ] **Q5.** Should a dashboard global pause exist at all, or should every break be per app?
