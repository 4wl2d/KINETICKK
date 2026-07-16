# Pokeball Architecture — Core Specification

**Version:** `1.0-core-draft`<br>
**Status:** canonical working document<br>
**Date:** 2026-07-14<br>
**Language:** English; exact protocol type and field names use Latin identifiers

---

## Contents

```text
0.  Document status and scope
1.  Definition of Pokeball
2.  Goals and non-goals
3.  Canonical model
4.  Choosing a Ball boundary
5.  Three logical zones
6.  Protocol algebra
7.  State and authority
8.  Decision and commit semantics
9.  Asynchrony, causality, and delivery semantics
10. System composition
11. Security and privacy
12. Execution profiles
13. Limits, budgets, and performance
14. Minimal manifest and source-code organization
15. End-to-end example I: catalog search
16. End-to-end example II: Checkout Flow
17. Testing, review, and operational verification
18. Practical checklist
19. Anti-patterns
20. Canonical Pokeball laws
21. Adoption strategy
22. Glossary
23. Canonical statement
```

Suggested first reading path:

```text
0–3   -> meaning and semantic foundation
4–10  -> Ball, state, protocol, and composition design
12    -> runtime profile selection
15–16 -> complete operational examples
18–20 -> checklist, anti-patterns, and canonical laws
```

Sections 11, 13, 14, 17, 21, and 22 serve as implementation and review references.

---

## 0. Document status and scope

This document is the sole normative text for understanding and implementing the Pokeball Architecture baseline. It describes the architectural core, the minimum executable semantics, and profile-specific extensions only to the extent required to design a real application.

This document is intentionally not a specification for a cloud platform, message broker, workflow engine, IAM system, disaster-recovery framework, or conformance-certification service.

The baseline scope includes:

- the `Ball` boundary and three logical zones;
- state ownership and authority scope;
- closed protocols of causes, decisions, and consequences;
- a pure bounded decision;
- atomic decision acceptance and the commit-before-dispatch rule;
- single-writer semantics;
- causality, idempotency, cancellation, and `OutcomeUnknown`;
- explicit inter-module composition;
- a minimal actor, grant, and capability model;
- finite resource limits;
- independent profile dimensions: execution, state durability, isolation, and security;
- two end-to-end examples and a practical checklist.

The following separate future specifications remain outside this document:

```text
Durable Runtime and Recovery
Distributed Delivery and Executors
Event Sourcing and Replay
Secure Isolation and Audit
Read Models and Subscriptions
Tooling, Schema and Conformance
Dynamic Extensions and Ownership Transfer
```

The absence of these extension specifications does not prevent use of the baseline architecture. It only prohibits attributing stronger guarantees to this document when those guarantees require a separate formal protocol.

### 0.1. Normative words

- **MUST** — a mandatory rule for the claimed profile.
- **MUST NOT** — a prohibited construction.
- **SHOULD** — a recommended rule; deviation requires a deliberate rationale.
- **MAY** — a permitted option.

### 0.2. Proportionality principle

A simple local `Ball` does not require a durable outbox, broker manifest, audit sink, transaction coordinator, or set of signed evidence artifacts. A mechanism appears only when the application claims the corresponding property.

> **The strength of the mechanism must be proportional to the strength of the guarantee.**

---

## 1. Definition of Pokeball

**Pokeball Architecture** is an architecture of statically or explicitly composed functional modules in which every module:

1. has an explicit application decision scope;
2. owns its own canonical state;
3. separates external input, pure decision-making, and external effects;
4. interacts through closed typed protocols;
5. executes within finite resource limits;
6. receives no implicit access to the state or authority of other modules.

The architectural unit is called a `Ball`.

Every `Ball` contains three logical zones:

```text
Interaction Hemisphere
        ↓ Intent / Query
Protocol Nucleus
        ↓ Effect / Command / Projection / Reply / Signal
Resource Hemisphere and explicit routes
```

The basic state-transition formula is:

```text
State + Pulse + DecisionContext
              ↓
           decide
              ↓
Decision = NextState + bounded semantic outputs
```

For a local resource cycle:

```text
External input
  -> Interaction: parse / normalize / authenticate / validate
  -> Intent
  -> Nucleus: decide
  -> commit Decision
  -> Effect
  -> Resource: execute through capability and safe sink
  -> validated Fact
  -> next Nucleus decision
```

Canonical statement:

> **Pokeball separates a validated cause, a local decision, atomic acceptance, and an observable consequence.**

---

## 2. Goals and non-goals

### 2.1. Goals

#### Local reasoning

A change inside a `Ball` must be understandable from its state, protocol, transition, and declared dependencies, without reading a global store or searching for hidden handlers.

#### Unambiguous state ownership

Every mutable semantic fact has one authority within a given scope. Copies are permitted only as projections, replicas, captured inputs, or materialized views with explicit provenance.

#### Controlled causality

An external action is the result of an explicit `Nucleus` decision, not an incidental call from a UI, controller, callback, or repository.

#### Bounded coupling

Inter-module relationships are declared, typed, bounded in fan-out and causal depth, and do not create a universal runtime graph.

#### Predictable cost

Inputs, outputs, state, queues, retries, fan-out, concurrency, and external responses have finite limits.

#### Zero mandatory runtime tax

A local `Inline` binding can compile to a direct call without a mandatory mediator, reflection, serialization, queue, thread hop, or structural allocation. This is a design target, not an unsupported universal guarantee.

#### Security by construction

The standard protocol makes it difficult to express raw SQL, arbitrary shell commands, arbitrary URLs, unrestricted filesystem access, or the transfer of secrets through ordinary projections. Actual security still requires correct adapters, credentials, policies, and deployment boundaries.

### 2.2. Non-goals

Pokeball does not claim that:

- every screen, endpoint, table, aggregate, or use case must be a separate `Ball`;
- event sourcing is mandatory;
- CQRS, a broker, an actor runtime, a DI framework, or a particular database is mandatory;
- a typed `Effect` is automatically safe;
- an in-process module is isolated from hostile native code;
- a distributed side effect executes exactly once;
- a Flow turns multiple systems into one ACID transaction;
- every timeout means that the action did not occur;
- every read model is a consistent snapshot;
- one linter can prove semantic ownership, least privilege, or the absence of ambient authority;
- zero allocation exists on every toolchain without a benchmark;
- unlimited architectural ceremony is acceptable merely because runtime overhead is small.

### 2.3. When the approach is especially useful

- a long-lived stateful application;
- a mobile or desktop product with offline operation and asynchronous callbacks;
- a modular monolith with multiple business capabilities;
- a backend with external side effects and retries;
- a workflow with cancellation, compensation, or reconciliation;
- security-sensitive resource access;
- a plugin host or component that requires an isolation profile;
- a system where ownership and change radius matter more than the convenience of a global store.

### 2.4. When a simplified Core is sufficient

A small prototype, script, stateless transform, or simple CRUD component without its own lifecycle does not require the full set of protocol surfaces. It is sufficient to preserve the essential properties:

```text
one state authority
explicit input validation
no Interaction -> Resources business shortcut
typed external operation
finite limits
no ambient global authority
```

A utility without state, protocol, or resource authority does not become a Ball merely for uniformity.

---

## 3. Canonical model

### 3.1. Ball type and instance

`BallType` defines the protocol and decision semantics. A `BallInstance` is a concrete state scope and single-writer authority.

```text
BallInstanceId {
    namespace
    ballType
    stateKey
}
```

- `namespace` prevents collisions between deployments, tenants, or realms;
- `ballType` identifies the protocol family;
- `stateKey` defines the local consistency and partition boundary.

For a local singleton Ball, `stateKey` may be the explicit value `singleton`. An optional identity that means both global and absent is not used.

### 3.2. State revision

Every accepted mutating input has a monotonic `CommitRevision` within the instance. The revision increases even when the semantic state bytes do not change but an input is accepted, operation state is updated, or a delivery or cancellation result is recorded.

```text
CommitRevision: UInt64 or wider monotonic value
```

A revision is not a wall-clock timestamp.

### 3.3. Canonical decision function

```text
decide(
    state: State,
    pulse: Pulse,
    context: DecisionContext
) -> DecisionResult<State>
```

```text
DecisionResult<State> =
    Accepted(Decision<State>)
  | Rejected(BusinessRejection)
```

```text
Decision<State> {
    nextState
    outputs: BoundedSequence<SemanticOutput>
}
```

A programming fault, crash, nontermination, or violated internal invariant is not a `BusinessRejection`. It is handled by the runtime fault policy and does not create a partial `Decision`.

### 3.4. Cause, decision, and consequence

```text
Cause:
    Intent | Fact | ModuleResult | ObservedSignal | ControlPulse

Decision:
    nextState + semantic outputs

Consequence:
    ProjectionOutput | ReplyOutput | EffectRequest
  | ModuleCommandRequest | SignalPublication | TimerRequest
```

`Projection`, `Reply`, `Effect`, `ModuleCommand`, `Signal`, and `TimerIntent` are semantic payload types. The `Nucleus` returns them only inside the canonical output envelopes defined in §6.12. Mechanical delivery records, transport attempts, storage rows, and tracing metadata are not business outputs of the `Nucleus`.

### 3.5. Semantic and mechanical identity

Identifiers fall into two classes.

**Semantic IDs** belong to the domain and may be stored in state:

```text
OperationId
OrderId
PaymentId
SearchSessionId
```

They must be known before `decide` and arrive as:

- a validated client-supplied ID, if the contract permits one;
- a deterministic value derived from existing semantic data;
- a trusted reserved ID from `DecisionContext`;
- an ID reserved by ingress or the runtime before evaluation.

The `Nucleus` MUST NOT read an ambient random generator.

**Mechanical IDs** belong to the runtime:

```text
CommitId
OutputId
AttemptId
StorageTransactionId
```

They may appear only after acceptance or commit and must not be required for a domain decision. Semantic state refers to an output through a stable `SemanticHandle`:

```text
SemanticHandle {
    operationId
    outputKind
    localOrdinalOrName
}
```

The runtime may store a mapping:

```text
SemanticHandle -> OutputId
```

but it does not rewrite business state merely to materialize a transport ID.

### 3.6. Identity hierarchy

The following identifiers are not interchangeable:

| Identity | What it identifies | When it appears | Reuse |
|---|---|---|---|
| `RequestId` | One transport or request attempt | At the Interaction boundary | New for each new attempt |
| `IdempotencyKey` | Repeated acceptance of one logical mutation | Before ingress acceptance | Preserved across client retries |
| `OperationId` | An accepted business operation | Reserved before `decide`, authoritative after acceptance | Stable through the operation lifecycle |
| `SemanticHandle` | A specific planned output or step within an operation | In a Nucleus decision | Stable across delivery retries |
| `CommitId` | An accepted runtime commit record | At acceptance or commit | Stable for that commit record |
| `OutputId` | A committed runtime output record | At acceptance or commit | Stable across redelivery |
| `AttemptId` | One dispatch or execution attempt | Before a specific attempt | New for each attempt |
| `StorageTransactionId` | The committed storage transaction that supported acceptance | After a successful storage commit | Unique to that transaction |

Typical lineage:

```text
RequestId attempt-1 ─┐
RequestId attempt-2 ─┼─ same IdempotencyKey
                     └─ OperationId
                          └─ SemanticHandle
                               └─ OutputId
                                    ├─ AttemptId 1
                                    └─ AttemptId 2
```

A new `RequestId` must not be used to bypass idempotency, and a new `OperationId` must not be created merely because a transport attempt was repeated.

---

## 4. Choosing a Ball boundary

A `Ball` is not automatically a screen, endpoint, table, repository, service, aggregate, or bounded context. Its boundary is chosen according to authority and invariant scope.

### 4.1. Parts should be combined into one Ball when they have

- invariants that must be accepted by one decision;
- one state key and one local consistency boundary;
- a shared lifecycle and recovery unit;
- one semantic owner;
- a closely aligned trust boundary;
- compatible scale and contention profiles;
- state size and transition complexity suitable for one instance.

### 4.2. Parts should be separated when they have

- independent terminal outcomes;
- different consistency or durability requirements;
- different privilege or trust boundaries;
- different partition keys or materially different load profiles;
- independent lifecycles;
- a need for separate crash containment;
- different semantic owners and change cadences.

### 4.3. Practical rule

> **A Ball is the smallest operationally feasible scope within which the required decision can be made by one authority without access to another Ball's mutable state.**

A boundary that is too small creates protocol chatter and artificial workflows. A boundary that is too large creates a God Nucleus, a hot key, and an enormous change radius.

### 4.4. Boundary worksheet

Before defining a `Ball`, record:

```text
name
ownedSemanticFacts
stateKey
strictInvariants
externalSourcesOfRecord
declaredDependencies
trustBoundary
lifecycleOwner
expectedStateSize
peakOpsPerKey
maximumTransitionCost
publicProtocolSurface
recoveryUnit
```

If a strict invariant cannot be enforced by the selected state key or storage mechanism, the boundary is wrong regardless of package-structure convenience.

### 4.5. What is not a Ball

A pure stateless helper, parser combinator, numeric library, formatter, or value-object package without its own state, lifecycle, protocol, and resource authority is ordinarily a utility, not a `Ball`.

---

## 5. Three logical zones

The zones are architectural roles. They do not require three processes, three classes, or three heap objects.

### 5.1. Interaction Hemisphere

Interaction adapts the external world to the semantic protocol and back.

It MUST:

- parse wire/platform input;
- reject malformed input;
- normalize only according to declared field rules;
- authenticate through a trusted boundary;
- validate ranges, sizes, and semantic shape;
- create a typed `Intent` or `Query`, or return a non-committed `BoundaryResponse`;
- transform a `BoundaryResponse`, `ReadResult`, `ProjectionOutput`, or `ReplyOutput` into context-safe output;
- apply output encoding and escaping appropriate to the specific channel.

It MUST NOT:

- mutate Sovereign State;
- make a business transition;
- create an `Effect` or `ModuleCommand`;
- call a Resource adapter directly for an application operation;
- treat a request field as proof of authentication;
- pass a raw framework object into the `Nucleus`.

Interaction may hold ephemeral UI or transport state: focus, scroll, animation, socket buffers, and request-parser state. If a value changes a business decision, it is no longer ephemeral and must enter the protocol, state, or context.

### 5.2. Protocol Nucleus

The `Nucleus` is the sole place for a local business decision.

It:

- owns the Sovereign State instance;
- accepts a closed `Pulse` and explicit `DecisionContext`;
- returns a bounded `Decision`;
- performs no I/O;
- reads no ambient clock, randomness, environment, locale, feature flags, or service locator;
- imports no platform SDK or HTTP, SQL, or filesystem drivers;
- does not mutate runtime ledgers directly;
- does not call another Ball's Nucleus.

The `Nucleus` may check business authorization from actor and context data and form the required grant constraints. Final enforcement at the target or resource remains a separate execution gate.

### 5.3. Resource Hemisphere

Resource executes its own `Ball`'s private `Effect` or provides an adapter to a declared external resource.

It MUST:

- hold the minimal capability;
- use a safe sink;
- obey timeout, cancellation, and response bounds;
- validate the external response;
- map exceptions and statuses into a closed `Fact`;
- return provenance and causal identity;
- make no new business decision.

Resource MUST NOT:

- mutate Sovereign State;
- call Interaction to make a decision;
- replace domain retry or fallback policy with a hidden unbounded loop;
- return a database entity, HTTP response, or SDK object to the `Nucleus`;
- use an unrestricted shared credential when least privilege is claimed.

### 5.4. Polar isolation

```text
Interaction --X--> Resources
Resources   --X--> Interaction
```

The permitted semantic path passes through the `Nucleus`. Mechanical infrastructure—a logger, allocator, tracing primitive, or bounded collection—may be used by both sides if it carries no mutable business meaning and creates no hidden communication path.

### 5.5. Permitted dependency graph

```text
interaction -> own external protocol
resources   -> own resource protocol
nucleus     -> own state + own protocols + mechanical foundation
assembly    -> public protocols + routes
runtime     -> Ball integration points, not business internals
```

Prohibited:

```text
interaction -> concrete resource adapter
resource adapter -> UI/controller
nucleus -> platform SDK
Ball A -> internals/state of Ball B
business code -> global service locator
```

---

## 6. Protocol algebra

### 6.1. Surfaces

Every `Ball` separates at least three protocol surfaces.

| Surface | Purpose | Primary types |
|---|---|---|
| External Interaction | User, HTTP, CLI, OS, UI | `Intent`, `Query`, `ReadResult`, `BoundaryResponse`, `Projection`, `Reply` |
| Private Resource | Owned adapters and executors | `Effect`, `Fact` |
| Integration | Other Balls and Flows | `ModuleCommand`, `ModuleResult`, `Signal`, public read contracts |

A transport envelope, serialization, authentication token, and broker metadata do not automatically become part of the semantic payload.

### 6.2. Intent

An `Intent` is a validated intent to initiate a mutation or operation.

```text
SearchRequested(query, pageSize)
CheckoutStarted(cartId, paymentMethodRef)
DocumentCloseRequested(documentId)
```

An `Intent` contains no raw HTTP request, JSON tree, mutable DTO, repository or service reference, or arbitrary map.

### 6.3. Query

A `Query` is a non-mutating request against committed local state or a separate declared read service.

```text
GetCatalogView
GetCheckoutStatus(operationId)
```

Local `read`:

```text
CommittedStateSnapshot<State> {
    ballInstanceId
    commitRevision
    stateSchemaVersion
    state: State
}

ReadContext {
    protocolVersion
    actorContext: AuthenticatedActorContext?
}

ConsistencyStamp {
    ballInstanceId
    commitRevision
    stateSchemaVersion
}

ReadResult<ResultPayload> {
    payload: ResultPayload
    consistencyStamp: ConsistencyStamp
}

read(
    snapshot: CommittedStateSnapshot<State>,
    query: Query,
    context: ReadContext
) -> ReadResult<ResultPayload>
```

For each `Query` in a given `protocolVersion`, the manifest MUST map exactly one statically known `ResultPayload` unambiguously. `ReadContext` contains only the trusted, bounded inputs of this canonical form; additional selectors are fields of the `Query` itself, not ambient observations. A `ConsistencyStamp` identifies the local committed snapshot from which the payload was built, but promises neither freshness after return nor multi-source atomicity.

A `ReadResult` is only a successful non-committed response: it is not a `SemanticOutput`, has no `SemanticHandle`, `sourceOrdinal`, or new `CommitRevision`, and is not subject to commit-before-dispatch. An unsuccessful boundary, validation, or admission path returns the existing `BoundaryResponse`, not an invented read-result variant. A local `read` does not mutate state, create an `Effect`, or hide external I/O. If a sensitive read requires an atomic audit record, model it as an explicit audited operation or a separate audited-read profile, not as a hidden side effect inside pure `read`.

### 6.4. Effect

An `Effect` is a private declarative operation of the Ball's own Resource Hemisphere.

```text
FindProducts(query, pageSize)
PersistSession(snapshot)
ReadCurrentTime
CaptureProviderPayment(...)
```

An `Effect` describes a permitted action, not a service object or function call.

### 6.5. Fact

A `Fact` is a validated, provenance-bound result of a previously accepted `EffectRequest` whose payload is an `Effect`.

```text
ProductsFound(handle, products)
ProductSearchFailed(handle, reason)
CurrentTimeObserved(handle, timestamp)
```

A Fact contains a causal reference that matches it to the committed `EffectRequest`. A push or subscription observation is a `Fact` only when the subscription or observation itself was created by a previously accepted `EffectRequest` and the observation returns its stable `SemanticHandle`. An unsolicited resource observation must not masquerade as a `Fact`.

### 6.6. Projection

A `Projection` is an immutable semantic view intended for external presentation or a local observer.

```text
SearchLoading(operationId)
SearchResults(items)
CheckoutReady(summary)
```

A Projection is not a database entity, mutable shared view model, or transport DTO.

### 6.7. BoundaryResponse and Reply

A `BoundaryResponse` is an addressed boundary or runtime response to a request that did not create an accepted `Decision`:

```text
BoundaryResponse =
    ValidationFailure
  | AdmissionFailure
  | DecisionRejected(BusinessRejection)
```

A `BoundaryResponse` is not a `SemanticOutput`, has no `SemanticHandle`, `sourceOrdinal`, or `CommitRevision`, and is not subject to commit-before-dispatch. Malformed input and authentication or validation failures are rejected before an `Intent` is created; Interaction encodes `Rejected(BusinessRejection)` as `DecisionRejected`, without turning it into a committed `Reply`.

A `Reply` is an addressed semantic response to a particular request or operation channel.

```text
RequestAccepted(operationId)
CheckoutCompleted(orderId)
```

A `Reply` differs from a `Projection`: a reply has requester and correlation semantics, whereas a projection describes an observable representation of state. A `Reply` exists only as the payload of an accepted `ReplyOutput`.

### 6.8. ModuleCommand

A `ModuleCommand` is an addressed public request for another `Ball` to perform a target-owned operation.

```text
Inventory.ReserveItems(...)
Payment.Capture(...)
Notification.SendReceipt(...)
```

A Command is not a private `Effect`. The target makes its own decision and returns a `ModuleResult`.

### 6.9. ModuleResult

A `ModuleResult` is the business outcome of a previously accepted `ModuleCommand`.

```text
InventoryReserved(reservationId)
InventoryReservationRejected(reason)
PaymentCaptureOutcomeUnknown(reference)
```

A transport ACK and a `ModuleResult` are different observations. An ACK may prove acceptance, but not business success.

### 6.10. Signal

A `Signal` is a semantic publication without a single required requester.

```text
SessionRevoked
OrderConfirmed
CatalogIndexUpdated
```

A Signal is not a command. The recipient must not interpret a publication as authority to perform a privileged action without a separate grant or command contract.

The recipient transforms a previously accepted `SignalPublication` into a causal input envelope:

```text
ObservedSignal {
    sourceBallInstanceId
    sourceCommitRevision
    semanticHandle
    sourceOrdinal
    signal: Signal
    issuerProvenance
}
```

An `ObservedSignal` is accepted only after its provenance and correspondence to a committed `SignalPublication` have been verified. Resource subscription observations use a causally bound `Fact`, not an `ObservedSignal`.

### 6.11. Pulse

```text
Pulse =
    Intent
  | Fact
  | ModuleResult
  | ObservedSignal
  | ControlPulse
```

A `ControlPulse` includes only declared lifecycle, timer, cancellation, or delivery observations. Arbitrary string-based system events are not used.

A `ControlPulse` is created only by a trusted runtime, resource, or route boundary after the declared origin and provenance have been verified. A raw customer, API, or UI request cannot become a `ControlPulse`: after Double Quarantine, an external request to start or cancel a semantic operation is an `Intent`. The result of target or resource cancellation remains a causally bound `Fact` or `ModuleResult`; a runtime cancellation observation may be a `ControlPulse` only if that owned category is explicitly declared.

If a post-commit mechanical runtime or route observation about dispatch or an ACK changes Sovereign State—for example, by moving a stored command step to `Dispatched`, recording acceptance or ambiguity, or recording a terminal delivery stop—the runtime MUST NOT write that state directly. It creates a declared typed `ControlPulse`, after which the ordinary single-writer `decide` applies the observation. An already typed business `Fact` or `ModuleResult` retains its own causal category and is not wrapped in a `ControlPulse`. A mechanical delivery Pulse cannot exist before source acceptance; the source commit itself does not prove dispatch.

