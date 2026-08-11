<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK local Pokeball policy

**Policy ID:** `kinetickk-local-standard`

**Revision:** `1`

**Owner:** KINETICKK project

**Scope:** the four authorities and the Desktop/Web in-process bindings named
in `authority-map.md`

<!-- pokeball-audit-policy
profileAuthorities=ContentCatalog|Profile|GameplayRun|AppSession
effectiveProfile=Inline+Transient+InProcess+Standard+Static
contentMutationPath=NONE
semanticRetry=PRESENT
semanticRetryAnchor=Core §9.9 / PBA-24
semanticRetryFamilies=legacy-purge|reset-write
semanticRetryPrimaryOwner=AppSession
semanticRetryTarget=Profile
semanticRetryAttemptsPerPulse=1
semanticRetryDisabledLayers=transport|executor|SDK/provider|reconciliation
semanticRetrySameIdentityResend=DISABLED
semanticRetryLegacyPurgePulse=SessionInteractionPulse.ResetRetryRequested
semanticRetryLegacyPurgeCommand=ProfileModuleCommand.RetryLegacyPurge
semanticRetryLegacyPurgeEvidence=flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt|ball/profile/nucleus/src/commonTest/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleusTest.kt|ball/profile/impl/src/commonTest/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponentTest.kt
semanticRetryResetWritePulse=SessionInteractionPulse.ResetConfirmed
semanticRetryResetWriteCommand=ProfileModuleCommand.ConfirmLegacyReset
semanticRetryResetWriteFailureResults=ProfileModuleResult.ResetWriteRejected|ProfileModuleResult.ResetWriteResourceFailure|ProfileModuleResult.ResetWriteOutcomeUnknown
semanticRetryResetWriteReturnLifecycle=SessionResetLifecycle.CONFIRMATION_REQUIRED
semanticRetryResetWriteFreshIdentity=semanticHandle|effectRef|sourceRevision
semanticRetryResetWriteResourceInvocationsPerPulse=1
semanticRetryResetWriteProviderMutationCallsPerPulse=0..1
semanticRetryResetWriteEvidence=flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt|ball/profile/impl/src/commonTest/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponentTest.kt|ball/profile/nucleus/src/commonTest/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleusTest.kt|ball/profile/resource/src/commonTest/kotlin/kinetickk/ball/profile/resource/ProfileStorageTest.kt
-->

This document selects only values and mechanisms that Pokeball Core leaves to
the project. Core remains authoritative. Typed protocols and bounds in Kotlin
remain authoritative where they are more specific. The final claim record pins
the exact byte digest of this policy and its Assembly.

## Effective profiles

ContentCatalog, Profile, GameplayRun, and AppSession select:

```text
Execution: Inline
State: Transient
Isolation: InProcess
Security: Standard
Binding: Static
```

They therefore use `SnapshotDecisionResult<State>` and a flattened accepted
snapshot frame. Persistence of Profile snapshots does not select
`SnapshotOutbox`: accepted State is authoritative in process, persistence can
return `OutcomeUnknown`, and the project claims no durable output or replay
guarantee. ContentCatalog is immutable/query-only after bootstrap and has no
runtime mutation path; that absence does not remove it from explicit
effective-profile resolution.

The four-authority Inline binding adds no semantic actor, mailbox, worker pool,
reflection registry, service locator, coroutine queue, transport serialization,
or thread hop to authority Decision, command, result, output, or Fact delivery.
`InlineDispatchGuard` and a bounded synchronous completion deque are mechanical
Foundation only. The App-owned Desktop Audio projection broker is separate
mechanical machinery with one bounded worker and a 24-task queue; it carries no
semantic Pulse, command, result, output, or Fact and does not change the
four-authority execution profile.

Impl-owned `ProfileComponent` and `GameplayCompositionComponent` are
Assembly-only composite construction handles. Session receives only
`SessionProfileRoute` and `GameplaySessionHost`; Gameplay receives only
`GameplayProfileRoute`; presentation receives only `GameplayPresentation` and
query ports. The verifier closes those type-use locations and also limits each
Nucleus ModuleResult/carrier constructor or factory to its corresponding
trusted Impl boundary.

