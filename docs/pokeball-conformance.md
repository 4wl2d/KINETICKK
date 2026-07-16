<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Pokeball conformance record

> **Result:** the rewrite and integrated local verification are complete, but no
> project conformance or release claim is made on this uncommitted moving
> baseline. `RG-09` also retains the state-byte-meter qualification. `partial`
> is not `pass`.

## Review scope

| Field | Value |
|---|---|
| Review ID | `KIN-PKB-R-20260716-01` |
| Mode | full rewrite implementation review, locally verified; formal gate review open |
| Source baseline | KINETICKK `b2720ff`; review target is the uncommitted `agent/pokeball-architecture-rewrite` working tree |
| Architecture baseline | Pokeball Core `1.0-core-draft`, SHA-256 `b16d26dbd1f8763740c48fb4de8f4a5728c0ae94094592c3b71ef72a46138052`, 211011 bytes |
| Agent Pack | revision 11, 15 files, package digest `a6d34ad374b0ea93aed029c5a5bb046294c7569bd557a3556d317d4f3ea852eb`, baseline `READY` |
| Accepted overlay | `docs/pokeball-project-overlay.md`, revision 1 |
| Authority | Singleton `Game` Feature Ball, `kinetickk.local/Game/local-player` |
| Profiles | `Inline + Transient + InProcess + Standard + Static` |
| Included | Shared Game, desktop/WebAssembly hosts, Interaction, Nucleus, Inline runtime, private Resources, static Assembly, manifest, migration/foundation artifacts, tests |
| Excluded | Packaging/notarization, distribution infrastructure, browser-launcher availability, legal changes, unselected extensions |

Applicable rules are `PKB-AR-GOV-001..004`, `BND-001..002`, `PRT-001..003`,
`STA-001..002`, `ID-001`, `DEC-001..003`, `ASY-001..005`, `CMP-001..004`,
`PRF-001..002`, `SEC-001..003`, `LIM-001..002`, `MAN-001..002`, and
`TST-001..002`. Async/status review is limited to the exact progress Effect/Fact
chain and declared post-acceptance delivery. No broader workflow or durability
semantics are inferred.

## Claims and exclusions under review

The review may prove only:

1. one caller-confined local writer publishes a complete accepted frame before
   dispatch and reserves the sole synchronous completion path before acceptance;
2. Game decisions are pure, deterministic, explicit-input, and bounded; and
3. raw Interaction input and raw Resource output are quarantined around scoped
   exact-key/numeric capabilities.

It explicitly does not claim durable state/output/status, zero-RPO recovery,
exactly-once, at-least-once, eventual delivery, lossless live visual delivery,
hostile isolation, hardened/general security, zero allocation/overhead, or an
atomic multi-source render.

## Protocol and authority inventory inspected

| Unit | Implementation artifact | Contract represented |
|---|---|---|
| Closed Pulses and output payloads | `features/game/nucleus/protocol/GameProtocol.kt`, `VisualFxProtocol.kt` | 21 Intents; 2 Queries; 2 progress Facts; `GameProjectionChanged`; 3 Effects |
| Canonical output envelopes | `GameProtocol.kt`, `GameNucleus.kt` | `ProjectionOutput`/`EffectRequest`, complete `SemanticHandle`, zero-based contiguous `sourceOrdinal` |
| Canonical reads | `features/game/nucleus/read/GameRead.kt` | `CommittedStateSnapshot`, `ReadContext`, `ReadResult`, exact 3-field `ConsistencyStamp` |
| Pure decision candidate | `features/game/nucleus/state/MutableGameState.kt`, `transition/GameNucleus.kt` | Explicit cloneable gameplay RNG, no Resource call, exact `Accepted | Rejected` result |
| Atomic runtime | `application/runtime/{DecisionModel,BoundedPreflightPolicy,InlineAcceptedFrameRuntime}.kt` | Immutable output batch, complete preflight, publication before dispatch, reentrancy rejection |
| Completion/output owner | `features/game/GameFeatureBall.kt` | One fixed pre-reserved `PersistProgress` Fact slot, causal depth 2, exact provider/handle quarantine; root visual cues returned separately from canonical read |
| Interaction authority | `interaction/GameInteraction.kt`, `interaction/validation/InteractionValidation.kt`, `interaction/fx/InteractionFxReducer.kt` | Raw numeric ingress quarantine; unstamped render attachment and visual collections/RNG outside Sovereign Game state |
| Visual output bound | `features/game/nucleus/protocol/VisualFxProtocol.kt` | 2047 retained cues + one `VisualCuesDropped` slot; sync-critical cues prefer decorative eviction and the defensive fallback evicts oldest visual |
| Private Resources | `features/game/resources/` and platform actuals | Fixed progress provider/keys and bounded numeric audio; typed unknown outcome |
| Static composition | `application/assembly/GameAssembly.kt` | Direct construction; no service registry/inter-Ball route |

