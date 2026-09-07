<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Migration baseline

## Immutable inputs

The migration starts from these exact inputs:

- KINETICKK base: `origin/main@a0762dd40df50a06f48f31f2916960ea04992dc2`.
- Profile/Lab pilot commit:
  `d1f519954ea4f3dd2f871adc3cc2e93f7648b42b`.
- Simplification source: the complete dirty KINETICKK tree captured as
  `1c440d59d99161585227c2a32b3e3eeb9a2b6656`, preserving the user's index and files.
- Pokeball repository: `git@github.com:4wl2d/Pokeball.git` at
  `b4a8219ecb70ae5e81214edd6b509b61d9db0637`.
- Core entrypoint: `spec/pokeball-architecture-core.md`, declared version
  `1.5.0-draft`, status `canonical draft`.
- Ordered Core set: 25 files, 733764 exact bytes, SHA-256
  `2ac605e4ff4db406b661356ea9c15a1b2d1683f68e515cd7cfc136c41c28daad`.
- Agent Pack: revision 15, 25 Markdown files including `BASELINE.md`; its
  digest covers the other 24 files and is SHA-256
  `736220908debbb93a84dd971ce5943efb79b957cb3c6d7c04ad6eba97ae1aa97`.

The Core pin is the published `feature/local-composition` snapshot from
[Pokeball PR #7](https://github.com/4wl2d/Pokeball/pull/7), verified against its
remote branch. The specification remains a canonical draft. Verification uses
a clean checkout of that exact commit supplied through `-PpokeballSnapshotDir`.
Recheck the commit, manifest order, byte counts and both digests; matching bytes
establish input identity, not application conformance. The earlier Core pin was
`de9ef7384795680c836d5e6c2c9b394286058670`; its evidence does not certify this migration.

## Frozen product behavior

The migration preserves the following externally observable behavior unless an
item appears under **Intentional delta**.

### Routes and workflow

All seven screens remain available: Home, Gameplay, Settings, Lab, Armory,
Rebirth, and Codex. Home and Gameplay are base destinations; the other five are
single overlays. Replacing an overlay never builds a feature-to-feature stack.
Opening an allowed overlay during a running game pauses that run. A choice
blocks overlays; Game Over and Victory allow only Rebirth. Closing Settings
propagates the persisted preferences to an active run. Restart and completed
Rebirth start a fresh run from the latest Profile snapshot.

The existing keyboard mapping remains `S/L/A/B/C/I/M/Escape/Enter` for Settings,
Lab, Armory, Rebirth, Codex (C or I), mute, back, and contextual enter.

### Profile

The full `PlayerProfile` remains the canonical source for preferences, economy,
loadout, Lab ranks, collection, and Rebirth progress. All accepted mutations
publish their complete next value before persistence is attempted. Persistence
failure or uncertainty never rolls an accepted value back and does not cause an
automatic new write. Rejections change neither state nor persistence.

The frozen mutation matrix covers preference normalization, sound/music mute,
Lab purchases, core-shape selection, weapon purchase/equip, Rebirth advance,
gameplay-progress merge, bootstrap, and the test-only replacement seam.
The target removes production arbitrary replacement but preserves validated
bootstrap semantics.

### Gameplay and content

Seeded gameplay remains deterministic for an equal captured bootstrap, seed,
and pulse trace. Simulation stays at 120 Hz with at most 48 fixed steps per
render frame. Current caps remain enemies `120`, projectiles `650`, pickups
`420`, trail points `110`, and visual-FX cues `2048`. Content now owns the
12-minute boss clock and its encounter/progression profile. Active-time goals
are 12–15 minutes including the boss; no hard defeat timer is introduced.
Fatigue recovers from actual sustained turns or low-speed Brake, while lateral
and counter-thrust remain available at exhaustion. Gameplay owns six character
abilities, optional world-fixed trials and bounded relic synergies. Codex reads
one coherent immutable build summary and preserves pause/reward selection.

The catalog remains exactly 400 items, 12 weapons, 8 meta upgrades, 40 relics,
six characters, and Rebirth levels `0..10`. Existing stable declaration order is captured as
explicit stable IDs during the migration; consumers receive immutable versioned
snapshots instead of reaching global catalog objects.

### Audio

All sixteen existing semantic cues keep their frequency, duration, gain, and
wave mapping. The music sequence and `0.32s` step remain unchanged. At most 32
caller cue requests are accepted and 3 caller sound requests are selected per
advance; the independent internal music tone is separate. Invalid tone
requests remain rejected. Normal cue and music behavior is unchanged;
failure-path observability is intentionally tightened as described below.

## Intentional delta

The compact exit workflow removes the old causal-depth assertion that failed
when a busy Profile refused progress before acceptance. An injected
persistence callback reproduced that failure on the original source snapshot;
ordinary UI reachability was not established. The retained regression requires
unchanged Profile economy, an exited run, and visible
`EXIT_PROGRESS_NOT_APPLIED` in Session.

The simplification corrects one additional retained-progress omission:
`hasPendingReductionOutputs` now accounts for elite kills, Dash hits, completed
orbits and the Architect-defeated character. Two new no-op/paused regressions
reproduced the omission on source snapshot
`1c440d59d99161585227c2a32b3e3eeb9a2b6656`; ordinary UI reachability of lost
progress was not established. Normal performance-harness outcome fingerprints
remain stable across all 21 scenarios. This is an intentional behavior correction,
not evidence of a reproduced user-facing loss.

Until KINETICKK `1.0.0`, `ProfileSnapshot` is the sole persisted Profile schema
and is treated as current rather than numbered. No version-family or migration
path is supported. The current wire schema explicitly allows omitted
`languageCode` and `runStatisticsOnLeft` fields, which resolve to
Russian and `false`; those defaults are part of this one current contract.
The Resource and platform capability expose only `readSnapshot` and
`writeSnapshot`; there is no reset, import, quarantine,
purge, or bulk-clear operation.

Platform composition owns the only physical storage authority. Android uses
SharedPreferences `kinetickk.profile.v2` key `snapshot`; Desktop uses Preferences
node `kinetickk/profile-v2` key `snapshot`; Web uses local-storage key
`kinetickk_profile_v2`. All other keys are outside the application contract: they
are ignored and remain untouched.

An absent current value, a strict codec rejection, or a decoded snapshot that
is incompatible with current Profile policy starts from defaults and leaves
bootstrap ready. A provider read failure instead blocks Session bootstrap and
shows the input-blocking `PROFILE UNAVAILABLE` UI; no local data is changed.

Audio fault staging is a deliberate operational delta. Synchronous Resource,
platform, and programming faults are no longer swallowed by best-effort
wrappers: they remain runtime faults and propagate after any already accepted
frame and output batch drains. Detached Desktop synthesis faults escape the
worker task to runtime. Web native `resume()` and `close()` Promise rejections
alone are observed and consumed as non-semantic post-acceptance projection
loss. No typed Audio Fact, result, or status is fabricated.

The migration may change internal ordering only where Pokeball acceptance
semantics require it, such as pause acceptance before overlay publication and
accepted Profile progress before returning Home. The rendered UX and final
business outcomes remain the same.

## Baseline evidence

Before the pilot commit, these commands passed:

```text
./gradlew :core:profile:data:desktopTest :feature:lab:impl:desktopTest --rerun-tasks
./gradlew verifyArchitecture desktopTest compileTestKotlinWasmJs wasmJsBrowserDistribution --rerun-tasks
git diff --check
```

The full gate executed 527 Gradle tasks successfully. Phase-two
characterization adds explicit Profile mutation, seeded gameplay, workflow,
audio-map, and content/balance coverage before any physical module move.

## Non-claims at baseline

This baseline makes no Pokeball conformance, certification, durability,
exactly-once, eventual-delivery, crash-atomic persistence, cross-device storage,
security-isolation, zero-overhead, or external-provider behavior claim. The
later bounded claim is a project self-attestation for one immutable
implementation commit only.
