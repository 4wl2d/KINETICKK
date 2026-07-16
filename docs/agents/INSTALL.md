# Installing and Updating the Agent Pack

> **Status:** derived noncanonical package. It neither defines the architecture nor extends the Core. Before using it, pass the baseline gate in [BASELINE.md](BASELINE.md). If the two conflict, the canonical specification prevails.

## Target layout

```text
target-repo/
├── spec/
│   └── pokeball-architecture-core.md
├── docs/
│   ├── agents/
│   │   ├── LICENSING.md
│   │   └── rest of the package
│   └── pokeball-project-overlay.md
└── AGENTS.md
```

Relative paths are based on this layout. Renaming the canonical specification requires a synchronized update to `BASELINE.md` and a new package validation.

## Initial installation

1. Copy the Core and all of `docs/agents/` from one validated change set, preserving exact bytes and filenames. `LICENSING.md` is a scoped notice only for the Core and Agent Pack: retain it in the exact package and do not use it to replace the target repository's existing software `LICENSE`. An equivalent form of attribution may be added, but it does not replace this file in the verified package.
2. Pass the exact Core and package-content gates in `BASELINE.md` before reading the runbooks. The source governance ledger is not required for portable verification: review IDs in the machine-readable block are provenance, while the self-contained validation state and content digest are part of the delivery.
3. Copy `PROJECT-OVERLAY.template.md` to `docs/pokeball-project-overlay.md`; then resolve every path/link reference from the new location and confirm that the baseline route remains `docs/agents/BASELINE.md`.
4. Complete every required field or specify `not-applicable` with a verifiable reason.
5. Obtain project-owner acceptance of the overlay and record the owner/date/revision.
6. Add only the routing block below to the root `AGENTS.md`. Do not copy all `PKB-AR-*` rules into it.
7. Run the package gates and a dry run on one real Ball. `AP-GATE-10` uses the actual relocated overlay copy, not the template in place; `AP-GATE-07` becomes applicable only after project-owner acceptance of the completed copy.

Recommended routing block:

```markdown
## Pokeball Architecture

For tasks within Pokeball scope, first check
`docs/agents/BASELINE.md`, then read
`docs/agents/AGENT-CONTRACT.md` and the accepted
`docs/pokeball-project-overlay.md`.

Canonical Core: `spec/pokeball-architecture-core.md`.
If the Agent Pack is stale, stop the conformance claim and report the discrepancy.
Project-specific rules do not override the Core without a recorded deviation.
```

The project's `AGENTS.md` retains its own build/test/repository rules. The routing block adds architectural source precedence but does not replace local instructions.

## Dry run

Give the agent one bounded task and require it to name the following before editing:

- baseline state;
- accepted overlay revision;
- the Ball, `StateKey`, mutable authority, and selected profiles;
- applicable `PKB-AR-*` rules;
- protocol/artifact diff;
- cross-transition value lineage: origin/correlation/versions/provenance, first atomic state assignment, consumers, retention/deletion, or trusted reintroduction;
- delayed privileged-action path: stable subject binding, current exact-handle grant, typed missing/expired outcome, and no raw grant/principal in live state;
- tests/evidence and explicit non-guarantees.

The dry run fails if the agent relies only on this package, invents project values, treats an example as a mandatory template, leaves a downstream payload free or ledger-derived, or makes a claim broader than the evidence.

## Updating

1. Freeze the old exact Core baseline and semantic diff.
2. Apply the new canonical specification.
3. Rebuild contract sources, runbooks, the reference index, and traceability.
4. Update the Core hash/byte count/version and package content digest in `BASELINE.md`; this metadata file is excluded from its own digest.
5. Recheck the overlay: new mandatory decisions, state migration/value-retention, and delayed authorization paths receive no implicit defaults.
6. Run the ten package gates and the project conformance gates.
7. Deliver the Core and package as one version.

Prohibited partial updates:

- updating only the hash;
- updating only the Core;
- copying one runbook without the contract/baseline;
- removing or replacing the scoped `LICENSING.md` within the exact Agent Pack;
- relocating the template and treating it as an accepted overlay;
- retaining an old conformance claim after changing a profile/route/authority.

## Removing or discontinuing Pokeball

If the project no longer claims Pokeball conformance, removing the routing block and artifacts is a project-owner decision. Historical deviations/evidence are not rewritten. Do not retain a stale routing block that points to an absent Core while continuing to call the scope conforming.
