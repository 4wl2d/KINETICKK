<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Architecture

This page says where code lives, which way dependencies point, and which rules the build enforces. Read it before
adding a module, a package, a gameplay system, a renderer, a sound or a setting, and before changing the frame loop or
the render contract. Rules marked **[checked]** fail `./gradlew verifyArchitecture`, which CI runs on every push; the
others are review rules. Build and release steps live in [DEVELOPMENT.md](DEVELOPMENT.md), balance rules in the
[balance policy](../balance/policy.md), the Ballarch evaluation in [BALLARCH-0.6.0.md](BALLARCH-0.6.0.md).

## 1. Modules

| Family | Modules | Owns |
|---|---|---|
| app | `:app:shared` (composition root), `:app:android`, `:app:desktop`, `:app:web` (platform hosts) | wiring, platform capabilities (profile storage, tone playback, display), startup resources |
| foundation | `:foundation:common`, `:foundation:design` | values, collections, seeded random, the dispatch runtime, diagnostics hooks; design tokens, typography, draw primitives, shared widgets |
| resource | `:resource:audio:api`, `:resource:audio:impl` | the sound catalog `SoundId` and `AudioService` (api); tone synthesis, the tone table `ToneCatalog` and playback (impl) |
| content | `:ball:content:api`, `:ball:content:impl` | static game data: definitions, ids, rule text and phrase tables (api), shipped catalogs (impl) |
| profile | `:ball:profile:{api,nucleus,interaction,impl}` | the persistent player profile: decider (nucleus), owner, codec and storage (impl), its screens (interaction) |
| gameplay | `:ball:gameplay:{api,nucleus,interaction,impl}` | one run: public commands and `GameplayRunHost` (api), simulation and render contract (nucleus), renderer (interaction), run owner (impl) |
| session | `:flow:session:{api,nucleus,interaction,impl}` | navigation between home, codex, profile screens and runs |

The build has 22 leaf modules. Module suffixes mean:
- `api`: public types and ports of a family. No UI, platform or effects.
- `nucleus`: pure, deterministic logic (gameplay simulation, profile and session deciders). No wall clock, IO,
  coroutines, reflection, platform or UI imports **[checked]**.
- `interaction`: Compose UI and Canvas rendering.
- `impl`: the owner that runs a nucleus, executes its effects and builds the feature for `:app:shared`.

Dependency rules **[checked]**:
1. The leaf modules are exactly the list in `build-logic/src/main/kotlin/kinetickk/gradle/ArchitectureModules.kt`.
2. No `:core:*` or `:feature:*` modules.
3. Each platform host depends in production on `:app:shared` only.
4. No `impl → impl` edge, tests included.
5. Production edges from `api` and `nucleus` modules target only `api` modules or `:foundation:common`.
6. Production nucleus sources import no effect API (platform, UI, IO, coroutines, reflection, wall clock, ambient
   randomness, `foundation.design|diagnostics|dispatch`), and call no ambient randomness (`Random.nextX()`,
   `Random.Default`, `.random()`, `.shuffled()`, `.shuffle()` without a seeded `Random`).
7. Production `api` sources import no platform, UI, IO, coroutine, reflection, wall-clock or design/diagnostics API.
8. The root project has no `src/`.

Adding or removing a module: add it to `settings.gradle.kts` **and** `ArchitectureModules.kt`; the suffix opts it into
rules 4, 5 and 6. Build logic must stay configuration-cache and Isolated-Projects safe (root tasks read other modules
only through `rootProject.fileTree(<path>)` or published artifacts). Add a module only when it removes a real cycle or
a real layering problem; package moves inside a module need no visibility changes because `internal` is module-wide.

## 2. A gameplay run

- **Lifecycle owner**: `GameComponent`
  (`ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt`). Start, pause for an
  overlay, exit, settings and the profile-progress handoff go through `GameplayNucleus.decide` and keep the
  decide/typed-reply style (§7).
