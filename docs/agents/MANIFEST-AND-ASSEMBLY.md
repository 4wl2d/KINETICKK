# Manifest and Assembly Runbook

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

Primary rules: `PKB-AR-PRT-001/003`, `PKB-AR-STA-002`, `PKB-AR-CMP-001/003`, `PKB-AR-MAN-001/002`, `PKB-AR-LIM-001/002`.

## 1. Owned surface inventory

For the selected `protocolVersion`, list every nonempty owned category:

```text
intents
queries: query -> result
projections
replies
effects
facts
signals
controlPulses
timers
```

A category is declared inline or by exactly one version-pinned authoritative reference. Omission means an empty closed set. An internal state variant does not become an owned protocol type merely because it is stored in state.

Every `Query` has one result payload of the same version. A successful response is a `ReadResult<ResultPayload>` with the exact authority stamp, not a committed output.

## 2. Imported contracts

For every target-owned command/result:

| Field | Requirement |
|---|---|
| Qualified operation | `Target.Operation`, with no caller alias/re-export. |
| Target protocol version | Exact version selected by dependency. |
| Assembly route | Exactly one route to a concrete target. |
| Consumer version | Matches the dependency target version. |
| Result algebra | Resolved by the target contract, not the caller manifest. |
| Identity/idempotency | Stable handle/key/scope/horizon. |
| Delivery/retry | Point, owner, attempts, unknown/status policy. |

## 3. Illustrative manifest skeleton

This example demonstrates the shape but does not define project values or a mandatory domain taxonomy:

```yaml
apiVersion: pokeball.dev/core/v1alpha1
kind: BallManifest
metadata:
  id: ExampleWork
  ballKind: FeatureBall
  protocolVersion: 1.0.0
  stateSchemaVersion: 1

spec:
  instance:
    stateKey: ExampleWorkId
    singleWriter: true

  owns: [example.work]

  protocols:
    intents: [WorkRequested]
    queries:
      - { query: GetWorkView, result: WorkView }
    projections: [WorkViewChanged]
    replies: [WorkAccepted]

  limits:
    maxInputBytes: 16384
    maxStateBytes: 65536
    maxCollectionItems: 64
    maxOutputsPerDecision: 4
    maxEffectsPerDecision: 0
    maxCommandsPerDecision: 0
    maxCausalDepth: 4
    maxRetriesPerOperation: 0
    maxTransitionSteps: 512
```

The numbers are illustrative and are not copied into an overlay without workload/evidence. Zero values here mean absent effect/command surfaces and disabled policy retry.

## 4. Assembly route record

Every route records:

```text
routeId
kind
producer Ball/output/protocolVersion
consumer target/operation/protocolVersion
delivery point and semantics
identity/dedup policy and horizon
ordering scope
retry owner and attempt cap
queue/backpressure/retention
security principal/capability
causal/fan-out limits
status/reconciliation behavior
```

Assembly wires contracts but does not make a business decision or read private state.

## 5. Static validation

The checks must prove:

- every used owned variant resolves exactly once;
- every omitted category is unused/empty;
- every Query has exactly one result;
- imported types resolve through one qualified dependency;
- dependency target version equals Assembly consumer version;
- every produced command/effect/read/signal has route or private capability binding;
- direct/control graphs acyclic; async feedback reviewed separately;
- all nine mandatory limits present with valid zero semantics;
- a persistent state-shape/retained-field change increments `stateSchemaVersion` and has migration/quarantine evidence; missing active values receive no silent null/default;
- every applicable delivery/fan-out/concurrency/retention cap present;
- no wildcard route, protocol re-export or hidden global locator;
- manifest claim has enforcement/evidence outside manifest text.

## 6. Change discipline

A change to a protocol variant, field meaning, ownership, version, route, limit, or guarantee is semantic. A persistent state-shape change requires a new `stateSchemaVersion`; `protocolVersion` changes only when public owned/imported algebra or field semantics change. Update migration/quarantine, compatible artifacts, tests, overlay, and the decision/change record together. A manifest-only repair does not close a defect if the state transition, adapter enforcement, or status mapping remains unresolved.
