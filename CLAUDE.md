# CLAUDE.md

@AGENTS.md

The shared guide above is the source of truth. Edit `AGENTS.md`, not this file, for anything that
applies to every agent. Claude-specific notes:

- **Working style the owner expects:** act on clear requests without re-asking. Keep a task list for
  multi-step work. Commit in small logical steps as you go on the current branch. Report outcomes
  plainly, including what was *not* verified.
- **Reviews:** when asked to review changes, compare against `docs/architecture-review.md` and
  `docs/remediation-design.md`, verify claims on the device where it matters, and rank findings by
  severity (P0–P3) with file:line references.
- **Device checks:** use adb from Bash (see "Verifying on a device"). Send long waits (break
  expiry, 60 s friction dialogs) to a background command instead of sleeping in the foreground.
- **Before finishing:** `./gradlew testDebugUnitTest` (and `assembleRelease` if DI, Room, R8 rules or
  models changed). Update the product-rules table, README and design doc when behaviour changes.
