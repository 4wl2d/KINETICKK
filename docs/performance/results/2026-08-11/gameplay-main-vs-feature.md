<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK performance comparison

Baseline: `main@fedceb8e2d9009d805d70249e10c77e424447945` at `fedceb8e2d9009d805d70249e10c77e424447945` (4 forks).

Candidate: `feature/pokeball-full-refactor@20a3e2f78988419f5263247085a9a4b3900f4a6e` at `20a3e2f78988419f5263247085a9a4b3900f4a6e` (4 forks).

Profile: `standard`; effect threshold: 5.00%; bootstrap resamples: 10,000.

Semantic contract: `outcome-fingerprint`.

## Outcome

incomparable: **1**, regression: **15**, stable: **4**

Lower wall time, CPU time, allocation and GC values are better. A verdict requires both the configured effect size and a bootstrap interval that excludes zero.

## Wall time and allocation

| Scenario | Baseline median | Candidate median | Wall Δ (95% bootstrap) | Baseline B/op | Candidate B/op | Allocation Δ | Verdict |
|---|---:|---:|---:|---:|---:|---:|---|
| `copy_capacity` | 17.959 µs | 18.400 µs | +2.46% [-0.71%, +5.09%] | 231.97 KiB | 232.02 KiB | +0.02% | **stable** |
| `copy_idle` | 439.89 ns | 1.215 µs | +176.18% [+172.14%, +180.14%] | 7.59 KiB | 7.63 KiB | +0.62% | **regression** |
| `fixed_step_collision_hit` | 106.663 µs | 114.692 µs | +7.53% [+1.67%, +11.70%] | 188.30 KiB | 200.64 KiB | +6.55% | **regression** |
| `fixed_step_collision_miss` | 94.031 µs | 101.018 µs | +7.43% [+6.50%, +10.90%] | 126.67 KiB | 139.01 KiB | +9.74% | **regression** |
| `harness_control` | 0.48 ns | 0.48 ns | -0.15% [-1.52%, +1.32%] | 0.0 B | 0.0 B | +0.00% | **stable** |
| `nucleus_frame_60hz_capacity` | 465.417 µs | 508.118 µs | +9.17% [+5.56%, +10.83%] | 324.27 KiB | 353.25 KiB | +8.94% | **regression** |
| `nucleus_frame_60hz_idle` | 2.009 µs | 3.071 µs | +52.84% [+42.85%, +57.42%] | 17.74 KiB | 18.63 KiB | +4.98% | **regression** |
| `nucleus_frame_paused` | 1.722 µs | 2.558 µs | +48.56% [+46.67%, +55.63%] | 17.27 KiB | 17.05 KiB | -1.27% | **regression** |
| `nucleus_pointer_move_idle` | 1.723 µs | 2.499 µs | +45.06% [+42.76%, +52.52%] | 17.07 KiB | 16.88 KiB | -1.14% | **regression** |
| `nucleus_viewport_change_idle` | 1.721 µs | 2.499 µs | +45.22% [+42.79%, +51.95%] | 17.10 KiB | 16.88 KiB | -1.32% | **regression** |
| `published_frame_60hz_idle` | 2.033 µs | 4.346 µs | +113.73% [+108.24%, +127.50%] | 17.80 KiB | 27.38 KiB | +53.80% | **regression** |
| `published_frame_paused` | 1.723 µs | 3.840 µs | +122.89% [+118.94%, +138.04%] | 17.27 KiB | 25.77 KiB | +49.23% | **regression** |
| `published_pointer_move_idle` | 1.710 µs | 5.124 µs | +199.72% [+189.72%, +225.89%] | 17.10 KiB | 34.30 KiB | +100.55% | **regression** |
| `reducer_frame_100ms_idle` | 2.290 µs | 3.743 µs | +63.42% [+56.35%, +66.61%] | 11.01 KiB | 16.43 KiB | +49.25% | **regression** |
| `reducer_frame_60hz_idle` | 762.93 ns | 1.641 µs | +115.07% [+108.19%, +123.70%] | 8.67 KiB | 9.45 KiB | +8.97% | **regression** |
| `render_model_capacity` | 11.718 µs | 11.726 µs | +0.07% [-3.82%, +2.72%] | 91.16 KiB | 90.84 KiB | -0.34% | **stable** |
| `render_model_idle` | 1.209 µs | 1.242 µs | +2.66% [-2.05%, +12.62%] | 9.01 KiB | 8.70 KiB | -3.47% | **stable** |
| `run_start` | 345.34 ns | 1.120 µs | +224.21% [+214.91%, +233.38%] | 7.04 KiB | 6.99 KiB | -0.67% | **regression** |
| `state_initialization` | 164.58 ns | 928.73 ns | +464.29% [+456.63%, +475.74%] | 3.83 KiB | 3.78 KiB | -1.22% | **incomparable** |
| `trace_2s_60hz` | 426.885 µs | 651.106 µs | +52.52% [+47.13%, +55.11%] | 3660.02 KiB | 3778.05 KiB | +3.22% | **regression** |

