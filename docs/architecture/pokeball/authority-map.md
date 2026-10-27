<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Authority map

This map is the accepted migration decomposition. It assigns semantic
ownership; package placement is the enforcement mechanism, not the source of
ownership by itself.

## Authorities

| Authority | Instance identity and lifetime | Sovereign or captured state | Sole writer / acceptance point |
|---|---|---|---|
| ContentCatalog | application lifetime; singleton typed authority | immutable catalog definitions, stable IDs, `ContentVersion`, policy and UI snapshots | Content bootstrap validates and publishes once; no runtime mutation protocol |
| Profile | `kinetickk.local/Profile/local-player`; application lifetime | complete `PlayerProfile`, `ProfileRevision`, captured `ProfilePolicySnapshot`, bootstrap and persistence status | Profile acceptor publishes one accepted Profile frame |
| GameplayRun | one monotonically allocated `RunId`; at most one active run | simulation and RNG, run phase, captured `GameplayContentSnapshot`, viewport/pointer/brake/preferences, skill and Apex input buffers, revision, at most one pending Profile command | that run's Gameplay acceptor publishes one accepted Gameplay frame |
| AppSession | `kinetickk.local/AppSession/local-session`; application lifetime | base destination, overlay, active `RunId`, projection revision, workflow phase, Rebirth confirmation, bootstrap availability | Session acceptor publishes one accepted Session frame |

`ProfileRevision`, Gameplay revision, and Session projection revision retain
their distinct owner meanings. Equality or derivation never transfers
ownership.

Gameplay publishes its accepted State and matching immutable render snapshot
through one `CommittedGameplayFrame`. The render snapshot's private
projection-source token is mechanical provenance only: it authorizes reuse
against that exact committed predecessor and carries no additional business
fact or writing authority.

## Business-fact ownership

| Fact or decision | Owner | Consumers and legal access |
|---|---|---|
| item, weapon, Core Matrix node, relic, enemy roster, and Rebirth definitions/policy | ContentCatalog | typed Content queries and captured immutable snapshots |
| skill, Apex and upgrade definitions (`SkillDefinition`, the active item carrying each effect (`ItemDefinition.active`), each character's `activeLane`, `ApexDefinition`, `UpgradePolicy`, each character's `upgradeSignature`); the level requirement table, level cap, level integrity and repair, `rebirthScaleFromLevel` and `RebirthProfile.dataRequirementScale`; reaction values, verbs, cues and keystone forms; the item rule vocabulary, `ItemDefinition.keystone`, `isDataEconomy` and the conversion constants; `BuildPolicy.maximumSkillFields`, `DraftDeckPolicy` and `OfferLanePolicy` with its active offer factors; Matrix effects including `DataStratum` and `DataOfferWeight` | ContentCatalog | captured into each run's `GameplayContentSnapshot` and into Profile's policy snapshot; Interaction formats their texts and never evaluates their rules |
| preferences and mute state, including display, frame-pacing, focus, reaction-effects and key-binding preferences | Profile | Profile queries; captured into Gameplay after accepted Session workflow; the platform display host receives accepted display values and applies them mechanically |
| economy, permanent Core Matrix ranks, cumulative achievements, loadout, collection, Rebirth progress | Profile | target-owned Profile queries; Gameplay captures the run-bootstrap result through a read capability, while later preferences arrive as explicit values in typed settings calls |
| Refit and Honing levels and their purchases; the recent item history (the newest 64 first acquisitions); Workshop affordability | Profile | `PurchaseCharacterUpgrade` and `PurchaseWeaponUpgrade` decide ownership, maximum and Matter; Gameplay captures the levels and the history at `startRun`; Home reads `HomeProgressProjection.workshopAffordable` and `upgradeLevels`, the Workshop reads `LoadoutProfileSnapshot.upgrades`, and prices come from Content `UpgradePolicy.nextCost` |
| current `ProfileSnapshot` bootstrap and persistence outcome | Profile | Resource exposes only `readSnapshot`/`writeSnapshot` and typed outcomes; Session reads bootstrap/persistence status |
| live simulation, deterministic RNG, run matter, discoveries, active weapon, Codex stacks, run terminal result | GameplayRun | Gameplay queries and target-owned result to Session/Profile routes |
| skill loadout (rank = the carrying active item's stacks; only item acquisition and replacement write it), `SkillRuntime` with the Apex cells and each effect's run damage, the incoming active item waiting for the slot prompt (`pendingActiveItemId`, `pendingActiveReborn`: owned run State until that choice is accepted, isolated in every reduction candidate), the combat field table, enemy marks, Core reaction state, aftershock slots, the compiled build (keystone mask, vocabulary clamps, dormant items), the run's draft deck and recent-item weights, the acquired-item report, applied Refit and Honing, Apex boss damage | GameplayRun | render model (`skillBar`, `apex`, `combatFields`, control bits, `coreState`, `maxLevel`, and the choice cards' active facts: `activeSlot`, `replacesActive`, `incomingItemId`, `salvageMatter`) and `GameplayBuildSummaryProjection` (`dormantItemIds`, `outOfDeckItemIds`, upgrade levels); acquired item ids reach Profile only inside the accepted progress update |
| navigation, overlay policy, start/restart/exit ordering, settings propagation, Rebirth orchestration, bootstrap-unavailable lifecycle, the character-selection gate | AppSession | Session projection (including `characterSelectionAvailable`) consumed by Session Interaction; the Workshop asks for a character only while selection is available |
| UI page, focus, viewport gesture mechanics, animation clock, local visual FX | owning Interaction role | never consumed as hidden business input; a business-relevant value becomes a Pulse, Context field, or committed State |
| Workshop tab and segment, Settings key capture, HUD one-shot memory (`HudPresentationMemory`), running touch tracker and press log, world FX looks and draw budget | owning Interaction role (Profile Interaction for the Workshop and Settings, Gameplay Interaction for the HUD and FX) | presentation only; the HUD derives its tier from the render model's level and `maxLevel`, its control mask from `skillBar` and `apex.unlocked`, and lingering visuals only from the render model; key capture writes nothing but typed Profile adjustments |
| cue-to-tone meaning | originating Ball's private audio executor | mechanical `ToneRequest` only crosses into Audio Resource |
| tone validation and bounded playback request selection | Audio Resource | receives only typed `ToneRequest` and a narrow playback capability; no provider acquisition |
| platform storage/audio provider acquisition and mechanical execution | instance-owned platform capability brokers / Execution-Gate mechanics bound by AppAssembly | one platform broker is the sole physical storage authority for the current Profile key; Profile/Audio retain Resource operation semantics, Assembly selects and binds only, gains no effect/policy authority, and broad provider types never enter a Resource constructor |
| platform display options and their mechanical application (window decoration, placement and size, V-Sync, Android display mode and system bars, browser fullscreen) | platform display host bound by AppAssembly | Profile owns the chosen values; Settings Interaction reads offered options and live window state through `SettingsDisplayPort`; Session Interaction propagates accepted preferences to the host and applies frame pacing and the interface scale in presentation; the host never writes Profile; browser fullscreen is browser-owned live state and is never stored |

