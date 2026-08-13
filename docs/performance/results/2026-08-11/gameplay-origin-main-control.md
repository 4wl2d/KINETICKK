<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Gameplay publication control against `origin/main`

- Candidate: `feature/pokeball-full-refactor@20a3e2f78988419f5263247085a9a4b3900f4a6e`
- Baseline: `origin/main@a0762dd40df50a06f48f31f2916960ea04992dc2`
- Protocol: smoke, one A-B-B-A cycle, two forks per branch
- Semantic contract: category, description, complete metadata, and deterministic
  outcome fingerprint match for all 20 shared scenarios
- Machine-readable report with embedded raw samples:
  [`gameplay-origin-main-control.json`](gameplay-origin-main-control.json)

| Scenario | Candidate ns/op | Baseline ns/op | Wall delta | Candidate B/op | Baseline B/op | Allocation delta |
|---|---:|---:|---:|---:|---:|---:|
| Published 60 Hz frame | 4,453.01 | 2,162.70 | +105.90% | 28,040.03 | 17,760.01 | +57.88% |
| Published pointer move | 5,253.09 | 1,746.11 | +200.84% | 35,144.04 | 17,064.01 | +105.95% |
| Published paused frame | 3,953.61 | 1,731.28 | +128.36% | 26,384.01 | 17,128.01 | +54.04% |

This control is intentionally targeted rather than presented as the full
standard comparison. It demonstrates that the dominant publication result is
not caused by the four commits between literal local `main` and `origin/main`.
