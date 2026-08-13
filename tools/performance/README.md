<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Performance evidence

KINETICKK uses layered measurements because no single benchmark can distinguish
simulation cost, state projection, Compose drawing, browser startup, and bundle
growth. The suite stores raw samples and exact source identities so a change can
be evaluated against evidence instead of one noisy stopwatch run.

The first recorded baseline is [`main@fedceb8`](../../docs/performance/results/2026-08-11/README.md)
versus `feature/pokeball-full-refactor@20a3e2f`. The local `main` reference was
four commits behind `origin/main`, so the gameplay runner also supports the exact
`origin/main@a0762dd` control.

## Coverage

| Layer | Evidence | Entry point |
|---|---|---|
| Gameplay mechanics | State construction, run start, copy/reduction, fixed steps, collisions, render projection, Nucleus commands, published interaction pipeline, pointer/viewport/pause paths, and a deterministic two-second trace | `:ball:gameplay:nucleus:performanceBenchmark` |
| Profile and persistence | Strict codec boundaries and rejection paths, logical fixtures, exact 65,536-byte payload, and isolated in-memory resource reads/writes | `:ball:profile:resource:profilePerformanceBenchmark` |
| Runtime overhead | Disabled/enabled collection, rolling-window publication, HUD projection, frame/dispatch/Canvas percentiles, jank rates, and entity peaks | `:ball:gameplay:interaction:performanceTelemetryBenchmark` and `F3` in-game |
| Browser/Wasm | Cold and warm navigation, paint/ready timing, resources, raw `requestAnimationFrame` intervals, long tasks, CDP CPU/heap counters, canvas discovery, and browser/page/network diagnostics | `browser_benchmark.py` |
| Physical Android | Process-cold startup, refresh-aware frame deadlines/jank, PSS/heap, thermal/battery state, semantic touch flow, screenshots, and fatal/ANR diagnostics on the four-device lab matrix | [`android_device_benchmark.py`](android_device_benchmark.md) |
| Build and delivery | Clean-command wall time, raw/gzip artifact sizes, hashes, Wasm sections, declared function count, and explicit optimized-to-distribution Wasm provenance | `measure_command.py` and `collect_artifacts.py` |

The JVM harness records wall time, current-thread CPU time, current-thread
allocated bytes, GC collections/time, p95, coefficient of variation, environment,
revision, and every raw sample. Unsupported runtime counters remain `null`; they
are never silently replaced with zero.

## Profiles

| Profile | Warmups | Measurements | Target time per measurement | A-B-B-A cycles |
|---|---:|---:|---:|---:|
| `smoke` | 2 | 3 | 40 ms | 1 |
| `standard` | 5 | 10 | 180 ms | 2 |
| `deep` | 10 | 20 | 450 ms | 3 |

Use `smoke` only to validate the pipeline. Use `standard` for a change decision;
use `deep` when the standard interval is wide or the decision is expensive.
Each cycle launches fresh JVMs in feature-baseline-baseline-feature order.

## Reproduce a branch comparison

Only compare revisions that both implement the exact raw-schema-v2 harness,
five-role source provenance, and semantic validation contract. Run the complete
gameplay and profile A-B-B-A matrix against an exact compatible base SHA:

```bash
bash tools/performance/scripts/compare-pr-base.sh \
  --base "$(git rev-parse origin/main^{commit})" \
  --profile standard
```

The runner rejects a base that lacks or drifts from the v2 capability marker and
attested source files. It retains a clean detached worktree under
`build/performance/worktrees`, never resets, cleans, removes, or commits it, and
refuses a non-empty output directory.

The original `main@fedceb8` and `origin/main@a0762dd` reports remain immutable
historical raw-schema-v1 evidence under `docs/performance/results/2026-08-11`.
Their compatibility adapters cannot be relabelled or executed through the v2
harness: doing so would mix schemas and validation workloads. The archived
`compare-main-refactor*.sh` entry points therefore fail before starting Gradle
on a v2 checkout. Reproduce those reports from their recorded historical commit,
or record candidate-only bootstrap evidence until a comparable v2 base exists.