- **Frame loop**: once per display frame the host (`GameplayContent`'s frame effect in
  `ball/gameplay/interaction/src/commonMain/kotlin/kinetickk/ball/gameplay/interaction/GameInteraction.kt`) calls
  `GameplayInteractionPort.accept(FrameElapsed)`; inputs arrive through the same port. `GameComponent` calls
  `GameplayNucleus.tick`, which admits the pulse, runs the due fixed 120 Hz steps in place on the single run state and
  drains FX and sound cues into the owner's reusable `GameplayTickOutputs`. The owner then publishes a render snapshot
  (restamped when the tick changed nothing visible), hands the FX cues to the interaction FX reducer, plays the cues
  and advances music once per accepted frame. Only a tick that publishes profile progress takes the ordered
  FX → profile → audio route with its typed completion. No per-frame copy of the state, no per-frame decision
  envelope. `InlineAcceptance` (`foundation.dispatch`) guards reentrancy; an exception mid-step stops the run.
- **Test entry**: `GameplayNucleus.decide(state, Intent(pulse))` runs the same reducer core on a deep copy and never
  mutates its input. It exists for tests and tools; production frames and inputs never call it.
- **Run state**: `MutableGameState`
  (`ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/MutableGameState.kt`) is the
  one run state, with plain eagerly copied storage. Cohesive groups live in state holders, each written by exactly one
  domain: `StepScratch` (`simulation`, per step), `PendingChoiceState` (`progression`), `SkillCastState` (`skills`),
  `CoreReactionState` (`elements`), `RelicMechanicState` (`build`), `SpawnDirectorState` (`director`) and
  `StructureRunState` (`structures`). The other root fields move into holders one domain at a time (§12).
- **Forwarders**: `MutableGameStateForwarders.kt` holds extension properties that forward the old root field names to
  their holder, so older call sites, tests and fingerprints stay source-identical. They are legacy: new code uses
  `state.<holder>.<field>`, the forwarder list may only shrink, and a new field never gets a forwarder.
- **Render contract**: `kinetickk.ball.gameplay.nucleus.render` (render model, projections, domain enums, formatting)
  and `kinetickk.ball.gameplay.nucleus.protocol` (FX and sound cue types). Interaction code reads nucleus state only
  through these packages **[checked]**, and these packages import no other nucleus package **[checked]**.
- **Reward previews** take one explicit deep copy of the run state (`deepCopy()`); a preview never writes run-level
  lists or opens anything. The same copy isolates a tick while profile progress is still pending.
- **Notices**: the nucleus still publishes English message strings (the render model's `message` and the build
  notices) that the HUD feed parses (`HudFeedMessages.kt`, `BuildNoticeTitles` in `InteractionFxReducer.kt`).
  Replacing them with typed notices is the next step (§12). Do not add new string parsing or new parsed message
  formats: add a typed render-model field, or a plain title that the feed shows as it is.

## 3. Packages inside the big modules

Rules for every module:
- The package equals the directory below `src/<sourceSet>/kotlin/` **[checked]**.
- At most 25 `.kt` files directly in one source directory **[checked, ratchet]**: give a growing area its own
  sub-package instead of a flat dump.
- No package import cycles inside a module **[checked, ratchet]**. The exception is the gameplay simulation domains
  under `kinetickk.ball.gameplay.nucleus.simulation`, which may depend on each other freely; the build treats that
  whole subtree as one node, so `model`, `render`, `protocol` and the nucleus root must still not form a cycle with it.
- A feature's `impl` package is private to that feature **[checked, ratchet]**: `x.impl` may be imported only from
  `x` and its sub-packages, and from the composition root `kinetickk.app`. Share through `x.api` or a neutral package.
- Leaf packages stay leaves: interaction `fx`, `layout` and `localization` import no other package of their module.

`:ball:gameplay:nucleus` (`kinetickk.ball.gameplay.nucleus`):

| Package | Holds |
|---|---|
| root | the nucleus entry `GameplayNucleus` (lifecycle `decide`, frame/input `tick`, render snapshots), `GameplayState`, `GameplayDecision`, `GameplayTickOutputs` |
| `reducer` | `GameReducer`: pulse admission and the in-place step core shared by `tick` and the pure `decide(Intent)` wrapper |
| `model` | plain state data, runtimes and pure value helpers used by the simulation |
| `simulation` | the run state, its holders and forwarders, the step order and frame admission (`GameLoop.kt`), run start and reset (`GameSession.kt`), cue output (`SimulationOutput.kt`), shared math and engagement |
| `simulation.<domain>` | one package per domain: `flight`, `combat`, `elements`, `enemies`, `director`, `variety`, `weapons`, `characters`, `skills`, `build`, `progression`, `world`, `structures`, `sites`, `dimensions` |
| `projection` | state → render-model mapping (`GameplayRenderModelMapper` and the site, structure and dimension mappers) |
| `render`, `protocol` | the render contract (§2) |

