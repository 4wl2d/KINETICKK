# Pokeball Project Overlay — Template

> **Status:** derived noncanonical template. It is not an accepted overlay, does not define the architecture, and does not extend the Core. Before using it, pass the baseline gate in `docs/agents/BASELINE.md`. If the two conflict, the canonical specification prevails.

Copy this file to `docs/pokeball-project-overlay.md`. Replace every `REQUIRED(...)` marker with a concrete value or `not-applicable: <verifiable reason>`. An accepted overlay contains no required markers and has an owner/date/revision.

The overlay concretizes only choices left open by the Core/accepted extension. It cannot silently weaken a normative rule. A departure is recorded in Deviations, and the affected scope is not called conforming.

## 1. Metadata

| Field | Value |
|---|---|
| Project | `REQUIRED(project name)` |
| Overlay revision | `REQUIRED(monotonic revision)` |
| Status | `draft` |
| Owner | `REQUIRED(accountable owner)` |
| Accepted by | `REQUIRED(project owner/review body)` |
| Accepted at | `REQUIRED(date)` |
| Scope | `REQUIRED(repositories/services/products covered)` |
| Core baseline | `REQUIRED(path and statement that BASELINE.md gate is READY)` |
| Accepted extensions | `REQUIRED(list with exact versions/scopes, or not-applicable)` |
| Supersedes | `REQUIRED(previous overlay revision, or not-applicable)` |

## 2. Conformance scope

### Included

`REQUIRED(exact Balls, flows, routes, bindings and environments covered)`

### Excluded

`REQUIRED(explicitly unreviewed components/environments/properties)`

### Claims

| Claim ID | Boundary | Mechanism | Assumptions | Evidence | Owner |
|---|---|---|---|---|---|
| `REQUIRED(id)` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED(path/review)` | `REQUIRED` |

### Explicit non-claims

| Boundary | Not claimed | Reason |
|---|---|---|
| `REQUIRED` | `REQUIRED(exactly-once/eventual delivery/durability/isolation/etc.)` | `REQUIRED` |

## 3. Deviations

If there are no deviations, record `none accepted`.

| ID | Core/extension source | Exact scope | Reason | Impact/non-conformance | Owner | Expiry/review | Controls |
|---|---|---|---|---|---|---|---|
| `REQUIRED` | `REQUIRED(section/law)` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

## 4. Ball inventory

Repeat the worksheet for every Ball.

### Ball: `REQUIRED(BallType id)`

#### Responsibility and boundary

| Field | Value |
|---|---|
| Kind | `REQUIRED(FeatureBall / FlowBall / Read Model Ball)` |
| Responsibility | `REQUIRED(one operational capability)` |
| Non-goals | `REQUIRED` |
| Protocol version | `REQUIRED` |
| State schema version | `REQUIRED` |
| Namespace | `REQUIRED` |
| StateKey type/value rule | `REQUIRED` |
| Full logical BallInstanceId | `REQUIRED(BallType + namespace + StateKey construction)` |
| Lifecycle/recovery owner | `REQUIRED` |
| Trust/isolation boundary | `REQUIRED` |

#### Authority and state

| Mutable semantic fact | Authority | Writer/fencing | State kind | Retention/recovery |
|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED(Sovereign/captured/replica/etc.)` | `REQUIRED` |

Strict invariants:

`REQUIRED(list and consistency boundary)`

#### Cross-transition decision values

Complete a separate entry for every value needed after the Pulse/Context that produced it; `not-applicable` requires proof that there are no later consumers.

| Value/output fields | Origin Pulse/Context | Authority + observed-via correlation | Protocol/schema versions + verified provenance | First atomic state assignment | Later consumers | Retention/deletion or trusted reintroduction | Security class + recovery evidence |
|---|---|---|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

#### Owned protocol

| Category | Variants or mappings | Origin/validation | Size/collection bound |
|---|---|---|---|
| Intents | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Queries | `REQUIRED(Query -> ResultPayload)` | `REQUIRED` | `REQUIRED` |
| Projections | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Replies | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Effects | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Facts | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Signals | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| ControlPulses | `REQUIRED` | `REQUIRED(trusted origin/provenance)` | `REQUIRED` |
| Timers | `REQUIRED` | `REQUIRED` | `REQUIRED` |

