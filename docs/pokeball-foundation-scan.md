<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Pokeball Foundation scan

> **Policy:** `KIN-FOUNDATION-1`, owner Vladislav Tomilov, accepted 2026-07-16.
> **Current verdict:** pass for the recorded static export/dependency/platform
> scan of the current working tree. This is not a final test or project
> conformance claim.

## Scope and admission rule

The scan covers `src/commonMain/kotlin/kinetickk/foundation/` and every import of
that package. Foundation is optional and mechanical. Reuse alone never moves a
Game value into this package.

A Foundation export is admitted only when it:

1. is independent of Game vocabulary, progression, screens, and Resources;
2. owns no mutable semantic fact and makes no gameplay decision;
3. is a deterministic value/mechanical operation with explicit inputs;
4. can be tested without constructing a Game Ball or platform capability; and
5. preserves one domain authority instead of creating shared domain state.

Game entities, weapons, Relics, settings, progression, catalogs, Pulses,
persistence codecs, Resource interfaces, Assembly routes, mutable registries,
global locators, and platform/clock/I/O access are forbidden.

## Export inventory

| File/export | Mechanical rationale | Authority |
|---|---|---|
| `collections/ImmutableCollections.kt`: `ImmutableList`, `ImmutableList.copyOf`, `immutableListOf`, `toImmutableList` | Defensively copied structurally read-only ordered storage; neither mutable backing storage nor mutable iterator is exposed | none |
| `collections/ImmutableCollections.kt`: `ImmutableSet`, `ImmutableSet.copyOf`, `immutableSetOf`, `toImmutableSet` | Defensively copied structurally read-only set with stable first-occurrence iteration | none |
| `random/CloneableXorWowRandom.kt`: `CloneableXorWowRandom`, `Snapshot`, `snapshot`, `copy`, `fromSnapshot` | Explicit-seed snapshot/copy cursor compatible with Kotlin 2.3.20 seeded XorWow behavior | none; the owning feature decides where a cursor is retained |

`CloneableXorWowRandom` subclasses `kotlin.random.Random` only to implement the
standard deterministic numeric API. Its constructor requires an explicit seed
or immutable snapshot; it performs no ambient/default random lookup. Game owns
the gameplay cursor, while `InteractionFxReducer` owns a separate visual cursor.

## Recorded scan

| Check | Inspection result | Evidence/result |
|---|---|---|
| File/export inventory | Exactly two production files and the exports listed above | pass |
| Authority scan | No singleton, registry, Resource, mutable business state, or domain lifecycle | pass |
| Vocabulary/dependency scan | No import of `kinetickk.features.game` and no Game-domain declaration | pass |
| Platform scan | No `java.*`, browser, Android, Preferences, localStorage, audio, clock, network, or filesystem import | pass |
| Dependency direction | Application runtime, Game protocol/state/projection/transition, and Interaction FX import Foundation; Foundation imports none of them | pass |
| Collection publication | `Decision`, `AcceptedFrame`, Game projections, and visual projections consume defensive immutable containers | pass for static usage inspection |
| Test source | `ImmutableCollectionsTest`, `CloneableXorWowRandomTest` | pass in the recorded 150-test desktop run |
| Exception ledger | No accepted exception | pass |

The static scan does not prove gameplay correctness, Kotlin/target compilation,
or every release gate. Those results remain in `docs/pokeball-conformance.md`.

## Validation commands

```bash
find src/commonMain/kotlin/kinetickk/foundation -type f -name '*.kt' -print | sort

rg -n '^import kinetickk\.features\.game|\b(Enemy|Weapon|Relic|Progress|GameSettings)\b' \
  src/commonMain/kotlin/kinetickk/foundation

rg -n '^import (java\.|kotlinx\.browser|android\.)|Preferences|localStorage|PlatformTone|Clock' \
  src/commonMain/kotlin/kinetickk/foundation

rg -n '^import kinetickk\.foundation' \
  src/commonMain/kotlin src/commonTest/kotlin
```

For the two middle commands, no match is the expected result. Reviewers must
still inspect semantics: an empty text search is not authority proof.

## Review record

| Field | Value |
|---|---|
| Baseline inspected | Working tree on `agent/pokeball-architecture-rewrite`, derived from `b2720ff` |
| Inspected at | `2026-07-16` |
| Scan owner | Rewrite architecture reviewer |
| Exceptions | none |
| Applied evidence | File inventory, declaration inspection, domain/platform import searches, reverse import inventory |
| Tests run by this scan update | `ImmutableCollectionsTest` and `CloneableXorWowRandomTest`, included in the successful 150-test desktop run |
| Follow-up trigger | Any addition, move, visibility expansion, algorithm replacement, or dependency-direction change under Foundation |
