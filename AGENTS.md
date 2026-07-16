## Pokeball Architecture

For tasks within Pokeball scope, first check
`docs/agents/BASELINE.md`, then read
`docs/agents/AGENT-CONTRACT.md` and the accepted
`docs/pokeball-project-overlay.md`.

Canonical Core: `spec/pokeball-architecture-core.md`.
If the Agent Pack is stale, stop the conformance claim and report the discrepancy.
Project-specific rules do not override the Core without a recorded deviation.

## Agent index

Use the smallest route that covers the task. The canonical Core always wins over
this index and every derived document.

| Task | Start here | Continue with |
|---|---|---|
| Establish the accepted project design | `docs/pokeball-project-overlay.md` | `docs/ARCHITECTURE.md`, `architecture/game/ball.yaml` |
| Change a Ball boundary, authority, protocol, profile, limit, or guarantee | `docs/agents/DESIGN-RUNBOOK.md` | `docs/agents/AGENT-CONTRACT.md`, `docs/agents/TRACEABILITY.md` |
| Change a manifest or Assembly binding | `docs/agents/MANIFEST-AND-ASSEMBLY.md` | `architecture/game/ball.yaml`, `docs/pokeball-migration.md` |
| Change persistence, results, retries, or outcome-unknown handling | `docs/agents/ASYNC-STATUS-RUNBOOK.md` | Overlay sections 5, 6, and 8 |
| Change trust boundaries or Resources | `docs/agents/SECURITY-LIMITS-RUNBOOK.md` | Overlay section 9 |
| Implement or review Kotlin/Compose code | `docs/ARCHITECTURE.md` | `docs/pokeball-migration.md`, then the relevant source and tests |
| Validate or make a conformance claim | `docs/agents/TEST-AND-REVIEW-GATES.md` | `docs/pokeball-conformance.md`, `docs/pokeball-foundation-scan.md` |

Project artifacts:

- `architecture/game/ball.yaml` is the declarative contract for the singleton
  local `Game` Feature Ball. It is not proof of implementation.
- `docs/pokeball-migration.md` maps the legacy engine to the target structure.
- `docs/pokeball-conformance.md` is the live, evidence-bounded gate record.
- `docs/pokeball-foundation-scan.md` owns the mechanical-only Foundation policy.

Do not mark a partial gate as passed, infer a distributed guarantee from an
in-process mechanism, or move game authority into Foundation for reuse.
