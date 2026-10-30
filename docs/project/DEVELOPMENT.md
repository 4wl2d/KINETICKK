<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Development

Build, verification, release, and contribution notes for KINETICKK.
For the game overview, see [README.md](../../README.md).

## Build and run

Requires JDK 17 or newer; Android builds also need the Android SDK.
The Gradle wrapper downloads Gradle and the project dependencies.
On Windows, replace `./gradlew` with `gradlew.bat`.

```bash
./gradlew :app:desktop:run
./gradlew :app:web:wasmJsBrowserDevelopmentRun
./gradlew :app:android:assembleDebug
```

### Frame rate

The simulation runs at a fixed 120 steps per second in density-independent
units, and frames between steps draw interpolated positions, so the game is the
same at any frame rate and any display density. With V-Sync the desktop frame
rate follows the monitor (a 144 or 240 Hz display already renders above
120 fps); Android uses the refresh rate chosen in Settings, and Auto asks for
the panel's highest. Settings can turn V-Sync off and limit the frame rate; the
uncapped launch mode below turns V-Sync off whatever Settings stores:

```bash
./gradlew :app:desktop:run -Pkinetickk.uncapped=true
KINETICKK_UNCAPPED_FPS=1 ./gradlew :app:desktop:run
```

A packaged desktop build takes `-Dkinetickk.uncapped=true` or the same
environment variable. Below 10 fps a frame advances at most 0.1 s of game time,
so the game slows down instead of skipping physics (a spiral-of-death guard).
`F3` shows the measured frame rate.

The Android debug APK is in `app/android/build/outputs/apk/debug/` and uses a
separate package from the release. Build the production web bundle with
`./gradlew :app:web:wasmJsBrowserDistribution`; its output is
`app/web/build/dist/wasmJs/productionExecutable/`.

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
./gradlew desktopTest
./gradlew :app:android:assembleDebug :app:android:assembleDebugAndroidTest :app:shared:assembleAndroidDeviceTest
CHROME_BIN=/path/to/chrome ./gradlew wasmJsBrowserTest wasmJsBrowserDistribution --max-workers=1
```

Without switches, `desktopTest` runs the screenshot harnesses (`RedesignShots*`
and the before/after captures) as smokes that render every screen state at least
once (the World marker flights only in part) without writing files, renders one
reward card gallery sheet per card kind, and samples the longest allocation and
layout sweeps. `KK_SHOTS=1` renders the full size and language matrix of the
`RedesignShots*` harnesses to PNGs under each module's `build/redesign-shots/`
(or `KK_SHOTS_OUT` where a harness reads it). The card gallery writes only when
`KK_SHOTS_OUT` names a folder, and the before/after captures only into `KK_BA_DIR`.
`KK_FULL_SWEEPS=1` runs every sweep in full.

`./gradlew verifyArchitecture` checks the module dependency graph.

CI splits `desktopTest` into parallel shards. `-Pkinetickk.desktopTest.shard=gates-1`
(through the last `gates-N`) runs one shard of the seeded balance gates, and `rest`
runs every class that no gate shard lists. The lists live in build-logic's
`DesktopTestShard`. Without the property, `desktopTest` runs every class.

[CI](../../.github/workflows/ci.yml) defines the complete platform checks.
[Performance tools](../../tools/performance/README.md) cover benchmark comparisons.
Agent instructions are in [AGENTS.md](../../AGENTS.md).

### Crash reports

Desktop reports stay in `~/.kinetickk/crashes/`
(`%USERPROFILE%\.kinetickk\crashes` on Windows). The crash dialog can open
that folder; `bash tools/crashes/latest.sh --path` prints the latest report path.
Reports include logs, runtime details, and diagnostic copies of saves. Nothing
is uploaded. See the [privacy note](PRIVACY.md).

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
