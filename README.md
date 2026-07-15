<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

<h1 align="center">KINETICKK</h1>

<p align="center">
  <strong>Your movement is the weapon. Your cursor is the threat.</strong>
</p>

<p align="center">
  A cross-platform physics-action roguelite powered by one Kotlin Multiplatform simulation.
</p>

<p align="center">
  <img alt="Kotlin 2.3.20" src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose Multiplatform 1.11.0" src="https://img.shields.io/badge/Compose_Multiplatform-1.11.0-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Desktop and WebAssembly" src="https://img.shields.io/badge/targets-Desktop_%2B_WebAssembly-42F5E9">
  <a href="LICENSE"><img alt="GNU GPL version 3 or later" src="https://img.shields.io/badge/license-GPLv3%2B-FF426D"></a>
</p>

<p align="center">
  <a href="#development">Development</a> ·
  <a href="#how-to-play">How to play</a> ·
  <a href="#systems">Systems</a> ·
  <a href="#contributing">Contributing</a> ·
  <a href="docs/ARCHITECTURE.md">Architecture</a> ·
  <a href="docs/LEGAL.md">Legal</a>
</p>

![KINETICKK start screen](docs/assets/kinetickk.png)

> [!IMPORTANT]
> KINETICKK is open-source software under the
> [GNU GPL version 3 or later](LICENSE). You may study, build, run, modify, and
> redistribute it. A distributed fork must keep the copyright and license
> notices, identify its changes, provide the complete corresponding source, and
> remain under the GPL. The KINETICKK name and branding are separate; see the
> [trademark policy](TRADEMARKS.md).

The cursor or touch point is both a magnetic target and a lethal singularity. Pull it away from the Core to build speed, turn that momentum into impact damage, and never let the Core touch the singularity.

The same shared engine, renderer, content catalog, progression system, and tests run across desktop (macOS, Windows, and Linux) and modern browsers through WebAssembly.

The repository is published as a working learning example for Kotlin
Multiplatform, Compose Canvas rendering, deterministic simulation, progression
systems, and cross-platform persistence. You can inspect the design, build the
whole game locally, experiment with it, and contribute changes under the GPL.

## At a glance

| 400 items | 12 weapons | 40 Relics | 9 enemy archetypes | 92 desktop tests |
|:---:|:---:|:---:|:---:|:---:|
| Deterministic catalog | Movement-reactive | Six aspects | Architect included | Seeded simulation |

## How to play

Magnetic Polarity saturates when the target stays far away in one direction. A saturated tether stops adding thrust: turn decisively or bring the target inward to recover before enemies intercept your line.

| Input | Action |
|---|---|
| Mouse / touch drag | Move the singularity and attract the Core |
| `Space` / **Dash** | Kinetic Dash and phase through bullets |
| `Shift` / right mouse / **Brake** | Gravity Brake |
| `P` / `Esc` | Pause or return |
| `1`–`4` | Select an item, weapon, or Relic option |
| `Q` | Reroll an item or weapon choice |
| `L` / `A` / `B` / `C` / `S` | Lab, Armory, Rebirth, Codex, Settings |
| `M` | Toggle sound and music |
| `R` | Restart after a completed run |

Defeat **The Architect** on the current Rebirth tier to unlock the next one. Rebirth starts a fresh run build with a stronger threat profile while preserving permanent progression, unlocks, Codex discovery, and settings.

## Development

Requirements: JDK 17 or newer. The Gradle wrapper downloads the matching Gradle distribution automatically.

```bash
git clone https://github.com/4wl2d/KINETICKK.git
cd KINETICKK
./gradlew run
```

On Windows, use `gradlew.bat run`.

### Browser development

```bash
./gradlew wasmJsBrowserDevelopmentRun
```

Open the local URL printed by Gradle. A production WebAssembly bundle can be built with:

```bash
./gradlew wasmJsBrowserDistribution
```

The optimized bundle is written to `build/dist/wasmJs/productionExecutable`.

### Verification and packaging

| Goal | Command |
|---|---|
| Run desktop tests | `./gradlew desktopTest` |
| Build the production web bundle | `./gradlew wasmJsBrowserDistribution` |
| List every available task | `./gradlew tasks` |

## Systems

- **Kinetic combat:** fixed-step simulation at 120 Hz, uncapped magnetic acceleration, swept high-speed collisions, mass-based impact damage, recoil, Gravity Brake, and Polarity saturation.
- **Buildcraft:** twelve movement-reactive weapons, forty rankable Relics, four Sovereign Relics, four bound Relic slots, and 400 deterministic items across twenty modifier families.
- **Run progression:** Data leveling, stat evolutions, Elite Keys, two-stage Totems, weapon mastery, combo rewards, velocity tiers, Kinetic Overdrive, and a twenty-minute Architect finale.
- **Persistent progression:** spendable Kinetic Matter, eight Lab upgrades, twelve Armory unlocks, three Core shapes, Codex discovery, and replayable Rebirth threat tiers.
- **Presentation:** infinite procedural grid, camera tracking, trails, particles, screen shake, configurable damage numbers, and procedural synth audio on desktop and web.
- **Opposition:** Drifter, Shooter, Charger, Interceptor, Weaver, Warden, Splitter, Elite, and Architect behaviors with projectiles and escalating wave mixes.

## Project layout

| Path | Responsibility |
|---|---|
| `src/commonMain` | Shared simulation, input orchestration, content, audio logic, Canvas UI, and rendering |
| `src/desktopMain` | Desktop window, JVM persistence, and platform tone output |
| `src/wasmJsMain` | Browser entry point, WebAssembly host page, `localStorage`, and web audio |
| `src/commonTest` | Deterministic engine, catalog, progression, Relic, audio, and math tests |

See [Architecture](docs/ARCHITECTURE.md) for the runtime flow, platform boundaries, and testing model.

## Contributing

Bug reports, ideas, tests, documentation, and pull requests are welcome. Read
[CONTRIBUTING.md](CONTRIBUTING.md) before submitting code.

Every commit in a pull request must include a Developer Certificate of Origin
sign-off. Before a copyrightable contribution is merged, its author must also
sign the [KINETICKK Contributor License Agreement](CONTRIBUTOR_LICENSE_AGREEMENT.md).
The CLA keeps the public contribution under GPL and lets the project owner
prepare store builds without taking away the contributor's right to use their
own work.

## Status

KINETICKK is a playable `0.1.0` prototype. APIs, balance, content, and saved-progress formats may change while the game is in active development.

## License

Copyright © 2026 Vladislav Tomilov.

KINETICKK's original code, tests, docs, game content, and project-made assets are
free and open-source under the **GNU General Public License version 3 or later**.
The GPL permits use, modification, redistribution, and commercial distribution.
When you distribute the game or a fork, you must follow the GPL's notice,
source-code, and copyleft terms.

The GPL does not grant rights to present a fork as the official KINETICKK game
or to imply endorsement by Vladislav Tomilov.

- [GNU GPL version 3 or later](LICENSE)
- [Legal overview](docs/LEGAL.md)
- [Copyright and open-source notice](NOTICE)
- [Authorship record](AUTHORS.md)
- [Trademark and brand policy](TRADEMARKS.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [Contribution policy](CONTRIBUTING.md)
- [Contributor License Agreement](CONTRIBUTOR_LICENSE_AGREEMENT.md)
- [Project governance](GOVERNANCE.md)
- [Corresponding source plan](SOURCE.md)
- [Asset provenance](ASSET_PROVENANCE.md)
- [Privacy note for prototype 0.1.0](PRIVACY.md)
