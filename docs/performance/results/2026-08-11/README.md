<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# `main` versus Pokeball full refactor

## Decision

`feature/pokeball-full-refactor@20a3e2f` is **not performance-neutral** relative
to the requested local `main@fedceb8`. The deterministic gameplay comparison
found 15 regressions, 4 stable scenarios, and one semantically incomparable
constructor scenario. The largest hot-path effect is not
the render-model mapper itself; it is repeated full projection around accepted
commands. Published frame work is 2.14× slower and published pointer work is
3.00× slower, with 1.54× and 2.01× the allocations respectively.

The browser's idle presentation remains healthy at 60 FPS with no long tasks or
frames above 33.33 ms, and cold/warm first-contentful paint improved. CDP used
heap after the sample nevertheless increased by 26.56%, a confirmed browser
regression that warrants long-session retention follow-up. Those browser results
do not cancel the deterministic interactive-pipeline regression.

The new Android target is feature-only because the requested `main` has no
Android application host. On the exact final APK, realme, Samsung, and Redmi
each completed three primary and three telemetry forks. Realme and Samsung also
passed 11/11 instrumented contracts each (two Compose UI journeys plus nine
platform/common tests). Samsung and Redmi exposed device-pipeline mode changes;
the compact PERF flow did not systematically worsen them. See the
[physical-device report](android-physical-device.md), which retains full raw
artifact closure and records the unavailable OnePlus plus MIUI-blocked Redmi
debug tests instead of claiming 4/4.

Before treating the refactor as performance-ready, cache or carry the accepted
render snapshot so preflight and Interaction do not rebuild it, then rerun the
standard matrix. The strict-v4 profile and architecture changes may still be
accepted as intentional correctness/security tradeoffs, but their measured cost
and delivery growth should remain explicit.

## Exact attribution

| Role | Reference |
|---|---|
| Requested baseline | local `main@fedceb8e2d9009d805d70249e10c77e424447945` |
| Candidate | `feature/pokeball-full-refactor@20a3e2f78988419f5263247085a9a4b3900f4a6e` |
| Control baseline | `origin/main@a0762dd40df50a06f48f31f2916960ea04992dc2` |
| Merge bases | candidate/local-main: `fedceb8`; candidate/origin-main: `a0762dd` |

Local `main` was four commits behind `origin/main`, so the full requested
comparison uses literal local `main` and a second control isolates that drift.
The targeted origin-main control reproduced the publication regression:

| Scenario | Time vs `origin/main` | Allocation vs `origin/main` |
|---|---:|---:|
| Published 60 Hz frame | +105.90% | +57.88% |
| Published pointer move | +200.84% | +105.95% |
| Published paused frame | +128.36% | +54.04% |

The final controlled gameplay run matched category, description, every metadata
field, and deterministic outcome fingerprint for 19 of 20 shared scenarios.
The one exception is a real behavior difference: the `main` constructor starts
in `MENU`, while the feature constructor starts in `RUNNING`; its timing is
reported only as a diagnostic and receives no verdict. The raw candidate
correctly records `dirty: true`: benchmark adapters/build tasks and the optional
HUD were uncommitted. The compared Nucleus/profile production sources still came
from `20a3e2f`, and Interaction/HUD code is outside those JVM operations. Build,
artifact, and browser measurements came from clean detached trees at the two
exact branch SHAs, so instrumentation was absent from both measured production
distributions. Raw JVM samples and input SHA-256 values are embedded in the
tracked aggregate reports.

## Protocol

- Gameplay and profile standard runs used two A-B-B-A cycles: four fresh JVM
  forks per branch, five warmups and ten 180 ms measurements per scenario.
- JVM environment was identical: macOS arm64, Temurin 21.0.11+10, 1 GiB fixed
  heap, G1, and `AlwaysPreTouch`.