## Decision and composition bounds

| Dimension | Effective bound | Enforcement owner |
|---|---:|---|
| Profile semantic outputs per accepted Decision | 2 | Profile acceptor preflight; whole Decision rejected/faulted before publication on overflow |
| Gameplay semantic outputs per accepted Decision | 3 | Gameplay acceptor preflight |
| Session semantic outputs per accepted Decision | 3 | Session acceptor preflight |
| Profile Resource effects per accepted Decision | 1 | Profile acceptor preflight before synchronous Resource dispatch |
| Gameplay Profile-command outputs per accepted Decision | 1 | Gameplay acceptor preflight before Profile dispatch |
| Session participant-command / ensure-run outputs per accepted Decision | 1 / 1 | Session accepted-frame and acceptor preflight |
| Session participant commands at one time | 1 | Session State/Nucleus plus acceptor |
| Session participant authorities | 2 | static `FlowParticipation`/Assembly validation |
| cross-authority read / command-result routes | 14 / 10 | closed typed Assembly inventory and generated projection; AppSession owns 9 of the command/result routes |
| rendered application routes | 7 | closed AppSession route protocol and Interaction mapping |
| same-stack causal depth | 8 | three acceptor depth guards plus the source-derived acyclic direct-control graph |
| cumulative fan-out per accepted root causal scope | 9840 | static closed-output/executor inventory and geometric proof; no runtime meter |
| Profile / Gameplay / Session completion deque capacity | 8 / 8 / 8 | three separately anchored bounded completion deques |
| active GameplayRun instances | 1 | Session allocation/lifecycle invariant |
| Gameplay fixed steps per render frame | 48 | Gameplay simulation loop |
| Gameplay simulation raw-delta / accumulator cap seconds | `0.1` / `0.3` | production GameLoop clamps before and during accumulator admission |
| enemies / projectiles / pickups / trail | 120 / 650 / 420 / 110 | captured Content enemy policy plus explicit Gameplay insertion/rollover limits |
| delayed Relic hits | 256 | Gameplay checks capacity before enqueue |
| Relic chain work / visited IDs | 5 / 6 | captured Relic rank `5`; Gameplay rejects the sixth iteration atomically before mutation |
| projectile hit-history IDs | 120 | projectile-local bounded identity set, reclaimed against live enemies |
| Gameplay sound cues / weapon nodes / orbitals / choices | 32 / 8 / 8 / 4 | Gameplay-owned bounded insertion or atomic validation |
| Arc Coil targets / generated item, weapon, or Relic reward choices | 6 / 3 | deployed weapon and three reward-generator paths |
| Gameplay trail samples per update | 32 | bounded per-update sampling loop |
| visual-FX cues per projection | 2048 | Gameplay bounded accumulator |
| Interaction particles / motion echoes / shockwaves | 700 / 36 / 48 | Interaction-ephemeral bounded reducers |
| Interaction damage numbers / weapon arcs | 140 / 128 | Interaction-ephemeral bounded reducers |
| Interaction frame delta seconds | `0..1` | Interaction ingress quarantine before Gameplay Pulse construction |
| Interaction viewport pixels / density | `1..32768` / `0.5..8` | Interaction ingress quarantine before Gameplay Pulse construction |
| Interaction pointer representation / choice index | finite / `0..3` | Interaction rejects non-finite pointer coordinates; target-owned Gameplay API factories close choice admission |
| authoritative Gameplay frame delta seconds | `0..1` | target-owned Gameplay API factory validates the fixed range before the Nucleus can receive the Pulse |
| authoritative Gameplay viewport pixels / density | `1..32768` / `0.5..8` | target-owned Gameplay API factory validates both dimensions and density before the Nucleus can receive the Pulse |
| authoritative Gameplay pointer | `0..current viewport` | Gameplay Nucleus validates both pointer coordinates against committed viewport state |
| Gameplay / Home / Armory presentation delta seconds | `0.1` / `0.1` / `0.1` | Interaction-owned presentation clocks clamp the first larger representable delta |
| Codex / Armory visible page slice | 10 / 3 | deployed draw paths select only the bounded current-page slice |
| accepted caller effect ToneRequests per advance | 32 | Audio Resource rejects an oversized caller batch atomically |
| caller effect ToneRequests selected per advance | 3 | Audio Resource preserves caller order/deduplication within the accepted batch |
| music clock advance delta seconds | `0.1` | Audio Resource clamps finite caller time before advancing its internal music clock |
| ToneRequest frequency Hz / duration seconds / gain | `20..20000` / `0.001..1` / `0..1` | Audio Application Surface value construction and Resource revalidation |
| Desktop audio workers / queued tasks | 1 / 24 | fixed executor and discard-oldest queue policy |
| Desktop synthesis samples / PCM bytes per tone | 22050 / 44100 | validated one-second maximum buffer shape |
| items / weapons / upgrades / relics | 400 / 12 / 8 / 40 | Content bootstrap validation |
| Rebirth level | `0..10` | Content typed policy plus Profile/Gameplay validation |
| equipped Relic slots / rank | 4 / `1..5` | captured Content Relic policy plus Gameplay retention/saturation |
| Profile retained Lab rank slots / each rank | captured upgrade count (at most 8) / `0..captured maxRanks` | Profile bootstrap compatibility plus policy-free Resource schema validation |
| Profile retained discoveries | captured `itemCount` (at most 400) | Profile bootstrap compatibility plus policy-free Resource schema validation |
| Profile master volume / text scale | `0..1` / `1..1.75` | Profile and Gameplay normalized compatibility plus Resource ingress validation |
| Profile simulation speed / damage-tier threshold | exact declared option sets | API declarations consumed by Profile Nucleus, Gameplay Nucleus, and Resource validation |
| Profile Gameplay discoveries per Pulse | captured `itemCount` (at most 400) | Profile validates count and every stable item ID before acceptance |
| Profile v4 UTF-8 payload | 65536 bytes | Profile Resource before decode and after encode |
| Desktop Preferences value length | 8192 UTF-16 code units | exact platform broker refuses 8193 before provider execution |
| Desktop Preferences key names admitted per exact node read | 64 | exact platform broker refuses 65 before project-owned membership iteration |

