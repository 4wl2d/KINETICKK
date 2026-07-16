# Example Crosswalk

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Catalog (§15) and Checkout (§16) are executable mental models and test fixtures. They do not define the project domain, default profile, limit values, or a mandatory number of Balls.

## Catalog Feature Ball

| Question | Canonical anchor | Agent rules | What it demonstrates |
|---|---|---|---|
| Boundary | §§15.2–15.5 | `PKB-AR-BND-001/002` | Session authority and Interaction/Nucleus/Resource zones without a global store. |
| State kinds | §15.2 | `PKB-AR-STA-001/002` | Search state, generations, and projection are not conflated. |
| Protocol | §§14.2, 15.1 | `PKB-AR-PRT-001/002` | Manifest + Intent/Effect/Fact/Projection/Signal closure. |
| Stale results | §§15.2, 15.4, 15.6–15.7 | `PKB-AR-ID-001`, `PKB-AR-ASY-001` | Generation/handle determines apply/ignore. |
| Cancellation | §§15.1–15.2, 15.8 | `PKB-AR-ASY-002/004` | Typed cancel/result races, compatible observation-order convergence, and a symmetric mutually-exclusive terminal-proof fault without last-arrival overwrite. |
| Security | §§15.3, 15.5 | `PKB-AR-SEC-001`–`003` | Ingress quarantine, restricted capability, safe sink, and response validation. |
| Timeout/unknown | §15.9 | `PKB-AR-ASY-002/003` | A possible-send boundary is not collapsed into a proven failure. |
| Summary | §15.10 | `PKB-AR-TST-002` | What the example proves and where its boundaries lie. |

### Catalog review trace

```text
SearchRequested generation N
-> accepted state + FindProducts handle N
-> resource Fact with exact handle/provenance
-> apply current / ignore stale / typed failure or unknown
-> projection/signals only after acceptance
```

Verify cancellation across all declared outcomes and result/cancel permutations. Do not infer durability or external exactly-once: the example does not promise either by itself.

Compatible evidence order must converge without a hidden buffering contract:

```text
AcceptedInProgress -> ProductsFound|Failed = Ready|Failed(AcceptedInProgress)
ProductsFound|Failed -> Ready|Failed(Requested) -> AcceptedInProgress = Ready|Failed(AcceptedInProgress)

AcceptedInProgress -> ProductSearchCancelled = Cancelled(AcceptedInProgress)
ProductSearchCancelled -> Cancelled(AcceptedInProgress) -> late AcceptedInProgress = corroborating no-op
```

For a terminal conflict, both permutations must be explicit:

```text
ProductSearchCancelled -> Cancelled -> contradictory ProductsFound = invariant/provenance fault
ProductsFound -> Ready -> contradictory ProductSearchCancelled = invariant/provenance fault
```

A terminal cancellation Fact itself proves acceptance, so AIP-first transport ordering is not assumed. A later weaker AIP creates no new semantic state/output. The mutually exclusive second proof creates no Decision/state/output and does not overwrite the first accepted terminal frame. `CancellationTooLate`, rejected/unknown cancellation, and a legitimate nonterminal late result remain separate compatible paths.

## Checkout Flow Ball

| Question | Canonical anchor | Agent rules | What it demonstrates |
|---|---|---|---|
| Flow threshold/owner | §§16.1, 16.14 | `PKB-AR-CMP-001/002` | An independent coordination authority, not a mediator. |
| Ingress/idempotency | §16.2 | `PKB-AR-PRT-002`, `PKB-AR-ASY-003` | One versioned canonical `CheckoutStartFingerprintV1(current Pulse, trusted actor Context)`: exact actor/namespace/business tuple, key/transport exclusion, equality of the retained value with the atomic Interaction record, and mismatch behavior. |
| State/facets | §16.3 | `PKB-AR-STA-001/002`, `PKB-AR-ASY-002/005` | Steps/facets and retained M/S/I/P value lineage. |
| Authorization | §16.4 | `PKB-AR-STA-002`, `PKB-AR-SEC-002` | Stable subject binding + current handle-scoped grant; no raw grant/principal in live state. |
| Sequence | §§16.5–16.9 | `PKB-AR-STA-002`, `PKB-AR-ID-001`, `PKB-AR-DEC-002` | A new handle per step; M→S→I→P retained atomically before downstream use. |
| Unknown/reconcile | §16.8 | `PKB-AR-ASY-002/003/005` | Acceptance unknown is separate from outcome unknown. |
| Compensation | §16.10 | `PKB-AR-STA-002`, `PKB-AR-CMP-002`, `PKB-AR-ASY-003` | Exact retained P/I/cart+original-handle targets; fallible new work, not rewind. |
| Cancellation | §16.11 | `PKB-AR-ASY-004` | Result/cancel race without loss. |
| Commit/delivery | §16.12 | `PKB-AR-DEC-002/003`, `PKB-AR-ASY-005` | Commit-before-dispatch and typed post-commit observation. |
| Status | §16.13 | `PKB-AR-PRT-003`, `PKB-AR-ASY-005` | Independent stamped status authority and bounded multi-stop records. |