### 6.12. SemanticOutput

```text
SemanticOutput =
    ProjectionOutput
  | ReplyOutput
  | EffectRequest
  | ModuleCommandRequest
  | SignalPublication
  | TimerRequest           # only if the Ball uses timers
```

All variants specialize a single envelope:

```text
SemanticOutputEnvelope<Payload> {
    semanticHandle: SemanticHandle
    sourceOrdinal: UInt32
    payload: Payload
}

ProjectionOutput     = SemanticOutputEnvelope<Projection>
ReplyOutput          = SemanticOutputEnvelope<Reply>
EffectRequest        = SemanticOutputEnvelope<Effect>
ModuleCommandRequest = SemanticOutputEnvelope<ModuleCommand>
SignalPublication    = SemanticOutputEnvelope<Signal>
TimerRequest         = SemanticOutputEnvelope<TimerIntent>
```

`sourceOrdinal` is a unique zero-based ordinal within ordered `Decision.outputs`: values run from `0..outputs.size-1` and are preserved through materialization and redelivery. These envelope names and fields are canonical; unspecified aliases are not used.

### 6.13. Error classes

Errors are separated into:

```text
BusinessRejection       # intent is valid but prohibited by state/policy
ValidationFailure       # Interaction does not create an Intent
AdmissionFailure        # runtime capacity unavailable; Decision not accepted
ResourceFailure         # external operation failed with known outcome
Cancelled               # cancellation outcome is known
TimedOut                 # local wait/deadline elapsed; action outcome may differ
OutcomeUnknown          # action may have happened; reconciliation required
ProgrammingFault        # bug, invariant violation, trap, nontermination
```

`ValidationFailure`, `AdmissionFailure`, and `BusinessRejection` may be encoded in a `BoundaryResponse`, but do not become a `SemanticOutput` and receive no commit identity.

`AdmissionFailure` has closed profile-specific reasons; the Core reason `CausalBudgetExceeded(scope, limit)` means that the Decision was not accepted because the full causal budget could not be reserved. It is not a business rejection and does not permit the budget to be reset on retry or resume.

`TimedOut` must not automatically become `ResourceFailure`. After dispatch, a timeout often means `OutcomeUnknown`.

### 6.14. Protocol closure

For a given protocol version, the set of variants must be statically known and handled exhaustively. Generic `Message`, `Any`, `Map<String, Any>`, and string-based handler discovery are not a canonical protocol.

---

## 7. State and authority

### 7.1. State kinds

| Kind | Owner | Meaning |
|---|---|---|
| `SovereignState` | Nucleus of a specific Ball | Canonical mutable semantic facts |
| `EphemeralState` | Interaction/transport | UI and transport mechanics |
| `ReplicaState` | Resource/read adapter | Cache or copy of an external source with provenance |
| `CapturedInput` | Flow/operation owner | Immutable versioned snapshot for decision and recovery |
| `ReadModelState` | Read Model Ball | Derived query-oriented state |
| `Projection` | No one | Immutable output, not an authority |
| `RuntimeState` | Runtime | Mailbox, claims, attempts, breaker, tracing |

### 7.2. One mutable fact—one authority

Normative rule:

> **Within one overlapping semantic scope, there cannot be two independent writers that each consider their own value canonical.**

The scope may include:

```text
semanticId
namespace / tenant / realm
stateKey or key range
region or jurisdiction
validity interval
```

An external provider or database may be the source of record. In that case, the local `Ball` owns decision state, operation state, or a replica, but does not pretend to own the external canonical fact.

### 7.3. State Belt

The `State Belt` is the logical ownership map of all Balls:

```text
StateBelt {
    AuthBall owns auth.session
    CatalogBall owns catalog.searchSession
    CartBall owns cart.contents
    CheckoutFlow owns checkout.operation
}
```

The State Belt IS NOT a mandatory global store, singleton API, or object available to every module. It is an architectural ownership model and, when needed, a generated registry.

### 7.4. Prohibition on direct cross-cell reads

One Ball's `Nucleus` MUST NOT read another Ball's mutable state through a memory reference, global-store selector, or shared ORM session.

Permitted ways to obtain another Ball's data:

- public read contract;
- versioned immutable snapshot;
- `ModuleResult`;
- observed signal/event;
- Read Model;
- target-side validation/reservation.

The view layer may combine multiple projections for presentation, provided that the combination makes no cross-authority business decision and is not claimed to be a consistent snapshot without a separate mechanism.

### 7.5. Captured input

A Flow or long-running operation may retain only the required fields:

```text
CapturedInput {
    sourceCorrelation
    sourceProtocolOrSchemaVersion
    verifiedProvenance
    capturedAt
    purpose
    fields
    expiresAtOrDeletionTrigger
}
```

The same rule applies to a value from an earlier `Pulse` or `DecisionContext` that a later Decision needs: it either becomes a bounded typed captured or result value in committed State with correlation, version, provenance, and retention, or is explicitly reintroduced by a new trusted `Pulse` or `DecisionContext`. Inbox, outbox, runtime, or participant history is not a decision-readable substitute. Captured input does not become a second mutable authority. Before a strict action, the target may require an expected version, reservation, or current revalidation.

### 7.6. Single writer

At any moment, one `BallInstance` has one logical writer. The Inline profile provides this through call discipline; a concurrent profile through a single-writer loop; and a movable durable profile through a storage-enforced ownership epoch or fence.

A lease or process-local mutex is insufficient if two runtimes can independently consider themselves the owner and write to the same authoritative store.

### 7.7. Commutative updates under single-writer acceptance

A Ball may declare a commutative or CRDT-like merge contract for concurrent ingress or independently prepared update values with proven properties:

```text
associative
commutative
idempotent or duplicate-aware
deterministic conflict handling
bounded metadata
```

Such a contract changes conflict and merge semantics but does not remove single-writer acceptance: one logical writer serializes the merge and assigns the `CommitRevision`. Two independent writers concurrently accepting a mutation of the same `BallInstance` are outside Core even when the algebraic properties hold. A genuine multi-writer profile requires a separate specification; without one, §7.6 and PBA-12 always apply.

---

## 8. Decision and commit semantics

### 8.1. DecisionContext

`pulse` in canonical `decide(state, pulse, context)` is the current cause of the transition. `DecisionContext` contains contextual observations that affect interpretation of that cause but are not themselves the current cause:

```text
DecisionContext {
    actorContext?
    authorizationSnapshot?
    configurationSnapshot
    featurePolicySnapshot?
    trustedTimeObservation?
    reservedSemanticIds[]
    deterministicSeed?
    semanticLimits
    artifactVersion
    issuedAt
    notBefore?
    expiresAt?
    contextGeneration
    contextDigest?
}
```

All decision-relevant data appears explicitly in committed `State`, the current `Pulse`, or `DecisionContext`. A value from an earlier Pulse or Context that is needed later is retained in State according to §7.5 or reintroduced through a new declared trusted input; hidden history or ledger reads are prohibited. The current `Pulse` is not duplicated in context; conflicting copies make the input invalid. Actor, configuration, policy, trusted time, reserved IDs, and semantic limits are contextual observations and belong in the versioned context.

`DecisionContext` need not be cryptographically signed in a local trusted process. For a cross-process or hostile boundary, the profile defines issuer and authenticity requirements.

Runtime capacity—CPU, mailbox occupancy, and disk pressure—must not change a business choice invisibly. It belongs to admission and may reject the entire `Decision` before acceptance.

### 8.2. Determinism

For the same canonical state, pulse, valid context, and transition artifact version, `decide` must return a semantically equal result.

Sources of nondeterminism—a clock, randomness, locale, time-zone data, configuration, unordered iteration, or an external query result—must be:

- excluded from the `Nucleus`;
- supplied as an explicit observation or context;
- or fixed by an implementation contract so that the semantic result does not depend on a platform accident.

Full bit-exact cross-language replay is not a Core guarantee.

### 8.3. Bounded decision

Every Ball declares finite limits for at least:

```text
maxInputBytes
maxStateBytes
maxCollectionItems
maxOutputsPerDecision
maxEffectsPerDecision
maxCommandsPerDecision
maxCausalDepth
maxRetriesPerOperation
maxTransitionSteps
```

The names and units are canonical:

| Limit | Scope and unit |
|---|---|
| `maxInputBytes` | Maximum size of one normalized `Pulse` together with its boundary metadata, in bytes. |
| `maxStateBytes` | Maximum size of the retained canonical state of one `BallInstance`, in bytes. |
| `maxCollectionItems` | Maximum number of elements in each individual bounded collection, in items; cumulative byte limits still apply. |
| `maxOutputsPerDecision` | Maximum number of elements in the complete `Decision.outputs`, in envelopes per Decision. |
| `maxEffectsPerDecision` | Maximum number of `EffectRequest` elements in `Decision.outputs`, in envelopes per Decision. |
| `maxCommandsPerDecision` | Maximum number of `ModuleCommandRequest` elements in `Decision.outputs`, in envelopes per Decision. |
| `maxCausalDepth` | Maximum number of mutating Decision hops in one total causal scope, including the root Decision. |
| `maxRetriesPerOperation` | Maximum number of repeated attempts that this Ball's policy permits for one logical operation beyond the initial attempt; the one-primary-retry-owner rule in §9.9 applies to each failure mode. |
| `maxTransitionSteps` | Maximum number of deterministic work units in one `decide`; a concrete binding must define and test its step meter. |

Core defines no implicit defaults: omission of any field above makes the manifest incomplete. `0` is permitted for `maxEffectsPerDecision`, `maxCommandsPerDecision`, or `maxRetriesPerOperation` and prohibits the corresponding output or retry; the other mandatory limits must be positive. An `unbounded` marker, an omitted field, and an implicit `not applicable` are prohibited. Delivery attempts, retention, concurrency, and other profile-specific caps are declared separately in the applicable profile.

Overflow MUST NOT cause truncation, partial state, or dispatch of a subset of non-drop-eligible outputs. The Decision is accepted in full or rejected in full.

### 8.4. Run-to-completion

One mutating transition runs to acceptance or rejection. Reentrant transitions are prohibited.

The total causal budget is tied to the root operation or another explicit causal scope. It includes at least the remaining `maxCausalDepth`, accounts for bounded output fan-out, and is not reset by yield, resume, transport retry, or a hop between Balls.

Before accepting a Decision, the runtime reserves depth in the same total causal budget and bounded completion slots for every output that may complete synchronously. If a full reservation is unavailable, the Decision is not accepted and a typed `AdmissionFailure(CausalBudgetExceeded)` is returned; no subset of outputs is dispatched. An already accepted synchronous completion is never dropped because the next execution quantum is exhausted.

If an Inline executor completes an `EffectRequest` synchronously, the causally bound `Fact` is placed in a pre-reserved bounded local deque only after acceptance of the current Decision:

```text
while deque not empty:
    if executionQuantum exhausted:
        return RetainedContinuation(deque, totalCausalBudget)
    pulse = pop_front()
    decision = decide(committedState, pulse, context.withRemainingCausalBudget)
    preflightAndReserve(decision, totalCausalBudget)
    acceptedFrame = accept(decision)
    dispatch acceptedFrame.outputs
    enqueue synchronous completions into reserved slots
```

A `RetainedContinuation` has one owner, bounded capacity, and a resume and status policy; resume continues the same total causal budget. It may be caller-owned or use fixed storage and does not impose a mailbox or queue on a Ball that has no synchronous causal chain. When the total limit is reached, previously accepted causes and outputs are not rolled back and the budget is not reset: the current Decision with a new over-budget output is not accepted; the current `Pulse` remains in a retained or terminal state according to the declared policy, and the runtime returns a typed admission or status outcome.

### 8.5. Atomic Decision acceptance

An observer must not see `nextState` without that Decision's required source outputs and must not see an output before the accepted state.

Source acceptance materializes one immutable frame:

```text
AcceptedDecisionFrame<State> {
    commitRevision
    nextState
    outputs: BoundedSequence<SemanticOutput>
}
```

For `Transient`:

1. validate bounds and admission, and pre-reserve capacity to retain or hand off the complete output batch;
2. materialize the complete `AcceptedDecisionFrame` without visible external changes;
3. make the frame current through one atomic publication—this is acceptance and the only point at which state becomes visible;
4. dispatch only from the retained accepted frame after publication.

State is not published through a separate pointer before its outputs. A queue is not mandatory: the frame and reservation may be caller-owned or fixed. While a Transient instance remains available after acceptance, the runtime does not lose the undelivered part of the batch; a process crash may lose the entire transient state and frame according to the profile contract.

For `SnapshotOutbox` or `EventJournal`:

1. validate bounds, expected revision, and route availability;
2. record state or events and source delivery records in one authoritative transaction;
3. permit dispatch only after durable success.

### 8.6. Commit-before-dispatch

> **No `SemanticOutput`—including `ProjectionOutput`, `ReplyOutput`, `EffectRequest`, `ModuleCommandRequest`, `SignalPublication`, and `TimerRequest`—is dispatched before successful acceptance of its Decision.**

Otherwise, a crash between dispatch and the state commit would create an external consequence without a recorded cause.

### 8.7. Preflight and admission

Preflight may check:

- output bounds;
- route or executor availability;
- capability shape;
- storage capacity;
- expected revision;
- runtime quotas.

Before Transient acceptance, preflight MUST reserve the ability to retain the complete output batch and, for synchronous completion, the corresponding bounded slots and total causal budget. A reservation is not dispatch and does not make an output visible.

Preflight MUST NOT:

- change the amount, actor, target, or operation kind;
- add a business fallback absent from the Decision;
- remove a required output to fit capacity;
- replace a business rejection with an admission failure.

### 8.8. Fault atomicity

Before acceptance, upon a trap, programming fault, violated invariant, allocation failure, or detected nontermination:

- partial state is not published;
- outputs are not dispatched;
- the fault is recorded operationally;
- the instance may be restarted, failed, or quarantined according to runtime policy.

After acceptance, the state and complete source output batch are already accepted facts and are not rolled back because of a dispatch or worker fault. Such a fault:

- does not revoke an output that has already been dispatched;
- retains an undelivered output in delivery state for as long as the selected state and output profile promises;
- creates a typed delivery observation, `OutcomeUnknown`, or terminal `DispatchStopped` according to the boundary contract;
- may move the instance to `Failed` or `Quarantined`, but does not turn an accepted Decision into partial acceptance.

For `Transient`, a process crash may lose the entire frame and pending work; this is a profile limit, not permission to continue serving visible state after partial loss of the batch.

### 8.9. Snapshot and event modes

Core permits two different persistence contracts.

Snapshot:

```text
decideSnapshot(State, Pulse, Context)
    -> Accepted(SnapshotDecision { nextState, outputs })
     | Rejected(BusinessRejection)
```

Event mode:

```text
decideEvents(State, Pulse, Context)
    -> Accepted(EventDecision)
     | Rejected(BusinessRejection)

EventDecision =
    EventMutation { events: NonEmptySequence, outputs }
  | NoDomainChange { outputs }
```

```text
evolve(State, DomainEvent) -> State
```

Event mode does not return an independent `nextState`. Every `Accepted(EventDecision)`, including `NoDomainChange` with an empty output batch, creates a durable commit envelope:

```text
AcceptedEventCommit {
    commitRevision
    acceptedInputMarker
    idempotencyMarker?
    operationStatusChanges
    decision: EventDecision
}
```

One authoritative transaction records the envelope, the event batch (which may be empty only for `NoDomainChange`), source output records materialized from `decision.outputs`, accepted-input and idempotency metadata, and operation-status changes. `CommitRevision` increases for every Accepted result; the commit envelope is not a synthetic domain event. `Rejected` creates no domain event, source output, or new CommitRevision. Full replay, upcasting, and migration are defined by a separate extension specification.

### 8.10. Local read

```text
read(
    CommittedStateSnapshot<State>,
    Query,
    ReadContext
) -> ReadResult<ResultPayload>
```

Canonical `CommittedStateSnapshot`, `ReadContext`, `ReadResult`, and `ConsistencyStamp` are defined in §6.3. Read operates on an immutable committed snapshot; the manifest for the selected `protocolVersion` unambiguously resolves `Query -> ResultPayload`. A successful response returns the same `BallInstanceId`, `CommitRevision`, and `stateSchemaVersion` from which the payload was built. Read does not mutate state or create a `Decision`, accepted-input marker, new `CommitRevision`, `SemanticHandle`, or `SemanticOutput`.

For a separate Read Model Ball, the stamp identifies its own authority and commit position. Multi-source source positions and stronger snapshot guarantees require a declared read contract or profile; a local stamp alone does not promise them.

### 8.11. Minimal lifecycle

The runtime lifecycle must not masquerade as arbitrary business messages. The minimal operational model is:

```text
Uninitialized -> Initializing -> Ready -> Draining -> Stopped
                              \-> Failed | Quarantined
```

- `Ready` means that the current state and required runtime records are available to accept inputs.
- `Draining` prohibits new logical operations but may accept completion, cancellation, and status inputs for already accepted operations.
- `Failed` means a runtime failure, not a business rejection.
- `Quarantined` prohibits further mutations until an explicit repair or recovery action.

A domain-visible lifecycle reaction exists only as a declared typed `ControlPulse`. Loss of a process or lease alone does not give a stale owner the right to commit a domain decision. Complete recovery and ownership-reacquisition transitions belong to a durable runtime extension.

---

## 9. Asynchrony, causality, and delivery semantics

### 9.1. Causal identity

Every asynchronous output has a semantic cause:

```text
CausalToken {
    operationId?
    semanticHandle
    sourceBallInstanceId
    sourceCommitRevision
    sourceOrdinal
    operationGeneration?
    causalDepth
    causalBudgetScope
}
```

`Fact` and `ModuleResult` return the token or an equivalent correlation identity. Every new semantic hop increments `causalDepth`; yield, continuation, and transport retry preserve `causalBudgetScope` and do not reset the total budget. A transport retry changes attempt metadata, not the logical operation or semantic handle.

### 9.2. Revisioned causality

A late result is not applied merely because it is valid against the schema.

Example:

```text
Search A: operationGeneration = 7, query = "phone"
Search B: operationGeneration = 8, query = "laptop"

Result A arrives after Result B.
```

The `Nucleus` applies an explicit policy:

```text
AcceptCurrentGeneration
IgnoreStale
MergeByDeclaredAlgebra
StoreAsHistoricalObservation
TriggerResync
```

The scheduler's incidental completion order is not a conflict policy.

### 9.3. Independent dimensions of operation state

An asynchronous `ModuleCommandRequest` or `EffectRequest` must not use one flat state enum that mixes transport and business semantics. At minimum, distinguish:

```text
Dispatch facet:
    NotDispatched | Dispatched | DispatchStopped

Acceptance facet:
    NotAccepted | Accepted | RejectedBeforeAcceptance | AcceptanceUnknown

Business outcome facet:
    NotExpected | Pending | Succeeded | Rejected | Failed | Cancelled | OutcomeUnknown

Cancellation facet:
    NotRequested
  | Requested
  | CancellationAcceptedBeforeStart
  | CancellationAcceptedInProgress
  | CancellationTooLate
  | CancellationRejected
  | CancellationUnknown
```

An operation may simultaneously be in a state such as:

```text
dispatch = Dispatched
acceptance = AcceptanceUnknown
outcome = Pending
cancellation = Requested
```

This is a normal distributed race, not a modeling error.

Cross-facet invariants:

- `NotDispatched` requires `NotAccepted` and cannot have a target-produced terminal business outcome.
- `RejectedBeforeAcceptance` means that the target operation was not accepted; the business outcome remains `NotExpected`, and delivery rejection is not encoded as `Failed`.
- A verified `Fact` or `ModuleResult` with accepted-operation provenance first sets `acceptance = Accepted`, then the terminal `outcome`; after such proof, `AcceptanceUnknown + Succeeded|Rejected|Failed|Cancelled` is invalid.
- `OutcomeUnknown` may coexist with `Accepted` or `AcceptanceUnknown` until reconciliation proves a terminal result.
- `CancellationAcceptedInProgress` is not a terminal business outcome; `CancellationTooLate`, `CancellationRejected`, and `CancellationUnknown` do not erase a legitimate result.
- `DispatchStopped` is a terminal state of the current delivery policy, not a `BusinessRejection` or proof of target non-execution.

### 9.4. ACK and business result

- An ACK answers: **was the declared acceptance point reached?**
- A `Fact` or `ModuleResult` answers: **what was the business outcome?**
- A terminal delivery observation answers: **can the current delivery policy continue delivery?**

A result may arrive before a separate ACK if it contains verifiable proof of acceptance. In that case, one serialized transition applies the proof as `acceptance = Accepted` together with the result outcome; the state `AcceptanceUnknown + proven terminal outcome` is not retained. An ACK without a result is permitted if the target accepted the work but has not yet completed it.

### 9.5. OutcomeUnknown

If a request may have left the process and the target or provider may have accepted it, the absence of a response does not prove non-execution.

```text
Timeout after possible send
    -> AcceptanceUnknown or OutcomeUnknown
    -> status query / reconciliation / manual decision
```

A blind retry is permitted only when:

- the target or provider guarantees idempotency on the same key;
- the duplicate horizon covers the retry window;
- the retry preserves semantic identity;
- policy explicitly permits the retry.

### 9.6. Idempotency

Idempotency is a contract, not a general aspiration.

For mutation ingress:

```text
IdempotencyScope + IdempotencyKey + IntentFingerprint
```

- same key + same fingerprint returns the existing operation and acceptance;
- same key + different fingerprint returns a conflict;
- creation of the idempotency record and operation state must be atomic in a durable state profile;
- the retention horizon must cover the declared legal retry horizon.

The fingerprint includes business-semantic fields and stable actor and target scope, but excludes trace ID, retry count, socket address, and reply channel.

For `ModuleCommandRequest` and `EffectRequest`, the idempotency key is normally derived from `OperationId + SemanticHandle`. A new transport attempt does not create a new logical operation.

Pokeball does not use the term `exactly once` without identifying the exact authority and failure model.

### 9.7. Cancellation

Cancellation is a new cause, not the erasure of a past fact.

```text
CancellationRequested(targetHandle, generation, reason)
```

After Interaction validation, an external user request to cancel a semantic operation is an `Intent`. An outgoing cancellation to a resource or another Ball is encoded as a typed `Effect` or `ModuleCommand`, respectively, and its outcome returns as a causally bound `Fact` or `ModuleResult`. A `ControlPulse` is not an external-ingress bypass.

The target or resource returns one of these outcomes:

```text
CancellationAcceptedBeforeStart
CancellationAcceptedInProgress
CancellationTooLate
CancellationRejected
CancellationUnknown
```

These are the same canonical variants as in the `Cancellation facet` in §9.3. If a result and cancellation race, both observations are accepted and serialized by the state machine. A cancellation request does not authorize ignoring a legitimate late result.

If two matching observations prove mutually exclusive terminal business outcomes for one logical operation, arrival order does not select the “true” outcome, and the second proof does not overwrite the already accepted terminal state. An explicit conflict transition is required: the second input is classified as an invariant or provenance fault before acceptance of its `Decision`; no state or outputs are accepted for it, and the previously accepted terminal frame is preserved under §8.8. The rule is symmetric for `terminal cancellation -> contradictory result` and `terminal result -> contradictory cancellation`; an exact duplicate of the same proof is handled idempotently.

