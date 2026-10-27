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
| application routes / overlays | 6 / 4 | closed `AppDestination` inventory (Home and Gameplay bases; Settings, Workshop, Rebirth and Codex overlays); character selection is accepted only over the Home base, bare or under the Workshop overlay, and the Workshop opens over a paused run without it |
| Gameplay fixed steps per render frame | 48 | Gameplay simulation loop |
| Gameplay simulation raw-delta / accumulator cap seconds | `0.1` / `0.3` | production GameLoop clamps before and during accumulator admission |
| Gameplay fixed-step admission rounding tolerance | 2 ulps of `1/120` s | GameLoop treats an accumulator within two Float ulps of a whole step as that step, so an input on a nominal step boundary lands on the same step at every frame rate; a frame below one step runs no step on a scalar copy |
| enemies / projectiles / pickups / trail | 120 / 650 / 420 / 110 | captured Content enemy policy plus explicit Gameplay insertion/rollover limits |
| Echo Chamber echo hits | 256 | Gameplay checks capacity before enqueue; echoes never echo |
| chain work / visited IDs | 6 / 7 | `MAX_CHAIN_TARGETS`; candidate-private scratch identity buffer; the chain stops at the bound |
| projectile hit-history IDs | 120 | projectile-local sorted bounded identity array, merge-reclaimed against sorted live enemies |
| Gameplay sound cues / weapon nodes / weapon entities / orbitals / choices | 32 / 12 (8 before Gravity Mines' second form) / 32 / 8 / 4 | Gameplay-owned bounded insertion or atomic validation; a full mine field drops the newest mine (a parry mine then detonates at once); a full entity list gives up a finished entity, else its oldest one other than a tether or beam (inside an entity pass that one finishes and leaves after the pass, so no index shifts) |
| Arc Coil targets / generated item, weapon, or Relic reward choices | 4 (6 on a Pentagon beat) / 4 | deployed weapon: two targets, a third at the first form and a fourth at the third, each hop within reach of the last; item offers 3 (4 with an offer-card rule), Relic offers 3 plus Meld or Decline, the active slot prompt one card per held active (3) plus Keep |
| POI offered / active / defenders | 2 / 1 / 3 inside the existing enemy cap | closed two-point construction, activation replaces the offer pair, defender pre-reservation |
| enemy roster / spawn budget / event members / live mines / Aegis links / Heralds per run | 19 (Content bound 24) / 14 / 10 / 10 / 3 per Aegis / 3 | bootstrap requires the exact EnemyType order; the director clamps its bank, plans sizes up to 10 (a pack 6 plus the Pack Hunters bonus) and never exceeds the hard enemy cap; Minelayers stop at ten live mines; an Aegis renews its three nearest allies; two Heralds are scheduled, at 360 s from tier 2 and at 540 s from tier 6, and the tier-10 Singularity Architect arrives with a third as its escort; `heraldsSpawned` counts all three |
| world obstacles held / destroyed unstable asteroids / obstacles per chunk | 128 (a 5 × 5 chunk window × 5) / 256 / 5 | Content validates the window against `MAX_OBSTACLES`; Gameplay regenerates the window only when the Core changes chunk and keeps destroyed ids as a bounded FIFO |
| Core obstacle contacts per fixed step / enemy-enemy slams per step / thrown enemies checked per step | 3 / 6 / 24 | the swept Core resolution stops after three contacts and then pushes the Core out; further slams wait for the next step |
| Data magnet / collection radius | 320 (item floor 120) / 56 | Content `PickupTuning`; latched pickups home at `max(950, 1.3 × Core speed)`; Keys and Relic resonances never expire and evict the oldest Data at the pickup cap |
| run level / Data requirement table | `1..maximumLevel` = 42 (Content bound 99) / `maximumLevel − 1` positive non-decreasing entries; Rebirth tier t scales the entries from `rebirthScaleFromLevel` (16, validated `1..99`) to `max(req, floor(req × (1 + k·t) + 0.5))`, saturating, with k = `REBIRTH_DATA_REQUIREMENT_SCALE_PER_TIER` (0.14) | `RunTempoProfile` and `RebirthProfile` construction; `gainData` stops leveling at the cap; Data credited there still counts toward `dataCollected` and charges the Apex instead |
| POI directed rewards | 6 per run | six Content schedule windows and checked queue growth before candidate publication |
| Character lattice | 4 collapse vertices / at most 3 retained | fourth distinct Dash position collapses the lattice; nearby positions add nothing |
| Character runtime: facets / Edge / Stellar Mass / Blink charges / void scars / drones / echo path / Dipole slams per step | 4 / 5 / 24 / 2 / 4 / 4 / 122 samples / 8 | copy-on-write buffers allocated once per run; the echo ring is paged (8 samples a page), so a step copies at most one page and a reduction the page table once, and the small lattice, drone and scar arrays are copied at most once per reduction; a full scar list detonates its oldest, a full hive refreshes instead of growing, the echo ring overwrites its oldest sample |
| combat fields / active buffs / severed Relics | 12 (Content bound 16): reaction and item fields share `maximumFields − maximumSkillFields` = 9, skill fields at most `maximumSkillFields` = 3 (one per slot; a recast overwrites it, a replaced active's field leaves without its closing effect); per kind FUSION 3, ROT_WELL 3, DARK_STAR 4, NULL_WELL 3, PLASMA 3, HUSH 2, TIME_BUBBLE 2, ATTRACTOR 2, a full kind replacing its oldest field in place / 16 / 4 | `addField` over a fixed 16 × 22 copy-on-write table refuses past the pool; Content validates `1 ≤ maximumSkillFields < maximumFields`; buff insertion rejects at capacity; severance keeps the newest four |
| field passes | shot eating over the 4 newest shot-eating fields (Maw ≤ 20, Time Bubble ≤ 24 with Rewind returning ≤ 12, Dead Air ≤ 20 shots per field); star pickup pull over the 2 newest; attractor bend ≤ 4 rad/s within 220; Compost ≤ 3 growths up to radius 200, never shrinking a well already wider; skill fields join the shot and pickup passes within the same caps | `CombatFieldSystem` pass order |
| reaction verbs | values ≤ 4 and cue ≤ 28 characters per reaction; Core link radius 480 (Core Link items add ≤ 240); Shard Guard ≤ 6 shards (5 per guard) for 4 s; Rock Echo ≤ 3 rocks within 260; Breakpoint ≤ 12 detonations per Brake release within 320; Flame Leap stops below 8 × run scale (0.6 per leap); aftershock slots 2; Overcurrent ≤ 1 per s; Arc Weave one 3-link chain per TURN edge (2 links in Standing Wave); Void Anchor one per run, blink ≤ 520 in a 30° cone with a 60 u singularity gap; forced and echoed reactions scale ≤ 1.3 and echoes are token-gated | Content `ReactionDefinition` validation; `ReactionSystem` verb sites; the Architect gate and boss control rules stay in force |
| Core impulses / erased-shot bursts shown | ≤ 700 u/s, reaction and item pushes sharing one 0.5 s gate (Rocket Clap 560) / 4 per fixed step | `applyCoreImpulse`; `eraseHostileShot` |
| active effects: pool / slots / ranks / values per rank | 18 (Content bound 24), each carried by exactly one active item, ids 269..286 in `SkillId` order / 3 (Z, X, C; every slot usable from level 1, an empty slot buffers nothing) / `1..3`, always the carrying item's stacks / 6 | Content bootstrap requires the exact `SkillId` order, validates every rank and requires one carrier per effect with the effect's name, tags in order and element; `GameplayContentSnapshot.activeItemIds` refuses a second carrier; item acquisition and replacement are the only `SkillLoadout` writers; the Gameplay API admits skill slots `0..2` only |
| skill cooldowns, refunds and charges | recast ≥ 2.5 s before any refund; static cooldown scale ≥ 0.5 (build cut ≤ 0.4); refunds ≤ 50 % of a cast's cooldown and never before the cast + 2.5 s; Decay Spores refunds ≤ 3 s per skill per cast; charges 1, or 2 with Skill Charges, each charge recharging on its own clock at cooldown × 1.2 | `SkillSystem` cast admission and refund bank; `BuildCompiler` clamps |
| skill cast work | 4 skill hits per cast; one damage instance per boss-class enemy per cast; Catalyze ≤ 10 enemies (scratch 10 ids); Storm Coil ≤ 3 targets per zap; Resonator ≤ 5 echoes per field; Null Shroud stores ≤ 18 bolts and releases them over ≤ 4 enemies; Dual Infusion ≤ 8 stacks per s; Decay Spores ≤ 8 Data per cast from the deaths it seeded (an item's Spored mark drops its own Data); one skill field per slot | candidate-private cast scope and scratch; bounded per-cast counters |
| movement-skill obstacle clearance | Arc Hook's nearest target needs a line to 200 u past it clear of obstacles by the Core radius + 30; Slingshot swings at most 1,100 u/s and at most the clear distance along its aim over 0.5 s; Void Step lands ≥ the Core radius + 80 from an obstacle surface, stepping back 20 u at a time | `SkillSystem` targeting and landing |
| skill and Apex input | one slot mask and one Apex flag, each buffered 0.16 s; a request is taken only while RUNNING for a filled slot or an unlocked Apex, and a repeat inside the buffer changes nothing; pause, overlays, exit and every reward choice that opens (item, Totem, weapon, Relic, Heirloom, Relic bind, directed reward, active slot prompt) clear the Dash, slot and Apex buffers, and binding or replacing an active clears that slot's bit | Gameplay reducer scalar reduction; Interaction rejects a skill slot outside `0..2` before Pulse construction |
| active item offers | actives reach a run only as ordinary item cards (level-up and Elite Bounty offers, POI item rewards); an active meets every item offer rule (deck, Rebirth rank, usefulness) and, instead of the catalog level passive items use (the higher of the run level and the lifetime-Matter level), is offerable while `OfferLanePolicy.activeWeight` > 0 once the run level reaches its `unlockLevel` (shipped Uncommon 5, Rare 7, Epic 12; no Common or Legendary active), and an unowned Catalyze only while 2 elements have affinity; inside a rolled tier its weight is multiplied by `activeWeight` (1), by `characterActiveWeight` (2) when its effect is in the run character's `activeLane` (3 distinct effects) and by `ownedActiveWeight` (1.5) when it would rank up, each finite and ≥ 0, with no added draw; at most one active card per offer (later cards leave actives out, never redraw); a POI item reward stays a uniform shuffle of its focus pool and takes the first 3, skipping every active after the first; a maxed active is not offered | reward stream draws in `OfferSystem` (`activeOfferable`, `activeOfferWeight`) and `PointOfInterestSystem`; Content `OfferLanePolicy` and `CoreShapeDefinition.activeLane` validation |
| active items: rank / passive | `maxStacks` exactly 3 whatever the rarity; the first stack binds the effect into the lowest empty slot at rank I and each later stack raises the rank, both ready at once / exactly one passive rule (no stat, clause, liability, Data economy, skill vocabulary, reroll, offer-card, Break-threshold or keystone rule, no weapon-family condition) whose base × `stackMagnitude(3)` stays within the median of its kind among shipped passive items of Rare or lower × `stackMagnitude(2)`, or an explicit cap where none exists, and two actives of one element never share a passive rule kind; the effect counts as one item part no Relic suppresses and nothing leaves dormant; tags and element count once, like any item's | `ItemDefinition` construction; `ContentCatalogTest` pins the passive table and its bounds; `BuildCompiler` |
| active slot prompt (`ChoiceType.ACTIVE_SLOT`) | an unowned active taken while Z, X and C all hold actives waits as run State (`pendingActiveItemId`, `pendingActiveReborn`, cleared when a run starts) and asks for one card per held active plus Keep; a replacement removes the held item from the run (stacks, Reborn flag, gain order, its passive on the recompile), ends everything its effect still runs, keeps shield and integrity within the new capacities and binds the incoming item in that slot at rank I; Keep salvages the incoming item for rarity rank × 2 Matter before the run's Matter gain (the maxed-item salvage; the card shows that base); an offer's rarity pity resolves when the item card is taken and a directed reward's repair applies once, after the prompt; any other acquisition of an unowned active with every slot held, or of a maxed item, salvages it | `GameSession.choose`, `openActiveSlotChoice`, `replaceActiveItem`, `releaseSkillSlot`; cards carry facts only (slot, held item and rank, incoming item, salvage Matter), Interaction words them |
| reward rerolls | item offers; weapon offers with more than 3 candidates; Relic offers except the Heirloom offer; never a Totem, Relic bind or active slot choice, nor a directed reward | `choicesCanReroll` and `rerollChoices` in the Nucleus; the render projection's copy mirrors only the choice-type and directed-reward exclusions |
| Apex ability | one per character (12 definitions, values ≤ 4), unlocked at the level cap; cooldown 60 s, build cut ≤ 0.3, never below 30 s; Data credited at the cap refunds 0.03 s per point, never before the last cast + 30 s; boss strikes ≤ the character's `bossStrikes` per activation (5 to 12 shipped, Content bound 12), each asking for the target's whole hit cap, never past a phase gate and never during a shift, a proximity channel's event striking only a boss whose edge is within `APEX_BOSS_REACH` 420 u of it; Apex shots aimed at a boss pierce ≤ 4 ordinary enemies (`APEX_BOSS_SHOT_PIERCE`); hostile bolts erased ≤ 96 per activation (refractions and parries included; shipped budgets 0 to 72, Mirror Stance 4); Perihelion holds ≤ 24 bolts; Hypercube cage ≤ 4 nodes; Apex shots 720 u/s for 2 s | Content `ApexDefinition` construction; `ApexSystem` bank, reach and strike/bolt budgets; Architect damage passes `gateArchitectDamage`, a Herald strike is clamped to the cap |
| item rule vocabulary | SKILL_HIT ≤ 4 per cast; SHOT_ERASED and MARK_CONSUMED ≤ 4 each per fixed step; FIELD_ENTER ≤ 1 per 0.5 s; APEX clauses every ≤ 3; ForceReaction ≤ 4 targets (cooldown ≥ 1 s or every ≥ 3 on a frequent trigger); Zone 60..300 for 0.5..4 s inside the field pool and kind caps (≥ 2 s or every ≥ 4 on a frequent trigger); EraseShots ≤ 12 within ≤ 400 (≥ 1 s on a frequent trigger); CoreImpulse 100..600 through the Core gate; PrimeDash 3 s; ReactionTuning ≤ +0.75 per reaction and aspect (shared rows ≤ +0.5, tuned radius ≤ 600); FieldTuning ≤ +0.5 per aspect with the reaction-field product ≤ 1.75; SkillCooldown cut ≤ 0.4; attuned stacks ≤ 8 per cast; CatalysisRate ≤ 2×; ApexCooldown ≤ 0.3; CoreLinkRadius ≤ +240; knockback conversion once per enemy per 0.5 s; one keystone per item (Epic or better), exactly one keystone item per reaction and at most one item each with Skill Charges and Skill Attunement | clause and rule validation at Content construction and catalog bootstrap; `BuildCompiler` clamps; per-step dispatch counters |
| status Breaks / reactions | 40 per s burst 16 / 30 per s burst 12 | deterministic token buckets; generation-0 events are admitted whole, generation 1 is dropped when empty, generation 2 never cascades |
| status and reaction visual cues per fixed step | 6 | Gameplay-owned per-step cue budget before the bounded accumulator |
| clause dispatch depth / item status applications | 2 / 6 per s per item at one stack | clause engine depth guard; per-item rate gate scaled by stack magnitude |
| Build notifications | 3 latest groups | Interaction-only six-second presentation queue replaces the oldest visible group |
| Gameplay trail samples per update | 32 | bounded per-update sampling loop; samples spread with speed so the 110 points hold the longest live point's life and fade |
| visual-FX cues per projection | 2048 | Gameplay bounded accumulator |
| Interaction particles / motion echoes / shockwaves | 700 / 36 / 48 | Interaction-ephemeral bounded reducers |
| Interaction damage numbers / weapon arcs | 140 / 128 | Interaction-ephemeral bounded reducers |
| Interaction frame delta seconds | `0..1` | Interaction ingress quarantine before Gameplay Pulse construction |
| Interaction viewport pixels / density | `1..32768` / `0.5..8` | Interaction ingress quarantine before Gameplay Pulse construction; device pixels are then divided by the density into dp, clamped to `1..32768` |
| Interaction pointer representation / choice index / skill slot | finite / `0..3` / `0..2` | Interaction rejects non-finite pointer coordinates and a density outside `0.5..8`, then divides the pointer by the accepted viewport density; target-owned Gameplay API factories close choice and skill-slot admission |
| running touch pointers / HUD keep-out rectangles | 5, a sixth ignored until one lifts, one pointer steering / 12 (the running HUD uses at most 9) | `RunningTouchTracker`; `WorldHudKeepOut` fixed arrays |
| authoritative Gameplay frame delta seconds | `0..1` | target-owned Gameplay API factory validates the fixed range before the Nucleus can receive the Pulse |
| authoritative Gameplay viewport dp / density | `1..32768` / `0.5..8` | target-owned Gameplay API factory validates both dimensions and density before the Nucleus can receive the Pulse; the simulation, the viewport and the pointer are dp |
| authoritative Gameplay pointer | `0..current viewport` | Gameplay Nucleus validates both pointer coordinates against committed viewport state |
| Gameplay / Home / Workshop presentation delta seconds | `0.1` / `0.1` / `0.1` | Interaction-owned presentation clocks clamp the first larger representable delta |
| Interaction frame pacing | before each frame of the run, Home and Settings-preview loops wait `max(0, 1/cap − elapsed − 0.5 ms)` in whole milliseconds; cap 0 waits nothing | `KkFramePacer`; Desktop V-Sync precedence is `-Dskiko.vsync.enabled`, then `-Pkinetickk.uncapped` / `KINETICKK_UNCAPPED_FPS`, then the stored preference |
| interface scale offered / applied | 100 % always, another option only while the window at that scale keeps a short side of at least 320 dp and a long side of at least 568 dp, none below 100 % on a touch-primary device / the stored scale clamped to the largest offered option at or below it; menus lay out at density × scale, the run's HUD at `max(1, density × scale)` while the world and the Gameplay viewport keep the raw density | Settings Interaction; Session and Gameplay Interaction presentation |
| Desktop window presets / Android refresh choices | offered only when within the screen's usable area, a larger stored preset clamped to it / at most 4 distinct whole-Hz rates within `24..360` Hz at the current resolution, highest first; Auto or an unavailable rate selects the fastest mode | platform display host; the stored Profile value is never rewritten |
| Codex catalog entries / search characters | 320 / 128 | Interaction rejects catalog overflow; bounded local name search feeds a lazy grid in original catalog order |
| Workshop weapon tiles | 24 | one tile per validated catalog weapon (Content bound) on the Weapons tab's scrolling grid; the pager and the selection reveal move the scroll, never the tile set |
| Settings groups / rows | 6 (Game, Sound, Display, Graphics, Interface, Controls; a group with no offered row is hidden) / 41 declared, each shown only when `SettingsDisplaySupport` offers it; Controls split into two columns from 600 dp of row width | Profile Interaction Settings reducer and layout; key capture is Interaction-ephemeral and consumes every key while it waits |
| world FX draw operations per frame | transient 900, links 600, fields 220, enemy glyphs 480, Core states 60; signature layers always draw (≤ 14 operations per reaction burst, ≤ 24 per skill burst); secondary layers stop above 60 on-screen enemies and only signatures draw above 90 | `FxDrawBudget` and `FxFrame`, draw-thread confined |
| Interaction element bursts / reaction stamps / skill bursts / Apex bursts / styled link life | 24 (≤ 4 live per reaction) / 3 / 8 / 2 / ≤ 0.35 s | Interaction-ephemeral bounded reducers trim the oldest |
| accepted caller effect ToneRequests per advance | 32 | Audio Resource rejects an oversized caller batch atomically |
| caller effect ToneRequests selected per advance | 3 | Audio Resource preserves caller order/deduplication within the accepted batch |
| music clock advance delta seconds | `0.1` | Audio Resource clamps finite caller time before advancing its internal music clock |
| ToneRequest frequency Hz / duration seconds / gain | `20..20000` / `0.001..1` / `0..1` | Audio Application Surface value construction and Resource revalidation |
| Desktop audio workers / queued tasks | 1 / 24 | fixed executor and discard-oldest queue policy |
| Desktop synthesis samples / PCM bytes per tone | 22050 / 44100 | validated one-second maximum buffer shape |
| Android audio workers / queued tasks | 1 / 24 | fixed executor and discard-oldest queue policy |
| Android synthesis samples / PCM bytes per tone | 22050 / 44100 | validated one-second maximum PCM16 buffer shape |
| items / weapons / Matrix nodes / Relics / named pairings / reactions | 320 / 24 / 40 / 24 / 32 / 21 (current catalog 287 including 18 active items / 24 / 32 / 22 / 20 / 21) | Content bootstrap validation, including key-consistent pairings, every weapon key carried by two Relics (one not Sovereign), declared elements, Break floors and Matrix prerequisites within their branch, each node one row below its deepest prerequisite; reactions are exactly one per pair of the 7 status elements, each with exactly one keystone item of Epic rarity or better that declares both its elements |
| Matrix node ranks / rows / effects per node | 5 / 8 / 4 | Content definition construction; every node lists one price per rank |
| Rebirth level / highest cleared / Ascendant characters / character clears | selected tier `0..rank` with rank = highest cleared + 1 (at most 10) / `-1..10` / twelve stable character IDs, only once tier 10 is cleared and only victorious characters / one tier per character in `CoreShape` order, each `-1` or `0..highest cleared`, only for victorious characters, tier 10 exactly for Ascendant ones (an Ascendant character may record `-1` from before the record) | Content typed policy plus one invariant shared by Profile construction, Resource validation and Gameplay start validation; a run's clear is validated against the rank |
| Rebirth Volatile Remains bursts | 48 pending | Gameplay drops further bursts while 48 wait; each detonates 0.35 s after its death |
| equipped Relic slots / rank | 4 / `1..3` (generic Meld reaches 2) | captured Content Relic policy plus Gameplay retention/saturation |
| run draft deck | one deal per run from its own stream (run seed xor `0x0DEC_C0DE`), at most `MAX_ITEMS` draws; reward draws keep their count and order; active items are dealt by their tier's share and every deal floor (early, Data, character archetype, archetype, element, wager, skill support) counts and promotes passive items only; a tier refills only when it holds fewer than its floor of dealt items the run can still take, actives included, and a refill may deal an offerable active from the reserve; the Data stratum deals the deck's Data floor plus `DataStratum` items per Data Archive rank; recent-item weights `max(0.35, 1/(1 + 0.8p))` on the deal, their square root on offers; Data-economy items weigh `1 + fraction × rank` in every offer lane (`DataOfferWeight` fraction `(0, 2]`, 1.5 shipped), with no added reward draw | `DraftDeckSystem`; immutable `ItemDeck`; Content `DraftDeckPolicy`, `OfferLanePolicy` and `MatrixEffect.DataOfferWeight` construction; `buildItemChoices` |
| permanent upgrade levels | 12 character and 24 weapon tracks, each `0..UpgradePolicy.maxLevel` (10); the next level costs `base × 2^level`, saturating at `Long.MAX_VALUE`, with base `1..10,000` | Content construction; Profile purchase checks ownership or unlock, then the maximum, then Matter; load clamps levels above a lowered maximum and refunds them; Resource schema; Gameplay start rejects other sizes or levels (`UPGRADE_LEVEL_OUT_OF_RANGE`) |
| upgrade levels on the wire | `"c"` ≤ 12 and `"w"` ≤ 24 lowercase base-36 digits, each `0..10`, no trailing zero; slot order frozen and append-only | Resource schema |
| Profile recent item history / acquired items per progress update | ≤ 64 ids, oldest first, repeats kept, each below the captured item count (Profile) and `MAX_ITEMS` (Resource) / ≤ 64 | Profile progress validation refuses the whole update above 64 (`TooManyAcquiredItems`) and keeps the newest 64; load filter; Resource schema; Gameplay keeps the newest 64 acquisitions and rejects a longer or foreign start history (`RECENT_ITEM_HISTORY_INVALID`) |
| Profile retained Matrix node ranks / each rank | one slot per stable MatrixNodeId (at most 40) / `0..5` on the wire, clamped to the captured `maxRanks` on load with the excess refunded | Profile bootstrap compatibility plus policy-free Resource schema validation; prerequisites and Rebirth gates apply only at purchase |
| Profile character achievement totals (including total kills, ricochets and Data pickups) / distinct winners | non-negative saturating `Long` / twelve stable character IDs | Profile validates deltas, persists cumulative progress and owns unlock evaluation |
| Profile retained discoveries | items: captured `itemCount` (at most 320); Relics 24; reactions 21; pairings 32; heirloom Relics 4 | Profile bootstrap compatibility plus policy-free Resource schema validation; legacy (pre-epoch) lists up to 400 items / 40 Relics are discarded at decode |
| Profile master volume / text scale | `0..1` / `1..1.75` | Profile and Gameplay normalized compatibility plus Resource ingress validation |
| Profile simulation speed / damage-tier threshold | exact declared option sets | API declarations consumed by Profile Nucleus, Gameplay Nucleus, and Resource validation |
| Profile display preferences | frame cap ∈ {0, 30, 60, 90, 120, 144, 165, 240}; refresh rate 0 or `24..360` Hz; interface scale ∈ {85, 100, 115, 130} %; 3 window modes, 7 window sizes, 3 reaction-effects levels | adjustment construction; Profile load compatibility and the Gameplay preferences projection require `preferences == normalized()`; the Resource refuses explicit defaults, unknown ids and non-members |
| Profile key bindings | 14 actions × 83 bindable keys, one distinct key per action; binding a held key swaps it with its holder; at most 14 non-default wire records sorted by action | `KeyBindings` construction (`of`, `with`); Resource schema |
| Profile Gameplay discoveries per Pulse | captured `itemCount` (at most 320) | Profile validates count and every stable item ID before acceptance |
| Profile snapshot UTF-8 payload | 65536 bytes | Profile Resource before decode and after encode |
| Desktop Preferences value length | 8192 UTF-16 code units | exact platform broker refuses 8193 before provider execution |
| Desktop worst-case Profile payload | 7,711 UTF-16 code units at `MAX_ITEMS` 320 | `ProfilePayloadBudgetTest` encodes every bounded fact at its longest form and requires it to fit one Desktop Preferences value |
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
| `gameplay.item-indexed-state` | item arrays, Reborn flags, status accumulators and discoveries <=320; clause timers/counters <= 320 x 2 | validated contiguous Item IDs; pending is a subset; cardinality-stable COW |
| `gameplay.weapon-indexed-state` | unlocked weapons/mutation counters <=24 | closed WeaponId bootstrap; invariant-preserving COW |
| `gameplay.matrix-indexed-state` | rank slots <=40; folded run modifiers fixed-size | validated closed MatrixNodeId catalog sizes the array; one immutable fold per run start |
| `gameplay.relic-indexed-state` | state arrays <=24; enemy status cells <=120 x 74 | validated RelicId catalog; per-enemy status layout driven by the element count (four 7-wide element blocks, 21 reaction-pair slots, Void memory, Unmake, Blackfire and the three mark cells: kind, expiry and whether a skill cast wrote it) times the enemy cap |
| `gameplay.upgrade-indexed-state` | 12 + 24 run-constant levels plus the run character's level and power | closed CoreShape/WeaponId catalogs; captured once at start, copy-on-write like the Matrix ranks |
| `gameplay.combat-field-table` | 16 records x 22 cells | Content field bound; fixed copy-on-write float table forked on full reductions |
| `gameplay.skill-runtime` | 3 slots x 24 cells + 12 Apex cells + 24 per-effect run damage cells; loadout 3; cast scratch 10 ids | closed slot count and the Content effect bound (`MAX_SKILLS`); fixed copy-on-write float array forked on full reductions; the effect damage outlives a replacement |
| `gameplay.item-deck` | membership <=320 bits and priority ids; history weights from <=64 recent ids; acquired-item report <=64 ids, oldest dropped | dealt once at run start; immutable deck and immutable report replaced on change |
| `gameplay.collision-live-enemy-ids` | <=120 | lazy compact sorted array from bounded live enemies |
| `gameplay.reducer-copy-collections` | equal to bounded committed source cardinality | isolated candidate storage; immutable sharing or owned copies; bounded pending outputs |
| `gameplay.render-projection-collections` | equal to bounded source cardinality | verified predecessor provenance; identity/storage reuse or exact ordered fallback |
| `gameplay.stable-compaction` | retained cardinality <= bounded source; stable survivor order | forward survivor compaction/reverse-tail deletion or index-stable removal |
| `profile.codec-temporary-collections` | unlocked <=24; Matrix ranks <=40; retired Lab ranks =8; discoveries <=400 legacy / <=320 current; recent items <=64; key records <=14; upgrade digits <=12 / <=24 | materialized after schema validation |
| `session.shell-entries` | 1..2 | one base plus one nullable overlay |
| `content.closed-ui-catalogs` | CoreShape 12 / WeaponMastery 4 / EnemyType 19 / SkillId 18 / Apex 12 (one per CoreShape) | bootstrap requires exact stable order |
| `profile.closed-input-catalogs` | GameAction 14 / BindableKey 83 / WindowMode 3 / WindowSizePreset 7 / ReactionEffectsLevel 3 | append-only enums persisted by stable wire id |
| `ui.catalog-backed-sources` | Codex items <=320; Codex enemies <=24; Workshop characters 12, weapons <=24 and Matrix nodes <=40; skill bar 3 slots; mastery closed | validated immutable Content plus bounded Profile state |
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
slot order) written only when non-empty. Rebirth progress carries an optional
sorted `ascendedCharacterIds` list, written only when non-empty, that requires the
final tier cleared and an Architect victory with each listed character. Unknown stable IDs in a current-epoch
payload still fail closed.

Release 0.5.0 adds optional fields to the same schema. Each is written only when
it differs from its default, so a payload without them stays byte-identical:
the preferences `windowModeId`, `windowSizeId`, `vsyncEnabled` (only `false`),
`frameRateCap`, `refreshRateHz`, `immersiveEnabled` (only `true`),
`interfaceScalePercent`, `showFps` (only `true`), `pauseOnFocusLoss` (only
`false`), `reactionEffectsId` and `keyBindings`; the collection's
`recentItemIds`; Rebirth progress's `characterClears`; and the profile's
`upgrades`. An explicitly written default
and an unknown id are refused at decode; a value outside its option set is
refused at decode and at encode, never clamped. `keyBindings` lists only rebound actions as
`{action, key}` records strictly sorted by action wire id; more than 14 records,
a record equal to its default or two actions on one key in the resulting map
fail. `recentItemIds` is chronological (oldest first, repeats kept, at most 64,
each below `MAX_ITEMS`); a retired-epoch payload must carry it empty. `upgrades`
holds one lowercase base-36 digit per slot (`"c"` for characters in `CoreShape`
order, `"w"` for weapons in `WeaponId` order, level 10 written `a`) with
trailing zero levels trimmed; each string is absent while its track is all zero
and the object is absent while nothing is bought. Slot order is frozen and only
appended to. A Lab-era payload that carries upgrades is inconsistent. Loading
clamps a level above a lowered `UpgradePolicy.maxLevel` and refunds the price of
the removed levels into Matter, like Matrix ranks.
`characterClears` holds one lowercase base-36 digit per character in the same
frozen slot order as `"c"`: the highest cleared tier plus one (`0` none, tier
10 written `b`), trailing zeros trimmed, absent while no character records a
clear. Decode refuses a non-digit, a value above `b`, more than twelve digits,
a trailing zero, a tier above the highest clear, a clear without that
character's victory and a tier-10 clear that is not Ascendant.

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
clock, and visual FX remain Interaction Ephemeral. So do the Workshop tab and
segment, Settings key capture, the HUD's one-shot memory of tier, skill and Apex
edges, the running touch tracker and press log, and the world FX draw budget;
none of them feeds a business decision. Delegated Compose State reads
use a `Value` suffix. Stable composition identity uses the owning entity or
screen identity; projection revision is a separate route/lifecycle token and is
not reused as a `key(...)` identity.
