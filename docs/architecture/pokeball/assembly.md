<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# AppSession Assembly

Assembly is the sole source for cross-authority route wiring. Protocol types are
owned by their target or producer in typed Kotlin; this document names and binds
them without redefining their payloads.

## Graphs

The compile-time import and same-stack direct-control graphs are both finite and
acyclic:

```text
AppSession -> GameplayRun -> Profile -> ContentCatalog
AppSession -> Profile
AppSession -> ContentCatalog
GameplayRun -> ContentCatalog
```

The architecture verifier derives this direct-control graph from every foreign
Application-Surface production dependency and import, then requires exact
equality with the typed read/command rows below. It also scans production uses
of every target-owned closed query and command variant in reverse: an unmapped
use, unused foreign Surface dependency, extra row, or missing row fails the
gate.

A result return is verified data delivery to a source Pulse and does not create
a reverse direct-control edge. Generated or inline dispatch remains a direct
edge until the target returns; the bounded completion deque prevents recursive
source acceptor entry without pretending there is an asynchronous transport.

`app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt` is the
only production holder/importer of the
Impl-owned `ProfileComponent` and `GameplayCompositionComponent` composites.
It passes only `SessionProfileRoute` plus `GameplaySessionHost` to Session,
`GameplayProfileRoute` to Gameplay, `GameplayPresentation` to Session
Interaction, and local/read-only Profile views to their declared Interactions.
The mechanical Profile result router is
`app/shared/src/commonMain/kotlin/kinetickk/app/shared/ProfileModuleResultRouter.kt`;
it switches only on the accepted
command-source identity and never inspects or reconstructs the ModuleResult.

The Android application plugin is isolated in the pure `app:android` host.
That leaf owns Android packaging and its `MainActivity`, has exactly one
production project dependency (`implementation -> :app:shared`), belongs to
AppAssembly, and neither introduces a semantic authority nor imports any
Ball/Flow/Resource implementation directly.
`app:shared` remains the KMP Compose Assembly and platform-capability leaf. The
host-to-shared compile edge stays wholly inside AppAssembly and therefore does
not appear in the semantic direct-control graph above.

## Read dependencies

| ID | Caller -> target | Target-owned query/result use | Consistency |
|---|---|---|---|
| `session-content-ui` | AppSession -> ContentCatalog | `ContentCatalog.uiCatalog` -> `UiCatalogSnapshot` | application-lifetime content version |
| `session-profile-run-bootstrap` | AppSession -> Profile | `ProfileQuery.GetRunBootstrap` -> `RunBootstrapProjection` | one Profile query snapshot per call |
| `session-profile-preferences` | AppSession -> Profile | `ProfileQuery.GetPreferences` -> `PreferencesProjection` | one Profile query snapshot per call |
| `session-profile-home` | AppSession -> Profile | `ProfileQuery.GetHomeProgress` -> `HomeProgressProjection` | explicitly non-atomic when joined with independent UI reads |
| `session-profile-collection` | AppSession -> Profile | `ProfileQuery.GetCollection` -> `CollectionProjection` | explicitly non-atomic when joined with independent run/UI reads |
| `session-profile-rebirth-progress` | AppSession -> Profile | `ProfileQuery.GetRebirthProgress` -> `RebirthProgressProjection` | one Profile query snapshot per call |
| `session-profile-persistence` | AppSession -> Profile | `ProfileQuery.GetPersistenceStatus` -> `PersistenceStatusProjection` | one Profile query snapshot per call |
| `session-gameplay-status` | AppSession -> GameplayRun | `GameplayQuery.GetRunStatus` -> `GameplayRunStatusProjection` | exact active `RunId` is validated by Session admission |
| `session-gameplay-weapon` | AppSession -> GameplayRun | `GameplayQuery.GetActiveWeapon` -> `GameplayActiveWeaponProjection` | explicitly non-atomic UI projection |
| `session-gameplay-codex` | AppSession -> GameplayRun | `GameplayQuery.GetCodexStacks` -> `GameplayCodexStacksProjection` | explicitly non-atomic UI projection |
| `gameplay-content-bootstrap` | GameplayRun -> ContentCatalog | `ContentCatalog.gameplayContent` -> `GameplayContentSnapshot` | captured at accepted `StartRun`; no later global lookup |
| `gameplay-profile-run-bootstrap` | GameplayRun -> Profile | `ProfileQuery.GetRunBootstrap` -> `RunBootstrapProjection` | validated trusted start Context for the exact Profile instance/revision |
| `gameplay-profile-preferences` | GameplayRun -> Profile | `ProfileQuery.GetPreferences` -> `PreferencesProjection` | validated trusted preferences Context for the exact Profile instance/revision |
| `profile-content-policy` | Profile -> ContentCatalog | `ContentCatalog.profilePolicy` -> `ProfilePolicySnapshot` | captured at Profile bootstrap; no later global lookup |

