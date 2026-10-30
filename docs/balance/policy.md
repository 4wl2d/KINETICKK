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
while they pass with probability at least 0.90, read from 300 runs of the base
tree and of the tree under change with the gate's own bot, profile, tier and
outcome; below that the gate is a coin flip and states the same rule over
`PopulationSeeds` (`i × 7,919 + 13`, i = 1,001..1,300; ItemVariety's V2 uses
`7,919 i + 101` over the same i), disjoint from every fixed gate seed (i ≤ 205,
and 2026). 0.5.0 introduced the rule against the 05885fa9 tree (0.5.0 before
active items). Release 0.6.0 changed what every gate measures on purpose (the
piloting flight law, harsher impacts, new obstacle kinds, director
personalities, ambient world events and the reference bot's human Dash
cadence), so the base is now the 0.6.0 tree 15b23096 and every population
threshold was restated once against it. When the probability read on
`PopulationSeeds` and the one read on the calibration windows disagree, the
runs of both, pooled, decide (this made the Apex Prism a population gate in
0.5.0). Knob values are chosen only on calibration windows and then confirmed
on the gates, never by filtering candidates against gate seeds or
`PopulationSeeds`. A restated gate that fails on its population is a
regression, not a re-roll.

Calibration windows (seeds `i × 7,919 + 13`):

| i | Use |
|---|---|
| 1..900 | obstacle-kind mix (1..300), flight law (301..600), impacts (601..900) |
| 1,001..1,300 | `PopulationSeeds`: gates only, never tuned on; the opt-in pilot gates play their first 150 (sites) or 120 (dimensions, structures) |
| 1,301..1,600 | enemies (Comet and Pylon weights, mini-boss and secret-boss integrity) and the 0.5.0 active-item calibration |
| 1,601..1,900 | run variety (personalities, affixes, champions, events, omens, signal prices) and the 0.5.0 active-item calibration |
| 1,901..2,200 | combined flight law and impacts |
| 2,201..2,500 | sites (2,201..2,300), structures (2,301..2,400), dimensions (2,401..2,500) |
| 2,501..2,800 | combined confirmation of the involuntary systems |
| 2,801..3,400 | the base of the D and H floors, never tuned on |

| Rule | Selection |
|---|---|
| threshold | a floor is min(the gate's design target or, without one, the seeded gate's own threshold on the same statement, with k of n read as k / n; the base population value − 2 SE), a ceiling max(that threshold, the base + 2 SE), both rounded outward to 0.01 (seconds down to 0.01 s); a design target never weakens (gate A's 0.62, the D and H floor of 480 s, tier-10 clears at most 3 of 5, the weapon ±35 % and character ±40 % bands, the defensive Prism and Diamond band, the 5 % perceptible-item floor); a 0.5.0 accepted trade-off binds more strictly only where the base meets it (V2's 0.81 does); SE is binomial for shares (0 when every run or none meets the statement, which leaves the seeded or design bound), the bootstrap SE for medians and ratios (20,000 resamples, RNG seed 20261027, paired by seed where two arms share seeds) and the delete-one jackknife SE for V2 |
| no-actives arms | an arm that means a run without active items (Gate I's unskilled arm, the Singularity baseline, reaction and item arms) plays the catalog without them (`withoutActiveItemCatalog`), never `activeWeight` 0 |
| gates D and H | the strong build's tier-10 median survival over `PopulationSeeds` is at least min(480 s, M − 2 SE), M the median survival over the 600 window runs i = 2,801..3,400 and SE the bootstrap SE of a 300-run median drawn from them, rounded down: D's M 877.85 s (SE 75.28 s) and H's 900.01 s (SE 39.92 s) put both floors at 480 s; a value below 420 s goes to the owner instead of becoming a floor; their win bounds (at most 3 of 5) stay seeded. About half of the window's runs reach the harness's 900 s cap, so the mean (655.30 s and 656.93 s) is the stable reading beside the median |
| Gate I | the median clear with actives over the median clear on the catalog without them, 300 `PopulationSeeds` runs per arm, is at least 0.80; 0.82 is a calibration target on the windows only |
| active share | over the Orb's 300 `PopulationSeeds` runs at least 0.25 bind an active item by 360 s, at least half of those reach 720 s, and their median share of the damage active effects cause lies in [0.07, 0.20] (base 0.090, SE 0.0061, so the floor sits below the design's 0.08); each character's first 25 runs along `PopulationSeeds` that bind one keep the top effect's pooled share ≤ 0.30, the design's bound |
| active frequency | a gate without a base, so its thresholds are the design's: two arms (fresh tier 0; the full-Matrix veteran at catalog level 80) of the first 300 `PopulationSeeds`, 25 per character; unowned-active cards 0.04–0.10 of item cards and ≤ 0.20 at each level 2–10; ≥ 0.80 of the runs that reach level 10 (fresh) or 16 (veteran) shown an active by then; median actives held at 720 s 1–3; POI item rewards ≤ 0.20 actives; when level 2–10's highest share + 2 SE reaches 0.20 on the calibration windows, margin comes from the unlock levels or `activeWeight`, never from the gate population |
| other population gates | gate A (0.62), the no-POI shares (0.56 as the reference, 0.50 without a POI), fresh tier-0 survival to 720 s (0.65) and its level band (at most 0.10 of the alive runs outside 22–30), V2 per character (Orb 0.76, Prism 0.78, Star 0.79, Hexagon 0.81), every Singularity arm (1.15, or the base ratio + 2 SE where higher: Slingshot 1.51, Spin Turbine 1.36, Gravity Assist 1.18, Crest Rider 1.17; no arm may add a singularity contact), the Apex cast (13 fights per character and tier, every fight), the Matter price locks P1 and P2, J1's survival (480 s), the Apex offence (the first 96 runs per offensive character, 4 forks each) and both Apex defence bands (the first 128 tier-10 runs of the Prism and of the Diamond, 4 forks each; the Prism by the pooled probability above) |
| seeded gates | every other reward-stream gate keeps its seeds and thresholds while they pass with probability ≥ 0.90 at the base: Rebirth B, C, E, F, G and the D/H win bounds, the run-level Data arms, Matter P3, Upgrade J1's win bound and J2–J4, ItemVariety V1, V3 and V4, and the Apex contract clauses other than the cast; on the 0.6.0 tree the lowest is 0.94 (the Data-seeking arms at tiers 3 and 6) |
| 0.6.0 design gates | the opt-in content gates (dimensions, sites, bounties, dungeons, encounter duels, omen arms, the run skeleton, event signals, the director budget) hold design bands met by content; the population rule does not restate them, since on the tree that defines the base it would make them vacuous. The pilot gates play the first 120 to 300 `PopulationSeeds` and print their bootstrap SEs; the run skeleton and the director budget read the bot-free variety seeds `i × 7,919 + 101` |
