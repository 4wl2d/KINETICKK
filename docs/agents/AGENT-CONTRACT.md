# Pokeball Agent Contract

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Every `PKB-AR-*` rule is defined exactly once here. Runbooks use these IDs but do not change their meaning.

## Mandatory workflow

Before answering or editing, the agent:

1. obtains `READY` from the baseline gate;
2. names the task mode, scope, Ball/authority, selected profiles, and excluded guarantees;
3. reads the accepted overlay and records missing project decisions;
4. selects the applicable `PKB-AR-*` rules and canonical sources;
5. names the artifacts, tests, and evidence;
6. after a change, verifies the semantic diff and related protocol/law/example/checklist/glossary slices;
7. does not make a conformance or release claim broader than the gates passed.

## Governance

| Rule ID | Sole definition | Core source |
|---|---|---|
| `PKB-AR-GOV-001` | The agent must confirm the exact baseline before using the derived package. | package `BASELINE.md`; Core §0 |
| `PKB-AR-GOV-002` | The agent follows the precedence: an overlay only concretizes a permitted choice and does not override the Core or an accepted extension. | Core §§0, 21.6 |
| `PKB-AR-GOV-003` | Before making a decision, the agent explicitly names the scope, authority, profiles, guarantees, non-guarantees, and required evidence. | Core §§0.2, 12–13; PBA-39/41/42 |
| `PKB-AR-GOV-004` | The agent does not invent an extension or attribute an excluded guarantee to the Core without a separate specification/evidence. | Core §§0, 2.2, 21.6 |

## Boundary, state, and protocol

| Rule ID | Sole definition | Core source |
|---|---|---|
| `PKB-AR-BND-001` | A Ball boundary is justified by shared invariants, lifecycle, authority, and `StateKey`, not by a layer, screen, table, or fashion. | Core §§1, 3.1, 4; PBA-01/11 |
| `PKB-AR-BND-002` | Interaction, Nucleus, and Resources are logically separated, and the Nucleus receives no platform/I/O path. | Core §5; PBA-01–03 |
| `PKB-AR-PRT-001` | A protocol is closed, typed, and versioned for one version; owned and imported contracts are not mixed. | Core §6, §14.1; PBA-04 |
| `PKB-AR-PRT-002` | The current mutating cause is passed in one `Pulse`, context does not duplicate it, and a raw external mutation becomes an `Intent` after validation. | Core §§6.11, 8.1; PBA-05 |
| `PKB-AR-PRT-003` | A `Query` does not mutate state, has exactly one result payload, and returns the stamp of the authority snapshot actually read. | Core §§6.3, 8.10, 9.11; PBA-30 |
| `PKB-AR-STA-001` | One mutable semantic fact has one authority, one instance has one writer, and directly reading another authority's mutable state is prohibited. | Core §§7.2, 7.4, 7.6; PBA-11–13 |
| `PKB-AR-STA-002` | Sovereign, captured, replica, and ephemeral state are labeled and are not used as interchangeable truth; every value needed after the transition that produced it is retained as a bounded typed captured input/result reference with source correlation, version, verified provenance, and retention, or is explicitly and trustworthily reintroduced through a later `Pulse`/`DecisionContext`; ledger/outbox/history/ambient reads are prohibited. | Core §§7.1, 7.3, 7.5, 8.1, 10.4; PBA-05/14/26 |

## Identity and decision

| Rule ID | Sole definition | Core source |
|---|---|---|
| `PKB-AR-ID-001` | Semantic identity is separate from mechanical IDs, and async input is accepted only under an exact causal handle/revision/generation policy. | Core §§3.1–3.6, 9.1–9.2; PBA-15–17 |
| `PKB-AR-DEC-001` | A `Decision` is pure, deterministic, and bounded; `DecisionResult` has two exact variants: `Accepted(Decision)` and `Rejected(BusinessRejection)`; the Nucleus, not an adapter/runtime, creates a semantic action. | Core §§3.3–3.4, 6.7, 8.2–8.4; PBA-03/04/06 |
| `PKB-AR-DEC-002` | The complete state/output frame is accepted atomically, and no output is dispatched before acceptance. | Core §§8.5–8.7; PBA-07/08 |
| `PKB-AR-DEC-003` | Reentrant mutation is prohibited, and a pre-acceptance fault and post-acceptance delivery fault have different semantics. | Core §§8.4, 8.8; PBA-09/10 |

## Async and status

