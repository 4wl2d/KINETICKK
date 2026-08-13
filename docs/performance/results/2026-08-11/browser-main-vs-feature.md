<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Browser/Wasm performance comparison

Baseline: `main` at `fedceb8e2d9009d805d70249e10c77e424447945`.

Candidate: `feature/pokeball-full-refactor` at `20a3e2f78988419f5263247085a9a4b3900f4a6e`.

Pinned Chromium `152.0.7977.8`; 5 isolated fresh profiles; 120 warmup + 600 measured rAF intervals per fork.

Outcome: improvement **4**, inconclusive **8**, regression **1**, stable **29**.

A verdict needs a 5.00% effect and a bootstrap interval excluding zero. Localhost navigation and idle rAF are browser-health measurements, not an interactive gameplay trace.

| Metric | Baseline | Candidate | Delta (95% bootstrap) | Verdict |
|---|---:|---:|---:|---|
| `cdp.after.JSHeapTotalSize` | 24.44 MiB | 24.69 MiB | +1.02% [-2.95%, +2.07%] | **stable** |
| `cdp.after.JSHeapUsedSize` | 12.15 MiB | 15.37 MiB | +26.56% [+2.16%, +38.99%] | **regression** |
| `cdp.delta.LayoutDuration` | 0.000 s | 0.000 s | +0.00% | **stable** |
| `cdp.delta.RecalcStyleDuration` | 0.000 s | 0.000 s | +0.00% | **stable** |
| `cdp.delta.ScriptDuration` | 0.505 s | 0.449 s | -11.07% [-31.91%, -2.93%] | **improvement** |
| `cdp.delta.TaskDuration` | 2.884 s | 2.767 s | -4.06% [-38.90%, -0.88%] | **stable** |
| `coldNavigation.firstContentfulPaintMillis` | 472.00 ms | 408.00 ms | -13.56% [-18.03%, -8.70%] | **improvement** |
| `coldNavigation.firstPaintMillis` | 16.00 ms | 12.00 ms | -25.00% [-40.00%, +0.00%] | **inconclusive** |
| `coldNavigation.navigation.domContentLoadedMillis` | 42.40 ms | 32.60 ms | -23.11% [-30.79%, +5.58%] | **inconclusive** |
| `coldNavigation.navigation.loadMillis` | 42.40 ms | 32.60 ms | -23.11% [-30.79%, +5.58%] | **inconclusive** |
| `coldNavigation.navigation.ttfbMillis` | 0.40 ms | 0.30 ms | -25.00% [-50.00%, +25.00%] | **inconclusive** |
| `coldNavigation.resources.totals.decodedBodySizeBytes` | 10.15 MiB | 10.59 MiB | +4.29% [+4.29%, +4.29%] | **stable** |
| `coldNavigation.resources.totals.transferSizeBytes` | 10.15 MiB | 10.59 MiB | +4.29% [+4.29%, +4.29%] | **stable** |
| `coldNavigation.resources.wasm.decodedBodySizeBytes` | 9.83 MiB | 10.27 MiB | +4.42% [+4.42%, +4.42%] | **stable** |
| `coldNavigation.resources.wasm.transferSizeBytes` | 9.83 MiB | 10.27 MiB | +4.42% [+4.42%, +4.42%] | **stable** |
| `coldNavigation.wallNavigationMillis` | 46.00 ms | 35.00 ms | -23.91% [-32.61%, +7.14%] | **inconclusive** |
| `coldNavigation.wallReadyMillis` | 1540.00 ms | 1412.00 ms | -8.31% [-9.92%, -6.89%] | **improvement** |
| `frameMeasurement.heapDeltaBytes.usedJsHeapSizeBytes` | 0 B | 0 B | +0.00% | **stable** |
| `frameMeasurement.longTaskSummary.count` | 0.000 count | 0.000 count | +0.00% | **stable** |
| `frameMeasurement.longTaskSummary.maximumDurationMillis` | 0.00 ms | 0.00 ms | +0.00% | **stable** |
| `frameMeasurement.longTaskSummary.totalDurationMillis` | 0.00 ms | 0.00 ms | +0.00% | **stable** |
| `frameMeasurement.statistics.framesPerSecond` | 60.002 FPS | 60.002 FPS | -0.00% [-0.50%, +0.17%] | **stable** |
| `frameMeasurement.statistics.maximum` | 16.80 ms | 16.80 ms | +0.00% [-49.70%, +297.02%] | **stable** |
| `frameMeasurement.statistics.mean` | 16.67 ms | 16.67 ms | +0.00% [-0.17%, +0.50%] | **stable** |
| `frameMeasurement.statistics.median` | 16.70 ms | 16.70 ms | +0.00% [-0.00%, +0.00%] | **stable** |
| `frameMeasurement.statistics.onePercentLowFramesPerSecond` | 59.524 FPS | 59.524 FPS | +0.00% [-33.11%, +16.47%] | **stable** |
| `frameMeasurement.statistics.over16_67MillisProportion` | 62.33% | 61.67% | -1.07% [-2.13%, +2.49%] | **stable** |
| `frameMeasurement.statistics.over33_33MillisProportion` | 0.00% | 0.00% | +0.00% | **stable** |
| `frameMeasurement.statistics.p95` | 16.70 ms | 16.71 ms | +0.03% [-0.60%, +0.60%] | **stable** |
| `frameMeasurement.statistics.p99` | 16.80 ms | 16.80 ms | -0.00% [-0.00%, +0.00%] | **stable** |
| `warmNavigation.firstContentfulPaintMillis` | 312.00 ms | 268.00 ms | -14.10% [-22.99%, -10.26%] | **improvement** |
| `warmNavigation.firstPaintMillis` | 12.00 ms | 12.00 ms | +0.00% [-40.00%, +66.67%] | **stable** |
| `warmNavigation.memory.usedJsHeapSizeBytes` | 10.11 MiB | 9.54 MiB | -5.66% [-5.66%, +0.00%] | **inconclusive** |
| `warmNavigation.navigation.domContentLoadedMillis` | 10.70 ms | 10.20 ms | -4.67% [-36.65%, +40.19%] | **stable** |
| `warmNavigation.navigation.loadMillis` | 10.90 ms | 10.30 ms | -5.50% [-36.42%, +39.45%] | **inconclusive** |
| `warmNavigation.navigation.ttfbMillis` | 0.20 ms | 0.20 ms | +0.00% [-0.00%, +100.00%] | **stable** |
| `warmNavigation.resources.totals.decodedBodySizeBytes` | 10.15 MiB | 10.59 MiB | +4.29% [+4.29%, +4.29%] | **stable** |
| `warmNavigation.resources.totals.transferSizeBytes` | 0 B | 0 B | +0.00% | **stable** |
| `warmNavigation.resources.wasm.decodedBodySizeBytes` | 9.83 MiB | 10.27 MiB | +4.42% [+4.42%, +4.42%] | **stable** |
| `warmNavigation.resources.wasm.transferSizeBytes` | 0 B | 0 B | +0.00% | **stable** |
| `warmNavigation.wallNavigationMillis` | 13.00 ms | 12.00 ms | -7.69% [-33.33%, +30.77%] | **inconclusive** |
| `warmNavigation.wallReadyMillis` | 1322.00 ms | 1268.00 ms | -4.08% [-5.79%, -3.18%] | **stable** |

## Diagnostics

Baseline: `{"consoleErrors": 0, "consoleMessages": 40, "consoleWarnings": 40, "httpErrors": 0, "pageErrors": 0, "requestFailures": 0}`

Candidate: `{"consoleErrors": 0, "consoleMessages": 40, "consoleWarnings": 40, "httpErrors": 0, "pageErrors": 0, "requestFailures": 0}`
