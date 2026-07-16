# Agent Rule Traceability

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

The sole rule definitions are in [AGENT-CONTRACT.md](AGENT-CONTRACT.md). This matrix shows where each rule is applied and what evidence closes it.

| AgentRuleId | Definition anchor | Core / law | Primary runbook | Required artifact / evidence | Gate | Overlay field |
|---|---|---|---|---|---|---|
| `PKB-AR-GOV-001` | Contract / Governance | §0 | `BASELINE`, `INSTALL` | exact hash/bytes/version result + clean-layout license/notice route | `AP-GATE-02`, `AP-GATE-10`, `RG-10` | metadata.coreBaseline |
| `PKB-AR-GOV-002` | Contract / Governance | §§0,21.6 | `README`, `INSTALL` | precedence + accepted overlay/deviation | `AP-GATE-03`, `RG-01` | metadata.extensions; deviations |
| `PKB-AR-GOV-003` | Contract / Governance | §§0.2,12–13; PBA-39/41/42 | `TEST-AND-REVIEW-GATES` | scope/profile/claim/evidence card | `RG-01`, `RG-08`, `RG-10` | conformance; profiles |
| `PKB-AR-GOV-004` | Contract / Governance | §§0,2.2,21.6 | `INSTALL` | extension/non-claim record | `RG-01`, `RG-08` | extensions; nonClaims |
| `PKB-AR-BND-001` | Contract / Boundary | §§1,3.1,4; PBA-01/11 | `DESIGN-RUNBOOK` | boundary worksheet/authority map | `RG-01`, `RG-03` | balls; authorities |
| `PKB-AR-BND-002` | Contract / Boundary | §5; PBA-01–03 | `DESIGN-RUNBOOK` | import graph/no-I/O Nucleus tests | `RG-01`, `RG-04` | balls.zones |
| `PKB-AR-PRT-001` | Contract / Protocol | §6,§14.1; PBA-04 | `MANIFEST-AND-ASSEMBLY` | exhaustive types/owned surface inventory | `RG-02`, `AP-GATE-08` | balls.protocols |
| `PKB-AR-PRT-002` | Contract / Protocol | §§6.11,8.1; PBA-05 | `DESIGN-RUNBOOK` | Pulse/context/origin tests | `RG-02`, `RG-04`, `RG-05` | balls.ingress |
| `PKB-AR-PRT-003` | Contract / Protocol | §§6.3,8.10,9.11; PBA-30 | `ASYNC-STATUS-RUNBOOK` | Query mapping/read/stamp tests | `RG-02`, `RG-03`, `RG-05` | balls.queries; statusAuthorities |
| `PKB-AR-STA-001` | Contract / State | §§7.2,7.4,7.6; PBA-11–13 | `DESIGN-RUNBOOK` | mutable fact owner/writer/fencing map | `RG-03` | balls.state; authorities |
| `PKB-AR-STA-002` | Contract / State | §§7.1,7.3,7.5,8.1,10.4; PBA-05/14/26 | `DESIGN-RUNBOOK`, `ASYNC-STATUS-RUNBOOK` | state-kind inventory + cross-transition value-liveness/recovery trace | `RG-03`, `RG-04`, `RG-05`, `RG-08` | balls.stateKinds; balls.decisionValueLineage |
| `PKB-AR-ID-001` | Contract / Identity | §§3.1–3.6,9.1–9.2; PBA-15–17 | `ASYNC-STATUS-RUNBOOK` | identity lineage/stale tests | `RG-04`, `RG-05` | asyncPolicies.identity |
| `PKB-AR-DEC-001` | Contract / Decision | §§3.3–3.4,6.7,8.2–8.4; PBA-03/04/06 | `DESIGN-RUNBOOK` | exact DecisionResult sum + determinism/property/bound tests | `RG-02`, `RG-04`, `RG-09`, `AP-GATE-08` | balls.decision |
| `PKB-AR-DEC-002` | Contract / Decision | §§8.5–8.7; PBA-07/08 | `DESIGN-RUNBOOK` | atomic frame/commit-before-dispatch faults | `RG-04` | profiles.stateGuarantee |
| `PKB-AR-DEC-003` | Contract / Decision | §§8.4,8.8; PBA-09/10 | `TEST-AND-REVIEW-GATES` | reentrancy/pre-post fault tests | `RG-04`, `RG-08` | profiles.failureModel |
| `PKB-AR-ASY-001` | Contract / Async | §§6.4–6.11,9.1–9.2; PBA-18 | `ASYNC-STATUS-RUNBOOK` | provenance/exact causal match tests | `RG-05` | asyncPolicies.observations |
| `PKB-AR-ASY-002` | Contract / Async | §§9.3–9.5; PBA-19/20 | `ASYNC-STATUS-RUNBOOK` | ACK/result/unknown traces | `RG-05` | asyncPolicies.unknown |
| `PKB-AR-ASY-003` | Contract / Async | §§9.6,9.9; PBA-21/22/24 | `ASYNC-STATUS-RUNBOOK` | idempotency/retry-owner matrix | `RG-05`, `RG-09` | asyncPolicies.retryOwners |
| `PKB-AR-ASY-004` | Contract / Async | §§9.7–9.10; PBA-23 | `ASYNC-STATUS-RUNBOOK` | cancel/deadline/timer race tests | `RG-05` | asyncPolicies.cancellation |
| `PKB-AR-ASY-005` | Contract / Async | §§6.11,8.8,9.11,12.5,16.3/16.13; PBA-05/10/30/42 | `ASYNC-STATUS-RUNBOOK` | delivery Pulse, multi-stop, pending/retention tests | `RG-03`, `RG-05`, `RG-08`, `AP-GATE-08` | statusAuthorities; routes.delivery |
| `PKB-AR-CMP-001` | Contract / Composition | §§10.2,10.7; PBA-25 | `COMPOSITION-PROFILES` | edge inventory/Assembly routes | `RG-06` | routes |
| `PKB-AR-CMP-002` | Contract / Composition | §§10.3–10.5; PBA-26/27 | `COMPOSITION-PROFILES` | Flow/participant ownership + retained result-ref/compensation-target lineage | `RG-03`, `RG-05`, `RG-06` | balls.flowOwnership; balls.decisionValueLineage |
| `PKB-AR-CMP-003` | Contract / Composition | §§10.6,10.8–10.11; PBA-28–30 | `COMPOSITION-PROFILES` | graph/fan-out/read claim checks | `RG-06`, `RG-09` | routes; readModels |
| `PKB-AR-CMP-004` | Contract / Composition | §14.7; PBA-43 | `COMPOSITION-PROFILES` | foundation dependency scan | `RG-06`, `RG-10` | foundationPolicy |
| `PKB-AR-PRF-001` | Contract / Profiles | §§0.2,12; PBA-39/40 | `COMPOSITION-PROFILES` | selected dimension matrix | `RG-08` | profiles |
| `PKB-AR-PRF-002` | Contract / Profiles | §§12.4–12.9,13.4; PBA-41/42 | `COMPOSITION-PROFILES` | claim/mechanism/assumption/evidence/non-claim | `RG-08`, `AP-GATE-09` | guarantees |
| `PKB-AR-SEC-001` | Contract / Security | §§11.1–11.2; PBA-31 | `SECURITY-LIMITS-RUNBOOK` | ingress/resource quarantine tests | `RG-07` | security.boundaries |
| `PKB-AR-SEC-002` | Contract / Security | §§11.3–11.5,11.7; PBA-32–34 | `SECURITY-LIMITS-RUNBOOK` | current per-action grant issuance/reintroduction/revalidation + capability/dual-gate tests | `RG-04`, `RG-07`, `RG-08` | security.grants; capabilities; balls.decisionValueLineage |
| `PKB-AR-SEC-003` | Contract / Security | §§11.6,11.9–11.11; PBA-35–37 | `SECURITY-LIMITS-RUNBOOK` | sink/redaction/unsafe registry tests | `RG-07`, `AP-GATE-09` | security.sinks; unsafeWaivers |
| `PKB-AR-LIM-001` | Contract / Limits | §8.3; PBA-38 | `SECURITY-LIMITS-RUNBOOK` | nine manifest fields + N/N+1 | `RG-09` | limits.mandatory |
| `PKB-AR-LIM-002` | Contract / Limits | §§10.9,13.1–13.2; PBA-29/38 | `SECURITY-LIMITS-RUNBOOK` | applicable caps/backpressure/quota tests | `RG-09`, `AP-GATE-09` | limits.applicable |
| `PKB-AR-MAN-001` | Contract / Manifest | §§14.1–14.3; PBA-04/25 | `MANIFEST-AND-ASSEMBLY` | exact owned/query resolution | `RG-02`, `RG-06`, `AP-GATE-08` | artifacts.manifests |
| `PKB-AR-MAN-002` | Contract / Manifest | §§10.7–10.8,14.4; PBA-25/28 | `MANIFEST-AND-ASSEMBLY` | dependency/route version equality | `RG-06`, `AP-GATE-08` | artifacts.assembly |
| `PKB-AR-TST-001` | Contract / Evidence | §§17.1–17.9,18; PBA-41 | `TEST-AND-REVIEW-GATES` | selected full evidence matrix | `RG-01`, `RG-02`, `RG-03`, `RG-04`, `RG-05`, `RG-06`, `RG-07`, `RG-08`, `RG-09`, `RG-10` | evidence |
| `PKB-AR-TST-002` | Contract / Evidence | §§15.10,16.14,20 | `EXAMPLE-CROSSWALK` | example-to-law crosswalk/no copied defaults | `RG-10`, `AP-GATE-03` | evidence.exampleUse |

## Matrix invariants

- Every `PKB-AR-*` rule in the contract must have exactly one row.
- Every `PBA-01–43` law must be covered in `REFERENCE-INDEX.md`.
- An artifact/evidence may be absent only with a `not-applicable` reason in the accepted overlay.
- `AP-GATE-*` are package gates; `RG-*` are project/release gates from `TEST-AND-REVIEW-GATES.md`.
- A change to a Core source, rule meaning, artifact, or gate requires updating the matrix and repeating package validation.
