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
| GameplayRun | one monotonically allocated `RunId`; at most one active run | simulation and RNG, run phase, captured `GameplayContentSnapshot`, viewport/pointer/brake/preferences, revision, at most one pending Profile command | that run's Gameplay acceptor publishes one accepted Gameplay frame |
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
| item, weapon, meta-upgrade, relic, and Rebirth definitions/policy | ContentCatalog | typed Content queries and captured immutable snapshots |
| preferences and mute state | Profile | Profile queries; captured into Gameplay after accepted Session workflow |
| economy, permanent Lab ranks, loadout, collection, Rebirth progress | Profile | target-owned Profile queries; Gameplay obtains run bootstrap and preferences only through `GameplayProfileRoute`, then captures the validated values in its accepted frame |
| current `ProfileSnapshot` bootstrap and persistence outcome | Profile | Resource exposes only `readSnapshot`/`writeSnapshot` and typed outcomes; Session reads bootstrap/persistence status |
| live simulation, deterministic RNG, run matter, discoveries, active weapon, Codex stacks, run terminal result | GameplayRun | Gameplay queries and target-owned result to Session/Profile routes |
| navigation, overlay policy, start/restart/exit ordering, settings propagation, Rebirth orchestration, bootstrap-unavailable lifecycle | AppSession | Session projection consumed by Session Interaction |
| UI page, focus, viewport gesture mechanics, animation clock, local visual FX | owning Interaction role | never consumed as hidden business input; a business-relevant value becomes a Pulse, Context field, or committed State |
| cue-to-tone meaning | originating Ball's private audio executor | mechanical `ToneRequest` only crosses into Audio Resource |
| tone validation and bounded playback request selection | Audio Resource | receives only typed `ToneRequest` and a narrow playback capability; no provider acquisition |
| platform storage/audio provider acquisition and mechanical execution | instance-owned platform capability brokers / Execution-Gate mechanics bound by AppAssembly | one platform broker is the sole physical storage authority for the current Profile key; Profile/Audio retain Resource operation semantics, Assembly selects and binds only, gains no effect/policy authority, and broad provider types never enter a Resource constructor |

The closed platform bindings are actuals inside the `app:shared` KMP leaf:
`androidMain` binds Android SharedPreferences/AudioTrack, `desktopMain` binds JVM providers, and
`wasmJsMain` binds browser providers. The separate `app:android` leaf is a pure
application/packaging host with exactly one production project edge to
`app:shared`; it adds no authority, business fact, writer, or semantic route.
For Profile persistence, those mutually exclusive platform bindings select
exactly one current location: Android preferences `kinetickk.profile` key
`snapshot`, Desktop node `kinetickk/profile` key `snapshot`, or Web key
`kinetickk_profile`. No Ball or Flow can select another storage root or key.

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
| construction and packaging hosts | `app:shared` (shared KMP Compose Assembly and platform bindings), `app:android` (pure Android host; only production project edge is `implementation -> :app:shared`), `app:desktop`, `app:web` |

Nucleus modules depend on neither Compose, platform APIs, nor Resource
implementations. Assembly constructs components and binds declared routes; it
does not branch on business state or construct business payloads.

The physical implementation remains one Profile component and one active
Gameplay host, without façade fan-out. Only `app:shared` Assembly may hold the
Impl composites `ProfileComponent` and `GameplayCompositionComponent`.
Downstream roles receive least-authority views: `ProfilePort` for local Profile
Interaction, `ProfileReadPort` for Home/Codex, `SessionProfileRoute` for
AppSession, `GameplayProfileRoute` for GameplayRun, `GameplaySessionHost` and
`GameplaySessionRunPort` for Session, and `GameplayPresentation` plus
`GameplayPresentationPort` for rendering. Result deliveries are validated in
the corresponding Impl before construction of a Nucleus-private
`ModuleResultPulse` or flattened pre-acceptance carrier.

## Writer and transition rules

Every mutation is a closed target-owned Pulse evaluated by one pure,
deterministic, terminating Nucleus. Rejection publishes nothing. Acceptance
materializes a complete immutable next State and ordered immutable output batch,
then the acceptor publishes State/revision exactly once before dispatch. A
same-state accepted Decision still advances the owning revision when the
protocol defines acceptance.

No acceptor is reentrant. Synchronous command completion enters the source only
through the bounded Foundation completion deque after the target acceptor has
returned. A caller-owned Gameplay Interaction root is decided directly under
the non-reentrant guard and uses a reusable causal-metadata carrier only while
dispatching its outputs; the deque remains reserved for nested completions.
Resource outcomes become typed target-owned facts; an
`OutcomeUnknown` never rewrites the accepted frame.
