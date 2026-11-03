<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Development

Build, verification, release, and contribution notes for KINETICKK.
For the game overview, see [README.md](../../README.md).

## Build and run

Requires JDK 17 or newer; Android builds also need the Android SDK.
The Gradle wrapper downloads Gradle and the project dependencies.
On Windows, replace `./gradlew` with `gradlew.bat`.

Desktop release optimization and application packaging need a JDK containing
`jmods`. If the current JDK omits them, the build selects a Java 21 toolchain;
install one or point `-Pkinetickk.packagingJdk=/absolute/path/to/jdk` at a suitable
JDK. CI uses a complete Temurin 17 JDK.

Select the complete build with `-Pkinetickk.variant=debug` or
`-Pkinetickk.variant=release`. The default is Release. The selection applies to
the shared Kotlin modules as well as the platform app, so diagnostic code is
compiled only into Debug. Debug writes to each module's `build-debug/` directory;
Release keeps `build/`. Switching variants needs no edits or `clean`, and cleaning
one variant leaves the other variant's artifacts intact.

Debug:

```bash
./gradlew -Pkinetickk.variant=debug :app:desktop:run
./gradlew -Pkinetickk.variant=debug :app:web:wasmJsBrowserDevelopmentRun
./gradlew -Pkinetickk.variant=debug :app:android:assembleDebug
```

Release:

```bash
./gradlew -Pkinetickk.variant=release :app:desktop:runRelease
./gradlew -Pkinetickk.variant=release :app:web:wasmJsBrowserDistribution
./gradlew -Pkinetickk.variant=release :app:android:assembleRelease
```

The portable Bash wrapper also supports `build`, `run`, `package` and `test` for
each platform:

```bash
bash tools/build/variant.sh debug desktop run
bash tools/build/variant.sh release desktop package
bash tools/build/variant.sh debug android run
bash tools/build/variant.sh release android run
bash tools/build/variant.sh debug web run
bash tools/build/variant.sh release web run
```

Desktop `build` creates a runnable JAR, while `package` includes a compact JDK.
Android `run` builds, signs with the local developer key, installs and launches
the selected variant; set `ANDROID_SERIAL` if several devices are connected.
The local key is for development, not store distribution. Web `run` serves the
selected build locally, and `build` creates a static distribution.

The Android debug APK is in `app/android/build-debug/outputs/apk/debug/` and
uses the `.debug` application ID, so it can coexist with Release. A production
Release APK needs your distribution signing credentials. To install and test the
same release optimization settings locally with the development signing key,
use `-Pkinetickk.variant=release :app:android:installBenchmark`; this existing
non-debuggable, shell-profileable build uses the release application ID.

Packaged macOS variants also have distinct bundle IDs and window titles. Desktop
variants retain the historical shared save location; compare them one at a time
and use test fixtures or Baby mode when progression should stay unchanged.

The production web bundle is in
`app/web/build/dist/wasmJs/productionExecutable/`. Serve that directory over HTTP
to run it, for example:

```bash
uv run python -m http.server 8899 --bind 127.0.0.1 --directory app/web/build/dist/wasmJs/productionExecutable
```

Release uses Android R8 code/resource optimization, desktop ProGuard shrinking,
optimization and obfuscation, and optimized production Wasm with no shipped
source maps. Desktop application packaging includes only the required JDK
modules. Both variants omit unused Compose inspector source information and trace
markers; Debug records its own measured phases and source identity. These choices do not change simulation rules or lower
visual quality; their runtime effect still needs a matched measurement on each
target platform.