The closed platform bindings are actuals inside the `app:shared` KMP leaf:
`androidMain` binds Android SharedPreferences/AudioTrack, `desktopMain` binds JVM providers, and
`wasmJsMain` binds browser providers. The separate `app:android` leaf is a pure
application/packaging host with exactly one production project edge to
`app:shared`; it adds no authority, business fact, writer, or semantic route.
For Profile persistence, those mutually exclusive platform bindings select
exactly one current location: Android preferences `kinetickk.profile.v2` key
`snapshot`, Desktop node `kinetickk/profile-v2` key `snapshot`, or Web key
`kinetickk_profile_v2`. No Ball or Flow can select another storage root or key.

Home and Codex may combine independent Profile and Gameplay reads only in
Session Interaction and must label the result non-atomic. Assembly never joins
those reads into a fabricated business snapshot. The Codex's Dormant and Not in
this run statuses and its Refit and Honing facts are Gameplay reads of the
current run; Home's Workshop stamp and bought-level count are Profile reads.
Interaction keeps only the upgrade level cap for layout.

Keyboard input has one owner per phase. While the base is Gameplay and no
overlay is open, Session Interaction lets every key reach Gameplay Interaction
first; Gameplay Interaction consumes only keys that Profile's `KeyBindings` map
to an action its phase accepts (while paused, only Pause and the Brake release),
and every other key bubbles back to the Session shortcuts. Over Home or an
overlay the Session shortcuts apply as before.

## Physical role ownership

| Role | Physical modules |
|---|---|
| shared mechanical Foundation | `foundation:common`, `foundation:design` |
| mechanical audio Resource | `resource:audio:api`, `resource:audio:impl` |
| Content | `ball:content:api`, `ball:content:impl` |
| Profile | `ball:profile:api`, `ball:profile:nucleus`, `ball:profile:resource`, `ball:profile:interaction`, `ball:profile:impl` |
| GameplayRun | `ball:gameplay:api`, `ball:gameplay:nucleus`, `ball:gameplay:interaction`, `ball:gameplay:impl` |
| AppSession Flow | `flow:session:api`, `flow:session:nucleus`, `flow:session:interaction`, `flow:session:impl` |
| construction and packaging hosts | `app:shared` (shared KMP Compose Assembly and platform bindings), `app:android` (pure Android host; only production project edge is `implementation -> :app:shared`), `app:desktop`, `app:web` |

Nucleus modules depend on neither Compose, platform APIs, nor Resource
implementations. Assembly constructs components and binds declared routes; it
does not branch on business state or construct business payloads.

The physical implementation remains one Profile component and one active
Gameplay host, without façade fan-out. Only `app:shared` Assembly may hold the
Impl composites `ProfileComponent` and `GameplayCompositionComponent`.
Assembly supplies target-owned read, settings, loadout, rebirth, progress,
run-lifecycle or presentation capabilities according to each consumer's work.
Read capabilities expose immutable projections and no mutation authority;
`ProfilePort` additionally admits local Profile interactions and stays within
that owner and Assembly. The API keeps no registry of consumers. Immediate
command results enter the source's serialized handler through the shared call
scope, which rejects duplicate and late completion before another invocation
can consume it. Business phases and result interpretation stay in the source
Nucleus; target acceptance and persistence stay in the target owner.

## Writer and transition rules

Every mutation is a closed target-owned Pulse evaluated by one pure,
deterministic, terminating Nucleus. Rejection publishes nothing. Acceptance
materializes a complete immutable next State and ordered immutable output batch,
then the acceptor publishes State/revision exactly once before dispatch. A
same-state accepted Decision still advances the owning revision when the
protocol defines acceptance.

No acceptor is reentrant. Owners reuse the Foundation Inline acceptance and
completion mechanism. A command result enters serialized source processing
after its target call returns and stays retained until the source accepts its
complete frame. Completion context belongs to that current input. Business
State, decisions, bounds and effect handling remain owner-specific. Gameplay
publishes its State and consistent render snapshot together. Resource outcomes
become typed owner inputs; neither uncertainty nor a later execution fault
rewrites an already accepted frame.
