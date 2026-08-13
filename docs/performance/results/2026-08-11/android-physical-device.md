<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Android physical-device validation

## Outcome

The current worktree now has a real Android application, adaptive compact-phone
geometry, Compose semantics/UI tests, a fail-closed physical-device benchmark,
and a separate Perfetto diagnostic. The exact final benchmark APK completed
three primary and three telemetry forks on realme RMX2002, Samsung SM-A325F, and
Redmi Note 9 Pro. The final debug and test APKs completed 11/11 instrumented
tests on realme and Samsung.

Those 11 tests are not all UI tests: two are rendered Compose journeys and nine
are platform/common contracts. The two UI tests verify safe rendered bounds and
the complete Home → Gameplay → Brake/Dash → Pause/PERF → Resume → Exit journey.
The OnePlus CPH2411 disconnected before the final build, while MIUI rejected
installation of the Redmi debug APK with 'INSTALL_FAILED_USER_RESTRICTED'.
Consequently this report claims 3/4 final physical performance coverage and 2/4
final physical instrumentation coverage, not 4/4.

All four exact viewport/density profiles are still covered by deterministic
layout tests in portrait and landscape. No runner accepted consent, uploaded an
APK, cleared application data, uninstalled a package, or changed a global
setting.

Local main at fedceb8 has no Android application host, so these measurements are
a feature-only qualification of the current MR, not a fabricated Android A/B
against main.

## Exact artifacts and protocol

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Benchmark APK, stable package | 7,175,221 | ae18ba15339c9276ccbefddb0bcdce86c39f6df9eb761b73dd76fd484c394c25 |
| Debug target APK, .debug package | 10,300,607 | db3fb33589ac90dced7560069b15a6e90b71fa111b05ec97a9e500d7904555d3 |
| Android test APK, .debug.test package | 1,206,993 | 6e47fd308a04d8b5c1e1e6d9b1751f314a8ac179606e72293f149fad125e760a |

The source is feature/pokeball-full-refactor at
20a3e2f78988419f5263247085a9a4b3900f4a6e with a dirty worktree. The
benchmark APK declares package com.vladislavtomilov.kinetickk, min SDK 26,
target SDK 36, non-debuggable, and profileable by shell.

- Primary flow: kinetickk-gameplay-primary-touch-v1, PERF disabled.
- Telemetry flow: kinetickk-gameplay-telemetry-touch-v1, compact PERF enabled.
- Sampling: three process-cold forks per measured device and at least 30 valid
  terminal frames per fork.
- Every fork verifies installed base.apk bytes, battery, thermal state, active
  refresh, selectors, cold launch, frames, diagnostics, and artifacts.

The realme/Samsung runs used worktree-status fingerprint
90a125ce8874ccad7f900de8117d9ae0ad915b8dd0c688a70fac28a10ebf108a
and device-tool SHA
055ba520df26dbe7806736be5f18c92f12c48c6aa9b99e8341f76ca7dbeb8fd6.
Before Redmi was rerun, the tool gained one API 31 compatibility rule: a
DisplayPresentTime column that is entirely zero is recorded as unavailable,
while malformed numbers and internal gaps still fail closed. The Redmi runs
therefore used status fingerprint
d89258b9a79a6868e46216075601757e38d006f672eedf9e586268dc7639024b
and final tool SHA
bd21f9da400aad8967888af70e8df16abe7161572ab30640ace2c20af6a2b591.
The production APK and flows are byte-identical; no cross-device aggregate or
verdict is computed.

## Adaptive UI and test coverage

The Android host applies WindowInsets.safeContent before composing the game.
Home and gameplay use shared pure geometry for drawing, hit testing, and
semantics. Compact gameplay exposes independent 64 dp Brake/Dash controls and
52×48 dp Pause/PERF controls, allowing a second touch while the first drags the
world. Pause, choice, terminal, and performance overlays adapt to short
landscape and narrow portrait layouts. Accessibility Brake is a stable
press-again toggle; direct pointer input keeps the normal hold/release behavior.
The compact PERF panel performs six text draws with precomputed strings; the
regular desktop layout is unchanged.