### 9.8. Deadlines and timeout

Distinguish:

- **business deadline**—the point after which a result loses its domain meaning;
- **transport timeout**—how long a caller waits for one attempt;
- **runtime execution limit**—how much CPU or wall time a resource may consume;
- **reconciliation horizon**—how long an operation remains potentially executed.

A hop may shorten a deadline but may not expand the authority of the original request.

The Nucleus uses a trusted time observation, not an ambient clock. A local scheduler may use a monotonic clock mechanically.

### 9.9. Retry ownership

Retry layers must be declared separately:

```text
semantic retry      # new decision under business policy
transport retry     # repeat of the same delivery
executor retry      # repeat of execution before/after acceptance
SDK retry           # low-level retry by the client library
reconciliation      # status/check operation after unknown outcome
```

One failure mode must have one primary retry owner. Other retries are either disabled or bounded and proven semantically transparent. Otherwise, `2 × 3 × 4` attempts across layers become 24 external calls.

### 9.10. Timers

A timer is an external observation, not a clock read inside the Nucleus.

```text
TimerIntent(schedule/cancel, semanticTimerId, generation, deadline)
TimerFired(semanticTimerId, generation, firingId, scheduledAt, observedAt)
```

Generation protects against an old firing after rescheduling. A durable replicated timer race protocol belongs to a separate runtime extension; Core requires only stable identity, deduplication, and an explicit late policy.

### 9.11. Operation status

A durable mutation or long-running workflow SHOULD provide an authenticated status query independent of the live reply channel:

```text
GetOperationStatus(OperationId)
```

Status must distinguish at least:

```text
NotFound
Accepted
InProgress
Completed
RejectedBeforeAcceptance
Rejected
Failed
Cancelled
OutcomeUnknown
DispatchStopped(
    stopped: one-or-more bounded records {
        semanticHandle,
        reason,
        attempts,
        lastObservation
    }
)
ExpiredFromStatusRetention
```

`RejectedBeforeAcceptance` reflects the acceptance facet, while `Rejected`, `Failed`, and `Cancelled` are distinct terminal business outcomes; they are not collapsed into one error status. `DispatchStopped` reports only exhaustion of the current delivery policy and remains separate from those outcomes and from `OutcomeUnknown`. Reply delivery is not the sole source of a terminal outcome.

A status query reads one declared committed **operation status authority**: a status ledger, `ReadModelState`, or other query-oriented state with its own revision. It losslessly projects accepted and rejected operation records, workflow state, and trusted runtime delivery observations, but does not become a second command authority for the original business facts. A `ConsistencyStamp` refers to a snapshot of this status authority; if the authority is materialized from multiple sources, the stamp does not promise their atomic freshness.

`NotFound` is permitted only when the snapshot read covers the request's authenticated namespace and contains neither an operation record nor a retention marker. When retention expires, a known record is first atomically replaced by an `ExpiredFromStatusRetention` marker; while the marker is retained, the query returns exactly that variant. After the declared marker horizon expires, absence once again means only `NotFound`.

Every `DispatchStopped` record comes from a trusted terminal delivery observation, contains the complete `SemanticHandle`, `reason`, `attempts`, and `lastObservation`, and does not overwrite the business lifecycle, cancellation, or a known terminal outcome. If one operation retains several independently dispatched outputs or steps, status stores zero-to-bounded-many unique stopped records rather than selecting one. The collection has a declared finite bound, deterministic canonical order, and idempotent merge of identical evidence by handle; a different handle never displaces an existing record. A domain-specific status payload must represent the entire minimum above as a closed, lossless model, including compatible independent facets.

### 9.12. Ordering

Ordering guarantees are local and explicit:

- Pulses for one instance are serialized by its single-writer mechanism.
- Outputs of one Decision have a `sourceOrdinal`.
- Synchronous completion is processed only after acceptance of the current Decision.
- Parallel Effects and Commands may complete in any order.
- There is no global order between different BallInstances.
- Transport order does not replace business sequence.

If two external actions must execute sequentially, a subsequent Decision creates the second output after the required outcome or acceptance of the first, or a separate ordered contract is used. Incidental worker scheduling must not be relied upon.

### 9.13. Live and durable outputs

By default:

| Output | Baseline semantics | Stronger semantics require |
|---|---|---|
| `ProjectionOutput` | Live delivery; drop or resync is permitted after source acceptance | Durable stream/subscription extension |
| `ReplyOutput` | Bound to a request channel and may be lost | Durable channel acceptance + status fallback |
| `EffectRequest` | Source accepted; execution contract is separate | Durable executor/provider idempotency/status |
| `ModuleCommandRequest` | Source accepted; target acceptance is separate | Target inbox/dedup/ACK contract |
| `SignalPublication` | Bounded publication | Broker append/replay contract |
| `TimerRequest` | Source accepted; scheduler contract is separate | Durable timer race/recovery contract |

Durability of source state does not automatically extend to every output channel.

If a binding uses the term `at-least-once`, it must name the exact delivery boundary. In Core, this can mean only duplicate-permitting attempts to transfer an already accepted output to the named executor or transport boundary under explicitly stated liveness assumptions; it does not mean target receipt, target acceptance, or business success.

Attempts and the retry horizon are finite. After they are exhausted, the runtime records `DispatchStopped` with reason and attempt evidence; a durable profile preserves the accepted output and terminal delivery observation until the declared retention horizon, while a Transient profile is limited to the process lifetime. The runtime does not continue retries indefinitely, reset identity or budget, or claim target delivery. A stronger claim must define the boundary, availability assumptions, recovery ownership, retention, and status and reconciliation policy.

---

## 10. System composition

### 10.1. Application component kinds

#### Feature Ball

Owns a local business capability and its state.

```text
AuthBall
CatalogBall
CartBall
PaymentBall
ProfileBall
```

A Feature Ball does not import another Ball's internals or read another Ball's state.

#### Flow Ball

Owns the coordination state of an independent multi-step workflow.

```text
CheckoutFlow
AccountDeletionFlow
OrderCancellationFlow
```

#### Read Model Ball

Owns materialized read state, source positions, freshness, and rebuild policy. It does not become the command authority for source facts.

#### Utility package

Pure stateless code without state, protocol, lifecycle, or resource authority. It need not be a `Ball`.

### 10.2. Permitted inter-module dependencies

An application `Ball` may declare four kinds of dependency.

#### ReadDependency

A small stable read-only contract:

```text
Catalog -> Pricing.GetCurrentQuote
```

The caller receives version and freshness metadata and does not treat the read as an atomic multi-source transaction.

#### DeclaredCommandDependency

A one-hop addressed command is permitted without a separate Flow when all of the following hold:

- there is one target;
- there is no multi-participant ordering;
- there is no independent coordination lifecycle;
- there is no cross-participant compensation;
- the source stores only local operation state;
- command, result, idempotency, and deadline contracts are explicit;
- the dependency graph remains bounded and acyclic at the level of direct control imports.

Example:

```text
OrderBall -> NotificationBall.SendReceipt
```

#### DeclaredSignalDependency

An explicit dependency on another `Ball`'s semantic publication. It is used when a consumer accepts a producer-owned `Signal` as a typed `ObservedSignal` Pulse but neither addresses a command to the producer nor gains authority from the Signal to perform a privileged action.

Every such dependency MUST declare:

```text
producer
signalType
consumer
producerProtocolVersion
consumerProtocolVersion
deliverySemantics
identityPolicy
idempotencyOrDedupPolicy
orderingScope
limits {
    maxConsumersPerSignal
    maxObservationBytes
    maxBufferedOrInFlightObservations
    maxCausalDepth
    maxDeliveryAttempts
}
```

`deliverySemantics` names the observation point and whether loss or redelivery is permitted; `identityPolicy` defines stable identity; `idempotencyOrDedupPolicy` defines duplicate handling or a justified `not-applicable`; `orderingScope` explicitly defines the local ordering scope or `none`. All limits are finite. Strong durable delivery is not inferred from the dependency itself and requires a corresponding channel contract.

An `ObservedSignal` carries the producer identity, source revision, handle, ordinal, and issuer provenance required by the declared identity and idempotency or deduplication policies. The selected protocol versions, delivery semantics, and ordering scope are fixed by the route contract, not added ad hoc by the consumer. The publisher does not import consumers: the producer-to-consumer edge belongs to the manifest or `Assembly`, has bounded fan-out, and does not become a wildcard subscription.

#### FlowParticipation

Used when coordination belongs to a separate Flow.

### 10.3. When a Flow is needed

A separate Flow is needed when at least one of the following coordination properties is material:

- multiple participant authorities must lead to one terminal outcome;
- sequence, branch, or join is business state;
- compensation or reconciliation exists between participants;
- the operation lives independently of the initiating Feature state;
- a separate manual-intervention queue is needed;
- the workflow is reused by multiple initiators;
- it has separate retention, security, or scaling requirements;
- it has child workflows and independent deadlines.

A Flow is not needed merely because one command hop exists.

A one-hop `DeclaredCommandDependency` is permitted only while every condition in §10.2 holds. Adding a Flow does not legalize unbounded fan-out, wildcard routing, or a cycle of direct control dependencies: those relationships must be bounded or redesigned separately.

### 10.4. Workflow sovereignty

> **Every stateful cross-authority workflow has one coordination owner.**

A participant owns its local operation. A Flow owns only orchestration facts:

```text
phase
participant semantic handles
captured versions
branch/join state
deadlines
cancellation state
compensation/reconciliation state
terminal workflow outcome
```

If a later-phase decision uses a value from earlier ingress or a verified participant result, the Flow must either retain a field-minimized typed value in its state or obtain it again through an explicitly declared trusted `Pulse` or `DecisionContext`. The retained value carries the source handle or correlation, protocol or schema version, verified provenance, and retention through the last reachable consumer. A runtime ledger, outbox, participant history, and ambient memory are not hidden inputs to the `Nucleus`.

A Flow does not copy participants' internal step ledgers and does not become the owner of their domain facts.

### 10.5. Compensation

Compensation is a new fallible action, not a rollback of time.

It has its own:

```text
SemanticHandle
idempotency
capability/grant
deadline
outcome
reconciliation policy
```

If the original action has `OutcomeUnknown`, conflicting compensation is not started before reconciliation unless the business risk is explicitly accepted.

For a parallel workflow, compensation order is derived from the dependency graph, not from incidental reverse completion order.

### 10.6. Read aggregation

Multiple read sources may be combined in:

- presentation composition layer;
- dedicated Read Model Ball;
- a Flow-captured snapshot for a specific operation.

A set of the latest independently read values is not called a consistent snapshot. A strict command decision must revalidate versions, use a target-owned reservation or constraint, or accept eventual workflow semantics.

### 10.7. Assembly

`Assembly` connects public protocol exports and imports to concrete targets and adapters.

```text
Catalog.FindProducts -> LocalCatalogSqlAdapter
CheckoutFlow.PaymentCapture -> PaymentBall.Capture
OrderConfirmed -> AnalyticsStream
CatalogIndexUpdated -> SearchReadModel.ObserveCatalogIndexUpdated
```

Assembly is responsible for the route, selected protocol version, delivery binding, and deployment wiring. It makes no business decision and reads no private state.

For a `DeclaredSignalDependency`, Assembly fixes the producer, consumer, both protocol versions, delivery semantics, identity policy, idempotency or deduplication policy, ordering scope, and finite limits. Such a route delivers an `ObservedSignal` but does not turn the Signal into a command or grant the consumer additional authority.

Static composition is the default. It may be generated direct dispatch and does not require a runtime service registry.

### 10.8. No protocol re-export

A Ball does not publish another Ball's owned types as its own contract.

Bad:

```text
CheckoutProjection {
    CartEntity
    PaymentProviderResponse
    InventoryRow
}
```

Good:

```text
CheckoutProjection {
    lineItems: CheckoutLineItem[]
    paymentStatus: CheckoutPaymentStatus
    availability: CheckoutAvailability
}
```

Opaque references are permitted when their semantics and ownership are clear.

### 10.9. Bounded composition

Project policy defines ceilings:

```text
maxDeclaredDependenciesPerBall
maxFlowParticipants
maxRoutesPerFlow
maxConsumersPerSignal
maxOutputsPerDecision
maxCausalDepth
maxCumulativeFanout
```

Limits must remain constant with respect to growth in the total number of modules. One universal event bus with wildcard subscriptions destroys the ability to calculate change radius.

Every `DeclaredSignalDependency` counts toward `maxDeclaredDependenciesPerBall`, and each of its consumers counts toward `maxConsumersPerSignal` and cumulative fan-out. An undeclared consumer and a wildcard signal route are prohibited.

### 10.10. Cycles

The compile-time import graph and direct synchronous or control dependency graph must be acyclic.

A business process may return to an earlier phase or create feedback through declared asynchronous signal dependencies or other explicit asynchronous routes, but such a cycle must have:

- stateful owner;
- stable identity/idempotency;
- finite causal/retry budget;
- terminal/escape condition;
- protection from self-amplifying fan-out.

Permitted asynchronous feedback does not make a compile-time import or direct synchronous or control dependency cycle permissible.

### 10.11. Versioning and compatibility

At minimum, distinguish:

```text
protocolVersion
stateSchemaVersion
transitionArtifactVersion
wireCodecVersion        # only when there is a wire boundary
```

Rules:

1. An existing variant does not change semantic meaning retroactively.
2. A breaking contract receives a new protocol version.
3. Assembly selects a concrete producer and consumer version pair for every dependency, including a signal route; a range alone does not prove semantic compatibility.
4. An unknown variant is not ignored as a business no-op without an explicit forwarding policy.
5. Persistent state is migrated or upcast before ordinary `decide`.
6. A committed durable output retains the protocol and codec semantics under which it was accepted; the current Assembly does not silently reinterpret it.
7. The transition artifact version is part of deterministic and replay lineage.

For a local Inline build, the compiler may provide exact compatibility. Independently deployed boundaries require contract tests and an explicit rollout window. Full wire canonicalization and a rolling-migration protocol belong to extension specifications.

---

## 11. Security and privacy

### 11.1. Double Quarantine

Both external sides of the Nucleus are treated as untrusted:

```text
User / HTTP / UI / OS        -> untrusted input
Network / DB / File / SDK    -> untrusted resource output
```

Data path:

```text
Untrusted bytes
  -> parse
  -> normalize by field contract
  -> validate and bound
  -> validated semantic value + trusted actor context when required
  -> Nucleus Policy Gate
```

A Resource response passes through an analogous parse, validation, and provenance path before a `Fact` is created.

The boundary before the `Nucleus` authenticates the actor and verifies the origin of actor context, but does not make the business authorization decision. A value becomes an authorized cause of a privileged action only after the `Nucleus` Policy Gate; authoritative execution then passes separately through the Execution Gate.

### 11.2. Actor context

Authentication is performed by a trusted boundary, not by the request payload.

Minimal semantic form:

```text
AuthenticatedActorContext {
    stableSubjectId
    issuer
    namespaceOrRealm
    authenticationMethod
    assuranceLevel?
    authenticatedAt
    expiresAt
    delegation?
}
```

`stableSubjectId` must be scoped by issuer and realm. Credential rotation does not automatically create a new subject.

The presence of an `issuer` field is not proof of origin. The selected security or isolation profile MUST define verifiable authenticity and integrity of actor context relative to an approved issuer. `AuthenticatedActorContext` itself describes the actor but grants no authority to perform an arbitrary action.

### 11.3. Policy Gate and Execution Gate

#### Policy Gate

The `Nucleus` decides whether the action is permitted for the actor in the current state and context.

#### Execution Gate

Immediately before an authoritative action, the target or resource verifies:

- technical capability;
- authenticity and integrity of actor context and, when a privileged action requires a grant, the grant itself from an approved issuer for the relevant realm, audience, and action;
- binding of actor and context, audience, action, object, operation, and every constrained command or effect field to the actual target and payload;
- expiry or revocation policy, when used;
- expected target version or invariant;
- idempotency key;
- endpoint identity;
- local quota/budget;
- safe sink constraints.

Resource does not reinterpret business intent, but enforces the proof and target-owned invariant.

The Execution Gate fails closed: missing, unverifiable, expired, revoked, or mismatched evidence does not authorize an authoritative action and is not replaced by an ambient credential, transport authentication, or a trusted string-valued `issuer`. Rejection returns as a declared typed outcome. Authentication of an IPC peer alone does not make that peer an approved authorization issuer.

### 11.4. Capability

A Capability is the minimum technical authority to perform a bounded class of operations.

Prefer:

```text
CatalogSearchCapability(index=products, maxPageSize=100)
PaymentCaptureCapability(merchant=42, currency=EUR, maxAmount=500)
SandboxFileCapability(root=/app/cache, operations=[read, write])
```

Instead of:

```text
DatabaseAdmin
ArbitraryHttp
FileSystemAll
ShellExecute
```

A Capability must be enforced by a real boundary: a scoped credential, restricted client, broker, DB role, OS handle, network policy, or isolated process. `if (allowed)` inside an adapter that already holds an unrestricted credential is only code discipline.

### 11.5. No ambient authority

Application code does not obtain resources through:

```text
Database.global
HttpClient.default
ServiceLocator.get(...)
ApplicationContext
GlobalScope
```

Dependencies are supplied through explicit construction or static assembly. The `Nucleus` receives semantic context and values, not service objects.

### 11.6. Safe sinks

A typed operation is necessary but insufficient.

| Resource | Safe sink requirements |
|---|---|
| SQL | Parameterization, timeout, row/byte limit, least-privilege DB role |
| Pattern search | Explicit literal/wildcard semantics and dialect escaping |
| Filesystem | Capability-rooted path resolution; no raw traversal |
| HTTP | Allowed service identity, DNS/redirect/response bounds |
| OS | Structured API; shell only as an explicit unsafe exception |
| HTML/JS | Context-specific encoding in Interaction |
| Serialization | Schema, size/depth bounds, unknown-field policy |

### 11.7. Authorization grant

A privileged cross-Ball command or effect may carry attenuated proof:

```text
AuthorizationGrant {
    actorContextId
    issuer
    audience
    action
    objectRef
    operationId
    constraints?
    expectedObjectVersion?
    issuedAt
    expiresAt
}
```

A Grant is effective only when the Execution Gate has verified the approved issuer, authenticity and integrity, and binding to trusted actor context and the actual operation. The action contract defines mandatory constrained fields; each such field in the actual payload, including amount and currency or equivalent restrictions, must match or fall within the grant. An unverified or absent constraint grants no additional authority.

A Flow passes only the grant required by the next participant, not the entire principal or session object. A Flow may forward or attenuate a grant within the original authority, but does not expand constraints and is not considered an issuer unless a trusted issuance policy separately permits it.

Exact cryptographic format, wire canonicalization, and key distribution belong to the Secure extension. Core requires a semantic verification contract but does not choose a signature, MAC, protected channel, unforgeable handle, or other concrete mechanism.

### 11.8. In-process limitations

In-process visibility, a private modifier, and a wrapper type do not isolate credentials from malicious native code. `InProcess + Hardened` may reduce accidental misuse but does not claim hostile-component containment.

An ordinary in-process data object with an `issuer` field is not proof. A Hardened binding must have a profile-defined trusted issuance and verification boundary even when cryptographic encoding is unnecessary within one trusted process.

A hostile plugin, parser, or SDK requires an `Isolated` boundary: a process or sandbox, bounded IPC, separate credentials, resource quotas, and crash containment.

### 11.9. Secrets

```text
Secret<T>
CredentialHandle
PrivateKeyHandle
AccessToken
```

Prohibited by default:

- ordinary `toString`/debug rendering;
- Projection, Signal, and generic Reply;
- logs/metric labels;
- unencrypted persistence;
- arbitrary serialization;
- generic read model/replica;
- plugin transfer.

A wrapper does not promise physical zeroization in a garbage-collected runtime. The implementation honestly documents copies, swap, crash dumps, and key lifecycle.

### 11.10. Unsafe escape hatch

Raw SQL, shell, an arbitrary URL, unsafe deserialization, or unrestricted filesystem access is permitted only as an explicitly named unsafe operation with:

```text
owner
reason
scope
capability
isolation decision
security review
tests
expiry/remediation
```

An escape hatch must not masquerade as an ordinary `Effect`.

### 11.11. Observability and privacy

An operational trace contains causal identifiers, type names, revisions, duration, and outcome, but no secrets or optional raw payloads.

A security audit, replay bundle, metrics, and debug trace are distinct data products. None substitutes for another.

---

## 12. Execution profiles

Profiles are independent dimensions. A single overloaded label such as `DurableSecureFast` is not used.

```text
execution:   Inline | BoundedConcurrent
state:       Transient | SnapshotOutbox | EventJournal
isolation:   InProcess | Isolated
security:    Standard | Hardened
composition: Static
```

`Secure`, as a strong adversarial claim, is reserved for a separate isolation and security specification.

### 12.1. Semantics common to all profiles

Every profile preserves:

- the three logical zones;
- explicit ownership;
- pure bounded decision;
- no reentrant transition;
- atomic Decision acceptance;
- commit-before-dispatch;
- causal identity;
- explicit idempotency/retry policy;
- typed cancellation/unknown outcomes;
- finite limits.

### 12.2. Inline

```text
Interaction -> decide -> atomically publish AcceptedDecisionFrame -> dispatch retained outputs
```

Properties:

- one mutating transition at a time;
- no mandatory mailbox;
- no mandatory parallel effects;
- no imposed synchronization, queue, coroutine, serialization, or thread hop;
- synchronous completion processed in the next iteration after acceptance;
- a caller-owned or fixed output buffer is permitted.

Suitable for mobile, desktop, embedded, a local backend use case, and a unit-test binding.

### 12.3. BoundedConcurrent

```text
bounded mailbox
  -> single-writer decision loop
  -> bounded effect/command workers
```

Required:

- finite mailbox capacity;
- typed backpressure/admission outcome;
- finite worker counts;
- a fairness policy that prevents obvious starvation of control inputs;
- out-of-order Fact/Result handling;
- cancellation/deadline race tests;
- no concurrent mutation of one state instance.

### 12.4. Transient state

- state lives only in process memory;
- a crash may lose state, live replies, and pending operations;
- the source claims no durable delivery;
- an external side effect may have an unknown outcome after a crash;
- recovery uses reinitialization or resynchronization according to the application contract.

### 12.5. SnapshotOutbox

Minimal durable-state profile:

- the state snapshot and revision, accepted-input marker, and durable source outputs are recorded by one authoritative transaction;
- output dispatch begins after commit;
- a durable source record remains `Pending` while the declared delivery policy permits attempts;
- the dispatcher makes a finite number of attempts under declared deadline and retry limits; repeated attempts preserve the same `OutputId` and may deliver a duplicate;
- target or executor deduplication is mandatory for duplicate-permitting irreversible operations;
- when attempts, the deadline, or another declared terminal delivery condition are exhausted, the source atomically records `DispatchStopped` with the reason and available attempt evidence; output and status are not silently discarded and are retained until the declared retention horizon;
- inbox and idempotency records have a declared retention horizon;
- the storage commit verifies the expected revision;
- if instance ownership can move, the commit verifies a storage-enforced ownership epoch;
- an unknown commit outcome does not permit blind re-evaluation of an irreversible input;
- a durable mutation provides operation status.