An admitted read creates no Decision, accepted-input marker, revision, handle,
or semantic output.

## Command/result routes

Every route is same-process, same-stack, target-owned, and statically bound.
Each accepted source command carries a target-owned `ModuleCommandRequest` and
semantic handle containing source instance, target instance, source revision,
and source ordinal. Target acceptance creates the only `ModuleResult` frame.
Return delivery verifies the complete correlation tuple before the trusted
caller Impl constructs its Nucleus-private `ModuleResultPulse`. A refusal before
target acceptance uses only the caller-owned flattened four-field carrier; no
public source completion wrapper exists.

| Route ID | Source -> target | Target-owned operation | Source completion |
|---|---|---|---|
| `session-profile-core-shape` | AppSession -> Profile | `ProfileModuleCommand.SelectCoreShape` -> `ProfileModuleResult.CoreShapeSelected` | `ProfileModuleResultPulse` / `ProfileCommandRejectedBeforeAcceptance` |
| `session-profile-mute` | AppSession -> Profile | `ProfileModuleCommand.ToggleMute` -> `ProfileModuleResult.PreferencesChanged` | `ProfileModuleResultPulse` / `ProfileCommandRejectedBeforeAcceptance` |
| `session-profile-rebirth` | AppSession -> Profile | `ProfileModuleCommand.AdvanceRebirth` -> `ProfileModuleResult.RebirthAdvanced` | `ProfileModuleResultPulse` / `ProfileCommandRejectedBeforeAcceptance` |
| `session-gameplay-start` | AppSession -> GameplayRun | `GameplayModuleCommand.StartRun` -> `GameplayModuleResult.RunStarted` | `GameplayModuleResultPulse` / `GameplayCommandRejectedBeforeAcceptance` |
| `session-gameplay-pause` | AppSession -> GameplayRun | `GameplayModuleCommand.PauseForOverlay` -> `GameplayModuleResult.OverlayPaused` | `GameplayModuleResultPulse` / `GameplayCommandRejectedBeforeAcceptance` |
| `session-gameplay-preferences` | AppSession -> GameplayRun | `GameplayModuleCommand.ApplyPreferences` -> `GameplayModuleResult.PreferencesApplied` | `GameplayModuleResultPulse` / `GameplayCommandRejectedBeforeAcceptance` |
| `session-gameplay-exit` | AppSession -> GameplayRun | `GameplayModuleCommand.ExitRun` -> `GameplayModuleResult.RunExited` | `GameplayModuleResultPulse` / `GameplayCommandRejectedBeforeAcceptance` |
| `gameplay-profile-progress` | GameplayRun -> Profile | `ProfileModuleCommand.ApplyGameplayProgress` -> `ProfileModuleResult.GameplayProgressApplied` | `GameplayNucleusPulse.ProfileModuleResultPulse` / `GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance` |

The AppSession Flow has exactly two participant authorities: Profile and
GameplayRun. A `FlowParticipation` declaration exists once per pair. The
AppSession/Profile participation references the six declared Profile reads and
three distinct command/result mappings. The AppSession/GameplayRun participation
references the three declared Gameplay reads and four distinct command/result
mappings. Its owned coordination is lifecycle, ordering, branching,
bootstrap availability, and terminal navigation; referenced reads and commands retain
their own ownership. The repository has exactly fourteen typed read routes.
AppSession has exactly seven command/result route mappings; the repository has eight
after adding GameplayRun/Profile progress.