For an empty category, write `empty by protocol version` rather than leaving the row undefined.

#### Imported dependencies

| Qualified operation/source | Target/source version | Dependency kind | Assembly route ID | Result/stamp contract |
|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

#### Selected profiles

| Dimension | Choice | Mechanism | Explicit non-guarantee | Evidence |
|---|---|---|---|---|
| Execution | `REQUIRED(Inline/BoundedConcurrent)` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| State | `REQUIRED(Transient/SnapshotOutbox/EventJournal)` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Isolation | `REQUIRED(InProcess/Isolated)` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Security | `REQUIRED(Standard/Hardened)` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

#### Artifact paths

| Artifact | Path/ID |
|---|---|
| Manifest | `REQUIRED` |
| State/protocol schema | `REQUIRED` |
| State schema migration/quarantine | `REQUIRED(version transition, authoritative evidence/default refusal, rollout)` |
| Nucleus transition/read implementation | `REQUIRED` |
| Assembly routes | `REQUIRED` |
| Transition/property tests | `REQUIRED` |
| Profile/fault/security evidence | `REQUIRED` |

## 5. Operation Status Authorities

Repeat this for every long-running/durable operation family. `not-applicable` requires a reason.

### Status authority: `REQUIRED(logical id)`

| Field | Value |
|---|---|
| Covered operation type/namespace | `REQUIRED` |
| Authority kind | `REQUIRED(status ledger / Read Model Ball / other)` |
| State key/full instance identity | `REQUIRED` |
| Schema version | `REQUIRED` |
| Revision/writer/fencing | `REQUIRED` |
| Accepted/rejected source | `REQUIRED` |
| Workflow source positions | `REQUIRED` |
| Stop-eligible source-output universe/order/cap | `REQUIRED(all applicable Reply/Effect/Command/Signal/Timer/Projection handles or justified exclusions)` |
| Typed delivery-observation protocol/origins/provenance | `REQUIRED(including status-only observations that do not mutate workflow state)` |
| ConsistencyStamp construction | `REQUIRED` |
| NotFound proof | `REQUIRED` |
| Expired marker/horizon | `REQUIRED` |
| Pending out-of-order owner/cap/backpressure | `REQUIRED` |
| Multiple stopped-record key/order/cap | `REQUIRED` |
| Freshness/lag | `REQUIRED` |
| Retention/profile mechanism | `REQUIRED` |
| Explicit non-guarantees | `REQUIRED` |
| Read/materializer tests | `REQUIRED` |

The status authority does not become command authority. If sources are not materialized atomically, record this as lag/non-guarantee.

## 6. Assembly routes

Repeat this for every inter-Ball edge.

### Route: `REQUIRED(route id)`

| Field | Value |
|---|---|
| Kind | `REQUIRED(ReadDependency/DeclaredCommandDependency/DeclaredSignalDependency/FlowParticipation)` |
| Producer/source + version | `REQUIRED` |
| Output/query/operation | `REQUIRED` |
| Consumer/target + version | `REQUIRED` |
| Delivery point/semantics | `REQUIRED` |
| Identity/dedup policy | `REQUIRED` |
| Duplicate horizon | `REQUIRED` |
| Ordering scope | `REQUIRED` |
| Primary retry owner | `REQUIRED` |
| Initial + retry/attempt caps | `REQUIRED` |
| Queue/in-flight/backpressure | `REQUIRED` |
| Causal/fan-out caps | `REQUIRED` |
| Retention/status/reconciliation | `REQUIRED` |
| Security principal/capability | `REQUIRED` |
| Evidence | `REQUIRED` |

## 7. Limits

Repeat the mandatory table for every Ball.

### Ball limits: `REQUIRED(BallType)`