The Core guarantee ends at durable acceptance and retained source output and status. It does not promise target receipt, target acceptance, or external execution under every failure schedule.

A concrete binding may claim `at-least-once` only relative to an explicitly named delivery point and with recorded liveness and failure assumptions, retry and retention horizon, and terminal policy. Without such a contract, `SnapshotOutbox` means duplicate-permitting bounded dispatch from a durable source record, not unconditional eventual delivery or exactly-once external execution.

### 12.6. EventJournal

- authoritative mutation consists of ordered domain events;
- every Accepted result records an `AcceptedEventCommit`, including `NoDomainChange` with an empty event batch;
- the commit envelope, event batch, accepted-input, idempotency and status metadata, and source outputs are accepted atomically;
- state is reconstructed through `evolve`;
- outputs are not regenerated by rerunning current transition code;
- exact replay requires pinned artifacts and context and belongs to a separate extension specification.

### 12.7. InProcess

- boundaries are logical and enforced by compile-time or runtime discipline;
- direct calls and a shared allocator are permitted;
- crash containment and hostile credential isolation are not claimed;
- least privilege requires scoped external credentials and adapters, not only visibility modifiers.

### 12.8. Isolated

- process/sandbox boundary;
- versioned authenticated IPC; peer authentication does not replace authorization-issuer verification;
- profile-defined authenticity and integrity of actor context and grants under §11.3;
- bounded frame size/depth;
- no shared mutable business memory;
- separate/scoped credentials;
- CPU/memory/wall/network/file limits;
- crash/restart/quarantine policy;
- anti-replay/idempotency according to operation class.

### 12.9. Hardened

`Hardened` adds the following to the selected execution and isolation profile:

- actor context from an approved issuer with verifiable authenticity and integrity;
- least-privilege capabilities;
- safe sinks;
- secret handling;
- explicit grants bound fail-closed to the actual privileged target and payload;
- bounded resource abuse controls;
- security tests and threat assumptions.

It does not mean absolute security.

### 12.10. Profile selection

| Scenario | Recommended starting profile |
|---|---|
| Local UI state machine | `Inline + Transient + InProcess + Standard` |
| Async mobile feature | `BoundedConcurrent + Transient/SnapshotOutbox + InProcess` |
| Durable backend aggregate | `BoundedConcurrent + SnapshotOutbox + InProcess + Hardened` |
| Hostile plugin/parser | `BoundedConcurrent + Transient/SnapshotOutbox + Isolated + Hardened` |
| Distributed irreversible effect | Durable source + separate executor/provider contract; not only a Core profile |

---

## 13. Limits, budgets, and performance

### 13.1. Three classes of limits

#### Semantic limits

Affect the business or orchestration decision:

```text
maxItemsPerOrder
maxWorkflowSteps
maxCommands
maxRetriesByPolicy
businessDeadline
```

They belong in state or context and are visible to the `Nucleus`.

#### Runtime caps

Constrain implementation resources:

```text
CPU
wall time
memory
mailbox
IPC bytes
storage bytes
parallel workers
delivery attempts per output
outbox and operation-status retention
```

They are enforced by the runtime and may cause an admission failure or fault, but do not rewrite the business decision.

#### Economic/shared quotas

Money, provider calls, API credits, and a shared tenant balance require an authoritative ledger or reservation. Core requires only an explicit contract and prohibits copying the entire parent balance into parallel branches. A complete distributed budget protocol is an extension.

### 13.2. Backpressure

Overload returns a typed outcome, for example:

```text
TemporarilyUnavailable(retryAfter?)
CapacityExceeded(scope)
QueueFull
StoragePressure
```

An admission failure does not masquerade as a permanent business rejection. An unbounded queue is prohibited.

`DispatchStopped` after an already accepted durable output is not an admission failure or business rejection. It is a terminal delivery observation: the source retains the output and status according to the declared retention policy and delegates the next decision to reconciliation or manual policy.

### 13.3. Zero Mandatory Runtime Tax

Core semantics do not require:

```text
runtime handler lookup
reflection discovery
in-process serialization
mandatory queue
mandatory thread hop
object message hierarchy
service locator
```

Only a concrete Inline binding with a benchmark on the exact toolchain may claim `maxStructuralAllocationsPerDecision = 0`.

Distinguish:

- **structural allocation**—created solely by the architectural runtime mechanism;
- **payload allocation**—useful domain or result data;
- **payload copy**—copying useful payload across a boundary.

### 13.4. Performance contract

A Ball or project records the applicable metrics:

```text
transition p50/p95/p99
structural allocations
payload copies
state bytes
mailbox occupancy
outbox age
max effects/commands
external response bytes
CPU/wall limits
binary contribution
```

An absolute number without a workload, hardware, compiler and runtime, payload distribution, and measurement method is not a substantiated claim.

### 13.5. Design-time tax

Architectural cost includes more than runtime:

```text
manifest size
number of artifacts
routes per operation
manual review hours
waiver count
schema/evidence maintenance
flow-to-feature ratio
```

A simple Ball should not pay ceremony for unused distributed guarantees.

---
## 14. Minimal manifest and source-code organization

### 14.1. Role of the manifest

`BallManifest` is a declarative description of a `Ball`'s architectural contract. It supports review, documentation generation, static checks, and later automation, but is not a mandatory runtime registry.

The presence of a manifest field does not prove that the implementation complies with it. For example:

- `singleWriter: true` requires actual scheduler or storage enforcement;
- `maxStructuralAllocations: 0` requires a benchmark;
- `capability: Catalog.Search` requires a concrete restricted client, credential, or broker;
- `noCrossStateReads: true` requires inspection of the dependency graph and code.

At an early stage, the manifest MAY be a small YAML or JSON file or a typed declaration in source code. A complete schema package is not part of Core.

`spec.protocols` lists only protocol variants owned by the `Ball` itself. For the selected `protocolVersion`, every non-empty owned category MUST be declared inline or resolved through exactly one explicit, version-pinned authoritative reference. An absent category denotes an empty closed set for that category; absence of the entire `protocols` block means that every owned category is empty. This rule is the same for `FeatureBall` and `FlowBall`.

Every owned `queries` entry MUST define a `query -> result` pair in which both payload types belong to the same protocol version. The result type is the payload of canonical `ReadResult<ResultPayload>` in §6.3, not a committed `ReplyOutput` or `ProjectionOutput`. A query with zero or multiple result mappings is invalid.

Imported `ModuleCommand` and `ModuleResult` types belong to the target. In `dependencies.commands`, a qualified `operation` together with `targetProtocolVersion` MUST unambiguously select the target's closed public command and result contract and MUST NOT be redeclared as an owned type of the caller or Flow. The selected version MUST match the corresponding route's `Assembly.consumerProtocolVersion`. Internal state variants and operation facets do not become separate manifest protocol variants merely because they are stored in `State`.

### 14.2. Minimal Core manifest

```yaml
apiVersion: pokeball.dev/core/v1alpha1
kind: BallManifest
metadata:
  id: Catalog
  ballKind: FeatureBall
  protocolVersion: 1.0.0
  stateSchemaVersion: 1

spec:
  instance:
    stateKey: CatalogSessionId
    singleWriter: true

  owns:
    - catalog.search-session
    - catalog.selection

  protocols:
    intents: [SearchRequested, ProductSelected, SearchCancelled]
    queries:
      - { query: GetCatalogView, result: CatalogProjection }
    projections: [SearchIdle, SearchLoading, SearchResults, SearchFailed, SearchCancellationStatus]
    effects: [FindProducts, CancelProductSearch]
    facts:
      - ProductsFound
      - ProductSearchFailed
      - ProductSearchOutcomeUnknown
      - ProductSearchCancelledBeforeStart
      - ProductSearchCancellationAcceptedInProgress
      - ProductSearchCancelled
      - ProductSearchCancellationTooLate
      - ProductSearchCancellationRejected
      - ProductSearchCancellationOutcomeUnknown
    signals: [ProductSelectionConfirmed]

  limits:
    maxInputBytes: 65536
    maxStateBytes: 131072
    maxCollectionItems: 100
    maxOutputsPerDecision: 8
    maxEffectsPerDecision: 2
    maxCommandsPerDecision: 0
    maxCausalDepth: 8
    maxRetriesPerOperation: 2
    maxDeliveryAttempts: 3
    maxTransitionSteps: 1024

  capabilities:
    required: [Catalog.Search]
    forbidden: [ArbitrarySql, Shell, ArbitraryUrl]
```

Empty protocol sections are omitted. In contrast, every mandatory limit from §8.3 is declared explicitly. For `maxEffectsPerDecision` and `maxCommandsPerDecision`, a value of `0` means that the corresponding output kind does not exist in this protocol version; if the protocol contains such a variant, its limit must be greater than zero. For `maxRetriesPerOperation`, a value of `0` means retries are disabled. Zero never means `unbounded` or “not checked.” In the Catalog example, `maxCommandsPerDecision: 0` is correct because Catalog does not create a `ModuleCommandRequest`.

### 14.3. Manifest for a Flow

```yaml
apiVersion: pokeball.dev/core/v1alpha1
kind: BallManifest
metadata:
  id: Checkout
  ballKind: FlowBall
  protocolVersion: 1.0.0
  stateSchemaVersion: 2

spec:
  instance:
    stateKey: CheckoutOperationId
    singleWriter: true

  owns:
    - checkout.workflow

  protocols:
    intents: [CheckoutStarted, CancellationRequested]
    controlPulses: [CheckoutCommandDeliveryObserved]
    queries:
      - { query: GetCheckoutStatus, result: CheckoutStatus }
    replies: [RequestAccepted]

  participants: [Cart, Inventory, Payment, Order]

  dependencies:
    commands:
      - { operation: Cart.LockForCheckout, targetProtocolVersion: 1.0.0 }
      - { operation: Cart.Unlock, targetProtocolVersion: 1.0.0 }
      - { operation: Inventory.Reserve, targetProtocolVersion: 1.0.0 }
      - { operation: Inventory.Release, targetProtocolVersion: 1.0.0 }
      - { operation: Payment.Capture, targetProtocolVersion: 1.0.0 }
      - { operation: Payment.CancelCapture, targetProtocolVersion: 1.0.0 }
      - { operation: Payment.Refund, targetProtocolVersion: 1.0.0 }
      - { operation: Payment.GetOperationStatus, targetProtocolVersion: 1.0.0 }
      - { operation: Order.Confirm, targetProtocolVersion: 1.0.0 }

  limits:
    maxInputBytes: 65536
    maxStateBytes: 262144
    maxCollectionItems: 256
    maxEffectsPerDecision: 0
    maxCommandsPerDecision: 8
    maxParticipants: 4
    maxParallelBranches: 2
    maxOutputsPerDecision: 8
    maxCausalDepth: 16
    maxRetriesPerOperation: 2
    maxDeliveryAttempts: 3
    maxTransitionSteps: 4096
    maxCompensations: 4
    maxCapturedInputBytes: 65536
    maxRoutesPerFlow: 9
    maxRetainedDispatchStops: 10
```

The inline manifest declares every non-empty Checkout-owned surface used by the example: mutation intents `CheckoutStarted` and `CancellationRequested`, the trusted post-commit control input `CheckoutCommandDeliveryObserved`, the query and result mapping `GetCheckoutStatus -> CheckoutStatus`, and the committed reply payload `RequestAccepted`. `CheckoutStatus` is the sole closed payload in §16.13 for a known, absent, or retention-expired operation; neither a transport 404 nor a second result type replaces it. Customer cancellation passes through Interaction and is not declared as a `ControlPulse`; `CheckoutCommandDeliveryObserved` is accepted only from a declared trusted runtime or route origin after handle and provenance verification. Participant command and result contracts are resolved through nine versioned `dependencies.commands` entries rather than copied into `spec.protocols`; this preserves target ownership and the prohibition on protocol re-export. `maxEffectsPerDecision: 0` establishes that the Flow creates no private `EffectRequest`: all work is performed through declared participant commands. Domain-specific `maxRetainedDispatchStops: 10` covers the initial `RequestAccepted` `ReplyOutput` and the nine closed Checkout command-step slots in §16.3, does not exceed `maxCollectionItems`, and does not become a new mandatory Core limit. `stateSchemaVersion: 2` records the retained workflow values added in §16.3; owned and imported protocol variants, dependency versions, and Assembly routes do not change. Migration of already stored state remains a separate rollout or extension contract under §§8.9/10.11: active v1 state must not be silently supplemented with null or default M/S/I/P values; the binding either reconstructs them from declared authoritative migration evidence or disallows normal v2 `decide` and moves the operation to a declared quarantine or manual path.

### 14.4. Assembly declaration

The relationship between a producer and consumer belongs to the composition root—`Assembly`—rather than being hidden in a global locator.

```yaml
assembly: ShopApplication
routes:
  - kind: DeclaredCommandDependency
    from: Checkout.CartLockCommand
    to: Cart.LockForCheckout
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.InventoryReservationCommand
    to: Inventory.Reserve
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.PaymentCaptureCommand
    to: Payment.Capture
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.OrderConfirmationCommand
    to: Order.Confirm
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.PaymentStatusCommand
    to: Payment.GetOperationStatus
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.PaymentRefundCommand
    to: Payment.Refund
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.InventoryReleaseCommand
    to: Inventory.Release
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.CartUnlockCommand
    to: Cart.Unlock
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredCommandDependency
    from: Checkout.PaymentCancellationCommand
    to: Payment.CancelCapture
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0

  - kind: DeclaredSignalDependency
    producer: Catalog
    signalType: ProductSelectionConfirmed
    consumer: RecommendationsReadModel
    producerProtocolVersion: 1.0.0
    consumerProtocolVersion: 1.0.0
    deliverySemantics: live-duplicate-permitting
    identityPolicy: sourceBallInstanceId+sourceCommitRevision+semanticHandle+sourceOrdinal
    idempotencyOrDedupPolicy: deduplicate-by-identity-within-retention
    orderingScope: CatalogSessionId
    limits:
      maxConsumersPerSignal: 1
      maxObservationBytes: 16384
      maxBufferedOrInFlightObservations: 64
      maxCausalDepth: 4
      maxDeliveryAttempts: 2
```

Assembly is responsible for concrete wiring. A `Ball` is responsible for the required semantic contract. The runtime is responsible for the invocation or delivery mechanism.

For a local monolith, a route may compile to a direct function call. For another process, the same route may use IPC. This must not change domain semantics.

### 14.5. Recommended directory structure

```text
features/
  catalog/
    interaction/
      mobile/
      http/
      test/

    nucleus/
      protocol/
      state/
      transition/
      policy/

    resources/
      search/

    ball.yaml

flows/
  checkout/
    interaction/
    nucleus/
      protocol/
      state/
      transition/
    ball.yaml

application/
  assembly/
  runtime/
  observability/

foundation/
  bounded/
  security/
  time/
  tracing/
```

Physical directories are not normative. Dependency direction is normative:

```text
interaction -> own public interaction protocol
resources   -> own private resource protocol
nucleus     -> own state + own protocols + mechanical foundation
assembly    -> public protocols + manifests
```

Prohibited directions:

```text
interaction -> resources
resources   -> interaction
nucleus     -> UI / HTTP / SQL / filesystem / platform SDK
feature A   -> feature B internals
feature A   -> mutable state of feature B
```

### 14.6. Interfaces, DI, and code generation

Pokeball does not require an interface for every class, a repository abstraction merely for mocking, or a runtime DI container.

An interface is justified when a real boundary exists:

- multiple production implementations;
- platform boundary;
- process/plugin boundary;
- public library contract;
- independently deployable adapter;
- a test double for an external nondeterministic resource, not for a pure Nucleus.

Constructor injection and compile-time wiring are permitted. A service locator and ambient global client are prohibited for application authority.

Code generation MAY create:

- tagged unions;
- exhaustive dispatch;
- bounded output frames;
- manifest consistency checks;
- state diagrams;
- route maps;
- test fixtures.

The generator must not become a hidden runtime framework. Stabilize the semantic model first, then automate repeatable mechanics.

### 14.7. Foundation Quarantine

The shared foundation contains only stable mechanical primitives:

```text
BoundedString / BoundedList
checked numeric operations
Revision / Deadline / Cancellation
SemanticHandle / TraceId
Secret wrapper
validation helpers
small result/error primitives
```

Domain authorities and universal business models do not belong in the foundation:

```text
User
Order
Payment
Cart
Session
Product
CommonResponse
BaseEntity
UniversalDto
```

Shared code is extracted when semantics, invariants, and change cadence match, not merely when field structure matches. Small local duplication is cheaper than a shared abstraction with high fan-in.

---

## 15. End-to-end example I: catalog search

This example shows a small `FeatureBall` with an `Inline` decision path and an asynchronous resource result. It requires no Flow, broker, or durable workflow engine.

### 15.1. Protocol

```text
CatalogIntent =
    SearchRequested(query, pageSize)
  | SearchCancelled(operationId)
  | ProductSelected(productId)

CatalogQuery =
    GetCatalogView

CatalogFact =
    ProductsFound(searchHandle, generation, products, provenance)
  | ProductSearchFailed(searchHandle, generation, failure, provenance)
  | ProductSearchOutcomeUnknown(searchHandle, generation, provenance)
  | ProductSearchCancelledBeforeStart(cancellationHandle, targetSearchHandle, generation, provenance)
  | ProductSearchCancellationAcceptedInProgress(cancellationHandle, targetSearchHandle, generation, provenance)
  | ProductSearchCancelled(cancellationHandle, targetSearchHandle, generation, provenance)
  | ProductSearchCancellationTooLate(cancellationHandle, targetSearchHandle, generation, provenance)
  | ProductSearchCancellationRejected(cancellationHandle, targetSearchHandle, generation, reason, provenance)
  | ProductSearchCancellationOutcomeUnknown(cancellationHandle, targetSearchHandle, generation, provenance)

CatalogProjection =
    SearchIdle
  | SearchLoading(operationId, query)
  | SearchResults(operationId, products)
  | SearchFailed(operationId, reason)
  | SearchCancellationStatus(operationId, cancellation)

CatalogEffect =
    FindProducts(searchHandle, query, pageSize, mode, deadline)
  | CancelProductSearch(targetSearchHandle)
```

The owned query mapping is `GetCatalogView -> CatalogProjection`. A successful local read returns `ReadResult<CatalogProjection>` with the stamp of the exact committed Catalog snapshot; it creates neither a `ProjectionOutput` nor a new commit.

### 15.2. State

```text
SearchCancellationFacet =
    NotRequested
  | Requested
  | CancellationAcceptedBeforeStart
  | CancellationAcceptedInProgress
  | CancellationTooLate
  | CancellationRejected
  | CancellationUnknown

CatalogState =
    Idle(revision)
  | Searching {
        operationId
        generation
        query
        pageSize
        pendingSearch: SemanticHandle
        pendingCancellation: SemanticHandle?
        cancellation: SearchCancellationFacet
        revision
    }
  | Ready {
        operationId
        generation
        query
        products
        pendingCancellation: SemanticHandle?
        cancellation: SearchCancellationFacet
        revision
    }
  | Failed {
        operationId
        generation
        reason
        pendingCancellation: SemanticHandle?
        cancellation: SearchCancellationFacet
        revision
    }
  | Cancelled {
        operationId
        generation
        cancellation: CancellationAcceptedBeforeStart | CancellationAcceptedInProgress
        revision
    }
```

State stores a `SemanticHandle`, not a storage-generated `OutputId`. A runtime ledger may associate:

```text
SemanticHandle {
    operationId = search-88
    outputKind = Catalog.FindProducts
    localOrdinalOrName = "generation-1"
} -> OutputId(out-8f...)
```

This mechanical association does not change business state.

### 15.3. Interaction: parsing and validation

Raw request:

```text
query = "50%_off' OR 1=1 --"
pageSize = "100"
```

Interaction performs:

```text
UTF-8 validation
NFC normalization according to field contract
SearchText.parse(maxUtf8Bytes = 128)
PageSize.parse(range = 1..100)
authentication context lookup
request deadline construction
```

The string is valid as literal user text. Special characters are not removed in the name of "security."

Request:

```text
pageSize = "2000000000"
```

is rejected as `BoundaryResponse(ValidationFailure.InvalidPageSize)`. Interaction does not create a `SearchRequested`, `Decision`, `CommitRevision`, or `SemanticHandle`: a boundary response is not a committed `ReplyOutput`. Clamping is permitted only as a separate, explicitly declared product policy.

The trusted boundary passes:

```text
DecisionContext {
    actorContext
    configurationSnapshot
    trustedTimeObservation
    reservedSemanticIds = [search-88]
    issuedAt
    expiresAt
}
```

`operationId` is neither read from an arbitrary client field nor generated from ambient randomness inside the Nucleus.

### 15.4. First decision

```text
Idle
+ SearchRequested(query, pageSize)
+ DecisionContext(reservedSemanticIds = [search-88])

searchHandle = SemanticHandle {
    operationId = search-88
    outputKind = Catalog.FindProducts
    localOrdinalOrName = "generation-1"
}

-> Searching {
     operationId = search-88
     generation = 1
     query
     pageSize
     pendingSearch = searchHandle
     pendingCancellation = none
     cancellation = NotRequested
   }

+ ProjectionOutput {
     semanticHandle = SemanticHandle {
         operationId = search-88
         outputKind = Catalog.SearchLoading
         localOrdinalOrName = "loading-generation-1"
     }
     sourceOrdinal = 0
     payload = SearchLoading(search-88, query)
  }
+ EffectRequest {
     semanticHandle = searchHandle
     sourceOrdinal = 1
     payload = FindProducts(
         searchHandle,
         query,
         pageSize,
         mode = LiteralContains,
         deadline = context.expiresAt
     )
   }
```

Runtime checks output bounds and capability binding, accepts the `Decision`, and then dispatches the Effect. If state is persistent, state and the effect outbox are recorded atomically.

### 15.5. Resource adapter and safe sink

The adapter receives only the `Catalog.Search` capability. It has no unrestricted database client, shell, or arbitrary HTTP client.

For SQL `LIKE` with literal semantics:

```text
escaped = escapeLikeLiteral(query, '\\')
pattern = "%" + escaped + "%"

SELECT product_id, title, price
FROM product_search
WHERE searchable_text LIKE ? ESCAPE '\\'
ORDER BY rank DESC, product_id ASC
LIMIT ?
```

The following are mandatory:

- parameter binding;
- explicit literal/wildcard semantics;
- read-only credential;
- approved index/query plan policy;
- timeout;
- row and response-byte bounds;
- schema validation of the external result.

Parameterization prevents SQL interpretation. Escaping `%` and `_` defines literal-search semantics specifically. These are separate responsibilities.

### 15.6. Successful result

The Resource produces a validated, provenance-bound `CatalogFact`:

```text
ProductsFound {
    searchHandle = SemanticHandle {
        operationId = search-88
        outputKind = Catalog.FindProducts
        localOrdinalOrName = "generation-1"
    }
    generation = 1
    products = BoundedList(max = 100)
    provenance = CatalogSearchExecutor / attempt-1
}
```

Decision:

