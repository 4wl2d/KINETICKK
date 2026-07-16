# Async and Status Runbook

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Primary rules: `PKB-AR-ID-001`, `PKB-AR-STA-002`, `PKB-AR-ASY-001`–`005`, `PKB-AR-DEC-002/003`, `PKB-AR-PRT-002/003`, `PKB-AR-LIM-002`, `PKB-AR-PRF-002`.

## 1. Draw one causal lineage

For every async operation, complete:

| Element | What to record |
|---|---|
| Logical identity | `OperationId`, `SemanticHandle`; `IdempotencyScope + IdempotencyKey + exact IntentFingerprint tuple/origin`; actor/target scope comes from declared Pulse/Context operands, key/transport metadata remains separate, and the retained fingerprint equals the atomic accepted record. |
| Accepted source | `BallInstanceId`, `CommitRevision`, `sourceOrdinal`, protocol/artifact version. |
| Mechanical delivery | `OutputId`, `AttemptId`, delivery point, attempt cap. |
| Result origin | Target/resource operation, issuer provenance, accepted-operation proof. |
| Later-used value | First atomic state assignment, authority/observed-via handles, both protocol versions, normalized provenance, consumers and deletion/reintroduction trigger. |
| Duplicate/stale | Stable observation/input ID, retention, generation/revision policy. |
| Unknown/reconcile | Trigger, owner, status command identity, terminal/manual path. |

No downstream result is applied based only on a matching payload, request ID, or transport order. No later output reads a prior Pulse, inbox/outbox, runtime, or participant ledger: the required value has already been committed as a bounded captured/result value or explicitly reintroduced by the current trusted input.

## 2. Do not conflate the four facets

| Facet | Minimum meaning |
|---|---|
| Dispatch | Not sent, might have been/was sent, finite policy stopped. |
| Acceptance | Target did not accept, accepted, rejected before acceptance, acceptance unknown. |
| Business outcome | Pending/succeeded/rejected/failed/cancelled/outcome unknown. |
| Cancellation | Not requested and all accepted/too-late/rejected/unknown races. |

Verifiable invariants:

- a source commit of a new output does not imply `Dispatched`;
- `NotDispatched` is incompatible with a target-produced terminal result;
- accepted-command proof advances acceptance to `Accepted` even if the ACK is lost;
- result-before-ACK also proves actual delivery unless the policy had already stopped;
- `DispatchStopped` does not prove rejection, failure, cancellation, or non-execution;
- a late ACK/result may refine acceptance/outcome but does not erase the stop of the current delivery policy;
- a cancellation observation does not delete a legitimate result;
- a compatible accepted-in-progress observation before/after a result preserves lifecycle and converges in one facet; a terminal cancellation Fact may prove acceptance without undeclared AIP-first delivery, while a later weaker AIP is corroboration without regression or duplicate output;
- mutually exclusive terminal result/cancellation proofs are not resolved by last-arrival-wins: the previously accepted terminal frame is retained, and the second proof fails closed before acceptance with no new state/output, symmetrically in both orders.

## 3. Select the correct causal input

| Observation | Canonical category |
|---|---|
| External request to start/cancel | Validated `Intent`. |
| Outcome private resource request | Provenance-bound `Fact`. |
| Outcome target Ball command | Provenance-bound `ModuleResult`. |
| Committed signal publication | `ObservedSignal` over a declared route. |
| Mechanical runtime/route lifecycle, dispatch, or ACK that affects state | Declared typed `ControlPulse`. |

Runtime does not write operation facets directly into Sovereign State. A `ControlPulse` must reference the exact committed source tuple and have trusted provenance, bounded/redacted evidence, and stable duplicate identity. An exact duplicate is idempotent; same-ID/different-payload and incompatible decisive proofs fail closed.

## 4. ACK, result, and ambiguity

Walk through these traces:

```text
commit -> crash before dispatch
commit -> dispatch -> ACK accepted -> later result
commit -> dispatch -> ACK rejected before target acceptance
commit -> dispatch -> ACK lost -> acceptance unknown -> status reconciliation
commit -> result with accepted proof before ACK
commit -> finite attempts exhausted -> DispatchStopped
DispatchStopped -> late ACK/result
```

For ambiguity, evidence must confirm crossing the declared source dispatch point. A timeout before a possible send does not create `AcceptanceUnknown`. A status/reconciliation command has a new handle but preserves the identity of the original operation; duplicate ambiguity does not create a second logical reconcile step.

## 5. Retry ownership

Complete one row for every failure mode:

| Failure mode | Primary owner | Logical key | Initial + retries | Horizon | Other layers |
|---|---|---|---:|---|---|

Check the multiplicative composition SDK × adapter × runtime × Flow. Other layers either do not retry or are bounded and demonstrably transparent. `maxRetriesPerOperation` is not equal to `maxDeliveryAttempts`; each applies within its own scope.

