# Pokeball Agent Pack

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

This directory translates the Pokeball Core into an operational workflow for coding, architecture, and review agents. It helps apply the specification but does not replace it.

## Start in three minutes

1. Open [BASELINE.md](BASELINE.md) and compare the SHA-256 of the Core's exact bytes.
2. Read the scope and non-goals in §§0–2 of the canonical specification.
3. Find the accepted project overlay. The template itself is not a project decision.
4. Read [AGENT-CONTRACT.md](AGENT-CONTRACT.md) and select the `PKB-AR-*` rules applicable to the task.
5. Follow the applicable runbook and name the expected artifacts and evidence before editing.
6. Before making a conformance claim, run the gates in [TEST-AND-REVIEW-GATES.md](TEST-AND-REVIEW-GATES.md).

If the Core is absent, the hash does not match, the overlay conflicts with the Core, or the task requires a nonexistent extension specification, the agent stops the conformance claim and reports the exact discrepancy.

## Task routing

| Task | Primary document | Additional document |
|---|---|---|
| Choose a Ball boundary or change state/protocol | [DESIGN-RUNBOOK.md](DESIGN-RUNBOOK.md) | [SECURITY-LIMITS-RUNBOOK.md](SECURITY-LIMITS-RUNBOOK.md) |
| ACK, result, retry, cancellation, unknown, status | [ASYNC-STATUS-RUNBOOK.md](ASYNC-STATUS-RUNBOOK.md) | [TEST-AND-REVIEW-GATES.md](TEST-AND-REVIEW-GATES.md) |
| Dependency, Flow, route, profile, guarantee | [COMPOSITION-PROFILES.md](COMPOSITION-PROFILES.md) | [MANIFEST-AND-ASSEMBLY.md](MANIFEST-AND-ASSEMBLY.md) |
| Grant, capability, unsafe path, privacy, bounds | [SECURITY-LIMITS-RUNBOOK.md](SECURITY-LIMITS-RUNBOOK.md) | project overlay |
| Manifest, Query mapping, Assembly | [MANIFEST-AND-ASSEMBLY.md](MANIFEST-AND-ASSEMBLY.md) | [REFERENCE-INDEX.md](REFERENCE-INDEX.md) |
| Test, audit, release gate | [TEST-AND-REVIEW-GATES.md](TEST-AND-REVIEW-GATES.md) | [TRACEABILITY.md](TRACEABILITY.md) |
| Understand Catalog/Checkout | [EXAMPLE-CROSSWALK.md](EXAMPLE-CROSSWALK.md) | canonical §§15–16 |
| Find a section, law, or term | [REFERENCE-INDEX.md](REFERENCE-INDEX.md) | canonical Core |
| Transfer the package | [INSTALL.md](INSTALL.md) | [PROJECT-OVERLAY.template.md](PROJECT-OVERLAY.template.md) |

## Source precedence

Use the following precedence when interpreting Pokeball:

1. the exact canonical Core pinned in `BASELINE.md`;
2. an accepted extension specification, only within its declared scope;
3. an accepted project overlay, only for choices left open by the Core/extension;
4. `AGENT-CONTRACT.md`, runbooks, traceability, and indexes;
5. illustrative examples.

An overlay cannot silently weaken the Core. An intentional departure is recorded as a deviation with an owner, impact, and scope; that scope cannot be called conforming.

## Package contents

| File | Role |
|---|---|
| `BASELINE.md` | The package's sole record of the exact Core hash and the stale gate. |
| `LICENSING.md` | Scoped CC BY 4.0 notice for the portable Core and Agent Pack. |
| `AGENT-CONTRACT.md` | The sole definitions of the stable `PKB-AR-*` rules. |
| `INSTALL.md` | Copying, wiring, and updating the package in another repository. |
| `DESIGN-RUNBOOK.md` | Ball design and change workflow. |
| `ASYNC-STATUS-RUNBOOK.md` | Async identity, facets, delivery, and status. |
| `COMPOSITION-PROFILES.md` | Dependencies, Flow, and profile/guarantee selection. |
| `SECURITY-LIMITS-RUNBOOK.md` | Security boundaries and finite limits. |
| `MANIFEST-AND-ASSEMBLY.md` | Closed surfaces, versions, and routes. |
| `TEST-AND-REVIEW-GATES.md` | Evidence matrix and ten release gates. |
| `EXAMPLE-CROSSWALK.md` | Map from illustrative examples to the rules. |
| `REFERENCE-INDEX.md` | Index of §§0–23, PBA-01–43, and the glossary. |
| `PROJECT-OVERLAY.template.md` | Required project-specific concretization. |
| `TRACEABILITY.md` | Rule → Core → artifact → evidence → gate. |

## What the package does not do

- It does not select project-specific profiles, limits, identities, routes, or grants.
- It does not promise durability, security, ordering, performance, or delivery without binding evidence.
- It does not create a runtime, schema package, or extension specification.
- It does not authorize the agent to change the Core based on an example or general best practice.
- It does not turn Catalog/Checkout into mandatory domain-model templates.

## License

The package's original materials are part of the licensed Pokeball Architecture
material and are available under `CC-BY-4.0`. When transferring the package,
follow [`INSTALL.md`](INSTALL.md) and retain the scoped
[`LICENSING.md`](LICENSING.md) in the exact package. Equivalent permitted
attribution may be added, but it does not replace this file or change the
consuming project's software license.
