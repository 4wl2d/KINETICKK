# Pokeball Project Overlay — KINETICKK

> **Status:** accepted project overlay, revision 1. This document concretizes
> choices left open by Pokeball Core. It does not override the Core or define an
> extension. The rewrite implementation is present on the working branch; final
> verification remains bounded by `docs/pokeball-conformance.md`.
>
> Pokeball Architecture and its overlay template are by Vladislav Tomilov
> (4wl2d), CC BY 4.0. This KINETICKK adaptation does not change the software
> license in the repository root.

## 1. Metadata

| Field | Value |
|---|---|
| Project | `KINETICKK` |
| Overlay revision | `1` |
| Status | `accepted` design; implementation locally verified; formal release/conformance review remains partial |
| Owner | `Vladislav Tomilov (4wl2d)` |
| Accepted by | `Vladislav Tomilov (4wl2d), by the direct 2026-07-16 full-rewrite goal` |
| Accepted at | `2026-07-16` |
| Scope | This repository's shared game, desktop application, and WebAssembly application |
| Core baseline | `spec/pokeball-architecture-core.md`; Agent Pack baseline `READY`; SHA-256 `b16d26dbd1f8763740c48fb4de8f4a5728c0ae94094592c3b71ef72a46138052`; 211011 bytes |
| Accepted extensions | `not-applicable: no Pokeball extension specification is selected` |
| Supersedes | `not-applicable: first overlay` |

## 2. Conformance scope

Included: the singleton `Game` Feature Ball; its Interaction, Nucleus, private
audio/progress Resources, caller-confined Inline runtime, static Assembly,
desktop/WebAssembly bindings, manifest, migration artifacts, and tests.

Excluded: packaging/notarization, distribution infrastructure, browser-launcher
availability, legal documents outside the imported Pokeball material, and every
guarantee requiring an unselected Pokeball extension.

### Claims

| Claim ID | Boundary | Mechanism | Assumptions | Evidence | Owner |
|---|---|---|---|---|---|
| `KIN-PKB-C01` | One local `Game` instance | `InlineAcceptedFrameRuntime` publishes one complete immutable `AcceptedFrame` before dispatch; `GameFeatureBall` reserves one synchronous completion slot | Trusted caller-confined in-process host; no hostile code | `InlineAcceptedFrameRuntimeTest`, `GameFeatureBallArchitectureTest` | Project owner |
| `KIN-PKB-C02` | Game decisions | `GameNucleus.decide(State, Pulse, GameDecisionContext)` clones accepted state, consumes explicit operation/RNG context, and returns a bounded exact result algebra | Same artifact, state, Pulse, and Context | `GameFeatureBallArchitectureTest`, `CloneableXorWowRandomTest`, `BoundedVisualFxCueAccumulatorTest`, Nucleus characterization suites | Project owner |
| `KIN-PKB-C03` | Interaction and Resource paths | Two quarantines plus exact-key progress and bounded numeric-tone capabilities | Platform APIs honor their documented process boundary | `GameInteractionValidationTest`, `ProgressStoreTest`, `GameAudioTest`, `DesktopAudioExecutionPolicyTest` | Project owner |

These rows name implementation evidence. Final test execution and gate closure
are recorded separately; the presence of a test class is not a pass result.

### Explicit non-claims

| Boundary | Not claimed | Reason |
|---|---|---|
| Process/browser crash | Durable state, durable output, durable status, zero-RPO recovery | State/status profile is `Transient`; Preferences/localStorage writes are best-effort effects, not `SnapshotOutbox` |
| Audio/progress effects | Exactly-once, at-least-once, eventual delivery | One bounded local attempt, no retry, and no durable dispatcher |
| Live projection/visual FX | Lossless delivery or replay | `ProjectionOutput` is live; visual cues may be dropped and semantic state is resynchronized by a stamped Query |
| In-process code | Hostile-component isolation or general hardened security | Selected profiles are `InProcess + Standard` |
| Runtime performance | Zero allocation or zero overhead | Immutable snapshots and decision copies allocate; no benchmarked claim exists |
| Rendering | Atomic multi-source read or an FX consistency stamp | The canonical Query contains only the Game snapshot; Interaction attaches its ephemeral FX snapshot after the read |