Direction inside the nucleus (review rule; the contract part is checked): `render` and `protocol` import no other
nucleus package, and `model` may import `render` (the domain enums live there). `simulation` and its domains import
`model`, `protocol`, `render` and each other, never `projection`, `reducer` or the root. `projection` imports
`simulation`, `model`, `render` and `protocol`. `reducer` and the root may import anything in the nucleus.

`:ball:gameplay:interaction` (`kinetickk.ball.gameplay.interaction`): the root hosts `GameplayContent`, the frame
loop, semantic controls, the build button, the frame compositor (`GameplayRenderer.kt`), the port, the crash context
and window preferences; `input` (validation, key and pointer mapping, the touch tracker); `layout` (pure geometry and
HUD metrics); `localization`; `fx` (the FX model: reducer, projections, reaction and skill tables); `canvas` (shared
canvas math `CanvasMath`, canvas geometry, cached world paths, canvas text fit); `canvas.look` (visual identity:
visual semantics, glyphs, sigils, item and relic icons, Core look and badges, also used by the Codex);
`canvas.world` (world renderers, the world layer order in `WorldCompositor.kt`, edge-marker planning);
`canvas.world.places` (sites, structures, dimensions, obstacles, events, edge markers); `canvas.fx` (the FX painter
and its pools, reaction FX, the Core ribbon, damage-number images); `canvas.hud` (HUD panels, the feed, minimap,
draw cache, FPS counter); `overlay` (shared overlay kit, pause build overview, status overlay, run report); `rewards`
(reward overlay, cards, relic and totem panels, chips, presentation) and `rewards.art` (card art).

Direction inside the interaction module (review rule):
- `fx`, `layout` and `localization` import no other package; `input` imports only `layout` and `localization`.
- `canvas` and its sub-packages import only each other, `fx`, `layout` and `localization`. They still import each
  other in one cycle; the `package-cycles` ratchet keeps it from growing.
- `overlay` imports only `canvas`, `canvas.look`, `canvas.fx`, `fx`, `layout`, `localization`, `rewards.art`, and
  `input` for the `GameplayInput` actions its report buttons emit.
- `rewards` and `rewards.art` import only `overlay`, `canvas`, `canvas.look`, `canvas.fx`, `fx`, `layout` and
  `localization` (and `rewards` imports `rewards.art`).
- The root may import anything.

`:ball:profile:interaction` and `:flow:session:interaction`: one package per screen feature with `api` (what other
features and `:app:shared` may use) and `impl` (private). The profile workshop keeps its `api` and `impl` plus the
`workshop.armory`, `workshop.hangar` and `workshop.matrix` screens. Shared widgets belong in `:foundation:design`,
shared module-local helpers in a neutral package of the module. `:ball:profile:impl` holds the owner in
`kinetickk.ball.profile.impl` and the codec and storage in `kinetickk.ball.profile.impl.storage`.

`:ball:content:api`: one flat package of definitions, ids, rule text and Russian phrase tables, split into files per
content family; tuning is a data class with defaults (the `EasyModeTuning` pattern in `RunModeContent.kt`). Shipped
data belongs in `:ball:content:impl`. Mechanical constants shared by rule text and the simulation are defined once in
the content model, never duplicated as literals.

## 4. Where new code goes