### Mechanically derived and schema-closed collections

These collections do not introduce a second admission authority. Their size is
inherited from a validated closed schema, a bounded source collection, or an
invariant-preserving private copy. The architecture verifier anchors both the
root derivation and every copy/materialization path listed here.

| Derived ID | Effective bound | Mechanical derivation |
|---|---:|---|
| `gameplay.item-indexed-state` | item arrays/discoveries <=400; family stacks <=20 | validated contiguous Item IDs; pending is a subset |
| `gameplay.weapon-indexed-state` | unlocked weapons/mutation counters <=12 | closed WeaponId bootstrap; invariant-preserving copy |
| `gameplay.meta-indexed-state` | rank slots <=8 | validated closed MetaUpgradeId catalog sizes the array |
| `gameplay.relic-indexed-state` | state arrays <=40; enemy relic cells <=120 x 3 x 40 | validated RelicId catalog times the enemy cap |
| `gameplay.collision-live-enemy-ids` | <=120 | filtered identity set from bounded enemies |
| `gameplay.reducer-copy-collections` | equal to bounded committed source cardinality | invariant-preserving private reduction clone |
| `profile.codec-temporary-collections` | unlocked <=12; ranks =8; discoveries <=400 | materialized after schema validation |
| `session.shell-entries` | 1..2 | one base plus one nullable overlay |
| `content.closed-ui-catalogs` | CoreShape 3 / WeaponMastery 4 | bootstrap requires exact stable order |
| `ui.catalog-backed-sources` | Codex items <=400; Armory weapons <=12; Lab upgrades <=8; mastery closed | validated immutable Content plus bounded Profile state |
| `audio.music-notes` | 8 | fixed literal array with modulo-only indexing |
| `foundation.immutable-set-copy` | list = input cardinality; set <= input cardinality | owned list copy; first-occurrence-only set copy |

