# Test and Review Gates

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Primary rules: all `PKB-AR-*`, especially `PKB-AR-TST-001/002`, `PKB-AR-GOV-003`, and `PKB-AR-PRF-002`.

## 1. Before review

Record:

```text
exact Core baseline
project overlay revision
code/artifact baseline
mode: answer | targeted review | validation | full release gate
atomic coverage units
questions
explicit exclusions
selected profiles/claims
previous evidence
```

Do not review a moving baseline. Validation checks the criteria/diff/affected axes and does not become new discovery without a separate scope.

## 2. Evidence matrix

| Contract | Minimum evidence |
|---|---|
| Boundary/authority | Authority map, StateKey/writer enforcement, cross-state dependency check. |
| Closed protocol | Exhaustive type/manifest resolution, exact `DecisionResult` wrapper, unknown variant/version tests. |
| Pure decision | Transition/property tests, determinism inputs, no-I/O/import checks, every output field traced to State/current Pulse/versioned Context. |
| Atomic acceptance | Fault injection before/after publication, full batch capacity. |
| Async identity | Causal source/provenance, prior-value first assignment/retention, duplicate/stale/out-of-order/race, and crash-recovery tests; compatible AIP/result orderings converge, terminal cancellation proof does not depend on AIP-first delivery, and a late weaker AIP does not regress state/output; both permutations of mutually exclusive terminal result/cancellation proofs fail closed without overwrite; the retained ingress fingerprint equals the atomic accepted record, actor/realm scope is separated, and key/RequestId/trace/reply metadata is excluded. |
| Status | Exact Query mapping/stamp, lifecycle/facets, every stop-eligible output handle, multi-stop/retention races. |
| Composition | Assembly/version/route graph, fan-out/causal caps. |
| Security | Boundary, current handle-scoped grant/reintroduction, capability/sink/secret/unsafe tests; no raw grant/principal in live state. |
| Limits | Manifest + full retained state/output N/N+1 tests + runtime cap enforcement. |
| Profiles/claims | Mechanism, assumptions, failure injection/benchmark, non-guarantees. |

Mocks of framework objects within the Nucleus do not replace pure transition tests. A manifest declaration does not replace enforcement. A benchmark without workload/hardware/build method does not prove a claim.

For a mutating decision, the validator compares the signature with Core §§3.3/6.7: the exact sum is `Accepted(Decision) | Rejected(BusinessRejection)`. A bare `BusinessRejection` as an alternative codomain is not shorthand and fails `RG-02`, `RG-04`, and `AP-GATE-08`.

## 3. Ten release gates

| Gate ID | Name | Question | Pass evidence |
|---|---|---|---|
| `RG-01` | Scope | Are the boundary, goals/non-goals, and extension limits consistent? | Scope card, authority map, no hidden redesign. |
| `RG-02` | Protocol | Is the protocol/read/output algebra closed and versioned? | Exhaustive manifests/types/query mappings. |
| `RG-03` | Authority | Does every mutable fact/workflow have one authority/writer, and is no retained reference presented as participant truth? | State/instance/status authority + value-lineage evidence. |
| `RG-04` | Decision | Are determinism, explicit State/Pulse/Context inputs, acceptance, commit-before-dispatch, and fault atomicity preserved? | Field-lineage, transition/property/fault tests. |
| `RG-05` | Async | Are identity, retained result values, ACK/result, unknown, retry, cancellation, and delivery/status races total? | M→S→I→P crash traces, compatible observation-order convergence, terminal proof subsumption, symmetric terminal result/cancel conflict, multi-stop and retention tests. |
| `RG-06` | Composition | Are edges/routes/versions/Flow ownership and graph bounds explicit? | Assembly/dependency/graph validation. |
| `RG-07` | Security | Are Double Quarantine, stable actor binding/current per-action grants, capabilities, sinks, secrets, and unsafe paths closed? | Delayed/recovered authorization tests + threat controls. |
| `RG-08` | Profiles | Do guarantees match selected profiles, assumptions, retained-value recovery, and non-guarantees? | Profile matrix + crash/recovery evidence. |
| `RG-09` | Limits | Are Decision/runtime/delivery/economic caps finite and tested? | Complete limits + N/N+1/backpressure. |
| `RG-10` | Integrity | Are examples, laws, tests, checklist, glossary, agent docs, and traceability consistent? | Structural/link/index/hash/package checks. |

A gate receives `pass`, `fail`, or `partial`. `partial` is not a release pass. Finding zero problems is normal; it is not proof of perfection outside the scope.

## 4. Profile-specific suites

### Inline/Transient

