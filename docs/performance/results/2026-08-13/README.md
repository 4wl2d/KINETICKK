<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Maximum optimization closure

## Decision

The repeated-projection regression documented on
[2026-08-11](../2026-08-11/README.md) is fixed structurally. An accepted game
decision now publishes one matching state/render frame, the published snapshot
is cached, render collections are reused only after exact content checks, and
render-neutral decisions restamp the existing immutable model instead of
projecting it again. The reducer and simulation also gained copy-on-write
storage, no-op fast paths, bounded fixed output batches, sorted projectile hit
history, lazy pending-output buffers, and allocation-free/indexed hot loops.

The safe measured frontier has been reached for this pass. Independent final
audits found no remaining P0/P1 correctness or consistency defect in state
copying, render identity, input semantics, runtime ordering, architecture, or
the Skiko build pipeline. The remaining candidates require a materially broader
architecture change (for example, fusing more FX ownership or splitting the
Compose/Wasm feature surface) and are not justified without a new isolated A/B
experiment.

This is not a claim that the expanded feature branch is free relative to the
smaller `main`. The final app Wasm, retained browser heap, and fully cold
no-cache build remain larger/slower than `main`; those costs are reported below.
The result is a validated optimization maximum for the current feature and
architecture, not a theoretical global optimum.

## Exact attribution and integrity

The authoritative final browser comparison reran all three targets under the
same schema-v3 harness and pinned browser:

| Role | Revision | Dirty | Exact source-tree SHA-256 |
|---|---|---:|---|
| Requested baseline | `fedceb8e2d9009d805d70249e10c77e424447945` | no | `83772cd6a06d942aad11b6adbd7954502f04ea2833f7b43123659e95d0301a3c` |
| Original feature | `20a3e2f78988419f5263247085a9a4b3900f4a6e` | no | `3c9661638df5a60a8666eb47f7f5e6d8328c84b03a9cd7430f5806cbc8e8771e` |
| Optimized worktree | `20a3e2f78988419f5263247085a9a4b3900f4a6e` | yes | `87906fd431369bad53f2b44a64cecc0cd258e2e5e9fa94158c3beeb5eab4e095` |

The final browser harness, probe, and Playwright wrapper were respectively
`0df55944648161a27b85955ca89738481caac6ea907fb48dbc0ba940a1b719a4`,
`b1c807f2b2e310d41fae3034d65e3c8698566070d04f3bb4e858490e564ddf75`,
and `a42cc44643d3e26746fab74fb8f1e46822947a02ad548b00d284955e37f394cb`.
The tool and target trees were checked before and after every run.

Benchmark integrity is fail-closed:

- timed operations publish an actual result and an independently observed
  outcome witness; constant/no-op substitutions fail validation;
- schema, metadata, signed values, fork identity, source roles, and outcome
  fingerprints are exact rather than permissive;
- benchmark adapter, harness, Gradle runner, comparator, and provenance-emitter
  sources are SHA-256 attested;
- browser, artifact, and command tooling now attest Git root, revision, dirty
  state, untracked files, and a content digest before and after measurement;
- command evidence records the actual Gradle launcher/daemon JVM and exact
  Gradle, Kotlin, Node, and Binaryen identities;
- no failed assertion was weakened. A real Android semantics-bounds regression
  introduced while removing `clickable` was reproduced on devices and fixed by
  restoring the correct merged-semantics boundary.

The stricter harness changes the JVM source contract and outcome fingerprint.
Consequently the old 2026-08-11 gameplay numbers and the final v2 numbers are
not presented as a formal statistical comparison. Repeating or copying an old
fork would manufacture evidence. Current absolute results are retained, while
any same-host deltas across a changed witness are labelled directional only.

## Runtime result

The real component benchmark measures `accept -> renderSnapshot ->
visualFxSnapshot` with fresh prestarted fixtures. Five standard forks produced:

| Real component operation | Pooled median | p95 | Allocated median |
|---|---:|---:|---:|
| Running 60 Hz frame | 8.247 µs | 10.125 µs | 3,745 B/op |
| Running pointer move | 3.094 µs | 4.388 µs | 1,273 B/op |
| Paused 60 Hz frame | 0.963 µs | 2.657 µs | 321 B/op |

Against the immediately preceding optimized snapshot, the observed directional
changes were `-4.66%/-15.26%`, `-2.70%/-15.41%`, and
`-7.32%/-34.34%` for wall/allocation respectively. The comparator correctly
marks that comparison incomparable because the final witness contract changed;
it is useful causal evidence, not a release verdict. See the
[component report](../../../../build/performance/final/maximum/component-comparison.md)
and [machine result](../../../../build/performance/final/maximum/component-comparison.json).

