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
Each curve is a product of t Float factors, identical on every platform.

| Quantity | Formula | Constant |
|---|---|---|
| enemy integrity `H(t)` | `1.27^t` | `REBIRTH_ENEMY_INTEGRITY_GROWTH` |
| incoming damage `D(t)` | `1.18^t` | `REBIRTH_INCOMING_DAMAGE_GROWTH` |
| Matter reward | `1.16^t` | `REBIRTH_MATTER_GROWTH` |
| enemy speed, spawn rate, enemy cap, Elite rate | `1 + 0.035t`, `1 + 0.09t`, `1 + 0.10t`, `1 + 0.07t` | `defaultRebirthProfile` |
| threat offset, opening enemies, bonus rerolls, obstacles | `12t` s, `5 + t`, `t / 4`, `1 + 0.05t` | `defaultRebirthProfile` |
| run-level Data from level 16 | `1 + 0.14t` | `REBIRTH_DATA_REQUIREMENT_SCALE_PER_TIER` |
| mutators | tier n adds the n-th: Pack Hunters, Hardened Elites, Hunter Protocol, Volatile Remains, Fractured Space, Overclocked Swarm, Entropy Tide, Armored Vanguard, Blackout, Singularity | `RebirthMutator` |

| t | H(t) | D(t) | Matter | numberScale (display) |
|---|---|---|---|---|
| 0 | 1.00 | 1.00 | 1.00 | 1 |
| 1 | 1.27 | 1.18 | 1.16 | 1.1 |
| 2 | 1.61 | 1.39 | 1.35 | 1.5 |
| 3 | 2.05 | 1.64 | 1.56 | 2.3 |
| 4 | 2.60 | 1.94 | 1.81 | 4.2 |
| 5 | 3.30 | 2.29 | 2.10 | 8.7 |
| 6 | 4.20 | 2.70 | 2.44 | 21 |
| 7 | 5.33 | 3.19 | 2.83 | 61 |
| 8 | 6.77 | 3.76 | 3.28 | 200 |
| 9 | 8.59 | 4.44 | 3.80 | 790 |
| 10 | 10.92 | 5.23 | 4.41 | 3,600 |

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

Measured with `balanceSweep` over the progression window (seeds `i × 7,919 + 13`, i = 3,401..3,500, 100 runs per
cell) on the tree that introduced this model. Arms: fresh = no Matrix, no upgrades, the Orb with the bot's loadout;
half = half the Matrix by price ([`halfMatrixRanks`](../../ball/gameplay/nucleus/src/desktopTest/kotlin/kinetickk/ball/gameplay/nucleus/simulation/EconomyGateHarness.kt))
with Refit and Honing 5, the Orb; full = every Matrix rank, Refit and Honing 10, the Pentagon with its signature Arc
Coil and the bot's own Relics; strong = full with Stormheart Capacitor, Pandemic Codex, Thunder Choir and Cinder Crown
bound from the start.

| Target | Statement | Window, 100 runs (SE) | Gate on `PopulationSeeds` |
|---|---|---|---|
| P1 | fresh, tier 0: win ≥ 0.75 | 0.92 (0.03) | 0.837 of 300 |
| P2 | fresh, tier 2: win ≤ 0.35; tier 4: ≤ 0.05 | 0.28 (0.04); 0.00 | 0.297; 0.003 |
| P3 | half, tier 5: win in [0.30, 0.85]; tier 8: ≤ 0.10 | 0.90 (0.03), above the band; 0.05 (0.02) | 0.897, above; 0.070 |
| P4 | strong, tier 10: win ≥ 0.80 | 0.84 (0.04); 0.85 (0.03) over 200 | 0.773 of 150, below |
| P5 | full, tier 10: win in [0.30, 0.85] | 0.70 (0.05); 0.75 (0.03) over 200 | 0.573 of 150 |
| P6 | win share never rises with the tier for a profile | holds for all four arms (table below) | holds (fresh 0/2/4, half 5/8) |
| P7 | maxed Honing ≥ ×1.8 weapon hit, maxed Refit ≥ ×1.6 character hit, full Matrix ≥ ×1.8 offense and effective integrity | ×2.26, ×1.97, ×3.56 / ×3.16 | in play: Orb weapon side ×2.01, ability ×1.84; Pentagon ×1.61, ×2.10 |

| Tier | fresh | half | full | strong |
|---|---|---|---|---|
| 0 | 0.92 | 1.00 | 1.00 | 1.00 |
| 1 | 0.72 | 1.00 | 1.00 | 1.00 |
| 2 | 0.28 | 1.00 | 1.00 | 1.00 |
| 3 | 0.12 | 0.99 | 1.00 | 1.00 |
| 4 | 0.00 | 0.98 | 1.00 | 1.00 |
| 5 | 0.00 | 0.90 | 1.00 | 1.00 |
| 6 | 0.00 | 0.71 | 0.99 | 1.00 |
| 7 | 0.00 | 0.32 | 0.98 | 1.00 |
| 8 | 0.00 | 0.05 | 0.93 | 0.97 |
| 9 | 0.00 | 0.01 | 0.86 | 0.93 |
| 10 | 0.00 | 0.00 | 0.70 | 0.84 |

Two targets are not met by the first cut. The half arm misses P3's tier-5 ceiling: its tier-5 runs are safe (10 %
deaths) and its losses arrive with tier 6's Overclocked Swarm, so no smooth change to `H` or `D` moves tier 5 without
moving tier 10 more (the tier-5 win share stayed at 0.87-0.95 for `D` from 1.15 to 1.20 and `H` from 1.21 to 1.35).
The strong build sits on P4's floor (0.84-0.85 on the window, 0.773 on the gate's 150 runs); its losses are deaths
between 250 s and 720 s, before the Architect. `P2` and `P4` pull `H` and `D` apart: on 200 window runs, `D` 1.17
lifts strong tier 10 to 0.88 but fresh tier 2 to 0.35, and `H` 1.24 gives 0.89 and 0.36. The same tree dealt the half
arm about 4.7 Legendaries a run at tier 5 and the full arms about 6.7 at tier 10, which the rarity rework cuts sharply,
so recalibrate on the integrated tree before reading P3-P5.

## Calibration recipe

1. Play the decisive cells on the progression window:
   `balanceSweep "-Psweep.cells=fresh:0,fresh:2,fresh:4,half:5,half:8,full:10,strong:10"`; then the full table
   (`-Psweep.tiers=0..10`) for P6.
2. Fresh tier 2 (P2) moves with `D` (`damage` knob): it is death-limited. Raise `damage` until fresh tier 2 sits near
   0.28 with fresh tier 0 above 0.85.
3. Full and strong at tier 10 (P4, P5) move with the deep Bulwark nodes (Bastion, Aegis) and the level-10 upgrades
   (`honing`, `refit`, `refitIntegrity`); `H` (`health`) mostly lengthens the regular crowds, since capped boss fights
   do not depend on it. Keep strong at least two standard errors above 0.80.
4. Half at tiers 5 and 8 (P3) moves with what the half Matrix lacks: the deep nodes and keystones. If P3 and P4
   conflict, give the second half of the Matrix more of the defense rather than changing `D`.
5. Confirm the economy with `-Psweep.mode=campaign` (median 40-70 runs); scale Matrix prices together (`costScale`
   tries a factor without editing content) and keep the Refit and Honing bases on `MatterIncomeProbeTest`'s
   derivation.
6. Write the chosen constants into their content homes, update this page's tables, and restate the gates under the
   [balance policy](policy.md).
