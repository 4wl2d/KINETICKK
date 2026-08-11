<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Migration baseline

## Immutable inputs

The migration starts from these exact inputs:

- KINETICKK base: `origin/main@a0762dd40df50a06f48f31f2916960ea04992dc2`.
- Profile/Lab pilot commit:
  `d1f519954ea4f3dd2f871adc3cc2e93f7648b42b`.
- Pokeball repository: `git@github.com:4wl2d/Pokeball.git` at
  `de9ef7384795680c836d5e6c2c9b394286058670`.
- Core entrypoint: `spec/pokeball-architecture-core.md`, declared version
  `1.4.0-draft`, status `canonical draft`.
- Ordered Core set: 25 files, 725281 exact bytes, SHA-256
  `d7792cb6adfaf9d7e3cf0c59bcc40b1158200bfcd0496661d3293035917f352c`.
- Agent Pack: revision 12, 25 Markdown files including `BASELINE.md`; its
  digest covers the other 24 files and is SHA-256
  `1332fbc4ccbc55112ea87fa902437e6bb27f043a67663ebbe6e11f3e4239089d`.

The Core and Agent Pack digests were recomputed from the clean local checkout
at `/Users/4wl2d/Documents/Pokeball`. The exact commit is also the local
`origin/master`; later formal verification must recheck the commit object,
manifest order, byte counts, and both digests. Matching bytes establish input
identity, not architectural conformance.

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

The existing keyboard mapping remains `S/L/A/B/C/M/Escape/Enter` for Settings,
Lab, Armory, Rebirth, Codex, mute, back, and contextual enter.

### Profile

The full `PlayerProfile` remains the canonical source for preferences, economy,
loadout, Lab ranks, collection, and Rebirth progress. All accepted mutations
publish their complete next value before persistence is attempted. Persistence
failure or uncertainty never rolls an accepted value back and is never retried
blindly. Rejections change neither state nor persistence.

The frozen mutation matrix covers preference normalization, sound/music mute,
Lab purchases, core-shape selection, weapon purchase/equip, Rebirth advance,
gameplay-progress merge, bootstrap, and the legacy test-only replacement seam.
The target removes production arbitrary replacement but preserves validated
bootstrap semantics.

### Gameplay and content

Seeded gameplay remains deterministic for an equal captured bootstrap, seed,
and pulse trace. Simulation stays at 120 Hz with at most 48 fixed steps per
render frame. Current caps remain enemies `120`, projectiles `650`, pickups
`420`, trail points `110`, and visual-FX cues `2048`. Gameplay, balance,
weapons, items, relic behavior, Rebirth tuning, and the 20-minute victory clock
are unchanged.

The catalog remains exactly 400 items, 12 weapons, 8 meta upgrades, 40 relics,
and Rebirth levels `0..10`. Existing stable declaration order is captured as
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

Save v4 is the sole intentional persistent-data incompatibility. Existing v2/v3
and legacy-matter values are detected but never imported. The application
blocks on an explicit reset modal and deletes only the enumerated old keys after
a default v4 snapshot is successfully written. Cancel deletes nothing. Failed
or unknown write preserves legacy data; partial or unknown purge requires an
explicit user retry. No Preferences node, storage area, or unrelated key is
cleared.

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