## 3. Deviations

`none accepted`

## 4. Ball inventory

### Ball: `Game`

#### Responsibility and boundary

| Field | Value |
|---|---|
| Kind | `FeatureBall` |
| Responsibility | Own the local player's run simulation, permanent progression decisions, settings, semantic screen state, and latest progress-persistence status |
| Non-goals | Platform event decoding, drawing, visual FX evolution, audio synthesis, storage encoding/I/O, distribution, analytics, or a general workflow engine |
| Protocol version | `1.0.0` |
| State schema version | `1` |
| Namespace | `kinetickk.local` |
| StateKey type/value rule | `LocalPlayerId`; current value `local-player` |
| Full logical BallInstanceId | `kinetickk.local/Game/local-player` |
| Lifecycle/recovery owner | `InlineAcceptedFrameRuntime`; Assembly quarantines one bounded bootstrap progress snapshot before revision 0 |
| Trust/isolation boundary | Trusted in-process application; raw platform input and raw Resource results cross separate typed validation boundaries |

#### Authority and state

| Mutable semantic fact | Authority | Writer/fencing | State kind | Retention/recovery |
|---|---|---|---|---|
| Run phase, rewards, physics/combat entities, weapon/relic state, timers, and gameplay RNG | `Game` | One caller-confined Inline writer; no movable ownership | Sovereign | Process lifetime only |
| Matter, unlocks, upgrades, settings, Rebirth, and Codex discovery | `Game` while running | Same writer | Sovereign | Process lifetime; complete snapshots are sent best-effort to progress Resource |
| Outstanding progress handle and latest persistence status | `Game` | Same accepted frame and exact matching Fact | Sovereign | Transient; process loss discards status |
| Validated bootstrap progress | Exact-key progress Resource, captured by Assembly | Assembly validates once before initial state | Captured Input | Normalized into revision 0; raw text is discarded |
| Canonical `GameProjection` read | No command authority | Pure mapping from one committed Game snapshot; the type contains no Interaction FX fields | Projection | Structurally immutable; the Game stamp covers the complete Query payload |
| Render-time visual attachment | `InteractionFxReducer` | Interaction applies `GameDispatchResult.Committed.visualFxCues` and joins its current snapshot after the stamped read | Ephemeral presentation | Unstamped, finite, replaceable, and not a Game Query result |
| Focus, pointer gesture edge, text measurer, interpolation clock | Interaction/Resource owner | Owning adapter | Ephemeral/Runtime | May be lost without changing Game authority |
| Particles, motion echoes, shockwaves, damage-number animation, decorative arcs, and visual RNG | `InteractionFxReducer` | Interaction-side single owner | Ephemeral | Finite, drop-eligible, and never read by a Game decision |

Strict invariants: a fixed step preserves the established 120 Hz order; banking
a run and updating permanent matter are one decision; pending rewards and choice
sets agree; gameplay entity identity is unique within the instance; gameplay RNG
advances only in an accepted decision; visual RNG never feeds the Nucleus; no
Resource I/O occurs in the Nucleus; each successful Query is derived from the
committed snapshot named by its `ConsistencyStamp`.

#### Cross-transition decision values

