<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK

**Your movement is the weapon. Your cursor is the threat.**

A physics-action roguelite for Android, desktop (macOS, Windows, Linux), and
WebAssembly, built with Kotlin Multiplatform and Compose.

![KINETICKK start screen](docs/assets/kinetickk.png)

Steer a magnetic singularity to accelerate your Core and turn momentum into
impact damage. Keep the Core away from the singularity, build a loadout, and
survive twelve minutes to face **The Architect**.

## How to play

Pull the cursor away from the Core to gain speed. Staying at the screen edges
drains Magnetic Polarity; return inward, turn, or brake to recover. Fresh
profiles start at 1× speed. Pauses and reward choices stop the run clock.
Space is full of asteroids: brushing one is harmless, hitting one fast hurts,
and ramming one at full speed shatters the Core. Bumpers throw you back, and
the minimap (Settings can hide it) shows what lies ahead. Data pickups latch on
once you pass near them and every level-up pulls in the Data on screen.

| Input | Action |
|---|---|
| Mouse / touch drag | Move the singularity and attract the Core |
| `Space` / **Dash** | Kinetic Dash and phase through bullets |
| `Shift` / right mouse / **Brake** | Gravity Brake |
| `P` / `Esc` | Pause or return |
| `1`–`4` | Select a reward |
| `Q` | Reroll an item or weapon choice |
| `L` / `A` / `B` / `C` or `I` / `S` | Core Matrix, Armory, Rebirth, Codex, Settings |
| `H` | Hangar: choose a character (from Home) |
| `M` | Toggle sound and music |
| `F3` | Toggle and reset the performance HUD |
| `R` | Restart after a completed run |

Twelve characters, chosen in the Hangar, each play a mechanic of their own
(facets, an echo ghost, polarity, a beat, Blink and more), share permanent
upgrades and can use all twenty-four weapons: trails, orbitals, auras,
projectiles, traps, chains, beams and pulses, sixteen open to Matter from the
start and eight that also need a Reborn rank. Weapons link to the build: each
point in the weapon's two tags adds 5 % power (up to 50 %); its signature
character (every character is the signature of two weapons) adds 20 %, starts
the run a weapon level up and brings a twist; a bound Relic carrying the
weapon's key (the heirloom included) adds 20 % once and grows 10 % stronger
itself; one stat scales it, and three mastery forms open at weapon levels 3, 6
and 10.
Builds combine 173 items, twenty-two Relics and optional anomaly trials. Items are
rules, not stat sticks: triggers, conditions and effects over nine archetype
tags and seven elements (Thermal, Cryo, Ion, Gravitic, Entropy, Sonic, Void)
whose statuses Break at a threshold and react with each other: every pair of
elements has its own reaction, 21 in all. Every Relic rewrites a rule and
carries a cost; Relic keys predict harmony and dissonance, and the Codex records
discovered reactions, pairings and known incompatibilities.
Nineteen enemy kinds, from Swarmling packs and Bombers to the Herald mini-boss,
arrive through a spawn director that shapes every run's waves differently; later
Rebirth tiers bring the toughest ones. Spend Kinetic Matter on the 32 nodes of
the Core Matrix and in the Armory between runs. Defeat The Architect to earn the
next Rebirth tier and a Reborn rank while keeping permanent progression. The
Rebirth ladder shows tiers 0 to 10 and what each rank opens: at least four more
items every rank, a weapon at ranks 1 to 7 and 9 (Glacier Bell first, Resonance
Drum last), the Spiral, Pentagon, Crescent and Hexagon at ranks 1, 3, 5 and 7,
Matrix nodes and the enemy kinds that join at that tier. Each tier is much
harder and adds a mutator (packs, hardened Elites, volatile remains, armored
vanguards, a blackout and an enraged Architect among them), and you can pick any
earned tier for the next run, so a hard tier never locks you out. Clearing tier
10 makes you Ascendant.

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
120 fps); Android asks for the panel's highest refresh rate. To lift the desktop
cap, turn V-Sync off with the uncapped mode:

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

[Publish web release](.github/workflows/web-release.yml) builds and tests the
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

Architecture checks require a clean Pokeball checkout at
`b4a8219ecb70ae5e81214edd6b509b61d9db0637`:

```bash
./gradlew verifyArchitecture verifyPokeballArchitecture verifyPokeballConformance -PpokeballSnapshotDir=/path/to/pinned/Pokeball
```

[CI](.github/workflows/ci.yml) defines the complete platform checks.
[Performance tools](tools/performance/README.md) cover benchmark comparisons.
Project-specific architecture inputs live in
[docs/architecture/pokeball](docs/architecture/pokeball/README.md);
agent instructions are in [AGENTS.md](AGENTS.md).

### Crash reports

Desktop reports stay in `~/.kinetickk/crashes/`
(`%USERPROFILE%\.kinetickk\crashes` on Windows). The crash dialog can open
that folder; `bash tools/crashes/latest.sh --path` prints the latest report path.
Reports include logs, runtime details, and diagnostic copies of saves. Nothing
is uploaded. See the [privacy note](docs/project/PRIVACY.md).

## Status

Version **0.4.0** is a playable prototype. Before 1.0.0, only the current
profile schema is supported: incompatible profiles start from defaults, and
older save locations remain untouched. Balance, content, and save formats may
change during development.

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
[GNU GPL version 3 or later](LICENSE). See [NOTICE](NOTICE),
[authors](docs/project/AUTHORS.md),
[third-party notices](docs/project/THIRD_PARTY_NOTICES.md),
[asset provenance](docs/project/ASSET_PROVENANCE.md), and
[corresponding source](docs/project/SOURCE.md).
The KINETICKK name and branding have a separate
[trademark policy](docs/project/TRADEMARKS.md).
