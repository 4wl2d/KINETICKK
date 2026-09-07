<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# AppSession Assembly

Owner-authored Kotlin interfaces and their actual runtime wiring are the source
of cross-authority operations and bindings. This record selects behavior and
ownership; it does not maintain a second operation, consumer, or route registry.
The generated manifest reports resolved project/import dependencies and the
relevant API and implementation sources for inspection.

## Dependency and capability boundaries

Both compile-time imports and Direct Control Dependencies remain acyclic. The
existing verifier resolves foreign Application-Surface imports against actual
Gradle project dependencies and checks each graph. A new valid operation or
acyclic dependency requires no update to a hard-coded operation inventory.
Missing dependencies, foreign internal/State access and forbidden platform
imports remain errors. An immediate bound return adds no reverse control edge.

`app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt` owns
composition of the Profile, Gameplay and Session components. It supplies the
needed target-owned capability to each consumer, while Profile continues to own
its settings, progress policy, results and persistence. An additional consumer
of an existing permitted capability changes composition, not Profile's inventory
of caller names. Broad implementation composites remain restricted to their
owners and Assembly; a capability reference does not replace a business check.

The Android application plugin stays in the pure `app:android` packaging host.
Its production project dependency is `implementation -> :app:shared`, wholly
inside AppAssembly. The host adds no semantic authority or direct-control edge;
platform persistence/audio authority stays in the existing restricted brokers.

## Reads and local commands

Reads use the target-owned query/result surface and create no mutation, output
or revision. Each call observes the selected owner snapshot; multiple Profile
and Gameplay reads do not imply a multi-owner atomic snapshot. Run identity and
Profile revision retain their lifecycle/persistence meanings.

A local command executes only from an accepted source output. The target writer
accepts its own State and complete outputs; the result or pre-acceptance refusal
enters the source's serialized decision processing. The common Inline mechanism
retains each completion until its source frame is accepted and prevents a late
or duplicate completion from being applied to another invocation. Accepted
results and remaining accepted work are processed before the first deferred
execution fault escapes. A failed save does not turn an accepted Profile change
into nonacceptance. A programming fault that prevents source processing leaves
the accepted completion retained and blocks a new root; it does not fabricate a
business refusal or discard the result.

All local cross-authority commands use this typed call mechanism. Profile
persistence retains its separate effect reference and revision because its
completion has an independent correlation contract. Runtime component/API
regressions verify each binding alongside the shared call-scope tests.

## Finite Session workflows

Session owns participant lifecycle, ordering, branching and terminal navigation.
It has one pending participant command at a time and one active GameplayRun;
Gameplay has at most one pending Profile progress command. A mute workflow is
finite by its actual phases: Session accepts intent, Profile accepts settings,
Session handles the result, the active Gameplay accepts those settings, and
Session finishes. Refusal and failure branches also terminate without silently
reissuing work. Bootstrap, overlay, rebirth and exit keep the ordering below.

This finite immediate execution requires no numeric dependency/participant/route
quota, geometric fan-out calculation or carried causal scope/depth. The proof
includes every completion and failure handler; an import DAG alone is not a
termination proof. Real completion-storage, output-batch and external-resource
capacities still require preflight and whole-candidate rejection on overflow.

## Required workflow order

```text
start/restart: Profile GetRunBootstrap -> Gameplay StartRun accepted -> Session navigation accepted
overlay:       Gameplay PauseForOverlay accepted -> Session overlay accepted
settings row:  Profile Interaction -> local Profile adjustment accepted
settings exit: Profile GetPreferences -> active Gameplay ApplyPreferences accepted -> Session closes/replaces overlay
rebirth:       Profile AdvanceRebirth accepted -> allocate/accept new GameplayRun -> Session navigation accepted
exit:          Gameplay ExitRun accepted -> optional Profile progress outcome -> Gameplay exit result -> Session outcome
bootstrap:     absent/rejected/incompatible current snapshot -> default Profile -> Session READY
               provider read failure -> Session BOOTSTRAP_UNAVAILABLE -> blocking PROFILE UNAVAILABLE UI
```

A participant's pre-acceptance refusal is a typed Session input. Execution faults
propagate after accepted outcomes and retained work are processed; they do not
create a synthetic refusal or success. Assembly supplies target-owned capabilities
while business decisions stay in their owners.

## Presentation ownership

`AppShellProjection` is immutable. Its projection revision produces a route
lifecycle token. It does not become a new destination, business owner, or
composition identity.

The build projection exposes Content-owned `CoreShape` and immutable `EquippedRelic`
values through Gameplay's existing Content dependency. Gameplay owns effective
stats, their contributions, eligible acquisitions and active/missing synergies;
the UI formats these values without evaluating combat rules. The existing
Gameplay-to-Profile command now carries elite defeats, Dash hits, completed
orbits and the victorious character. Profile owns cumulative achievement totals,
distinct winning characters and unlock decisions. This adds no route or authority.
