<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Performance tools

## JVM benchmarks

```bash
./gradlew :ball:gameplay:nucleus:performanceBenchmark -PbenchmarkProfile=smoke
./gradlew :ball:profile:resource:profilePerformanceBenchmark -PbenchmarkProfile=smoke
./gradlew :ball:gameplay:interaction:performanceTelemetryBenchmark -PbenchmarkProfile=smoke
```

Use `smoke` to check the harness, `standard` for comparisons, and `deep` for
longer sampling. Select cases with `-PbenchmarkScenarios=name_a,name_b` and
write results with `-PbenchmarkOutput=/absolute/path/result.json`.
Press `F3` in-game for the rolling frame, dispatch, and Canvas performance HUD.

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
