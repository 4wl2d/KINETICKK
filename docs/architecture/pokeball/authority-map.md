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
| Profile | `kinetickk.local/Profile/local-player`; application lifetime | complete `PlayerProfile`, `ProfileRevision`, captured `ProfilePolicySnapshot`, bootstrap/reset/persistence status | Profile acceptor publishes one accepted Profile frame |
| GameplayRun | one monotonically allocated `RunId`; at most one active run | simulation and RNG, run phase, captured `GameplayContentSnapshot`, viewport/pointer/brake/preferences, revision, at most one pending Profile command | that run's Gameplay acceptor publishes one accepted Gameplay frame |
| AppSession | `kinetickk.local/AppSession/local-session`; application lifetime | base destination, overlay, active `RunId`, projection revision, workflow phase, Rebirth confirmation, reset-modal lifecycle | Session acceptor publishes one accepted Session frame |

`ProfileRevision`, Gameplay revision, and Session projection revision retain
their distinct owner meanings. Equality or derivation never transfers
ownership.

## Business-fact ownership

| Fact or decision | Owner | Consumers and legal access |
|---|---|---|
| item, weapon, meta-upgrade, relic, and Rebirth definitions/policy | ContentCatalog | typed Content queries and captured immutable snapshots |
| preferences and mute state | Profile | Profile queries; captured into Gameplay after accepted Session workflow |
| economy, permanent Lab ranks, loadout, collection, Rebirth progress | Profile | target-owned Profile queries; Gameplay receives only captured run bootstrap |
| save bootstrap, v4 compatibility status, reset confirmation, persistence/purge outcome | Profile | Session receives typed Profile results/queries; Resource reports typed facts only |
| live simulation, deterministic RNG, run matter, discoveries, active weapon, Codex stacks, run terminal result | GameplayRun | Gameplay queries and target-owned result to Session/Profile routes |
| navigation, overlay policy, start/restart/exit ordering, settings propagation, Rebirth orchestration, reset modal | AppSession | Session projection consumed by Session Interaction |
| UI page, focus, viewport gesture mechanics, animation clock, local visual FX | owning Interaction role | never consumed as hidden business input; a business-relevant value becomes a Pulse, Context field, or committed State |
| cue-to-tone meaning | originating Ball's private audio executor | mechanical `ToneRequest` only crosses into Audio Resource |
| tone validation and platform playback | Audio Resource | no business decision or semantic cue authority |

Home and Codex may combine independent Profile and Gameplay reads only in
Session Interaction and must label the result non-atomic. Assembly never joins
those reads into a fabricated business snapshot.

## Physical role ownership

| Role | Physical modules |
|---|---|
| shared mechanical Foundation | `foundation:common`, `foundation:design` |
| mechanical audio Resource | `resource:audio:api`, `resource:audio:impl` |
| Content | `ball:content:api`, `ball:content:impl` |
| Profile | `ball:profile:api`, `ball:profile:nucleus`, `ball:profile:resource`, `ball:profile:interaction`, `ball:profile:impl` |
| GameplayRun | `ball:gameplay:api`, `ball:gameplay:nucleus`, `ball:gameplay:interaction`, `ball:gameplay:impl` |
| AppSession Flow | `flow:session:api`, `flow:session:nucleus`, `flow:session:interaction`, `flow:session:impl` |
| construction hosts | `app:shared`, `app:desktop`, `app:web` |

Nucleus modules depend on neither Compose, platform APIs, nor Resource
implementations. Assembly constructs components and binds declared routes; it
does not branch on business state or construct business payloads.

## Writer and transition rules

Every mutation is a closed target-owned Pulse evaluated by one pure,
deterministic, terminating Nucleus. Rejection publishes nothing. Acceptance
materializes a complete immutable next State and ordered immutable output batch,
then the acceptor publishes State/revision exactly once before dispatch. A
same-state accepted Decision still advances the owning revision when the
protocol defines acceptance.

No acceptor is reentrant. Synchronous command completion enters the source only
through the bounded Foundation completion deque after the target acceptor has
returned. Resource outcomes become typed target-owned facts; an
`OutcomeUnknown` never rewrites the accepted frame.