| Device profile | API | Logical profile | Navigation / active Hz | Final physical result |
|---|---:|---:|---|---|
| OnePlus CPH2411 | 35 | 360×804 dp | gestures / 120 Hz | Pure portrait+landscape geometry passes; device absent from final adb devices |
| realme RMX2002 | 30 | 360×800 dp; about 360×718 dp usable | three-button / 60 Hz in app | **11/11 instrumentation; primary 3/3; telemetry 3/3** |
| Samsung SM-A325F | 33 | 411×914 dp; about 411×836 dp usable | three-button / 90 Hz | **11/11 instrumentation; primary 3/3; telemetry 3/3** |
| Redmi Note 9 Pro | 31 | 393×873 dp; about 393×788 dp usable | three-button / 60 Hz | **Primary 3/3; telemetry 3/3**; debug install rejected by MIUI |

The common suites cover minimum target size, safe bounds, pairwise non-overlap,
input mapping, compact overlay policy, persistent Brake semantics, and compact
PERF field coverage. The same build passed Desktop/Wasm compilation and strict
Pokeball architecture verification at 22 leaf modules and 77 declared edges.

## Primary performance, PERF disabled

The full-flow gfxinfo summary includes UIAutomator-driven setup and journey
actions; its platform-janky percentage is published for completeness but is not
a pure steady-gameplay score. The terminal ring is a separate bounded sample.
FrameCompleted minus IntendedVsync is completion latency, not Android jank. On
API 31+, terminal jank is strictly FrameCompleted greater than FrameDeadline;
API 30 leaves it unavailable rather than inventing a deadline.

| Device | Hz | Cold p50 | Full-flow janky | Terminal completion p50 / p95 / p99 | UI submission p50 / p95 / p99 | Deadline miss | Produced / presented FPS | PSS p50 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| realme RMX2002 | 60 | 430 ms | 49.13% | 10.92 / 14.48 / 16.08 ms | 2.83 / 5.07 / 5.54 ms | unavailable on API 30 | 60.12 / unavailable | 74.32 MiB |
| Samsung SM-A325F | 90 | 558 ms | 60.56% | 16.68 / 27.16 / 28.26 ms | 5.72 / 9.04 / 11.74 ms | 34.45% | 89.98 / 89.98 | 80.57 MiB |
| Redmi Note 9 Pro | 60 | 427 ms | 88.90% | 40.65 / 41.76 / 43.78 ms | 3.18 / 7.71 / 12.82 ms | 100.00% | 59.85 / unavailable | 102.26 MiB |

All three devices produced at active-refresh cadence with 0% cadence
missed-vsync intervals in the terminal rings. This does not imply frames met
their Android deadlines: production and completion are pipelined. Samsung
remained bimodal:

| Samsung primary fork | Completion p50 / p95 | Deadline miss |
|---:|---:|---:|
| 1 | 15.82 / 17.80 ms | 0.84% |
| 2 | 15.78 / 18.03 ms | 2.52% |
| 3 | 26.27 / 27.95 ms | 100.00% |

Redmi also showed a mode transition. A retained one-fork diagnostic immediately
before the final series had completion p50 24.11 ms with 0% deadline misses.
The following three primary and three telemetry forks entered a roughly 40 ms,
100%-deadline-miss mode even though thermal status remained 0. This is evidence
against deriving a refresh cap or memory policy from a single run. A randomized,
same-device 60-versus-90 experiment is still required before changing refresh
policy.

## Telemetry observer check

The telemetry flow enables the compact PERF panel and remains separate from the
primary result.

| Device | Full-flow janky | Completion p50 / p95 | Deadline miss | PSS p50 | Primary comparison |
|---|---:|---:|---:|---:|---|
| realme RMX2002 | 29.09% | 9.49 / 12.42 ms | unavailable | 72.91 MiB | Both terminal runs stay near/below one 16.67 ms period; telemetry is not slower in this sample |
| Samsung SM-A325F | 58.34% | 16.96 / 27.20 ms | 34.45% | 82.90 MiB | +0.28 / +0.04 ms and identical aggregate deadline rate; one slow fork in each flow |
| Redmi Note 9 Pro | 78.05% | 40.61 / 42.05 ms | 100.00% | 102.86 MiB | -0.04 / +0.29 ms and identical deadline rate; all six final forks stayed in the slow mode |