## Flow participations

| FlowParticipation ID | Flow / participant | Owned coordination | Dependency references |
|---|---|---|---|
| `app-session-profile` | AppSession / Profile | lifecycle, ordering, branching, and bootstrap availability | `session-profile-run-bootstrap`, `session-profile-preferences`, `session-profile-home`, `session-profile-collection`, `session-profile-rebirth-progress`, `session-profile-persistence`; `session-profile-core-shape`, `session-profile-mute`, `session-profile-rebirth` |
| `app-session-gameplay` | AppSession / GameplayRun | lifecycle, ordering, branching, overlay and terminal navigation | `session-gameplay-status`, `session-gameplay-weapon`, `session-gameplay-codex`; `session-gameplay-start`, `session-gameplay-pause`, `session-gameplay-preferences`, `session-gameplay-exit` |

At most one Session participant command is pending. GameplayRun holds at most
one pending Profile command. Same-stack causal depth is at most eight. Source
acceptance reserves the next alternative-completion slot; target acceptance
reserves the result return before publishing. A target pre-acceptance failure
creates no target frame or revision and is delivered only through the verified
carrier completion.

## Closed semantic output executors

Every declared semantic output variant has exactly one statically selected
effective route and consumer/executor. A closed command-route row may select one
of its already declared effective target routes from the target-owned command
variant; it does not add a consumer or wildcard registry.

| ID | Output variant | Conditional selection | Effective route | Consumer/executor |
|---|---|---|---|---|
| `ProfileOutput.PersistSnapshot` | `ProfileOutput.PersistSnapshot` | always | `profile-resource-write-snapshot` | `DefaultProfileComponent.execute -> ProfileResource.writeSnapshot` |
| `ProfileOutput.CompleteCommand@app-session` | `ProfileOutput.CompleteCommand` | `profile-complete-consumer/app-session-command-source` | `profile-result-to-app-session` | `AppSession Nucleus` |
| `ProfileOutput.CompleteCommand@gameplay-run` | `ProfileOutput.CompleteCommand` | `profile-complete-consumer/gameplay-run-command-source` | `profile-result-to-gameplay-run` | `GameplayRun Nucleus` |
| `GameplayOutput.EmitVisualFx` | `GameplayOutput.EmitVisualFx` | always | `gameplay-visual-fx` | `InteractionFxReducer.apply` |
| `GameplayOutput.SendProfileCommand` | `GameplayOutput.SendProfileCommand` | always | `gameplay-profile-progress` | `GameComponent.executeProfileCommand -> GameplayProfileRoute.acceptFromGameplay` |
| `GameplayOutput.AdvanceAudio` | `GameplayOutput.AdvanceAudio` | always | `gameplay-audio-advance` | `GameComponent.execute -> GameplayAudioExecutor.advance` |
| `GameplayOutput.EnsureAudioUnlocked` | `GameplayOutput.EnsureAudioUnlocked` | always | `gameplay-audio-unlock` | `GameComponent.execute -> GameplayAudioExecutor.ensureUnlocked` |
| `GameplayOutput.CompleteCommand` | `GameplayOutput.CompleteCommand` | AppSession is the only command source | `gameplay-result-to-app-session` | `AppSession Nucleus` |
| `AppSessionOutput.EnsureGameplayRun` | `AppSessionOutput.EnsureGameplayRun` | always | `session-ensure-gameplay-run` | `DefaultAppSessionComponent.ensureGameplayRun -> GameplaySessionHost.createRun` |
| `AppSessionOutput.SendProfileCommand` | `AppSessionOutput.SendProfileCommand` | target operation selects one of three closed routes | `session-profile-command-closed-route` | `DefaultAppSessionComponent.executeProfileCommand -> SessionProfileRoute.acceptFromSession` |
| `AppSessionOutput.SendGameplayCommand` | `AppSessionOutput.SendGameplayCommand` | target operation selects one of four closed routes | `session-gameplay-command-closed-route` | `DefaultAppSessionComponent.executeGameplayCommand -> GameplaySessionRunPort.acceptFromSession` |
| `AppSessionOutput.SynchronizeAudioPreferences` | `AppSessionOutput.SynchronizeAudioPreferences` | always | `session-audio-preferences` | `DefaultAppSessionComponent.updateAudioPreferences` |
| `AppSessionOutput.PlayMuteFeedback` | `AppSessionOutput.PlayMuteFeedback` | always | `session-audio-mute-feedback` | `DefaultAppSessionComponent.playMuteFeedback` |
| `AppSessionOutput.PlayRebirthAcceptedFeedback` | `AppSessionOutput.PlayRebirthAcceptedFeedback` | always | `session-audio-rebirth-feedback` | `DefaultAppSessionComponent.playRebirthAcceptedFeedback` |