### Checkout delivery trace

```text
Decision commits Step = NotDispatched/NotAccepted/NotExpected
-> runtime dispatch attempt after acceptance
-> declared CheckoutCommandDeliveryObserved ControlPulse
-> next serialized Decision updates Step
-> ambiguity may create one Payment.GetOperationStatus step
-> status authority materializes workflow + trusted delivery facts
-> initial RequestAccepted ReplyOutput stop enters status authority only, not CheckoutState
```

### Checkout value-liveness trace

```text
CheckoutStarted(M, actor A)
F = exact accepted actor/namespace-scoped CheckoutStartFingerprintV1(current Pulse, A)
-> atomically retain captured M + F + stable actor binding
CartLocked(S) via cartLockHandle
-> retain S with authority/observed handle, versions, provenance
InventoryReserved(I) via inventoryHandle
-> retain I + emit Capture(S, M, current grant[paymentHandle])
PaymentCaptured(P) via paymentHandle
or Captured(P) observed via statusHandle for authority paymentHandle
-> retain normalized P + emit Confirm(S, I, P)
Order rejected / CancellationTooLate
-> Refund(P + original payment handle)
-> Release(I + original reservation handle)
-> Unlock(cartId + original lock handle)
```

The same P through a second valid route corroborates without a second Order output; conflicting P/original proof fails closed. `stateSchemaVersion` changes from `1 → 2`, while `protocolVersion` remains `1.0.0`; active v1 state without authoritative M/S/I/P migration evidence receives no defaults and enters the declared quarantine/manual path.

Test variations:

- the same CheckoutStarted business fields for two stable subjects and two issuer/realm scopes produce different F values;
- the same actor/business tuple with a different idempotency key, RequestId, or trace/reply metadata produces the same F; same key + same F returns the previous OperationId, while same key + different F produces a conflict;
- retained F is byte-for-byte equal to the atomic Interaction idempotency record before and after a crash; a prior transition artifact passes verify/migrate/quarantine without a normal ledger read;
- crash before dispatch;
- accepted/rejected ACK without result;
- result before ACK;
- ACK loss/ambiguity;
- terminal delivery stop then late proof;
- duplicate/stale/conflicting observation;
- Refund + Inventory.Release stops in both orders;
- initial RequestAccepted reply stop + all nine command stops, capacity `10/11`;
- stop before base status projection and stop/retention race.
- crash/recovery after Start, CartLocked, InventoryReserved, direct/reconciled capture, and order rejection: the exact downstream payload/handle matches the uninterrupted trace;
- missing/expired/wrong current grant after a delay: no privileged output, and the exact typed compensation/manual outcome;
- cancellation-too-late retains P before Refund; premature cleanup and a conflicting value/version/proof for one authority action fail closed.

## What must not be copied as a default

- `CatalogSessionId`/`CheckoutOperationId` as a universal `StateKey`;
- manifest numeric limits without workload/evidence;
- four Checkout participants or nine routes;
- SnapshotOutbox/durability if the project's selected profile does not require it;
- grant fields without a concrete issuer/audience/object model;
- Checkout status authority identity/schema/writer without a project overlay;
- the example package/module layout as a mandatory repository structure.

## How to use the example in a review

1. Find only an analogous property, not a similar domain name.
2. Cite the canonical section/PBA and `PKB-AR-*`, not only the example.
3. Compare the project artifact with the invariant/trace.
4. Record differences in profiles/limits/trust/retention.
5. If the example does not cover the property, return to the Core; do not derive a rule from preference.
