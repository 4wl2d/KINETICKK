<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Android physical-device benchmark

`android_device_benchmark.py` installs and verifies one APK, then runs the same
touch-gameplay protocol on the four attached lab phones. It is an external adb
harness, so it adds no Gradle module and can be used with any build variant that
exposes the stable KINETICKK semantics contract.

The default matrix is intentionally explicit:

| Serial | Lab device | Expected API / maximum active refresh |
|---|---|---|
| `7DTSXC49PZMRFUPJ` | OnePlus CPH2411 | 35 / 120 Hz |
| `NVYXIJQGTOMVNZZH` | realme RMX2002 | 30 / 90 Hz |
| `R58R603CSEY` | Samsung SM-A325F | 33 / 90 Hz |
| `c91ae939` | Redmi Note 9 Pro | 31 / 60 Hz |

Those values are not trusted as constants. Every run reads the live model, API,
size, density, active refresh rate, navigation mode, rotation policy, low-power
mode, battery, thermal status, temperatures, and physical memory from each
serial. The active refresh rate is read again immediately before and after every
measured flow; a change invalidates that fork because one defensible frame budget
can no longer be assigned to it.

## Run

Build the non-debuggable, shell-profileable benchmark APK, attach and unlock all
four devices, then run:

```bash
./gradlew :app:android:assembleBenchmark
python3 tools/performance/android_device_benchmark.py \
  --apk app/android/build/outputs/apk/benchmark/app-android-benchmark.apk \
  --output build/performance/android-device/current \
  --label feature/pokeball-full-refactor \
  --forks 3
```

With no `--serial`, all four serials above are mandatory. Repeat `--serial` for a
deliberate subset. The default application identity is:

```text
package:   com.vladislavtomilov.kinetickk
component: com.vladislavtomilov.kinetickk/kinetickk.app.shared.MainActivity
```

The normal install command is `adb install -r -t --no-incremental`. It replaces
the app while preserving its data. `--skip-install` is safe only for a deliberate
rerun: the harness streams the installed `base.apk` back through adb and refuses
to proceed unless its SHA-256 is byte-for-byte equal to `--apk`.

## Stable UI flow

[`android_gameplay_flow.json`](android_gameplay_flow.json) has a setup boundary:
opening Home and entering Gameplay happen before `gfxinfo` is reset. The measured
primary portion exercises Dash and Brake, pauses and resumes, then leaves
gameplay running long enough to collect a useful frame window. It deliberately
does not enable the performance HUD, so the primary result measures ordinary
gameplay. [`android_gameplay_telemetry_flow.json`](android_gameplay_telemetry_flow.json)
uses the same schema and interaction sequence but enables the HUD first. Run the
telemetry-on diagnostic as a separate artifact:

```bash
python3 tools/performance/android_device_benchmark.py \
  --apk app/android/build/outputs/apk/benchmark/app-android-benchmark.apk \
  --flow tools/performance/android_gameplay_telemetry_flow.json \
  --output build/performance/android-device/telemetry-current \
  --label feature/pokeball-full-refactor-telemetry \
  --forks 3
```

Every interaction is selector-only. Ordered `anyOf` selectors use the Compose
test tag exported as a UIAutomator resource ID first and the human accessibility
description second. A missing selector, malformed bounds, or more than one match
is a hard fork failure; the runner never guesses a coordinate.

On MIUI 13, `uiautomator dump` can prefix a valid hierarchy with a noisy
`ThemeCompatibilityLoader` stack trace because
`/data/system/theme_config/theme_compatibility.xml` is absent. The harness
extracts and validates the XML envelope instead of treating that OEM warning as
the hierarchy. If the normal dump has no valid XML, it retries once with
`--compressed`; if both fail it stops without falling back to screenshot-derived
or fixed coordinates.

Known consent screens are blockers, not automation targets. In particular, an
`App scan recommended` Play Protect dialog fails immediately without selecting
`Scan app`, expanding/choosing an install override, or uploading the APK. MIUI
privacy/user-agreement screens likewise fail without accepting terms. A person
must resolve either consent decision before rerunning the benchmark.

The resource-ID authority contract is:

```text
kinetickk.home
kinetickk.home.start
kinetickk.gameplay
kinetickk.gameplay.performance
kinetickk.gameplay.dash
kinetickk.gameplay.brake
kinetickk.gameplay.pause
kinetickk.gameplay.resume
kinetickk.gameplay.exit
```

Custom flows use schema version 1 and actions `wait`, `tap`, `longPress`, and
`sleep`. `measurementStartStep` separates navigation/setup from the focused
measurement. Semantic selector fields are exact-match `resourceId`,
`contentDescription`, `text`, `className`, `package`, `clickable`, and `enabled`;
`anyOf` lists ordered alternatives. Unknown keys are rejected.

