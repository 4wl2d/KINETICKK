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

Compare the current clean commit with an exact compatible base SHA:

```bash
bash tools/performance/scripts/compare-pr-base.sh --base FULL_BASE_SHA --profile standard
```

The runner uses separate base worktrees and fresh JVMs in A-B-B-A order.
It requires matching harness, semantic workloads, fingerprints, and environment.
Release 0.2.0 uses `gameplay-core-v3` and
`profile-persistence-current-schema-v3`; older workloads are not comparable.
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