| Mandatory limit | Value | Enforcement | N/N+1 evidence |
|---|---:|---|---|
| `maxInputBytes` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxStateBytes` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxCollectionItems` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxOutputsPerDecision` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxEffectsPerDecision` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxCommandsPerDecision` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxCausalDepth` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxRetriesPerOperation` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| `maxTransitionSteps` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

### Applicable profile/domain caps

| Cap | Scope/unit | Value | Enforcement/evidence | Reason or not-applicable |
|---|---|---:|---|---|
| Delivery attempts/deadline | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Mailbox/workers/in-flight | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Retention/duplicate/status pending | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Response/decompression/IPC/storage | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Participants/routes/fan-out/branches | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Compensations/timers/subscriptions | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| CPU/wall/memory/network/file | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |
| Economic quota/reservation | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

## 8. Async policies

### Idempotency and retries

| Operation/failure mode | Key scope/mismatch | Horizon | Primary owner | Other layers | Evidence |
|---|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

### Unknown/reconciliation/manual

| Operation | Possible-send boundary | Unknown state | Reconcile identity/route | Terminal/manual policy | Evidence |
|---|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

### Cancellation/deadline/timer

| Operation | Typed request/outcomes | Generation/dedup | Race policy | Evidence |
|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

## 9. Security

### Trusted boundaries and actors

| Boundary | Raw input/output | Validation/provenance | Actor issuer/assurance | Failure response |
|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

### Grants and capabilities

| Privileged action | Grant issuer/audience/constraints | Current execution gate | Capability/credential owner | Evidence |
|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

For every delayed/recovered action, specify the issuance/reintroduction boundary, exact action-handle key, stable subject binding proof, context/expiry/revocation policy, typed missing/expired outcome, and protected committed-grant retention. A raw grant/session/principal is not live state; the grant for another action is not reused.

### Sinks, secrets and unsafe

| Path | Safe sink/encoding | Secret/PII/redaction policy | Isolation principal | Unsafe waiver ID/expiry |
|---|---|---|---|---|
| `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

## 10. Foundation policy

| Field | Value |
|---|---|
| Policy ID/revision/owner | `REQUIRED` |
| Foundation package paths | `REQUIRED` |
| Allowed mechanical primitive classes | `REQUIRED` |
| Forbidden domain/policy/authority classes | `REQUIRED` |
| Export + dependency/reverse-dependency scan artifact | `REQUIRED` |
| Mutable state/protocol/resource-authority scan | `REQUIRED` |
| Exceptions/deviations, owner and expiry | `REQUIRED(none or exact accepted records)` |
| Review date/result | `REQUIRED` |

`foundationPolicy` does not turn broad reuse into evidence: every allowed export must have matching semantics, invariants, and change cadence across consumers. A domain model or authority is not silently moved into foundation.

## 11. Evidence register

| Evidence ID | Rule/gate | Artifact/test/measurement | Exact baseline/environment | Result | Owner/date |
|---|---|---|---|---|---|
| `REQUIRED` | `REQUIRED(PKB-AR/RG)` | `REQUIRED` | `REQUIRED` | `REQUIRED` | `REQUIRED` |

Coverage must include manifests, Assembly, transition/read/property/race/fault/security tests, profile claims, and benchmarks/threat assumptions where applicable.

## 12. Acceptance

Before changing `draft -> accepted`, the reviewer confirms:

- baseline gate `READY`;
- all markers have been replaced or have a `not-applicable` reason;
- deviations are explicit;
- every Ball/route/profile/status authority has an owner and evidence;
- cross-transition value lineage, schema migration/quarantine, and delayed authorization are complete without a hidden ledger/history read;
- all nine mandatory limits are complete for every Ball;
- selected claims have non-guarantees;
- `foundationPolicy` is complete and the scan evidence resolves;
- `RG-01–10` have a pass/explicit partial result;
- the overlay revision/date/acceptor are recorded.

```text
Decision: REQUIRED(accepted | rejected)
Accepted overlay revision: REQUIRED
Accepted by: REQUIRED
Date: REQUIRED
Review/evidence ID: REQUIRED
Remaining partial scope: REQUIRED(none or exact list)
```