Desktop uses [ProGuard 7.10.0](https://www.guardsquare.com/manual/releasenotes),
which supports Kotlin 2.4. The optimization mapping and removed-code report are
retained in `app/desktop/build/compose/release-mapping.txt` and
`release-usage.txt`. Keep the mapping alongside distributed artifacts.

Desktop disables only ProGuard's `method/inlining/unique` pass. With this
Kotlin/Compose graph that pass produces invalid JVM stack-map frames in game and
Canvas code; the failure is related to [CMP-8625](https://youtrack.jetbrains.com/issue/CMP-8625).
Shrinking, obfuscation, short-method and tail inlining, constant folding, and the
JVM's runtime optimizer remain enabled. CI verifies every application class in
the optimized JAR before accepting it; a successful packaging task alone cannot
detect this regression.

macOS `jpackage` rejects a package version whose major component is zero. Its
package metadata therefore uses `(game major + 1).minor.patch`: game `0.6.0`
packages as `1.6.0`. The game version, save format, and other platforms retain
`0.6.0`; this mapping also preserves upgrade order when the game reaches `1.x`.

### Debug diagnostics

Open **DEBUG** (or press `F3`) to inspect frame timing and record a diagnostic
session. Press **Начать запись** before reproducing a slowdown, then **Стоп** and
**Копировать**. **Стоп + копия** performs both steps. Include the report and the
steps that triggered the problem when asking for an investigation. If clipboard
access is unavailable, open **Текст отчёта** and copy the selectable text manually.
The panel and recorder are absent from Release.

**Show FPS** remains available in both Debug and Release. Its lightweight counter
measures gameplay frame callbacks over half-second windows independently of the
recorder. Showing it does not start diagnostic capture; in Debug the desktop and
landscape HUD button can open the detailed panel, while Release displays the value
without a diagnostic action. Portrait uses a small read-only strip below the clock;
pause and reward screens keep a read-only counter above their overlays.

At run startup, reward UI is precomposed and measured in an unplaced temporary
slot, then disposed before gameplay frames and controls start. It rolls no loot
and writes no profile data. This moves one-time UI initialization to startup;
runtime comparisons must account for that cost separately. During a reward deal,
actual text chooses a shared fitting size over bounded frame steps, preserving
scrolling for text that exceeds the minimum size.

Frame intervals and CPU timings for simulation, dispatch and Canvas submission
help distinguish expensive game work from time spent elsewhere. These timings
do not measure GPU execution or prove that a frame was presented on screen.
Treat reported causes as leads to investigate. Keep the same machine, graphics
backend, viewport, frame cap and scenario when comparing results, and compare
Release against Release for gameplay performance decisions.

The live panel updates twice per second over the latest 600 intervals. Recording
retains at most 12,000 frame rows and 2,048 events; older entries are overwritten
and the report includes the overwrite counts. Runtime counters are sampled about
every two seconds where the platform supports them. The export contains timings,
events, context and potential causes in plain text with tab-separated frame rows.
An unknown or uncapped frame target has no target-based jank estimate.

Use **Скрыть** during recording to reduce panel overhead. Copying automatically
stops recording before formatting the report. Reports stay local until you share
them; review their contents before sending them elsewhere.

On desktop, recording suspends the periodic crash-checkpoint disk writes. A
captured exception still writes the current crash context; abrupt native termination
during recording may leave only the checkpoint from before recording began.

The [Android diagnostic journey](../../tools/build/android_debug_diagnostics_flow.json)
checks recording, hidden controls, Dash/Brake, Pause/Resume and copying with the
existing device harness. Select a device explicitly and preserve its app data:

```bash
uv run python tools/performance/android_device_benchmark.py \
  --apk app/android/build-debug/outputs/apk/debug/app-android-debug.apk \
  --package com.vladislavtomilov.kinetickk.debug \
  --component com.vladislavtomilov.kinetickk.debug/kinetickk.app.shared.MainActivity \
  --serial YOUR_SERIAL --forks 1 \
  --flow tools/build/android_debug_diagnostics_flow.json \
  --output build/variant-verification/android-debug-functional
```

This journey enables recording and uses session-only Baby mode to avoid progression;
its timings are functional evidence, not a production performance benchmark.

### Frame rate

The simulation runs at a fixed 120 steps per second in density-independent
units, and frames between steps draw interpolated positions, so the game is the
same at any frame rate and any display density. With V-Sync the desktop frame
rate follows the monitor (a 144 or 240 Hz display already renders above
120 fps); Android uses the refresh rate chosen in Settings, and Auto asks for
the panel's highest. Settings can turn V-Sync off and limit the frame rate; the
uncapped launch mode below turns V-Sync off whatever Settings stores:

```bash
./gradlew :app:desktop:runRelease -Pkinetickk.uncapped=true
KINETICKK_UNCAPPED_FPS=1 ./gradlew :app:desktop:runRelease
```

A packaged desktop build takes `-Dkinetickk.uncapped=true` or the same
environment variable. Below 10 fps a frame advances at most 0.1 s of game time,
so the game slows down instead of skipping physics (a spiral-of-death guard).
The Debug diagnostics panel shows the measured frame rate.

The FPS limit keeps a monotonic schedule: early timer wakes carry their remainder
into the next frame instead of accumulating above the selected rate. It is an
average cap, with short timing variation to avoid missing V-Sync; Unlimited
still imposes no software cap. Long stalls reset the schedule without a burst
of catch-up frames.

### Automatic web releases

[Publish web release](../../.github/workflows/web-release.yml) builds and tests the
latest stable GitHub release, then attaches its browser files and
`kinetickk-web.json` to that release. The game at
[tomilov.tech/game/](https://tomilov.tech/game/) picks up the ready build
automatically, without republishing the portfolio. Prereleases are excluded.
The workflow runs on release publication and edits, checks hourly for missed
updates, and can be started manually with **Run workflow**.

Files are verified before the readiness manifest is uploaded. Published web
builds are never overwritten; use a new release tag to change the game. Tags
must be 1–64 letters, digits, dots, underscores or hyphens, beginning with a
letter or digit. GitHub release immutability must remain off until the build
assets have been attached. Only the publication job needs `contents: write`;
no personal token or Sites credential is needed in GitHub Actions.

The site keeps each session on a fixed release URL under the same origin and
checks for a newer ready release at most once a minute per active server
instance. During a build or GitHub outage it serves the last successful build
available to that instance, with the bundled build as a fallback. Existing
sessions continue on their selected release; reopen the game to update.

### Verification

```bash
./gradlew -Pkinetickk.variant=release desktopTest
./gradlew -Pkinetickk.variant=debug :app:shared:desktopTest
./gradlew -Pkinetickk.variant=debug :app:android:assembleDebug :app:android:assembleDebugAndroidTest :app:shared:assembleAndroidDeviceTest
./gradlew -Pkinetickk.variant=release :app:android:assembleBenchmark
CHROME_BIN=/path/to/chrome ./gradlew -Pkinetickk.variant=release wasmJsBrowserTest wasmJsBrowserDistribution --max-workers=1
```

CI builds both variants and scans the actual JAR/APK/Wasm bytes for diagnostic
implementation markers. To repeat a check locally after building both desktop
JARs:

```bash
uv run python tools/build/verify_variants.py --platform desktop \
  --debug app/desktop/build-debug/compose/jars --release app/desktop/build/compose/jars \
  --output build/variant-verification/desktop.json
```

The same command accepts `--platform android` with two APKs, or `--platform web`
with two distribution directories. Reports include hashes and byte sizes. These
checks prove packaging separation, not gameplay equivalence or faster frame times;
the [performance tools](../../tools/performance/README.md) provide matched runtime
measurements.

To check byte reproducibility, keep a passing report, rebuild the same sources
with the same toolchain and signing configuration, then rerun the scanner with
the same artifact paths and `--reference /path/to/previous-report.json`. A mismatch
fails the check; a build succeeding twice alone is not a byte reproducibility test.

Debug Web uses a full executable link: Kotlin 2.4's incremental Wasm linker can
retain sections that depend on its cache history. Library compilation stays
incremental. Signed Android APKs can differ inside the signing block even when
every ZIP payload and entry metadata matches; retain that comparison separately
instead of calling the complete signed files byte-identical.

CI also loads every optimized application class without running its static
initializers, catching JVM verification and linkage failures before distribution:

```bash
java -cp /path/to/release.jar tools/build/VerifyJvmClasses.java /path/to/release.jar
```

A real game journey is still required: class verification alone cannot detect
initialization failures or incorrect behavior. The current Compose 1.12 Web
resource loader emits one upstream `wasmExports.memory` deprecation warning on
startup; application debug logging is excluded from Release, and unrelated
browser errors are not suppressed.

Without switches, `desktopTest` runs the screenshot harnesses (`RedesignShots*`
and the before/after captures) as smokes that render every screen state at least
once (the World marker flights only in part) without writing files, renders one
reward card gallery sheet per card kind, and samples the longest allocation and
layout sweeps. `KK_SHOTS=1` renders the full size and language matrix of the
`RedesignShots*` harnesses to PNGs under each module's `build/redesign-shots/`
(or `KK_SHOTS_OUT` where a harness reads it). The card gallery writes only when
`KK_SHOTS_OUT` names a folder, and the before/after captures only into `KK_BA_DIR`.
`KK_FULL_SWEEPS=1` runs every sweep in full.

`./gradlew verifyArchitecture` checks the module dependency graph, forbidden
imports and ambient randomness in production nucleus sources, platform-free API
sources, and source structure: file, function and package size ratchets,
renderer and feature import boundaries, package cycles and package/directory
agreement. The rules, limits and baseline policy are in
[ARCHITECTURE.md](ARCHITECTURE.md); `./gradlew updateSourceStructureBaseline`
lowers the ratchet ceilings. The import sensor is not a full purity proof;
qualified calls and effects behind helpers still require review.
See the [Ballarch 0.6.0 evaluation](BALLARCH-0.6.0.md) for scope, measurements,
and the distinction between these checks and Core runtime conformance.

CI splits `desktopTest` into parallel shards. `-Pkinetickk.desktopTest.shard=gates-1`
(through the last `gates-N`) runs one shard of the seeded balance gates, and `rest`
runs every class that no gate shard lists. The lists live in build-logic's
`DesktopTestShard`. Without the property, `desktopTest` runs every class.

[CI](../../.github/workflows/ci.yml) defines the complete platform checks.
[Performance tools](../../tools/performance/README.md) cover benchmark comparisons.
Agent instructions are in [AGENTS.md](../../AGENTS.md).

### Balance sweep

`balanceSweep` plays the reference bot (the input-only NoPoi bot of the gate
harness) over many seeds and prints one row per Rebirth tier and profile: win
share with its binomial standard error, median survival and Architect clear time,
median run and weapon level, mean Legendaries, Epics and Relics, median banked
Matter and the run ends by cause (`WIN`, `TIMEOUT` for a run alive at the
harness's 900 s, or the end message). It is a tool, not a test: no `check`, shard
or CI job runs it. The model it calibrates is in
[progression.md](../balance/progression.md).

```bash
./gradlew -Pkinetickk.variant=release :ball:gameplay:nucleus:balanceSweep
./gradlew -Pkinetickk.variant=release :ball:gameplay:nucleus:balanceSweep \
  -Psweep.tiers=0,2,5,8,10 -Psweep.arms=fresh,half -Psweep.window=16401..16700
./gradlew -Pkinetickk.variant=release :ball:gameplay:nucleus:balanceSweep \
  "-Psweep.cells=fresh:2,half:5,full:10,strong:10" "-Psweep.knobs=damage=1.16;damage=1.18,honing=1.09"
./gradlew -Pkinetickk.variant=release :ball:gameplay:nucleus:balanceSweep \
  -Psweep.mode=campaign -Psweep.window=16401..17000 -Psweep.campaigns=16
```

| Property | Meaning | Default |
|---|---|---|
| `sweep.tiers` | tiers `first..last` or a list | `0..10` |
| `sweep.arms` | `fresh`, `half`, `full`, `strong` (the design targets' profiles) | all |
| `sweep.cells` | explicit `arm:tier` pairs instead of tiers × arms | — |
| `sweep.window` | probe indices; seeds are `i × 7,919 + 13`, never the gate population | `16401..16500` |
| `sweep.threads` | worker threads | every core |
| `sweep.knobs` | content overrides `name=value,…`; `;` separates sets, each printed as its own table: `health`, `damage`, `matter` (per-tier bases), `honing`, `refit`, `refitIntegrity`, `weaponCap`, `costScale`, `bossCapShrink` | none |
| `sweep.out` | file for one tab-separated line per run | none |
| `sweep.mode` | `cells`, or `campaign`: whole profiles from a fresh start that bank Matter, buy the cheapest upgrade they can afford and push their Rebirth rank; prints the runs to a full Matrix with one maxed character and weapon | `cells` |
| `sweep.campaigns` | campaigns in campaign mode | `12` |

Tune only on the calibration windows of the [balance policy](../balance/policy.md)
(16,401..17,000 is the progression window); 100 runs per cell resolve a win share
to about ±0.05. A full table (11 tiers × 4 profiles × 100 runs) takes about
five to ten minutes on 16 threads.

### Startup resources

Before opening the menu, the shared app loads every bundled font and resolves its
families, prepares the fixed sound cues and music notes, and builds retained icon,
world and terminal geometry. The loading screen shows completed stages; an error
keeps the menu closed and offers Retry. Successfully prepared resources survive
both retries and desktop window recreation. The ready typography and its font
resolver stay with the app owner, so changing desktop window settings does not
bring the loading screen back.

The web HTML also shows a lightweight splash while JavaScript/WASM starts. Browser
audio remains suspended until a user gesture. A missing native audio device does
not prevent the game from starting.

This barrier covers bundled resources and reusable render primitives. Seeded world
generation, layout for the actual viewport, changing damage text, runtime/JIT work
and driver/GPU caches still happen during play. The existing reward layout guard
remains in place. Preloading does not promise that every frame is free of work or
that a graphics driver will never compile a shader later.

### Crash reports

Desktop Debug reports stay in `~/.kinetickk/crashes/`
(`%USERPROFILE%\.kinetickk\crashes` on Windows). The crash dialog can open
that folder; `bash tools/crashes/latest.sh --path` prints the latest report path.
Reports include logs, runtime details, and diagnostic copies of saves. Nothing
is uploaded. Release does not install the diagnostic reporter or its periodic
checkpoint worker. See the [privacy note](PRIVACY.md).

## Contributing

Bug reports and focused pull requests are welcome. Describe the change and the
checks you ran; include comparable performance results for hot-path changes.
Follow the existing code and retain regression tests. Identify the source and
license of copied, generated, or adapted material. New project-authored files
use an accurate SPDX copyright line and `GPL-3.0-or-later` license identifier.

Contributions use GPL-3.0-or-later. Contributors keep their copyright and must
have the right to submit the material. No separate CLA or DCO sign-off is required.

## License

Copyright © 2026 Vladislav Tomilov. Licensed under
[GNU GPL version 3 or later](../../LICENSE). See [NOTICE](../../NOTICE),
[authors](AUTHORS.md),
[third-party notices](THIRD_PARTY_NOTICES.md),
[asset provenance](ASSET_PROVENANCE.md), and
[corresponding source](SOURCE.md).
The KINETICKK name and branding have a separate
[trademark policy](TRADEMARKS.md).
