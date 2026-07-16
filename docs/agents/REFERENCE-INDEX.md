# Core Reference Index

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

All definitions and normative text are in the [canonical Core](../../spec/pokeball-architecture-core.md). This file is only an index.

## Sections 0–23

| § | Title | Primary package route |
|---:|---|---|
| 0 | Document status and scope | `BASELINE`, `AGENT-CONTRACT` governance |
| 1 | Definition of Pokeball | `DESIGN-RUNBOOK` |
| 2 | Goals and non-goals | `AGENT-CONTRACT` governance |
| 3 | Canonical model | `DESIGN-RUNBOOK` |
| 4 | Choosing a Ball boundary | `DESIGN-RUNBOOK` |
| 5 | Three logical zones | `DESIGN-RUNBOOK`, `SECURITY-LIMITS-RUNBOOK` |
| 6 | Protocol algebra | `DESIGN-RUNBOOK`, `ASYNC-STATUS-RUNBOOK` |
| 7 | State and authority | `DESIGN-RUNBOOK` |
| 8 | Decision and commit semantics | `DESIGN-RUNBOOK`, `TEST-AND-REVIEW-GATES` |
| 9 | Asynchrony, causality, and delivery semantics | `ASYNC-STATUS-RUNBOOK` |
| 10 | System composition | `COMPOSITION-PROFILES` |
| 11 | Security and privacy | `SECURITY-LIMITS-RUNBOOK` |
| 12 | Execution profiles | `COMPOSITION-PROFILES` |
| 13 | Limits, budgets, and performance | `SECURITY-LIMITS-RUNBOOK` |
| 14 | Minimal manifest and source-code organization | `MANIFEST-AND-ASSEMBLY` |
| 15 | End-to-end example I: catalog search | `EXAMPLE-CROSSWALK` |
| 16 | End-to-end example II: Checkout Flow | `EXAMPLE-CROSSWALK`, `ASYNC-STATUS-RUNBOOK` |
| 17 | Testing, review, and operational verification | `TEST-AND-REVIEW-GATES` |
| 18 | Practical checklist | `TEST-AND-REVIEW-GATES` |
| 19 | Anti-patterns | All runbooks as a negative review list |
| 20 | Canonical Pokeball laws | This index, `AGENT-CONTRACT` |
| 21 | Adoption strategy | `INSTALL`, `DESIGN-RUNBOOK` |
| 22 | Glossary | This index |
| 23 | Canonical statement | `AGENT-CONTRACT` overview |

## PBA-01–43