The two `ProfileOutput.CompleteCommand` rows are mutually exclusive final
consumers selected only by the canonical `commandSource` and effective protocol
identity. The transport router/sink is not a semantic consumer. Their shared
reservation is therefore `max=1`, never two branches for one accepted output.
`GameplayOutput.CompleteCommand` has AppSession as its only final consumer.

## Cumulative fan-out scope

`maxCumulativeFanout=9840` applies to one accepted root causal scope. One unit
is one distinct accepted `SemanticOutput` branch from its complete accepted source tuple
(authority, instance, accepted-frame commit revision, source
ordinal, and output variant, plus `OutputId` when materialized and
`semanticHandle` only for a triggered inter-Ball/addressable contract) through
one effective route to one effective consumer/executor. Each output in the
closed table above has one consumer.

Terminal branches count. All co-reachable branches across accepted causal
depths `0..7` sum, and separate converging branches to the same authority
remain separate units (a diamond counts route traversals, not unique
authorities). Mutually exclusive alternatives share one reservation equal to
their maximum and only the selected accepted alternative consumes it. A
duplicate traversal record for the same source tuple, route, and consumer adds
no unit; a newly accepted source tuple does. A separately accepted
independent root starts a fresh scope and ceiling.

The source-derived graph is same-stack: No asynchronous semantic handoff exists
and none can reset or escape the root scope. With at most three outputs, one
consumer per output, and eight accepted levels, the static ceiling is
`3^1 + 3^2 + ... + 3^8 = 9840`. The verifier resolves the closed output table and
tree, diamond, terminal, co-reachable, mutually exclusive, duplicate, and
independent-root fixtures. This proof introduces no runtime fan-out counter.

## Required workflow order

```text
start/restart: Profile GetRunBootstrap -> Gameplay StartRun accepted -> Session navigation accepted
overlay:       Gameplay PauseForOverlay accepted -> Session overlay accepted
settings row:  Profile Interaction -> local Profile adjustment accepted
settings exit: Profile GetPreferences -> active Gameplay ApplyPreferences accepted -> Session closes/replaces overlay
rebirth:       Profile AdvanceRebirth accepted -> allocate/accept new GameplayRun -> Session navigation accepted
exit:          Gameplay ExitRun accepted -> Profile ApplyGameplayProgress accepted -> Session Home accepted
bootstrap:     absent/rejected/incompatible current snapshot -> default Profile -> Session READY
               provider read failure -> Session BOOTSTRAP_UNAVAILABLE -> blocking PROFILE UNAVAILABLE UI
```

Participant rejection or carrier failure is a closed Session workflow input and
cannot be skipped or rewritten into success. Assembly only constructs
components, selects these static bindings, and transports owner-created frames.

## Route inventory

The rendered route inventory is closed and remains exactly:

```text
base: Home | Gameplay
overlay: Settings | Lab | Armory | Rebirth | Codex
```

`AppShellProjection` is immutable. Its projection revision produces a route
lifecycle token. It does not become a new destination, business owner, or
composition identity.