| Rule ID | Sole definition | Core source |
|---|---|---|
| `PKB-AR-ASY-001` | Every `Fact`, `ModuleResult`, or observation has trusted provenance and an exact causal match to a previously accepted source. | Core §§6.4–6.11, 9.1–9.2; PBA-18 |
| `PKB-AR-ASY-002` | ACK and business result are independent, and possible execution without proof remains a first-class unknown. | Core §§9.3–9.5; PBA-19/20 |
| `PKB-AR-ASY-003` | A retry preserves logical identity, has explicit idempotency/mismatch/horizon semantics, and has one primary owner per failure mode. | Core §§9.6, 9.9; PBA-21/22/24 |
| `PKB-AR-ASY-004` | Cancellation, deadline, and timer are modeled as typed race outcomes, not as an instantaneous local stop flag. | Core §§9.7–9.10; PBA-23 |
| `PKB-AR-ASY-005` | A post-commit mechanical delivery observation changes Sovereign State only through declared trusted ingress, while status losslessly retains independent lifecycle/cancellation facets and all bounded stopped handles. | Core §§6.11, 8.8, 9.11, 12.5, 16.3/16.13; PBA-05/10/30/42 |

## Composition and profiles

| Rule ID | Sole definition | Core source |
|---|---|---|
| `PKB-AR-CMP-001` | Every inter-Ball edge declares one dependency kind and is resolved by a specific Assembly route/version. | Core §§10.2, 10.7; PBA-25 |
| `PKB-AR-CMP-002` | A stateful multi-participant workflow has one coordinator owner, and a Flow does not become a wildcard mediator. | Core §§10.3–10.5; PBA-26/27 |
| `PKB-AR-CMP-003` | Protocol re-export is prohibited, graph/fan-out is bounded, and a multi-source aggregate is not called atomic without a mechanism. | Core §§10.6, 10.8–10.11; PBA-28–30 |
| `PKB-AR-CMP-004` | Foundation contains mechanical primitives, not a shared mutable domain model. | Core §14.7; PBA-43 |
| `PKB-AR-PRF-001` | Execution, state, isolation, and security profile dimensions are selected independently and in proportion to the task. | Core §§0.2, 12; PBA-39/40 |
| `PKB-AR-PRF-002` | Every guarantee names its boundary, mechanism, assumptions, evidence, and explicit non-guarantees. | Core §§12.4–12.9, 13.4; PBA-41/42 |

## Security and limits

| Rule ID | Sole definition | Core source |
|---|---|---|
| `PKB-AR-SEC-001` | Raw ingress and raw resource output pass through parsing/validation/provenance quarantine before reaching the Nucleus. | Core §§11.1–11.2; PBA-31 |
| `PKB-AR-SEC-002` | Actor, grant, capability, and dual execution gates are explicit, and ambient authority is prohibited. | Core §§11.3–11.5, 11.7; PBA-32–34 |
| `PKB-AR-SEC-003` | Safe sinks, secret containment, and a time-bounded, owned unsafe waiver are mandatory for risky paths. | Core §§11.6, 11.9–11.11; PBA-35–37 |
| `PKB-AR-LIM-001` | All nine mandatory limits in §8.3 are finite, explicit, and enforced without truncation or partial acceptance. | Core §8.3; PBA-38 |
| `PKB-AR-LIM-002` | Applicable delivery/concurrency/retention/response caps, backpressure, and economic quotas are declared separately. | Core §§10.9, 13.1–13.2; PBA-29/38 |

## Manifest and evidence

| Rule ID | Sole definition | Core source |
|---|---|---|
| `PKB-AR-MAN-001` | A manifest resolves every nonempty owned category and `Query -> result` exactly once; omission means an empty closed set. | Core §§14.1–14.3; PBA-04/25 |
| `PKB-AR-MAN-002` | An imported contract version matches the sole Assembly target route and is not redeclared by the caller. | Core §§10.7–10.8, 14.4; PBA-25/28 |
| `PKB-AR-TST-001` | Evidence covers transitions, races, faults, selected profiles, security, and declared claims, not only the happy path. | Core §§17.1–17.9, 18; PBA-41 |
| `PKB-AR-TST-002` | Catalog and Checkout are used as an illustrative crosswalk but create no new guarantees or project values. | Core §§15.10, 16.14, 20 |

## Fail-closed conditions

The agent does not fill a gap with its own best practice when the missing decision changes authority, protocol semantics, profile, guarantee, security principal, limit, retry owner, or retention. It records the missing overlay decision and asks the owner to accept it. A local implementation detail is permitted without escalation only when it does not change observable semantics and passes every applicable rule and gate.