These targets were inspected and were compiled/executed as recorded by
`KIN-E03`; the table alone is not a conformance result.

## Release gates

| Gate | State | Current evidence | Required to close |
|---|---|---|---|
| `RG-01` Scope | pass | Accepted overlay defines one Ball, authority/non-goals, all five profile dimensions, exclusions, and no extension | Reopen on scope/authority/profile change |
| `RG-02` Protocol | partial | Kotlin/manifest inventory, canonical envelopes/read values, exhaustive representative variants, desktop tests, and both target compilations passed locally | Attest the same evidence on an immutable source baseline |
| `RG-03` Authority | partial | Sovereign/ephemeral split, FX-free stamped read, immutable containers, direct-mutation/import scans, and projection/authority tests passed locally | Attest the same evidence on an immutable source baseline |
| `RG-04` Decision | partial | Determinism, rejection, publication ordering, output-envelope preflight, and reentrancy tests passed locally | Attest the same evidence on an immutable source baseline |
| `RG-05` Async/status | partial | Exact progress handle/provider/reason, fixed completion reservation, stale/mismatch/unknown/retention paths, depth 2, and stamped status tests passed locally | Attest the same evidence on an immutable source baseline |
| `RG-06` Composition | partial | Static `GameAssembly`, private capabilities, empty dependency graph, version bindings, and Foundation direction checks passed locally | Attest the same evidence on an immutable source baseline |
| `RG-07` Security | partial | Both quarantines, malformed/oversized/provider paths, safe fixed-key/numeric capabilities, and forbidden-capability scans passed locally | Attest the same evidence on an immutable source baseline |
| `RG-08` Profiles | partial | `Inline + Transient + InProcess + Standard + Static`, Resource queue distinction, fault evidence, and non-guarantees were verified locally | Attest the same evidence on an immutable source baseline; no stronger claim may be inferred |
| `RG-09` Limits | partial | Nine finite limits, generic `N/N+1`, visual/audio caps, completion capacity, and cross-limit cue assertion passed locally | Replace or formally qualify the model estimator/fixed GameBallState byte allowance, then attest on an immutable baseline |
| `RG-10` Integrity | partial | Core/Agent Pack hashes, package comparison, index/links, YAML, source/test/doc consistency, and clean diff checks passed locally | Attest the same evidence on an immutable source baseline |

No failed gate is currently recorded. That is not a pass outside the units
actually closed. Any discovered implementation conflict changes the applicable
row to `fail` until corrected or covered by an allowed accepted deviation.

## Exact evidence sources

