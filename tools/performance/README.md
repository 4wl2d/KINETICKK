<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Performance tools

## JVM benchmarks

```bash
./gradlew :ball:gameplay:nucleus:performanceBenchmark -PbenchmarkProfile=smoke
./gradlew :ball:profile:impl:profilePerformanceBenchmark -PbenchmarkProfile=smoke
./gradlew :ball:gameplay:interaction:performanceTelemetryBenchmark -PbenchmarkProfile=smoke
```

Use `smoke` to check the harness, `standard` for comparisons, and `deep` for
longer sampling. Select cases with `-PbenchmarkScenarios=name_a,name_b` and
write results with `-PbenchmarkOutput=/absolute/path/result.json`.
For interactive investigation, run
`./gradlew -Pkinetickk.variant=debug :app:desktop:run` and open **DEBUG**.
Record the problematic gameplay, stop recording, and copy the diagnostic report.
Release does not contain the diagnostic UI or recorder. Use the same build variant
on both sides of a performance comparison; Debug measurements include instrumentation.

The recorder's opt-in JVM probe compares stopped, live-panel and recording hooks
in alternating order, with raw nanoseconds and allocation counters:

```bash
KK_DEBUG_RECORDER_BENCH=1 KK_DEBUG_RECORDER_OUT=/absolute/path/recorder.jsonl \
./gradlew -Pkinetickk.variant=debug :app:shared:desktopTest --tests '*DebugRecorderOverheadBenchmarkTest' --rerun
```

This isolates recorder callback overhead. It excludes panel drawing, report
formatting, runtime sampling and game work; it does not establish FPS, GPU time
or a Release speedup.

The reward animation regression uses the real gameplay host with a frozen four-card
choice. After the deal entrance settles, animation must keep changing the rendered
image while the frame clock applies no new compositions. Its ordinary run checks
12 frames; the opt-in probe checks 120 and records a JSON line:

```bash
KK_REWARD_ANIMATION_BENCH=1 KK_REWARD_ANIMATION_LABEL=candidate \
KK_REWARD_ANIMATION_OUT=/absolute/path/reward-animation.jsonl \
./gradlew -Pkinetickk.variant=release :ball:gameplay:interaction:desktopTest \
  --tests '*RewardAnimationPerformanceTest' --rerun
```

Use the identical probe and variant on both revisions in separate test JVMs.
The allocation total covers threads present at the start of the sample; wall time
includes test-clock advancement and UI synchronization. Neither measurement is FPS
or GPU presentation time. The frozen fixture excludes simulation, profile storage
and the initial opening cost; the composition assertion checks the animation path.

To measure the same frozen choice in a native window with the production frame
loop, use the opt-in desktop probe. It closes its own window after sampling and
never loads player data:

```bash
KK_REWARD_DESKTOP_BENCH=1 KK_REWARD_DESKTOP_LABEL=candidate \
KK_REWARD_DESKTOP_OUT=/absolute/path/reward-desktop.jsonl \
./gradlew -Pkinetickk.variant=release :ball:gameplay:interaction:rewardDesktopBenchmark \
  -Dskiko.vsync.enabled=false
```

`WARMUP_MS` (2400), `SAMPLE_MS` (6000), `WIDTH` (1728), and `HEIGHT` (1084)
use the `KK_REWARD_DESKTOP_` prefix. Frozen mode starts with CHOICE already
composed, so use the transition scenario to capture opening work:

```bash
KK_REWARD_DESKTOP_BENCH=1 KK_REWARD_DESKTOP_SCENARIO=transitions \
KK_REWARD_DESKTOP_WARMUP_MS=0 KK_REWARD_DESKTOP_SAMPLE_MS=18000 \
KK_REWARD_DESKTOP_LABEL=candidate KK_REWARD_DESKTOP_OUT=/absolute/path/rewards.jsonl \
./gradlew -Pkinetickk.variant=release :ball:gameplay:interaction:rewardDesktopBenchmark \
  -PrewardBenchmarkJava=21 -PrewardBenchmarkHeap=128m \
  -PrewardBenchmarkJfr=/absolute/path/rewards.jfr -Dskiko.vsync.enabled=false
```