For one current-branch suite without a comparison:

```bash
./gradlew :ball:gameplay:nucleus:performanceBenchmark \
  -PbenchmarkProfile=smoke

./gradlew :ball:profile:resource:profilePerformanceBenchmark \
  -PbenchmarkProfile=smoke

./gradlew :ball:gameplay:interaction:performanceTelemetryBenchmark \
  -PbenchmarkProfile=smoke
```

Use `-PbenchmarkScenarios=name_a,name_b` to select scenarios and
`-PbenchmarkOutput=/absolute/path/result.json` to keep a specific raw result.

## Browser and artifact evidence

Build and serve the production distribution, then benchmark the served URL.
Use the same host, browser version, viewport, protocol, and server setup for both
sides of a comparison.

```bash
./gradlew :app:web:wasmJsBrowserDistribution
python3 -m http.server 8899 \
  --bind 127.0.0.1 \
  --directory app/web/build/dist/wasmJs/productionExecutable
```

In another shell:

```bash
tools/performance/playwright_cli.sh install-browser chromium

python3 tools/performance/browser_benchmark.py \
  --url http://127.0.0.1:8899/ \
  --output build/performance/browser.json \
  --label current \
  --revision "$(git rev-parse HEAD)" \
  --dirty true \
  --forks 5 \
  --warmup-frames 120 \
  --measure-frames 600 \
  --require-canvas \
  --fail-on-diagnostics

python3 tools/performance/collect_artifacts.py \
  --root app/web/build/dist/wasmJs/productionExecutable \
  --application-wasm-source \
    app/web/build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm \
  --output build/performance/artifacts.json \
  --label current
```

The application-Wasm source must be the optimized linker output outside the
distribution. Collection fails unless its size, SHA-256, and bytes match exactly
one selected `.wasm` file. The inventory records both repository-relative source
provenance and the hashed distribution path; it never guesses the application
module from source-map metadata, file size, or Wasm section-count heuristics.

The repository wrapper pins `@playwright/cli@0.1.18`. Every browser fork gets a
fresh session, working directory, and persistent profile, so its first navigation
is cold and its second navigation is warm without leaking state between branches.
Browser result schema v2 preserves the natural pre/post-frame CDP heap samples and
adds a separate fail-closed live-retention sample immediately after one CDP
`HeapProfiler.collectGarbage` pass. It records both `Performance.getMetrics` and
`Runtime.getHeapUsage`; a missing CDP command or metric invalidates the fork rather
than silently falling back to the phase-sensitive natural heap value.
Compare compatible results with:

```bash
python3 tools/performance/compare_browser_results.py \
  --baseline build/performance/main-browser.json \
  --candidate build/performance/feature-browser.json \
  --output-json build/performance/browser-comparison.json \
  --output-markdown build/performance/browser-comparison.md
```

`measure_command.py` measures an external command's wall time and diagnostic
waited-child CPU usage. Detached daemon CPU is deliberately excluded. Run clean
builds in isolated worktrees when using it for branch attribution. Command-result
schema v2 retains every warmup and measured sample. Before and after measurement it
requires `--cwd` to be the exact Git worktree root, `--revision` to equal the full
`HEAD^{commit}` object ID, and `--dirty` to match porcelain status including untracked files;
the exact commit ID and status bytes must remain unchanged. The output must not exist at
validation time and, when placed inside the worktree, must be Git-ignored so writing
the evidence cannot falsify the dirty attestation; tracked paths and Git metadata are
always forbidden. Blocking branch comparisons continue to require `dirty=false`.

