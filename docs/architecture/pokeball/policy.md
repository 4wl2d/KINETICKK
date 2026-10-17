<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK local Pokeball policy

**Policy ID:** `kinetickk-local-standard`

**Revision:** `2`

**Owner:** KINETICKK project

**Scope:** the four authorities and the Android/Desktop/Web in-process bindings named
in `authority-map.md`

<!-- pokeball-audit-policy
profileAuthorities=ContentCatalog|Profile|GameplayRun|AppSession
effectiveProfile=Inline+Transient+InProcess+Standard+Static
contentMutationPath=NONE
semanticRetry=ABSENT
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
The `app:android` application/packaging host contributes exactly one production
project edge, `implementation -> :app:shared`. Both ends belong to AppAssembly;
the split adds no authority, semantic route, or delivery hop.
`InlineDispatchGuard` and a bounded synchronous completion deque are mechanical
Foundation only. The guard inlines its dispatch block to avoid a hot-path
function object; its `@PublishedApi internal` reentrancy bit exists solely for
that inline implementation and exposes no public mutation API or business
state. The App-owned Desktop Audio projection broker is separate
mechanical machinery with one bounded worker and a 24-task queue; it carries no
semantic Pulse, command, result, output, or Fact and does not change the
four-authority execution profile.

The Gameplay sole writer publishes accepted `GameplayState` and its matching
`GameplayRenderSnapshot` together through one `CommittedGameplayFrame`; both
read-only projections therefore always come from the same atomic publication.

Projection reuse must preserve the exact accepted State/render relationship and
match an independently built fresh projection. Reuse may be based on unchanged
immutable state parts or verified predecessor provenance; it must not expose
mutable storage to readers or change the order of visible values. The concrete
storage and copying representation is an implementation choice tested through
retained snapshots, rejected candidates and deterministic input traces.