```text
Searching(generation = 1, pendingSearch = searchHandle, cancellation)
+ ProductsFound(searchHandle, generation = 1, products, provenance)

-> Ready(operationId, generation = 1, products, pendingCancellation, cancellation)
+ ProjectionOutput {
     semanticHandle = SemanticHandle {
         operationId
         outputKind = Catalog.SearchResults
         localOrdinalOrName = "results-generation-1"
     }
     sourceOrdinal = 0
     payload = SearchResults(operationId, products)
  }
```

### 15.7. Stale result

The user starts a second search before the first one completes:

```text
first  -> generation 1 / SemanticHandle(search-88, Catalog.FindProducts, "generation-1")
second -> generation 2 / SemanticHandle(search-89, Catalog.FindProducts, "generation-2")
```

A late `ProductsFound` with the complete handle of the first operation and `generation = 1` does not replace generation 2. The Nucleus:

- ignores the stale result;
- or records a diagnostic outcome;
- or uses only safe cache information under an explicitly declared merge rule.

It does not apply a result according to "the last callback wins."

### 15.8. Cancellation race

```text
Searching(pendingSearch = searchHandle, cancellation = NotRequested)
+ SearchCancelled(operationId)

cancelHandle = SemanticHandle {
    operationId
    outputKind = Catalog.CancelProductSearch
    localOrdinalOrName = "cancel-generation-2"
}

-> Searching(
     pendingSearch = searchHandle,
     pendingCancellation = cancelHandle,
     cancellation = Requested
   )
+ ProjectionOutput {
     semanticHandle = SemanticHandle {
         operationId
         outputKind = Catalog.SearchCancellationStatus
         localOrdinalOrName = "cancel-requested-generation-2"
     }
     sourceOrdinal = 0
     payload = SearchCancellationStatus(operationId, Requested)
  }
+ EffectRequest {
     semanticHandle = cancelHandle
     sourceOrdinal = 1
     payload = CancelProductSearch(targetSearchHandle = searchHandle)
  }
```

Every cancellation Fact must match simultaneously on `cancellationHandle`, `targetSearchHandle`, `generation`, and trusted provenance. After matching, the following exhaustive transitions apply:

| Fact | State transition | Subsequent behavior |
|---|---|---|
| `ProductSearchCancelledBeforeStart` | `Searching -> Cancelled(cancellation = CancellationAcceptedBeforeStart)`; from `Ready/Failed` with an already accepted terminal search result—an invariant/provenance fault with no new Decision | A search result is no longer expected; a mutually exclusive terminal proof in either order does not overwrite the previously accepted state. |
| `ProductSearchCancellationAcceptedInProgress` | From `Searching/Ready/Failed(cancellation = Requested)`, the current lifecycle/result is retained and only the facet changes to `CancellationAcceptedInProgress`; a repeat in the same facet or a late weaker proof from `Cancelled(CancellationAcceptedInProgress)` is a corroborating no-op with no new semantic state/output | Acceptance does not prove physical stop; a matching search result before or after this Fact remains authoritative. |
| `ProductSearchCancelled` | `Searching(cancellation = Requested)` or `Searching(cancellation = CancellationAcceptedInProgress) -> Cancelled(cancellation = CancellationAcceptedInProgress)`; from `Ready/Failed` with an already accepted terminal search result—an invariant/provenance fault with no new Decision | The matching terminal Fact itself proves cancellation acceptance and the `Cancelled` outcome even if the separate accepted-in-progress Fact is delayed or lost; a mutually exclusive terminal proof does not overwrite previously accepted state. |
| `ProductSearchCancellationTooLate` | The current `Searching/Ready/Failed` is retained with `cancellation = CancellationTooLate` | An already received or subsequent matching result remains authoritative. |
| `ProductSearchCancellationRejected` | The current state is retained with `cancellation = CancellationRejected` | The ordinary search lifecycle continues; the reason is available to the status projection. |
| `ProductSearchCancellationOutcomeUnknown` | The current state is retained with `cancellation = CancellationUnknown` | A matching result is not discarded; the reconciliation/status policy remains explicit. |

Every accepted state-changing transition above creates a `ProjectionOutput` with a complete `SemanticHandle`, the next zero-based `sourceOrdinal`, and payload `SearchCancellationStatus(operationId, cancellation)`. An exact duplicate or the specified corroborating no-op creates no duplicate semantic state/output. A conflict path is not a transition: it accepts no `Decision`, state, or output and follows the fault/quarantine policy in §8.8.

For one exact cancellation/search lineage, observation order is not a hidden precondition:

```text
ProductSearchCancellationAcceptedInProgress
ProductsFound
-> Ready(cancellation = CancellationAcceptedInProgress)

ProductsFound
-> Ready(cancellation = Requested)
ProductSearchCancellationAcceptedInProgress
-> Ready(cancellation = CancellationAcceptedInProgress)

ProductSearchCancellationAcceptedInProgress
ProductSearchCancelled
-> Cancelled(cancellation = CancellationAcceptedInProgress)

ProductSearchCancelled
-> Cancelled(cancellation = CancellationAcceptedInProgress)
late ProductSearchCancellationAcceptedInProgress
-> corroboration; no new semantic state/output
```

`ProductSearchFailed` follows the same compatible-ordering rules. They imply neither AIP-first transport ordering nor unbounded buffering.

Mutually exclusive terminal proofs have a symmetric contract:

```text
ProductSearchCancelled -> Cancelled
ProductsFound          -> invariant/provenance fault; no Decision; Cancelled remains accepted

ProductsFound          -> Ready
ProductSearchCancelled -> invariant/provenance fault; no Decision; Ready remains accepted
```

`ProductSearchFailed` follows the same terminal-conflict rule. This is neither last-arrival-wins nor an implicit conversion of typed `ProductSearchCancelled` into `CancellationTooLate`. Closing observation ordering changes the concrete Catalog `transitionArtifactVersion`, but not the public `protocolVersion: 1.0.0`, `stateSchemaVersion: 1`, state shape, manifest, or routes; no state migration is required.

Both permitted ordering traces have the same meaning:

```text
CancellationTooLate -> Searching(cancellation = CancellationTooLate)
ProductsFound       -> Ready(cancellation = CancellationTooLate)

ProductsFound       -> Ready(cancellation = Requested)
CancellationTooLate -> Ready(cancellation = CancellationTooLate)
```

Likewise, a matching `ProductsFound` after `CancellationAcceptedInProgress`, `CancellationRejected`, or `CancellationUnknown` is accepted and retains the corresponding cancellation facet. A linear `Loading | Cancelled | Ready` enum without an independent facet is insufficient.

### 15.9. Timeout and unknown

Local deadline expiry means that the caller stopped waiting. If the external request might have been accepted, the result:

```text
ProductSearchOutcomeUnknown
```

is not automatically converted into `ProductSearchFailed`. Repetition is usually safe for a read-only search, but that is a property of the specific effect contract, not a general timeout rule.

### 15.10. What the example demonstrates

- Interaction does not know SQL.
- Resource does not know UI state.
- The Nucleus does not know the database driver.
- User text does not become query code.
- State is protected by generation/handle causality.
- Runtime ID is separate from the semantic handle.
- The Inline path requires no mediator or queue.
- The asynchronous adapter can be replaced without changing decision semantics.

---

## 16. End-to-end example II: Checkout Flow

Checkout requires a separate `FlowBall` because it has multiple authorities, an independent terminal outcome, cancellation, compensation, and reconciliation.

### 16.1. Participants

```text
AuthBall
CartBall
InventoryBall
PaymentBall
OrderBall
CheckoutFlowBall
```

Local contracts:

```text
Cart.LockForCheckout
Cart.Unlock

Inventory.Reserve
Inventory.Release

Payment.Capture
Payment.CancelCapture
Payment.Refund
Payment.GetOperationStatus

Order.Confirm
```

`CheckoutFlowBall` knows the sequence and terminal meaning. Participants know only their own invariants and operations.

The owned surface inventory for Checkout `protocolVersion: 1.0.0` is defined by `spec.protocols` in the §14.3 manifest. `CheckoutStarted` and `CancellationRequested` resolve as `Intent`, `CheckoutCommandDeliveryObserved` as a trusted post-commit `ControlPulse`, `GetCheckoutStatus -> CheckoutStatus` as an owned Query/result mapping, and `RequestAccepted` as a committed `Reply` payload. Named participant results, including `CartLocked`, `InventoryReserved`, `PaymentCaptured`, status, and cancellation outcomes, are imported `ModuleResult` variants of the corresponding target-owned operation contract from `dependencies.commands`, not owned Checkout variants.

### 16.2. Ingress and idempotency

The external mutation contains:

```text
CheckoutStarted {
    cartId
    paymentMethodRef
    expectedCartVersion
    idempotencyKey
}
```

For `CheckoutStarted start` and trusted `AuthenticatedActorContext A`, one canonical semantic function applies:

```text
CheckoutStartFingerprintV1(start, A) =
    fingerprintV1(
        stableSubject = ScopedStableSubject(
            issuer = A.issuer,
            namespaceOrRealm = A.namespaceOrRealm,
            stableSubjectId = A.stableSubjectId
        ),
        namespace = A.namespaceOrRealm,
        operationKind = Checkout.Start,
        cartId = start.cartId,
        paymentMethodRef = start.paymentMethodRef,
        expectedCartVersion = start.expectedCartVersion
    )
```

`ScopedStableSubject` makes a lexical subject ID unambiguous within issuer/realm scope under §11.2. `fingerprintV1` receives one labeled typed tuple in the order shown; the concrete binding defines deterministic canonical encoding/digest and an artifact version, but does not change the field set or its sources. Interaction and the Checkout transition use the same `CheckoutStartFingerprintV1`, selected by compatible artifact versions.

`start.idempotencyKey`, `RequestId`, trace ID, retry/attempt data, and the reply channel are not fingerprint operands. They are not added through whole-Pulse hashing or ambient metadata. Interaction stores `IdempotencyScope + start.idempotencyKey + F`, where `F = CheckoutStartFingerprintV1(start, A)`; the accepted Checkout frame must atomically store the same `F` in `CapturedCheckoutInput.source.ingressFingerprint`. The Nucleus receives `start` as the current `Pulse` and `A` as the declared trusted `DecisionContext.actorContext`; the accepted-input ledger/history is not read as hidden input.

Same key + same fingerprint returns the existing `OperationId`. Same key + different fingerprint returns `IdempotencyConflict`.

`IdempotencyConflict` is encoded as a noncommittable `BoundaryResponse(DecisionRejected)` and is neither a `ReplyOutput` nor a separate owned manifest protocol variant.

### 16.3. Flow state

```text
CheckoutState {
    operationId
    phase
    cartSnapshot?: VerifiedStepValue<CheckoutCartSnapshot>
    retained {
        checkoutInput?: CapturedCheckoutInput
        inventoryReservation?: VerifiedStepValue<InventoryReservationRef>
        paymentCapture?: VerifiedStepValue<PaymentRef>
    }
    steps {
        cartLock
        inventoryReservation
        paymentCapture
        paymentCancellation?
        paymentReconciliation?
        orderConfirmation
        inventoryRelease?
        paymentRefund?
        cartUnlock?
    }
    cancellation
    deadline
    terminalOutcome?
}
```

Retained values have the following closed, bounded forms:

```text
CapturedCheckoutInput {
    paymentMethodRef: PaymentMethodRef
    source {
        operationId
        ingressFingerprint
        sourceProtocolVersion
        interactionArtifactVersion
    }
    actorBinding {
        stableSubjectBindingDigest
        issuer
        namespaceOrRealm
        actorContextId?
    }
}

VerifiedStepValue<T> {
    value: T
    authorityActionHandle: SemanticHandle
    observedViaStepHandle: SemanticHandle
    authorityProtocolVersion
    observedViaProtocolVersion
    resultProvenance
}
```

`CapturedCheckoutInput.source` binds M to the current `operationId`, exact output of `CheckoutStartFingerprintV1(start, A)`, Checkout protocol version, and Interaction artifact version; `ingressFingerprint` is byte-for-byte equal to the fingerprint in the atomically accepted Interaction idempotency record. The typed Intent itself has already been created by the verified Interaction boundary, while actor provenance is retained in the field-minimized binding below. No field requires reading the accepted-input ledger after commit. `actorBinding` is a stable-subject digest from the verified initial `AuthenticatedActorContext`, not a credential, session, or unrestricted principal. Optional `actorContextId` is permitted only as a non-authorizing issuer record reference that remains stable for at least the workflow horizon; if the issuer rotates/expires it, a fresh context/grant proves the same subject through digest + issuer + realm. Possession of the binding by itself authorizes nothing. `resultProvenance` is a bounded redacted descriptor/digest of already verified issuer and causal evidence for the result's authority action, not a reference that the `Nucleus` later dereferences in runtime history. Input `sourceProtocolVersion` equals the selected Checkout version; result authority/observation versions come from version-pinned dependencies. These fields are captured workflow values, but they do not make the Flow the authority for the cart, payment method, reservation, or payment: the participant still checks its own target and invariants.

This derivation correction changes `transitionArtifactVersion`, but not the public `protocolVersion: 1.0.0`, declared state shape, `stateSchemaVersion: 2`, or routes. A persisted schema-v2 record created by a prior artifact cannot be considered correct based on shape alone: before normal recovery/`decide`, rollout verifies equality of the retained value with authoritative accepted idempotency evidence or performs a declared deterministic migration. Missing evidence or a mismatch leads to a quarantine/manual path; the normal Nucleus receives no ledger read. If a concrete binding cannot distinguish artifacts or safely migrate a record in its persisted form, it increments its own state schema version under §10.11.

While a dependent transition remains reachable, these invariants apply:

- `retained.checkoutInput` appears atomically with acceptance of `CheckoutStarted`; M is retained while a capture decision is reachable, and the actor binding until the last reachable privileged order/cancellation/compensation decision. This closed example shape retains the entire bounded record until the later of the two triggers;
- `cartSnapshot` appears only from a verified result matching `steps.cartLock.handle` and is retained until a terminal order or terminal unlock/reconciliation;
- `retained.inventoryReservation` appears only from a verified result matching `steps.inventoryReservation.handle` and is retained until a terminal order or terminal release/reconciliation;
- `retained.paymentCapture` appears only from a verified direct/reconciled capture result matching the original payment handle and is retained until a terminal order and terminal refund/reconciliation;
- an exact duplicate of one observation is idempotent; another declared direct/reconciliation proof for the same `authorityActionHandle`, authority version, and canonical value is corroboration and creates no output/overwrite, while another value/authority version or a conflicting proof for the same authority action is an invariant/provenance fault;
- a value cannot be deleted merely because a dependent command was emitted: cleanup is permitted after the last reachable consumer and acceptance of all mandatory terminal/compensation outputs under the declared retention policy.

All retained records pass `maxInputBytes`, `maxCapturedInputBytes` where applicable, and `maxStateBytes` for the complete next frame. They are not truncated: an oversized value rejects the entire Decision before acceptance. Payment references, actor binding, and provenance are classified as sensitive and do not enter Reply, Projection, status, or an ordinary log/metric/trace; status exposes only a redacted typed lifecycle.

Every step stores semantic data:

```text
Step {
    handle: SemanticHandle
    dispatch: NotDispatched | Dispatched | DispatchStopped
    acceptance: NotAccepted | Accepted | RejectedBeforeAcceptance | AcceptanceUnknown
    outcome: NotExpected | Pending | Succeeded | Rejected | Failed | Cancelled | OutcomeUnknown
    cancellation: NotRequested | Requested | CancellationAcceptedBeforeStart | CancellationAcceptedInProgress | CancellationTooLate | CancellationRejected | CancellationUnknown
}
```

The facets are independent and preserve these invariants:

- `NotDispatched` requires `NotAccepted` and `NotExpected`;
- `AcceptanceUnknown` is permitted only while no verifiable ACK/result/status proof exists;
- a verified `ModuleResult` with accepted-command provenance itself proves target acceptance, advances `NotDispatched -> Dispatched` if the delivery policy has not already stopped, and then advances acceptance to `Accepted`, even if the separate ACK was lost;
- terminal `Succeeded | Rejected | Failed | Cancelled` requires `acceptance = Accepted`;
- `outcome = OutcomeUnknown` does not imply `acceptance = AcceptanceUnknown`: these cases are stored separately;
- a cancellation observation does not delete an already accepted business result.

Post-commit delivery/acceptance observations enter the Flow only as an owned `ControlPulse`:

```text
CheckoutCommandDeliveryObserved {
    source {
        sourceBallInstanceId
        sourceCommitRevision
        sourceOrdinal
        stepHandle: SemanticHandle
    }
    observationId
    observation:
        CommandDispatched(attemptId, deliveryEvidence)
      | CommandAccepted(acceptanceEvidence)
      | CommandRejectedBeforeAcceptance(reason, acceptanceEvidence)
      | CommandDeliveryAmbiguous(attemptId, ambiguityEvidence)
      | CommandDispatchStopped(reason, attempts, lastObservation)
    issuerProvenance
}
```

The trusted runtime/route boundary creates this Pulse only after source acceptance and verifies that `stepHandle` matches a previously committed Checkout step, that `observationId`/provenance are authentic, and that size and attempts satisfy `maxInputBytes`/`maxDeliveryAttempts`. A source commit by itself leaves a new step in `NotDispatched | NotAccepted | NotExpected`; runtime does not write `CheckoutState` facets directly.

The complete source tuple must resolve to exactly one previously committed `ModuleCommandRequest`; a handle without source commit/ordinal is insufficient. `observationId` is scoped to trusted issuer + source tuple, remains stable when one observation is redelivered, and differs for a new observation; it is a mechanical dedup identity, not a `SemanticHandle` or `AttemptId`. The dedup record is retained for at least the declared duplicate-delivery horizon. Every `reason`/evidence/`lastObservation` is a protocol-versioned bounded redacted value object: a raw transport exception, secret, or unbounded provider payload does not enter the Pulse/status.

The serialized transition applies an observation monotonically:

| Observation | Change to the matching `Step` |
|---|---|
| `CommandDispatched` | `NotDispatched -> Dispatched`; acceptance/outcome are not inferred from send evidence alone. |
| `CommandAccepted` | `NotDispatched -> Dispatched` if dispatch has not already stopped; `NotAccepted \| AcceptanceUnknown -> Accepted`; `NotExpected -> Pending`. |
| `CommandRejectedBeforeAcceptance` | `NotDispatched -> Dispatched` if dispatch has not already stopped; `NotAccepted \| AcceptanceUnknown -> RejectedBeforeAcceptance`; `NotExpected \| Pending \| OutcomeUnknown -> NotExpected`. Accepted/terminal proof conflicts with such an observation and fails closed. |
| `CommandDeliveryAmbiguous` | Permitted only after evidence that the attempt crossed the declared source dispatch point; `NotDispatched -> Dispatched`, and in the absence of acceptance proof, `NotAccepted -> AcceptanceUnknown` and `NotExpected -> Pending`. |
| `CommandDispatchStopped` | Any unfinished dispatch facet advances to `DispatchStopped`; acceptance, outcome, and cancellation are retained, except when verified `lastObservation = may-have-left` without acceptance proof: then `NotAccepted -> AcceptanceUnknown` and `NotExpected -> Pending`. |

The same `observationId` with the same canonical payload is idempotent and creates no new output. The same ID with a different payload, an unknown/stale handle, or incompatible accepted/rejected proofs are invalid trusted input/an invariant fault and are not applied through arrival-order overwrite. A weaker late observation does not regress proven acceptance/result. A late ACK or provenance-bound `ModuleResult` may refine acceptance/outcome after `DispatchStopped`, but the terminal fact that the current delivery policy is exhausted is retained. The current Checkout protocol version does not implicitly reopen a stopped policy.

For example, a result that arrives before the separate ACK is stored as:

```text
dispatch = Dispatched
acceptance = Accepted
outcome = Succeeded
cancellation = CancellationTooLate
```

A result with accepted-command provenance is proof of acceptance; the state `acceptance = AcceptanceUnknown, outcome = Succeeded` is prohibited.

### 16.4. Authorization

`AuthenticatedActorContext` is issued by the trusted boundary. The Nucleus checks policy in the current state and creates target-scoped requirements.

For payment:

```text
AuthorizationGrant {
    actorContextId
    issuer
    audience = PaymentBall
    action = Payment.Capture
    objectRef = checkout operation / payment method reference
    operationId
    constraints {
        amount = exact total
        currency = snapshot.currency
        paymentMethodRef
    }
    expectedObjectVersion = snapshot.quoteVersion
    issuedAt
    expiresAt
}
```

For the initial actor context, the trusted boundary defines a bounded `actorContextId` unambiguously bound to verified `stableSubjectId + issuer + namespaceOrRealm`; the Flow retains only this binding, not the original principal/session. Every later decision that creates a privileged output receives current authorization as a versioned part of `DecisionContext.authorizationSnapshot`:

```text
CheckoutAuthorizationSnapshot {
    actionGrants: BoundedMap<SemanticHandle, CurrentActionAuthorization>
}

CurrentActionAuthorization {
    grant: AuthorizationGrant
    subjectBindingProof
    issuerProvenance
    contextVersion
    observedAt
}
```

The map is bounded by `maxOutputsPerDecision`, keyed by the exact semantic handle, and is not the current cause of the transition. `subjectBindingProof` is bounded and proves that the current grant actor corresponds to the retained stable subject/issuer/realm; it is neither a credential nor authority by itself. The selected security profile and accepted project overlay declare the trusted issuer/issuance-or-reintroduction boundary, authenticity/integrity mechanism, freshness/revocation policy, and action-specific expiry. For capture, the Nucleus matches the grant against retained `actorBinding`, `paymentMethodRef`, current `cartSnapshot.value.total/currency/quoteVersion`, `operationId`, `paymentHandle`, audience/action/object, and trusted `CurrentActionAuthorization.observedAt`; the target repeats the expiry/revocation check at the actual execution time. Refund/Release/Unlock and other privileged compensation each use a separate entry for their own action handle; the capture grant is neither reused nor broadened.

The Payment Execution Gate accepts a grant only from the profile-declared trusted `issuer`, verifies authenticity/integrity, actor context, audience, action, object, operation, and expiry, and then compares every constrained field with the actual `Payment.Capture` payload fail-closed. Changed `amount`, `currency`, `paymentMethodRef`, or expected version does not inherit the original authorization. Missing, expired, stale-version, or mismatched current authorization before commit creates no privileged output and follows the exact capture/compensation transition in §§16.7/16.10; for compensation that cannot be completed, the terminal outcome is `NeedsManualReconciliation`. At source acceptance, the grant must be valid at trusted `observedAt`; the target checks it again during actual execution. After the command output is committed, its payload and grant are immutable: later expiry/rejection is handled through the existing typed rejected/unknown/reconciliation path, not by ambient refresh or hidden grant substitution.

A raw grant is not a field of live `CheckoutState`. The exact grant inside an accepted command output is retained only in the protected committed output record for the dispatch/redelivery and audit horizon; a durable profile retains the same record under its state guarantee. The grant is redacted from status/log/trace and deleted under the declared security-retention policy after that horizon. Exact cryptographic encoding remains a profile/extension detail; semantic binding is mandatory. The Flow does not pass a general unrestricted principal object.