The compact HUD shows no systematic terminal penalty in these ordered
three-fork samples after draw count was reduced from eleven to six. This is a
diagnostic observation, not a formal equivalence verdict: the flows were not a
randomized paired A/B and their full-flow action sets differ.

## Perfetto diagnostic

A separate Samsung trace used the same exact final benchmark APK. It completed
45 seconds of ftrace/process/package/track-event capture:

- 116,053,354 bytes;
- SHA-256
  3a27694dba0d56ae5c9e75bf0588b46efa648b2c9f986fefa1e0e3dee5aef54a;
- pre/post installed APK identity matched;
- Perfetto returned from baseline 0/0 through ready 1/1 to final idle 0/0;
- the owned remote trace was removed after host bytes and size were verified.

The 111 MiB trace stays in the local build output rather than Git. Its
[machine-readable provenance](android-trace-samsung.json) and
[human summary](android-trace-samsung.md) are retained. The trace is
overhead-bearing and diagnostic-only; a short unrelated detached session
entirely between the three query snapshots is theoretically unobservable.

## Incomplete device evidence

- OnePlus 7DTSXC49PZMRFUPJ was absent throughout final validation. No older APK
  result is promoted to the final table.
- Redmi accepted the exact stable benchmark package without any automated
  consent action, but two later debug-install attempts were rejected by MIUI as
  user-restricted. Their complete stderr is retained. No coordinate, terms
  acceptance, Play Protect scan, or install override was attempted.

## Retained evidence and rerun

Every evidence ZIP is deterministic: fixed entry metadata, sorted paths, and an
embedded manifest containing byte length and SHA-256 for every JSON, schema,
flow, logcat, gfxinfo, XML, PNG, environment, install, and UI-event artifact.

- [realme/Samsung primary, full evidence](android-primary-realme-samsung-evidence.zip)
  — 5f8860f88fda2d700555bb12a5b0a6f5ecfae4dbfbab4780eaafdd474e3b6901
- [realme/Samsung telemetry, full evidence](android-telemetry-realme-samsung-evidence.zip)
  — f8d2a538372a47dd2b1585a082ec699491813b61d9df9a54154b0e6115a715fa
- [Redmi primary, full evidence](android-redmi-primary-evidence.zip)
  — 1cb1d25d01bc846ed481938bd09d853d575febae83ac2b673715aa7d02686da4
- [Redmi telemetry, full evidence](android-redmi-telemetry-evidence.zip)
  — 58590eaded9b52e13415e0b6d58de8980f8bc6d8721a6c0b8ca3f00b856afc25
- [Redmi fast-mode diagnostic](android-redmi-fast-diagnostic-evidence.zip)
  — 705c54f800cc3d7b0c7a2ea7eb53705e524c5a34416c279176d6ee7938296d19
- [Instrumentation outputs and APK hashes](android-ui-tests-evidence.zip)
  — d4e462f0da944d9f7f274cb2757d794d1bfefb6545d5f3982a3390cdc0ff4ddd
- [Perfetto metadata, queries, config, and UI evidence](android-trace-samsung-metadata-evidence.zip)
  — dc83255b3c1331e433c2995581a69283df7ce2758f7ede8ee964b4b10a678cec

    ./gradlew --no-daemon --no-parallel \
      :app:shared:assembleBenchmark \
      :app:shared:assembleDebug \
      :app:shared:assembleDebugAndroidTest

    python3 tools/performance/android_device_benchmark.py \
      --apk app/shared/build/outputs/apk/benchmark/app-shared-benchmark.apk \
      --repository . \
      --flow tools/performance/android_gameplay_flow.json \
      --forks 3 \
      --serial DEVICE_SERIAL \
      --output build/performance/android-device/primary-current