Impl-owned `ProfileComponent` and `GameplayCompositionComponent` remain
Assembly construction handles. Consumers receive the narrow target-owned
capabilities needed by their workflow or presentation. Their concrete typed
interfaces and wiring are authoritative; no consumer-name registry or fixed
count of constructor call sites is required to prove acceptance and return
ordering. Existing module, foreign-State, platform and capability boundaries
remain independently enforced.

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
| Profile / Gameplay / Session completion deque capacity | 8 / 8 / 8 | owned bounded completion storage; preflight and overflow tests |
| active GameplayRun instances | 1 | Session allocation/lifecycle invariant |
| Gameplay fixed steps per render frame | 48 | Gameplay simulation loop |
| Gameplay simulation raw-delta / accumulator cap seconds | `0.1` / `0.3` | production GameLoop clamps before and during accumulator admission |
| Gameplay fixed-step admission rounding tolerance | 2 ulps of `1/120` s | GameLoop treats an accumulator within two Float ulps of a whole step as that step, so an input on a nominal step boundary lands on the same step at every frame rate; a frame below one step runs no step on a scalar copy |
| enemies / projectiles / pickups / trail | 120 / 650 / 420 / 110 | captured Content enemy policy plus explicit Gameplay insertion/rollover limits |
| Echo Chamber echo hits | 256 | Gameplay checks capacity before enqueue; echoes never echo |
| chain work / visited IDs | 6 / 7 | `MAX_CHAIN_TARGETS`; candidate-private scratch identity buffer; the chain stops at the bound |
| projectile hit-history IDs | 120 | projectile-local sorted bounded identity array, merge-reclaimed against sorted live enemies |
| Gameplay sound cues / weapon nodes / orbitals / choices | 32 / 8 / 8 / 4 | Gameplay-owned bounded insertion or atomic validation |
| Arc Coil targets / generated item, weapon, or Relic reward choices | 6 / 4 | deployed weapon; item offers 3 (4 with an offer-card rule), Relic offers 3 plus Meld or Decline |
| POI offered / active / defenders | 2 / 1 / 3 inside the existing enemy cap | closed two-point construction, activation replaces the offer pair, defender pre-reservation |
| enemy roster / spawn budget / event members / live mines / Aegis links / Heralds per run | 19 (Content bound 24) / 14 / 10 / 10 / 3 per Aegis / 2 | bootstrap requires the exact EnemyType order; the director clamps its bank, plans sizes up to 10 (a pack 6 plus the Pack Hunters bonus) and never exceeds the hard enemy cap; Minelayers stop at ten live mines; an Aegis renews its three nearest allies; Heralds are scheduled at 360 s and 540 s |
| POI directed rewards | 6 per run | six Content schedule windows and checked queue growth before candidate publication |
| Character lattice | 4 collapse vertices / at most 3 retained | fourth distinct Dash position collapses the lattice; nearby positions add nothing |
| combat fields / active buffs / severed Relics | 12 (Content bound 16) / 16 / 4 | `addField` and buff insertion reject at capacity; severance keeps the newest four |
| status Breaks / reactions | 40 per s burst 16 / 30 per s burst 12 | deterministic token buckets; generation-0 events are admitted whole, generation 1 is dropped when empty, generation 2 never cascades |
| status and reaction visual cues per fixed step | 6 | Gameplay-owned per-step cue budget before the bounded accumulator |
| clause dispatch depth / item status applications | 2 / 6 per s per item at one stack | clause engine depth guard; per-item rate gate scaled by stack magnitude |
| Build notifications | 3 latest groups | Interaction-only six-second presentation queue replaces the oldest visible group |
| Gameplay trail samples per update | 32 | bounded per-update sampling loop |
| visual-FX cues per projection | 2048 | Gameplay bounded accumulator |
| Interaction particles / motion echoes / shockwaves | 700 / 36 / 48 | Interaction-ephemeral bounded reducers |
| Interaction damage numbers / weapon arcs | 140 / 128 | Interaction-ephemeral bounded reducers |
| Interaction frame delta seconds | `0..1` | Interaction ingress quarantine before Gameplay Pulse construction |
| Interaction viewport pixels / density | `1..32768` / `0.5..8` | Interaction ingress quarantine before Gameplay Pulse construction; device pixels are then divided by the density into dp, clamped to `1..32768` |
| Interaction pointer representation / choice index | finite / `0..3` | Interaction rejects non-finite pointer coordinates and a density outside `0.5..8`, then divides the pointer by the accepted viewport density; target-owned Gameplay API factories close choice admission |
| authoritative Gameplay frame delta seconds | `0..1` | target-owned Gameplay API factory validates the fixed range before the Nucleus can receive the Pulse |
| authoritative Gameplay viewport dp / density | `1..32768` / `0.5..8` | target-owned Gameplay API factory validates both dimensions and density before the Nucleus can receive the Pulse; the simulation, the viewport and the pointer are dp |
| authoritative Gameplay pointer | `0..current viewport` | Gameplay Nucleus validates both pointer coordinates against committed viewport state |
| Gameplay / Home / Armory presentation delta seconds | `0.1` / `0.1` / `0.1` | Interaction-owned presentation clocks clamp the first larger representable delta |
| Codex catalog entries / search characters | 192 / 128 | Interaction rejects catalog overflow; bounded local name search feeds a lazy grid in original catalog order |
| Armory visible page slice | 3 | deployed draw path selects only the bounded current-page slice |
| accepted caller effect ToneRequests per advance | 32 | Audio Resource rejects an oversized caller batch atomically |
| caller effect ToneRequests selected per advance | 3 | Audio Resource preserves caller order/deduplication within the accepted batch |
| music clock advance delta seconds | `0.1` | Audio Resource clamps finite caller time before advancing its internal music clock |
| ToneRequest frequency Hz / duration seconds / gain | `20..20000` / `0.001..1` / `0..1` | Audio Application Surface value construction and Resource revalidation |
| Desktop audio workers / queued tasks | 1 / 24 | fixed executor and discard-oldest queue policy |
| Desktop synthesis samples / PCM bytes per tone | 22050 / 44100 | validated one-second maximum buffer shape |
| Android audio workers / queued tasks | 1 / 24 | fixed executor and discard-oldest queue policy |
| Android synthesis samples / PCM bytes per tone | 22050 / 44100 | validated one-second maximum PCM16 buffer shape |
| items / weapons / Matrix nodes / Relics / named pairings / reactions | 192 / 12 / 40 / 24 / 32 / 21 (current catalog 173 / 12 / 32 / 22 / 20 / 21) | Content bootstrap validation, including key-consistent pairings, declared elements, Break floors and Matrix prerequisites within their branch, each node one row below its deepest prerequisite; reactions are exactly one per pair of the 7 status elements |
| Matrix node ranks / rows / effects per node | 5 / 8 / 4 | Content definition construction; every node lists one price per rank |
| Rebirth level | `0..10` | Content typed policy plus Profile/Gameplay validation |
| equipped Relic slots / rank | 4 / `1..3` (generic Meld reaches 2) | captured Content Relic policy plus Gameplay retention/saturation |
| Profile retained Matrix node ranks / each rank | one slot per stable MatrixNodeId (at most 40) / `0..5` on the wire, clamped to the captured `maxRanks` on load with the excess refunded | Profile bootstrap compatibility plus policy-free Resource schema validation; prerequisites and Rebirth gates apply only at purchase |
| Profile character achievement totals (including total kills, ricochets and Data pickups) / distinct winners | non-negative saturating `Long` / six stable character IDs | Profile validates deltas, persists cumulative progress and owns unlock evaluation |
| Profile retained discoveries | items: captured `itemCount` (at most 192); Relics 24; reactions 21; pairings 32; heirloom Relics 4 | Profile bootstrap compatibility plus policy-free Resource schema validation; legacy (pre-epoch) lists up to 400 items / 40 Relics are discarded at decode |
| Profile master volume / text scale | `0..1` / `1..1.75` | Profile and Gameplay normalized compatibility plus Resource ingress validation |
| Profile simulation speed / damage-tier threshold | exact declared option sets | API declarations consumed by Profile Nucleus, Gameplay Nucleus, and Resource validation |
| Profile Gameplay discoveries per Pulse | captured `itemCount` (at most 192) | Profile validates count and every stable item ID before acceptance |
| Profile snapshot UTF-8 payload | 65536 bytes | Profile Resource before decode and after encode |
| Desktop Preferences value length | 8192 UTF-16 code units | exact platform broker refuses 8193 before provider execution |
| Desktop Preferences key names admitted per exact node read | 64 | exact platform broker refuses 65 before project-owned membership iteration |