`reusePreviousHierarchy: true` is an explicit timing contract, not an implicit
cache. It is legal only on `tap` and `longPress`, and only after the same setup or
measurement phase has successfully captured a hierarchy. The selector is
resolved again against that exact XML; a missing or ambiguous node fails without
capturing a replacement or guessing coordinates. Every event records whether
its hierarchy was freshly captured or reused, plus the capture phase, source
step, artifact path, and XML SHA-256. The primary measurement captures a fresh
Gameplay tree for Dash, reuses it for Brake and Pause, captures a fresh paused
tree in the Resume wait, reuses that tree for the Resume tap, then freshly
verifies Gameplay. The telemetry flow captures a fresh Gameplay tree for the
PERF tap and reuses it for Dash, Brake, and Pause; its Resume sequence is the
same. This keeps the active run from expiring while repeated OEM UIAutomator
dumps take several seconds. The tracked reuse indexes are respectively
`[4, 5, 7]` and `[4, 5, 6, 8]`.

Transient hierarchy-command errors are retried until the selector timeout. The
terminal failure retains the last dump error; consent blockers and ambiguous
selectors still fail immediately.

## Evidence and validity

The output directory must be empty. The root contains
`android-device-benchmark.json` (contract:
[`android_device_benchmark.schema.json`](android_device_benchmark.schema.json))
and a Markdown readout. Each device/fork retains:

- exact Git revision, dirty state digest, APK SHA-256, installed APK SHA-256,
  adb version, flow hash, and harness hash;
- raw `am start -W`, `gfxinfo framestats`, `dumpsys meminfo`, live display,
  battery and thermal dumps;
- selector event records and UIAutomator XML;
- final screenshot and timestamp-bounded logcat;
- the full measured flow's complete `gfxinfo` summary: total frames, Android's
  platform janky-frame count/rate, frame percentiles, histograms, and diagnostic
  counters;
- the terminal framestats ring's completion and UI-submission latency
  distributions, one/two-refresh completion overruns, neighboring
  `IntendedVsync` produced cadence, neighboring `DisplayPresentTime` presented
  cadence, cadence missed-vsync count/rate, and API 31+ completion-deadline
  misses;
- startup distribution, heap/PSS, view/activity counts, and fatal/ANR/crash
  diagnostics.

The full-flow summary and terminal ring are separate measurement scopes. The
former accumulates from the `gfxinfo reset` through the whole measured flow; the
latter is Android's bounded terminal steady-state ring. A completion crossing
one refresh budget is named a completion overrun, not jank. On API 31 and newer,
terminal jank is strictly `FrameCompleted - FrameDeadline > 0`; API 30 records
that metric as unavailable/null. A short trailing group of not-yet-presented
modern framestats rows is counted and excluded from presented cadence. Some API
31 OEMs, including the lab Redmi Note 9 Pro, expose `DisplayPresentTime` but set
every valid row to zero. That whole-column unset sentinel is reported as
`available: false`, `availabilityState: column-present-all-unset`, with cadence
values and excluded-tail count null. Column absence is separately recorded as
`column-absent`. Non-zero invalid data, a missing timestamp inside an otherwise
presented sequence, or another malformed partial sequence still fails closed.
Cadence missed-vsync count rounds each neighboring interval to active-refresh
periods and counts periods beyond the first; its rate divides those misses by
observed intervals plus misses.

Portable provenance never embeds a workspace or SDK absolute path. Repository
artifacts use repository-relative logical names; external artifacts use a stable
namespace plus basename. adb/aapt2 provenance records logical SDK location,
basename, version, and executable SHA-256.

The APK manifest is decoded with the newest local SDK `aapt2` when available.
`<profileable android:shell="true">` is therefore a declared APK fact even when
an OEM's `dumpsys package` omits `profileableByShell`. The report keeps
`manifestDeclared`, nullable `dumpsysObserved`, effective value, and source
separately; omission is never converted to `false`, and contradictory explicit
observations fail closed.

Every fork starts after `am force-stop` and verifies the old process is gone.
This is a **process-cold** start, not a reboot/filesystem/ART cold start: app data,
compiled code, OS caches, and filesystem caches remain. The harness never clears
data, uninstalls the package, changes navigation, forces a refresh rate, changes
rotation, overrides battery/thermal state, or writes a global setting. It only
replaces/verifies the APK, wakes the display, force-stops this package, launches
its activity, and injects the declared touch flow.

A run exits non-zero if any required device, environment field, selector, cold
startup timing, frame window, memory field, APK identity, process-health check,
thermal guard, or fatal diagnostic is missing or invalid. Partial raw evidence is
still retained so the failure is auditable.

## Comparison rule

Never compare raw frame counts between phones. Frame counts reflect refresh rate,
measurement duration, scheduler and OEM behavior. For a branch decision, compare
only results from the **same serial** with the same APK variant, UI flow hash,
fork count, active refresh, navigation mode, charging state, acceptable thermal
status, and no fatal diagnostics. Interpret one/two-refresh completion overruns
using that serial's `1000 / activeRefreshRateHz` budget. Keep full-flow Android
platform janky frames, terminal API 31+ `FrameDeadline` misses, and produced /
presented cadence as distinct signals. API 30 terminal deadline jank and absent
`DisplayPresentTime` remain unavailable rather than being estimated.

Run focused parser and contract tests with:

```bash
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/performance \
  python3 tools/performance/test_android_device_benchmark.py
```
