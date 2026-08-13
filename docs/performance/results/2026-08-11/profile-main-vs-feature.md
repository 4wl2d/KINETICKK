<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Branch-native profile codec comparison

- Feature: `feature/pokeball-full-refactor` at `20a3e2f78988419f5263247085a9a4b3900f4a6e`
- Main: `main` at `fedceb8e2d9009d805d70249e10c77e424447945`
- Profile: `standard`; forks per branch: 4
- Effect threshold: 5.00%; hierarchical bootstrap: 10,000 resamples.
- Delta convention: positive means feature is slower or allocates more.
- Comparison caveat: The logical fixtures match, but each branch measures its native wire contract: main legacy-v3 versus feature strict-v4. Deltas are end-to-end migration outcomes, not same-byte codec microbenchmarks; strict-v4 validation and canonical re-encoding are intentionally included. Payload bytes and SHA-256 values are outcomes, not inputs.

| Scenario | Feature wire/bytes | Main wire/bytes | Feature ns/op | Main ns/op | Wall delta (95% bootstrap) | Verdict | Feature B/op | Main B/op | Allocation delta (95% bootstrap) | Verdict |
|---|---:|---:|---:|---:|---:|---|---:|---:|---:|---|
| `profile_encode_default` | strict-v4 / 959 | legacy-v3 / 61 | 1,249.21 | 346.80 | +260.21% [+224.36%, +269.59%] | regression | 2,679.87 | 2,112.00 | +26.89% [+13.87%, +30.68%] | regression |
| `profile_decode_default` | strict-v4 / 959 | legacy-v3 / 61 | 4,114.01 | 577.16 | +612.80% [+597.82%, +624.77%] | regression | 9,292.01 | 3,128.00 | +197.06% [+194.12%, +199.49%] | regression |
| `profile_roundtrip_default` | strict-v4 / 959 | legacy-v3 / 61 | 5,612.57 | 925.77 | +506.26% [+482.25%, +513.00%] | regression | 11,968.00 | 5,240.00 | +128.40% [+118.39%, +131.45%] | regression |
| `profile_encode_logical_maximum` | strict-v4 / 2744 | legacy-v3 / 1671 | 11,409.43 | 7,167.51 | +59.18% [+53.25%, +62.23%] | regression | 26,548.02 | 45,896.02 | -42.16% [-42.46%, -41.96%] | improvement |
| `profile_decode_logical_maximum` | strict-v4 / 2744 | legacy-v3 / 1671 | 56,072.12 | 37,920.00 | +47.87% [+47.07%, +49.38%] | regression | 98,316.05 | 75,528.05 | +30.17% [+30.05%, +30.28%] | regression |
| `profile_roundtrip_logical_maximum` | strict-v4 / 2744 | legacy-v3 / 1671 | 68,129.21 | 44,863.97 | +51.86% [+50.41%, +53.10%] | regression | 124,860.06 | 121,424.04 | +2.83% [+2.62%, +2.98%] | stable |

The aggregate JSON preserves every raw fork sample, input-file SHA-256, and full wire SHA-256 outcome, so the result remains auditable after local build output is removed. Medians combine all measurement samples from equally sized, interleaved branch fork sets.
