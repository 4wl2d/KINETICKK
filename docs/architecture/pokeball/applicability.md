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
| same-stack commands and results | Session coordinates Profile/Gameplay; Gameplay reports progress to Profile through exactly 10 command/result routes | target-owned `ModuleCommand`/`ModuleResult` mappings, semantic-handle correlation, Nucleus-private result Pulses, flattened refusal carriers, depth reservation, completion deque |
| Flow composition | Session owns lifecycle, ordering, branching, recovery, and terminal navigation | one coordinator, two `FlowParticipation` rows, finite routes, one pending command |
| external Resource effects | local persistence and tone playback | minimum exact-key/tone capabilities, commit-before-dispatch, closed facts, fault containment |
| raw trust/representation edge | persisted JSON and platform provider data are untrusted | 65536-byte precheck, strict decoding, provenance quarantine, validation/business-stage tests |
| persistent schema change | save format changes incompatibly to v4 | explicit detection, no v2/v3 import, reset/quarantine lifecycle, write-before-purge and restart tests |
| explicit user semantic retry | after a legacy-purge failure, `SessionInteractionPulse.ResetRetryRequested` is present under `PBA-24`; primary owner `AppSession` issues exactly one `ProfileModuleCommand.RetryLegacyPurge` to target `Profile` | one purge attempt per explicit user Pulse; Profile returns one closed result; transport, executor, SDK/provider, and reconciliation retries disabled; no blind/automatic retry; cumulative attempts exactly one per Pulse |
| finite variable dimensions | catalog, simulation, output, audio, causal, and workflow collections exist | exact `N/N+1`, no truncation or partial acceptance, static graph counts |
| shared Foundation | two or more Balls share immutable collections, PRNG, dispatch guard, or completion deque | project scan proving mechanical-only exports and no mutable business meaning/communication |
| explicit conformance claim | the final docs-only commit self-attests one implementation freeze | frozen Core/pack/source/environment/policy/Assembly, RG-01..RG-10 evidence, exact non-guarantees |

## Absent trigger scopes

These statements are implementation requirements to be proved from a closed
inventory at the freeze commit. They are not current `TriggerAbsenceProof`
artifacts. Exact proofs are created only if the final verdict actually relies on
the absence.

| Absent scope | Denial by construction |
|---|---|
| actors, authentication, tenants, grants, secrets, privileged actions | local single-player inputs cannot vary Decision/read authorization; no credential or secret type/capability exists |
| network, remote deployment, IPC, independently versioned endpoints | Desktop/Web bind all authorities in one process and one build; no network client/server or remote route exists |
| detached asynchronous semantic delivery | no actor/mailbox/coroutine queue/event bus carries a Pulse, command, result, output, or Fact; all semantic commands complete through the bounded same-stack deque. App platform-broker mechanical execution, including Desktop tone playback, is outside this absence scope and may not acquire business meaning |
| root idempotency or cancellation protocol | no root idempotency key/record and no cancellable operation exists; these inactive layers are not inferred from the present AppSession-owned semantic policy |
| dynamic registry or wildcard routing | all authorities, instances, queries, commands, results, and routes are closed typed/static sets |
| process/security isolation | selected profile is `InProcess + Standard`; no containment or separate-principal claim is made |
| durable outbox, event journal, status materializer, or operation-status query | state profile is Transient; persistence facts affect Profile reset/persistence state but do not provide delivery replay or accepted-operation status |

If source introduces any listed semantic type, route, configuration, claim, or
reachable behavior inside an absent scope, the absence is invalidated and the
corresponding Core path must be implemented and tested instead. Mechanical
provider machinery outside the explicitly bounded semantic-delivery scope does
not establish detached business delivery.

## Applicable package routes

The migration uses the Agent Pack routes for design, protocol, bounds,
composition/profiles, async-status command semantics, security/limits,
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