It opens CHOICE at 1 s after the first gameplay callback, closes at 4 s, and
reopens at 5/9/13/17 s. Raw rows include wall-clock callback intervals, the supplied
Compose frame-clock delta, phase age and transition markers. Inspect the entire
first second and the whole choice interval to detect work delayed past opening.
Reward UI preparation finishes before gameplay callbacks begin; assess that
startup cost separately in the JFR/full-process timing.

`KK_REWARD_DESKTOP_PHASE_METRICS=1` adds diagnostic root measure/draw wrappers;
independent descendant remeasurement may bypass them. Use identical instrumentation
on both sides. `rewardBenchmarkJfrSettings` accepts a JFR settings name or path.

Compare fresh processes with the
same variant, viewport, density, renderer and V-Sync setting. JSONL and raw TSV
separate callback intervals, composition changes, thread allocation and process
CPU; callback intervals are not GPU presentation times. Initial/settled samples
must be compared separately, and this fixture excludes simulation and storage.

`OverlayWordFitTest` compares batched word measurement against the previous fitter
over English/Russian text and width/style boundaries. `KK_WORD_FIT_BENCH=1` with
`KK_WORD_FIT_OUT=/absolute/path/word-fit.jsonl` enables alternating paired timing
and allocation samples; these measure text fitting alone.

For Profile owner/decider/completion changes, the codec suite alone does not
measure the changed path. The opt-in owner probe runs a real mute command and
its accepted caller with either a fake Resource or the codec and in-memory
readback. It never opens player storage:

```bash
KK_PROFILE_OWNER_BENCH=1 KK_PROFILE_OWNER_LABEL=candidate \
KK_PROFILE_OWNER_REVISION=FULL_SOURCE_SHA KK_PROFILE_OWNER_DIRTY=true \
KK_PROFILE_OWNER_FORK=1 KK_PROFILE_OWNER_OUT=/absolute/path/candidate-1.jsonl \
./gradlew :ball:profile:impl:desktopTest --tests '*ProfileOwnerPerformanceBenchmarkTest' --rerun
```

Copy the identical probe to both revisions, use fresh test JVMs in A-B-B-A
order and a new output file per fork. `WARMUPS`, `SAMPLES`, and `ITERATION_MS`
use the `KK_PROFILE_OWNER_` prefix (defaults 5, 10, 180). The output records
per-sample nanoseconds and allocated bytes per completed command. Allocation
counters require HotSpot; these are neither RSS nor physical storage timings.
Do not infer FPS, durable-write latency or statistical significance from this
probe. See the [0.6.0 evaluation](../../docs/project/BALLARCH-0.6.0.md).

The `published_reaction_frame_60hz_1x`, `published_reaction_frame_60hz_2x`, and
`published_reaction_burst_100ms_2x` scenarios cover a canonical high-activation
elemental build with 120 enemies, in-flight weapon volleys and a full combat field
table: nine reaction and item fields in mixed kinds plus one field per skill slot.
They measure simulation and immutable render publication, including reuse of the
previous frame, and validate deterministic state, outputs and projection values.

For native desktop rendering, run the same dense feedback scene in an isolated
window that closes automatically and never loads or writes a player profile:

```bash
KK_REACTION_DESKTOP_BENCH=1 ./gradlew :ball:gameplay:interaction:reactionDesktopBenchmark
```

`KK_REACTION_DESKTOP_OUT` selects the JSON-lines file; `LABEL`, `WARMUP_MS` and
`SAMPLE_MS` use the same prefix. Set `KK_REACTION_DESKTOP_EFFECTS=0` for the matched
scene without feedback. Compare the same backend, viewport, density and display.
The report separates frame-callback intervals from Canvas CPU submission time;
neither is a GPU presentation timestamp. Defaults are 2 s warmup and 6 s sampling.

The opt-in `ReactionHeavyRenderBenchmarkTest` and `ReactionFxPerformanceBenchmarkTest`
separate raster Canvas CPU work from FX application/publication. Enable them with
`KK_REACTION_RENDER_BENCH=1` or `KK_REACTION_FX_BENCH=1`, respectively, and run the
selected `:ball:gameplay:interaction:desktopTest --tests '*ReactionHeavyRenderBenchmarkTest' --rerun`
(or the FX class). `OUT` and `LABEL` share the corresponding environment prefix.
Their ordinary tests also verify visible feedback, bounded state and retained snapshots.
Keep raster, native desktop, browser and device results separate.

