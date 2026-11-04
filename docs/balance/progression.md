<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Progression model

This page is the one description of how difficulty and power grow over the eleven Rebirth tiers (0..10). The
simulation computes in tier-normalized power units: a tier-0 Arc Coil hit is 15 and a Drifter has 28 integrity at
every tier, and the numbers a run shows are those units times the tier's display scale (`numberScale`, owned by the
display rules). Difficulty lives in four layers, each with one content home; permanent progression, not the tier,
decides which tier a player can beat. The gates that hold the design targets are `RebirthBalanceGateTest` (P1-P3, P6),
`UpgradeBalanceGateTest` (P4, P5, P7 in play) and `PermanentPowerBudgetTest` (P7); the many-run tool is
`balanceSweep` ([DEVELOPMENT.md](../project/DEVELOPMENT.md#balance-sweep)).

| Layer | Grows with | Content home |
|---|---|---|
| Tier | the selected Rebirth tier t | `defaultRebirthProfile` (`ball/content/api/.../RebirthContent.kt`) |
| Permanent | Matter spent between runs | `defaultMatrixNodes` (`MatrixCatalog.kt`), `UpgradePolicy` (`UpgradeContent.kt`) |
| Run | run progress `p = elapsed / 720 s` | `RunTempoProfile` (`RunTempoProfile.kt`) |
| Build | the run's items, Relics and Melds | item, Relic and build policy content (soft caps in `BuildPolicy`) |

## Tier layer

The tier multiplies what the enemy brings and never the player's power (`playerPowerMultiplier` is 1 at every tier).
The last three tiers surge on top of the plain growth, so tier 10 stays a hard fight even for a fully progressed
profile: progression makes it possible and easier, never easy.
Each curve is a product of t Float factors, identical on every platform.

| Quantity | Formula | Constant |
|---|---|---|
| enemy integrity `H(t)` | `1.29^t × S(t)` | `REBIRTH_ENEMY_INTEGRITY_GROWTH` |
| incoming damage `D(t)` | `1.19^t × S(t)` | `REBIRTH_INCOMING_DAMAGE_GROWTH` |
| late surge `S(t)` | `1 + 0.05 × max(0, t − 7)²` (×1.05, ×1.20, ×1.45 at tiers 8-10) | `REBIRTH_LATE_SURGE`, `REBIRTH_LATE_SURGE_FROM` |
| Matter reward | `1.16^t` | `REBIRTH_MATTER_GROWTH` |
| enemy speed, spawn rate, enemy cap, Elite rate | `1 + 0.035t`, `1 + 0.09t`, `1 + 0.10t`, `1 + 0.07t` | `defaultRebirthProfile` |
| threat offset, opening enemies, bonus rerolls, obstacles | `12t` s, `5 + t`, `t / 4`, `1 + 0.05t` | `defaultRebirthProfile` |
| run-level Data from level 16 | `1 + 0.14t` | `REBIRTH_DATA_REQUIREMENT_SCALE_PER_TIER` |
| mutators | tier n adds the n-th: Pack Hunters, Hardened Elites, Hunter Protocol, Volatile Remains, Fractured Space, Overclocked Swarm, Entropy Tide, Armored Vanguard, Blackout, Singularity | `RebirthMutator` |

| t | H(t) | D(t) | Matter | numberScale (display) |
|---|---|---|---|---|
| 0 | 1.00 | 1.00 | 1.00 | 1 |
| 1 | 1.29 | 1.19 | 1.16 | 1.1 |
| 2 | 1.66 | 1.42 | 1.35 | 1.5 |
| 3 | 2.15 | 1.69 | 1.56 | 2.3 |
| 4 | 2.77 | 2.01 | 1.81 | 4.2 |
| 5 | 3.57 | 2.39 | 2.10 | 8.7 |
| 6 | 4.61 | 2.84 | 2.44 | 21 |
| 7 | 5.94 | 3.38 | 2.83 | 61 |
| 8 | 8.05 | 4.22 | 3.28 | 200 |
| 9 | 11.87 | 5.74 | 3.80 | 790 |
| 10 | 18.50 | 8.26 | 4.41 | 3,600 |

The boss hit cap is a share of the boss's own integrity: one damage instance removes at most `hitCapShare × maxHp ×
clamp(1 − hitCapTierShrink × t, hitCapMinimumScale, 1)` (the Architect 1.5 %, 2 % less per tier, at least half;
mini-bosses 6 %; the secret bosses 0.55 %). A build whose hits reach the cap clears the bar in the same number of hits
at every tier, so the power that answers `H(t)` also reaches the cap. Before this model the cap was a share of the
tier-0 integrity: the Architect at tier 10 needed about 900 capped hits whatever the build, and the fully upgraded
strong build timed out in two thirds of its tier-10 runs. The secret bosses' share keeps their duels at the strong
build's tier 3 inside 60-180 s (`EncounterDuelGateTest`): at 1.5 % a capped build felled them in 71 hits and about
40 s.

## Permanent layer

### Core Matrix

Every node is bought once per rank; ranks add, and percent effects of one kind add across nodes. Integrity and
shields are shares: `IntegrityShare` adds a fraction of the character's base integrity (100 plus its trait) at run
start, `ShieldShare` adds a fraction of the current maximum integrity to the shield capacity (it starts charged and
grows with every integrity gain). The first rows are the cheap on-ramp; the deep, Rebirth-gated nodes and the
keystones are the most expensive purchases and carry the late defense and weapon growth, so the second half of the
Matrix by price lifts a profile from the middle tiers to tier 10.

| Node (gate) | Effect per rank | Prices |
|---|---|---|
| Impact Driver | +15 % impact damage | 400 / 1,500 / 6,000 |
| Weapon Calibration | +12 % weapon power | 500 / 2,000 / 8,000 |
| Critical Lattice | +6 % critical chance, +25 % critical damage | 2,400 / 10,000 |
| Armed Start | +1 starting weapon level and automatic cap | 3,000 / 12,000 |
| Catalyst Core | reactions +20 %, statuses +15 % | 7,600 |
| Overdrive Coil (R1) | 50 starting Overdrive, +25 % gain | 7,600 |
| Reborn Arsenal (R6) | weapon run growth +0.8p (×1.31 at the boss) | 25,000 |
| Glass Engine (R3, keystone) | all damage +35 %, integrity −15 % | 25,000 |
| Reinforced Hull | +15 % integrity | 300 / 1,200 / 5,000 |
| Hardlight Reserve | shield +10 % of integrity | 800 / 3,000 |
| Impact Dampers | obstacle damage −25 %, lethal speed +150 | 1,000 / 2,400 |
| Second Wind (R1) | once per run: 30 % integrity, 2 s invulnerable | 7,600 |
| Nanoforge | +0.8 integrity per second | 1,800 / 7,000 |
| Bastion Protocol (R2) | +30 % damage reduction | 22,000 |
| Aegis Lattice (R7) | shield +50 % of integrity, recharge 40 % sooner | 30,000 |
| Juggernaut (R4, keystone) | mass +30 %, impact +30 %, contact damage −40 %, drag +10 % | 25,000 |
| Systems row (Magnet Array, Cryo Loop, Vector Thrusters, Near Miss, Ricochet Rig, Gravity Surfer R9) | utility | 200..15,000 |
| Overclocked Reflexes | +12 % attack speed | 5,000 / 20,000 |
| Perpetual Drift (R5, keystone) | drag −40 %, magnetism −10 %, a free Dash every 6 s | 25,000 |
| Fortune row (Salvage, Data Archive, Reroll Cache, Wide Offer, Lucky Charm, Elite Bounty R2, Heirloom Vault R3) | economy | 400..7,600 |
| Prospector (R4, keystone) | Matter +50 %, enemy integrity +12 % | 25,000 |

The whole Matrix costs 358,500 Matter. Keystones are not exclusive: each is priced as a late purchase (25,000, together
28 % of the Matrix) and keeps its trade-off. Exclusive keystones would need a respec flow, a purchase rejection and a
migration for saves that own several, which the price achieves without.

Budget at the run start of the Orb with Arc Coil (`PermanentPowerBudgetTest`): a full Matrix against none multiplies
the weapon's hit by 2.34 (power and the all-damage multiplier), its damage per second by 3.56 (with the critical
factor and activation speed) and effective integrity `(integrity + shield) / (1 − damage reduction)` by 3.16.

### Refit and Honing

| Track | Effect at level L | Level 5 | Level 10 |
|---|---|---|---|
| Honing (a weapon) | its own hits × `1.085^L` (`weaponPowerGrowth`) | ×1.50 | ×2.26 |
| Refit (a character) | every damage its mechanics deal × `1.07^L` (`characterPowerGrowth`) | ×1.40 | ×1.97 |
| Refit (a character) | integrity + `0.05 L` of its base (`characterIntegrityPerLevel`) | +25 % | +50 % |

Refit reaches all character damage: the ability applicator, the Hive's drones and the Echo ghost (through the proc
pipeline), the Diamond's parry shot and the Crescent's Blink strike. Level n+1 costs `base × 2^n`: base 55 for a
character (raised 20 % per Reborn-rank unlock step), 40 for a weapon (20 % per Rebirth gate), so a track to 10 costs
56,265 or 40,920. The bases follow `MatterIncomeProbeTest`'s derivation (`round5(1.2 × M6 / 512)` and
`round5(0.85 × M6 / 512)`, M6 the full-Matrix tier-6 median Matter).

### Economy target

A full Matrix, one character's Refit 10 and one weapon's Honing 10 (455,685 Matter) take about 40-70 runs of normal
play. `balanceSweep -Psweep.mode=campaign` measures it: 16 Orb campaigns from a fresh profile, each banking its runs,
buying the cheapest affordable purchase after every run, pushing its Rebirth rank and farming one tier below after a
lost push. On the tree that introduced this model they finish in 46 / 50 / 59 runs (p25 / median / p75) and first
clear tiers 0-10 at runs 1, 2, 3, 5, 9, 12, 16, 22, 38, 42 and 47 (medians; 14 of 16 cleared tier 10 by the end).

## Run layer

All run curves read `p = clamp(elapsed / 720 s, 0, 1)` and live in `RunTempoProfile`.

| Curve | Formula | At the boss |
|---|---|---|
| enemy integrity | `1 + 5.9p` | ×6.9 |
| hostile damage | `1 + 0.5p` (`hostileDamageRunGrowth`) | ×1.5 |
| equipped weapon | `1 + 1.6p` (+ Reborn Arsenal `0.8p`) | ×2.6 (×3.4) |
| flat, status, reaction, proc and skill damage | `1 + 1.6p` | ×2.6 |
| impacts | `1 + 1.2p` | ×2.2 |
| abilities and weapon-based items | `1 + 1.0p` | ×2.0 |
| weapon level factor | `1 + 0.08 (level − 1)` (`weaponLevelStep`, `weaponLevelFactor`) | level 19 ×2.44, the cap ×3.32 |

The weapon level never passes `maximumWeaponLevel` = 30: automatic amplification (every second run level up to 10,
plus Armed Start and the signature), Elite resonance (+1 per Elite-class kill), Totem chests and directed WEAPON
rewards all go through one capped amplification. At the cap an Amplify card salvages 12 base Matter instead, and a
resonance adds nothing. The relation the run is designed around: by the boss, integrity grows ×6.9 while a building
player's weapon grows ×2.6 (run) × ×2.44 (a fresh run's level 19) × ×1.35 (Ascended mastery) ≈ ×8.6 before items, so a
run that builds keeps pace with the ramp; the tier's `H(t)` on top of it is answered by permanent power.

## Build layer

Items, Relics, Melds and Legendaries live inside the build policy's soft caps (amplification above 1.5 counts half,
critical multiplier at most ×4, vulnerability at most 1.5); their tuning belongs to the build rules, not this model.

## Design targets

Measured with `balanceSweep` over the progression window (seeds `i × 7,919 + 13`, i = 16,401..16,500, 100 runs per
cell) on the integrated rebalance tree (every lane merged: the Relic budget and the Relic value pass, the rarity rework,
Core Freeze, the dungeon and dimension changes). Arms: fresh = no Matrix, no upgrades, the Orb with the bot's loadout;
half = half the Matrix by price ([`halfMatrixRanks`](../../ball/gameplay/nucleus/src/desktopTest/kotlin/kinetickk/ball/gameplay/nucleus/simulation/EconomyGateHarness.kt))
with Refit and Honing 5, the Orb; full = every Matrix rank, Refit and Honing 10, the Pentagon with its signature Arc
Coil and the bot's own Relics; strong = full with Stormheart Capacitor, Aegis Inversion, Echo Chamber and Pandemic Codex
bound from the start (the Relics that fit Arc Coil).

| Target | Statement | Window, 100 runs (SE) |
|---|---|---|
| P1 | fresh, tier 0: win ≥ 0.75 | 0.99 (0.01) |
| P2 | fresh: tier 2 ≤ 0.65 (contested), tier 3 ≤ 0.30, tier 4 ≤ 0.05 | 0.58 (0.05); 0.18 (0.04); 0.01 (0.01) |
| P3 | half: tier 7 in [0.30, 0.85] (contested), tier 9 ≤ 0.10 | 0.50 (0.05); 0.00 |
| P4 | strong, tier 10: win in [0.20, 0.60] (at most 3 of 5: hard, but winnable) and a median survival of at least 480 s | 0.37 (0.05), median survival 565 s |
| P5 | full, tier 10: win in [0.10, 0.50] (possible, rarely) | 0.27 (0.04), median survival 533 s |
| P6 | win share never rises with the tier for a profile | holds for all four arms (table below) |
| P7 | maxed Honing ≥ ×1.8 weapon hit, maxed Refit ≥ ×1.6 character hit, full Matrix ≥ ×1.8 offense and effective integrity | ×2.26, ×1.97, ×3.56 / ×3.16 |

| Tier | fresh | half | full | strong |
|---|---|---|---|---|
| 0 | 1.00 | 1.00 | 1.00 | 1.00 |
| 1 | 1.29 | 1.19 | 1.00 | 1.00 |
| 2 | 1.66 | 1.42 | 1.00 | 1.00 |
| 3 | 2.15 | 1.69 | 1.00 | 1.00 |
| 4 | 2.77 | 2.01 | 1.00 | 1.00 |
| 5 | 3.57 | 2.39 | 1.00 | 1.00 |
| 6 | 4.61 | 2.84 | 1.00 | 0.99 |
| 7 | 5.94 | 3.38 | 1.00 | 1.00 |
| 8 | 8.05 | 4.22 | 0.97 | 0.99 |
| 9 | 11.87 | 5.74 | 0.98 | 0.97 |
| 10 | 18.50 | 8.26 | 0.85 | 0.90 |

A fresh profile clears tiers 0 and 1, contests tier 2, mostly loses tier 3 and cannot clear tier 4, so it must bank
Matter on the low tiers first; half the progression opens the middle tiers and stalls at 7-8; the full progression
clears tiers 8 and 9 reliably and makes tier 10 possible: with a fitting build about two runs in five, and never
easily (the owner's call: tier 10 stays hard, full progression only makes it easier to play). The first cut of these targets (P2 tier 2 ≤ 0.35, P3 tier 5 in
[0.30, 0.85] and tier 8 ≤ 0.10, P5 ≤ 0.85) was restated once on the integrated tree, after the Relic value pass made
every Relic pay for its liability: the tier-2 win share of a fresh profile and the tier-5 share of the half arm do not
move with `H` or `D` (fresh tier 2 read 0.52-0.56 and half tier 5 0.97-1.00 for `H` 1.27-1.31 and `D` 1.18-1.20), so the
statements moved to the tiers where the profiles actually meet their wall. `H` 1.29 and `D` 1.19 were chosen on the
window from the sets (1.27, 1.18), (1.29, 1.18), (1.27, 1.20), (1.29, 1.20) and (1.31, 1.19) (`calib` sweeps in the
change notes). The plain curves left tier 10 easy for the full progression (strong 0.90, full 0.85), so the late surge
`S(t)` restores the 0.6.0 bound of at most 3 of 5 and gate D's 480 s survival floor as P4. Its strength came from the
same window (strong / full win share at tier 10, 200 runs: s = 0.03 → 0.60 / 0.52, 0.04 → 0.48 / 0.43, 0.05 → 0.37 /
0.28 with median survival 552 s, 0.06 → 0.31 / 0.24 below 480 s, 0.08 → 0.15 / 0.10, 0.20 → 0.00); tiers 0-7 do not
move.

## Calibration recipe

1. Play the decisive cells on the progression window:
   `balanceSweep "-Psweep.cells=fresh:0,fresh:2,fresh:3,fresh:4,half:7,half:9,full:10,strong:10"`; then the full table
   (`-Psweep.tiers=0..10`) for P6.
2. Fresh tiers 3 and 4 (P2) are death-limited and move with `D` (`damage` knob); tier 2 barely moves with either curve.
   Keep fresh tier 0 above 0.85.
3. Full and strong at tier 10 (P4, P5) move with the deep Bulwark nodes (Bastion, Aegis) and the level-10 upgrades
   (`honing`, `refit`, `refitIntegrity`); `H` (`health`) mostly lengthens the regular crowds, since capped boss fights
   do not depend on it. Keep strong at least two standard errors above 0.80.
4. Half at tiers 7 and 9 (P3) moves with what the half Matrix lacks: the deep nodes and keystones. If P3 and P4
   conflict, give the second half of the Matrix more of the defense rather than changing `D`.
5. Confirm the economy with `-Psweep.mode=campaign` (median 40-70 runs); scale Matrix prices together (`costScale`
   tries a factor without editing content) and keep the Refit and Honing bases on `MatterIncomeProbeTest`'s
   derivation.
6. Write the chosen constants into their content homes, update this page's tables, and restate the gates under the
   [balance policy](policy.md).