- Gameplay classification requires an absolute median effect of at least 5% and
  a hierarchical 95% bootstrap interval excluding zero (10,000 resamples).
- Browser evidence used pinned Chromium 152.0.7977.8, five isolated fresh
  profiles per branch, 120 warmup and 600 measured rAF intervals at 1280×720.
- Production builds ran a timestamp-validated, non-overlapping A-B-B-A sequence
  as clean offline/no-daemon builds in distinct worktrees. The dependency cache
  was shared but its warmth is not treated as a measured invariant. Artifact
  gzip values are deterministic gzip-9 results.
- Profile branches never shared platform persistence. The benchmark used exact,
  private in-memory providers; browser forks used separate persistent profiles.

## Coverage matrix

| Area | What was measured | Comparison status |
|---|---|---|
| Harness | Empty primitive operation | Stable; -0.15%, interval crosses zero |
| Gameplay lifecycle | State initialization and run start | Run start comparable; constructor diagnostic only (`MENU` versus `RUNNING`) |
| State mechanics | Idle/capacity copy, 60 Hz and 100 ms reduction | Directly comparable |
| Simulation | Collision hit/miss and deterministic 2-second trace | Directly comparable |
| Projection | Idle/capacity render-model mapping | Directly comparable |
| Nucleus | Frame idle/capacity, pointer, viewport, pause | Directly comparable |
| Publication | Accepted frame, pointer, and paused pipelines | Directly comparable |
| Explicit fixed-step bound | Feature 48-step maximum drain | [Feature-only smoke diagnostic](gameplay-feature-only-smoke.json); 11.93 µs and 37.42 KiB/op, old API has no identical contract |
| Profile codec | Default and logical-maximum encode/decode/roundtrip | Same logical fixture, branch-native v3 versus strict v4 |
| Profile boundary/resource | 65,536-byte boundary, malformed/oversize/unknown/noncanonical/UTF-8 rejection, empty/default/maximum reads and write-readback | Current suite smoke-verified; no false same-byte comparison to v3 |
| Runtime telemetry | Disabled/enabled collection, window publication, HUD projection | Current feature standard run |
| Browser | Navigation, paint, ready, resources, rAF, long tasks, heap/CDP, diagnostics | Exact production branches |
| Delivery | Clean build wall time, raw/gzip files, hashes, Wasm sections/functions | Exact production branches |
| Android UI | Safe-area bounds, minimum targets, overlap, semantics, Home→Gameplay→Pause/PERF→Resume→Exit | Four profiles in pure tests; latest debug/test APK 2/4, 11/11 contracts on realme and Samsung |
| Android device runtime | Process-cold startup, full-flow gfxinfo, terminal completion/UI submission/deadline/cadence, PSS, thermal, diagnostics | Feature-only; primary and telemetry isolated, three forks on realme, Samsung, and Redmi |

## Gameplay result

The full table, raw-sample aggregates, p95, CPU, allocation, GC, variability,
environment, and bootstrap intervals are in the
[gameplay report](gameplay-main-vs-feature.md) and
[machine-readable result](gameplay-main-vs-feature.json).

