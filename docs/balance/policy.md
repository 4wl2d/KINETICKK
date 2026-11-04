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
cadence), so the 0.6.0 tree 15b23096 became the base and every population
threshold was restated once against it. The tree that landed the totem chests
and Core Stasis changed the reward stream on purpose again: the open-world
Totem left, each Elite-class kill amplifies the current weapon, dungeons hold
Totem chests and collapse, a still Core freezes (Core Stasis), and the
population bots load out their first owned preferred weapon. So the base is
now that tree, e33bfb67 (the Elite-resonance base the gates cite), and every
population threshold was restated once against it. The full rebalance of
2026-11-04 changed what every gate measures once more (the progression model of
[progression.md](progression.md), the 4-Relic budget with the Meld and the
Relic value pass, the rarity rework, Core Freeze, dungeon waves and hazards),
so its integrated tree is the base now: the gates it failed were restated once
against it, and the Rebirth gates A-H gave way to the targets P1-P7 (baseline.md, "2026-11-04 Full rebalance"). When the probability read
on `PopulationSeeds` and the one read on the calibration windows disagree, the
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
| 2,801..3,400 | the base of the 0.6.0 D and H floors (retired with gates A-H), never tuned on |
| 3,401..4,400 | item rarity: the full-run rarity probe and the offer-only runs (`RarityDistributionProbeTest`, `KK_RARITY_SYNTH`) |
| 4,401..6,400 | the offer-only rarity design gate (`RarityDistributionGateTest`), never tuned on |
| 6,401..16,400 | the offer-only rarity tails (10,000 runs per arm, `KK_RARITY_SYNTH`), a report, never tuned on |
| 16,401..17,000 | Rebirth curves and permanent progression ([progression.md](progression.md)): the `balanceSweep` cells (16,401..16,500 by default) and campaigns |

| Rule | Selection |
|---|---|
| threshold | a floor is min(the gate's design target or, without one, the seeded gate's own threshold on the same statement, with k of n read as k / n; the base population value − 2 SE), a ceiling max(that threshold, the base + 2 SE), both rounded outward to 0.01 (seconds down to 0.01 s); a design target never weakens (the progression targets P1-P7, tier 10 clears at most 3 of 5 with a 480 s median survival for the strong build, the weapon ±35 % and character ±40 % bands, the defensive Prism and Diamond band, the 5 % perceptible-item floor); a 0.5.0 accepted trade-off binds more strictly only where the base meets it (V2's 0.81 does); SE is binomial for shares (0 when every run or none meets the statement, which leaves the seeded or design bound), the bootstrap SE for medians and ratios (20,000 resamples, RNG seed 20261027, paired by seed where two arms share seeds) and the delete-one jackknife SE for V2 |
| no-actives arms | an arm that means a run without active items (Gate I's unskilled arm, the Singularity baseline, reaction and item arms) plays the catalog without them (`withoutActiveItemCatalog`), never `activeWeight` 0 |
| progression targets | P1-P7 of [progression.md](progression.md) are design bounds, played over `PopulationSeeds` by `RebirthBalanceGateTest` (P1-P3, P6) and `UpgradeBalanceGateTest` (P4, P5 over the first `TOP_RUNS`, P7 on fixed encounters); P4 keeps the 0.6.0 bounds (the strong build clears tier 10 in at most 3 of 5 and survives a median of at least 480 s) and P2, P3 and P5 were restated once on the rebalance base (progression.md, "Design targets") |
| Gate I | the median clear with actives over the median clear on the catalog without them, 300 `PopulationSeeds` runs per arm, is at least 0.77 (the Elite-resonance base reads 0.825, paired SE 0.024, so its base − 2 SE of 0.777 lies below the seeded 0.80); 0.82 is a calibration target on the windows only |
| active share | over the Orb's 300 `PopulationSeeds` runs at least 0.25 bind an active item by 360 s, at least half of those reach 720 s, and their median share of the damage active effects cause lies in [0.07, 0.20] (rebalance base 0.077, SE 0.0030; the Elite-resonance base's 0.096, SE 0.0049, had put the floor at 0.08); each character's first 25 runs along `PopulationSeeds` that bind one keep the top effect's pooled share ≤ 0.30, the design's bound |
| active frequency | a gate without a base, so its thresholds are the design's: two arms (fresh tier 0; the full-Matrix veteran at catalog level 80) of the first 300 `PopulationSeeds`, 25 per character; unowned-active cards 0.04–0.10 of item cards and ≤ 0.20 at each level 2–10; ≥ 0.80 of the runs that reach level 10 (fresh) or 16 (veteran) shown an active by then; median actives held at 720 s 1–3; POI item rewards ≤ 0.20 actives; when level 2–10's highest share + 2 SE reaches 0.20 on the calibration windows, margin comes from the unlock levels or `activeWeight`, never from the gate population |
| other population gates | the no-POI shares (0.68 as the reference, 0.50 without a POI), fresh tier-0 survival to 720 s (0.81) and its level band (at most 0.05 of the alive runs outside 22–30), V2 per character (Orb 0.77, Prism 0.80, Star 0.78, Hexagon 0.80), every Singularity arm (1.15, or the base ratio + 2 SE where higher: Spin Turbine 1.34, Kickback Plate 1.22, Slingshot 1.20, Gravity Assist 1.17, Mass Driver 1.16; no arm may add a singularity contact), the Apex cast (13 fights per character and tier, every fight), the Matter price locks P1 and P2, the Data-avoiding arm's forced Data picks (at most 0.20 of the runs; rebalance base 0.107, SE 0.018), the Apex offence (the first 96 runs per offensive character, 4 forks each) and both Apex defence bands (the first 128 tier-10 runs of the Prism and of the Diamond, 4 forks each; the Prism by the pooled probability above) |
| seeded gates | every other reward-stream gate keeps its seeds and thresholds while they pass with probability ≥ 0.90 at the base: the run-level Data arms, Matter P3, ItemVariety V1, V3 and V4, and the Apex contract clauses other than the cast; on the Elite-resonance base the lowest is 0.95 (the Data-seeking arm at tier 6), and 0.93 on the tree before it (the Data-seeking arm at tier 0) |
| 0.6.0 design gates | the opt-in content gates (dimensions, sites, bounties, dungeons, encounter duels, omen arms, the run skeleton, event signals, the director budget) hold design bands met by content; the population rule does not restate them, since on the tree that defines the base it would make them vacuous. The pilot gates play the first 120 to 300 `PopulationSeeds` and print their bootstrap SEs; the run skeleton and the director budget read the bot-free variety seeds `i × 7,919 + 101`; the offer-only rarity gate (the Legendary and Epic bands per run of the max- and base-Luck arms at tiers 3, 6 and 10) plays 2,000 runs per arm and tier on its own window |