| Value/output fields | Origin | Correlation, version, and provenance | First atomic assignment | Later consumer and retention |
|---|---|---|---|---|
| Bootstrap progress | Assembly Resource quarantine | Fixed provider/key; codec v3 or supported v2; protocol 1.0.0 | Initial Game frame | Normalized sovereign fields; raw text discarded |
| Pointer/viewport/frame/brake data | Interaction quarantine | Closed typed Intent after finite/range/size validation | Intent decision | Retained only where later simulation/hit testing needs it; replaced by newer accepted Intent |
| Gameplay RNG cursor | Initial seed and accepted Game state | `CloneableXorWowRandom`, transition artifact `game-v1` | Revision 0, then each consuming decision | Spawn/loot/combat only; copied with each decision candidate |
| Visual FX cue batch and visual RNG | Accepted `GameProjectionChanged`; Interaction seed | Cue batch has the source operation/ordinal; visual RNG is private to `InteractionFxReducer` | Cue batch is in the complete Decision; Interaction receives it separately from the stamped Query | Rendering only; 2048-cue batch with explicit visual-drop metadata; never reintroduced into Game decisions |
| Progress generation, snapshot, and handle | Decision that changes persistent fields | `SemanticHandle(OperationId, PERSIST_PROGRESS, generation-N)` plus zero-based `sourceOrdinal`; provider `PLATFORM_LOCAL` | Snapshot, outstanding handle, `Pending`, and `EffectRequest` share one accepted frame | Exact matching progress Fact refines status; stale/provider-mismatched Fact rejects without mutation |
| Query payload | `CommittedStateSnapshot` | `ReadContext(protocolVersion=1.0.0)` and exact Game `ConsistencyStamp` | No assignment or commit | Caller receives `ReadResult`; no output identity is created |

#### Owned protocol

| Category | Variants or mappings | Origin/validation | Bound/identity |
|---|---|---|---|
| Intents | `FrameElapsed`, `ViewportChanged`, `PointerMoved`, `PointerPressed`, `PointerReleased`, `BrakeChanged`, `DashRequested`, `PauseToggled`, `EscapeRequested`, `ScreenOpenRequested`, `MuteToggled`, `ChoiceSelected`, `ChoicesRerolled`, `EnterPressed`, `RunStartRequested`, `ReturnToMenuRequested`, `RebirthRequested`, `CoreShapeSelected`, `MetaUpgradePurchaseRequested`, `WeaponPurchaseOrEquipRequested`, `UserGestureObserved` | Compose/platform Interaction; raw numeric values pass finite/range quarantine before Intent creation; key/pointer actions map to closed variants | `maxInputBytes`; no input collection |
| Queries | `GetGameProjection -> GameProjection`; `GetPersistenceStatus -> PersistenceStatus` | Local pure read over `CommittedStateSnapshot`; Game projection excludes Interaction FX | Canonical `ReadResult` with `ConsistencyStamp(ballInstanceId, commitRevision, stateSchemaVersion)`; no commit/output identity |
| Projection payload | `GameProjectionChanged(visualFxCues)` | Nucleus only | Canonical `ProjectionOutput`; immutable cue collection; ordinal 0 in current builder |
| Effect payloads | `AdvanceAudio`, `EnsureAudioUnlocked`, `PersistProgress` | Nucleus only; private capability bindings | Canonical `EffectRequest`; complete `SemanticHandle`; contiguous `sourceOrdinal` |
| Facts | `ProgressPersisted`, `ProgressPersistenceOutcomeUnknown` | Resource quarantine; exact handle, `PLATFORM_LOCAL` provider, and closed reason | One reserved synchronous completion slot; causal depth 2 |
| Replies, Signals, ControlPulses, Timers | `empty by protocol version` | not-applicable | 0 |

The only owned semantic-output envelopes are canonical `ProjectionOutput` and
`EffectRequest`. Every envelope contains
`SemanticHandle(operationId, outputKind, localOrdinalOrName)` and a unique
zero-based `sourceOrdinal` covering `0..outputs.size-1`. Pre-acceptance parsing,
validation, admission, and decision failures are boundary responses, never
committed Replies. There are no imported contracts or inter-Ball dependencies.

#### Selected profiles