### Mechanically derived and schema-closed collections

These collections do not introduce a second admission authority. Their size is
inherited from a validated closed schema, a bounded source collection, or an
invariant-preserving private copy or write-detaching copy-on-write fork. Runtime property tests cover the root bounds, rejection and isolation of retained
snapshots; the verifier consumes their execution reports without prescribing
private copy methods or local implementation text.

Shared immutable storage and copy-on-write may preserve source cardinality.
Every mutable builder remains candidate-private. Pending discoveries, audio and
visual cues remain bounded and retain their ordering across copy, drain and
rejection. The runtime tests cover those properties; no separate list of safe
copying modes or source-body expectations is a policy requirement.

| Derived ID | Effective bound | Mechanical derivation |
|---|---:|---|
| `gameplay.item-indexed-state` | item arrays, Reborn flags, status accumulators and discoveries <=192; clause timers/counters <= 192 x 2 | validated contiguous Item IDs; pending is a subset; cardinality-stable COW |
| `gameplay.weapon-indexed-state` | unlocked weapons/mutation counters <=12 | closed WeaponId bootstrap; invariant-preserving COW |
| `gameplay.matrix-indexed-state` | rank slots <=40; folded run modifiers fixed-size | validated closed MatrixNodeId catalog sizes the array; one immutable fold per run start |
| `gameplay.relic-indexed-state` | state arrays <=24; enemy status cells <=120 x 71 | validated RelicId catalog; per-enemy status layout driven by the element count (four 7-wide element blocks, 21 reaction-pair slots, Void memory, Unmake and Blackfire cells) times the enemy cap |
| `gameplay.collision-live-enemy-ids` | <=120 | lazy compact sorted array from bounded live enemies |
| `gameplay.reducer-copy-collections` | equal to bounded committed source cardinality | isolated candidate storage; immutable sharing or owned copies; bounded pending outputs |
| `gameplay.render-projection-collections` | equal to bounded source cardinality | verified predecessor provenance; identity/storage reuse or exact ordered fallback |
| `gameplay.stable-compaction` | retained cardinality <= bounded source; stable survivor order | forward survivor compaction/reverse-tail deletion or index-stable removal |
| `profile.codec-temporary-collections` | unlocked <=12; Matrix ranks <=40; retired Lab ranks =8; discoveries <=400 legacy / <=192 current | materialized after schema validation |
| `session.shell-entries` | 1..2 | one base plus one nullable overlay |
| `content.closed-ui-catalogs` | CoreShape 6 / WeaponMastery 4 / EnemyType 19 | bootstrap requires exact stable order |
| `ui.catalog-backed-sources` | Codex items <=192; Codex enemies <=24; Armory weapons <=12; Matrix nodes <=40; mastery closed | validated immutable Content plus bounded Profile state |
| `audio.music-notes` | 8 | fixed literal array with modulo-only indexing |
| `foundation.immutable-set-copy` | list = input cardinality; set <= input cardinality | safe immutable reuse or owned copy; stable first-occurrence set semantics |

