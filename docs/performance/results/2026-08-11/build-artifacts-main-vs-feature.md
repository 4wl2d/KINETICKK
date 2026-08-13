<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Build and artifact performance comparison

Baseline: `main` at `fedceb8e2d9009d805d70249e10c77e424447945`.

Candidate: `feature/pokeball-full-refactor` at `20a3e2f78988419f5263247085a9a4b3900f4a6e`.

Build measurements are a validated, non-overlapping candidate-baseline-baseline-candidate sequence (A-B-B-A with A=candidate) of clean, offline, no-daemon production builds in distinct working directories. Positive deltas mean the candidate is larger/slower.

## Clean production build

| Branch | Repetition 1 | Repetition 2 | Median |
|---|---:|---:|---:|
| Baseline | 20.90 s | 20.01 s | 20.46 s |
| Candidate | 29.67 s | 26.14 s | 27.90 s |

Median delta: **+36.40%**.

## Distribution inventory

| Scope | Baseline raw | Candidate raw | Raw delta | Gzip delta |
|---|---:|---:|---:|---:|
| Entire distribution | 11.03 MiB | 11.46 MiB | +3.97% | +4.10% |
| `html` | 0.00 MiB | 0.00 MiB | +0.00% | +0.00% |
| `javascript` | 0.32 MiB | 0.32 MiB | +0.20% | +0.23% |
| `legal-metadata` | 0.06 MiB | 0.06 MiB | +0.15% | +0.19% |
| `source-map` | 0.81 MiB | 0.81 MiB | +0.34% | +0.33% |
| `wasm` | 9.83 MiB | 10.27 MiB | +4.42% | +4.32% |

## Branch-specific application Wasm

| Metric | Baseline | Candidate | Delta |
|---|---:|---:|---:|
| Raw bytes | 1.58 MiB | 2.02 MiB | +27.47% |
| Gzip-9 bytes | 0.52 MiB | 0.67 MiB | +30.86% |
| Declared functions | 5,879 | 8,001 | +36.09% |

Application Wasm is identified by its sourceMappingURL custom section. Byte-identical non-application Wasm files: 1.