| Dimension | Choice | Mechanism | Explicit non-guarantee | Evidence artifact |
|---|---|---|---|---|
| Execution | `Inline` | Caller-confined run-to-completion, reentrancy guard, and one fixed reserved completion slot | No Ball mailbox, Ball worker, concurrent fairness, or parallel decision claim | `InlineAcceptedFrameRuntimeTest`, `GameFeatureBallArchitectureTest` |
| State | `Transient` | One atomically replaced in-memory `AcceptedFrame` | No crash durability or durable dispatch/status | Publication/fault cases in the same tests |
| Isolation | `InProcess` | Explicit construction and dependency/source boundaries | No hostile containment | Architecture/import review |
| Security | `Standard` | Double Quarantine, scoped capabilities, exact-key/numeric safe sinks | No general `Hardened` claim | Interaction and Resource tests |
| Composition | `Static` | `GameAssembly` directly constructs the one Ball and its private capabilities | No dynamic registry or runtime route discovery | `GameAssembly.kt`, architecture review |

#### Artifact paths

| Artifact | Path/ID |
|---|---|
| Manifest | `architecture/game/ball.yaml` |
| Protocol | `src/commonMain/kotlin/kinetickk/features/game/nucleus/protocol/GameProtocol.kt` |
| Canonical read values | `src/commonMain/kotlin/kinetickk/features/game/nucleus/read/GameRead.kt` |
| State/transition/projection | `src/commonMain/kotlin/kinetickk/features/game/nucleus/{state,transition,projection}/` |
| Interaction quarantine/FX | `src/commonMain/kotlin/kinetickk/features/game/interaction/{validation,fx}/` |
| Resources | `src/commonMain/kotlin/kinetickk/features/game/resources/` plus platform actuals |
| Assembly/runtime | `src/commonMain/kotlin/kinetickk/application/{assembly,runtime}/` |
| Migration/quarantine | `docs/pokeball-migration.md`; Game state schema 1 has no prior Ball snapshot |
| Evidence | `src/commonTest/kotlin/kinetickk/` and `src/desktopTest/kotlin/kinetickk/` |

## 5. Persistence status authority

`PersistenceStatus` is owned by the same singleton `Game` authority; there is no
separate status Ball or external status ledger. Its closed variants are
`NeverRequested`, `Pending(handle)`, `Persisted(handle)`, and
`OutcomeUnknown(handle, typedReason)`. `GetPersistenceStatus` returns a
`ReadResult<PersistenceStatus>` stamped with the exact committed Game snapshot.

This status is `Transient`, latest-operation only, and process-local. It does not
claim durable observation, reconciliation, exactly-once execution, delivery
facets, cancellation facets, or a retained history. The normal progress call is
synchronous: before accepting `PersistProgress`, preflight reserves the sole
completion slot and causal depth for the matching Fact. The Fact runs at depth 2
after source acceptance; an unavailable reservation rejects the source Decision,
and an already accepted completion is retained rather than dropped.
If its Fact does not commit, `GameContinuationStatus.Retained` preserves the
exact handle, causal scope, and typed last failure; a new root receives
`AdmissionFailure.CausalBudgetExceeded` until the same continuation resumes.

## 6. Assembly, delivery, and Resources

There are no inter-Ball routes. Static `GameAssembly` directly binds:

| Binding/output | Target | Delivery and bounds | Retry/unknown | Security |
|---|---|---|---|---|
| `game-audio-v1`: `AdvanceAudio`, `EnsureAudioUnlocked` | `GameAudioResource` and platform `NumericTonePlayer` | One post-acceptance Resource invocation; 32 cues accepted and at most 3 selected per advance | No retry/status; audio is best-effort and drop-eligible | Finite frequency/duration/gain/wave; no arbitrary media/file/network access |
| `game-progress-v1`: `PersistProgress` | `ProgressStore` | One post-acceptance call; one pre-reserved completion slot | No retry; possible write failure maps to typed `ProgressPersistenceOutcomeUnknown` | Fixed provider, namespace, keys, codec, and 65536-byte UTF-8 limit |
| `GameProjectionChanged` | `GameAssembly`-owned `InteractionFxReducer` | Returned as `GameDispatchResult.Committed.visualFxCues` after accepted-frame publication; Interaction applies cues and passes a separate `VisualFxProjection` to the renderer | Live delivery; visual loss/resync is permitted | Immutable bounded cues; no Interaction state appears under the Game Query stamp |

