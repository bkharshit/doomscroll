# AGENTS.md

Guidance for AI coding agents (and humans) working on **Doomscroll**, an Android self-control app.
It blocks Instagram Reels and YouTube Shorts with an AccessibilityService and enforces daily app time limits.

Background reading: `docs/architecture-review.md` (findings R1–R32) and `docs/remediation-design.md`
(designs D1–D10). Code comments and commits refer to these IDs.

## Build and test

Requires JDK 17. Gradle has no pinned JDK (don't add `org.gradle.java.home` to `gradle.properties`;
it breaks other machines), so set `JAVA_HOME` in your shell:

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home  # or Android Studio's jbr
./gradlew testDebugUnitTest          # all JVM unit tests (fast, run before every commit)
./gradlew assembleDebug              # debug APK
./gradlew assembleRelease            # R8-minified release; check it still builds after DI/Room/model changes
```

- Toolchain: AGP 8.7, Kotlin 2.0.21, KSP, Compose BOM, Hilt 2.52, Room 2.6, DataStore. `minSdk 26`, `targetSdk 35`, Java 17.
- Dependencies go through `gradle/libs.versions.toml`, never hard-coded versions in `app/build.gradle.kts`.
- There are no instrumented tests. Everything that matters is a JVM unit test.

## Architecture

Single `:app` module, package `com.doomscroll.android`:

| Package | Role | May depend on |
| --- | --- | --- |
| `domain/` | All decisions: interception rules, limit and break policy, shield auto-resume. **Pure Kotlin.** | `core/model`, `core/util` (pure parts), repository *interfaces* |
| `core/model`, `core/util` | Models, `LimitPolicy`, `ForegroundCalculator`, small helpers | nothing app-specific |
| `data/` | DataStore (`PreferencesDataStore`, `BreakDataStore`), Room (`data/db`), repository implementations | `domain`, `core` |
| `service/` | `DoomscrollAccessibilityService`, `ForegroundSessionTracker`, `ActionExecutor`, `A11yUiNode`, `UsageLimitEnforcer` | everything below UI |
| `notification/` | Break countdown notification + end-break receiver | `domain` |
| `ui/` | Compose screens + `@HiltViewModel`s (MVI-ish: `UiState` + intents) | `domain`, repository interfaces |
| `di/` | Hilt `AppModule` | everything |

Rules:
- **`domain/` must not import `android.*`, `androidx.*`, `ui`, `service`, `data.local`, `data.db` or `org.json`.**
  `DomainLayeringTest` enforces this. Anything that touches the platform gets an interface in `domain`
  (`Enforcer`, `BreakStateStore`, `PermissionChecker`) and an implementation outside it.
- Rules are pure functions: `InterceptionRule.evaluate(Screen): Decision`. They never click.
  `ActionExecutor` performs `Action`s against the live tree. Add a platform by adding a rule class plus
  its view-ID constants, not by editing the service.
- Wiring is Hilt only (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `di/AppModule`).
  Don't reintroduce service-locator access through the `Application` or hand-written ViewModel factories.
- ViewModels don't hold a `Context`. Use injected interfaces.
- Time comes from an injected `java.time.Clock` in anything with day or deadline logic, so tests can move it.

### How enforcement works (keep these invariants)

- **Foreground app** comes from the *active application window*
  (`windows.firstOrNull { TYPE_APPLICATION && isActive }`), resolved 300 ms after a
  `TYPE_WINDOW_STATE_CHANGED`. Never trust `event.packageName` for this: launcher animations,
  system UI and the keyboard emit window events while the user is still in the app.
- **`ForegroundSessionTracker`** schedules one check for when the budget runs out and loops
  (≥15 s between re-checks) until usage really reaches the limit. It must be re-synced whenever
  the shield, a limit, a per-app toggle or a break changes. The service collects those flows for this.
- **Usage** is computed from raw `UsageEvents` (`ForegroundCalculator`), not `queryUsageStats`
  daily buckets. Finished days are saved to Room once.
- **Block screen**: launch `BlockedAppActivity` with `NEW_TASK | CLEAR_TOP` and *no* preceding
  `GLOBAL_ACTION_HOME`. Pressing Home first makes the system trim the block screen's task instantly.
- **Interception** runs on the `a11y-eval` HandlerThread. The main thread only does a cheap gate
  (volatile reads, no IPC) and coalesces events: 150 ms window, adaptive up to 600 ms via `EvalThrottle`.
  Window changes evaluate immediately. Actions run on the main thread against a freshly fetched tree.
- `Screen` looks view IDs up lazily with `findAccessibilityNodeInfosByViewId` (resolved inside the
  target app's process). Don't switch back to walking the whole tree node by node: it measured
  130+ ms per evaluation on Instagram's feed.
- **`InterceptionEngine` never returns `Pass` while short-form content is detected.** Inside the
  1 s cooldown it returns `RetryAfter` and the service re-evaluates. There is deliberately **no**
  loop backoff and **no** escalation to Home. Both shipped once and became bugs (tapping Reels
  3× disabled the blocker; opening Reels 3× closed Instagram).
- Accessibility `packageNames` is intentionally *not* restricted: the tracker needs window events
  from every app to know when the user leaves a monitored one.
- Recycle every `AccessibilityNodeInfo` you obtain on API < 33 (`NodePool`, `recycle()` helpers).

### Product rules (behaviour users rely on)

| Thing | Rule |
| --- | --- |
| Limits | Range `LimitPolicy.MIN..MAX` (5–300 min, step 5). Code never changes a user's limit; migrations only fill *missing* keys. |
| Emergency break (block screen) | 2 per app per day, 5 min. Wait 10 s, then +20 s per earlier break that day. Typed intention from the 2nd. |
| Conscious Pause (dashboard, all apps) | 10 per day, 3/5/10 min, 10 s breathe + typed intention. Settings adds 5 more for today after a 60 s wait (repeatable). Extras reset at midnight. |
| Shield off | 10 s wait; refused under Strict Lockdown; turns itself back on after 60 min (`ShieldAutoResume`). The accessibility button can only turn it **on**. |
| Strict Lockdown | No breaks, no pauses, no extra pauses, no shield-off. Turning it off takes a 60 s wait. |
| Allowances | Persisted (`BreakDataStore`, separate file, excluded from backup so a restore can't refill them). Reset at local midnight even if the process stays alive. |
| Metrics | Room rows per (local day, rule). "Time saved" is an estimate (3 min per intercept) and must be labelled as one. |
| UI data | Show only real data. No placeholder numbers, fake charts or hard-coded "vs yesterday" text. Use empty/loading states instead. |

Changing any of these is a product decision. Update this table, the README and
`docs/remediation-design.md` (D6) in the same change.

## Code style

- Kotlin official style (`kotlin.code.style=official`), 4-space indent, trailing commas fine.
  Keep files focused; prefer small pure functions in `domain` with KDoc that says *why*.
- Comments explain intent and constraints, not what the next line does. Reference R/D IDs when a
  line exists because of a finding.
- Coroutines: structured scopes only (service scope, `@ApplicationScope`, `viewModelScope`).
  No `GlobalScope`, and no unscoped `CoroutineScope(...)` in `Application.onCreate`.
- Flows: one source of truth in DataStore/Room; derive with `map`/`combine`. Don't call
  `distinctUntilChanged()` on a `StateFlow` (deprecated as an error); `map` first.
- Compose: keep long-lived `pointerInput(Unit)` blocks reading callbacks through `rememberUpdatedState`.
  Expensive drawing goes in `drawWithCache`. Slider-like inputs keep a draft and persist on finish.
- Strings shown to users belong in `res/values/strings.xml` (older screens still inline some; move them when touched).
- SQLite on minSdk 26 is 3.18: no `UPSERT`/`ON CONFLICT DO UPDATE`. Use insert-or-ignore + update in a `@Transaction`.
- Package visibility: reading another app's resources needs a `<queries>` entry in the manifest.

## Testing

- Add or update a JVM test with every behaviour change. Put fakes in `app/src/test/.../TestFakes.kt`
  (`FakeSettingsRepository`, `FakeUsageRepository`, `RecordingEnforcer`, `MutableClock`, `InMemoryBreakStore`).
- Time-based logic: `runTest` + `advanceTimeBy`/`runCurrent`, `backgroundScope` for long-lived
  collectors, and a clock that follows the scheduler where deadlines are compared to wall time.
- Interception rules: build a `Screen` from an in-memory `UiNode` tree (`Screen(pkg, root)`).
  Regression tests for bypasses live in `ReelsBypassTest`. Add a case for every new bypass found.
- `org.json` and Android classes are stubs on the JVM. Keep tested code free of them.

## Verifying on a device

- A Pixel 7 is usually connected over wireless adb (`~/Library/Android/sdk/platform-tools/adb`).
  `adb install -r app/build/outputs/apk/debug/app-debug.apk` keeps app data.
- Check `adb shell dumpsys window | grep isKeyguardShowing` first. Never try to unlock the phone; ask the user.
- Useful logs: `adb logcat -s ForegroundTracker DoomscrollA11y`. Foreground activity:
  `adb shell dumpsys activity activities | grep topResumedActivity`. Use `uiautomator dump` to find tap targets
  (screenshots are scaled; don't guess coordinates from them).
- It's the user's real phone: don't answer system permission prompts, don't change their limits or settings
  beyond what a test needs (and restore them), and say if a test used up an allowance.
  Keep screenshots out of the repo (`pixel7_*.png` is ignored for ad-hoc captures).
- Only test on the device when the change affects runtime behaviour and the user hasn't said not to.

## Git and workflow

- Work on a branch, never directly on `main`. Don't push or open PRs unless asked.
- Small, logical commits as you go, each building and passing `testDebugUnitTest`.
- Conventional Commits with a scope: `feat(pauses): …`, `fix(interception): …`, `perf(service): …`,
  `refactor: …`, `build: …`, `docs: …`, `test: …`, `chore(ui): …`. Subject is imperative, lower case, ≤ ~72 chars.
  The body explains *why* and what changed, and notes how it was verified (e.g. "Verified on a Pixel 7: …").
- Never commit secrets or third-party binaries: `*.keystore`/`*.jks`, `*.apk`, `local.properties`,
  `.rea/`. Signing config is read from `~/.gradle/gradle.properties` and skipped when absent.
- Third-party APKs, tools and research notes stay out of git (review item R27). Don't add any.

## Docs to keep in sync

- `README.md`: features and user-facing rules.
- `docs/remediation-design.md`: designs. Mark deliberate deviations as revised rather than deleting the original intent.
- This file: architecture invariants, product rules, and workflow.