Named boundary evidence executes exact maxima and the first rejected, deferred,
trimmed, or retained-overflow case where the bound is executable. Real collection,
complete-output and completion-storage bounds remain enforced by their owners.
The architecture gate depends on the existing runtime tests for those properties,
including old-snapshot isolation and independent fresh projection comparison.
It does not compare private function bodies, copy modes, or local variable names.
Existing simulation
policy is explicit: enemy, projectile, pickup,
sound, node, choice, hit-history, and delayed-hit additions enforce their named
limits; trail and selected Interaction-ephemeral collections use their declared
rollover policy. Gameplay hot-loop removals compact survivors in stable order
and never increase the source bound. Semantic output and participant-command batches are never
truncated or partially accepted. Gameplay simulation consequences are held in
a typed fixed batch with at most one visual-FX, Profile-progress, and audio or
unlock slot in canonical order. Production maps those slots directly into one
exact-size immutable Gameplay output list; the generic `SimulationOutput` view
materializes wrappers only for explicit inspection. Audio cues or a nonzero
audio delta without an `AdvanceAudio` consequence fail before mapping. The accepted transition publishes one coherent next State/output frame after
its complete outputs and workflow inputs are known. A mechanical render/audio projection may
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

Static immediate workflows use their complete finite execution structure as the
work bound. This covers result, refusal and failure handlers and any work they
can emit, not merely the import DAG. No global dependency/participant/route quota,
carried causal-depth protocol or geometric fan-out total is selected. Actual
queues, retained outputs and external requests still have finite capacity; an
overflow rejects the complete candidate before acceptance and never loses
accepted work. Immediate commands use target-owned typed calls and their bounded
call-scoped results. Profile persistence separately retains `ProfileEffectRef`
and snapshot-revision correlation between its accepted effect and write Fact.

No numeric `maxTransitionSteps`, `maxStateBytes`, or
`maxOutputBytesPerDecision` claim is selected. Termination and semantic-output
bounds are established by static control-flow/type proofs and collection caps;
there is therefore no invented Decision Work Meter or unrelated byte-measure
artifact. The save payload limit is a raw storage-boundary input/encoded-output
measure, not a general Ball State-size claim.

## Persistence boundary

Before KINETICKK `1.0.0`, there is one current Profile JSON boundary. It is
strict:

- unknown or missing required fields fail;
- no leniency, coercion, special floats, `ignoreUnknownKeys`, or
  `@JsonIgnoreUnknownKeys`;
- all economy `Long` values use canonical validated decimal strings;
- stable IDs are explicit; maps become sorted record lists and collections are
  sorted; defaults are encoded;
- representation and closed-type validation occur before a bootstrap Pulse;
  State/Context-dependent compatibility and default-selection decisions remain Profile
  Nucleus policy.

The current wire schema explicitly permits omitted `languageCode` and
`runStatisticsOnLeft` fields. Their nullable DTO defaults represent absence;
the resulting preference values are Russian and `false`, respectively. Present
values still pass strict validation and canonical wire re-encoding. These
optional fields belong to the single current schema, without a version family
or migration path.

The collection carries one optional `itemCatalogEpoch`. Encoders always write
the current epoch; a payload without it (or with an older epoch) comes from the
retired 400-item / 40-Relic catalog, so decode keeps every economy, Matrix,
loadout, Rebirth, achievement and preference value and discards only the item
and Relic discovery lists before validation.