### 16.5. Cart lock and captured snapshot

First decision:

```text
Idle + currentPulse: CheckoutStarted(
    cartId,
    paymentMethodRef = M,
    expectedCartVersion,
    idempotencyKey
)
with DecisionContext(actorContext = A)

startFingerprint = CheckoutStartFingerprintV1(currentPulse, A)

cartLockHandle = SemanticHandle {
    operationId
    outputKind = Cart.LockForCheckout
    localOrdinalOrName = "cart-lock"
}

-> LockingCart(
       retained.checkoutInput = CapturedCheckoutInput(
           paymentMethodRef = M,
           source = {
               operationId,
               ingressFingerprint = startFingerprint,
               sourceProtocolVersion = Checkout.protocolVersion,
               interactionArtifactVersion = context.artifactVersion
           },
           actorBinding = stableSubjectBinding(A)
       )
   )
+ ModuleCommandRequest {
      semanticHandle = cartLockHandle
      sourceOrdinal = 0
      payload = Cart.LockForCheckout(
          cartId,
          expectedCartVersion
      )
  }
+ ReplyOutput {
      semanticHandle = SemanticHandle {
          operationId
          outputKind = Checkout.RequestAccepted
          localOrdinalOrName = "initial-acceptance"
      }
      sourceOrdinal = 1
      payload = RequestAccepted(operationId)
}
```

The Interaction/Policy Gate requires trusted, valid `A`; the explicit operands `currentPulse + A` define `startFingerprint` without implicit Context capture or a ledger read. The idempotency record with the exact `startFingerprint`, captured input, and both initial outputs belongs to one authoritative acceptance transaction. Repeating the same key/fingerprint returns the existing `OperationId` and does not replace the captured value with another actor/payment binding.

After target acceptance/result, the Flow receives a field-minimized snapshot:

```text
CheckoutCartSnapshot {
    cartId
    cartVersion
    lineItems: productRef + quantity
    quoteId
    quoteVersion
    total
    currency
    expiresAt
}
```

The snapshot contains no profile history, UI draft, or unrelated cart fields. It is immutable and has provenance/version. Cart remains the authority for its state.

### 16.6. Inventory reservation

```text
LockingCart(
    retained.checkoutInput = C
) + currentPulse: CartLocked(snapshot = S)

with currentResultEnvelope metadata {
    authorityActionHandle = cartLockHandle,
    observedViaStepHandle = cartLockHandle,
    authorityProtocolVersion = Cart.LockForCheckout.targetProtocolVersion,
    observedViaProtocolVersion = Cart.LockForCheckout.targetProtocolVersion,
    resultProvenance = R_C
}

verifiedCart = VerifiedStepValue(
    value = S,
    authorityActionHandle = currentResultEnvelope.authorityActionHandle,
    observedViaStepHandle = currentResultEnvelope.observedViaStepHandle,
    authorityProtocolVersion = currentResultEnvelope.authorityProtocolVersion,
    observedViaProtocolVersion = currentResultEnvelope.observedViaProtocolVersion,
    resultProvenance = currentResultEnvelope.resultProvenance
)

inventoryHandle = SemanticHandle {
    operationId
    outputKind = Inventory.Reserve
    localOrdinalOrName = "inventory-reservation"
}

-> ReservingInventory(
       cartSnapshot = verifiedCart,
       retained.checkoutInput = C
   )
+ ModuleCommandRequest {
      semanticHandle = inventoryHandle
      sourceOrdinal = 0
      payload = Inventory.Reserve(
          items = S.lineItems,
          operationId
      )
  }
```

The verified `CartLocked` envelope must match `cartLockHandle` and the version-pinned Cart contract; the provenance-bound snapshot, preserved checkout input, and Inventory output are accepted in one frame. Duplicate delivery uses the same command identity/idempotency identity. The Flow does not create a new logical reservation for every transport retry.

### 16.7. Payment capture

After inventory succeeds:

```text
ReservingInventory(
    cartSnapshot = RS,
    retained.checkoutInput = C
) + currentPulse: InventoryReserved(reservationRef = I)

with currentResultEnvelope metadata {
    authorityActionHandle = inventoryHandle,
    observedViaStepHandle = inventoryHandle,
    authorityProtocolVersion = Inventory.Reserve.targetProtocolVersion,
    observedViaProtocolVersion = Inventory.Reserve.targetProtocolVersion,
    resultProvenance = R_I
}

S = RS.value

verifiedInventory = VerifiedStepValue(
    value = I,
    authorityActionHandle = currentResultEnvelope.authorityActionHandle,
    observedViaStepHandle = currentResultEnvelope.observedViaStepHandle,
    authorityProtocolVersion = currentResultEnvelope.authorityProtocolVersion,
    observedViaProtocolVersion = currentResultEnvelope.observedViaProtocolVersion,
    resultProvenance = currentResultEnvelope.resultProvenance
)

paymentHandle = SemanticHandle {
    operationId
    outputKind = Payment.Capture
    localOrdinalOrName = "payment-capture"
}

captureAuthorization =
    context.authorizationSnapshot.actionGrants[paymentHandle]

-> CapturingPayment(
       cartSnapshot = RS,
       retained.checkoutInput = C,
       retained.inventoryReservation = verifiedInventory
   )
+ ModuleCommandRequest {
      semanticHandle = paymentHandle
      sourceOrdinal = 0
      payload = Payment.Capture(
          amount = S.total,
          currency = S.currency,
          paymentMethodRef = C.paymentMethodRef,
          idempotencyKey = operationId / "capture",
          grant = captureAuthorization.grant
      )
  }
```

Before acceptance, the Nucleus verifies the exact result handle/versions/provenance, equality of the actor/method/amount/currency/version constraints in §16.4, and grant validity at trusted `captureAuthorization.observedAt`; the Execution Gate repeats the expiry/revocation check during actual execution. `verifiedInventory`, next state, and the immutable capture output are accepted in one frame. Neither M, I, nor the grant is read from a prior Intent, participant/runtime ledger, or outbox.

If `captureAuthorization` is missing/expired/stale/mismatched, the current Checkout version does not wait for an ambient refresh or repeat the same result as a new cause. The same Decision retains `verifiedInventory`, creates no `Payment.Capture`, and advances to `Compensating`. Valid current Release/Unlock authorizations from the same context may atomically create the outputs in §16.10; an absent fallback grant is not replaced with a credential and records the residual action as `NeedsManualReconciliation`. After demonstrably successful Release/Unlock, the terminal outcome is `RejectedBeforeExternalCommitment`; an unknown/failed residual follows the ordinary compensation/reconciliation policy. A duplicate of the same `InventoryReserved` does not return the Flow to the capture path afterward.

A payment result may arrive:

- after the ACK;
- before the separate ACK, but with accepted-command provenance;
- as `OutcomeUnknown` after a provider timeout.

The Flow does not treat the absence of an ACK as proof of nondelivery if the command might already have been accepted.

### 16.8. Unknown payment outcome

The two unknown cases are not conflated.

If delivery might have left the source but target acceptance has not yet been proven:

```text
payment.dispatch = Dispatched
payment.acceptance = AcceptanceUnknown
payment.outcome = Pending
phase = ReconcilingPaymentAcceptance
```

The cause of this transition is `CheckoutCommandDeliveryObserved(CommandDeliveryAmbiguous)` for the matching `paymentHandle`, not a source commit or direct runtime write. `CommandDispatchStopped` whose retained `lastObservation` also proves "might have left the source" without acceptance proof retains the stop facet and starts the same reconciliation path. The `steps.paymentReconciliation` field ensures that duplicates or multiple ambiguous attempts do not create a second status command with a different semantic identity.

If the target accepted the command and returned accepted-command proof, but the provider outcome was lost:

```text
payment.dispatch = Dispatched
payment.acceptance = Accepted
payment.outcome = OutcomeUnknown
phase = ReconcilingPaymentOutcome
```

The second form cannot degrade back to `AcceptanceUnknown`: target acceptance has already been proven. In both phases, the Flow creates a declared command with a new identity for the status step while preserving the identity of the original capture:

```text
statusHandle = SemanticHandle {
    operationId
    outputKind = Payment.GetOperationStatus
    localOrdinalOrName = "payment-status"
}

ModuleCommandRequest {
    semanticHandle = statusHandle
    sourceOrdinal = 0
    payload = Payment.GetOperationStatus(
        originalPaymentHandle = paymentHandle,
        originalProviderIdempotencyKey = operationId / "capture"
    )
}
```

It MUST NOT:

- create a new capture identity;
- treat a timeout as permanent failure;
- immediately release inventory and permit another checkout;
- start a refund before establishing whether capture occurred, unless the provider contract makes this safe.

Possible reconciliation results:

```text
Captured(acceptedCommandProof, paymentRef)
DefinitelyNotCaptured(acceptanceEvidence)
StillUnknown(acceptanceFacet, evidence)
```

`Captured` immediately records `acceptance = Accepted, outcome = Succeeded`. `DefinitelyNotCaptured` distinguishes `RejectedBeforeAcceptance` from an accepted target command with provider-level `Rejected/Failed`. `StillUnknown` preserves the supplied acceptance facet and does not collapse `AcceptanceUnknown + Pending` into `Accepted + OutcomeUnknown` without proof.

`Captured(..., paymentRef)` does not leave the reference only inside the reconciliation Pulse: after status-result provenance is verified, it follows the same transition that retains `VerifiedStepValue<PaymentRef>` and creates the Order output as the direct `PaymentCaptured` below.

### 16.9. Order confirmation

After proven capture:

```text
CapturingPayment(
    cartSnapshot = RS,
    retained.checkoutInput = C,
    retained.inventoryReservation = RI
) + currentPulse: PaymentCaptured(paymentRef = P)

with currentResultEnvelope metadata {
    authorityActionHandle = paymentHandle,
    observedViaStepHandle = paymentHandle,
    authorityProtocolVersion = Payment.Capture.targetProtocolVersion,
    observedViaProtocolVersion = Payment.Capture.targetProtocolVersion,
    resultProvenance = R_P
}

or

ReconcilingPaymentAcceptance | ReconcilingPaymentOutcome(
    cartSnapshot = RS,
    retained.checkoutInput = C,
    retained.inventoryReservation = RI
) + currentPulse: Captured(acceptedCommandProof, paymentRef = P)

with currentResultEnvelope metadata {
    authorityActionHandle = paymentHandle from acceptedCommandProof,
    observedViaStepHandle = statusHandle,
    authorityProtocolVersion = Payment.Capture.targetProtocolVersion,
    observedViaProtocolVersion = Payment.GetOperationStatus.targetProtocolVersion,
    observedResultProvenance = R_STATUS,
    resultProvenance = normalizeOriginalActionProof(
        acceptedCommandProof,
        R_STATUS
    )
}

S = RS.value

verifiedPayment = VerifiedStepValue(
    value = P,
    authorityActionHandle = currentResultEnvelope.authorityActionHandle,
    observedViaStepHandle = currentResultEnvelope.observedViaStepHandle,
    authorityProtocolVersion = currentResultEnvelope.authorityProtocolVersion,
    observedViaProtocolVersion = currentResultEnvelope.observedViaProtocolVersion,
    resultProvenance = currentResultEnvelope.resultProvenance
)

orderHandle = SemanticHandle {
    operationId
    outputKind = Order.Confirm
    localOrdinalOrName = "order-confirmation"
}

-> ConfirmingOrder(
       cartSnapshot = RS,
       retained.checkoutInput = C,
       retained.inventoryReservation = RI,
       retained.paymentCapture = verifiedPayment
   )
+ ModuleCommandRequest {
      semanticHandle = orderHandle
      sourceOrdinal = 0
      payload = Order.Confirm(
          cartSnapshot = S,
          inventoryReservationRef = RI.value,
          paymentRef = verifiedPayment.value
      )
  }
```

The direct result must be provenance-bound to `paymentHandle`; the reconciliation result must demonstrably describe the same authority action/provider idempotency key, but retains `observedViaStepHandle = statusHandle`. Independent valid proof of the same P through another declared route is corroboration: it creates no second `Order.Confirm` and does not overwrite the accepted value; another P/authority version or a conflicting original-action proof fails closed. `verifiedPayment`, next state, and the Order output are accepted in one frame. The Order target rechecks its own invariants and expected references. The Flow cannot force Order to accept invalid state merely because the preceding steps succeeded.

### 16.10. Compensation

If order confirmation is definitively rejected after payment capture, the Flow creates new operations:

```text
SemanticHandle(operationId, Payment.Refund, "payment-refund")
SemanticHandle(operationId, Inventory.Release, "inventory-release")
SemanticHandle(operationId, Cart.Unlock, "cart-unlock")
```

Target values are derived only from committed Checkout state:

```text
Payment.Refund target
    = {
        paymentRef = state.retained.paymentCapture.value,
        originalPaymentHandle = state.steps.paymentCapture.handle,
        operationId = state.operationId
      }

Inventory.Release target
    = {
        reservationRef = state.retained.inventoryReservation.value,
        originalReservationHandle = state.steps.inventoryReservation.handle,
        operationId = state.operationId
      }

Cart.Unlock target
    = {
        cartId = state.cartSnapshot.value.cartId,
        originalLockHandle = state.steps.cartLock.handle,
        operationId = state.operationId
      }
```

Every compensation command receives a `semanticHandle` from the closed slot shown, an `idempotencyKey` from that handle/operation, a deadline from the retained workflow deadline plus trusted current context, and `CurrentActionAuthorization` for the exact compensation handle. The participant Execution Gate repeats the action/target/operation/expiry checks. If a concrete target contract permits compensation only by original semantic handle instead of P/I, that handle-based target form must be versioned and declared by the participant contract itself; the Flow does not assume it silently. The Capture grant is not used for Refund/Release/Unlock.

Every compensation has:

- a new complete `SemanticHandle` and its own `ModuleCommandRequest`/zero-based `sourceOrdinal`;
- its own idempotency key;
- deadline;
- authorization/capability;
- an outcome and `OutcomeUnknown` path.

The Order rejection/result and retained P/I/cart values are accepted even if compensation authorization is absent; the compensation steps created and the complete available output batch are changed by the same accepted frame. A crash cannot leave a dependent output without its source value or clear a value before its last consumer. Missing/expired compensation authorization does not trigger ambient credential fallback: no action is created, the residual target remains in state, and the terminal outcome becomes `NeedsManualReconciliation`. Compensation is not rollback. It may partially fail.

Terminal outcomes may be:

```text
Completed(orderId)
RejectedBeforeExternalCommitment
FailedCompensated
FailedPartiallyCompensated
NeedsManualReconciliation
CancelledBeforeCommitment
CancelledWithResidualEffects
```

### 16.11. Cancellation race

Customer cancellation passes parsing/authentication/validation in Interaction and arrives during payment as an owned `Intent`:

```text
CancellationRequested(generation = 1)
```

The Flow records the request and sends a declared target command:

```text
paymentCancellationHandle = SemanticHandle {
    operationId
    outputKind = Payment.CancelCapture
    localOrdinalOrName = "payment-cancellation"
}

ModuleCommandRequest {
    semanticHandle = paymentCancellationHandle
    sourceOrdinal = 0
    payload = Payment.CancelCapture(targetHandle = paymentHandle, generation = 1)
}
```

If the target contract treats cancellation as a privileged action, its output uses a separate current grant for `paymentCancellationHandle`; the capture grant is not reused or stored in live state.

One possible sequence:

```text
PaymentCaptured result
CancellationTooLate
```

`PaymentCaptured` contains accepted-command proof, so under the same §16.9 rule the Flow atomically retains provenance-bound `retained.paymentCapture`, records `payment.acceptance = Accepted`, `payment.outcome = Succeeded`, and cancellation `CancellationTooLate`; the result is not discarded because of a local cancel flag. The declared policy then applies: continue the order or create a new `Payment.Refund` handle whose target comes from retained P and whose current grant is keyed by the refund handle. The cancellation branch does not keep P only in the current Pulse.

Another sequence is also possible:

```text
CancellationAcceptedBeforeStart
```

Then payment is not performed, and inventory/cart may be safely released.

### 16.12. Commit and runtime identity

Flow state stores:

```text
SemanticHandle(operationId, Payment.Capture, "payment-capture")
SemanticHandle(operationId, Inventory.Reserve, "inventory-reservation")
```

The runtime ledger stores:

```text
SemanticHandle(operationId, Payment.Capture, "payment-capture")
    -> OutputId(out-payment-...)
SemanticHandle(operationId, Inventory.Reserve, "inventory-reservation")
    -> OutputId(out-inventory-...)
```

Every source Decision atomically accepts:

```text
next workflow state
new EffectRequest/ModuleCommandRequest outputs
idempotency markers
operation status changes
```

If the current Pulse introduces a decision-relevant M/S/I/P value for the first time, its `CapturedCheckoutInput`/`VerifiedStepValue` and every new output that depends on it belong to the same accepted frame. No crash point can publish an output without the retained source value or retain the value without the complete output batch. The protected output record retains the exact immutable scoped grant with which the output was accepted; live state stores only the actor/value lineage from §§16.3–16.4.

Runtime then dispatches the outputs. This removes the dependency of domain state on commit-time infrastructure IDs.

The dispatch/ACK result is not written back into this commit retroactively. Runtime stores mechanical attempt evidence and returns a typed `CheckoutCommandDeliveryObserved` with the original `SemanticHandle`; only the next serialized Decision may change the corresponding `Step` and create a reconciliation output if needed.

### 16.13. Operation status and reply

Initial reply:

```text
ReplyOutput(
    semanticHandle = SemanticHandle(operationId, Checkout.RequestAccepted, "initial-acceptance"),
    sourceOrdinal = 1,
    payload = RequestAccepted(operationId)
)
```

is not a terminal business result. The client obtains terminal status through:

```text
GetCheckoutStatus(operationId)
```

The owned result payload is closed:

```text
CheckoutProgress =
    LockingCart
  | ReservingInventory
  | CapturingPayment
  | ReconcilingPaymentAcceptance
  | ReconcilingPaymentOutcome
  | ConfirmingOrder
  | Compensating

CheckoutCancellationFacet =
    NotRequested
  | Requested
  | CancellationAcceptedBeforeStart
  | CancellationAcceptedInProgress
  | CancellationTooLate
  | CancellationRejected
  | CancellationUnknown

CheckoutTerminalOutcome =
    Completed(orderId)
  | RejectedBeforeExternalCommitment
  | FailedCompensated
  | FailedPartiallyCompensated
  | NeedsManualReconciliation
  | CancelledBeforeCommitment
  | CancelledWithResidualEffects

CheckoutLifecycle =
    Accepted
  | InProgress(progress: CheckoutProgress)
  | Completed(orderId)
  | RejectedBeforeAcceptance
  | Rejected(outcome: RejectedBeforeExternalCommitment)
  | Failed(outcome: FailedCompensated | FailedPartiallyCompensated | NeedsManualReconciliation)
  | Cancelled(outcome: CancelledBeforeCommitment | CancelledWithResidualEffects)
  | OutcomeUnknown(progress: CheckoutProgress)

CheckoutDispatchStop {
    semanticHandle: SemanticHandle
    reason
    attempts
    lastObservation
}

CheckoutDispatchFacet =
    NoDispatchStopped
  | OneOrMoreDispatchStopped(
        stops: BoundedSequence<CheckoutDispatchStop>
    )

CheckoutStatus =
    CheckoutNotFound(operationId)
  | CheckoutExpiredFromStatusRetention(operationId)
  | CheckoutKnownStatus(
        operationId,
        lifecycle: CheckoutLifecycle,
        cancellation: CheckoutCancellationFacet,
        dispatch: CheckoutDispatchFacet
    )
```

`CheckoutKnownStatus` retains independent lifecycle, cancellation, and dispatch facets. `Accepted` means an accepted Flow operation before entry into the first participant phase. `InProgress` and `OutcomeUnknown` retain the current `CheckoutProgress`. Terminal mapping is total: `Completed(orderId)` maps to `Completed`; `RejectedBeforeExternalCommitment` to `Rejected`; the three failure outcomes to `Failed`; and the two cancellation outcomes to `Cancelled`. Root `RejectedBeforeAcceptance` is projected only from the authoritative rejected Checkout acceptance record; nonacceptance of a participant command remains in the matching `Step.acceptance` and does not turn an already accepted Flow operation into a root rejection.

`NoDispatchStopped` means the empty set. `OneOrMoreDispatchStopped.stops` is nonempty, contains no more than the manifest's `maxRetainedDispatchStops` records, and has a unique complete `semanticHandle`; this tighter bound also satisfies the general `maxCollectionItems`. For the current protocol version, serialization follows the fixed order of ten stop-eligible source-output slots: `cartLock`, `initialAcceptanceReply`, `inventoryReservation`, `paymentCapture`, `paymentCancellation`, `paymentReconciliation`, `orderConfirmation`, `inventoryRelease`, `paymentRefund`, `cartUnlock`; arrival order does not affect the result. `initialAcceptanceReply` means the exact handle `SemanticHandle(operationId, Checkout.RequestAccepted, "initial-acceptance")` from §16.5; the remaining slots correspond to the `steps` fields in §16.3.

Before acceptance of any Decision that creates a stop-eligible `SemanticOutput` for the first time, the source/status capacity plan reserves a record slot for every new unique handle; the initial Decision in §16.5 reserves two slots—`cartLock` and `initialAcceptanceReply`. The number of exposed handles over the operation's lifetime cannot exceed either declared bound; `N+1` rejects the entire Decision before source acceptance and dispatch. After acceptance, a valid terminal observation cannot be lost because of materialization pressure: it remains retained/pending until applied by the status authority, which may honestly lag with its own stamp but may not evict/truncate the record. The status materializer handles a capacity/byte failure as typed backpressure/operational fault with retry within the profile policy, not as grounds to change an already accepted semantic fact.

Merging the same terminal observation is idempotent. For the current delivery policy, a nonequivalent second stop record with the same `semanticHandle` is an invariant fault and does not overwrite the first by arrival order; there is no implicit reopening of this policy. A record for another handle is added without deleting existing records. Stopped records are retained until the declared status-retention transition and may coexist with any compatible lifecycle/cancellation state; they do not turn it into `Failed` or `OutcomeUnknown`.

For this query, the declared operation status authority is committed `CheckoutStatusAuthority` with its own revision in an authenticated namespace. It materializes Flow status changes and trusted runtime delivery observations without becoming the workflow's command authority. Its records transition as follows:

```text
absent + accepted/rejected record     -> CheckoutKnownStatus
CheckoutKnownStatus + workflow commit -> updated CheckoutKnownStatus
CheckoutKnownStatus + trusted terminal delivery observation
    -> same lifecycle/cancellation + idempotent merge by semanticHandle
delivery observation before its causal accepted/workflow record
    -> bounded pending by operationId/semanticHandle; no lossy status commit
causal record + pending observations
    -> one CheckoutKnownStatus update with canonical stop merge
CheckoutKnownStatus + covered sources + no pending + retention expiry
    -> CheckoutExpiredFromStatusRetention marker
expired marker + observation at/before covered source position
    -> unchanged expired marker
expired marker + marker-horizon expiry -> absent / CheckoutNotFound
```