## Tail latency, CPU and variability

| Scenario | Baseline p95 | Candidate p95 | Baseline CPU median | Candidate CPU median | Baseline CV | Candidate CV |
|---|---:|---:|---:|---:|---:|---:|
| `copy_capacity` | 19.059 µs | 18.643 µs | 17.658 µs | 18.092 µs | 2.88% | 1.13% |
| `copy_idle` | 484.88 ns | 1.233 µs | 430.64 ns | 1.205 µs | 4.82% | 1.05% |
| `fixed_step_collision_hit` | 117.831 µs | 130.615 µs | 106.094 µs | 114.485 µs | 4.32% | 4.45% |
| `fixed_step_collision_miss` | 96.799 µs | 106.079 µs | 93.708 µs | 100.564 µs | 2.01% | 2.90% |
| `harness_control` | 0.49 ns | 0.51 ns | 0.48 ns | 0.48 ns | 1.18% | 2.39% |
| `nucleus_frame_60hz_capacity` | 485.539 µs | 518.470 µs | 463.741 µs | 507.481 µs | 3.64% | 1.21% |
| `nucleus_frame_60hz_idle` | 2.139 µs | 3.342 µs | 1.986 µs | 3.045 µs | 3.39% | 5.32% |
| `nucleus_frame_paused` | 1.831 µs | 2.694 µs | 1.696 µs | 2.535 µs | 2.26% | 2.52% |
| `nucleus_pointer_move_idle` | 1.818 µs | 2.607 µs | 1.699 µs | 2.477 µs | 2.61% | 2.19% |
| `nucleus_viewport_change_idle` | 1.861 µs | 2.613 µs | 1.697 µs | 2.476 µs | 2.92% | 2.48% |
| `published_frame_60hz_idle` | 2.158 µs | 4.655 µs | 2.012 µs | 4.314 µs | 2.75% | 3.27% |
| `published_frame_paused` | 1.779 µs | 4.094 µs | 1.695 µs | 3.813 µs | 1.71% | 3.51% |
| `published_pointer_move_idle` | 1.803 µs | 5.549 µs | 1.687 µs | 5.102 µs | 2.40% | 4.14% |
| `reducer_frame_100ms_idle` | 2.542 µs | 3.854 µs | 2.282 µs | 3.724 µs | 5.44% | 1.56% |
| `reducer_frame_60hz_idle` | 812.50 ns | 2.015 µs | 749.69 ns | 1.629 µs | 5.31% | 7.69% |
| `render_model_capacity` | 12.638 µs | 12.311 µs | 11.599 µs | 11.615 µs | 3.43% | 1.79% |
| `render_model_idle` | 1.343 µs | 1.367 µs | 1.197 µs | 1.229 µs | 4.14% | 4.43% |
| `run_start` | 389.63 ns | 1.144 µs | 335.91 ns | 1.110 µs | 9.38% | 2.50% |
| `state_initialization` | 169.78 ns | 970.57 ns | 159.17 ns | 922.17 ns | 1.68% | 2.05% |
| `trace_2s_60hz` | 446.336 µs | 672.156 µs | 422.069 µs | 645.725 µs | 2.57% | 2.26% |

## Incomparable semantic checkpoints

- `state_initialization` differs in: outcomeFingerprint.

## Environment

| Field | Baseline | Candidate |
|---|---|---|
| `osName` | `Mac OS X` | `Mac OS X` |
| `osVersion` | `26.5.2` | `26.5.2` |
| `architecture` | `aarch64` | `aarch64` |
| `javaVersion` | `21.0.11` | `21.0.11` |
| `javaVendor` | `Eclipse Adoptium` | `Eclipse Adoptium` |
| `vmName` | `OpenJDK 64-Bit Server VM` | `OpenJDK 64-Bit Server VM` |
| `availableProcessors` | `18` | `18` |
| `maxHeapBytes` | `1073741824` | `1073741824` |
| `garbageCollectors` | `['G1 Young Generation', 'G1 Concurrent GC', 'G1 Old Generation']` | `['G1 Young Generation', 'G1 Concurrent GC', 'G1 Old Generation']` |
| `jvmArguments` | `['-XX:+AlwaysPreTouch', '-XX:+UseG1GC', '-Xms1g', '-Xmx1g', '-Dfile.encoding=UTF-8', '-Duser.country=US', '-Duser.language=en', '-Duser.variant']` | `['-XX:+AlwaysPreTouch', '-XX:+UseG1GC', '-Xms1g', '-Xmx1g', '-Dfile.encoding=UTF-8', '-Duser.country=US', '-Duser.language=en', '-Duser.variant']` |
