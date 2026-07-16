<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Pokeball migration record

> **Status:** the source rewrite and integrated local validation are complete;
> formal release-gate closure remains open on the uncommitted working tree. The
> behavior baseline is KINETICKK commit `b2720ff`.
> The architecture baseline is Pokeball Core `1.0-core-draft`, SHA-256
> `b16d26dbd1f8763740c48fb4de8f4a5728c0ae94094592c3b71ef72a46138052`.

## Migration contract

The rewrite preserves the observable desktop/WebAssembly game while replacing
the monolithic mutable-engine/platform boundary with the accepted singleton
`Game` Feature Ball. It does not add a Ball per screen/system, a Flow, a Read
Model Ball, durable state, a network service, dynamic composition, or a new
distribution mechanism.

The source of truth is, in order:

1. `spec/pokeball-architecture-core.md`
2. `docs/pokeball-project-overlay.md`
3. `architecture/game/ball.yaml`
4. `docs/ARCHITECTURE.md`

These artifacts constrain the implementation; none substitutes for executable
or static evidence.

## Legacy-to-rewrite map

| Baseline responsibility | Rewritten owner | Migration result |
|---|---|---|
| `App.kt` remembers and mutates `GameEngine` | `GameAssembly`, `GameFeatureBall`, and Game Interaction | Compose maps raw input through validation, dispatches closed Intents, and renders immutable query payloads |
| `GameEngine.kt` owns decisions, RNG, platform requests, UI hit testing, and cosmetic FX | Pure Game Nucleus plus explicit Interaction/Resources | `MutableGameState` is a private cloned decision candidate; gameplay RNG is explicit; I/O is removed; bounded FX cues cross a typed projection output |
| `GameRenderer.kt` reads mutable engine fields | Canonical `GameProjection` plus Interaction render attachment | Stamped Game read is FX-free; Interaction joins its structurally immutable visual snapshot after the read and exposes no mutation capability to the renderer |
| Ambient/top-level progress storage functions | Private progress Resource plus Assembly bootstrap quarantine | Fixed provider/keys, bounded UTF-8 codec input, second loaded-value normalization/cap check, complete snapshots, typed outcomes, and exact-handle Facts |
| `GameAudio` and platform tone player | Private audio Resource | Closed audio Effects, finite numeric safe sink, explicit desktop queue/loss policy, direct Wasm sink |
| Content, Relic, Rebirth, settings catalogs | `features/game/nucleus/domain` | Domain rules remain private to Game and do not enter Foundation |
| Seeded `Random` owned by mutable engine | `CloneableXorWowRandom` cursor in sovereign state | Decision cloning preserves exact Kotlin 2.3.20 seeded sequence without ambient random reads |
| Cosmetic particles, echoes, shockwaves, damage text, arcs | `GameAssembly`-owned `InteractionFxReducer` | `GameDispatchResult` returns post-commit cues separately from the stamped Query; Interaction uses a separately seeded explicit RNG and bounded ephemeral collections |
| Baseline engine/system tests | Nucleus characterization suites plus architecture/runtime/Interaction/Resource/Foundation tests | Behavior assertions are retained under role-first packages and supplemented by boundary/fault/limit evidence |

## Protocol migration

Protocol `1.0.0` closes the former direct method-call surface:

- all 21 Intents are declared, including `ReturnToMenuRequested` and
  `UserGestureObserved`;
- `GetGameProjection -> GameProjection` and
  `GetPersistenceStatus -> PersistenceStatus` return canonical `ReadResult`s;
- every successful read returns the exact Game
  `ConsistencyStamp(ballInstanceId, commitRevision, stateSchemaVersion)` and
  creates no commit/output identity; the Game projection contains no
  Interaction FX under that stamp;
- `GameProjectionChanged` is a canonical `ProjectionOutput`;
- `AdvanceAudio`, `EnsureAudioUnlocked`, and `PersistProgress` are canonical
  `EffectRequest`s;
- progress outcomes are causally bound `ProgressPersisted` or
  `ProgressPersistenceOutcomeUnknown` Facts;
- every output uses the complete
  `SemanticHandle(operationId, outputKind, localOrdinalOrName)` and a contiguous
  zero-based `sourceOrdinal`.

Replies, Signals, ControlPulses, Timers, Commands, and imported contracts are
empty for this version. Validation/admission/decision rejection remains a
pre-acceptance boundary result.

## State classification split

Sovereign Game state includes run phase, rewards, permanent progression,
settings, pointer/viewport/brake values needed later, accumulator and simulation
timers, gameplay entities and IDs, gameplay RNG cursor, the outstanding progress
handle, and the latest transient persistence status.

Interaction-ephemeral state includes focus, text measurement, platform event
objects, interpolation clock, pointer gesture edge, visual RNG, particles,
motion echoes, shockwaves, damage-number animation, and decorative arcs. The
Nucleus emits bounded `VisualFxCue`s but never observes the reducer's resulting
state. `GameProjection` contains no visual FX fields; the separate
`VisualFxProjection` attachment is Interaction-owned and unstamped. Enemies, projectiles,
pickups, gameplay trail, weapon nodes/orbitals, and
the Totem remain Sovereign because later decisions consume them.