The desktop audio Resource, not the Ball runtime, owns one daemon worker and an
`ArrayBlockingQueue` of 24 tone tasks with `DiscardOldestPolicy`. That policy is
permitted only for best-effort audio. WebAssembly calls the validated numeric Web
Audio sink directly. Neither platform path creates a Ball mailbox or Ball worker.

## 7. Limits

### Ball limits: `Game`

| Mandatory limit | Value | Enforcement/evidence source |
|---|---:|---|
| `maxInputBytes` | 4096 bytes | Interaction/input estimator; `GameInteractionValidationTest`, `InlineAcceptedFrameRuntimeTest` |
| `maxStateBytes` | 16777216 bytes | State estimator before acceptance; `InlineAcceptedFrameRuntimeTest` |
| `maxCollectionItems` | 2048 items per collection | State/output collection scan; `ImmutableCollectionsTest`, `BoundedVisualFxCueAccumulatorTest` |
| `maxOutputsPerDecision` | 3 envelopes | Output builder and preflight |
| `maxEffectsPerDecision` | 2 `EffectRequest`s | Closed envelope preflight |
| `maxCommandsPerDecision` | 0 | Protocol has no `ModuleCommandRequest` |
| `maxCausalDepth` | 2 mutating hops | Root Intent depth 1; progress Fact depth 2; fixed completion reservation |
| `maxRetriesPerOperation` | 0 retries | Resource executor performs one initial attempt |
| `maxTransitionSteps` | 48 fixed simulation steps | Accepted-state step meter and preflight |

`InlineAcceptedFrameRuntimeTest` contains generic exact `N/N+1` checks for all
nine meters and project-specific additional limits. `GameFeatureBallArchitectureTest`
cross-checks each declared Sovereign collection cap at `N/N+1`; capacity-aware
domain construction prevents normal transitions from proposing overflow and
preflight fails closed on a violating candidate. `RG-09` remains partial for the
recorded state-byte-meter qualification and immutable-baseline review.

Additional finite caps: audio cues `32` accepted/`3` selected; progress payload
`65536` UTF-8 bytes; enemies `120`; projectiles `650`; pickups `420`; trail
points `110`; delayed Relic hits `256`; visual cues `2048` (2047 retained cues
plus one `VisualCuesDropped` metadata slot); particles `700`; motion echoes `36`; shockwaves `48`;
damage numbers `140`; decorative arcs `128`; accepted Resource attempts `1`.
Desktop audio alone has `1` daemon worker and queue capacity `24` with
discard-oldest backpressure. No Ball mailbox, Ball worker, fan-out, participant,
inter-Ball route, compensation, subscription, network, file, or economic quota
exists.

## 8. Async and live-delivery policies

| Operation | Identity/mismatch | Retry owner/horizon | Unknown/retention policy |
|---|---|---|---|
| Progress persistence | Reserved `OperationId` + `SemanticHandle(PERSIST_PROGRESS, generation-N)` + source ordinal; exact provider/handle Fact only | One Resource call; zero retry | Possible write maps to typed `OutcomeUnknown`; status and completion slot are process-local |
| Audio | Complete output handle/ordinal | One Resource invocation; zero retry | No Fact/status; tone tasks may be discarded under the declared desktop audio policy |
| Visual projection | Complete `GAME_PROJECTION_CHANGED` handle/ordinal | No retry/replay | Sync-critical cues first displace the oldest decorative cue; defensive bypass overflow displaces the oldest visual cue; all loss is counted by `VisualCuesDropped`; live delivery remains drop/resync |

Frame clock observations are validated `FrameElapsed` Intents, not
`TimerRequest`s. No ambient clock enters the Nucleus.

## 9. Security

