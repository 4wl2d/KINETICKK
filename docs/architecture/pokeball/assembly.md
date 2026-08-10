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

A result return is verified data delivery to a source Pulse and does not create
a reverse direct-control edge. Generated or inline dispatch remains a direct
edge until the target returns; the bounded completion deque prevents recursive
source acceptor entry without pretending there is an asynchronous transport.

## Read dependencies

| ID | Caller -> target | Target-owned query/result use | Consistency |
|---|---|---|---|
| `session-content-ui` | AppSession -> ContentCatalog | immutable UI catalog snapshots | application-lifetime content version |
| `session-profile-shell` | AppSession -> Profile | run bootstrap, preferences, Home/Lab/loadout/collection/Rebirth/persistence projections | one Profile query snapshot per call; multi-query UI joins are explicitly non-atomic |
| `gameplay-content-bootstrap` | GameplayRun -> ContentCatalog | `GameplayContentSnapshot` | captured at accepted `StartRun`; no later global lookup |
| `profile-content-policy` | Profile -> ContentCatalog | `ProfilePolicySnapshot` | captured at Profile bootstrap/reset; no later global lookup |

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
| `session-profile-core-shape` | AppSession -> Profile | select Home core shape | Profile participant result Pulse |
| `session-profile-mute` | AppSession -> Profile | toggle mute | Profile participant result Pulse |
| `session-profile-rebirth` | AppSession -> Profile | advance Rebirth | Profile participant result Pulse |
| `session-profile-reset` | AppSession -> Profile | confirm reset or retry legacy purge | Profile participant result Pulse |
| `session-gameplay-start` | AppSession -> GameplayRun | start the reserved or retained `Created` run | Gameplay participant result Pulse |
| `session-gameplay-pause` | AppSession -> GameplayRun | pause for overlay | Gameplay participant result Pulse |
| `session-gameplay-preferences` | AppSession -> GameplayRun | apply accepted preferences | Gameplay participant result Pulse |
| `session-gameplay-exit` | AppSession -> GameplayRun | exit active run | Gameplay participant result Pulse |
| `gameplay-profile-progress` | GameplayRun -> Profile | apply accepted run progress | Gameplay completion Pulse |

The AppSession Flow has exactly two participant authorities: Profile and
GameplayRun. A `FlowParticipation` declaration exists once per pair. Its owned
coordination is lifecycle, ordering, branching, reset/recovery, and terminal
navigation; referenced reads and commands retain their own ownership.

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
