# Design Runbook

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Use this runbook when creating a Ball, changing a boundary/state/protocol, or reviewing an architectural change. Primary rules: `PKB-AR-GOV-*`, `PKB-AR-BND-*`, `PKB-AR-PRT-*`, `PKB-AR-STA-*`, `PKB-AR-ID-001`, `PKB-AR-DEC-*`.

## 1. Task card

Complete this before considering alternatives:

| Field | Requirement |
|---|---|
| Business decision | One verifiable decision or capability, not the name of a layer. |
| Scope/non-goals | What changes and what is explicitly excluded. |
| Mutable facts | Which facts change and who currently has authority over them. |
| Strict invariants | What must be accepted within one consistency boundary. |
| Lifecycle | Creation, active states, terminal/retention/recovery behavior. |
| Trust boundary | Raw sources, principals, and privileged outputs. |
| Expected load | State size, operations/key, contention, transition cost. |
| Required guarantees | Only with boundary/assumptions/evidence. |
| Existing artifacts | Manifest, Assembly, tests, ADR/deviation, overlay revision. |

If the card requires a project-specific decision absent from the accepted overlay, the agent records a missing decision. It does not silently choose a profile, authority, or limit.

## 2. Choosing a boundary

First group the facts that must share:

- strict invariants;
- one `StateKey` and serialized acceptance;
- lifecycle/recovery unit;
- semantic owner;
- trust/isolation boundary;
- a compatible scale/contention profile.

Separate them when terminal outcomes, authority, partition key, privilege, lifecycle, or recovery differ. A screen, endpoint, repository, table, or team ownership is not by itself boundary proof.

### Boundary worksheet

```text
BallType:
Ball kind: Feature | Flow | Read Model
Owned semantic facts:
StateKey and namespace:
Strict invariants:
External sources of record:
Declared dependencies:
Trust boundary:
Lifecycle/recovery owner:
Expected state/transition bounds:
Public protocol surface:
Selected profiles:
Explicit non-guarantees:
```

Gate: every mutable fact appears exactly once in the authority map; a cross-state decision read is absent or replaced with captured input, a target-owned check, a Read Model, or a Flow.

## 3. State model

Distinguish:

| Kind | Permitted use |
|---|---|
| Sovereign State | The sole mutable truth of this authority. |
| Captured Input | An immutable versioned snapshot for a specific workflow decision/recovery. |
| Replica State | A copy with provenance/revision/freshness; not command authority. |
| ReadModelState | Derived query-oriented state owned by a Read Model Ball; it has its own revision/source positions/freshness. |
| Projection | An immutable semantic output/view; it has no owner and is not an authority. If a Projection is a Query result payload, its `ReadResult` carries the stamp of the authority snapshot actually read. |
| Ephemeral State | Does not participate in a semantic decision and may be lost under the selected profile. |
| Runtime State | Mailbox, claims, attempts, breaker, and tracing; mechanical runtime state, not business authority. |

For every state field, record the owner, writer, source, revision/generation, retention, and stale policy. Do not store mechanical `OutputId`/`AttemptId` as pre-commit semantic identity; use `SemanticHandle`.

### Value-liveness worksheet

For every value used after the transition that produced it, complete:

| Value/output fields | Origin Pulse/Context | Source correlation/authority handle | Authority + observation versions/provenance | First atomic state assignment | Later consumers | Retention/deletion or trusted reintroduction | Security class |
|---|---|---|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

Every field of a later output must be derived from exact committed State, the current Pulse, or a declared versioned `DecisionContext`. Inbox/outbox/runtime/participant history is not decision input. For direct/reconciled proof, record the authority action and observed-via handle separately; the same value may corroborate, while a conflicting value/proof fails closed. A state-shape change receives a new `stateSchemaVersion` and migration/quarantine evidence; missing active values are not synthesized as null/default.

## 4. Closed protocol

Create an inventory before writing code:

```text
Intent
Query -> ResultPayload
Fact
ModuleCommand / ModuleResult imports
ObservedSignal
ControlPulse
SemanticOutput payloads: Projection, Reply, Effect, ModuleCommand, Signal, TimerIntent
BoundaryResponse before acceptance
```

For every variant, specify its owner, protocol version, origin, validation, causal identity, size bound, and terminal/error mapping. An omitted owned category is genuinely empty. Imported target types are not copied into the caller protocol.

Specific checks:

- external mutation/cancellation creates an `Intent` after validation;
- a business result remains a `Fact`/`ModuleResult`;
- a mechanical post-commit lifecycle/delivery observation becomes a `ControlPulse` only if it affects Sovereign State;
- a `Query` has one result payload and creates no commit/output identity;
- validation/admission/decision rejection before acceptance is returned as a `BoundaryResponse`, not a `ReplyOutput`.

## 5. Pure decision

Describe:

```text
decide(State, Pulse, DecisionContext) -> DecisionResult<State>
DecisionResult<State> = Accepted(Decision<State>) | Rejected(BusinessRejection)
read(CommittedStateSnapshot, Query, ReadContext) -> ReadResult<ResultPayload>
```

Verify that:

- identical canonical input produces a semantically equal result;
- clock/random/config/policy/actor/reserved IDs are passed explicitly;
- the current cause is not duplicated in context;
- every output field has one explicit State/current Pulse/DecisionContext lineage, and a prior-Pulse/Context value is retained or reintroduced through a declared trusted path;
- the output batch is complete, ordered, and bounded;
- the transition performs no I/O and does not depend on runtime capacity;
- overflow does not truncate state/output;
- there is no reentrant mutation;
- a fault before acceptance publishes no partial state/output.

## 6. Identity and causality

Trace the lineage:

```text
RequestId -> IdempotencyKey -> OperationId -> SemanticHandle
                                          -> CommitRevision
SemanticHandle + committed source -> OutputId -> AttemptId(s)
```

For every async input, specify the exact source handle, revision/generation, trusted issuer, duplicate identity, and stale/conflict policy. A retry changes the attempt but not the logical operation/handle.

## 7. Limits and profiles

Complete all nine §8.3 limits, then only the applicable profile caps. Select the execution/state/isolation/security dimensions independently. For every guarantee, complete its mechanism, assumptions, failure boundary, retention, and evidence; record the non-guarantee alongside it.

Do not accept a decision if finite capacity for the complete Decision/state/required retained evidence cannot be proven before acceptance.

## 8. Required artifacts

A minimal design change updates the following consistently:

- state/protocol types;
- value-liveness/retention/security worksheet and state-schema migration/quarantine artifact;
- manifest owned/imported surfaces and limits;
- Assembly routes/versions;
- authority/profile/guarantee overlay;
- transition/read/property/race/fault/security tests;
- glossary/checklist/laws/examples if their assertions are affected;
- the change/decision record under project governance.

## 9. Review output

The agent concludes the review with this table:

| Unit | Result | Evidence | Remaining decision |
|---|---|---|---|
| Boundary/authority | pass/fail/partial | artifact/test | owner/action |
| Protocol/read closure | pass/fail/partial | manifest/types | owner/action |
| Decision/commit | pass/fail/partial | transition/fault tests | owner/action |
| Async/identity | pass/fail/partial | traces/race tests | owner/action |
| Security/limits/profiles | pass/fail/partial | enforcement/evidence | owner/action |

The phrase "conforms to Pokeball" is permitted only with an exact baseline, an accepted overlay, and an explicit list of gates passed.