The independently validated five-fork Nucleus suite records these current
publication-path medians:

| Nucleus publication operation | Wall median | Allocated median |
|---|---:|---:|
| Published 60 Hz frame | 5.316 µs | 5,456 B/op |
| Published pointer move | 4.833 µs | 3,560 B/op |
| Published paused frame | 4.741 µs | 2,984 B/op |

These absolute values prove the current path and outcome; they are deliberately
not compared to the v1 adapter used in the initial report.

### Runtime changes that reached the frontier

- atomic `GameplayState` plus `GameplayRenderSnapshot` publication and one
  projection per committed revision;
- opaque state/snapshot provenance and exact revision/content validation;
- identity reuse for render-neutral decisions and structural sharing for all
  projection families with raw-float-bit equality and retained-snapshot tests;
- copy-on-write primitive arrays/sets, direct RNG copying, lazy empty audio/FX/
  discovery buffers, and branch-isolation differential tests;
- fixed typed simulation-output batches with canonical order and fail-closed
  audio consequences;
- sorted bounded projectile history with a linear-small/binary-large lookup,
  one-pass live-ID retention, and capacity/history fingerprints;
- indexed stable compaction, no-op intent fast paths, direct root dispatch, and
  nested completion/fault ordering tests;
- per-category immutable FX sharing and old-snapshot immutability checks;
- hoisted render geometry/text helpers, primitive input classification, and a
  Canvas/semantic input path without the Compose `clickable` runtime graph.

## Browser result

The authoritative `clean3` protocol used pinned Chromium `152.0.7977.8`, five
fresh profiles per target, 120 warmup plus 600 measured rAF intervals per fork,
1280×720, and a forced-GC measurement after the natural end-of-run sample.
All 15 forks succeeded with zero console, page, request, or HTTP errors.

| Metric | `main` | Original feature | Final | Final vs feature |
|---|---:|---:|---:|---:|
| Median FPS | 60.0024 | 60.0030 | 60.0024 | Stable |
| p95 / p99 frame | 16.70 / 16.80 ms | 16.705 / 16.80 ms | 16.70 / 16.80 ms | Stable |
| Natural CDP JS heap | 11,756,264 B | 15,877,292 B | 17,282,024 B | +8.85%, regression |
| Forced post-GC retained heap | 5,443,244 B | 5,994,168 B | 6,152,780 B | +2.65%, stable |
| Script duration | 0.243885 s | 0.186452 s | 0.192243 s | +3.11%, stable |
| Task duration | 1.676328 s | 1.414676 s | 1.419951 s | +0.37%, stable |

The natural-heap increase is retained as a real sampled regression, but forced
GC shows it is mostly garbage backlog rather than a new final retention leak.
Final retained heap is stable against the original feature, while it remains
`+13.04%` (about 710 KiB) against `main`. Against `main`, cold/warm FCP improved
`16.81%/15.58%` and warm ready improved `5.21%`; idle frame pacing stayed
stable. The localhost browser workload is a strict presentation/health trace,
not a representative combat GPU trace.

Evidence:

- [final raw](../../../../build/performance/final/maximum/browser-retention-clean3-final.json)
- [final versus `main`](../../../../build/performance/final/maximum/browser-retention-clean3-final-vs-main.md)
- [final versus original feature](../../../../build/performance/final/maximum/browser-retention-clean3-final-vs-feature.md)

## Delivery result

The final optimized application Wasm is byte-identical between the optimized
linker output and hashed distribution file:

| Artifact | Raw | gzip-9 | SHA-256 / note |
|---|---:|---:|---|
| Application Wasm | 2,186,906 B | 724,071 B | `b7763844ec2d2682ebcaff03cbdf2c3d46f73d96780a679451b511e82d20f357` |
| Skiko Wasm | 8,652,729 B | 3,319,858 B | `46caff5f783599bd1c5d3e5e87959d7cb5102c515aac671c9280664368e71dab` |
| Entire 16-file distribution | 11,242,316 B | 4,131,073 B | manifest `96830ae0e463381a1f831bce6667c78601a8917d64b300421b81ef22f72c2813` |

Relative to the original feature, application Wasm is still `+3.39%` raw and
`+2.40%` gzip, but the entire distribution is `-6.48%` raw and `-2.85%` gzip.
Relative to `main`, application Wasm is `+31.80%/+34.00%`, while the entire
distribution is `-2.76%` raw and `+1.13%` gzip. The distinction matters: Skiko
dominates the full distribution, while new application functionality dominates
the branch-specific code delta.

Production linking no longer creates source maps or a `sourceMappingURL`
section. The app module contains 8,117 declared functions and no custom Wasm
sections. The explicit optimized-source SHA plus byte-equality match replaces
the former source-map heuristic. See the
[artifact inventory](../../../../build/performance/final/maximum/artifacts-final-maximum.json).

