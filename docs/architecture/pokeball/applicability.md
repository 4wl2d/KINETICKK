<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Core applicability and claim boundaries

This is the project-owned trigger inventory for the migration. It does not
change Core applicability. Missing metadata cannot suppress an inferred
trigger.

## Present triggers

| Concern | Why it is present | Required construction/evidence |
|---|---|---|
| always-applicable boundary/state/decision laws | four semantic authorities mutate or publish owned facts | one writer, closed Pulse/query/output inventories, pure Nuclei, atomic accepted frames, reentrancy tests |
| cross-authority reads | Session, Gameplay, and Profile consume exactly 14 target-owned snapshot routes | target-owned query/result pairs, source-bound query views, explicit freshness, Assembly bindings, non-atomic multi-read disclosure |
| same-stack commands and results | Session coordinates Profile/Gameplay; Gameplay reports progress to Profile through exactly 8 command/result routes | target-owned `ModuleCommand`/`ModuleResult` mappings, semantic-handle correlation, Nucleus-private result Pulses, flattened refusal carriers, depth reservation, completion deque |
| Flow composition | Session owns lifecycle, ordering, branching, recovery, and terminal navigation | one coordinator, two `FlowParticipation` rows, finite routes, one pending command |
| external persistence Resource effects | one current local `ProfileSnapshot` | minimum `readSnapshot`/`writeSnapshot` capability, one platform-owned exact key, commit-before-dispatch, closed provider outcomes/Facts, exact failure-before-execution versus possible-execution classification, and runtime-fault propagation |
| live mechanical Audio Projection (Core §9.13) | accepted Gameplay outputs project bounded tones through Resource/platform mechanics | minimum tone capability, commit-before-dispatch, no typed Audio Fact/result/status, and exact runtime-fault/projection-loss split |
| raw trust/representation edge | current persisted JSON and platform provider data are untrusted | independent 65536-byte codec gate before decode, Desktop 8192-code-unit value gate before write, and 64-key returned-inventory admission before project-owned membership iteration; strict decoding, provenance quarantine, and validation/business-stage tests; no bound is claimed for provider enumeration/allocation |
| finite variable dimensions | catalog, simulation, output, audio, causal, and workflow collections exist | exact `N/N+1`, no truncation or partial acceptance, static graph counts |
| shared Foundation | two or more Balls share immutable collections, PRNG, dispatch guard, or completion deque | project scan proving mechanical-only exports and no mutable business meaning/communication |

The finite physical graph has exactly 23 leaf modules. The separate
`app:android` packaging host is mechanical AppAssembly infrastructure: its
complete production project-dependency set is the single edge
`implementation -> :app:shared`. That intra-AppAssembly packaging edge is not a
business command, read route, output route, or additional authority.

The product is pre-`1.0.0`, so persistence compatibility and migration are not
active concerns. The current `ProfileSnapshot` shape is the entire supported
contract. An absent, rejected, non-canonical, or policy-incompatible current
payload constructs the default Profile. A provider read failure is different:
it blocks Session bootstrap and renders the input-blocking Profile-unavailable
UI. Keys outside the single platform-selected current key are not inspected and
remain untouched.

For that Audio row, synchronous Resource/platform calls propagate.
Synchronous JavaScript invocation/graph faults propagate.
Android and Desktop worker faults escape the detached `Runnable` to runtime; this is not caller propagation.
Web native `resume()`/`close()` Promise rejections alone are explicitly observed and consumed by
`.catch(() => undefined)` as non-semantic post-acceptance projection loss.

## Absent trigger scopes

These statements are implementation requirements to be proved from a closed
inventory at the freeze commit. They are not current `TriggerAbsenceProof`
artifacts. Exact proofs are created only if the final verdict actually relies on
the absence.

| Absent scope | Denial by construction |
|---|---|
| actors, authentication, tenants, grants, secrets, privileged actions | local single-player inputs cannot vary Decision/read authorization; no credential or secret type/capability exists |
| network, remote deployment, IPC, independently versioned endpoints | Android/Desktop/Web bind all authorities in one process and one build; no network client/server or remote route exists |
| detached asynchronous semantic delivery | no actor/mailbox/coroutine queue/event bus carries a Pulse, command, result, output, or Fact; all semantic commands complete through the bounded same-stack deque. App platform-broker mechanical execution, including Android/Desktop tone playback, is outside this absence scope and may not acquire business meaning |
| root idempotency or cancellation protocol | no root idempotency key/record and no cancellable operation exists |
| dynamic registry or wildcard routing | all authorities, instances, queries, commands, results, and routes are closed typed/static sets |
| process/security isolation | selected profile is `InProcess + Standard`; no containment or separate-principal claim is made |
| durable outbox, event journal, status materializer, or operation-status query | state profile is Transient; persistence facts affect Profile persistence state but do not provide delivery replay or accepted-operation status |

If source introduces any listed semantic type, route, configuration, claim, or
reachable behavior inside an absent scope, the absence is invalidated and the
corresponding Core path must be implemented and tested instead. Mechanical
provider machinery outside the explicitly bounded semantic-delivery scope does
not establish detached business delivery.

## Applicable package routes

The migration uses the Agent Pack routes for design, protocol, bounds,
composition/profiles, Resource outcome semantics, security/limits,
manifest/Assembly, routine tests, and conformance gates. Catalog and Checkout
remain examples only; no fixture value or guarantee is imported.

The final review evaluates all `RG-01` through `RG-10`. Each unit is `PASS` or
`FAIL`; `partial` is a failure for the claim. Agent Pack gates validate the Pack
itself and are not misreported as KINETICKK certification.

## Exact allowed claim

Only after a frozen implementation commit and all local/browser gates pass may
the next docs-only commit say:

> KINETICKK on implementation SHA `<sha>` conforms within the declared scope
> to Pokeball Core 1.4.0-draft at `de9ef7384795680c836d5e6c2c9b394286058670`.

This is a project self-attestation, not external certification. It does not
guarantee crash-atomic persistence, exactly-once or eventual delivery,
cross-device storage, or behavior of external audio/storage providers. Any
later product or build change invalidates the evidence and requires a new
freeze, environment capture, audit, and attestation.
