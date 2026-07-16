# Baseline gate

> **Status:** derived noncanonical package. This file does not define the architecture. If the two conflict, the canonical specification prevails.

This is the only place within `docs/agents/` where the exact SHA-256 of the canonical Core is recorded. The hash is calculated over exact bytes without normalizing line endings, Unicode, or whitespace.

```yaml
schema: pokeball-agent-pack/v1
packRevision: 11
status: derived-noncanonical
canonicalSpec:
  pathFromRepositoryRoot: spec/pokeball-architecture-core.md
  declaredVersion: 1.0-core-draft
  sha256: b16d26dbd1f8763740c48fb4de8f4a5728c0ae94094592c3b71ef72a46138052
  bytes: 211011
builtFrom:
  changeRequest: PKB-I-0023
  semanticIssues: [PKB-I-0004, PKB-I-0009, PKB-I-0010, PKB-I-0021, PKB-I-0024]
  changeReview: PKB-R-20260715-22
  validationReview: PKB-R-20260715-25
  releaseGateReview: PKB-R-20260715-26
  packagingChangeReview: PKB-R-20260715-28
  packagingValidationReview: PKB-R-20260715-29
  languageChangeRequest: PKB-I-0025
  languageChangeReview: PKB-R-20260715-30
  languageValidationReview: PKB-R-20260715-31
  attributionChangeReview: PKB-R-20260715-32
  attributionValidationReview: PKB-R-20260715-33
packageIntegrity:
  fileCountIncludingBaseline: 15
  digestScope: lexicographic filename + NUL + exact bytes + NUL for every sibling Markdown file except BASELINE.md
  sha256: a6d34ad374b0ea93aed029c5a5bb046294c7569bd557a3556d317d4f3ea852eb
validation:
  status: criteria-passed
  evidenceMode: embedded-metadata
  sourceGovernanceLedgerRequiredAfterInstall: false
hashOccurrencePolicy: BASELINE-only
```

## Verification

From the repository root:

```sh
shasum -a 256 spec/pokeball-architecture-core.md
wc -c spec/pokeball-architecture-core.md
```

For package content, from the target repository root:

```sh
python3 - <<'PY'
from pathlib import Path
import hashlib

root = Path('docs/agents')
h = hashlib.sha256()
for path in sorted(p for p in root.glob('*.md') if p.name != 'BASELINE.md'):
    h.update(path.name.encode('utf-8'))
    h.update(b'\0')
    h.update(path.read_bytes())
    h.update(b'\0')
print(h.hexdigest())
PY
```

The Core hash/byte count/version, package file count, and package digest must exactly match the machine-readable block. `validation.status` must be `criteria-passed`. Review IDs are provenance labels for the source release and do not require the source governance ledger after a portable copy; delivery integrity is verified through the self-contained fields above.

The root `LICENSE` and `NOTICE.md` license the source repository and are not included in the package content digest. The portable package carries the scoped `LICENSING.md`; it is included in the exact digest, so `AP-GATE-10` requires this file to be retained without replacing the consuming project's software license. Additional equivalent attribution is permitted, but does not replace the file within the verified package.

## Gate states

| State | Condition | Agent action |
|---|---|---|
| `READY` | The path, exact Core hash/byte count/version, and package count/digest match, and `validation.status = criteria-passed`. | The contract and runbooks may be applied within their scope. |
| `STALE` | The specification exists, but at least one value does not match. | Do not use the package as the basis for a change or conformance claim; read the actual Core and synchronize the entire package. |
| `MISSING` | The Core or this file is absent. | Stop the Pokeball-specific change and request the canonical artifacts. |
| `UNVALIDATED` | Integrity matches, but `validation.status` is not `criteria-passed`. | Analysis is permitted; release and conformance claims are prohibited until validation. |

Do not update only the hash. A new hash is permitted only after verifying the semantic diff, traceability, runbooks, reference index, and package gates. An absent accepted project overlay does not make the package stale, but it blocks project-specific decisions.

## Baseline gate report

The agent records:

```text
Pokeball baseline: READY | STALE | MISSING | UNVALIDATED
Core path: ...
Declared version: ...
Exact hash/bytes match: yes | no
Accepted overlay: path + revision | absent
Task scope: ...
Applicable PKB-AR rules: ...
```