- reservation/publication before/after fault;
- full retained frame inside process;
- no hidden queue/thread/serialization tax claim;
- explicit process-loss non-guarantee;
- external ambiguous outcome after crash.

### BoundedConcurrent

- mailbox full/backpressure;
- duplicate ingress;
- out-of-order completion;
- cancel/deadline/result races;
- AIP/result converge in both orders; terminal cancellation before a separate AIP proves acceptance, and a late AIP is a no-op;
- both terminal result/cancel conflict orderings: first accepted frame retained, contradictory proof has no accepted Decision/state/output;
- finite workers/causal chain/fairness.

### SnapshotOutbox/EventJournal

- before/inside/after transaction crash points;
- commit ACK lost;
- crash before dispatch and between outputs;
- request sent/ACK lost/target accepted before source update;
- recovery from committed output records without rerun;
- Checkout same-payload/two-subject and two-realm separation, same-actor key/transport exclusion, same-key reuse/conflict, and exact accepted-record/retained-fingerprint equality after recovery;
- a persisted record from a previous transition artifact passes declared equality verification/migration or quarantine; matching state shape is not sufficient, and the normal Nucleus does not read the ledger;
- exact retained M/S/I/P + authority/observed-via provenance after Start, CartLocked, InventoryReserved, direct/reconciled capture and order rejection;
- same P corroboration, conflicting P/proof failure, premature cleanup and v1→v2 migration refusal without authoritative evidence;
- attempt exhaustion/status/retention;
- initial ReplyOutput stop + all command-step stops in one operation, including capacity `N/N+1`;
- ownership fencing and unknown commit outcome.

### Hardened/Isolated

- forged/wrong issuer/audience/payload/expired/revoked grant;
- delayed/recovered action uses a current exact-handle grant and same-subject proof; Capture/Cancel/Refund/Release/Unlock grants are not reused;
- raw grant/session/principal absent from live state/status/telemetry; committed output grant protected/redacted until declared horizon;
- credential/capability boundary;
- safe-sink injection and secret redaction;
- isolation escape/crash/resource limit tests;
- unsafe waiver expiry/revocation.

## 5. Agent Pack gates

| Gate ID | Name | Pass condition |
|---|---|---|
| `AP-GATE-01` | Package closure | All declared files are present; relative links are not broken. |
| `AP-GATE-02` | Baseline integrity | The exact hash/byte count/version matches; the Core hash occurs within the package only in `BASELINE.md`. |
| `AP-GATE-03` | Noncanonical precedence | Every file is marked derived; there is no hidden override of the Core. |
| `AP-GATE-04` | Rule integrity | Every `PKB-AR-*` rule is defined exactly once and is unique. |
| `AP-GATE-05` | Core coverage | §§0–23, PBA-01–43, and all glossary terms are indexed. |
| `AP-GATE-06` | Traceability | Every rule has a source, semantically matching runbook procedure, artifact/evidence, gate, and actual existing overlay field. |
| `AP-GATE-07` | Overlay completeness | In a consuming project, the accepted overlay has no incomplete required fields; the source package records this gate as `N/A`, not `pass`. |
| `AP-GATE-08` | Protocol/async/value closure | Owned/imported/query/routes, async/status policies, and cross-transition decision values are resolved without a hidden variant/read. |
| `AP-GATE-09` | Claims/security/limits | Claims have a mechanism/evidence/non-guarantee; authority and caps are explicit. |
| `AP-GATE-10` | Portability dry run | A clean target layout actually performs the documented template relocation/routing, retains the scoped `LICENSING.md` within the exact package, and then preserves semantic destinations, links/hash without the source governance repository and without changing the project's software license. |

`PROJECT-OVERLAY.template.md` intentionally contains required markers. In the source package, `AP-GATE-07 = N/A: template coverage verified`; `pass` is permitted only after checking an accepted copy in a consuming project. `AP-GATE-10` cannot be closed by copying the package in place: the validator must transfer the scoped `LICENSING.md` without replacing the project's software license, perform `PROJECT-OVERLAY.template.md -> docs/pokeball-project-overlay.md`, resolve links from the new location, and verify the routing block and attribution route.

## 6. Review record

```text
Review ID:
Mode:
Exact baselines:
Questions:
Coverage units:
Previous reviews:
Exclusions:
Method/evidence:
Applicable PKB-AR rules:
Gate results RG-01..10:
Issue/change IDs:
Failed/partial units:
Explicit non-guarantees:
Follow-up trigger:
```

The final wording is bounded by the evidence: "on baseline X, gates A–J passed; no failed/partial units remain within the stated scope." Do not use "perfect," "secure in general," "production-ready," or "exactly-once" without a separately proven claim.
