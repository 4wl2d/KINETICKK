<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK local Pokeball policy

**Policy ID:** `kinetickk-local-standard`

**Revision:** `1`

**Owner:** KINETICKK project

**Scope:** the four authorities and the Desktop/Web in-process bindings named
in `authority-map.md`

This document selects only values and mechanisms that Pokeball Core leaves to
the project. Core remains authoritative. Typed protocols and bounds in Kotlin
remain authoritative where they are more specific. The final claim record pins
the exact byte digest of this policy and its Assembly.

## Effective profiles

Profile, GameplayRun, and AppSession select:

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
runtime mutation profile.

The Inline binding adds no actor, mailbox, worker pool, reflection registry,
service locator, coroutine queue, transport serialization, or thread hop.
`InlineDispatchGuard` and a bounded synchronous completion deque are mechanical
Foundation only.

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
| Session / repository command-result mappings | 9 / 10 | closed Assembly inventory and generated projection |
| rendered application routes | 7 | closed AppSession route protocol and Interaction mapping |
| same-stack causal depth | 8 | three acceptor depth guards plus the source-derived acyclic direct-control graph |
| Profile / Gameplay / Session completion deque capacity | 8 / 8 / 8 | three separately anchored bounded completion deques |
| active GameplayRun instances | 1 | Session allocation/lifecycle invariant |
| Gameplay fixed steps per render frame | 48 | Gameplay simulation loop |
| Gameplay simulation raw-delta / accumulator cap seconds | `0.1` / `0.3` | production GameLoop clamps before and during accumulator admission |
| enemies / projectiles / pickups / trail | 120 / 650 / 420 / 110 | captured Content enemy policy plus explicit Gameplay insertion/rollover limits |
| delayed Relic hits | 256 | Gameplay checks capacity before enqueue |
| projectile hit-history IDs | 120 | projectile-local bounded identity set, reclaimed against live enemies |
| Gameplay sound cues / weapon nodes / orbitals / choices | 32 / 8 / 8 / 4 | Gameplay-owned bounded insertion or atomic validation |
| Arc Coil targets / generated item, weapon, or Relic reward choices | 6 / 3 | deployed weapon and three reward-generator paths |
| Gameplay trail samples per update | 32 | bounded per-update sampling loop |
| visual-FX cues per projection | 2048 | Gameplay bounded accumulator |
| Interaction particles / motion echoes / shockwaves | 700 / 36 / 48 | Interaction-ephemeral bounded reducers |
| Interaction damage numbers / weapon arcs | 140 / 128 | Interaction-ephemeral bounded reducers |
| Interaction frame delta seconds | `0..1` | Interaction ingress quarantine before Gameplay Pulse construction |
| Interaction viewport pixels / density | `1..32768` / `0.5..8` | Interaction ingress quarantine before Gameplay Pulse construction |
| Interaction pointer / choice index | `0..validated viewport` / `0..3` | validated viewport-relative pointer and closed Gameplay choice admission |
| authoritative Gameplay frame delta seconds | `0..1` | Gameplay Nucleus admission independently revalidates the foreign Pulse |
| authoritative Gameplay viewport pixels / density | `1..32768` / `0.5..8` | Gameplay Nucleus admission independently revalidates both dimensions and density |
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

Desktop writes only node `kinetickk/profile`, key `snapshot_v4`; Web writes only
`kinetickk_profile_v4`. Legacy discovery/purge is limited to Desktop node
`kinetickk/progression` keys `progress_v2` and `kinetickk_matter`, and Web keys
`kinetickk_progress_v2` and `kinetickk_matter`.

Reset ordering is write-default-v4, observe success, then purge only the exact
legacy keys. Failure or uncertainty preserves legacy data. A user
`RetryLegacyPurge` is a new accepted business Pulse over retained reset state;
it is not automatic dispatch redelivery, transport retry, or idempotent replay.

## Capability and failure policy

Resources receive the minimum explicit bounded capability: exact-key read,
write, and remove functions or bounded tone playback. No Ball receives ambient
Preferences, `localStorage`, filesystem, browser, or audio-provider authority.
Provider exceptions are quarantined into closed facts. Programming/invariant
faults are not converted into business rejections.

All rejection, validation, admission, resource-result, and participant-result
reasons are closed variants. Open field/reason strings are prohibited.

## UI policy

Compose render models are immutable. Page, focus, viewport, gestures, animation
clock, and visual FX remain Interaction Ephemeral. Delegated Compose State reads
use a `Value` suffix. Stable composition identity uses the owning entity or
screen identity; projection revision is a separate route/lifecycle token and is
not reused as a `key(...)` identity.