| Scenario | `main` | Feature | Wall delta | Allocation delta | Verdict |
|---|---:|---:|---:|---:|---|
| State initialization | 165 ns | 929 ns | +464.29% | -1.22% | Incomparable (`MENU`→`RUNNING`) |
| Run start | 345 ns | 1.12 µs | +224.21% | -0.67% | Regression |
| Copy idle | 440 ns | 1.21 µs | +176.18% | +0.62% | Regression |
| Copy capacity | 17.96 µs | 18.40 µs | +2.46% | +0.02% | Stable |
| Render projection idle | 1.21 µs | 1.24 µs | +2.66% | -3.47% | Stable |
| Render projection capacity | 11.72 µs | 11.73 µs | +0.07% | -0.34% | Stable |
| Reducer, 60 Hz | 763 ns | 1.64 µs | +115.07% | +8.97% | Regression |
| Reducer, 100 ms | 2.29 µs | 3.74 µs | +63.42% | +49.25% | Regression |
| Collision miss | 94.03 µs | 101.02 µs | +7.43% | +9.74% | Regression |
| Collision hit | 106.66 µs | 114.69 µs | +7.53% | +6.55% | Regression |
| Nucleus frame, idle | 2.01 µs | 3.07 µs | +52.84% | +4.98% | Regression |
| Nucleus frame, capacity | 465.42 µs | 508.12 µs | +9.17% | +8.94% | Regression |
| Nucleus pointer | 1.72 µs | 2.50 µs | +45.06% | -1.14% | Regression |
| Nucleus viewport | 1.72 µs | 2.50 µs | +45.22% | -1.32% | Regression |
| Nucleus paused frame | 1.72 µs | 2.56 µs | +48.56% | -1.27% | Regression |
| Published frame | 2.03 µs | 4.35 µs | +113.73% | +53.80% | Regression |
| Published pointer | 1.71 µs | 5.12 µs | +199.72% | +100.55% | Regression |
| Published paused frame | 1.72 µs | 3.84 µs | +122.89% | +49.23% | Regression |
| Two-second deterministic trace | 426.89 µs | 651.11 µs | +52.52% | +3.22% | Regression |

### Causal evidence

Pure `toRenderModel()` projection is statistically stable, but the feature's
[`GameComponent.preflight`](../../../../ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt)
creates a complete `GameplayNucleus.renderSnapshot(next)` for every accepted
work item. [`GameInteraction.dispatch`](../../../../ball/gameplay/interaction/src/commonMain/kotlin/kinetickk/ball/gameplay/interaction/GameInteraction.kt)
requests another complete snapshot after acceptance, while pointer handling also
queries a snapshot before dispatch. The resulting ordinary frame performs two
full projections and a pointer move performs three. The old branch's query and
post-commit projection cache avoided that repetition.

The projection copies enemies, projectiles, pickups, trail, nodes, orbitals, 400
item stacks, discovery data, and 40 relic ranks. This explains why the published
pipeline diverges sharply while the isolated mapper does not. A secondary
collision regression coincides with construction of a live-enemy-id set on each
fixed step and per-friendly-projectile retention work. Lifecycle/preflight
wrappers account for the initialization, start, copy, and reducer overhead and
should be optimized only after the publication duplication is removed.

## Profile and persistence result

See the [profile report](profile-main-vs-feature.md) and
[aggregate JSON](profile-main-vs-feature.json). These numbers compare equivalent
logical profiles through each branch's native wire contract; they are not a
same-byte serializer microbenchmark. Strict v4 includes validation and canonical
re-encoding by design.

| Logical operation | Payloads (feature/main) | Time delta | Allocation delta |
|---|---:|---:|---:|
| Encode default | 959 / 61 bytes | +260.21% | +26.89% |
| Decode default | 959 / 61 bytes | +612.80% | +197.06% |
| Roundtrip default | 959 / 61 bytes | +506.26% | +128.40% |
| Encode logical maximum | 2,744 / 1,671 bytes | +59.18% | -42.16% |
| Decode logical maximum | 2,744 / 1,671 bytes | +47.87% | +30.17% |
| Roundtrip logical maximum | 2,744 / 1,671 bytes | +51.86% | +2.83% |

The larger default payload magnifies fixed v4 work. Maximum encode is slower but
allocates materially less than v3; decode remains the primary optimization target
if profile loading becomes user-visible.

## Browser, build, and delivery result

Detailed evidence: [browser report](browser-main-vs-feature.md),
[browser JSON](browser-main-vs-feature.json), [build/artifact report](build-artifacts-main-vs-feature.md),
and [build/artifact JSON](build-artifacts-main-vs-feature.json). The raw five-fork
browser documents and artifact inventories are retained beside those reports.