The current Android benchmark artifact is a 1,060,582-byte minified APK with
SHA-256 `f01a519d3c098d0a56a18068d482358c438b2370a6c488ecb354cbb242bf6d1d`.
It is non-debuggable, shell-profileable, v2-signed, and 16 KiB aligned; R8
mapping and usage outputs are non-empty.

## Build result

Build optimization removed unnecessary Android resource/R tasks from resource-
free shared libraries, narrowed Compose compiler/plugin application, enabled
optimized resource shrinking, made configuration cache safe, removed production
Wasm source-map work, and replaced seven per-project Skiko unpack/copy pipelines
with one cacheable shared filtered artifact transform. The transform writes only
`skiko.wasm` and `skiko.mjs`, validates the archive fail-closed, and reduces the
intermediate Skiko write volume by about 85.7% versus the prior filtered
per-module design.

| Build evidence | Result | Interpretation |
|---|---:|---|
| Full Android CI-like graph, first run | 71 s, 945 actionable | 868 executed, 68 cache, 9 up-to-date; CC stored |
| Exact Android graph reuse | 4 s, 936 actionable | 22 executed, 914 up-to-date; CC reused |
| Final standalone cold/no-cache Wasm distribution | 28.10 s median | `+40.15%` versus `main` 20.05 s; honest remaining cost |
| Standalone Wasm distribution with scoped parallelism | 26.64 s median | `-5.21%` versus final serial; five deterministic runs |
| Legacy cache-warm A-B-B-A diagnostic | 16.97 s median | `-23.45%` versus `main` 22.17 s; legacy schema, not final verdict |

Global `org.gradle.parallel=true` was not enabled: a combined shared-output graph
showed race risk, and broad parallelism was not proven safe. Only the isolated
distribution command has positive deterministic evidence. CI now uses pinned
Ubuntu/JDK versions, prevents duplicate feature push/PR runs, and defines a new
recurring build protocol with one warmup plus three clean offline/no-build-cache
samples, exact source/tool/JVM provenance, and no v1/v2 comparison. The old
schema-v1 timing rows above remain diagnostics until that v2 recurring series is
populated.

## Validation closure

| Surface | Final evidence |
|---|---|
| Unified build | `desktopTest`, architecture verification, production browser distribution, and benchmark APK: success in 59 s; 835 tasks; configuration cache stored |
| Architecture | 22 modules, 77 edges, 14 routes, 10 mappings; resolved manifest byte-identical and all bound anchors resolved |
| Kotlin/desktop | Full desktop tests plus focused component, reducer, COW, mapper, input, layout, and semantics tests passed |
| Tooling | 140/140 Python performance tests; all performance Python compiled; workflow YAML, shell syntax, and diff whitespace checks passed |
| Browser | 15/15 strict schema-v3 forks; 9,000 measured rAF intervals total; post-GC supported; zero actionable diagnostics |
| Android devices | 11/11 instrumented tests on OnePlus API 35, realme API 30, and Samsung API 33: 33/33 total |
| Android delivery | Package/target identities, non-debuggable/profileable manifest, R8 outputs, signature, and 16 KiB alignment verified |
| Wasm delivery | Source/dist app Wasm byte-identical; Skiko referenced and byte-identical; no maps or `sourceMappingURL` |

Redmi instrumentation was not claimed: the current debug/test packages were not
installed and MIUI had previously rejected USB installation. No permission
bypass, UI automation, reinstall, or test-count substitution was used.

## Remaining limits

- The final feature intentionally retains more state and code than `main`:
  post-GC browser heap is `+13.04%` and app Wasm gzip is about `+34.00%`.
- A fully cold, single-worker, no-cache production build remains about `40%`
  slower than `main`; configuration-cache and scoped-parallel paths are much
  faster but do not erase that cold compiler/linker cost.
- Browser evidence is idle-rAF health, not combat GPU, audio-latency, thermal,
  or multi-hour retention evidence. Those require device-specific traces.
- The raw comparator remains trusted reviewed code. Producers attest their full
  tool/source trees, but a separate per-file comparator tool contract was not
  added in this pass.
- Deeper FX fusion, COW-wrapper consolidation, and a smaller Compose feature
  partition may reduce the remaining costs, but each crosses the safe local-
  optimization boundary and requires a fresh behavior-equivalent baseline.

The operating and reproduction contract is in the
[performance tooling guide](../../../../tools/performance/README.md). Raw local
artifacts are intentionally kept under `build/performance/final/maximum`; the
tracked report records their hashes and the distinction between comparable,
directional, and incomparable evidence.
