# Security and Limits Runbook

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Primary rules: `PKB-AR-STA-002`, `PKB-AR-SEC-001`–`003`, `PKB-AR-LIM-001/002`, `PKB-AR-PRF-002`, `PKB-AR-DEC-001/002`.

## 1. Authority/threat map

For every privileged path, name:

```text
raw source
trusted parsing/validation boundary
stable actor and assurance
business authorization owner
grant issuer/audience/constraints
technical capability and credential owner
safe sink/interpreter
result provenance boundary
secrets/PII classification
isolation principal
limits/quotas/backpressure
audit/evidence owner
```

Package/module visibility is not a security boundary. Hostile code requires enforceable isolation if the project claims containment.

## 2. Double Quarantine

| Boundary | Required checks | Output |
|---|---|---|
| External → Interaction | Parse, schema/range/size, normalization, auth source, unknown fields, encoding. | Typed `Intent`/`Query` or pre-acceptance `BoundaryResponse`. |
| Resource/target → adapter | Provenance/issuer, schema/range/size, decompression, correlation, error mapping, redaction. | Typed `Fact`/`ModuleResult`/declared observation. |

A raw framework request, SDK response, database row, provider exception, or unbounded body does not enter the Nucleus.

## 3. Grant and capability

The business gate in the Nucleus checks the actor, action, object, and state/policy and creates a target-scoped requirement/grant. The execution gate checks:

- approved issuer and integrity;
- audience/target;
- actor/action/object/operation;
- full constrained payload fingerprint;
- version/current authoritative constraint;
- validity, revocation, and anti-replay;
- presence of the minimum technical capability.

A capability is implemented through a restricted client/credential/broker/ACL. An admin client, ambient global client, or arbitrary URL/path/SQL/shell is not a minimum capability.

For a privileged action after delay/recovery, live state stores only a bounded stable-subject/value binding with issuer/realm, correlation, versions, provenance, and retention—not a raw grant, bearer, credential, session, or principal. The current action grant arrives through a declared trusted versioned `DecisionContext`, keyed by the exact action handle, and proves the same subject binding. Missing/expired/revoked/stale/mismatched proof creates no privileged output and leads to the named typed state/outcome. A Capture grant is not reused for Cancel/Refund/Release/Unlock; a committed output retains its own immutable grant in a protected/redacted record until the dispatch/audit horizon, and the Execution Gate checks actual execution.

## 4. Safe sinks, secrets, and unsafe paths

For every sink, record a structured/parameterized/context-encoded API. User data is not interpreted as code, a query, path, shell, or template instruction.

Secrets/PII and sensitive payment/actor/provenance references do not enter an ordinary state projection, status evidence, logs, metrics, traces, or replay artifact without an explicit policy. Delivery/status evidence uses typed bounded redacted codes/digests, not a raw exception.

An unsafe escape hatch contains:

```text
unique name
owner
exact capability/scope
business justification
threat controls
test/evidence
reviewer
expiry/review date
revocation path
```

An expired or incomplete waiver fails closed.

## 5. Nine mandatory decision limits

Every manifest declares all rows:

| Limit | Scope |
|---|---|
| `maxInputBytes` | One normalized `Pulse` with boundary metadata. |
| `maxStateBytes` | Retained canonical state of one instance. |
| `maxCollectionItems` | Every bounded collection; the byte cap still applies. |
| `maxOutputsPerDecision` | The complete output batch of a Decision. |
| `maxEffectsPerDecision` | `EffectRequest` instances in one Decision. |
| `maxCommandsPerDecision` | `ModuleCommandRequest` instances in one Decision. |
| `maxCausalDepth` | Mutating hops total causal scope. |
| `maxRetriesPerOperation` | Policy retries beyond the initial attempt. |
| `maxTransitionSteps` | Deterministic work units of one `decide`. |

There are no implicit defaults. Zero is permitted only for an absent effect/command kind or disabled retries. Overflow neither truncates state/collection/output nor dispatches a subset.

## 6. Applicable additional caps

Declare these only when the path exists, but then declare them explicitly:

- delivery attempts, deadlines, duplicate/retention horizons;
- mailbox capacity, workers, in-flight/pending items, fairness;
- response/decompression/IPC/storage bytes;
- participants, routes, consumers, fan-out, parallel branches;
- compensations, timers/subscriptions, status pending/stop records;
- CPU/wall/memory/network/file/process caps;
- economic quotas/reservations.

A tighter domain-specific cap does not become a new mandatory Core limit. It must not exceed the general cap and must cover every accepted semantic fact without eviction.

## 7. Admission and economic policy

Runtime capacity does not change the business choice. Before acceptance, preflight reserves the complete state/output/required continuation capacity. Insufficient capacity returns a typed admission/backpressure outcome; an already accepted fact is not forgotten.

An economic quota has an authority, reservation/commit/release lifecycle, units, reset horizon, and race policy. A simple counter check without atomic reservation is not enforcement.

## 8. Evidence

At minimum:

- parser/fuzz/size/decompression tests;
- forged/wrong-audience/expired/revoked grant tests;
- delayed/recovered action tests for wrong subject binding/context version/handle and separate Capture/Cancel/Refund/Release/Unlock grants;
- payload substitution tests;
- restricted capability and safe-sink contract tests;
- secret/redaction snapshots;
- N/N+1 tests for every bound;
- full retained-value state/output frame N/N+1 and premature-cleanup tests;
- mailbox/backpressure/fairness tests under concurrency;
- fault/retention tests under durability;
- a benchmark with workload/hardware/build method for a performance claim;
- isolation escape/crash tests if containment is claimed.

Missing evidence for a claim weakens the claim but does not authorize the agent to invent a mechanism.
