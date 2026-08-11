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
| `profile-content-policy` | Profile -> ContentCatalog | `ContentCatalog.profilePolicy` -> `ProfilePolicySnapshot` | captured at Profile bootstrap/reset; no later global lookup |

An admitted read creates no Decision, accepted-input marker, revision, handle,
or semantic output.

## Command/result routes

Every route is same-process, same-stack, target-owned, and statically bound.
Each accepted source command carries a `CommandRef` containing source instance,
target instance, source revision, and source ordinal. Target acceptance creates
the only result frame. Return delivery verifies the complete correlation tuple
before constructing the source result Pulse.

| Route ID | Source -> target | Target-owned operation | Source completion |
|---|---|---|---|
| `session-profile-core-shape` | AppSession -> Profile | `ProfilePulse.SelectCoreShape` -> `ProfileCommandOutcome.CoreShapeSelected` | `SessionControlPulse.ProfileCommandCompleted` / `SessionControlPulse.ProfileCommandRejectedBeforeAcceptance` |
| `session-profile-mute` | AppSession -> Profile | `ProfilePulse.ToggleMute` -> `ProfileCommandOutcome.PreferencesChanged` | `SessionControlPulse.ProfileCommandCompleted` / `SessionControlPulse.ProfileCommandRejectedBeforeAcceptance` |
| `session-profile-rebirth` | AppSession -> Profile | `ProfilePulse.AdvanceRebirth` -> `ProfileCommandOutcome.RebirthAdvanced` | `SessionControlPulse.ProfileCommandCompleted` / `SessionControlPulse.ProfileCommandRejectedBeforeAcceptance` |
| `session-profile-reset-confirm` | AppSession -> Profile | `ProfilePulse.ConfirmLegacyReset` -> `ProfileCommandOutcome.ResetCompleted` / `ProfileCommandOutcome.ResetWriteRejected` / `ProfileCommandOutcome.ResetWriteOutcomeUnknown` / `ProfileCommandOutcome.ResetNeedsAttention` | `SessionControlPulse.ProfileCommandCompleted` / `SessionControlPulse.ProfileCommandRejectedBeforeAcceptance` |
| `session-profile-reset-retry` | AppSession -> Profile | `ProfilePulse.RetryLegacyPurge` -> `ProfileCommandOutcome.ResetCompleted` / `ProfileCommandOutcome.ResetNeedsAttention` | `SessionControlPulse.ProfileCommandCompleted` / `SessionControlPulse.ProfileCommandRejectedBeforeAcceptance` |
| `session-gameplay-start` | AppSession -> GameplayRun | `GameplaySessionPulse.StartRun` -> `GameplayCommandOutcome.RunStarted` | `SessionControlPulse.GameplayCommandCompleted` / `SessionControlPulse.GameplayCommandRejectedBeforeAcceptance` |
| `session-gameplay-pause` | AppSession -> GameplayRun | `GameplaySessionPulse.PauseForOverlay` -> `GameplayCommandOutcome.OverlayPaused` | `SessionControlPulse.GameplayCommandCompleted` / `SessionControlPulse.GameplayCommandRejectedBeforeAcceptance` |
| `session-gameplay-preferences` | AppSession -> GameplayRun | `GameplaySessionPulse.ApplyPreferences` -> `GameplayCommandOutcome.PreferencesApplied` | `SessionControlPulse.GameplayCommandCompleted` / `SessionControlPulse.GameplayCommandRejectedBeforeAcceptance` |
| `session-gameplay-exit` | AppSession -> GameplayRun | `GameplaySessionPulse.ExitRun` -> `GameplayCommandOutcome.RunExited` | `SessionControlPulse.GameplayCommandCompleted` / `SessionControlPulse.GameplayCommandRejectedBeforeAcceptance` |
| `gameplay-profile-progress` | GameplayRun -> Profile | `ProfilePulse.ApplyGameplayProgress` -> `ProfileCommandOutcome.GameplayProgressApplied` | `GameplayControlPulse.ProfileCommandCompleted` / `GameplayControlPulse.ProfileCommandRejectedBeforeAcceptance` |

The AppSession Flow has exactly two participant authorities: Profile and
GameplayRun. A `FlowParticipation` declaration exists once per pair. The
AppSession/Profile participation references the six declared Profile reads and
five distinct command/result mappings. The AppSession/GameplayRun participation
references the three declared Gameplay reads and four distinct command/result
mappings. Its owned coordination is lifecycle, ordering, branching,
reset/recovery, and terminal navigation; referenced reads and commands retain
their own ownership. Therefore AppSession has exactly nine command/result route
mappings; the repository has ten after adding GameplayRun/Profile progress.

## Flow participations

| FlowParticipation ID | Flow / participant | Owned coordination | Dependency references |
|---|---|---|---|
| `app-session-profile` | AppSession / Profile | lifecycle, ordering, branching, reset and recovery | `session-profile-run-bootstrap`, `session-profile-preferences`, `session-profile-home`, `session-profile-collection`, `session-profile-rebirth-progress`, `session-profile-persistence`; `session-profile-core-shape`, `session-profile-mute`, `session-profile-rebirth`, `session-profile-reset-confirm`, `session-profile-reset-retry` |
| `app-session-gameplay` | AppSession / GameplayRun | lifecycle, ordering, branching, overlay and terminal navigation | `session-gameplay-status`, `session-gameplay-weapon`, `session-gameplay-codex`; `session-gameplay-start`, `session-gameplay-pause`, `session-gameplay-preferences`, `session-gameplay-exit` |

At most one Session participant command is pending. GameplayRun holds at most
one pending Profile command. Same-stack causal depth is at most eight. Source
acceptance reserves the next alternative-completion slot; target acceptance
reserves the result return before publishing. A target pre-acceptance failure
creates no target frame or revision and is delivered only through the verified
carrier completion.

## Required workflow order

```text
start/restart: Profile GetRunBootstrap -> Gameplay StartRun accepted -> Session navigation accepted
overlay:       Gameplay PauseForOverlay accepted -> Session overlay accepted
settings row:  Profile Interaction -> local Profile adjustment accepted
settings exit: Profile GetPreferences -> active Gameplay ApplyPreferences accepted -> Session closes/replaces overlay
rebirth:       Profile AdvanceRebirth accepted -> allocate/accept new GameplayRun -> Session navigation accepted
exit:          Gameplay ExitRun accepted -> Profile ApplyGameplayProgress accepted -> Session Home accepted
reset:         Profile bootstrap/reset result -> Session reset-modal projection; no Assembly-created reset payload
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
