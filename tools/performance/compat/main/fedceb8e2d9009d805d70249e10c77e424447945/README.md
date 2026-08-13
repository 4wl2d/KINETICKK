<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Main performance compatibility overlay

This overlay is intentionally pinned to main revision
`fedceb8e2d9009d805d70249e10c77e424447945`.

The comparison runner copies this directory and the shared benchmark harness into a detached
worktree, then replaces that worktree's root `build.gradle.kts` with `root.build.gradle.kts`.
It never applies these files to the current checkout and never commits them to `main`.

The old `GameNucleus` is the closest main-branch equivalent of the refactored `GameReducer`: both
copy the accepted mutable model, apply one intent, drain emitted cues, and build typed outputs. The
`nucleus_*` compatibility scenarios additionally create `GameProjection`, corresponding to the
refactored adapter's explicit render snapshot publication.

`fixed_step_collision_miss` and `fixed_step_collision_hit` enter the private main simulation step
through `MutableGameState.update(FIXED_STEP)`. This executes exactly one fixed step while retaining
the small accumulator wrapper that cannot be isolated through main's internal API.

`fixed_step_budget_48_idle` is deliberately absent: main keeps its accumulator and `simulateStep`
private, so a direct 48-step budget drain cannot be reproduced without reflection or production
source changes.

## Legacy profile codec

The overlay also registers `profilePerformanceBenchmark` for the branch-native main `ProgressCodec`
v3 format. It covers default and maximum logical profiles without platform storage or synthetic
payload padding. Raw v3 payload byte count and SHA-256 are recorded as outcomes in metadata.

The three default scenario names match the feature v4 suite. Their logical profile shape matches,
but the wire formats intentionally do not: metadata identifies `legacy-v3`, so a report must not
treat payload size, strictness, or rejection behavior as apples-to-apples v3/v4 evidence.

The maximum scenarios use `profile_*_logical_maximum` names. They intentionally do not alias the
feature suite's existing `profile_*_maximum`, because those v4 scenarios pad `contentVersion` to an
exact 65,536-byte schema-capacity payload rather than measuring the branch-native logical maximum.
The v3 codec accepts legacy v2 and normalizes invalid values; no strict-v4 rejection scenario is
published under a shared name.