| Evidence family | File/class | Coverage intended; execution status |
|---|---|---|
| Generic Inline runtime | `src/commonTest/kotlin/kinetickk/application/runtime/InlineAcceptedFrameRuntimeTest.kt` / `InlineAcceptedFrameRuntimeTest` | Immutable Decision/frame, publication-before-dispatch, rejection, reentrancy, generic nine-limit `N/N+1`; passed in `KIN-E03` |
| Game boundary | `src/commonTest/kotlin/kinetickk/features/game/GameFeatureBallArchitectureTest.kt` / `GameFeatureBallArchitectureTest` | Deterministic trace, FX-free immutable stamped read, separate visual cues, ingress rejection, Resource ordering/faults, persistence causality, limits, explicit Nucleus inputs; passed in `KIN-E03` |
| Interaction quarantine | `src/commonTest/kotlin/kinetickk/features/game/interaction/GameInteractionValidationTest.kt` / `GameInteractionValidationTest` | Exact frame/viewport/pointer boundaries and invalid-viewport retention; passed in `KIN-E03` |
| Interaction FX | `src/commonTest/kotlin/kinetickk/features/game/interaction/fx/InteractionFxReducerTest.kt` / `InteractionFxReducerTest` | Separate deterministic visual RNG, exact finite visual caps, clear/rebase policy; passed in `KIN-E03` |
| Visual cue bound | `src/commonTest/kotlin/kinetickk/features/game/nucleus/protocol/BoundedVisualFxCueAccumulatorTest.kt` / `BoundedVisualFxCueAccumulatorTest` | Exact 2048 boundary, synchronization-cue retention, reset-on-drain, explicit visual-drop metadata; passed in `KIN-E03` |
| Progress Resource/bootstrap | `features/game/resources/progress/{ProgressStoreTest,ProgressCodecTest}.kt`, `application/assembly/GameAssemblyTest.kt` | Provider identity, fixed keys, UTF-8/codec limits, second loaded-value normalization, non-finite/collection rejection, complete writes, typed unknown; passed in `KIN-E03` |
| Audio Resource | `src/commonTest/kotlin/kinetickk/features/game/resources/audio/GameAudioResourceTest.kt` / `GameAudioTest` | Cue priority/cap, safe tone sink, failure containment; passed in `KIN-E03` |
| Desktop audio policy | `src/desktopTest/kotlin/kinetickk/features/game/resources/audio/DesktopAudioExecutionPolicyTest.kt` / `DesktopAudioExecutionPolicyTest` | One worker, queue 24, discard-oldest declaration; passed in `KIN-E03` |
| Immutable Foundation | `src/commonTest/kotlin/kinetickk/foundation/collections/ImmutableCollectionsTest.kt` / `ImmutableCollectionsTest` | Defensive-copy, iterator/cast resistance, equality/order; passed in `KIN-E03` |
| RNG Foundation | `src/commonTest/kotlin/kinetickk/foundation/random/CloneableXorWowRandomTest.kt` / `CloneableXorWowRandomTest` | Kotlin 2.3.20 sequence compatibility and exact cursor copy; passed in `KIN-E03` |
| Behavior preservation | `src/commonTest/kotlin/kinetickk/features/game/nucleus/characterization/` | Migrated gameplay/progression/catalog/system behavior baseline; passed in `KIN-E03` |

## Evidence register

| Evidence | Scope | Current status |
|---|---|---|
| `KIN-E01` | Core hash/bytes/version and Agent Pack file count/digest | pass |
| `KIN-E02` | Pre-rewrite behavior baseline: assemble, 92 desktop tests, Wasm test compilation | pass for `b2720ff` only |
| `KIN-E03` | Rewritten protocol, authority, runtime, Resources, Assembly, and named tests | pass locally: `desktopTest` 150/150, `wasmJsTestClasses`, `assemble`, and `wasmJsBrowserDistribution` succeeded on JDK 21/Gradle 8.14.3/macOS arm64 |
| `KIN-E04` | Browser runtime | partial: `wasmJsBrowserTest` compiled its test executable but ChromeHeadless could not start because `/Applications/Google Chrome.app/...` and `CHROME_BIN` were unavailable |
| `KIN-E05` | Foundation export/dependency/platform static scan | pass for the inspected working tree; both Foundation suites also passed within `KIN-E03` |
| `KIN-E06` | Manifest/document syntax and diff hygiene | pass locally: YAML closure parsed as 21 Intents/2 Queries/1 Projection/3 Effects/2 Facts; links/index/package comparison and `git diff --check` passed |

## Final validation commands

```bash
shasum -a 256 spec/pokeball-architecture-core.md
wc -c spec/pokeball-architecture-core.md

python3 - <<'PY'
from pathlib import Path
import hashlib

root = Path('docs/agents')
h = hashlib.sha256()
for path in sorted(p for p in root.glob('*.md') if p.name != 'BASELINE.md'):
    h.update(path.name.encode('utf-8'))
    h.update(b'\0')
    h.update(path.read_bytes())
    h.update(b'\0')
print(h.hexdigest())
PY

./gradlew assemble
./gradlew desktopTest --rerun-tasks
./gradlew wasmJsTestClasses --rerun-tasks
./gradlew wasmJsBrowserDistribution
```

The final review also parses `architecture/game/ball.yaml`, checks links and the
root agent index, runs the Foundation/import/legacy-path searches, inspects test
reports, and runs `git diff --check`. A successful build alone is insufficient.

Recorded local result: all four listed validation commands passed. The separate
`wasmJsBrowserTest` attempt reached browser launch and failed only because the
machine had no ChromeHeadless binary/`CHROME_BIN`.

## Valid closing language

Only after every gate is `pass` on an immutable source baseline may the record
say:

> On KINETICKK commit `<commit>` and Pokeball Core hash `<hash>`, gates
> `RG-01..10` passed within the recorded scope; no failed or partial unit remains.

Until then, the valid statement is: **the Pokeball design, rewrite
implementation, and integrated local verification are complete; formal
immutable-baseline review and the `RG-09` meter qualification remain, so
conformance is not yet claimed.**