Every current producer result also carries `sourceTreeSha256`. This digest covers the `HEAD` commit,
exact index entries, porcelain status, every tracked worktree file (including missing
entries and symlink targets), and every non-ignored untracked regular file or symlink.
It is captured before and after measurement, so same-path content changes are rejected
even when porcelain status text is unchanged. Ignore decisions use repository
`.gitignore` files, whose own contents are fingerprinted; ignored Gradle/build outputs
are deliberately excluded.

Artifact inventory schema v3 and browser schema v3 use the same fail-closed helper:
their explicit revision/dirty declarations must match the exact `--repo-root`, and
the digest must remain unchanged across inventory collection or all browser forks.
Browser evidence separately attests the measured target worktree and the tool worktree
containing its wrapper and probe. This permits honest external main/feature worktree
comparisons while the comparator requires the complete harness-tree identity, wrapper,
and probe to match. A dirty target remains attributable through its exact digest and
unchanged pre/post snapshots; CI policy may still require clean inputs. Build artifacts
remain outputs rather than cryptographic proof of the compiler invocation that created
them; the attestation guarantees that their label and recording interval describe the
current immutable source tree honestly. The comparators are trusted reviewed code and
are not themselves authenticated by an evidence document.

The v2 environment fingerprints the exact `Launcher JVM` and `Daemon JVM` lines
reported by the worktree's executable `./gradlew --version --no-daemon` both before
and after timing—not whichever `java` happens to be first on `PATH`. It also records
the Gradle distribution SHA-256, Kotlin version, and Gradle-managed Node.js and
Binaryen versions. A v1 result remains valid only inside a consistently legacy group;
comparisons never mix v1 and v2 evidence, and v2 rejects unknown environment fields.

The scheduled production job primes dependencies and toolchains online in an
isolated Gradle user home, then records one explicit warmup and three clean,
offline, no-build-cache repetitions in a single v2 document. Ambiguous installed
Node.js or Binaryen versions fail closed instead of being attributed to an
arbitrary executable.

## Physical Android evidence

Build the non-debuggable, shell-profileable benchmark variant and run the
selector-first process-cold protocol on attached devices:

```bash
./gradlew :app:android:assembleBenchmark
python3 tools/performance/android_device_benchmark.py \
  --apk app/android/build/outputs/apk/benchmark/app-android-benchmark.apk \
  --output build/performance/android-device/current \
  --label current \
  --forks 3
```

The default matrix is OnePlus CPH2411 (API 35), realme RMX2002 (API 30),
Samsung SM-A325F (API 33), and Redmi Note 9 Pro (API 31). The runner derives a
frame budget from the refresh rate observed immediately before and after each
fork; raw frame counts are never compared across devices. It does not clear app
data, uninstall, change display/navigation/rotation settings, accept OEM terms,
or bypass Play Protect. See the [Android benchmark contract](android_device_benchmark.md)
for the exact selectors, environment gates, retained artifacts, and same-serial
comparison rule.

Run ordinary gameplay with `android_gameplay_flow.json`; measure the compact
PERF observer separately with `android_gameplay_telemetry_flow.json`. Never pool
the two flows. Preserve a complete, portable result directory with the
deterministic archiver:

```bash
python3 tools/performance/archive_evidence.py \
  --source build/performance/android-device/current \
  --output build/performance/android-device/current-evidence.zip \
  --logical-root android-device-current
```

The ZIP uses fixed entry metadata and embeds a manifest with the byte length and
SHA-256 of every raw artifact. For overhead-bearing Perfetto capture, use the
separate [Android trace contract](android_trace_capture.md); trace output is
diagnostic and is never merged into the gfxinfo verdict.

## Decision contract

1. **Semantics first.** A gameplay timing is comparable only when category,
   description, fixture cardinalities, deterministic outcome fingerprint, and
   scenario metadata match. A profile comparison requires the same logical-shape
   contract. A gameplay mismatch is retained as an `incomparable` row with no
   verdict; a profile logical mismatch stops aggregation. Neither is a
   performance win or loss.
