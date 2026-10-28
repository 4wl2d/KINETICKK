<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Balance gate policy

This record sets how the seeded balance gates in the Gameplay tests choose their
seeds, thresholds, calibration windows and standard errors. The tests cite it as
`policy.md, "Balance gate populations"`; the accepted trade-offs and release
decisions behind individual gates are in [`baseline.md`](baseline.md).

## Balance gate populations

Balance gates play seeds fixed by definition, never chosen by outcome. A gate
whose outcome depends on the run's reward stream keeps its fixed seeds only
while they pass with probability at least 0.90, read from 300 runs of the
05885fa9 tree (0.5.0 before active items) and of the tree under change with the
gate's own bot, profile, tier and outcome; below that the gate is a coin flip
and states the same rule over `PopulationSeeds` (`i × 7,919 + 13`,
i = 1,001..1,300; ItemVariety's V2 uses `7,919 i + 101` over the same i),
disjoint from every fixed gate seed (i ≤ 205, and 2026). When the probability
read on `PopulationSeeds` and the one read on the calibration windows disagree,
the runs of both, pooled, decide: the Apex Prism's fixed 64 runs passed 0.94
of the time on `PopulationSeeds` but 0.75 on the windows and about 0.82 pooled,
so the Prism is a population gate. Knob values are chosen only on calibration
windows (i ≤ 900 and from 1,301; the active-item calibration used
i = 1,301..1,600 and 1,601..1,900) and then confirmed on the gates, never by
filtering candidates against gate seeds or `PopulationSeeds`. A restated gate
that fails on its population is a regression, not a re-roll.

| Rule | Selection |
|---|---|
| threshold | a floor is min(the seeded gate's own threshold on the same statement, with k of n read as k / n, the 05885fa9 population value − 2 SE), a ceiling max(that threshold, the base + 2 SE), both rounded outward to 0.01 (seconds down to 0.01 s); a 0.5.0 accepted trade-off binds more strictly only where the base meets it (V2's 0.81 does; fresh tier-0 survival's 0.73 did not and is dropped, leaving 0.66); SE is binomial for shares, the bootstrap SE for medians and ratios (20,000 resamples, RNG seed 20261027) and the delete-one jackknife SE for V2 |
| no-actives arms | an arm that means a run without active items (Gate I's unskilled arm, the Singularity baseline, reaction and item arms) plays the catalog without them (`withoutActiveItemCatalog`), never `activeWeight` 0 |
| gates D and H | the strong build's tier-10 median survival over `PopulationSeeds` is at least min(480 s, M − 2 SE), M the median survival over the 600 calibration-window runs i = 1,301..1,900 with the shipped tuning and SE the bootstrap SE of a 300-run median drawn from them, rounded down: 427.87 s for D and 431.46 s for H; a value below 420 s goes to the owner instead of becoming a floor; their win bounds (at most 3 of 5) stay seeded |
| Gate I | the median clear with actives over the median clear on the catalog without them, 300 `PopulationSeeds` runs per arm, is at least 0.80; 0.82 is a calibration target on the windows only |
| active share | over the Orb's 300 `PopulationSeeds` runs at least 0.25 bind an active item by 360 s, at least half of those reach 720 s, and their median share of the damage active effects cause lies in [0.08, 0.20]; each character's first 25 runs along `PopulationSeeds` that bind one keep the top effect's pooled share ≤ 0.30, the design's bound, which stays below the base + 2 SE of 0.31 |
| active frequency | a gate without a base, so its thresholds are the design's: two arms (fresh tier 0; the full-Matrix veteran at catalog level 80) of the first 300 `PopulationSeeds`, 25 per character; unowned-active cards 0.04–0.10 of item cards and ≤ 0.20 at each level 2–10; ≥ 0.80 of the runs that reach level 10 (fresh) or 16 (veteran) shown an active by then; median actives held at 720 s 1–3; POI item rewards ≤ 0.20 actives; when level 2–10's highest share + 2 SE reaches 0.20 on the calibration windows, margin comes from the unlock levels or `activeWeight`, never from the gate population |
| other population gates | gate A, the no-POI shares, fresh tier-0 survival and its level band, V2 per character, every Singularity arm (1.15, or the base ratio + 2 SE where higher: Slingshot 1.29, Spin Turbine 1.24, Recoil Spring 1.18, Crest Rider 1.18; no added singularity contact, Slingshot ≤ 0.02), the Apex cast (13 fights per character and tier), the Matter price locks P1 and P2, J1's survival, the Apex offence (the first 96 runs per offensive character, 4 forks each) and both Apex defence bands (the first 128 tier-10 runs of the Prism and of the Diamond, 4 forks each; the Prism by the pooled probability above) |
| seeded gates | every other reward-stream gate keeps its seeds and thresholds while they pass with probability ≥ 0.90 at the base and on the final tree: Rebirth B, C, E, F, G and the D/H win bounds, the run-level Data arms, Matter P3, Upgrade J1's win bound and J2–J4, ItemVariety V1, V3 and V4, and the Apex contract clauses other than the cast |