The retired Lab stays readable as one optional `labProgress` field at its old
position. A payload that carries it must list exactly the eight frozen upgrade
wire ids in sorted order with ranks inside their frozen maxima; decode refunds
the full price of every owned rank (the sum of `baseCost × k²` for each owned
rank k) into Matter, raises lifetime Matter to at least the new balance and
starts an empty Matrix. Encoders never write the Lab. The optional `matrix`
field lists only owned nodes, strictly sorted by wire id with ranks `1..5`; a
payload holding both fields is inconsistent. The cumulative `totalKills`,
`ricochets` and `dataPickups` achievement counters are written only when
non-zero. Discovered reactions, discovered
pairings and heirloom Relics are optional sorted stable-ID lists (heirlooms keep
slot order) written only when non-empty. Unknown stable IDs in a current-epoch
payload still fail closed.

The current payload is the canonical encoding of `ProfileSnapshot(revision,
profile)`. It has no persisted schema-version discriminator, profile ID, or
content-version field. The 65536-byte codec bound protects JSON representation ingress and encoded
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

Platform composition is the sole physical storage authority and selects one
current location per target: Android SharedPreferences `kinetickk.profile.v2` key
`snapshot`, Desktop Preferences node `kinetickk/profile-v2` key `snapshot`, and Web
local-storage key `kinetickk_profile_v2`. The Resource, Ball, Flow, and
Interactions cannot choose another root, node, or key.

There is no pre-`1.0.0` migration or supported family of versioned schemas. Keys
outside the selected current key are never read, enumerated for compatibility,
removed, or cleared. They remain untouched. `ProfileResource` and the injected
platform capability expose only `readSnapshot` and `writeSnapshot`; no reset,
purge, import, or bulk-clear operation exists.

Desktop Preferences may mechanically return all key names in the selected node
so the broker can prove whether exact key `snapshot` is present. That bounded
name enumeration creates no compatibility inventory: non-current names do not
select behavior, their values are not read, and they are not mutated.

An absent current value, strict codec rejection, revision exhaustion, or
current-policy incompatibility constructs the default Profile with bootstrap
ready. A provider read failure is not treated as incompatible data: it produces
`ProfileBootstrapStatus.Blocked`, maps Session to
`BOOTSTRAP_UNAVAILABLE`, and renders the input-blocking `PROFILE UNAVAILABLE`
UI without mutating storage.

There is no semantic retry route. Every accepted Profile mutation may emit one
`ProfileOutput.PersistSnapshot`; the executor invokes `writeSnapshot` once and
feeds the one typed completion back to Profile. A rejected outbound snapshot
does not call the provider. A known provider failure before execution becomes
`ResourceFailure`; possible execution or failed exact read-back becomes
`OutcomeUnknown`. Neither result rolls back the accepted Profile frame or
schedules another write.

## Capability and failure policy

Resources receive the minimum explicit bounded capability: exact-key snapshot
read/write functions or bounded tone playback. No Ball receives ambient
Preferences, `localStorage`, filesystem, browser, or audio-provider authority.
The platform broker classifies only explicit provider failures into closed
technical read/mutation outcomes. A known failure before mutation stays
`ResourceFailure`; possible execution
alone becomes `OutcomeUnknown`. Every unclassified exception, programming fault,
invariant fault, and allocation failure follows runtime-fault policy and is not
converted into provider evidence, a business rejection, or an accepted result.

Under Core §9.13 live mechanical Projection, Audio produces no typed Fact, result, or status.
Synchronous Audio Resource and platform calls propagate under runtime-fault policy. That rule
includes synchronous Web `AudioContext` invocation and graph construction calls. Android and
Desktop synthesis run inside detached executor workers.
Android and Desktop synthesis faults escape their detached executor `Runnable` to the runtime.
This is a worker-runtime escape with no caller-propagation claim.
Only Android worker InterruptedException during executor shutdown is expected cancellation:
restore the interrupt flag and rethrow unless executor.isShutdown. This exception is restricted to
private `synthesize` submitted by the bounded executor; synchronous `play`, `close`, Resource calls,
provider failures and unrelated interruptions keep the existing runtime-fault policy.
AudioTrack.release remains in finally, including cancellation. The targeted device test
`closingDuringPlaybackCancelsWithoutAnUncaughtWorkerFailure` exercises this shutdown order.

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
