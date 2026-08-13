<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# origin/main gameplay performance compatibility overlay

This overlay is pinned to `origin/main@a0762dd40df50a06f48f31f2916960ea04992dc2`.
It must not float with the remote branch.

The comparison runner copies this overlay and the shared benchmark harness into a detached
`build/performance/worktrees/origin-main` worktree. It changes only the worktree's
`feature/gameplay/domain/build.gradle.kts`, never commits the overlay, and never resets, cleans, or
removes the worktree.

The adapter uses the commit's own `GameReducer`, mutable state, render mapper, effects, and
`GameSnapshot`/`GameDispatchResult.Committed` publication types. The old `GameEngine` keeps its
state private and cannot accept a deterministic capacity fixture, so the adapter performs the same
branch-native reducer-to-publication sequence directly rather than using reflection or altering
production visibility.

The shared core scenarios are exposed, including the three branch-specific publication pipeline
measurements. `fixed_step_budget_48_idle` remains excluded from the
cross-branch contract. Seed, viewport, density, collection cardinalities, entity coordinates, and
the fixture/state fingerprint algorithms are synchronized with the current feature benchmark.