Named boundary evidence exercises exact maxima and the first rejected, deferred,
trimmed, or retained-overflow case where the bound is executable. The causal
depth claim combines a static composition proof with exact guard evidence: the
verifier derives an acyclic direct-control graph from foreign Application-Surface
dependencies and production imports, while each deployed acceptor admits depths
zero through seven and refuses eight. Completion deque capacity is a separate
mechanical bound with its own deployed-deque evidence. Existing simulation
policy is explicit: enemy, projectile, pickup,
sound, node, choice, hit-history, and delayed-hit additions enforce their named
limits; trail and selected Interaction-ephemeral collections use their declared
rollover policy. Semantic output and participant-command batches are never
truncated or partially accepted. A mechanical render/audio projection may
deterministically select within its separate execution cap only after its owning
semantic frame has been accepted. The three-request audio selection limit applies
only to caller effect tones; the Resource's independent music sequencer may emit
one additional internal tone and is not counted in that caller-batch bound.
Profile retained-state and configuration evidence covers only constructible
numeric/list/set overflow and membership cases. `WeaponId`, `ParticleDensity`,
`DamageNumberSize`, and `DamageNumberFormat` are closed typed domains, so the
project does not invent an impossible typed N+1 value; exact maximum weapon-set
retention and rejection of unknown wire stable IDs remain separate schema
evidence rather than a numeric collection-overflow claim.

The selected composition property is `maxCumulativeFanout=9840`. Its scope is
one accepted root causal scope, with accepted causal depths `0..7`, at most three
semantic outputs from each accepted Decision, and exactly one effective
consumer/executor per output. The static ceiling is
`3^1 + 3^2 + ... + 3^8 = 9840`. A unit is one distinct branch from the complete
accepted source tuple through one effective route to one consumer/executor.
Terminal and all co-reachable branches count, including separate branches that
converge on one authority. Mutually exclusive alternatives reserve their
maximum. Exact retry/redelivery of the same tuple, route, and consumer adds
nothing; a new accepted source tuple does. Each independent accepted root starts
a fresh scope. No asynchronous semantic handoff exists. No runtime fan-out meter
is introduced: this is a static composition proof checked against the closed
output/executor inventory and resolver fixtures.

No numeric `maxTransitionSteps`, `maxStateBytes`, or
`maxOutputBytesPerDecision` claim is selected. Termination and semantic-output
bounds are established by static control-flow/type proofs and collection caps;
there is therefore no invented Decision Work Meter or unrelated byte-measure
artifact. The save payload limit is a raw storage-boundary input/encoded-output
measure, not a general Ball State-size claim.

## Persistence boundary

The v4 JSON boundary is strict:

- unknown or missing required fields fail;
- no leniency, coercion, special floats, `ignoreUnknownKeys`, or
  `@JsonIgnoreUnknownKeys`;
- all economy `Long` values use canonical validated decimal strings;
- stable IDs are explicit; maps become sorted record lists and collections are
  sorted; defaults are encoded;
- `schemaVersion=4`, profile ID `local-player`, and content version
  `kinetickk-content-1` are exact;
- representation and closed-type validation occur before a bootstrap Pulse;
  State/Context-dependent compatibility and reset decisions remain Profile
  Nucleus policy.

The 65536-byte v4 codec bound protects JSON representation ingress and encoded
output; it does not promise that every valid payload fits every physical
provider. Desktop Preferences separately admits at most 8192 UTF-16 code units.
Its exact read helper admits at most 64 key names returned from the private target
node before project-owned membership iteration. The JDK/provider necessarily
enumerates and allocates that returned key array before this project gate; that
external provider work is outside semantic-delivery scope and is not claimed to
be bounded here.
The exact broker classifies an 8193-unit value before invocation as
`FAILED_BEFORE_EXECUTION`, which becomes a known write `ResourceFailure`, not
`OutcomeUnknown`, and never rolls back the already accepted Profile frame.