| Boundary/path | Quarantine, provenance, and safe sink | Actor/grant | Secret/unsafe policy |
|---|---|---|---|
| Raw Compose/platform input -> Game | `GameInteractionValidator` checks finite frame/viewport/pointer values and inclusive ranges before constructing numeric Intents; event mapping uses closed variants | Local anonymous player; no privileged operation | Invalid raw input returns a pre-acceptance validation result and creates no Pulse |
| Stored text/value -> bootstrap | Fixed provider identity/key, UTF-8 validity, <=65536 bytes, closed codec versions/ranges, then Assembly rebuilds one normalized bounded `StoredProgress` | not-applicable | Malformed/oversized/non-finite or over-cap input is quarantined; the raw text/object never enters Game state |
| Progress result -> Fact | Adapter maps only `PLATFORM_LOCAL`, exact source handle, and closed reason enums; Nucleus rechecks provider and handle | not-applicable | Raw exceptions are contained and never enter a Fact/status |
| Progress effect -> platform | Typed `StoredProgress`, fixed Preferences/localStorage namespace and keys | not-applicable | No arbitrary key/path/query; no unsafe waiver |
| Audio effect -> platform | Finite bounded tone fields and enumerated waveform | not-applicable | No URL/file/device selection; no unsafe waiver |

No grant, credential, PII, secret, external network, raw SQL, shell, arbitrary
URL, filesystem path, or hostile plugin path exists in the covered application.

## 10. Foundation policy

| Field | Value |
|---|---|
| Policy | `KIN-FOUNDATION-1`, owner Vladislav Tomilov, 2026-07-16 |
| Paths | `src/commonMain/kotlin/kinetickk/foundation/` |
| Allowed current exports | Defensive immutable collections and explicit-seed snapshot/copy PRNG mechanics |
| Forbidden | Game entities, progression/settings, catalogs, protocol lifecycle, mutable business state, Resource authority |
| Scan artifact | `docs/pokeball-foundation-scan.md` |
| Exceptions | `none` |
| Current result | Mechanical-only static scan and the two Foundation test suites passed for the inspected working tree; formal gate scope remains bounded by the conformance record |

## 11. Evidence register and gate state

| Evidence ID | Rules/gates | Artifact | Baseline/environment | Current result |
|---|---|---|---|---|
| `KIN-E01` | `GOV-001`, `RG-10`, `AP-GATE-02` | Core/package exact integrity | Pokeball `dfffab3`; KINETICKK baseline `b2720ff` | pass |
| `KIN-E02` | Behavior baseline | Baseline `assemble`, `desktopTest`, `wasmJsTestClasses` | JDK 21, Gradle 8.14.3, macOS arm64 | pass for pre-rewrite baseline only: 92 desktop tests |
| `KIN-E03` | `RG-02..09` | Rewritten source plus named common/desktop test classes | JDK 21, Gradle 8.14.3, macOS arm64 working tree | local integrated pass: 150/150 desktop tests, Wasm test compilation, assembly, and browser distribution |
| `KIN-E04` | Browser runtime | `wasmJsBrowserTest` | local machine | partial: test executable compiled, but ChromeHeadless could not start because Chrome/`CHROME_BIN` was unavailable |
| `KIN-E05` | `CMP-004`, Foundation portion of `RG-06/10` | export/import/platform static scan plus Foundation tests | working tree inspected 2026-07-16 | pass for the recorded scan and desktop test execution |

Current gate result: `RG-01` scope/design pass; the local integrated
implementation, test, link, manifest, and evidence review completed. The formal
record still keeps `RG-02..10` partial on the uncommitted moving baseline, and
`RG-09` additionally retains the state-byte-meter qualification. The `RG-10`
Core/Agent-Pack integrity sub-check is pass; that does not close the whole gate.
No project conformance or release claim is made while any gate is partial.

## 12. Acceptance

Decision: `accepted` as the project architecture/design overlay.

| Acceptance field | Value |
|---|---|
| Accepted overlay revision | `1` |
| Accepted by | `Vladislav Tomilov (4wl2d), via the direct full-rewrite goal` |
| Date | `2026-07-16` |
| Review/evidence ID | `KIN-E01` |
| Remaining partial scope | Final integrated implementation evidence and `RG-02..10` |