| Change | Touch, in this order | Prove it with |
|---|---|---|
| New enemy | `EnemyType` and an `EnemyDefinition` entry in `defaultEnemyDefinitions()` (`ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/EnemyContent.kt`); behaviour in `simulation.enemies` (`EnemyBehaviors.kt`, `EnemyBehaviorsWorlds.kt`, bosses in `MiniBossBehaviors.kt` / `SecretBossBehaviors.kt`); projection only if it needs new visible state (`EnemyProjection` in `render/GameplayRenderProjection.kt`); drawing in `canvas/world/EnemyRenderer.kt` and `enemySilhouette` (`canvas/look/GameplayVisualSemantics.kt`); Russian name in the content phrase tables; Codex entry | behaviour test in the enemies package; `EnemyWorldsAllocationTest`; affected balance gates (spawn weights change outcomes) |
| New item | `ItemEffect` / `ItemDefinition` (`ContentDefinitions.kt`), an `ItemDraft` in `:ball:content:impl`, appended after the last draft list that `defaultItems()` concatenates (`ball/content/impl/src/commonMain/kotlin/kinetickk/ball/content/impl/ItemCatalogAssembly.kt`; item ids are positions in that order and profiles store them); compile in `simulation.build` (`BuildCompiler.kt`); rule text via the content rule writer (`BuildRulesText.kt`) and Russian phrases (`ItemPhrasesInRussian.kt`); icon in `canvas/look/ItemIconRenderer.kt` | build compile test; rule-text test; `RewardCardFaceAllocationTest` if the card face changes; item gates under the balance policy |
| New site | `SiteKind` and `SiteDefinition` (`SiteContent.kt`); rules in `simulation.sites` (`updateSites` in `SiteSystem.kt`); `SiteProjection` (`ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/render/SiteProjection.kt`) plus its mapper (`projection/SiteRenderMapping.kt`); drawing in `canvas/world/places/SiteRenderer.kt`, glyph in `canvas/look/SiteGlyphs.kt`, edge marker in `canvas/world/places/EdgeMarkerRenderer.kt` | site tests; the opt-in site pilot gates; world hot-path allocation test |
| New renderer / visual layer | a file in the matching `canvas.<layer>` package, called from the compositor at its place in the layer order (`drawGameplay` in `GameplayRenderer.kt`, world layers in `drawWorld`, `WorldCompositor.kt`); read only the render model and the FX projection; colours, strokes and type from `:foundation:design` tokens; cached paths/brushes/text slots (§8) | extend the matching `*HotPathAllocationTest`; a `RedesignShots*` screenshot smoke |
| New sound | a semantic cue in `GameplayAudioCue` (`nucleus.protocol`), its `SoundId` entry (`resource/audio/api/src/commonMain/kotlin/kinetickk/resource/audio/api/SoundId.kt`) and mapping in `GameplayAudioExecutor.kt`, its tone and priority in the one tone table (`resource/audio/impl/src/commonMain/kotlin/kinetickk/resource/audio/impl/ToneCatalog.kt`); emitted from the simulation through `emitSound` (`SimulationOutput.kt`), never from UI code; no new mixer or bus | cue order/priority test; `DefaultAudioServiceTest` when the service changes |
| New setting | field in `PlayerPreferences` (`ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/PlayerProfile.kt`); persisted with a default for old saves in `ProfileCodec.kt` (`ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/storage/ProfileCodec.kt`); UI in `settings.impl` (`SettingsState.kt` and its renderer/layout); applied to a running game through the session's settings workflow and the lifecycle owner's apply-preferences command | codec round-trip and old-save test; settings compose test; session workflow test |
| New gameplay system or mechanic | a file in the owning `simulation.<domain>` package; state in the owning holder (§6); one call at its place in `simulateStep` (`GameLoop.kt`); tuning knobs in content with an `enabled` flag when the owner may want to turn it off | unit tests in the domain package; retained-state checklist (§6); gate impact per §9 |
| New module | §1 checklist | `verifyArchitecture` |

`simulateStep` stays one linear function in the root `simulation` package, in today's call order: insert a new
update as one call at its place, and never extract, wrap or reorder the statements around it.

## 5. Determinism

The simulation must produce the same run from the same seed, profile and inputs on every platform; balance gates
depend on it.
- Time comes only from the fixed step (120 Hz). No wall clock, `TimeSource`, frame time or platform call inside the
  nucleus **[checked]**. Rendering interpolates between steps; it never feeds back into the simulation.
- Randomness comes only from the run's salted streams (on `MutableGameState`: `gameplayRandom`, `rewardRandom`,
  `spawnRandom`, `siteRandom`, the deck seed) or from stateless hashes (`worldHash` / `HashRandom` in
  `simulation/world/WorldHash.kt`) with a new salt. Never `Random.Default` or unseeded collection helpers
  **[checked]**.
