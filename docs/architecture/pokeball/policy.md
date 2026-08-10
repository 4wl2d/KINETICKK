<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK local Pokeball policy

**Policy ID:** `kinetickk-local-standard`

**Revision:** `1`

**Owner:** KINETICKK project

**Scope:** the four authorities and the Desktop/Web in-process bindings named
in `authority-map.md`

This document selects only values and mechanisms that Pokeball Core leaves to
the project. Core remains authoritative. Typed protocols and bounds in Kotlin
remain authoritative where they are more specific. The final claim record pins
the exact byte digest of this policy and its Assembly.

## Effective profiles

Profile, GameplayRun, and AppSession select:

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
runtime mutation profile.

The Inline binding adds no actor, mailbox, worker pool, reflection registry,
service locator, coroutine queue, transport serialization, or thread hop.
`InlineDispatchGuard` and a bounded synchronous completion deque are mechanical
Foundation only.

## Decision and composition bounds

| Dimension | Effective bound | Enforcement owner |
|---|---:|---|
| Profile semantic outputs per accepted Decision | 2 | Profile acceptor preflight; whole Decision rejected/faulted before publication on overflow |
| Gameplay semantic outputs per accepted Decision | 3 | Gameplay acceptor preflight |
| Session semantic outputs per accepted Decision | 3 | Session acceptor preflight |
| Session participant commands at one time | 1 | Session State/Nucleus plus acceptor |
| Session participant authorities | 2 | static `FlowParticipation`/Assembly validation |
| same-stack causal depth | 8 | binding reservation before source/target acceptance |
| active GameplayRun instances | 1 | Session allocation/lifecycle invariant |
| Gameplay fixed steps per render frame | 48 | Gameplay simulation loop |
| enemies / projectiles / pickups / trail | 120 / 650 / 420 / 110 | Gameplay candidate-State validation and construction |
| visual-FX cues per projection | 2048 | Gameplay bounded accumulator |
| accepted / executed audio requests per frame | 32 / 3 | Ball-private executor / Audio Resource binding |
| items / weapons / upgrades / relics | 400 / 12 / 8 / 40 | Content bootstrap validation |
| Rebirth level | `0..10` | Content typed policy plus Profile/Gameplay validation |
| Profile v4 UTF-8 payload | 65536 bytes | Profile Resource before decode and after encode |

Every collection and batch bound has exact `N` and first-`N+1` tests. Existing
simulation collection policy is explicit and preserved: enemy, projectile, and
pickup additions at their caps are refused; trail rollover drops the oldest
mechanical point before retaining the new one. Semantic output and
participant-command batches are never truncated or partially accepted. A
mechanical render/audio projection may deterministically select within its
separate declared execution cap only after its owning semantic frame has been
accepted.

No numeric `maxTransitionSteps`, `maxStateBytes`, or
`maxOutputBytesPerDecision` claim is selected. Termination and semantic-output
bounds are established by static control-flow/type proofs and collection caps;
there is therefore no invented Decision Work Meter or unrelated byte-measure
artifact. The save payload limit is a raw storage-boundary input/encoded-output
measure, not a general Ball State-size claim.

## Persistence boundary

The v4 JSON boundary is strict:

- unknown or missing required fields fail;
- no leniency, coercion, special floats, `ignoreUnknownKeys`, or
  `@JsonIgnoreUnknownKeys`;
- all economy `Long` values use canonical validated decimal strings;
- stable IDs are explicit; maps become sorted record lists and collections are
  sorted; defaults are encoded;
- `schemaVersion=4`, profile ID `local-player`, and content version
  `kinetickk-content-1` are exact;
- representation and closed-type validation occur before a bootstrap Pulse;
  State/Context-dependent compatibility and reset decisions remain Profile
  Nucleus policy.

Desktop writes only node `kinetickk/profile`, key `snapshot_v4`; Web writes only
`kinetickk_profile_v4`. Legacy discovery/purge is limited to Desktop node
`kinetickk/progression` keys `progress_v2` and `kinetickk_matter`, and Web keys
`kinetickk_progress_v2` and `kinetickk_matter`.

Reset ordering is write-default-v4, observe success, then purge only the exact
legacy keys. Failure or uncertainty preserves legacy data. A user
`RetryLegacyPurge` is a new accepted business Pulse over retained reset state;
it is not automatic dispatch redelivery, transport retry, or idempotent replay.

## Capability and failure policy

Resources receive the minimum explicit bounded capability: exact-key read,
write, and remove functions or bounded tone playback. No Ball receives ambient
Preferences, `localStorage`, filesystem, browser, or audio-provider authority.
Provider exceptions are quarantined into closed facts. Programming/invariant
faults are not converted into business rejections.

All rejection, validation, admission, resource-result, and participant-result
reasons are closed variants. Open field/reason strings are prohibited.

## UI policy

Compose render models are immutable. Page, focus, viewport, gestures, animation
clock, and visual FX remain Interaction Ephemeral. Delegated Compose State reads
use a `Value` suffix. Stable composition identity uses the owning entity or
screen identity; projection revision is a separate route/lifecycle token and is
not reused as a `key(...)` identity.