A blind retry after possible external execution is permitted only when semantic identity and the target/provider idempotency horizon are preserved. Otherwise, a status/reconciliation/manual path is required.

## 6. Cancellation, deadline, and timer

Cancellation is a separate operation/race:

- an external cancel enters as an `Intent`;
- an outgoing cancel is a typed `Effect`/`ModuleCommand` with its own handle;
- the executor/target outcome returns as a `Fact`/`ModuleResult`;
- accepted-in-progress does not imply a terminal business outcome;
- too-late/rejected/unknown does not erase a result;
- timer firing has a stable timer identity, generation, dedup, and explicit late policy.

Test both cancel/result orders, timeout/late result, and old generation/new generation. For exact lineage, separately exercise `AIP -> result` and `result -> AIP`: both produce the same result lifecycle with the AIP facet. Then verify `AIP -> terminal cancellation` and `terminal cancellation -> late AIP`: the terminal Fact itself proves acceptance, and the later weaker AIP is a corroborating no-op. Finally, both permutations of mutually exclusive terminal result/cancellation proofs preserve the first terminal frame, while the second proof produces an invariant/provenance fault before acceptance with no state/output.

## 7. Operation Status Authority

For every long-running/durable operation, the overlay records:

- logical authority id/type and authenticated namespace;
- state key/instance identity, schema version, writer/fencing;
- source positions/revisions and materialization owner;
- `ConsistencyStamp` construction;
- accepted/rejected/workflow/delivery source mappings;
- full stop-eligible `SemanticOutput` universe, canonical order and capacity reservation;
- `NotFound`, retention marker, and marker horizon;
- freshness/lag/non-guarantees;
- pending/out-of-order capacity and backpressure;
- status retention under the profile.

The status authority does not become command authority for the original business facts. Its stamp proves the status snapshot read but not the atomic freshness of all sources.

### Multiple stopped deliveries

An operation with multiple retained outputs stores zero-to-bounded-many records independently of output kind:

```text
SemanticHandle -> { typed reason, attempts, typed lastObservation }
```

Requirements:

- full handle — unique key;
- finite per-operation cap covers all permitted retained output/step handles;
- A Reply/Effect/Command/Signal/Timer/Projection output is excluded only by an explicit Core/profile contract, not because it is not a workflow Step;
- deterministic serialization does not depend on arrival/map order;
- identical duplicate — no-op;
- same-key conflicting terminal evidence is an invariant fault, not last-write-wins;
- another handle is added without eviction;
- lifecycle/cancellation are not rewritten;
- a stop that arrives before the causal workflow record is bounded-pending or causally ordered, but not dropped;
- a retention marker is created after covered source positions and an empty pending set;
- a covered late duplicate does not resurrect an expired operation.

For Transient, retention ends with the process lifetime. A durable claim requires a selected durable profile, an atomic source record/output mechanism, and a declared horizon.

## 8. Mandatory test matrix

| Trace | Expected evidence |
|---|---|
| Crash before dispatch | State does not say `Dispatched`; output fate matches the profile. |
| ACK before dispatch pulse | Monotonic join, with no impossible `NotDispatched + Accepted`. |
| Result before ACK | `Accepted` plus terminal/pending outcome, with no retry of a successful logical operation. |
| Compatible cancellation observation order | `AIP -> result` and `result -> AIP` converge without lifecycle regression; terminal cancellation before AIP is accepted as terminal proof, and a late AIP is a no-op without duplicate output. |
| Terminal result/cancel conflict | Both permutations preserve the first accepted terminal frame; the second mutually exclusive proof accepts no Decision/state/output and fails closed rather than using last-write-wins. |
| ACK loss | `AcceptanceUnknown` only after a possible send; one reconciliation handle. |
| Stop before/after late proof | The stop is retained; the proof refines other facets. |
| Duplicate/stale/conflict | No-op, no regression, fail-closed conflict. |
| Two independent stops | Both arrival orders produce the same full status. |
| Reply + command stops | The initial reply and all permitted command steps belong to one exact handle universe; a status-only reply observation does not mutate workflow state. |
| Capacity `N/N+1` | `N` is lossless; `N+1` is not accepted before source acceptance. For Checkout, the exact boundary is `10/11`. |
| Stop before base projection | Bounded pending; canonical merge after the base record. |
| Stop versus expiry | A covered late duplicate neither loses nor resurrects the record. |
| Status read | Exact authority stamp, typed bounded redacted payload. |
| Crash between M→S→I→P steps | Recovery yields the exact retained value/provenance and the same downstream payload/handle without rerunning `decide` for a committed output. |
| Direct/reconciled P | There is one authority handle; observed-via handle/version differ; the same P corroborates without a second Order, while conflicting P/proof fails closed. |
| Order reject/cancel-too-late | Refund/Release/Unlock use exact retained P/I/cart plus original handles and separate current grants; premature cleanup is prohibited. |