- A new mechanic that needs no randomness draws from no stream. Adding, removing or reordering a draw on an existing
  stream shifts every later draw: treat it as a balance change (§9). Prefer a new salt for new randomness, as
  structure placement and obstacle generation do.
- Never decide anything from `HashMap`/`HashSet` iteration order or `hashCode()`; iterate lists, enum-indexed arrays,
  `mutableMapOf` (insertion-ordered) or sorted keys. Membership checks on hash sets are fine.
- Previews and forks never write live state; the live run has exactly one writer per run-level list.

## 6. Retained gameplay state

Every new field that survives a step:
1. lives in the state holder of the system that writes it (one writer), accessed directly, with no forwarder;
2. is reset at run start (`startRun` in `GameSession.kt`);
3. is rebased if it stores a world position (`rebaseWorldIfNeeded` in `WorldRebaseSystem.kt`);
4. joins the fingerprint witnesses: the exact simulation fingerprint (`exactSimulationFingerprint` and
   `populateAdditionalRetainedState`, the nucleus witness fixtures used by `GameplayReductionIsolationTest`) and the
   benchmark `canonicalStateFingerprint` plus its witness test (`GameplayPerformanceBenchmarkTest`);
5. if the player can see it, becomes a typed `GameplayRenderModel` field filled by the mapper (not a message string);
6. if it is a collection, has an explicit `MAX_*` bound and a defined behaviour when full;
7. has tests for each lifecycle edge it can meet: layer switch, Architect collapse, rebase, structure-window move, run
   reset, preview fork, pause/choice freeze.

Homes for planned mechanics. None of these mechanics exists yet; when one lands, use these places instead of
inventing new ones:
- Run-long state of dungeon structures and their rewards (for example a bounded list of claimable chests) goes into
  `StructureRunState`, written only by the `structures` domain.
- State that tracks how the Core moves over a run (for example a meter for standing still) goes into a new holder
  `CoreStasisState` in `simulation.flight`. It is reset next to the Core motion resets in `startRun` and rebased in
  `WorldRebaseSystem.kt`.
- Values the player sees become top-level fields of `GameplayRenderModel`
  (`ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/render/GameplayRenderProjection.kt`),
  always constructed with named arguments; a list replaces a single projection in the same position.
- Presentation-only events (for example a structure breaking apart) become a new `VisualFxCue` type
  (`nucleus.protocol`) and a list on the interaction `VisualFxProjection`, drawn from an `FxPool` budget in
  `canvas/fx/FxPainter.kt`.
- A new sound follows the "New sound" row of §4.

## 7. Ballarch: where decide/complete applies

Use the Ballarch-style split (pure decide → typed decision → owner executes effects → typed reply) only where
durability or cross-owner coordination matters:
- profile persistence (`ProfileNucleus` decide/complete, `DefaultProfileComponent`): keep;
- session navigation (`AppSessionNucleus`): keep the decider, without ceremony that has no second implementation;
- gameplay lifecycle commands (start, pause, exit, settings, progress handoff): keep typed replies.

Never for per-frame simulation, continuous input, rendering, FX or audio: those are plain in-place computation
(`GameplayNucleus.tick`). Determinism and testability come from pure functions over explicit state, not from
envelopes, revisions or copies. Do not add a new decide/complete layer, protocol wrapper or identity type without a
second real implementation or a durability need.

## 8. Hot paths

Per-frame code: the simulation tick, input ingestion, render-model refresh, Canvas drawing, FX advance and audio
advance.
- Steady-state frames allocate nothing: no collection builders, capturing lambdas, boxing, string building or
  formatting, `Regex`, `copy()` of data classes, or new `Path`/`Brush` objects. Reuse buffers owned by the state or the
  renderer; cache paths, brushes and text layouts by stable keys (`HudDrawCache`, `WorldPaths`, `KkPathCache`,
  `measureKkText`); numbers through the cached number text helpers; damage labels through `DamageNumberImages`.