A delivery observation causally references a previously committed stop-eligible source output. For a command Step, the same runtime fact enters the Flow through `CheckoutCommandDeliveryObserved(CommandDispatchStopped)` and the status authority through its declared typed delivery-observation input. For `initialAcceptanceReply`, only the status-authority path applies: its trusted `ControlPulse` under §6.11 is bound to the exact committed `ReplyOutput` source tuple and does not masquerade as a command Pulse or business result because reply delivery does not change `CheckoutState`. The concrete status-authority protocol/origin binding is recorded in the project overlay.

The materializer either applies sources in causal order or retains an out-of-order observation in bounded pending state; the source record/observation remains retained under the selected profile policy until application. For `Transient`, this promise ends with the process lifetime; durable retention requires a `SnapshotOutbox`/`EventJournal` binding and declared horizon. Per-operation pending stop keys are bounded by `maxRetainedDispatchStops`; the global queue/backpressure cap is defined by the concrete binding. A retention marker may be committed only after declared source positions/horizons covering the operation and with an empty pending set. Therefore, a late duplicate does not resurrect an expired operation, and an accepted observation is not lost in a race with retention within the stated profile guarantee.

`GetCheckoutStatus -> CheckoutStatus` returns as `ReadResult<CheckoutStatus>` with the stamp of the exact committed `CheckoutStatusAuthority` snapshot for all three outer variants. Therefore, `NotFound` and expiry do not require an invented snapshot of an absent `CheckoutFlowBall` instance. The stamp does not promise that materialized status has already caught up with every source. A Query is not a `ReplyOutput`/`ProjectionOutput` and creates no new commit. A live/durable reply channel for the concrete profile remains an alternative. Loss of an HTTP connection does not delete an accepted operation under a durable profile.

### 16.14. What the example demonstrates

- A Flow appears because of independent coordination authority, not because of every inter-module call.
- Feature Balls preserve local invariants.
- Captured input is minimal and versioned.
- The Checkout ingress fingerprint has one explicit actor/namespace-scoped derivation from the current Pulse + trusted Context, excludes key/transport metadata, and is retained equal to the atomic Interaction record.
- Values from prior ingress/results needed by a later Decision are retained as bounded provenance-bound workflow values until the last consumer; runtime/participant history is not decision input.
- A privileged output receives a current action-scoped grant through a declared trusted versioned context; a raw grant/principal does not live in Flow state, while the exact committed grant is protected inside the output record.
- ACK, acceptance, result, cancellation, and unknown are not conflated.
- Compensation is new fallible work, not rewind.
- Idempotency preserves logical identity across retries.
- Runtime delivery identity is separate from semantic workflow state.
- Post-commit dispatch/ACK/ambiguity changes Flow state only through a declared trusted `ControlPulse`.
- The status authority honestly distinguishes a known, absent, and retention-expired operation, retains all bounded stopped output/step handles, and does not conflate delivery exhaustion with business outcome.

---

## 17. Testing, review, and operational verification

### 17.1. Transition tests

The Nucleus is tested without mocks of external framework objects:

```text
Given committed State
And Pulse
And DecisionContext
Expect Accepted or BusinessRejection
Expect next State
Expect ordered SemanticOutputs
```

Tests cover:

- every protocol variant;
- state invariants;
- stale results;
- duplicate inputs;
- every cancellation outcome and applicable observation order: `AcceptedInProgress -> ProductsFound|ProductSearchFailed` and `ProductsFound|ProductSearchFailed -> AcceptedInProgress` converge on the same `Ready|Failed(AcceptedInProgress)`; `AcceptedInProgress -> ProductSearchCancelled` and `ProductSearchCancelled -> late AcceptedInProgress` converge on `Cancelled(AcceptedInProgress)` without duplicate output; mutually exclusive terminal result/cancellation proofs in both orders retain the first frame, while the second proof accepts no Decision/state/output and follows the invariant/provenance fault policy;
- deadline transitions;
- output-limit overflow;
- exact `N+1` synchronous completion trace at causal-budget exhaustion, with no dropped Fact or hidden continuation;
- full operation-facet invariants, including the result-proof transition to `Accepted`;
- every declared post-commit delivery `ControlPulse`: exact committed source tuple/provenance, dispatch, accepted/rejected ACK, ambiguity, and terminal stop; an exact duplicate is idempotent, a stale/weaker observation does not regress proof, and conflicting evidence is not overwritten by arrival order;
- for every outgoing payload field, exact derivation from committed State, the current Pulse, or a declared versioned `DecisionContext`; a prior Intent/result, outbox, participant/runtime ledger, and ambient memory are not read;
- Checkout initial acceptance: the retained `ingressFingerprint` is exactly equal to the atomic Interaction record; the same typed `CheckoutStarted` for two stable subjects or two issuer/realm scopes produces different fingerprints, while changing only `idempotencyKey`/RequestId/trace/reply metadata preserves the fingerprint;
- Checkout M→S→I→P transitions: initial M/actor binding, cart S, I, and direct/reconciled P are retained with exact correlation/authority+observation handles/versions/provenance in the same Decision that creates the dependent output; the same P through a second valid route corroborates without a second Order output, while conflicting P/original proof fails closed;
- invalid DecisionContext;
- terminal states.

Local read tests are run separately from transition tests. For every `Query`, they verify the single declared result payload, exact correspondence of `ConsistencyStamp` to the source `CommittedStateSnapshot`, a deterministic result for identical snapshot/query/context, and the absence of a `Decision`, mutation, new `CommitRevision`, `SemanticHandle`, or `SemanticOutput`. For operation status, they additionally and exhaustively verify `NotFound`, all known lifecycle variants, `OutcomeUnknown`, zero/one/multiple independent `DispatchStopped` records, the retention transition `Known -> Expired marker -> NotFound`, and a stamp of the status-authority snapshot specifically. Checkout separately tests a reply-only stop, `initialAcceptanceReply` plus all nine command stops, different arrival orders, duplicate/conflict, and capacity `10/11`; the result uses one canonically ordered `semanticHandle` set with no silent eviction/truncation, while reason/evidence remains typed, bounded, and redacted. Materializer tests permute the causal accepted/workflow/output record and delivery stop, verify bounded pending merge, and test the stop/retention race: a covered late duplicate does not resurrect the expired marker.

### 17.2. Property-based tests

Useful properties:

```text
state invariant preserved after every Accepted Decision
outputs <= declared bounds
forbidden actor produces no privileged output
stale generation cannot replace current generation
same state+pulse+context+artifact gives same Decision
rejected input does not change state
fault does not publish partial output
compensation never reuses original action identity
every example output maps to one canonical SemanticOutput envelope and full SemanticHandle
every outgoing payload field has one explicit State | current Pulse | declared DecisionContext lineage
CheckoutStartFingerprintV1(P, A1) != CheckoutStartFingerprintV1(P, A2) for different stable subject/issuer/realm scope
CheckoutStartFingerprintV1(P with key/transport metadata X, A) = CheckoutStartFingerprintV1(P with key/transport metadata Y, A)
retained Checkout ingress fingerprint = atomically accepted Interaction idempotency-record fingerprint
retained workflow value is not cleared while a dependent transition remains reachable
same authority action cannot bind different retained value/authority version/conflicting proof; alternate valid observation of the same value only corroborates
nonterminal cancellation acceptance commutes with a legitimate matching result and preserves its lifecycle in both observation orders
terminal cancellation proof subsumes a delayed accepted-in-progress observation; a later weaker proof does not change terminal state or publish duplicate output
mutually exclusive terminal result/cancellation proofs never overwrite an accepted terminal frame in either arrival order
```

For a state machine, generating a sequence of Pulses is useful, not only individual examples.

### 17.3. Resource contract tests

Every adapter is tested separately:

- safe sink usage;
- capability enforcement;
- response schema validation;
- timeout/cancellation behavior;
- idempotency key propagation;
- retry ownership;
- multiplicative retry composition: one failure mode does not receive active retries simultaneously in SDK/adapter/runtime/Flow;
- response-size and decompression bounds;
- mapping external errors into typed Facts;
- mapping every cancellation executor outcome into a closed `Fact`/`ModuleResult` or declared trusted `ControlPulse` variant according to origin;
- `OutcomeUnknown` at ambiguous boundaries;
- secret redaction.

### 17.4. Interaction tests

Verify:

- parser rejection;
- invalid boundary input returns `BoundaryResponse` but creates no Pulse, Decision, CommitRevision, SemanticHandle, or committed ReplyOutput;
- normalization policy;
- duplicate/unknown field behavior where relevant;
- authentication context source;
- Checkout uses one selected `CheckoutStartFingerprintV1` in Interaction and transition: same key + same fingerprint returns the prior `OperationId`, same key + semantic/actor-scope mismatch returns `IdempotencyConflict`, while key/RequestId/trace/reply changes do not themselves change the fingerprint;
- external mutation/cancellation after validation creates exactly one declared `Intent`, not a `ControlPulse`;
- a successful Query response matches the declared result mapping, returns canonical `ReadResult`/`ConsistencyStamp`, and creates no commit identity;
- operation status returns absent/expired/known cases within the declared closed result payload, not through transport 404, `BoundaryResponse`, or an undeclared second result type;
- output encoding/escaping;
- request and reply correlation;
- absence of raw framework objects inside Nucleus protocol.

### 17.5. Architecture tests

CI SHOULD verify:

```text
no interaction -> resources import
no resources -> interaction import
no nucleus -> platform/I/O import
no direct foreign state access
every downstream output value is present in committed State, current Pulse or declared versioned DecisionContext; no free value or decision read from inbox/outbox/runtime/participant history
every prior-Pulse value needed after a crash is retained with bounded type, source handle/correlation, protocol/schema version, verified provenance and last-consumer retention
every retained ingress fingerprint equals the atomically accepted idempotency-record fingerprint from one declared versioned function over explicit Pulse/Context operands; key and transport metadata are excluded
persisted state-shape change increments stateSchemaVersion without silently changing owned/imported protocolVersion
no feature internals import
no protocol re-export
no raw external request -> ControlPulse path; every external mutation/cancellation enters through one declared Intent after Interaction validation
every post-commit runtime/route observation that changes Sovereign State resolves to one declared typed ControlPulse, exact previously committed source tuple and trusted provenance, then passes through single-writer decide; source commit and direct runtime state write cannot materialize Dispatched/ACK/ambiguity/DispatchStopped facets
closed protocol exhaustiveness
every non-empty Ball-owned protocol category resolves each used variant exactly once through an inline declaration or one version-pinned authoritative reference; omitted categories are empty for FeatureBall and FlowBall alike
every owned Query resolves exactly one result payload for the same protocolVersion; successful reads use canonical ReadResult/ConsistencyStamp and create no Decision, CommitRevision, SemanticHandle or SemanticOutput
every operation-status result payload exhaustively distinguishes NotFound, Accepted, InProgress, Completed, RejectedBeforeAcceptance, Rejected, Failed, Cancelled, OutcomeUnknown, zero-to-bounded-many DispatchStopped records keyed by SemanticHandle and ExpiredFromStatusRetention; absence/expiry is derived from a stamped declared status-authority snapshot
for Checkout, the stopped-handle universe includes the initial RequestAccepted ReplyOutput and all nine command Step handles; cap/order/reservation/materialization and 10/11 tests cover the same exact set
every imported ModuleCommand/ModuleResult resolves through exactly one declared dependency whose targetProtocolVersion matches Assembly.consumerProtocolVersion; imported target types are not redeclared as caller-owned
every example output maps to the canonical envelope/payload algebra; no orphan EffectIntent/ModuleCommandIntent/CommandId
direct import/synchronous/control graphs are acyclic
async feedback is tested separately for owner, identity, finite budget and escape condition
every inter-Ball edge has ReadDependency, DeclaredCommandDependency, DeclaredSignalDependency or FlowParticipation
every produced command/read/effect has a declared route or private capability binding
every mandatory §8.3 limit and every applicable delivery cap is present; zero is accepted only for an absent EffectRequest/ModuleCommandRequest kind or disabled retries as defined by §8.3
unsafe effect registry
foundation domain-type quarantine
```

An import linter result does not prove semantic ownership or security isolation. It proves only the verifiable structural property.

### 17.6. Concurrent profile tests

For `BoundedConcurrent`, add:

- out-of-order Facts/Results;
- mailbox full/backpressure;
- concurrent duplicate ingress;
- cancel versus completion;
- timeout versus late result;
- worker crash;
- bounded causal chain;
- starvation and priority behavior, if claimed.

For `Transient`, fault injection verifies both sides of the single publication point:

- failure during reservation/materialization before publication makes neither state nor any output visible;
- failure immediately after atomic publication but before the first dispatch leaves the complete retained `AcceptedDecisionFrame` visible, from which the entire undelivered batch can continue under the profile policy;
- failure between dispatch of two outputs neither rolls back state/the first output nor loses the retained record of the second;
- no separate fallible state-publication-then-enqueue window exists.

### 17.7. Durable-state profile tests

For `SnapshotOutbox`/`EventJournal`, add these crash points:

```text
before transaction
inside transaction
commit acknowledged but response lost
before dispatch
request sent but ACK lost
after target acceptance before source update
post-commit delivery observation before/after matching ControlPulse commit
before/after result commit
recovery with pending outbox
recovery after accepted NoDomainChange with and without outputs
delivery-attempt exhaustion persisted as DispatchStopped
initial ReplyOutput delivery exhaustion materialized by status authority without mutating workflow state
Checkout recovery after accepted CheckoutStarted/CartLocked before InventoryReserved
Checkout recovery after accepted CheckoutStarted preserves exact equality of retained ingress fingerprint and atomic idempotency record
Checkout recovery after accepted InventoryReserved before PaymentCaptured
Checkout recovery after direct or reconciled PaymentCaptured before Order result
Checkout recovery after Order rejection before/between Refund, Release and Unlock
```

Minimum properties:

- no dispatch before source commit;
- no state without corresponding durable output record;
- no outbox regeneration by rerunning transition;
- accepted `NoDomainChange` restores the same CommitRevision/acceptance marker/output batch and deduplicates repeated input;
- crash-before-dispatch does not create `Dispatched`; recovered dispatch/ACK/ambiguity/exhaustion changes Sovereign State only through a declared typed Pulse, and a duplicate observation does not create a second reconciliation output;
- the initial reply stop and command-step stops are materialized over one bounded `SemanticHandle` universe; a reply observation does not masquerade as a command Pulse and does not change `CheckoutState`;
- status materialization does not lose a stop that arrived before its causal workflow record and does not replace a retention marker with a late duplicate after the covered source position;
- SnapshotOutbox/EventJournal restore exact retained M/S/I/P values and provenance without rerunning `decide` or reading runtime/participant history; Transient explicitly does not promise this after process loss;
- after a crash, `InventoryReserved(I)` creates the exact Capture from retained M + S + a current valid action grant, direct/reconciled `PaymentCaptured(P)` creates the exact Confirm from retained S/I + P, and order rejection creates Refund/Release/Unlock only for retained P/I/cart;
- the same authority action with a conflicting value/version/proof, premature cleanup, and wrong-target compensation fail closed; cleanup after the last terminal consumer survives repeated recovery;
- the complete retained C/RS/RI/RP frame satisfies the exact `maxStateBytes` boundary N, while an N+1 oversized value rejects the entire Decision before state/output acceptance without truncation;
- active Checkout v1 state without authoritative M/S/I/P migration evidence receives no synthesized defaults and does not enter normal v2 `decide`; the binding proves migration or quarantine/manual routing;
- a schema-v2 Checkout record from a prior transition artifact does not enter normal recovery based only on matching shape: the retained ingress fingerprint is verified against the authoritative accepted record/declared migration, otherwise the operation is quarantined; the normal Nucleus does not read the ledger;
- duplicate ingress returns same logical operation;
- stale owner cannot commit if ownership is movable;
- ambiguous external outcome remains unknown until reconciled.

### 17.8. Security tests

For privileged paths:

- missing/expired/wrong-audience grant;
- forged/tampered grant and untrusted/wrong issuer;
- substitution of amount/currency/object/operation or another constrained command field after authorization;
- wrong retained actor binding, payment method, action handle, context version, quote version or grant validity across async delay/recovery;
- a rotated/expired actor-context reference requires a current `subjectBindingProof` for the same stable subject/issuer/realm; possession of the digest/ID without proof does not authorize the action;
- Capture/Cancel/Refund/Release/Unlock receive separate current action grants; the capture grant is not reused, and missing fallback authorization leads to a typed residual/manual outcome with no privileged output;
- raw grant/session/principal is absent from live workflow state, status, Projection, and ordinary telemetry; the exact committed grant remains only in the protected/redacted output record until the declared dispatch/audit horizon;
- insufficient actor assurance;
- revoked capability;
- target object version changed;
- endpoint/credential scope too broad;
- SSRF/path traversal/shell/raw SQL attempts;
- secret leakage in logs, projections and exceptions;
- authenticated resource exhaustion.

### 17.9. Benchmarks

The performance report records:

```text
hardware and OS
language/runtime/compiler version
optimization flags
allocator/GC mode
payload sizes and distribution
warmup and sample method
instrumentation overhead
profile combination
latency distribution
allocation/copy counts
```

At minimum, the following SHOULD be compared:

```text
direct hand-written call
Pokeball Inline binding
concurrent binding where applicable
existing framework baseline
```

Do not claim "zero overhead" based on one microbenchmark without a realistic payload and build settings.

### 17.10. Observability

Recommended causal fields:

```text
ballType
ballInstanceId
commitRevision
pulseType
semanticHandle
operationId
outputId when available
stateRevisionBefore/After
attemptId
outcome
latency
traceId
```

An operational trace, security audit, and exact replay data are distinct artifacts. A redacted trace does not guarantee replay.

---

## 18. Practical checklist

### 18.1. Core Ball

- [ ] The `Ball` boundary is justified by invariants, lifecycle, authority, and `StateKey`.
- [ ] Interaction, Nucleus, and Resources are logically separated.
- [ ] The Nucleus imports no platform/I/O SDK.
- [ ] Protocol variants are closed and typed.
- [ ] The manifest for the selected `protocolVersion` resolves every used owned variant through exactly one inline declaration or version-pinned authoritative reference; an absent category is genuinely empty for both FeatureBall and FlowBall.
- [ ] Every owned `Query` maps unambiguously to one result payload; a successful response is a `ReadResult` with the stamp of the source snapshot and creates no `Decision`, new `CommitRevision`, `SemanticHandle`, or `SemanticOutput`.
- [ ] The status payload distinguishes the entire §9.11 minimum as a closed algebra; `NotFound`/retention expiry comes from a stamped status authority, while zero-to-bounded-many stopped records for every stop-eligible output/step `SemanticHandle` do not erase one another, business lifecycle, or cancellation.
- [ ] A raw external mutation/cancellation after Double Quarantine creates a declared `Intent`; `ControlPulse` is accepted only from a declared trusted runtime/resource/route origin.
- [ ] A post-commit dispatch/ACK/ambiguity/terminal-stop observation that affects Sovereign State is declared as a typed `ControlPulse`, passes through single-writer `decide`, has a handle/provenance/dedup policy, and does not arise from a source commit or direct runtime write.
- [ ] The current cause is passed in exactly one typed `Pulse`; other contextual observations are passed through `DecisionContext`; no ambient or duplicated cause exists.
- [ ] Every outgoing payload field is derived from committed State, the current Pulse, or a declared versioned `DecisionContext`; a value from a prior Pulse/Context needed later is retained as a bounded typed captured/result value with correlation/version/provenance/retention or is explicitly reintroduced through a trusted path.
- [ ] State has one writer.
- [ ] Every mutable semantic fact has one authority.
- [ ] There is no direct read of another authority's mutable state.
- [ ] A `Decision` is bounded and is accepted in full or not accepted.
- [ ] No output is dispatched before acceptance/commit.
- [ ] Reentrant transition is prohibited.
- [ ] Runtime IDs are not used as pre-commit domain identity.
- [ ] Async results have provenance and semantic correlation.
- [ ] The stale-result policy is explicit.
- [ ] The idempotency policy is explicit for a retryable mutation/effect; the fingerprint has an exact business + stable actor/target tuple, key/transport metadata remains separate, and a retained copy, if any, equals the atomic accepted record.
- [ ] Timeout is not disguised as proven failure.
- [ ] Cancellation is modeled as a race, not an instantaneous stop.
- [ ] Compatible nonterminal cancellation acceptance and a legitimate result converge explicitly in both observation orders; terminal cancellation proof does not depend on undeclared AIP-first delivery, and a late weaker proof neither regresses state nor duplicates output.
- [ ] Both permutations of mutually exclusive terminal result/cancellation proofs have an explicit symmetric conflict policy: the second proof does not overwrite accepted terminal state or publish partial output.
- [ ] Queries do not mutate state.
- [ ] Every mandatory §8.3 limit and applicable delivery cap is declared; `0` is used only for an absent `EffectRequest`/`ModuleCommandRequest` kind or disabled retries under §8.3 and never means unbounded.
- [ ] Unsafe escape hatches are listed separately.

### 18.2. Composition

- [ ] A public dependency is declared as `ReadDependency`, `DeclaredCommandDependency`, `DeclaredSignalDependency`, or `FlowParticipation`.
- [ ] Every imported `ModuleCommand`/`ModuleResult` resolves through exactly one versioned dependency that matches the Assembly route and is not redeclared as a caller-owned type.
- [ ] A Feature does not import another Feature's internals.
- [ ] A one-hop command is not artificially turned into a micro-Flow.
- [ ] A stateful multi-participant workflow has one coordinator owner.
- [ ] A Flow does not copy mutable participant truth.
- [ ] A Flow stores only field-minimized workflow values needed by later decisions/recovery; a participant/runtime ledger, outbox, and history do not become hidden decision input.
- [ ] A public contract does not re-export another authority's owned types.
- [ ] The route graph and fan-out are bounded.
- [ ] Compile-time import and direct synchronous/control graphs are acyclic unless covered by a waiver.
- [ ] Async feedback, if present, has an owner, stable identity/idempotency, a finite causal/retry budget, an escape condition, and fan-out protection.
- [ ] A multi-source read is not called an atomic snapshot without a corresponding mechanism.

### 18.3. Concurrent

- [ ] The mailbox is bounded.
- [ ] Backpressure is typed.
- [ ] Worker concurrency is bounded.
- [ ] Out-of-order result tests exist.
- [ ] One primary retry owner is selected for every failure mode; other layers are disabled or bounded and demonstrably semantically transparent.
- [ ] Causal depth and cumulative fan-out are bounded.
- [ ] Cancellation/deadline/late-result races are represented in state.

### 18.4. Durable state

- [ ] State/events and durable outputs are accepted atomically.
- [ ] Commit-before-dispatch is proven by fault-injection tests.
- [ ] Ingress/inbox duplicate identity and retention are defined.
- [ ] Output identity remains stable across redelivery.
- [ ] ACK and business result are distinct.
- [ ] External `OutcomeUnknown` has a reconciliation path.
- [ ] Crash-before-dispatch, ACK loss, accepted/rejected ACK, result-before-ACK, and delivery exhaustion are tested; stopped records for multiple output/step handles, including Reply and command, are retained losslessly in both arrival orders.
- [ ] Recovery uses committed output records, not a rerun of `decide` for dispatch.
- [ ] Recovery restores exact retained values and their provenance for every later command/compensation target; a crash between source result, state assignment, and dependent output creates no free or incorrect value.
- [ ] A movable owner has a fencing mechanism.
- [ ] Operation status is available independently of a live reply.
- [ ] RPO/RTO and downstream durability are not assumed automatically.