Projection collections and accepted output batches now use defensively owned
`ImmutableList`/`ImmutableSet` containers. A Kotlin read-only `List` view over
shared mutable backing storage is not used as the publication guarantee.

Visual cue construction has an explicit 2048-item bound: 2047 retained cues and
one `VisualCuesDropped(count)` metadata slot. Synchronization-critical clear,
motion, advance, and rebase cues first displace the oldest decorative cue; a
defensive bypass fallback displaces the oldest visual cue. Loss is deterministic,
visible, and cannot reject/change gameplay.

## Runtime and completion migration

`InlineAcceptedFrameRuntime` performs ingress preflight, pure decision,
complete-frame preflight, single-reference `AcceptedFrame` publication, then
dispatch. It rejects reentrant submission and contains no mailbox or worker.

`GameFeatureBall` owns exactly one fixed synchronous completion slot for the only
Fact-producing Effect, `PersistProgress`. Before source acceptance, preflight
reserves that slot and causal depth 2. The Resource result is quarantined after
publication and drained in the next Inline iteration with the persist
`OperationId`; an already accepted completion is retained until processed and is
not silently discarded. It is cleared only after its own commit; otherwise
`GameContinuationStatus.Retained` exposes the exact handle/scope/failure and new
roots receive `AdmissionFailure.CausalBudgetExceeded` until resume succeeds.
Audio and live visual projection create no Facts.

## Required stages and evidence state

| Stage | Exit condition | Current status |
|---|---|---|
| 0. Freeze and inventory | Source/Core/Agent Pack hashes and behavior baseline recorded | complete |
| 1. Contract | Accepted overlay, authority map, closed manifest, five selected profile dimensions, limits, and non-claims agree | complete; consistency review passed locally |
| 2. Pure Nucleus | Explicit State/Pulse/Context, deterministic RNG, exact result algebra, no I/O/platform path | complete; tests and source scans passed locally |
| 3. Atomic runtime | Complete-frame preflight, publication before dispatch, bounded completion reservation, reentrancy rejection | complete; integrated desktop evidence passed |
| 4. Projection/Interaction | Immutable render payload; raw ingress quarantine; cosmetic FX owned by Interaction | complete; integrated desktop evidence passed |
| 5. Resources/Assembly | Exact-key progress and numeric-tone capabilities explicitly constructed and quarantined | complete; desktop/Wasm compilation and Resource tests passed |
| 6. Foundation | Only mechanical immutable-collection/PRNG primitives exported | complete for the recorded static scan and tests |
| 7. Verification | Behavior, transition, fault, security, and limit suites pass on supported compilable targets | complete locally: 150/150 desktop tests, Wasm test compilation, assembly, and browser distribution; browser execution blocked by missing Chrome launcher |
| 8. Gate review | `RG-01..10` have no `partial` or `fail` within scope | open: formal immutable-baseline review and the `RG-09` state-byte-meter qualification remain |

## Compatibility requirements

- Preserve the 120 Hz fixed-step order and 48-step per-Decision cap.
- Preserve seeded gameplay determinism for the same validated bootstrap and
  Intent sequence; visual RNG is intentionally separate and non-authoritative.
- Preserve behavior covered by the 92-test desktop baseline unless an
  intentional change receives a separate accepted decision.
- Preserve JVM Preferences node `kinetickk/progression`, keys `progress_v2` and
  `kinetickk_matter`; preserve browser localStorage keys
  `kinetickk_progress_v2` and `kinetickk_matter`.
- Preserve supported progress codec v3/v2 handling; malformed, invalid-UTF-8,
  or over-65536-byte input is quarantined.
- Keep desktop and browser hosts as bindings of the same common Game contract.
- Preserve complete-frame acceptance: overflow rejects/backpressures before
  acceptance and never dispatches a partial non-drop-eligible batch.
- Preserve visual caps: particles 700, motion echoes 36, shockwaves 48, damage
  numbers 140, and decorative arcs 128; preserve the 2048-cue batch with its
  reserved visual-drop metadata slot.

## State schema and external data

Game state schema `1` is the first Pokeball-owned in-memory schema, so no prior
Game Ball snapshot exists to migrate. External progress text is captured input,
not authoritative Ball state. The progress Resource parses supported legacy data
into validated `StoredProgress`; unsupported/malformed/over-limit text never
enters the Nucleus.

Any later retained state-shape change requires a `stateSchemaVersion` increment,
an explicit migration/quarantine path, and updated overlay/manifest/tests.

## Legacy removal and final checks

The rewritten production paths live under `application/`, `features/game/`, and
`foundation/`; baseline `model`, `audio`, and `ui` production paths are removed
on the rewrite branch. The recorded local closure searches prove:

- no production renderer or host constructs/reads the former mutable engine;
- the Nucleus imports no platform, I/O, clock, or ambient random API;
- Resources cannot mutate Game state directly;
- every used protocol variant appears exactly once in the manifest;
- both Queries resolve one payload and carry the committed Game stamp;
- the final source/test tree and documentation pass link, YAML, diff, and build
  validation recorded in `docs/pokeball-conformance.md`.

Those working-tree results do not substitute for the still-open formal
immutable-baseline release review.