- New FX report their draw ops to the `FxDrawBudget` pools and respect the crowd level of detail in `FxPainter`.
- Validation (`check`/`require`) belongs at lifecycle boundaries, not per entity per frame.
- Allocation guards: `WorldHotPathAllocationTest`, `HudHotPathAllocationTest`, `KkHotPathAllocationTest`,
  `RewardCardFaceAllocationTest`, `CharacterRuntimeAllocationTest`, `CombatFieldAllocationTest`,
  `EnemyWorldsAllocationTest` and the owner-level `GameplayDispatchAllocationTest`. Extend the matching one when you
  add a renderer or a per-frame system; their thresholds may only go down.
- A hot-path change ships with before/after numbers from the matching benchmark, Release against Release:
  `:ball:gameplay:nucleus:performanceBenchmark`, `:ball:gameplay:impl:componentPerformanceBenchmark`,
  `:ball:gameplay:interaction:performanceTelemetryBenchmark`, `:ball:gameplay:interaction:reactionDesktopBenchmark`,
  `:ball:gameplay:interaction:rewardDesktopBenchmark`, `:ball:profile:impl:profilePerformanceBenchmark`; compare
  with the tools in [performance tools](../../tools/performance/README.md).

## 9. Tests and balance gates

- A test lives in the same module and package as the code it tests and may use `internal` members. Shared fixtures
  live in a fixture file of that package.
- Test sources must keep the shape the CI scanners understand: one top-level `class` per test at column 0, indented
  `@Test` functions, no test base classes, nested test classes, `object` holders or parameterized tests
  (`tools/ci/desktop_test_union.py`, `DesktopTestShardsTest`).
- Seeded balance gates are listed by class name in `build-logic/src/main/kotlin/kinetickk/gradle/DesktopTestShards.kt`.
  Renaming, splitting or moving a gate class updates that list; moving it to another module also updates the shard's
  `tasks` in `.github/workflows/ci.yml`. Other new test classes run in the `rest` shard automatically.
- A change to gameplay numbers, spawn/reward rules or the order or number of random draws must keep every gate passing
  under the [balance policy](../balance/policy.md); record any restated gate in [baseline.md](../balance/baseline.md).
  Never pick seeds by outcome. Measurement harnesses that script slow movement must explicitly disable any mechanic
  that reacts to the Core standing still.
- A behaviour-neutral refactor proves itself by identical per-test output of the desktop suite before and after.
- Every bug or exploit fix comes with a regression test that fails without the fix.
- Run before pushing: `./gradlew :build-logic:test verifyArchitecture` plus the affected modules' `desktopTest`;
  `wasmJsBrowserTest` when common code changes; `-Pkinetickk.gradle.isolatedProfile=true --isolated-projects` when
  build logic changes.

## 10. Size limits and ratchets

| Rule id | Limit | Scope |
|---|---|---|
| `main-file-lines` | 600 physical lines | production `.kt` files |
| `test-file-lines` | 1,000 physical lines | test `.kt` files |
| `function-lines` | 100 lines from the `fun` line to the end of the body | production functions; pure data tables written as `= listOf(...)`-style calls or `= when (x) { a -> value }` without any lambda or block are exempt |
| `package-files` | 25 `.kt` files | one production source directory |
| `renderer-nucleus-imports` | 0 | interaction modules importing nucleus packages outside the render contract |
| `feature-impl-imports` | 0 | imports of another feature's `impl` package |
| `package-cycles` | 0 packages in cycles | production package import graph per module |
| `package-directory`, `contract-imports`, `function-scan`, `rule-config`, `architecture-doc-paths` | hard, never baselined | see §11 |

Aim lower than the limits: most files stay under 400 lines and most functions under 40. The function heuristic does
not measure property accessors, `init` blocks or lambdas stored in properties; do not move long logic there to dodge
the limit.

The baseline `source-structure-baseline.tsv` in `build-logic/` lists every item that exceeded a limit when the rules
landed, with its size as a ceiling. A listed item may shrink but never grow; an item not listed must stay within the
limit. The ceilings only go down:
- `./gradlew updateSourceStructureBaseline` lowers ceilings to the current sizes, deletes rows that are now within the
  limit, and carries a row when a listed file or function moved (same file name, same function name, not larger). It
  never adds or raises a row and fails if anything still violates.
- `-Pkinetickk.sourceStructure.resetBaseline=true` rewrites the file from scratch. Use it only when the maintainer asks
  (a reviewed restatement such as the end of a large refactor) and list the new rows in the change summary.

## 11. Reading a verification failure