### 18.5. Hardened / Isolated

- [ ] Actor context comes only from a trusted boundary.
- [ ] A privileged output is bound to a scoped grant.
- [ ] The execution gate checks the grant and current authoritative constraints.
- [ ] The grant has a trusted issuer/authenticity/integrity and is fail-closed bound to every constrained field of the actual command payload.
- [ ] A privileged action after delay/recovery receives a current handle-scoped grant from a declared trusted versioned context; raw grant/principal is not stored in live state, the capture grant is not reused for compensation, and the committed grant is protected and redacted in the output ledger.
- [ ] A capability is implemented through a restricted credential/client/broker/ACL.
- [ ] Arbitrary URL/path/SQL/shell is absent from the standard protocol.
- [ ] Secrets do not enter ordinary projections/logs/metrics.
- [ ] Hostile code is placed in a process/sandbox if containment is claimed.
- [ ] CPU/memory/wall/network/file limits are enforced at the boundary.

---

## 19. Anti-patterns

| Anti-pattern | Why it violates the model | Preferred construction |
|---|---|---|
| `UI -> Repository -> Network` as a business path | The decision and effect bypass the Nucleus | `Intent -> Decision -> Effect` |
| Global mutable store | Multiple implicit owners and a broad blast radius | Isolated state authorities |
| Cross-cell selector over multiple mutable stores | Hidden coordination authority | Read Model or Flow-owned operation |
| Universal mediator/event bus | Runtime discovery, wildcard coupling, hidden fan-out | Typed explicit routes |
| `Map<String, Any>` / string message names | No closed algebra or exhaustiveness | Tagged unions / sealed types |
| `ExecuteSql(String)` | Raw interpreter authority | `FindUserById(UserId)` + safe adapter |
| `FetchUrl(String)` | SSRF and arbitrary endpoint authority | Endpoint-scoped typed capability |
| `RunShell(String)` | Command injection and ambient OS authority | Structured OS operation; waiver for shell |
| Unbounded queue/retry/fan-out | Unpredictable memory/latency/cost | Declared finite limits |
| Retry in SDK + adapter + runtime + Flow for one failure mode | Multiplicative attempts and unclear ownership | One primary retry owner per failure mode; other layers disabled or bounded and semantically transparent |
| Timeout = failed action | The external action might have occurred | `OutcomeUnknown` + reconciliation |
| Cancel flag = physical stop | Cancellation may be too late | Cancellation protocol/state |
| Shared `common` domain model | Transitive coupling and foreign ownership | Local semantic types + opaque refs |
| Protocol re-export | An owner change propagates transitively | A dedicated consumer-facing contract |
| Feature calls the target Nucleus directly | Bypasses route, authority, and delivery semantics | Declared command/read contract |
| Flow for every one-hop call | Ceremony and coordination explosion | `DeclaredCommandDependency` |
| One God Flow for the entire system | Hot key, enormous state graph, change radius | Multiple workflow authorities |
| One Ball per DTO/table | Micromodularity without an invariant boundary | Boundary by authority/invariants/lifecycle |
| Repository interface only for a mock | Artificial abstraction without a real boundary | Pure transition tests + adapter contract tests |
| Service locator/global SDK client | Ambient authority and invisible dependency | Explicit scoped capability injection |
| Adapter "checks itself" while holding an admin client | No independent enforcement | Restricted credential/ACL/broker |
| Roll back state after an emitted external action | The consequence does not disappear | Forward recovery/reconciliation |
| Rebuild the outbox by rerunning the Nucleus | A new version may produce different outputs | Preserve the committed output ledger |
| One `Pending/Done/Failed` status for a distributed step | Does not express acceptance/cancel/unknown races | Orthogonal facets |
| "Durable" without saying what is durable | Conflates source, target, executor, and reply guarantees | Qualified guarantee per boundary |

---

## 20. Canonical Pokeball laws

### Structure and decision semantics

**PBA-01 — Three-Zone Boundary.** Every application `Ball` has logically separated Interaction, Nucleus, and Resource/route boundaries.

**PBA-02 — Polar Isolation.** Interaction and Resources do not form a direct application path to each other.

**PBA-03 — Pure Bounded Nucleus.** The Nucleus accepts only explicit inputs/context, performs no I/O, and terminates within declared bounds.

**PBA-04 — Closed Protocol.** The set of protocol variants is statically known for a concrete version.

**PBA-05 — Explicit Decision Inputs.** A Decision uses only committed `State`, the current trusted cause `Pulse`, and a trusted versioned `DecisionContext`. A value from a prior Pulse/Context needed later is retained in State with explicit correlation/version/provenance/retention or reintroduced by a new declared trusted input; hidden observations and history/ledger reads are prohibited.

**PBA-06 — Controlled Causality.** Only the Nucleus creates a semantic external-action intent.

**PBA-07 — Atomic Decision.** State mutation and the complete `SemanticOutput` batch are accepted in full or not accepted.

**PBA-08 — Commit Before Dispatch.** A `SemanticOutput` does not leave the source boundary before successful acceptance/commit of its Decision.

**PBA-09 — No Reentrant Transition.** Completion does not invoke a nested mutating transition before the current commit finishes.

**PBA-10 — Fault Atomicity.** A fault before acceptance publishes no partial state or partial accepted-output batch. A fault after acceptance does not roll back the accepted Decision and is handled as a delivery/execution fault with declared retry, status, and unknown-outcome semantics.

### State and identity

**PBA-11 — Single Semantic Authority.** Every mutable semantic fact has one authority within its scope.

**PBA-12 — Single Writer.** One instance accepts mutating Decisions sequentially, including Decisions with commutative/CRDT-like merge; movable ownership requires fencing. Independent multi-writer acceptance is outside the Core.

**PBA-13 — State Isolation.** A Ball neither reads nor changes another Ball's mutable state directly.

**PBA-14 — Explicit State Kind.** Canonical state, ephemeral state, replica, captured input, and projection are not conflated.

**PBA-15 — Semantic Handle.** Domain state references planned work through semantic identity that does not depend on commit-time infrastructure IDs.

**PBA-16 — Trusted Identifier Allocation.** Decision-relevant IDs are known to the Nucleus as validated/reserved inputs; ambient randomness is prohibited.

**PBA-17 — Revisioned Causality.** An async result is applied only to a matching operation/handle/generation or under an explicit merge rule.

### Delivery and operations

**PBA-18 — Provenance-Bound Result.** A `Fact` is bound to a previously accepted `EffectRequest` whose payload is an `Effect`, and a `ModuleResult` to a previously accepted `ModuleCommandRequest`; an observation from a declared resource stream is bound to an accepted subscription `EffectRequest`. Every result carries trusted issuer provenance and causal identity.

**PBA-19 — ACK/Result Separation.** Delivery acceptance and business outcome are distinct observations. A post-commit mechanical dispatch/ACK observation that changes Sovereign State enters only as a declared typed `Pulse`, not as a direct runtime write or invented `ModuleResult`.

**PBA-20 — First-Class Unknown.** Possible external execution without a proven result is modeled as `OutcomeUnknown`.

**PBA-21 — Explicit Idempotency.** A retryable mutation/action has a declared identity, scope, mismatch behavior, and horizon.

**PBA-22 — Stable Logical Retry.** A transport retry creates no new logical operation or effect identity.

**PBA-23 — Cancellation Is a Protocol.** Cancellation has observable accepted/too-late/unknown outcomes and is not treated as an instantaneous stop.

**PBA-24 — Owned Retry Policy.** Semantic, transport, executor, and provider retry owners are separate and bounded.

### Composition

**PBA-25 — Declared Dependency.** Every inter-module dependency is declared as a read, one-hop command, bounded signal observation, or Flow participation.

**PBA-26 — Workflow Sovereignty.** A stateful multi-participant business process has one coordination owner.

**PBA-27 — No Wildcard Mediator.** A Flow describes a specific workflow, not a universal dispatch registry.

**PBA-28 — No Protocol Re-export.** A Ball does not publish another Ball's owned type as its own public contract.

**PBA-29 — Bounded Composition.** Participants, routes, fan-out, causal depth, and public surface have finite ceilings.

**PBA-30 — Honest Read Consistency.** Every successful `Query` response carries the declared `ConsistencyStamp` of the authority snapshot actually read. `NotFound` or retention expiry for an operation-status query is proven by a snapshot of the declared status authority, not by an absent operation instance. Multi-source aggregation is not called an atomic/consistent snapshot without a corresponding mechanism.

### Security and resources

**PBA-31 — Double Quarantine.** Raw external input and raw resource output pass through a parsing/validation/provenance boundary before the Nucleus.

**PBA-32 — Capability-Sealed Effect.** Every external effect requires the minimum explicit technical authority.

**PBA-33 — Dual Gate.** Business authorization is accepted by the Nucleus; technical/current authoritative constraints are checked during execution.

**PBA-34 — No Ambient Authority.** Global resource clients, credentials, and a service locator are not an application API.

**PBA-35 — Safe Sink.** An interpreter boundary uses a parameterized, structured, or context-encoded API.

**PBA-36 — Secret Containment.** A secret is not logged, projected, or serialized without an explicit policy.

**PBA-37 — Explicit Unsafe Escape Hatch.** Raw interpreter authority requires a separate name, capability, owner, review, controls, and expiry.

### Cost and guarantees

**PBA-38 — Bounded Execution.** Inputs, state, outputs, queues, retries, fan-out, concurrency, and external responses are finite.

**PBA-39 — Profile-Proportional Mechanism.** A Ball pays runtime and design-time cost only for the guarantees it uses.

**PBA-40 — Zero Mandatory Runtime Tax.** The Core does not require a mediator, reflection, serialization, queue, thread hop, or object hierarchy for an Inline binding.

**PBA-41 — Measured Claim.** Performance, durability, and security claims apply to a concrete binding and are supported by corresponding evidence.

**PBA-42 — Honest Guarantee Scope.** Source durability or a durable pending output does not imply successful target receipt/acceptance, executor safety, client reply durability, eventual delivery without liveness assumptions, or zero-RPO recovery.

**PBA-43 — Foundation Quarantine.** Foundation contains mechanical primitives, not a shared mutable domain model.

---

## 21. Adoption strategy

### 21.1. Start with one vertical slice

A suitable candidate:

- has a clear state lifecycle;
- accepts several input variants;
- performs one or two external effects;
- has observable success/failure;
- is not the most critical central workflow.

### 21.2. Sequence

1. List mutable semantic facts and identify their authority.
2. Choose a `StateKey` and boundary based on invariants/lifecycle.
3. Define `Intent`, `Query`, `Effect`, `Fact`, `Projection`, `Reply`, and, if needed, `Signal`.
4. Implement pure `decide` without a DSL or framework.
5. Separate Interaction and Resource adapters.
6. Add state revision, semantic handles, and a stale-result policy.
7. Record finite limits.
8. Add transition/property/adapter tests.
9. Declare explicit dependencies and Assembly routes.
10. Measure runtime cost and change radius.
11. Introduce a manifest schema, generator, and linter only after several real Balls.

### 21.3. When to add a Flow

A Flow is added not at the first inter-module call, but when at least one substantial property appears:

- multiple participants and sequence;
- independent workflow identity;
- compensation/reconciliation;
- shared deadline/cancellation;
- a terminal outcome that does not belong to one Feature;
- a lifecycle that outlives the initiating feature state;
- manual intervention queue.

One such property is sufficient if it causes the conditions for a simple one-hop dependency in §10.2 to cease being satisfied together; counting multiple indicators is not required.

### 21.4. When to add durability

A durable state profile is needed when a crash must not lose accepted state/work or when an operation outlives the request/process.

Until then, `Transient` plus explicit external idempotency where needed is sufficient. Outbox, journals, and recovery infrastructure should not be introduced merely for architectural fashion.

### 21.5. When to add isolation

A process/sandbox boundary is needed for:

- hostile plugins;
- high-risk native parsers/SDKs;
- privileged credentials;
- crash containment;
- tenant isolation;
- enforceable CPU/memory/network limits.

Logical package separation does not replace a security-principal boundary.

### 21.6. Documentation evolution

After the Core, only material supported by real implementation demand should be developed as separate documents:

```text
1. Durable Runtime and Recovery
2. Distributed Delivery and Executors
3. Event Sourcing and Replay
4. Secure Isolation and Audit
5. Read Models and Subscriptions
6. Tooling, Schema and Conformance
7. Dynamic Extensions and Ownership Transfer
```

Every extension must:

- have its own scope;
- not increase the mandatory cost of the Core;
- have executable examples;
- record limits and the failure model;
- not claim a guarantee without a reference implementation/tests.

---

## 22. Glossary

**Assembly** — an explicit composition root that binds public protocol outputs to consumers/targets.

**AuthenticatedActorContext** — a trusted description of a stable subject, verified approved issuer, authenticity/integrity, authentication assurance, validity, and delegation.

**Ball** — the smallest operationally useful scope of an application decision, state authority, and lifecycle.

**BallInstance** — a concrete state-owning instance: `BallType + namespace + StateKey`.

**BallType** — the versioned protocol and decision semantics of a family of instances.

**BusinessRejection** — an expected domain-level rejection without state mutation or a runtime fault.

**BoundaryResponse** — a boundary/runtime response for a request with no accepted Decision: `ValidationFailure`, `AdmissionFailure`, or `DecisionRejected`; it is not a `SemanticOutput` and does not pass through commit-before-dispatch.

**Capability** — the minimum technical authority to perform a restricted class of resource operations.

**Captured Input** — an immutable, bounded, field-minimized value from a prior ingress/result/context/authority snapshot, retained in workflow state for a later decision/recovery with source correlation, applicable version, verified provenance, and a deletion trigger; it does not become a second mutable authority.

**CausalToken** — operation/handle/generation/revision lineage and the remaining total causal budget that bind a result to its cause across yield, resume, and delivery attempts.

**CommitRevision** — the monotonic number of an accepted mutating input/Decision within a BallInstance.

**CommitId** — the mechanical identity of an accepted runtime commit record; it does not replace `CommitRevision` and is not used as pre-commit semantic identity.

**Commit-before-dispatch** — the rule under which dispatch of a `SemanticOutput` is permitted only after successful acceptance/commit of the complete source Decision frame.

**CommittedStateSnapshot** — immutable local-read input that combines Sovereign State with its `BallInstanceId`, `CommitRevision`, and `stateSchemaVersion`.

**ConsistencyStamp** — the exact identity of the committed snapshot actually used by a successful read: `BallInstanceId + CommitRevision + stateSchemaVersion`; by itself it promises neither subsequent freshness nor multi-source atomicity.

**Decision** — a bounded Nucleus result: next state or event decision and the complete ordered `SemanticOutput` batch, accepted atomically.

**DecisionContext** — trusted versioned contextual observations capable of changing a decision: actor, authorization, config, policy, time, reserved IDs, and semantic limits; the current cause of the decision is passed separately as a `Pulse`, while committed workflow values remain in `State`.

**ControlPulse** — a declared trusted lifecycle/timer/cancellation/delivery observation from a runtime/resource/route boundary; it is not raw external mutation ingress. If a post-commit mechanical dispatch/ACK observation changes Sovereign State, it passes as this typed input through single-writer `decide` rather than being written directly by runtime; a business `Fact`/`ModuleResult` is not reclassified.

**DeclaredCommandDependency** — an explicit one-hop command dependency without an independent multi-participant workflow.

**DeclaredSignalDependency** — an explicit one-hop route from a committed `SignalPublication` to an `ObservedSignal` with producer/consumer versions, delivery, identity, dedup, ordering, and finite limits.

**Delivery Observation** — a provenance-bound post-commit mechanical fact about a dispatch attempt, target acceptance/rejection, ambiguity, or exhaustion of the delivery policy; it differs from a business `Fact`/`ModuleResult` and affects Sovereign State only through a declared typed `ControlPulse`.

**DispatchStopped** — a terminal delivery observation that the declared finite dispatch policy is exhausted; it does not prove business failure, cancellation, or non-execution. Operation status with multiple independently delivered outputs/steps retains a bounded record for every stopped `SemanticHandle` rather than selecting one.

**Effect** — a private declarative external operation of the owning Ball.

**EffectRequest** — a canonical `SemanticOutput` envelope with a `SemanticHandle`, `sourceOrdinal`, and an `Effect` payload.

**EventJournal** — a durability profile in which authoritative state is reconstructed from committed domain events, while every accepted input, including `NoDomainChange`, receives a durable `AcceptedEventCommit`; source output records are retained in the same authoritative transaction.

**Fact** — a validated provenance-bound outcome of a previously accepted `EffectRequest`; a subscription observation is likewise causally bound to the accepted subscription request and its `SemanticHandle`.

**Feature Ball** — a Ball that owns a local business capability/state authority.

**Flow Ball** — a Ball that owns the coordination state and terminal outcome of a specific workflow.

**Foundation Quarantine** — the rule that limits a shared foundation to mechanical primitives and prohibits it from becoming a repository for a shared domain model.

**Grant** — scoped business-authorization proof from an approved issuer with verified authenticity/integrity and complete binding to actor, action, object, audience/target, operation, payload fingerprint, validity period, and anti-replay data.

**Interaction Hemisphere** — the external input/output adapter boundary: parsing, normalization, authentication integration, validation, and encoding.

**Intent** — a validated request to initiate a state change or operation.

**ModuleCommand** — an addressed public command to another Ball authority.

**ModuleCommandRequest** — a canonical `SemanticOutput` envelope with a `SemanticHandle`, `sourceOrdinal`, and a `ModuleCommand` payload.

**ModuleResult** — the business outcome of a previously accepted ModuleCommand.

**Nucleus** — the Ball's pure bounded decision authority.

**OperationId** — the stable identity of an accepted logical operation; it is not equal to a RequestId or delivery attempt.

**Operation Status Authority** — a committed query-oriented ledger/state with its own revision that losslessly represents operation lifecycle, retention markers, and trusted delivery observations; its stamp proves the status snapshot read but neither makes the authority the owner of the original business facts nor promises atomic freshness across multiple sources.

**AttemptId** — the mechanical identity of one delivery/execution attempt; it changes on retry and does not replace `OperationId`, `SemanticHandle`, or `OutputId`.

**ObservedSignal** — a provenance-checked causal input envelope created from a previously accepted `SignalPublication` over a declared signal route.

**OutcomeUnknown** — a state in which an external action might have occurred but no proven outcome exists.

**OutputId** — the runtime/storage identity of a committed output record. It need not be part of domain state.

**Projection** — immutable semantic view state for a consumer/renderer.

**ProjectionOutput** — a canonical `SemanticOutput` envelope with a `SemanticHandle`, `sourceOrdinal`, and a `Projection` payload.

**Pulse** — the current trusted cause of a mutating decision: `Intent`, `Fact`, `ModuleResult`, `ObservedSignal`, or a declared `ControlPulse`; raw external mutation/cancellation enters only as an `Intent` after Interaction validation and is not a field of `DecisionContext`.

**Query** — a non-mutating read request to committed state or a read authority; for a concrete protocol version it maps unambiguously to one result payload.

**RequestId** — the identity of one transport/request attempt; it does not replace `IdempotencyKey` or `OperationId`.

**ReadContext** — canonical trusted local-read context: the selected `protocolVersion` and optional `AuthenticatedActorContext`; selectors belong to the typed `Query`, not ambient state.

**ReadDependency** — a small declared read-only contract with another authority.

**ReadResult** — a successful noncommitted Query response with a statically known payload and `ConsistencyStamp`; it is not a `SemanticOutput` and receives no commit/semantic identity.

**Read Model Ball** — the owner of materialized read state, source positions, and freshness/consistency metadata.

**Reply** — an addressed semantic response bound to a request/operation and existing only as the payload of an accepted `ReplyOutput`; a pre-acceptance boundary failure or rejection is not a Reply.

**ReplyOutput** — a canonical `SemanticOutput` envelope with a `SemanticHandle`, `sourceOrdinal`, and a `Reply` payload.

**Replica State** — an external/cache copy with provenance, revision, and freshness; it is not decision authority.

**RetainedContinuation** — a bounded single-owner continuation of a reserved synchronous causal chain; resume continues the original total causal budget and declared terminal policy.

**Resource Hemisphere** — the adapter/executor boundary that implements an Effect through a capability and safe sink and returns a Fact.

**Safe Sink** — a parameterized/structured/context-encoded API that does not interpret user data as code.

**SemanticHandle** — the stable domain-visible identity of a planned output/step that exists before runtime materialization.

**SemanticOutput** — the closed family of canonical envelopes `ProjectionOutput | ReplyOutput | EffectRequest | ModuleCommandRequest | SignalPublication | TimerRequest`; every variant has a `SemanticHandle`, zero-based `sourceOrdinal`, and typed payload.

**Signal** — a semantic publication without one mandatory requester; not a universal `DataChanged`.

**SignalPublication** — a canonical `SemanticOutput` envelope with a `SemanticHandle`, `sourceOrdinal`, and a `Signal` payload.

**SnapshotOutbox** — a durability profile in which a state snapshot and durable output records are accepted in one atomic source transaction, while every record has a finite duplicate-permitting dispatch policy, retention/status semantics, and terminal `DispatchStopped`; eventual delivery is not guaranteed.

**StorageTransactionId** — the mechanical identity of the successfully committed storage transaction that supported acceptance; it is not a domain-visible handle, operation identity, or independent proof of a business outcome.

**Sovereign State** — canonical mutable state with one decision authority.

**State Belt** — a logical map of isolated state authorities; not a global mutable-store API.

**StateKey** — the identity of a BallInstance's local state/consistency/partition boundary.

**Structural Allocation** — an allocation that exists only because of an architecture runtime mechanism, not because of useful payload.

**TimerRequest** — a canonical `SemanticOutput` envelope with a `SemanticHandle`, `sourceOrdinal`, and a `TimerIntent` payload for a Ball with declared timers.

**Workflow Sovereignty** — the rule of one coordination owner for one stateful multi-participant workflow.

**Zero Mandatory Runtime Tax** — the absence of mandatory mediator/reflection/serialization/queue/thread-hop/object-hierarchy overhead in Core Inline semantics; concrete zero-allocation claims require a benchmark.

---

## 23. Canonical statement

> **Pokeball Architecture structures an application as a set of explicitly composed functional modules. Every Ball separates Interaction, Protocol Nucleus, and Resources; has one owner of canonical state; makes decisions through a pure bounded function; atomically accepts state and semantic outputs before any dispatch; interacts through closed typed protocols; binds asynchronous results to their cause; represents idempotency, cancellation, and an unknown outcome explicitly; obtains external authority only through scoped capabilities; and pays the cost of concurrency, durability, and isolation only where those guarantees are actually needed.**

Short formulas:

```text
One mutable fact — one authority.
One instance — one writer.
One stateful workflow — one coordinator.
Only the Nucleus creates a semantic effect.
Commit first, then dispatch.
Timeout does not prove failure.
Cancellation does not prove stop.
Dependencies and resources are always bounded.
The mechanism is proportional to the guarantee.
```

---

**End of Pokeball Architecture — Core Specification `1.0-core-draft`.**
