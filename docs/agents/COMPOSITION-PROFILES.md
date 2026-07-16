# Composition and Profiles

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Primary rules: `PKB-AR-STA-002`, `PKB-AR-CMP-001`–`004`, `PKB-AR-PRF-001/002`, `PKB-AR-MAN-002`, `PKB-AR-LIM-002`.

## 1. Classify every edge

| Kind | When to use it | Required declarations |
|---|---|---|
| `ReadDependency` | A small read-only contract with another authority. | Source, result/stamp/freshness, no command authority. |
| `DeclaredCommandDependency` | One bounded command hop with no independent workflow. | Qualified operation, versions, route, idempotency, delivery, result. |
| `DeclaredSignalDependency` | A committed publication is observed by bounded consumers. | Producer/consumer versions, identity, dedup, ordering, fan-out/attempt caps. |
| `FlowParticipation` | Coordination itself has state/lifecycle/terminal meaning. | Flow owner, participants, steps, deadlines, compensation/reconciliation. |

No edge is resolved merely by the phrase "event bus," a service locator, package visibility, or runtime discovery.

## 2. One-hop or Flow

One-hop remains permitted when there is no independent multi-participant state, branching/join, compensation/reconciliation, independent lifecycle, manual queue, or separate terminal outcome.

A Flow is required when at least one substantial coordination property exists:

- multiple authorities converge on one terminal outcome;
- sequence/branch/join is business state;
- compensation or reconciliation belongs to the coordinator;
- the operation lives independently of the initiating Feature;
- shared deadline/cancellation/manual intervention;
- separate retention/security/scaling requirements.

A Flow owns handles, captured versions, phase, branch/join, deadlines, compensation/reconciliation, terminal workflow outcome, and only the field-minimized captured/result references required by later decisions/recovery. Every reference carries authority/observed-via correlation, versions, verified provenance, and a deletion trigger; this does not make the Flow the authority for a participant fact. The Flow neither copies nor reads an internal participant/runtime ledger as decision input and gains no right to violate a target invariant.

## 3. Graph review

Build four distinct graphs:

1. compile-time imports;
2. direct synchronous/control dependencies;
3. async signal/command feedback;
4. data/read provenance.

The first two must be acyclic unless covered by an explicit waiver. Async feedback is not declared a harmless cycle: it must have an owner, identity/dedup, a finite causal/retry budget, an escape condition, and fan-out protection.

Verify:

- every producer→consumer dependency edge is resolved by exactly one declared Assembly route/version;
- one `SignalPublication` may have multiple explicitly listed consumer edges only within `maxConsumersPerSignal` and `maxCumulativeFanout`; wildcard and duplicate edge resolution and unbounded subscriptions are prohibited;
- protocol versions are compatible;
- no wildcard target/consumer;
- participant/route/fan-out ceilings are finite;
- ordering scope is local and honest;
- multi-source values are not called an atomic snapshot without a mechanism;
- no protocol re-export and no feature internals import.

## 4. Profile dimensions

Select each row independently:

| Dimension | Core choices | Question |
|---|---|---|
| Execution | Inline / BoundedConcurrent | Are mailbox/workers/backpressure and race semantics required? |
| State | Transient / SnapshotOutbox / EventJournal | What survives a process crash, and how are outputs accepted? |
| Isolation | InProcess / Isolated | Is enforceable principal/crash/tenant containment required? |
| Security | Standard / Hardened | Which actor/grant/capability/audit controls are mandatory? |

Invalid shortcuts:

- `BoundedConcurrent` does not imply durability;
- `SnapshotOutbox` does not imply target receipt/eventual delivery;
- `Isolated` does not make application policy correct;
- `Hardened` does not replace scoped capabilities/safe sinks;
- `Transient` cannot promise post-crash retained work/status.

A `SnapshotOutbox`/`EventJournal` claim must restore exact retained decision values together with the state/output frame. If a binding restores the phase/handles but loses the payload lineage of a later command, the durability gate has not passed.

## 5. Guarantee record

Record every claim as follows:

```text
Claim:
Boundary:
Selected profile:
Mechanism:
Failure assumptions:
Ordering/delivery point:
Retry/retention horizon:
Evidence artifact:
Observed metric or proof:
Explicit non-guarantees:
Owner and review date:
```

The words `exactly-once`, `at-least-once`, `eventual delivery`, `durable`, `isolated`, `zero overhead`, `secure`, and `atomic` do not constitute a proven guarantee without this record.

## 6. Compensation/reconciliation

Every compensation is a new fallible operation with its own `SemanticHandle`, idempotency, current action-specific grant/capability, deadline, result/unknown, and reconciliation policy. The target is derived from the retained value plus the original authority handle/operation, or from an explicitly versioned participant handle-target contract; the Flow does not infer the target from ledger/history. A Capture grant is not reused for cancellation/refund/release/unlock. Compensation is not a rewind.

If the original outcome is unknown, conflicting compensation is not started without reconciliation or an explicitly accepted risk. Parallel compensation order is derived from the dependency graph, not from incidental completion order.

## 7. Foundation Quarantine

For every shared/foundation package, perform a separate scan:

1. list exported types/functions and all reverse dependencies;
2. classify each export as a stable mechanical primitive or a domain/policy concept;
3. verify that the package owns no mutable business state, protocol lifecycle, resource authority, or universal domain model;
4. compare the semantics, invariants, and change cadence of all consumers—matching shape/fields alone does not justify extraction;
5. record the verdict `allowed | relocate | explicit deviation`, owner, and evidence path.

Bounded containers, checked numeric operations, revision/deadline/cancellation, semantic/tracing IDs, validation, and small result/error primitives are permitted. `User`, `Order`, `Payment`, `Cart`, `Session`, `Product`, `CommonResponse`, `BaseEntity`, `UniversalDto`, and similar business authorities/models do not pass the scan merely because they are widely reused.

Evidence contains the export inventory, dependency/reverse-dependency scan, discovered domain symbols/authorities, exception/deviation owner, and review date. The accepted project policy is recorded in overlay `foundationPolicy`; absence of this artifact means `PKB-AR-CMP-004`/PBA-43 fails.

## 8. Required artifacts

- Assembly route table with versions and delivery semantics;
- Flow/participant authority map;
- cross-transition value-liveness/retention/recovery map;
- graph report and explicit waivers;
- profile/guarantee matrix;
- finite participants/routes/fan-out/concurrency/attempt/retention caps;
- foundation export/dependency scan and accepted `foundationPolicy`;
- transition/race/fault tests for the selected profiles;
- operational evidence for the claim.

If the mechanism/evidence is absent, the review result is "claim not substantiated," not an automatic redesign of the Core.