2. **Environment second.** Keep exact revisions, JDK/JVM flags, architecture,
   browser version, viewport, protocol, and storage isolation in the report.
   Do not compare measurements from different hosts as if they were paired.
3. **Effect plus uncertainty.** For sampled JVM/browser metrics, classify a
   regression only when the median effect is at least 5% and the hierarchical
   95% bootstrap interval excludes zero. Preserve p95 and variability even when
   the median is stable.
4. **Exact delivery budgets.** Treat distribution gzip growth above 2%,
   branch-specific application-Wasm growth above 5%, or clean build-wall growth
   above 15% as requiring an explicit explanation. Exact byte deltas need no
   confidence interval.
5. **No silent acceptance.** A pull request that touches a hot path records the
   command/report and explains every material regression or budget increase.
   Correctness, architecture, and security may justify a tradeoff, but the cost
   remains visible.

These thresholds are review guardrails, not claims that a 4.9% change is free.
Trend the raw values and tighten budgets after enough comparable CI history exists.

## Runtime HUD

Press `F3` during gameplay to enable the rolling performance HUD. Pressing `F3`
again disables it; every toggle resets the window. The HUD reports frame,
accepted-command dispatch, and Canvas draw p50/p95/p99/max, FPS, 1% low FPS,
fractions over 16.67/33.33 ms, and current/peak entity counts. Samples are bounded
to 600 values per channel and snapshots are published at most twice per second.
After one-time composition creates the bounded buffers and empty projection, the
disabled steady-state per-frame path performs no timing, sorting, allocation, or
drawing.

The HUD is diagnostic evidence for a real machine, display, and driver. It does
not replace deterministic JVM comparisons or browser traces.

## Adding or changing a benchmark

- Put deterministic mechanical work in an existing module's `desktopTest`
  compilation. Do not add a benchmark leaf module: the architecture verifier
  intentionally fixes the 23-module graph.
- Make setup happen outside the measured operation, consume an observable result,
  and include cardinalities plus a deterministic semantic fingerprint.
- Use branch-specific thin adapters when types changed. Keep the shared scenario
  contract and harness identical; never force old and new code through different
  amounts of work just to make them compile.
- Add a new versioned compatibility directory for a new baseline SHA. Do not
  mutate historical adapters or relabel a smoke run as standard evidence.
- Commit reports and aggregate JSON under `docs/performance/results`. Gameplay
  and profile aggregates embed every raw fork sample plus input-file SHA-256;
  individual convenience files may remain under ignored `build/performance/results`.

Pokeball conformance attestation treats benchmark source and build integration as
implementation. After such changes, follow the documented implementation-freeze
and docs-only attestation sequence in `docs/architecture/pokeball`; never weaken
the conformance verifier to make a performance change pass.

## CI

For a pull request whose exact base already contains the incremental suite,
`.github/workflows/performance.yml` runs `compare-pr-base.sh` against
`pull_request.base.sha`. Gameplay and profile each use clean, detached base code,
the same branch-native tasks, and fresh A-B-B-A JVM forks. The check fails on a
confirmed regression, an unpaired scenario, or a semantic contract mismatch.
Gameplay requires matching deterministic outcome fingerprints; profile requires
exact category, description, and all payload SHA/boundary/expected-outcome metadata.
The initial benchmark-migration PR cannot benchmark a suite absent from its base.
It therefore records strict candidate-only gameplay and profile raw reports,
validates their schema, source provenance, semantic witnesses, revision, clean
identity, and fork identity, and emits no branch-relative verdict. Once the exact
base carries the same v2 capability and source contract, CI switches to the
blocking A-B-B-A regression gate.

Every path publishes the generated Markdown to the GitHub step summary and
uploads raw reports. Weekly/manual runs retain the broader historical and
production-browser evidence. The normal CI still owns correctness and
architecture gates.