Desktop writes only node `kinetickk/profile`, key `snapshot_v4`; Web writes only
`kinetickk_profile_v4`. Legacy discovery/purge is limited to Desktop node
`kinetickk/progression` keys `progress_v2` and `kinetickk_matter`, and Web keys
`kinetickk_progress_v2` and `kinetickk_matter`.

Reset ordering is write-default-v4, observe success, then purge only the exact
legacy keys. Failure or uncertainty preserves legacy data. Core §9.9 / PBA-24
applies to two explicit user-owned semantic retry families, both coordinated
only by AppSession and targeted at Profile.

For legacy-purge failure, one
`SessionInteractionPulse.ResetRetryRequested` issues exactly one
`ProfileModuleCommand.RetryLegacyPurge`, and Profile performs at most one purge
attempt for that accepted command.

For `ResetWriteRejected`, `ResetWriteResourceFailure`, or
`ResetWriteOutcomeUnknown`, the reset returns to
`SessionResetLifecycle.CONFIRMATION_REQUIRED` and emits no automatic command. A
later explicit `SessionInteractionPulse.ResetConfirmed` authorizes one
superseding write to the same fixed v4 key through exactly one new
`ProfileModuleCommand.ConfirmLegacyReset` and one Profile Resource write
invocation. A local encode rejection produces `ResetWriteRejected` before the
provider, so that invocation makes zero provider mutation calls; otherwise it
makes at most one provider mutation call. The
accepted command has a fresh semantic handle, effect reference, and source
revision; it is not same-identity redelivery. Rejection and ResourceFailure are
known not to have executed. OutcomeUnknown may have committed, and the later
confirmation does not falsely resolve that prior ambiguity; if it succeeds, its
newer snapshot is established before legacy purge. `ProfilePersistenceStatus`
describes the latest attempt.

Profile is not a co-primary retry-policy owner. Transport, executor,
SDK/provider, and reconciliation retries are disabled. Neither family is
automatic, blind, transparent, idempotent, or a reconciliation attempt. The
cumulative semantic attempt bound is one Profile Resource invocation per
explicit user Pulse, with the separate provider mutation bound above.

## Capability and failure policy

Resources receive the minimum explicit bounded capability: exact-key read,
write, and remove functions or bounded tone playback. No Ball receives ambient
Preferences, `localStorage`, filesystem, browser, or audio-provider authority.
The platform broker classifies only explicit provider failures into closed
technical read/mutation outcomes. A known failure before mutation stays
`ResourceFailure` (or a known-present legacy key for purge); possible execution
alone becomes `OutcomeUnknown`. Every unclassified exception, programming fault,
invariant fault, and allocation failure follows runtime-fault policy and is not
converted into provider evidence, a business rejection, or an accepted result.

Under Core §9.13 live mechanical Projection, Audio produces no typed Fact, result, or status.
Synchronous Audio Resource and platform calls propagate under runtime-fault policy. That rule
includes synchronous Web `AudioContext` invocation and graph construction calls. Desktop synthesis
runs inside a detached executor `Runnable`; a synthesis fault escapes that `Runnable` to the runtime.
This is a worker-runtime escape with no caller-propagation claim.

Web native `resume()` and `close()` Promise rejections are explicitly observed and consumed only by
`.catch(() => undefined)` as non-semantic post-acceptance mechanical projection loss. Those sinks do
not catch synchronous failures: synchronous JavaScript invocation and graph faults still propagate.

All rejection, validation, admission, resource-result, and participant-result
reasons are closed variants. Open field/reason strings are prohibited.

## UI policy

Compose render models are immutable. Page, focus, viewport, gestures, animation
clock, and visual FX remain Interaction Ephemeral. Delegated Compose State reads
use a `Value` suffix. Stable composition identity uses the owning entity or
screen identity; projection revision is a separate route/lifecycle token and is
not reused as a `key(...)` identity.