`verifyArchitecture` runs `verifyModuleGraph` (module edges, nucleus and api import sensors) and
`verifySourceStructure` (everything in §10). A structure failure prints one block per rule and writes the same text,
plus every baseline row with its current slack, to `build/reports/source-structure/report.txt` (`build-debug/…` for
Debug builds):

```
Source structure verification failed with 3 violation(s). Baseline: build-logic/source-structure-baseline.tsv. Rules: docs/project/ARCHITECTURE.md (section "Size limits and ratchets").
function-lines (limit 100 lines; a baselined item may not grow):
 - ball/gameplay/…/SiteSystem.kt#updateSites (line 64): 131 lines, not baselined
   Fix: Extract named helpers per phase; split composables into child composables.
main-file-lines (limit 600 lines; a baselined item may not grow):
 - ball/gameplay/…/HudRenderer.kt: 634 lines, ceiling 612
   Fix: Split the file along a cohesive section (a type, a layer, a panel, a data family) into a new file in the same package or a new sub-package. Do not raise the ceiling.
stale baseline rows:
 - main-file-lines ball/…/OldRenderer.kt: within the limit or gone
   Fix: run ./gradlew updateSourceStructureBaseline (it only lowers ceilings, deletes stale rows and carries moved items).
Full report: build/reports/source-structure/report.txt
```

| Message | Meaning | Fix |
|---|---|---|
| `…, not baselined` / `…, ceiling N` | a new item over the limit / a listed item grew | split the code; never raise the ceiling |
| `stale baseline rows` | you improved something | run `updateSourceStructureBaseline` and commit the file |
| `looks moved from …` | a listed file or function changed path | run `updateSourceStructureBaseline` to carry its ceiling |
| `renderer-nucleus-imports` | interaction code imports simulation/model/reducer state | add what you need to the render contract, or move a pure helper to `:foundation:common` |
| `contract-imports` | a render-contract file imports nucleus internals | move the type into the contract, or project it in the mapper |
| `feature-impl-imports` | a feature reaches into another feature's `impl` | move the shared symbol to that feature's `api` or a neutral package |
| `package-cycles` | packages import each other (the message names one import per edge) | move the shared symbol into a lower package both can import |
| `package-directory` | package and directory differ | move the file or fix the declaration |
| `function-scan` | braces did not balance after masking literals (a brace inside a nested string template) | move that literal out of the template into a `val` |
| `rule-config` | a package named in `SourceStructureRules.kt` no longer exists | update the constant to the current package name |
| `architecture-doc-paths` | this page names a path that no longer exists | update this page |

## 12. Known next steps

- **Typed notices**, replacing English message parsing between nucleus and UI: an `enum class RunNoticeId` plus
  `RunNotice(id, args)` on the render model, keeping `message` as a fallback; migrate one notice family at a time,
  each with an English and Russian golden of the rendered feed line; then split the content text by family.
- **Render contract in the gameplay api**: move the render contract and FX cues to `:ball:gameplay:api` so that
  interaction drops its nucleus edge. Do it with the next render-contract change; the import rules already hold the
  boundary.
- **FX drawn from reducer state**: a read-only `VisualFxView` over the interaction FX reducer in place of the copied
  `VisualFxProjection` data classes, with frame invalidation by revision. Do it with the FX redesign.
- **A simulation facade**: a `GameplaySimulation` type wrapping `GameplayNucleus.tick`, a port change and the removal
  of the `FrameElapsed` pulse. The tick is the seam it wraps.
- **Re-packaging**: design sub-packages; content, session and test packages, with tests mirroring their main
  packages. Do it with the redesign, or in one scripted pass that updates `DesktopTestShards.kt`.
- **Domain enums**: they stay in `render`. Move them only into a new contract vocabulary package (then forbid
  `model → render` imports), never into `model`, which the contract may not import.
- **More holders**: sites, variety, world, dimensions, flight, outbox, presentation, stats, inventory and discovery
  state; then move call sites off the forwarders, one domain at a time.
- Forbid `api(...)` edges from an `impl` module to an `interaction` module as a module rule.
- Enforce the interaction package direction (§3) as an import rule once the layout has been stable for a release.
- Enable `allWarningsAsErrors` module by module after a build reports zero warnings.