| Law | Title | Agent rules |
|---|---|---|
| `PBA-01` | Three-Zone Boundary | `PKB-AR-BND-001/002` |
| `PBA-02` | Polar Isolation | `PKB-AR-BND-002` |
| `PBA-03` | Pure Bounded Nucleus | `PKB-AR-BND-002`, `PKB-AR-DEC-001` |
| `PBA-04` | Closed Protocol | `PKB-AR-PRT-001`, `PKB-AR-DEC-001`, `PKB-AR-MAN-001` |
| `PBA-05` | Explicit Decision Inputs | `PKB-AR-PRT-002`, `PKB-AR-STA-002`, `PKB-AR-ASY-005` |
| `PBA-06` | Controlled Causality | `PKB-AR-DEC-001`, `PKB-AR-LIM-001` |
| `PBA-07` | Atomic Decision | `PKB-AR-DEC-002` |
| `PBA-08` | Commit Before Dispatch | `PKB-AR-DEC-002` |
| `PBA-09` | No Reentrant Transition | `PKB-AR-DEC-003` |
| `PBA-10` | Fault Atomicity | `PKB-AR-DEC-003`, `PKB-AR-ASY-005` |
| `PBA-11` | Single Semantic Authority | `PKB-AR-BND-001`, `PKB-AR-STA-001` |
| `PBA-12` | Single Writer | `PKB-AR-STA-001` |
| `PBA-13` | State Isolation | `PKB-AR-STA-001` |
| `PBA-14` | Explicit State Kind | `PKB-AR-STA-002` |
| `PBA-15` | Semantic Handle | `PKB-AR-ID-001` |
| `PBA-16` | Trusted Identifier Allocation | `PKB-AR-ID-001` |
| `PBA-17` | Revisioned Causality | `PKB-AR-ID-001`, `PKB-AR-ASY-001` |
| `PBA-18` | Provenance-Bound Result | `PKB-AR-ASY-001` |
| `PBA-19` | ACK/Result Separation | `PKB-AR-ASY-002/005` |
| `PBA-20` | First-Class Unknown | `PKB-AR-ASY-002` |
| `PBA-21` | Explicit Idempotency | `PKB-AR-ASY-003` |
| `PBA-22` | Stable Logical Retry | `PKB-AR-ASY-003` |
| `PBA-23` | Cancellation Is a Protocol | `PKB-AR-ASY-004` |
| `PBA-24` | Owned Retry Policy | `PKB-AR-ASY-003` |
| `PBA-25` | Declared Dependency | `PKB-AR-CMP-001`, `PKB-AR-MAN-001/002` |
| `PBA-26` | Workflow Sovereignty | `PKB-AR-STA-002`, `PKB-AR-CMP-002` |
| `PBA-27` | No Wildcard Mediator | `PKB-AR-CMP-002` |
| `PBA-28` | No Protocol Re-export | `PKB-AR-CMP-003`, `PKB-AR-MAN-002` |
| `PBA-29` | Bounded Composition | `PKB-AR-CMP-003`, `PKB-AR-LIM-002` |
| `PBA-30` | Honest Read Consistency | `PKB-AR-PRT-003`, `PKB-AR-CMP-003`, `PKB-AR-ASY-005` |
| `PBA-31` | Double Quarantine | `PKB-AR-SEC-001` |
| `PBA-32` | Capability-Sealed Effect | `PKB-AR-SEC-002` |
| `PBA-33` | Dual Gate | `PKB-AR-SEC-002` |
| `PBA-34` | No Ambient Authority | `PKB-AR-SEC-002` |
| `PBA-35` | Safe Sink | `PKB-AR-SEC-003` |
| `PBA-36` | Secret Containment | `PKB-AR-SEC-003` |
| `PBA-37` | Explicit Unsafe Escape Hatch | `PKB-AR-SEC-003` |
| `PBA-38` | Bounded Execution | `PKB-AR-LIM-001/002` |
| `PBA-39` | Profile-Proportional Mechanism | `PKB-AR-GOV-003`, `PKB-AR-PRF-001` |
| `PBA-40` | Zero Mandatory Runtime Tax | `PKB-AR-PRF-001` |
| `PBA-41` | Measured Claim | `PKB-AR-GOV-003`, `PKB-AR-PRF-002`, `PKB-AR-TST-001` |
| `PBA-42` | Honest Guarantee Scope | `PKB-AR-GOV-003`, `PKB-AR-PRF-002`, `PKB-AR-ASY-005` |
| `PBA-43` | Foundation Quarantine | `PKB-AR-CMP-004` |

## Glossary index

Definitions are read only from Core §22.