The opt-in world generation probe measures adjacent and distant chunk-window refreshes and
structure placement queries, including allocations and deterministic output witnesses. The
diagnostics probe separates bounded event recording from disk checkpoint time and uses a
temporary reporter without opening player data:

```bash
KK_WORLD_GENERATION_BENCH=1 ./gradlew :ball:gameplay:nucleus:desktopTest --tests '*WorldGenerationPerformanceBenchmarkTest' --rerun
KK_CRASH_EVENT_BENCH=1 ./gradlew -Pkinetickk.variant=debug :app:shared:desktopTest --tests '*DesktopCrashEventPerformanceBenchmarkTest' --rerun
```

Both accept `LABEL`, `OUT`, `WARMUPS` and `SAMPLES` with their respective environment prefix.
Use identical settings and alternate baseline/candidate processes without simultaneous builds or
tests. `KK_DAMAGE_CROP_BENCH=1` enables the image-construction microbenchmark in
`DamageNumberImageCacheTest`; the reaction scene remains the end-to-end raster check because it
includes both new-label construction and subsequent animated draws.

Compare the current clean commit with an exact compatible base SHA:

```bash
bash tools/performance/scripts/compare-pr-base.sh --base FULL_BASE_SHA --profile standard
```

The runner uses separate base worktrees and fresh JVMs in A-B-B-A order.
It requires matching harness, semantic workloads, fingerprints, and environment.
Release 0.6.0 uses `gameplay-core-v5` and
`profile-persistence-current-schema-v6`; older workloads are not comparable.
The gameplay workloads run on the 0.6.0 flight law and world, the suite adds
the `terrain_window`, `structure_dungeon` and `dimension_visit` scenarios, and
the profile business maximum adds every world discovery.
CI produces candidate-only results when the base lacks the current contract,
then resumes comparisons once both sides support it. Candidate-only results
carry no claim about improvement or regression against the base.

The active contract is [incremental-gate-v2.json](contracts/incremental-gate-v2.json).
Keep results under ignored `build/performance/` and attach them to the PR or CI
run. Historical reports and their adapters remain available in Git history.

## Browser and build output

Build and serve the production bundle:

```bash
./gradlew :app:web:wasmJsBrowserDistribution
python3 -m http.server 8899 --bind 127.0.0.1 --directory app/web/build/dist/wasmJs/productionExecutable
```

In another terminal:

```bash
tools/performance/playwright_cli.sh install-browser chromium
python3 tools/performance/browser_benchmark.py --url http://127.0.0.1:8899/ --output build/performance/browser.json --label current --revision "$(git rev-parse HEAD)" --dirty false --forks 5 --require-canvas --fail-on-diagnostics
```

Use the actual dirty status of the measured checkout. Keep the host, browser,
viewport, and server setup identical for both sides. Each fork uses an isolated
browser profile. `compare_browser_results.py` compares matching results;
`collect_artifacts.py` inventories distribution bytes and verifies the optimized
application Wasm against the packaged bytes. `measure_command.py` records build
timing. Run each script with `--help` for its arguments.

## Android

```bash
./gradlew :app:android:assembleBenchmark
python3 tools/performance/android_device_benchmark.py --apk app/android/build/outputs/apk/benchmark/app-android-benchmark.apk --output build/performance/android-device/current --label current --forks 3
```

See the [device benchmark](android_device_benchmark.md) and
[trace capture](android_trace_capture.md) commands for selectors, device
preconditions, and output formats. Compare the same device and APK identities;
keep ordinary gameplay and telemetry flows separate. The runner preserves app
data and device settings. `archive_evidence.py` packages a result with hashes.

## Checks

```bash
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/performance python3 -m unittest discover -s tools/performance -p 'test_*.py'
```

[Performance CI](../../.github/workflows/performance.yml) runs the comparisons
and uploads results. Preserve raw samples and explain confirmed regressions
or delivery-budget increases; incompatible or unpaired results establish no
performance verdict.

The JVM comparator reports blocking regressions for every metric, including GC.
When a baseline median is zero, it bootstraps the absolute median difference
instead of treating every positive candidate median as a regression. An interval
that includes zero is inconclusive; a consistent new cost still fails the gate.