| Result | `main` | Feature | Delta/verdict |
|---|---:|---:|---|
| Idle FPS | 60.002 | 60.002 | Stable |
| Idle 1% low FPS | 59.524 | 59.524 | Stable |
| rAF p95 / p99 | 16.70 / 16.80 ms | 16.70 / 16.80 ms | Stable |
| Long tasks / >33.33 ms frames | 0 / 0 | 0 / 0 | Stable |
| Cold FCP | 472 ms | 408 ms | -13.56%, improvement |
| Cold ready | 1,540 ms | 1,412 ms | -8.31%, improvement |
| Warm FCP | 312 ms | 268 ms | -14.10%, improvement |
| CDP used JS heap after sample | 12.15 MiB | 15.37 MiB | +26.56%, regression |
| Clean production build median | 20.46 s | 27.90 s | +36.40% |
| Entire distribution raw | 11.03 MiB | 11.46 MiB | +3.97% |
| Entire distribution gzip-9 | 3.90 MiB | 4.06 MiB | +4.10% |
| Branch-specific app Wasm raw | 1.58 MiB | 2.02 MiB | +27.47% |
| Branch-specific app Wasm gzip-9 | 0.52 MiB | 0.67 MiB | +30.86% |
| Branch-specific Wasm functions | 5,879 | 8,001 | +36.09% |

The shared Skiko Wasm is byte-identical. The growth is therefore attributed to
the application Wasm, not the rendering runtime. Browser diagnostics recorded no
page errors, HTTP errors, request failures, or console errors on either branch.

## Monitoring overhead added to the feature

The [standard raw result](telemetry-feature.json) measures the isolated observer
primitives and bounds their steady-state cost:

| Operation | Median | Allocation |
|---|---:|---:|
| Disabled guard | 1.02 ns | 0 B |
| Monotonic timer pair | 17.54 ns | 0 B |
| Record frame/dispatch/Canvas | 2.93–2.94 ns each | 0 B |
| Record entity counts | 1.89 ns | 0 B |
| Full 600×3 rolling snapshot | 10.42 µs | 15.87 KiB |
| HUD text projection | 181 ns | 1.38 KiB |

The allocating snapshot and HUD text projection run at most twice per second and
only when `F3` telemetry is enabled. After one-time composition allocates three
600-value buffers and an empty projection, disabled steady-state frames perform
no clock read, sorting, telemetry mutation, or HUD draw. The benchmark does not
isolate the real `drawPerformanceHud` call; enabled Canvas timing includes it on
the running device.

The Android [physical-device result](android-physical-device.md) closes that
specific gap with a PERF-off primary flow and a separate compact-PERF flow. On
Samsung the terminal completion p50/p95 changed by only +0.28/+0.04 ms with an
identical 34.45% aggregate deadline rate; Redmi likewise stayed in its slow mode
with -0.04/+0.29 ms and an identical 100% rate. The ordered three-fork evidence
is diagnostic and is not treated as a randomized equivalence test.

## Limits and next evidence

No benchmark can literally observe every hardware and workload dimension at
once. This suite deliberately separates stable CI evidence from device-specific
diagnostics. The localhost browser trace is an idle presentation check, not an
automated combat journey; GPU/driver frame pacing, desktop compositor behavior,
audio-device latency, thermal throttling, and long-session heap retention remain
machine-specific. Use the `F3` HUD for representative combat and attach JFR/NMT
or browser tracing when a sampled metric regresses. Compose recomposition/slot
churn should be traced specifically across Home↔Gameplay and overlay add/remove,
including idle gameplay under an overlay.

Future changes should first run `smoke`, then use `standard` for the decision.
Retain exact SHA, semantic fingerprints, raw samples, environment, and a written
explanation for any ≥5% sampled regression or delivery-budget increase. The
[operating guide](../../../../tools/performance/README.md) contains the commands
and review contract.