| Term | Primary route |
|---|---|
| Assembly | `MANIFEST-AND-ASSEMBLY` |
| AuthenticatedActorContext | `SECURITY-LIMITS-RUNBOOK` |
| Ball | `DESIGN-RUNBOOK` |
| BallInstance | `DESIGN-RUNBOOK`, overlay |
| BallType | `DESIGN-RUNBOOK` |
| BusinessRejection | `DESIGN-RUNBOOK` |
| BoundaryResponse | `DESIGN-RUNBOOK` |
| Capability | `SECURITY-LIMITS-RUNBOOK` |
| Captured Input | `DESIGN-RUNBOOK`, `COMPOSITION-PROFILES` |
| CausalToken | `ASYNC-STATUS-RUNBOOK` |
| CommitRevision | `DESIGN-RUNBOOK` |
| CommitId | `DESIGN-RUNBOOK` |
| Commit-before-dispatch | `DESIGN-RUNBOOK`, `ASYNC-STATUS-RUNBOOK` |
| CommittedStateSnapshot | `DESIGN-RUNBOOK` |
| ConsistencyStamp | `ASYNC-STATUS-RUNBOOK` |
| Decision | `DESIGN-RUNBOOK` |
| DecisionContext | `DESIGN-RUNBOOK` |
| ControlPulse | `ASYNC-STATUS-RUNBOOK` |
| DeclaredCommandDependency | `COMPOSITION-PROFILES` |
| DeclaredSignalDependency | `COMPOSITION-PROFILES` |
| Delivery Observation | `ASYNC-STATUS-RUNBOOK` |
| DispatchStopped | `ASYNC-STATUS-RUNBOOK` |
| Effect | `DESIGN-RUNBOOK` |
| EffectRequest | `DESIGN-RUNBOOK` |
| EventJournal | `COMPOSITION-PROFILES` |
| Fact | `ASYNC-STATUS-RUNBOOK` |
| Feature Ball | `DESIGN-RUNBOOK` |
| Flow Ball | `COMPOSITION-PROFILES` |
| Foundation Quarantine | `COMPOSITION-PROFILES` |
| Grant | `SECURITY-LIMITS-RUNBOOK` |
| Interaction Hemisphere | `DESIGN-RUNBOOK` |
| Intent | `DESIGN-RUNBOOK` |
| ModuleCommand | `MANIFEST-AND-ASSEMBLY` |
| ModuleCommandRequest | `MANIFEST-AND-ASSEMBLY` |
| ModuleResult | `ASYNC-STATUS-RUNBOOK` |
| Nucleus | `DESIGN-RUNBOOK` |
| OperationId | `ASYNC-STATUS-RUNBOOK` |
| Operation Status Authority | `ASYNC-STATUS-RUNBOOK`, overlay |
| AttemptId | `ASYNC-STATUS-RUNBOOK` |
| ObservedSignal | `COMPOSITION-PROFILES` |
| OutcomeUnknown | `ASYNC-STATUS-RUNBOOK` |
| OutputId | `DESIGN-RUNBOOK` |
| Projection | `DESIGN-RUNBOOK` |
| ProjectionOutput | `DESIGN-RUNBOOK` |
| Pulse | `DESIGN-RUNBOOK` |
| Query | `DESIGN-RUNBOOK`, `MANIFEST-AND-ASSEMBLY` |
| RequestId | `ASYNC-STATUS-RUNBOOK` |
| ReadContext | `DESIGN-RUNBOOK` |
| ReadDependency | `COMPOSITION-PROFILES` |
| ReadResult | `DESIGN-RUNBOOK` |
| Read Model Ball | `COMPOSITION-PROFILES` |
| Reply | `DESIGN-RUNBOOK` |
| ReplyOutput | `DESIGN-RUNBOOK` |
| Replica State | `DESIGN-RUNBOOK` |
| RetainedContinuation | `TEST-AND-REVIEW-GATES` |
| Resource Hemisphere | `DESIGN-RUNBOOK`, `SECURITY-LIMITS-RUNBOOK` |
| Safe Sink | `SECURITY-LIMITS-RUNBOOK` |
| SemanticHandle | `ASYNC-STATUS-RUNBOOK` |
| SemanticOutput | `DESIGN-RUNBOOK` |
| Signal | `COMPOSITION-PROFILES` |
| SignalPublication | `COMPOSITION-PROFILES` |
| SnapshotOutbox | `COMPOSITION-PROFILES`, `TEST-AND-REVIEW-GATES` |
| StorageTransactionId | `DESIGN-RUNBOOK` |
| Sovereign State | `DESIGN-RUNBOOK` |
| State Belt | `DESIGN-RUNBOOK` |
| StateKey | `DESIGN-RUNBOOK`, overlay |
| Structural Allocation | `COMPOSITION-PROFILES` |
| TimerRequest | `ASYNC-STATUS-RUNBOOK` |
| Workflow Sovereignty | `COMPOSITION-PROFILES` |
| Zero Mandatory Runtime Tax | `COMPOSITION-PROFILES` |
