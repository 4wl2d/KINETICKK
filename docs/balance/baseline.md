<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Product and balance baseline

This record holds the product behavior the game keeps, the intentional changes
release by release, and the balance trade-offs the owner accepted. The
Gameplay balance tests cite it as `baseline.md`; the rules that choose their
seeds, thresholds and calibration windows are in [`policy.md`](policy.md).

## Product behavior

The game keeps the following externally observable behavior unless an item
appears under **Intentional delta**.

### Routes and workflow

All six screens are available: Home, Gameplay, Settings, Workshop, Rebirth and
Codex (the Workshop replaced the Lab, Armory and Hangar in 0.5.0, see below).
Home and Gameplay are base destinations; the other four are single overlays.
Replacing an overlay never builds a feature-to-feature stack.
Opening an allowed overlay during a running game pauses that run. A choice
blocks overlays; Game Over and Victory allow only Rebirth. Closing Settings
propagates the persisted preferences to an active run. Restart and completed
Rebirth start a fresh run from the latest Profile snapshot.

The keyboard mapping is `S/W/L/A/H/B/C/I/M/Escape/Enter` for Settings, the
Workshop, the Workshop's Matrix, Weapons and Characters tabs (H from Home only),
Rebirth, Codex (C or I), mute, back, and contextual enter. While a run is
running, keys bound to gameplay actions reach the run first.

### Profile

The full `PlayerProfile` remains the canonical source for preferences, economy,
loadout, Core Matrix ranks, collection, and Rebirth progress. All accepted mutations
publish their complete next value before persistence is attempted. Persistence
failure or uncertainty never rolls an accepted value back and does not cause an
automatic new write. Rejections change neither state nor persistence.

The Profile mutation matrix covers preference normalization, sound/music mute,
Matrix purchases and reset, core-shape selection, weapon purchase/equip, Refit and
Honing purchases, key bindings and display preferences, Rebirth advance,
gameplay-progress merge, bootstrap, and the test-only replacement seam.
Production code has no arbitrary replacement; bootstrap semantics stay
validated.

### Gameplay and content

Seeded gameplay remains deterministic for an equal captured bootstrap, seed,
and pulse trace. Simulation stays at 120 Hz with at most 48 fixed steps per
render frame. Current caps remain enemies `120`, projectiles `650`, pickups
`420`, trail points `110`, and visual-FX cues `2048`. Content now owns the
12-minute boss clock and its encounter/progression profile. Active-time goals
are 12–15 minutes including the boss; no hard defeat timer is introduced.
Fatigue recovers from actual sustained turns or low-speed Brake, while lateral
and counter-thrust remain available at exhaustion. Gameplay owns twelve character
abilities, optional world-fixed trials and bounded relic synergies. Codex reads
one coherent immutable build summary and preserves pause/reward selection.

The catalog has 287 hand-authored items (the 18 active items at ids `269..286`,
each carrying one of the 18 active effects), 24 weapons, 32 Core Matrix nodes,
22 Relics, 19 enemy types, twelve characters, twelve Apex abilities and Rebirth
levels `0..10` (see the build-system, 0.4.0 and 0.5.0 intentional deltas
below). Stable declaration order is captured as explicit stable IDs;
consumers receive immutable versioned snapshots instead of reaching global
catalog objects.

### Audio

All sixteen existing semantic cues keep their frequency, duration, gain, and
wave mapping. The music sequence and `0.32s` step remain unchanged. At most 32
caller cue requests are accepted and 3 caller sound requests are selected per
advance; the independent internal music tone is separate. Invalid tone
requests remain rejected. Normal cue and music behavior is unchanged;
failure-path observability is intentionally tightened as described below.

## Intentional delta

The build system replaced the generated 400-item stat catalog, the 40 rank-only
Relics and the 12 Relic synergies. Items are Trigger–Condition–Effect clause
data over archetype tags and five status elements with threshold Breaks and ten
reactions; 20 Relics rewrite rules with mandatory liabilities, ranks `1..3`,
key-predictable harmony/dissonance and 20 named pairings; offers are
build-aware with a separate reward random stream; Rebirth gates content, rolls
Reborn cards and carries heirloom Relics. Seeded traces therefore differ from
the previous baseline and were re-baselined once. Profile saves from the
retired catalog keep all progress except item and Relic discoveries, which no
longer map to the new content. The kill sweep now removes only enemies whose
death was resolved, so a kill caused during the sweep resolves on the next step
instead of being dropped.

The 0.4.0 rework lands in stages. Its contract step raises impact run growth
from `1 + 0.6p` to `1 + 1.2p` (p is progress toward the boss clock) and gives
character abilities and weapon-based item effects their own ability power: the
weapon's power, level, mastery, Overdrive and Rebirth factors with ability run
growth `1 + 1.0p`. Hits of the equipped weapon keep weapon run growth. Reward
cards price weapon-based effects with the same ability power. The content
version is `kinetickk-content-3`. Rebirth tiers now carry cumulative mutators,
a pack-size bonus and an obstacle-density multiplier; the RICOCHET and SLAM
triggers, enemy disable and silence timers, the hostile-damage helper, the
variable Core radius (default `16`) and the obstacle queries exist, but no
gameplay reads or fires them yet. The seeded baseline trace is unchanged. The
no-POI probe now runs its fixed ten seeds and requires at least five wins;
six win, and seed `23770` no longer does.

Its first wave changes enemies, elements, frame pacing and permanent upgrades
together.

- **Enemies and spawning.** Content owns the roster: nineteen enemy types with
  threat, time and Rebirth gates and spawn weights, ten of them new (Swarmling,
  Bomber, Lancer, Sniper, Aegis, Minelayer, Phantom, Broodmother, Reflector and
  the Herald mini-boss). A spawn director on its own random stream replaces the
  fixed-interval single spawns and the stage roll table: pacing phases bank
  budget for packs, flanks, swarms, walls, rings and ambushes placed in sectors
  around the heading, and leashed enemies are moved back to spawn distance
  instead of deleted. Elites jitter and roll their aspect over all seven
  elements from that stream, and Heralds arrive at 360 s and 540 s on their
  tiers. Every hostile shot, contact, strike, blast and mine deals damage
  through the hostile-damage helper, so enemy damage grows with the run and
  falls with Hollow; strikes, lances, blasts and mines hurt at any Core speed.
  Each run takes its seed from the application's seed source (the default
  source keeps the fixed seed). Multi-member events hold the Warden cap for
  every member, only Swarms bring Swarmlings and a Herald's repeat escorts fill
  only free ordinary room. The comparative fixture pins the Elite aspect it was
  measured against; with the grown hostile damage every form still keeps at
  least three boss-proof build directions fresh, with Matrix upgrades and at
  Rebirth.
- **Elements and reactions.** Sonic (Resonance: more knockback and impact
  damage taken; Shatter: a radial throwing shockwave) and Void (Hollow: the
  enemy's damage output falls and it remembers a share of damage taken; Unmake:
  the remembered damage strikes again ×1.6 after `0.35s`, capped at 40 % of
  maximum integrity, a weighted share for bosses) join the five elements.
  Eleven reactions cover the new pairs, so every one of the 21 element pairs
  reacts. "Boss" in these rules means the Architect and the Herald: Seismic and
  Stasis stun or stop only ordinary enemies (a stopped enemy keeps drifting on
  its momentum while it bleeds off), Silence stops attacks for `2s` while a
  boss falls silent for `0.75s` and then ignores Silence for `2s` (so chained
  Silences cannot keep it from attacking), and Oblivion executes weakened
  non-boss enemies through tethers like every execute. Hollow never cuts an
  enemy's damage below half; its memory ends with its last stack, however that
  stack is spent. Stasis spends hostile shots and mines in its reach instead
  of removing them mid-walk. Thunder Choir (Sonic) and Null Covenant (Void,
  maximum integrity −10 % of the whole ceiling, raises included) join the
  Relics, items `80..89` are appended and the enemy status layout grows to 71
  cells.
- **Frame pacing.** The simulation is density independent: the interaction
  boundary divides the viewport and the pointer by the display density, so
  world units, speeds and spawn distances are dp on every display, and the
  world layer is drawn magnified by the density (the HUD keeps device pixels
  and `uiScale`). Tests run at density 1 and are unchanged. The render model
  publishes an interpolation alpha (accumulator over the fixed step, `1`
  outside a running phase and until the first step after a run starts or
  resumes) and previous positions; renderers draw moving things, the new
  enemies, telegraphs, tethers and mines included, between the last two
  simulated states, and continuous animations run on the matching interpolated
  time. A running frame whose admitted time stays below one step only advances
  the accumulator on a scalar copy and reuses every projection. Step admission
  tolerates two Float ulps, so an input on a nominal step boundary lands on the
  same step at 30, 60, 144 and 240 Hz; at 50 Hz input the step timing is
  unchanged. World rebases also shift pickup, camera, Morningstar and orbital
  previous positions. The 120 Hz step, the 48-step cap and the `0.1` / `0.3` s
  clamps are unchanged; the version is `0.4.0`.
- **Core Matrix.** The Core Matrix replaces the Lab (the Lab route, the L key
  and the screen slot stay). Its 32 nodes in four branches carry per-rank
  prices, any-of prerequisites, Rebirth-rank gates and typed effects that every
  run applies from content at start; the Lab's hard-coded run multipliers and
  its hidden extra reroll per four Data Archive ranks are gone. Purchases check
  the rank maximum, the Rebirth rank, the prerequisites and Matter in that
  order; Reset refunds every rank. Loading checks only node membership and rank
  maxima, clamping ranks above a lowered maximum and refunding them, so a
  rebalance never wipes a save. A save that still carries Lab ranks decodes
  with their full spend refunded into Matter and an empty Matrix. The Matrix
  damage multiplier applies once per damage instance, so bases derived from a
  hit, Hollow memory included, are measured without it. Matrix effects reach
  every new path: enemy integrity applies to every spawn
  (director events, escorts, Heralds, Broodmother births, Splitter fragments
  and trial defenders), contact damage to strikes, Second Wind to every lethal
  hit and Elite Bounty to every Elite or Herald kill, executes included. Total
  kills, ricochets and Data pickups join the cumulative Profile achievements;
  Gameplay publishes them with any other progress or when the run banks.

Seeded runs differ from the previous baseline: the seeded baseline trace keeps
its Core path and only its opening Drifters move. To keep tier 0 at the
previous difficulty the director banks one enemy per tempo interval at phase
rate 1 and keeps spawns clear of offered anomalies. The no-POI probe counts
only victories that skip every POI; the reference seed and at least five of
the fixed probe seeds win.

The second wave adds world obstacles, a minimap, a wider Data magnet, six more
characters with the Hangar, and 83 items.

- **Obstacles.** Each run generates asteroids, bumpers and, under Fractured
  Space, unstable asteroids in a five-by-five window of 1200-unit chunks around
  the Core, hashed from the run seed and the absolute chunk (an accumulated
  rebase origin keeps chunks stable across rebases); the first 900 units around
  the run origin stay clear. The Core sweeps against them right after it moves.
  The collision bands are cursor reach, not fixed speeds: a collision starts to
  hurt at the steady speed that cursor reach `0.45` of the viewport's long axis
  sustains and is lethal at the speed of reach `0.72` (Matrix lethal bonuses on
  top), whatever the heading; tier 0 starts 80 faster and deals 70 % (both fade
  by tier 3). Measured flight along the long axis puts reach `0.35` below the
  band, `0.6` inside it and `0.79` past it on 1280×800 and 412×915; the short
  axis only flies slower, and holding the cursor near the edge no longer flies
  free. The lethal speed never drops below a Dash from a reach-`0.35` cruise
  plus 60, so such a Dash can hurt but never kills. Collision damage is a share
  of maximum integrity, grows `1 + 0.04t` instead of the Rebirth
  incoming-damage multiplier, ignores damage reduction, is absorbed by shields
  and is never cancelled by facets; a lethal collision zeroes integrity past
  shields unless Second Wind or the Ouroboros Seal answers it (only a revive's
  own invulnerability holds it off, and the 0.25 s hit cooldown never does);
  Glass Comet's liability doubles collision damage and keeps shields out. A
  bumper throw stays below 90 % of the lethal speed. Bounces at the
  damage-start speed or faster count ricochets and fire RICOCHET (a bumper at
  most once per 1.5 s); enemies slide around obstacles, are pushed out of them
  and take slam damage (firing SLAM) when thrown into them or into each other;
  asteroids stop shots; spawns, totems and anomalies move clear. The ambush
  waits for 75 % of the damage-start speed. A drawn asteroid's edges lie on its
  collision circle.
- **Minimap.** The HUD shows a north-up minimap around the Core (a Settings
  toggle, on by default; Blackout halves its range). The world keep-out reserves
  it in every layout.
- **Pickups.** The magnet radius is 320 (item floor 120) and Data is collected
  within 56. A pickup entering the radius latches and homes faster than the Core
  until collected, without fading on the way; a level-up and the end of a
  choice latch every on-screen Data. Keys and Relic resonances never expire,
  and at the cap they evict the oldest Data. Event Horizon's liability now removes the latch and the level-up
  vacuum instead of halving the radius; Magnet Lens gives +90.
- **Characters and the Hangar.** Twelve characters (Star, Spiral, Dipole, Pentagon,
  Crescent and Hexagon appended in stable order) each own a distinct mechanic,
  trigger, base traits and difficulty from content; at most four use Dash as their main
  trigger and only the Diamond uses Brake. The Prism's brake barrier is gone: four
  facets each negate one hurt window, regenerate on a paused six-second timer and never
  cancel damage kinds marked non-cancellable (obstacle collisions). Orbit, Puncture,
  Halo, Parry (with Edge) and Lattice are reworked (Brake no longer collapses the
  lattice). Dipole's ATTRACT only adds a pull to enemy steering; REPEL throws what it
  pushes (and what the Core strikes) for 0.6 s after the push ends. Ability
  magnitudes are calibrated by seeded 90-second balance fights (Drifter packs at
  random bearings, content bolt and contact damage, exact and 0.05-0.15 s jittered
  Dash and brake timing) that keep every offensive character's averaged ability
  output within 40 % of their median. The Prism and the Diamond are defensive
  characters and the accepted rule leaves them outside that band (content damage
  caps their prevented totals near a fifth of the offensive median): they are
  accepted by the defensive criterion instead, the Prism losing at most 60 % and
  the Diamond at most 75 % of the integrity the Orb loses in the same fights
  (measured: 44 % and 23 % with exact timing, 36 % and 57 % jittered). Character state is
  copy-on-write: a reduction forks scalars and shares the echo ghost path, lattice,
  drones and scars until its first write. The echo path is paged, so a fixed step
  copies at most the small page its sample lands on (and the page table once per
  reduction); the small lattice, drone and scar arrays are copied at most once per
  reduction. A replayed teleport moves the ghost without striking along the gap.
  New unlocks count total kills, ricochets, Data pickups and the Reborn
  rank; no existing unlock target rose. The six Home form tiles became one Hangar card,
  and the Hangar overlay (H, from Home only) lists every character with its unlock
  progress and selects through the Profile.
- **Items.** The item catalog grows to 173 (bound 192). Items `90..172` are
  appended: six Sonic and six Void items, ten that answer ricochets and slams
  (five each, every clause rate-limited), twelve weapon-support items (the first
  attack-speed stats, an infusion for each of the five original elements, a kill
  refund and a detonating legendary), one item for each Sonic and Void reaction,
  six Fortune items, four for each archetype and four high-risk Epics. Commons
  and Uncommons are open from the start; each Rebirth tier opens four more (the
  Rares, the Epics, six new legendaries and one Uncommon at tier 10). A reaction
  clause naming two elements fires only for the reaction between them, and one
  named element now matches every reaction involving it instead of only the
  reaction's first element. ARSENAL III adds one stack of the weapon's element
  (the dominant element while weapons carry none) on every fourth weapon hit,
  counted rather than rolled, and Breaks deal 25 % more damage; it no longer
  repeats the dominant infusion on every third hit, and without an element it
  adds nothing. Only one card-adding item (Gambler's Ledger, Cornucopia Core) is
  offered per run. Data from a level-up clause joins the level loop that fired
  it, and overheat locks the Dash at full heat before its clauses vent. A
  desktop test flies every item through scripted encounters and requires a 5 %
  change in its favor at one stack (a fallen Core keeps no integrity and still
  meets the scripted hits it missed; a liability alone scores nothing); the
  items it caught were retuned (Rime Rounds, Cold Snap, Null Rounds, Graviton
  Seed, Permafrost, Flashpoint, Killstreak Engine, Deadeye Reticle, Weakpoint
  Scanner, Detonator Spur, Quiet Engine, Hollow Point and Resonance Fork).
  Expansion Epics and legendaries borrow a family scene for their card.

Together these rules meet in a few places. A slam is one rule: an enemy the
Dipole's REPEL threw (or the one it strikes) takes the Dipole slam, any other
throw the obstacle slam, and both share one per-enemy cooldown, so each contact
deals one slam and fires SLAM once; thrown enemies meet obstacles only in the
obstacle pass, at their normal speed. REPEL-thrown enemies no longer slide
around rocks, and a landed obstacle slam ends the throw. A collision pauses
facet regeneration and costs Stellar Mass like any other hit, while no facet
ever cancels it. Every Blink (the Crescent's, and Blink Drive's on any
character) stops before an obstacle on its line and marks its step as a
teleport, so the Spiral's echo jumps it, and every spawn point is nudged clear
of obstacles. A phased Phantom takes part in no slam and a shifting enemy fires
no SLAM; a slam whose Data opens a level-up choice ends the fixed step. The
asteroid Dash floor counts the stronger on-beat Pentagon Dash, and a Dash's
Tesseract origin and Pentagon beat end with its phase, so a Gravity Surfer
phase never revives them. In the combined game the
Orb's baseline damage in the impact encounters rose and two crit-damage items
fell under the 5 % floor, so Weakpoint Scanner (`+120 %` against any status)
and Harmonic Lens (`+125 %` against Sonic, `+140 %` since the final weapon balance)
gained crit damage; the impact
encounters never come within 60 of an obstacle. The balance fights still hold every offensive character within 40 %
of the median. The no-POI bot steers around obstacles, skips ricochet and slam
items (it rarely bounces), passes on the Furnace Covenant and keeps wider of
skipped POIs; the reference seed and seven of the ten probe seeds win.

The weapon contract step types the roster before the weapons wave. Twelve
`WeaponId` entries are appended (24 in all, bound 24), each with a family, an
element, two build tags, a Relic key, a scaling stat, a cadence, a signature
character, three mastery forms and a signature twist; the free-form weapon tags
are gone. Every weapon key rides on at least two Relics, one of them ordinary,
and every character is the signature of exactly two weapons. The original twelve
keep their Matter prices; the additions cost 120 to 2,600 and eight of them need
Rebirth rank 1 to 9, which purchases enforce (`WeaponRebirthLocked`); the
loadout projection carries the rank, so the Armory shows such a weapon as
Rebirth-locked with its action disabled, and the Home Armory stamp ignores it.
The new weapons equip and idle until their behavior lands. Totem recalibration
and point of interest weapon offers now deal only weapons the profile owns: a
profile that owns only the starter can amplify but no longer recalibrate, a
weapon reroll is offered only when more owned weapons remain than one offer
shows, and a weapon's point of interest direction follows its first tag. A point
of interest lists only directions with an owned weapon behind them and, with
none, offers Amplify alone. The no-POI runs own the original
twelve weapons, so their recalibration offers match the earlier ones; the seeded
trace and the probe results are unchanged.

The third wave gives every weapon its behavior and synergies, shows them on the
weapon screens, and turns Rebirth into tier selection.

- **Weapons.** All 24 weapons get their behavior and the weapon synergy rules.
  Effective weapon power multiplies tag resonance (+5 % per point in the weapon's
  two tags, at most +50 %), the signature character (×1.2; the run starts one weapon
  level up and the automatic ceiling rises by one), Relic key harmony (×1.2 while a
  bound Relic, the heirloom included, carries the weapon's key; that Relic's upside
  counts ×1.1), the scaling stat clamped to `[1, 1.6]` (CRIT adds 15 % crit chance
  to the weapon's own hits, STATUS deals ×1.4 against statused targets, an
  Overdrive-scaling weapon takes ×1.5 in Overdrive instead of the generic ×1.45),
  weapon run growth `1 + 1.6p` and the mastery bonus, now 0, +10, +20 and +35 % with
  concrete forms at levels 3, 6 and 10. Continuous and contact weapons, and
  activations a trigger drives (Dash cones, impact shockwaves, brake tolls, Dash
  rifts, Blink spears), scale damage and rotation with three quarters of the
  attack-speed, mastery and build activation gain; discrete weapons keep
  `cooldown()`. Kinetic weapons adopt the dominant element at affinity tier 2 on
  every third qualifying hit. Proc rule: an activation qualifies only its first
  landed hit and a persistent source (trail, aura, contact, tether, beam, sentry) at
  most once per 0.25 s; only qualifying hits fire `WEAPON_HIT` clauses, Echo
  Chamber, the ARSENAL counter and Relic on-hit effects, and a weapon's own element
  lands only with damage. Character hooks take every contact hit and continuous hits
  once per enemy per 0.22 s. Weapon offers deal owned weapons weighted toward
  signature and tag fit. Weapon state lives in one `WeaponRuntime` (bounded entity
  list, proc gates, counters) forked and rebased with the run; the render model
  gains trailing `weaponEntities` and `weaponSynergy` projections, and trail points
  carry their life and strength. The pause board lists the active synergies (on
  phones while they fit).
- **Weapon numbers.** Calibrated in `WeaponBalanceTest` so that no weapon dominates:
  each weapon stays within ±35 % of the median on its family's primary metric and
  between half and 1.35 times the median on the other, at a fresh level-1 checkpoint
  and at an Ascended level-10 reference block; in each fight no weapon of the other
  families out-damages the best weapon of that fight's own families; the geometric
  mean of a weapon's two shares stays at most 1.3; and Arc Coil leads neither fight.
  +25 % attack speed adds at least 12 % to every weapon; one `WEAPON_HIT` item procs
  at most 1.5 times per base cooldown among forty enemies. They depart from the
  design sketch: Flux Wake 22/s with Scorch every 2 s (more than six enemies in the
  trail share six enemies' worth), Morningstar `13.8 + 0.036·speed`, Phase Lattice
  55/s over a wider band, Null Lance 68, Gravity Mines 68 with a proximity trigger,
  Ion Swarm 4.5 (fire rate ×1.25 at its third form), Rift Blades `18 + 0.018·speed`,
  Arc Coil 16 falling off ×0.9 per hop, Quasar Cannon 87, Entropy Field 11/s,
  Singularity Spear 100, Prism Relay 18 with 2, 3, 3 and 3 bounces (the twin relay
  carries 20 %; the third form adds no bounce and bursts 12 % within 60), Gyre Disc
  45 (radius 11, ×1.65 at the first form), Scatter Bloom 10 per pellet with a passive
  volley of seven, Tether Arc 5.5/s ramping ×3 (×8 at its third form), Sentry Grid
  4.8 every 0.6 s with a 32 % splash within 40 of the struck enemy, Hunter Salvo 9,
  Concussion Engine 24 (a lone enemy takes 70 % of a wave, plus 18 % of that for each
  other enemy it catches, up to four), Glacier Bell 37, Event Lens 15/s and 32 on
  collapse, Solar Sweep 41/s (the nearest enemy in a beam burns at full heat, the
  ones behind it at half; third-form arcs 90 %), Aegis Shards 13 per shard, each
  piercing on to three more enemies it has not struck (four at the second form) and
  keeping 90 % of its damage at each, Hollow Rift 54 and Resonance Drum 30. The
  dominance checks retuned eight weapons (single/crowd share of the median, fresh
  then Ascended): Sentry Grid 1.14/1.16 and 0.99/1.17 (from 2.66/0.97 and 2.38/1.18),
  Aegis Shards 0.92/0.75 and 1.17/0.95 (from 2.24/0.91 and 2.95/1.30), Ion Swarm
  1.21/1.07 and 1.17/0.96 (from 1.71/1.30 and 1.96/1.24), Prism Relay 1.00/0.52 and
  1.08/1.21 (Ascended crowd from 1.96), Concussion Engine 1.18/1.05 and 1.02/0.84
  (fresh single from 1.49), Solar Sweep 1.30/1.07 and 0.80/0.93 (fresh crowd from
  1.48), Flux Wake 1.21/1.15 and 0.93/1.04 (fresh crowd from 1.47) and Gyre Disc
  1.03/1.08 and 1.26/1.13 (fresh crowd from 1.30). Each fight's leader belongs to its
  own families with at least 7 % to spare (fresh: Solar Sweep 1.30 single against Ion
  Swarm 1.21, Hollow Rift 1.24 crowd against Flux Wake 1.15; Ascended: Gyre Disc 1.26
  single against Ion Swarm and Aegis Shards 1.17, Morningstar 1.31 crowd against
  Prism Relay 1.21), the highest geometric mean is 1.19 (Gyre Disc, Ascended) and Arc
  Coil sits at 1.29/0.54 and 0.73/0.96. The comparative boss fights and the item
  impact encounters are chaotic, so neither picks a weapon number: a build direction
  is viable when it clears all four comparative encounters from at least two of three
  fixed seeds, and an item whose best gain lands within 2.5 points of the 5 % floor
  is measured again over fifteen fights. Harmonic Lens grants `+140 %` crit damage
  against Sonic (the 4× crit multiplier cap limits what more would add) and measures
  +6.5 % over fifteen fights; its gain still follows the arena weapon's cadence (in
  the weapon-only encounter a Sentry Grid firing 10 % slower takes it from about
  5.5 % to 2.5 %). With Ion Swarm and Prism Relay in
  the no-POI bot's weapon order no Rebirth gate flipped (no difficulty knob moved) and
  the no-POI probe wins all ten of its seeds.
  Forms and secondary numbers follow the same calibration, and the mastery form
  texts state them: Flux Wake's trail doubles its width and lasts 4 s, Gravity Mines
  gain 30 % radius, Ion Swarm shots pierce one enemy from the start and one more at
  the second form, Quasar shots grow 10 %, Scatter Bloom pellets pierce two (three
  at the second form) and keep their pierce point-blank, Tether Arc's second tether
  deals 75 % and charges every 0.8 s, Hunter missiles burst for 75 % within 110
  (×1.2 at the second form) with Decay on every second hit, Glacier Bell's passive
  pulse reaches 170 at 60 %, Event Lens's second lens carries 25 % and its well
  implodes for 10, Solar Sweep gains 120 length and scorches every 1.5 s, Entropy
  Field decays every 1.6 s and the Drum knocks back 240. Orbital counts no longer
  grow with the level (the level is in the power): two, one more at Ion Swarm's and
  Rift Blades' first form, more with Agony. Mines and lenses space their Gravitic
  Mass per enemy (1.5 s and 2.5 s) and wells add none, so Collapse cannot cascade.
  Null Lance, Quasar Cannon and Singularity Spear aim at an enemy within 0.5 rad and
  700 of the heading. Weapon cooldown refunds (Hair Trigger, Autoloader) also
  advance a weapon's own timers (Scatter Bloom's volley, Glacier Bell's toll and
  pulse, Hollow Rift's passive rift, Concussion Engine's shockwave); contact weapons
  surge like continuous ones. The Spiral's ghost shockwaves keep the Core's 0.35 s
  cadence as a second source, orbitals accumulate their phase (a speed change never
  jumps them), and a returning Gyre disc with the third form glances around a rock.
  The perceptible-impact arenas that carry a weapon fly both Ion Swarm (a volley)
  and Sentry Grid (a persistent source) and keep the better result, so `WEAPON_HIT`
  items are measured under the proc rule; the comparative scenario keeps three
  viable directions for every form, each viable from at least two of three seeds.
- **Weapon screens.** The Armory lays all 24 weapons on a scrolling grid that brings
  the inspected weapon into view when it opens and whenever the selection moves,
  keeps rows at least 84 px tall on narrow phones, tints each icon with its element,
  names a Rebirth-gated weapon's rank on its tile and marks the selected character's
  two signature weapons with its form. Its detail panel adds the three mastery forms
  by level, a synergy block read from content and the Profile alone (the signature
  character and its twist, the heirloom candidates whose keys harmonize, the tag
  resonance) and the typed facts (family, element, Relic key, scaling, cadence); the
  board frame pins the action under the scrolling sheet. A locked unlock stays
  pressable and names why (Matter short or Rebirth rank) in a polite live region, as
  does a purchase the Profile refuses; neither reaches the Profile unless the screen
  believes it can succeed. The Hangar's signature row lists the shown character's
  two weapons with their owned, starter, Rebirth or price state and any heirloom
  harmony. Codex weapons are always named, an unowned one keeping the locked look,
  with their facts, cost and Rebirth rank; during a run the detail reads the run's
  character, bound Relics and per-tag points as active only for the equipped weapon,
  and outside a run the next character and the heirloom candidates. Weapon offer
  cards and Totem weapon rows name the family, element and tags and mark the run
  character's signature weapon. The synergy figures these screens quote (+20 % once
  for the signature and once for harmony, +10 % for each harmonized Relic, +5 % per
  tag point up to +50 %) are presentation copy; Gameplay owns the arithmetic. The
  policy row for the Armory now names that grid (one tile per catalog weapon, at
  most 24) in place of the old three-tile page slice.
- **Rebirth.** The Rebirth rework replaces advancing with tier selection. The
  Rebirth rank is one above the highest cleared tier (at most 10), and a player may
  select any tier from 0 up to that rank, so `highestCleared` may exceed `level`:
  Profile construction, the codec and gameplay start validation enforce one
  invariant (`highestCleared` in `-1..10`, `level` in `0..rank`), and a run's clear
  is checked against the rank rather than the live selection
  (`ClearedLevelAboveRank`). `ProfileRebirth.selectRebirthTier` replaces
  `advanceRebirth` (`RebirthTierLocked` above the rank; reselecting the current tier
  is accepted and confirms it); Session sends `SelectRebirthTier(tier)` and keeps
  the two-press confirmation only for a tier above the highest clear. Unlock gates
  (items, Relics, point of interest items, Heirloom cards and the build summary,
  plus weapon, character and Matrix purchases) read the rank, while tier scaling,
  Reborn odds, the Warden cap, the opening and the roster read the selected tier, so
  dropping to tier 0 keeps every rank unlock. Tiers follow new formulas (enemy
  integrity `1 + 0.32t + 0.05t²`, incoming damage `1 + 0.14t + 0.008t²`, speed `1 +
  0.035t`, spawn rate `1 + 0.09t`, cap `1 + 0.1t`, Elite rate `1 + 0.07t`, threat
  `12t` s, Matter `1 + 0.2t`, power `1 + 0.03t`, no integrity bonus, `t / 4`
  rerolls, `5 + t` openers) and short names instead of directives. Mutators added
  here: Volatile Remains (ordinary enemies burst 0.35 s after death, 6 damage in
  r70, escaped at 400 u/s or in a Dash; at most 48 pending), Entropy Tide (healing
  −40 %, shield regeneration −30 %), Armored Vanguard (one ordinary spawn in five
  plated, rolled from a stateless hash of the run seed and enemy ID so the spawn
  stream is untouched; weapon damage −40 % until an impact cracks it), Blackout
  (Data the magnet has not caught fades after 10 s) and Singularity (a fourth,
  enraged Architect phase at quarter gates and a Herald escort). The Architect's
  per-hit cap is 1.5 % of its tier-0 integrity (its integrity divided by the tier's
  integrity multiplier), less 2 % of that share per tier (tier 10 keeps 80 %), so a
  build that hits the cap also needs more hits on a harder tier. A tier-10 clear
  records the character as Ascendant (optional sorted `ascendedCharacterIds`,
  written only when non-empty). The Rebirth screen is a tier ladder; Victory names
  the rank it earns, Home shows the rank badge and the HUD the active mutator
  sigils.

In the merged game Armored Vanguard plates blunt every weapon: a twin flight of each
of the 24 weapons against plated and bare targets measures exactly 60 % of the
weapon damage until one impact cracks the plates, then the full amount (element
damage and character abilities are not weapon damage and pass unchanged). A chosen
heirloom harmonizes like any bound Relic under the rank's heirloom count and the
Heirloom Vault. The Rebirth ladder lists exactly the weapons, characters, Relics,
items, Matrix nodes and enemies each rank opens (Glacier Bell at rank 1 through
Resonance Drum at 9; Spiral, Pentagon, Crescent and Hexagon at 1, 3, 5 and 7). The
Profile screens' weapon fixture states the calibrated mastery forms, and the
Gameplay state witness covers the weapon runtime. The reworked weapons left the
half-Matrix profile winning tier 8 in two of five seeds and the strong build
clearing tier 10 in three, so enemy integrity's quadratic tier term rose from 0.035
to 0.05 and the Architect's cap shrinks with the tier (above); the R10 enrage was
probed and stays. Eight desktop gates play the no-POI bot on five fixed seeds and
record their measured outcomes: tier 0 fresh wins 5/5 (4/5 before the final weapon
balance), tier 3 with a full Matrix and
the Pentagon's signature Arc Coil 5/5, tier 6 fresh 0/5, the half Matrix 5/5 at tier
2 and 0/5 at tier 8 (1/5 before the final weapon balance), the strong build (full Matrix, signature, Charge harmony, four
Relics, both bonuses read from the synergy projection) clears tier 10 once while
surviving a median 850 s, its tier-10 fights never beat the tier-3 median by less
than 1.3×, and a full Matrix with the Pentagon and Arc Coil clears tier 10 once.

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
`minimapEnabled` is written only as `false`; its absence means on.
The Resource and platform capability expose only `readSnapshot` and
`writeSnapshot`; there is no reset, import, quarantine,
purge, or bulk-clear operation.

Platform composition owns the only physical storage. Android uses
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
alone are observed and consumed without changing any accepted outcome. No
typed Audio result or status is fabricated.

Internal ordering changed only where it had to, such as pause acceptance before
overlay publication and accepted Profile progress before returning Home. The
rendered UX and final business outcomes remain the same.

### Release 0.5.0

Release 0.5.0 answers ten owner requests: a capped Data curve, 96 more items,
reaction behaviors, run skills, a level-42 Apex ability, permanent upgrades, one
Workshop for every upgrade screen, an evolving level badge, reaction visuals and
display and control settings. Seven elements and 21 reactions stay; those ten
requests weakened no balance gate, and every new feature has gates of its own.
Each new Profile field is optional and written only when it differs from its
default, so every earlier golden payload is byte-identical. Player text ships in
English and Russian. A follow-up inside 0.5.0 (content version unchanged) turns
the run skills into active items reached only through ordinary item offers. It
restates the gates its reward stream turned into coin flips as population gates
whose thresholds follow from the 05885fa9 base population, which loosens some of
them (fresh survival's 0.73 becomes 0.66 and four Singularity arms get ceilings
above 1.15, for example), and it lowers the survival floors of gates D and H,
whose premise was the free lane skills (see the gate records below).

- **Data curve and the level cap.** Content owns the level table. The run level
  stops at `RunTempoProfile.maximumLevel` 42 (Content bound 99); levels 1 to 15
  keep the 0.4.0 requirement, levels 16 to 20 ramp by up to `rampPlateau` 190
  and levels 31 to 35 by `topPlateau` 0, so reaching 42 takes 15,148 Data (31
  takes 7,898; the last level 746). From level 16 (`rebirthScaleFromLevel`)
  Rebirth tier t scales each requirement by `1 + 0.14t`, rounded half up and
  never below the base, so the totals at tiers 0, 3, 6 and 10 are 15,148,
  20,933, 26,716 and 34,429. k = 0.14 is the smallest value on a 0.02 grid from
  0.10 at which no full-Matrix run that avoids Data reached 42 before 720 s at
  tier 3 or 6 on 60 population seeds, and the measured Data supply grows by
  about the same `1 + 0.14t`. Scaling the whole table instead flipped gate D
  (median 87.8 s), so the opening levels stay unscaled. Each level-up adds 2.5
  maximum integrity and repairs 7.5 (`levelIntegrity`, `levelRepair`). Data
  collected at the cap still counts toward `dataCollected` and charges the Apex
  instead of leveling. The render model carries `maxLevel`. The Data levers
  are content numbers: Data Prospector GainData 2.5, Data Siphon DATA_GAIN 0.12
  with 40 per Elite, Windfall Sensor 0.12, Magnet Lens 0.08, Jackpot Relay
  GainData 3 and Decay Spores up to 8 Data per cast. Each Data Archive rank
  gives +15 % Data, deals three more rank-0 Data items into the run's draft deck
  (`MatrixEffect.DataStratum(3)`; the deck's Data floor of 4 becomes all 13 at
  rank 3) and adds 1.5 to the offer weight of Data-economy items in every lane
  (`MatrixEffect.DataOfferWeight(1.5)`, fraction validated in (0, 2]; ×5.5 at
  rank 3). The weight draws nothing, so a profile without the node sees exactly
  the offers it saw before. Of 0.5, 1, 1.5 and 2, 1.5 is the smallest that
  brought at least 85 % of 60 population seeds' Data-seeking tier-0 runs to 42
  inside [510, 630] s with at most 5 % early: 54 of 60, and 51 of 60 on a
  holdout set, against 5 of 60 without the weight.
- **Items.** The catalog grows from 173 to 287 (bound 320). Items `173..268` are
  appended in eight themed batches: 21 reaction keystones (exactly one Epic or
  better per reaction, declaring both its elements), casting and Apex items,
  marks, fields and terrain, Core movement, one item per weapon family, and
  wardens, wagers and legends; 23 of them are gated by Rebirth rank. They use
  the new rule vocabulary instead of flat stats. Items `269..286` are the 18
  active items (below). Items whose effects wait for an active item or another
  weapon are Dormant until the run can use them; offers weigh them down and the
  Codex says so. Each run deals a draft deck from its own stream (run seed xor
  `0x0DEC_C0DE`), so reward draws keep their count and order while the offers of
  a seed change; a rank-0 deck holds 129 to 142 of 221 items (the actives dealt
  by their tier's share; every deal floor promotes passive items only) and the
  Codex marks the rest Not in this run. A tier refills from the reserve only
  when it runs out of dealt items the run can still take. Offer lanes are
  softer: the aligned lane is taken 55 % of the time, fit weighs `1 + 0.6 ×
  min(fit, 3)`, the loadout point and an unpicked weapon family leave the fit,
  saturated tags count half and risky lifts pass a 0.36 filter. The Profile
  remembers the newest 64 first acquisitions; a remembered item is dealt with
  weight `max(0.35, 1/(1 + 0.8p))` and offered with its square root. Launch Rail
  DAMAGE 0.3 for 3 s, Echo Capacitor SkillCooldown 0.15, Overcharge Glyph
  ACTIVATION 0.3 for 3 s, Tidewater Hull DR 0.2, Homing Rifling 3 s with a 2 s
  cooldown and Like Poles FLAT 40 are calibrated numbers; Scavenger Protocol
  (DATA_GAIN 0.035, GainData 0.5), Lodestar Ledger (0.04), Mycelial Ledger
  (0.02, 0.3) and Rote Memory (0.05) carry halved Data amounts so a fresh run
  does not level past 30 by accident. Every item passes the 5 %
  perceptible-impact floor, the 173 older ones on the 0.4.0 setups alone and
  each active item in the skill arena with only itself in the loadout, its
  passive included; the impact arenas gained skill, field, mark, throw, Apex and
  family-weapon encounters, an item whose clause needs a skill tag or a
  refreshed weapon is flown with one, and the arenas' loadout actives carry an
  inert passive so the skill-support items keep the conditions they had before
  active items. The no-POI bot passes on mark, field and throw items it cannot
  aim and, when told to, seeks or avoids Data-economy items.
- **Reactions.** Every reaction gains one play-style verb and a keystone form
  that an item switches on; no reaction hits harder than before, and eight lost
  power (Thermal Shock 12 → 10, Plasma 6 → 5, Wildfire 5 → 4, Superconduct 7 →
  6, Magnetize 5 → 4, Seismic 4 → 3.5, Fracture 3 → 2.5, Annihilate 9 → 8). The
  verbs leave marks, fields, Core states and refunds behind: Spall slams, Plasma
  lances, Accretion Data, Ablaze contact, Frictionless drift, an Ice Fan, Glass
  Charge piercing, an Attractor, Overcurrent, Compost, Blast Jump, Shard Guard,
  Arc Weave, Rock Echo, Breakpoint, Hush, Flame Leap, a Time Bubble, Void
  Anchor, the Maw and Erasure. Fields now live in one table of twelve (nine for
  reactions and items, one per skill slot) with per-kind caps; a full kind
  replaces its oldest field. Compost grows a Rot Well up to 200 u and never
  shrinks one that Field Tuning already widened past it. The enemy status layout
  grows from 71 to 74 cells for one mark per enemy: its kind, its expiry and
  whether a skill cast wrote it. Wildfire's residue follows its declared
  values, Stasis no longer spends Minelayer mines, and Breaks and reactions draw
  in element colour indices 8 to 22, leaving the threat colour to damage the
  Core takes. A gate plays every verb on and off in a scripted arena and
  requires a 10 % change in its play metric (21 of 21 pass; for example Blast
  Jump distance ×2.80, Shard Guard erases 32 shots against 0, Erasure Data
  ×19.2). The Architect keeps its volleys and timers inside a Time Bubble, a
  Maw or an Attractor (24 of 24 volleys each) and keeps attack windows under any
  Hush (18 of 18, quiet share 0.275).
- **Active items.** Eighteen items (`269..286` in effect order: six Uncommon,
  eight Rare and four Epic, none gated by Rebirth) each carry an active effect
  cast from slot Z, X or C. They replace 0.5.0's skill drafts at run levels 2, 8
  and 16 and its rank-up cards: an active reaches a run only as an ordinary item
  card (level-up and Elite Bounty offers, POI item rewards) under every item
  offer rule, unlocked by the run level (Uncommon 5, Rare 7, Epic 10). Inside
  its rolled tier its weight is multiplied by `activeWeight` 1, by
  `characterActiveWeight` 3 when the effect is one of the character's three lane
  effects and by `ownedActiveWeight` 8 when the card would rank up an owned
  active, with no added reward draw; an offer shows at most one active, a POI
  item reward keeps its uniform shuffle and skips every active after the first,
  and an unowned Catalyze waits for two elements with affinity. The first copy
  binds the lowest empty slot at rank I and each further copy raises it to II
  and III, ready at once. Each active also carries one passive rule whose rank
  III gives at most what the median of its kind among shipped Rare-or-lower
  passive items gives at two stacks: ElementPotency 0.346, BreakPower 0.4225 or
  StatusDuration 1.384 by element, Catalyze's ReactionPower 0.2685 and Dual
  Infusion's damage against targets with a status 0.1152, while Slingshot
  (COOLING 0.25 at speed 900 or more) and Full Stop (DAMAGE_TAKEN −0.15 while
  braking) keep their first values. No Rare-or-lower passive adds critical
  damage conditioned on a Frozen target, so an explicit cap of 0.22 bounds Frost
  Snap's 0.219, which keeps its rank III (about 0.48) below the +50 % of Quench
  Seal, the Epic item that adds it. A new active taken with all three slots held
  opens a slot prompt: a card per held active replaces it (the old item leaves
  the run and its effects end; the new one binds there at rank I), and Keep
  salvages the new item for rarity rank × 2 Matter. Effects are cooldown-based
  (never below 2.5 s): rank I runs from 7 s (Arc Hook, Void Step) to 13.6 s
  (Null Shroud) and rank III from 4.5 s (Void Step) to 11.05 s (Null Shroud,
  Dual Infusion). Calibration gave ten effects 40 % more power, read per
  parameter (Solar Flare, Meteor, Frost Line's duration, Frost Snap, Arc Hook,
  Gravity Well's damage per second and duration, Resonator, Null Shroud's
  returned damage, Full Stop, and Catalyze's radius), and seven cooldowns 15 %
  less (Solar Flare, Frost Line, Entropic Tide, Resonator, Null Shroud at 13.6,
  12.35 and 11.05 s, Catalyze, Dual Infusion); Null Shroud keeps its 10, 14 and
  18 stored bolts, and Storm Coil, Mass Driver, Decay Spores, Sonic Boom, Void
  Step and Slingshot are unchanged. Catalyze's ranks list the targets and
  reaction scale a cast runs (8, 10 and 10 enemies, each at the forced-reaction
  ceiling of 1.3), and a Nucleus test fails when any shipped rank value passes
  the bound its cast runs through. Refunds never exceed half a cast's cooldown,
  and reactions, items and Catalysis refund them. Skill damage goes through
  `dealSkillDamage` and the Architect gate, at most once per boss per cast.
  Kinetic skill damage keeps the Resonance, Gravitic and Event Horizon terms but
  never takes Brittle's bonus, so Brittle pays once, on the direct hit that
  spends it. Decay Spores' Data cap and refund cap count only the deaths its
  cast seeded; an item's Spored mark drops its Data without drawing on them.
  Every character has a skill hook (for example the Prism casts 15 % faster and
  the Spiral's ghost repeats damage skills at 35 %). An active item's tags and
  element count once, like any item's; the drafted skills' separate tag points
  are gone. Movement skills keep the Core clear of obstacles: Arc Hook needs a
  clear line 200 past its target, Slingshot swings at most 1,100 u/s and no
  farther than its clear path, and Void Step lands at least 80 beyond the Core's
  radius from a rock. Skill Charges holds two casts, each recharging on its own
  clock. The no-POI bot takes and casts actives as a player would: a rank-up of
  an owned active always, a new one while a slot is free and, with every slot
  held, only a lane active, in place of the lowest-rank active outside the lane.
  Data-seeking profiles take Decay Spores, then Gravity Well, and a lane active
  before other Data cards, and never give up those two for a lane active. It
  casts Solar Flare at any heat once three enemies are in reach and Entropic
  Tide at six Decay stacks in reach, and Catalyze waits for three enemies
  carrying an element. Effect numbers come from a phase-free 96-seed balance
  arena that holds every rank III a third above its rank I (measured 1.55 for
  Decay Spores to 2.76 for Resonator) and every family within 40 % of its median
  at both ranks (0.64 to 1.33 at rank I, 0.62 to 1.34 at rank III); the effects
  golden pins them.
- **Apex abilities.** Each character has one Apex ability (Perihelion, Monolith,
  Vivisect, Corona, Mirror Stance, Hypercube, Hypernova, Twin Echo, Magnetar,
  Crescendo, Total Eclipse, Swarm Queen) that unlocks at level 42 with an "APEX
  ONLINE" banner, is ready at once and casts on V or the level badge. Its
  cooldown is 60 s; Data collected at the cap takes 0.03 s per point off it,
  never below 30 s after the last cast. Against the Architect and Heralds an
  activation spends at most its character's `bossStrikes`: the Ring 5; the
  Prism, Diamond, Star and Crescent 6; the Shard and Tesseract 8; the Spiral,
  Dipole and Pentagon 9; the Orb and Hexagon 12 (Content bound 12). Characters
  that kill the Architect fast get more strikes, so every Apex shortens a fight
  by a like share. A strike asks for the boss's whole hit cap: through the
  Architect gate it never passes a phase gate, nothing lands during a shift, and
  a Herald's strike is clamped to its cap. The Architect circles the Core at 300
  to 380 u, so instead of needing contact a proximity channel's event (a beat,
  burst, nova, pulse, ghost or scar) strikes a boss whose edge is within 420 u
  (`APEX_BOSS_REACH`), drawn as the character's Apex link.
  The channels were changed so they land: Hypernova novas every second whatever
  its Mass, the Hypercube pulses every 0.6 s, Swarm Queen's drones reach for a
  boss in Apex reach, Perihelion's release waits through a shift that starts
  mid-release, a Dash during Vivisect strikes, Apex shots aimed at a boss pierce
  up to four ordinary enemies, and Monolith's facets also take obstacle
  collisions and regrow in 1 s. An activation erases at most 96 bolts (the
  characters' budgets run from 0 to 72). Mirror Stance parries the next four
  hits while the Diamond brakes; four was chosen on 192 population runs by a
  rule set before the grid. A parry now also takes a Volatile Remains burst like
  any other hostile blast; before, the burst hit a braking Diamond unparried.
- **Permanent upgrades.** Refit (per character) and Honing (per owned weapon)
  run from level 0 to 10; the next level costs `base × 2^level` Matter
  (characters 55 and weapons 40, 20 % more per Rebirth rank of their gate and
  rounded to 5, so 65 to 130 and 50 to 110), saturating, and is never refunded.
  The bases follow `round5(1.2 × M6 / 512)` and `round5(0.85 × M6 / 512)`, where
  M6 = 23,247 is the median banked Matter of a full-Matrix tier-6 run over 200
  population seeds; Refit 10 then costs 1.21 and Honing 10 0.88 of it, inside
  [1, 1.5] and [0.7, 1]. Refit adds 2 maximum integrity and 2 %
  character-mechanic damage per level and moves one signature parameter (never
  below half its base); Corona never narrows a halo that Refit widened and Swarm
  Queen never shortens a drone life that Refit lengthened. Honing adds 3 % to
  the current weapon's own hits and follows a mid-run weapon switch. The run
  captures the levels at start and rejects out-of-range ones
  (`UPGRADE_LEVEL_OUT_OF_RANGE`); the Build summary and the Codex show them.
  Loading clamps a level above a lowered maximum and refunds it. Profile adds
  the pulses `PurchaseCharacterUpgrade` and `PurchaseWeaponUpgrade` and the
  refusals `UpgradeMaxLevel` and `WeaponNotOwned`. Morningstar's speed share
  fell from 0.036 to 0.031 to keep Honing 10 inside the weapon band.
- **Workshop.** One Workshop overlay with Characters, Weapons and Core Matrix
  tabs replaces the Lab, the Armory and the Hangar, so the app has six routes
  and four overlays. W opens it; L, A and H open it on the Matrix, Weapons and
  Characters tabs (H over Home only). It opens over a paused run as well;
  characters change only over Home, and purchases apply from the next run. Each
  character and weapon detail has an upgrade block with the level and the next
  price, and a character's block names its Refit signature. Home has five rows
  (Play, Workshop, Rebirth, Codex, Settings), two chips and a Workshop stamp
  that is the Profile's affordability read; characters are chosen only in the
  Workshop, so Home shows the current character's name and (!) on the facts
  card's line and no Characters card, and its notice line is the version alone.
  The pause menu offers Resume, Settings, Workshop, Codex and Exit. The composition owner keeps
  the shell's last Workshop tab request beside the features, so L, A or H after
  a reopened desktop window still shows its tab. The pinned
  `AppSessionApiContractTest.shellProjectionRetainsExactlyEightRoutes…` became
  `…ExactlySixRoutesAndOnlySessionOwnedWorkflowState`.
- **Character clears.** The Profile records the highest Rebirth tier each
  character cleared (an Architect victory with it at that tier), raised by a
  higher clear and kept by a lower one. The Workshop's Characters tab marks
  each victorious character with that tier's plate and numeral (the Ascendant
  crown beside a tier-10 clear) and its detail reads Cleared with the tier, or
  Not cleared. A victory saved before 0.5.0 recorded no tier: it shows a plain
  plate (an Ascendant one reads tier 10) until the character wins again.
- **Level badge.** Tiers T1 to T5 keep their thresholds (1, 10, 20, 30, 40) and
  T6 APEX starts at the level cap with its own look and the Apex states; the
  Data bar and line change per tier and become the Apex meter at the cap; tier,
  unlock, ready, cast and refund edges play once. The active row (Z, X, C) shows
  in every layout once an active item binds a slot, with key caps while a
  keyboard is in use, and the Apex key
  plate widens to any rebound key's label with the Data bar starting after it.
  Touch accepts five pointers with one steering; a pointer held through a
  choice or a pause keeps its role when the run returns. The badge is the Apex
  control.
- **Reaction visuals.** All 21 reactions and the Breaks draw distinct procedural
  looks with element-coloured links and stay recognizable in monochrome;
  lingering fields, control glyphs, marks and Core states come from the render
  model. A per-frame draw budget and crowd level of detail (secondary layers off
  above 60 on-screen enemies, signatures only above 90) bound the cost, and the
  Reaction effects setting (Full, Reduced, Minimal) scales it.
- **Settings.** Settings grows from 4 to 6 groups and from 15 to 41 rows: window
  mode and size presets, V-Sync, a frame-rate limit, the Android refresh rate
  and hidden system bars, web fullscreen, interface scale (85 to 130 %), Show
  FPS, pause when unfocused (on by default), reaction effects and rebinding of
  all 14 gameplay actions with conflict swap and reset. Profile stores the
  values through twelve new `AdjustPreference` adjustments; the platform display
  host lists and applies them. The desktop composition owner lives above the
  window, so a decoration or V-Sync change reopens the window and keeps the
  session and the run. Leaving fullscreen places the stored windowed preset
  only once the window has left fullscreen, a Borderless window has no
  resizable edges, Android offers at most four refresh rates and only within 24
  to 360 Hz (the range a stored rate admits), and hidden Android system bars
  give their space to the play area. Show FPS starts a new measuring window
  after a stall instead of publishing a falsely low value. The interface scale
  applies to menus and to the run's HUD, while the world keeps the raw density.
  The worst-case Profile payload is 7,711 UTF-16 code units of the 8,192 one
  Desktop Preferences value admits; it is measured at `MAX_ITEMS` 320, so the
  287-item catalog leaves it unchanged.

The performance suites are `gameplay-core-v4` (the reaction workload fills the
field table) and `profile-persistence-current-schema-v5` (the business maximum
carries every 0.5.0 field). Content goldens: items `0..172` `68d4572118b9586d`
(only the Data levers moved), items `173..268` `6ae3869a3317ee94`, the active
items `269..286` with the active offer factors and every character's lane
`a73030c02a213d44`, build tuning `90af4a2ca532d113`, weapon and Matrix economy
`6e61ebea0bcb73f7`, active effects, Apex, upgrades and tempo
`443334dcc0d91f0d`; Rebirth `461252b1f47728a5` and Relics `c8c02a43bec87968`
are unchanged, and the seeded baseline trace still passes unchanged. Active
items re-pinned four goldens on purpose: items `173..268` (from
`c11c59b06e52e3dc`) and build tuning (from `d6fa6a5d0a26712d`) for player text
that names actives instead of skills; the effects golden (from
`0e1fa079fddfd61b`) for the removed skill cadence and tag points, that wording,
the calibrated rank numbers (Catalyze's targets and reaction scale at its cast
bounds, so its rank III text names the wider area and the ten reactions at
130 % it shares with rank II), Resonator's rank III text (it replays at 84 %)
and Null Shroud's base bolts and 12.35 s rank II cooldown; the actives golden
(first `aea321347af3e7dc`) for the calibrated passives, the Epic unlock at 10
and the lane factors 3 and 8.

Each knob was chosen on population seeds disjoint from every fixed gate list
and then confirmed on the gate seeds. On the final tree the desktop suite runs
2,361 tests with no failure (the Gameplay nucleus 896, its interaction 441);
the nucleus suite takes 891 s of wall time, 614 s of it in the Apex fight class
on the critical fork, and a second run of the eleven reward-stream gate classes
prints the same lines.

Active items change the reward stream of every gate the bot plays, so each
seeded gate was checked against populations (policy, balance gate
populations). `PopulationSeeds` are `i × 7,919 + 13` for i = 1,001..1,300;
knob values were chosen only on the calibration windows i = 1,301..1,600 and
1,601..1,900 and then read on the gates, never by filtering against gate seeds
or `PopulationSeeds`. The chance that a gate's own seeds pass was read from 300
`PopulationSeeds` runs of the 05885fa9 tree (0.5.0 before active items) and of
the final tree, with the gate's own bot, profile, tier and outcome. A gate below
0.90 re-rolled with every reward-stream change; it now states its rule over the
population, with its threshold derived from the 05885fa9 value (n 300 unless
named, standard error SE) by the policy's rule. The gates read:

- Arms without actives. Gate I's unskilled arm and the Singularity baseline,
  reaction and item arms play the catalog without its 18 actives, which deals
  the 0.5.0 deck (universe 203) and stream and reproduced the 05885fa9
  no-skills arm run for run; the Singularity skill arms keep the catalog at
  `activeWeight` 0 and own only the active they measure. A passive-only deck
  (universe 203, actives offered beside it) was probed and not adopted: over
  144 seeds per character V2 read 0.764 with the 221 deck against 0.763 at the
  base; at `activeWeight` 1 the passive-only deck broke ActiveItemFrequency (a)
  and (b) and the flatness active share, and at 0.65, the highest value that
  kept them, it moved V2 no closer to the base, cut gate H's population from 466
  to 451 s and raised the POI economy rewards' active share from 0.121 to 0.167.
- Rebirth gates. A (fresh tier-0 wins; its seeds passed 0.49 of the time at the
  base) wins 0.670 of `PopulationSeeds` (201 of 300) against a floor of 0.62
  (base 0.680, SE 0.027). The strong build's tier-10 median survival (its seeds
  passed 0.82 of the time for D and 0.78 for H) is 463.63 s for D (3 of 300
  won) and 514.52 s for H (1 of 300) against floors of 427.87 s and 431.46 s
  (base 574.48 s, SE 16.24, and 583.33 s, SE 20.37; the floors are explained
  below). Gate I (31 seeds, 0.78 at the base) compares the median clear with
  actives to the catalog without them over 300 runs per arm: 87.90 against
  94.13 s, ratio 0.934, floor 0.80 (base 0.891, SE 0.042); its 0.82 target held
  on the calibration windows, 0.861 and 0.833. The other gates keep their seeds,
  which pass with probability at least 0.9998 at the base and on the final
  tree: B 4/5 (fights 51 to 94 s; seed 71284 touches the Singularity at 780 s),
  C 0/5 (all fall within 19 s), D and H 0 wins each (bound at most 3), E 4/5 at
  tier 2 and 0/5 at tier 8, F 5/5 at tier 3 (median fight 79 s) and never at
  tier 10, G 0/5. Gates B and G play the bot with `avoidWeaponHarmony`, which
  never binds a Relic carrying the equipped weapon's key, so their setup
  without key harmony holds by construction rather than by seed.
- Level gate. Fresh tier-0 runs (11 seeds, 0.35 at the base) are alive at 720 s
  in 0.700 of `PopulationSeeds` (210 of 300) against a floor of 0.66 (base
  0.713, SE 0.026), and 4 of those 210 stand outside levels 22 to 30 (0.019,
  ceiling 0.06; base 0.028, SE 0.011). The no-POI gates are population shares
  as well (their seeds 0.53 and 0.70 at the base): 0.593 of the runs win as the
  reference run must (floor 0.46; base 0.527, SE 0.029) and 0.597 win without
  touching a POI (floor 0.47; base 0.533, SE 0.029); seed 2026 is still replayed
  twice to compare its trace and now falls at 204 s. The Data arms keep their
  seeds (each at least 0.94 on the final tree): a full Matrix that avoids Data
  never reaches 42 before 720 s (levels 33 to 37 at tier 0 and with Refit and
  Honing at 10, 33 to 38 at tier 3; at tier 6 one run reaches 42 at 796.4 s and
  one falls at 644 s). Seeking Data, it reaches 42 at tier 0 on 4 of 5 seeds
  inside [510, 630] s (553.5 to 623.2 s; 55446 at 666.6 s), at tier 3 on 5 of 5
  inside [480, 660] s (524.3 to 557.6 s) and at tier 6 on 3 of 5 (496.2 to
  580.0 s; 475.1 and 694.3 s outside).
- Apex fight gate: every Apex keeps the Architect contract on its 72 fixed
  fights at tiers 0 and 10 (the volleys its own shots hold back stay under the
  bound; no clause broke in 1,816 population fights), and an activation never
  lowers a character's damage to the Architect. The bot casts its Apex in
  0.9968 of 312 population fights, 13 per character and tier (floor 0.99; base
  0.9983, SE 0.0023); the Orb's tier-10 seed 7998203 stays silent. The benefit
  is judged on forked pairs: each run is played four times with both arms' Core
  moved by k/100 u, with and without the Apex. Three fights per character, as
  first specified, could not hold the ±40 % band (a bootstrap passed it at most
  0.6 % of the time). The offence takes the first 96 runs that reach the
  Architect per offensive character along `PopulationSeeds` (its fixed 48 runs
  passed 0.97 of the time at the base, 0.83 now): the median of the ten
  characters' median kill-time ratios is 0.846 (band [0.75, 0.95]; base 0.847,
  SE 0.005), and each character's benefit lies between 0.80 (Ring) and 1.21
  (Shard) of the median benefit (band ±40 %; base 0.80 to 1.17). The defensive
  check runs at tier 10 on the first 128 runs of the Prism and the Diamond along
  `PopulationSeeds`, four forks each, and compares the damage taken (integrity
  and shield) in the Apex windows with the same windows without it: Prism 0.771
  and Diamond 0.560 (band [0.55, 0.85]; base 0.715, SE 0.037, and 0.694, SE
  0.041). Their fixed 64 runs passed 0.99 of the time each at the base; on the
  final tree the Diamond's pass 0.58 of the time, and the Prism's 0.94 on
  `PopulationSeeds` but 0.75 on the calibration windows. Where the two disagree
  the pooled runs decide: pooled, a 64-run Prism draw passes about 0.82 of the
  time, below 0.90, so the Prism became a population gate as well. At tier 0
  the Prism took damage in only a tenth of its windows, and most of what the
  first check counted was shield decay from the test's own refill. The no-POI
  bot casts the Apex where its boss channel lands (outside a shift, within 420 u
  plus the boss's radius, and in the last phase or at least strikes + 2 hit
  caps above the next phase gate, or below 40 % integrity), Dashes whenever it
  can during Vivisect and Total Eclipse, and brakes during Mirror Stance when a
  hostile shot is within 160 u or an ordinary enemy within 120 u.
- Active share: the Orb plays all 300 `PopulationSeeds`. The runs that bind an
  active item by 360 s count (286 of 300, floor 0.25; all 300 at the base, when
  its lane skills came free), at least half of them must reach 720 s (210 do),
  and their median share of the damage active effects cause is 0.089 (q10
  0.024, q90 0.181) against the band [0.08, 0.20] (base 0.113 over 214 judged
  runs, SE 0.0038). Judged on 31 owning runs walked from i = 1, it passed 0.99
  of the time at the base and 0.70 on the final tree (population median
  0.089). On the calibration windows it read 0.1025 and 0.0988, and the medians
  of their 31-run blocks reached 0.08 in 9 of 9 (lowest 0.082) and 7 of 9
  (lowest 0.072). Each
  character's first 25 owning runs along `PopulationSeeds` keep the top
  effect's pooled share at most 0.30: Storm Coil 0.224 (base Sonic Boom 0.280,
  SE 0.014; three runs per character passed 0.76 of the time at the base).
  Singularity contacts stay at 0 in every arm over 300 runs (Slingshot may add
  0.02), and each arm's obstacle contacts stay within its bound of the 0.0106
  per second baseline: 1.15, or the base ratio plus two SE where that is higher
  (Slingshot 1.29, Spin Turbine 1.24, Recoil Spring 1.18, Crest Rider 1.18); the
  arms read 0.873 to 1.088 (base 0.836 to 1.129; all 15 arms passed their 30
  seeds 0.205 of the time at the base).
- Active frequency, a new gate without a base, so its thresholds are the
  design's: two arms of the first 300 `PopulationSeeds` (25 per character),
  fresh at tier 0 and a veteran with the full Matrix at catalog level 80.
  Unowned-active cards are 0.063 and 0.058 of all item cards (band [0.04,
  0.10]) and at most 0.20 at every level from 2 to 10 (highest: fresh 0.179 at
  level 7, veteran 0.075 at level 10). A fresh run that reaches level 10 has
  been shown one in 241 of 249 (51 ended earlier) and a veteran by level 16 in
  290 of 300 (floor 0.80 each); the median run holds 3 actives at 720 s on both
  arms (band 1 to 3); POI item rewards stay at most 0.120 actives (fresh,
  economy focus; bound 0.20). The gate once played 48 seeds per arm; during
  calibration seven disjoint 48-run windows of the same sequence from i = 1,001
  read fresh (c) 0.79 to 0.94, then judged over all 48 runs, and (b)'s highest
  level, level 7 in each, 0.158 to 0.244, and 3 of the 7 passed, so the gate
  plays the population from the start. The flatness gate measures each tier's
  passive items over its passive cards: with the deck Uncommon 0.93, Rare 1.23,
  Epic 0.70 and risk 1.17 of the Common rate (worst item Graviton Seed 1.35);
  the actives are 0.095 of all cards and the Orb's lane actives come 2.63 times
  as often as the others (bounds [0.04, 0.10] and 0.80 to 1.25 times
  `characterActiveWeight`).
- Item variety: on 12 seeds per character, isolated runs share 0.092 to 0.101
  of their items on average (bound 0.30; the closest pair 0.231 against 0.55),
  consecutive runs that carry the Profile history share 0.037 to 0.052 (bound
  0.22), and 210 of 217 rank-0 items are shown (bound 0.90). V2 plays 300 runs
  per character (seeds `7,919 k + 101`, k = 1,001..1,300; its 12 seeds passed
  0.30 to 0.55 of the time at the base): the isolated overlap is 0.702 (Orb),
  0.695 (Prism), 0.706 (Star) and 0.767 (Hexagon) of what the same seeds share
  with every item dealt and the 0.4.0 lanes, against 0.78, 0.79, 0.79 and the
  accepted 0.81 (base 0.735, 0.752, 0.743 and 0.807).
- Economy: on the gate seeds median banked Matter is 2,374 fresh at tier 0,
  5,127 with a full Matrix, 12,458 at tier 3, 20,889 at tier 6 and 14,879 for
  the strong tier-10 build. The price locks read populations (their seeds passed
  0.91 and 0.95 of the time at the base, 0.84 and 0.89 now): the dearest rank-0
  base, 55, is 0.024 of the fresh tier-0 median, 2,310 (bound 0.03; base 0.022
  of 2,499), and the last Refit level (28,160) costs 1.273 and the last Honing
  level (20,480) 0.926 of the full-Matrix tier-6 median M6, 22,116 (bands [1,
  1.5] and [0.7, 1]; base 1.197 and 0.871 of 23,517). With Refit and Honing at
  10 the strong tier-10 build survives a median 610.56 s over `PopulationSeeds`
  (floor 480 s; base 687.28 s, SE 20.13; its seeds passed 0.94 of the time at
  the base, 0.80 now) and wins 0 of its 5 seeds, the Signature build 0 of 5, the
  half Matrix still loses all five at tier 8 and a fresh tier-6 run still never
  wins, while the Orb and the Pentagon fight 1.15 to 1.27 times stronger in the
  J5 encounters. Refit 10 keeps every character in its band (median 1,807.7; Orb
  1.31, Shard 0.72) and Honing 10 keeps every weapon in its family band
  (Morningstar crowd 1.32).

The owner's premise, actives only through offers, costs margin that is
recorded here as trade-offs. The 05885fa9 base handed the strong Pentagon its
lane skills Sonic Boom, Resonator and Storm Coil free at levels 2, 8 and 16;
now a lane active arrives only when an offer shows it, and Reborn 10 stays
hard, since no difficulty lever moved. Gates D and H therefore keep the design
floor of 480 s only where the shipped tuning reaches it: each floor is
min(480 s, M − 2 SE), with M the median survival over the 600
calibration-window runs (D 471.08 s, H 481.60 s) and SE the bootstrap SE of a
300-run median drawn from them (21.60 s and 25.07 s), rounded down to 427.87 s
and 431.46 s. Both stay above 420 s, the seven minutes gate D's name states,
below which the change would have gone back to the owner. Other bounds follow
from the 05885fa9 base population by the policy's threshold rule, not from the
premise. Among them, fresh tier-0 survival to 720 s keeps a floor of 0.66
instead of 0.5.0's 0.73, since the base itself reads 0.713 here (0.715 on
0.5.0's 400 audit seeds); every Singularity arm whose base ratio plus two SE
exceeds 1.15 has that as its own ceiling, and Slingshot may end 0.02 more of its
runs in a singularity contact where the seeded gate allowed no more; gate A's
floor is 0.62 against the seeded 4 of 5; and the bot casts its Apex in at least
0.99 of 312 fights where every one of 72 needed a cast. The active share's band
and the active frequency's bounds are judged over populations instead of
31 and 48 seeds.

The Apex Diamond's band holds by 0.010: its population reads 0.560 against
0.55, less than a quarter of its SE (0.044). On the calibration windows the
shipped content reads 0.589 (run-bootstrap SE 0.029) and no Diamond-lane
back-off reaches 0.55 + 2 SE (0.607): the shipped Null Shroud already holds its
base bolts, dropping its 40 % more power as well reads 0.597, and dropping its
shorter cooldowns too, which leaves Null Shroud at its base, 0.584. Frost Snap's
power stays, since without it Solar Flare leaves the strike family's 40 % band.
The next change on the Diamond's lane can tip this gate.

Both defensive Apex bands play 128 runs, the population the base was measured
on, since these fights already set the suite's runtime, and that population
does not settle them either: a fresh 128-run draw (four forks each) from the
256 calibration-window runs lands in [0.55, 0.85] with probability about 0.82
for the Prism (window ratio 0.814, near the ceiling) and 0.84 for the Diamond
(0.589, near the floor). This is a trade-off for the owner, not a further
restatement.

Calibration reads its 40 % per parameter, so an effect scaled in two
parameters gains more per cast: Gravity Well's longer duration and stronger
damage per second together give about 1.96 times the damage of a cast.
Catalyze's 40 % is its radius (×1.4), beside its 15 % shorter cooldown. A cast
picks at most 10 enemies (the cast scratch) and scales a forced reaction at
most 1.3, so its targets and reaction scale rose only to those bounds (rank I
from 6 at 1.0 to 8 at 1.3, rank II from 8 at 1.15 to 10 at 1.3, rank III still
10 at 1.3), and rank III gains over rank II only its radius and cooldown.

Other margins are thin but pass (distance to the bound in standard errors of
the final population): the active share 1.4 SE above 0.08, fresh survival 1.5
SE above 0.66, gate D 1.5 SE above its floor and gate A 1.8 SE above 0.62. The
fresh active frequency peaks at 0.179 at level 7 against 0.20 (0.199 with two
SE on the gate population; 0.191 and 0.186 on the calibration windows, so no
unlock level moved). The Apex cast has one silent fight of 312 and fails at
four; a fresh 312-fight draw at the population's 12 silent fights of 1,816
passes 0.85 of the time. The tier-6 Data-seeking arm passes its seeds at
exactly 3 of 5 (probability 0.97).

Before active items, 0.5.0's own populations showed what its fixed seeds could
not. They are 0.5.0 history, measured on its calibration C tree with skills
drafted, and they still explain the values below. On the gate seeds the five
review fixes that followed left gates A and I, the fresh level arm and the
skill-share median as they were; they moved the Prism's variety ratio (0.721
to 0.687), the defensive Apex ratios (Prism 0.735 to 0.742, Diamond 0.612 to
0.634), some Data-seeking arrival times and one gate H seed, all inside their
bounds.

- The calibrated values hold on disjoint seeds. Data-seeking tier-0 runs reach
  42 at a median of 586 s over 400 audit seeds (359 inside [510, 630] s, 2
  early; 585.7 and 588.9 s on the tuning and holdout sets), tiers 3 and 6 at 535
  and 546 s, and no full-Matrix run that avoids Data reaches 42 before 720 s at
  tier 0, 3 or 6 or with Refit and Honing at 10 (0 of 400 each). The Apex
  offence median is 0.851 over 96 runs × 4 forks (every character 0.838 to
  0.858) and 0.843 on the audit seeds. The defence reads Prism 0.757 and 0.758
  and Diamond 0.691 and 0.666 over 192 runs × 4 forks, so a 64-run gate lands in
  its band with probability 0.96 or more. The audit's tier-6 Matter median,
  23,550, derives the same bases 55 and 40.
- Gates D and H hold but with less margin. The offer weight and k 0.14 moved
  D's population median from 719 s to about 590 s; across the tuning, holdout
  and audit sets the chance that a five-seed median reaches 480 s is 0.80 to
  0.88 for D and 0.74 to 0.88 for H.
- Fresh tier-0 survival to 720 s is 0.74 over 1,400 seeds (0.715 on the 400
  audit seeds; 200-seed blocks 0.715 to 0.770), below the 0.75 target and the
  0.80 of 0.4.0. Skills save early runs and lose more late ones: of the runs
  alive at 500 s, 13.7 % fall before 720 s with skills and 9.1 % without. The
  11 fixed FRESH seeds are a favourable draw (9 of 36 disjoint 11-seed blocks
  pass that gate's rule). `levelIntegrity` 3.5 and `levelRepair` 10.5 would lift
  survival to 0.77 over 400 seeds and steady D and H, but they turn the
  Singularity contact gate red on its 30 fixed seeds (Crest Rider 0.0146 per
  second against 0.0143), while over 120 other seeds Crest Rider sits at 0.84 of
  the baseline. Lengthening that seed list after seeing the failure would choose
  seeds by outcome, so the values stay 2.5 and 7.5: a recorded trade-off.
- Gate I's ratio is 0.80 over 1,400 seeds (78.33 s against 97.82 s), short of
  the 0.82 target. It swings from 0.69 to 1.02 between 100-seed blocks (5 of 14
  reach 0.82), and the 31 gate seeds (0.937) rank 8th of 45 disjoint 31-seed
  blocks. Only `skillTagPoints` 0 (the drafted skills' tag points, which active
  items removed) reached 0.82 on 200 seeds, and it cost the
  tier-10 strong build its skill resonance: the chance of a five-seed median of
  at least 480 s fell from 0.80 to 0.55 for D and from 0.88 to 0.48 for H.
  Raising level integrity to win them back pulled gate I to about 0.78, so
  nothing was adopted.
- ItemVariety V2 is 0.74 to 0.81 per character over 196 seeds (Orb 0.752, Prism
  0.747, Star 0.738, Hexagon 0.808; on 96 audit seeds 0.699, 0.748, 0.736 and
  0.814), against a target of 0.75 over 48 seeds; the 12 gate seeds are the
  best block for every character. The deck lowers the overlap mostly through
  one item the bot takes whenever it is offered: Detonator Spur is in 0.96 to
  0.99 of runs on the 0.4.0 lanes and 0.64 to 0.66 with the deck, and without
  it the ratio is 0.89 to 1.00. No documented deck, lane or skill knob brings
  all four characters to 0.75 over 48 seeds without costing fresh survival, and
  `skillTagPoints` 0 also broke D and H. The history does what it is for:
  consecutive runs share 0.03 to 0.07 of their items, against 0.11 to 0.13 for
  two runs with every item dealt on the 0.4.0 lanes.

## Release 0.6.0

Release 0.6.0 answers eight owner requests:

1. Replayability: run omens, six director personalities, ONSLAUGHT themes,
   Elite affixes, champions, ambient world events, event signals and a run
   Keynote (one featured tag and element) make each run read differently.
2. Points of interest: sites in eight families (anomaly, race, challenge,
   bounty, cache, secret, portal, signal), four mini-bosses, two secret bosses
   and five easter eggs.
3. Structures and dungeons: seven capsule-wall templates placed from 4,000 u
   out, with sealed rooms, garrisons and a boss or treasure room.
4. Dimensions: Zenith (Heaven), Nadir (Hell) and the secret Archive, each with
   its own law, roster, objective and reward cards.
5. Easy Mode: a joke Baby mode whose green cursor does not kill; nothing in it
   counts toward any progress.
6. Obstacles: six new kinds with items built around them, and a fast hit
   always hurts: impact speed counts the scrape and damage keeps a floor.
7. Physics: piloting flight, where turning builds speed, straight flight tires
   and spool-up is quicker.
8. Trail: the Core's ribbon follows its path instead of the velocity vector.

Balance decisions and accepted trade-offs:

- Involuntary systems are part of every run and every gate: the flight law,
  the impact rework, the realspace obstacle-kind mix, Comets and Pylons,
  director personalities, ONSLAUGHT themes, Elite affixes, champions, ambient
  world events, the Keynote and the seeded site kinds. Opt-in systems (every
  site, cache, egg, sigil, event signal, structure, rift and omen) are skipped
  by the reference bot through one optional-site list, with "No omen" pinned;
  each has a design gate of its own with a scripted pilot.
- The reference bot flies a human Dash cadence: no new Dash while its previous
  Dash runs, and no threat-dodge or Apex Dash when the predicted post-Dash
  speed reaches the build's lethal obstacle speed with a solid body on the
  path within 0.3 s. The old bot re-requested its Dash every 0.02 s frame and
  dashed into rocks, which the harsher impacts made lethal; the impacts stayed.
  The bot also never swings a Slingshot, or flies a Blink or Arc Hook line,
  into a skipped optional site.
- Scripted harnesses whose subject is not terrain (abilities, weapons,
  reactions, skills) fly an obstacle-free arena; those that need obstacles
  clamp their pilot below the dash-safe speed near rocks.
- Balance that the flight law and the impacts moved was restored in content,
  never by a threshold: the weapon, character, skill, item and Apex retunes,
  Glass Harvest at Reborn 1 (the rank-0 Data ladder keeps its 13 items), the
  Keynote element share 0.8 -> 0.7 (a Keynote deck grows by at most nine
  items), the Shard's mass x0.8, Mirror Stance's single parry, a 60 s Nadir
  visit, the dimension objective bosses' integrity share and Still Water's
  -15 % weapon power.
- Accepted as shipped: a far sigil spawn counts as engagement; bounty guards
  stay as ordinary enemies after a claim; the Sigil panel's reward reads
  UNKNOWN; an objective boss abandoned past 6,000 u ends that visit's
  objective; a fresh run spends 60 % more seconds at ambush speed; Grace
  lowers drag by 15.61 % (band top 16 %); structure walls collapse at the
  Architect and an abandoned dungeon is spent; omens are offered in Easy runs.
- The strong tier-10 ricochet pace is far below 0.5.0's (0.034 against 0.58
  per minute on i = 2,501..2,800): the old pace came from the old bot's
  Dash-chain slams, and `ricochetReach` stays 0.45.

Re-pinned goldens (old -> new): items `0..172` with every item effect
`68d4572118b9586d` -> `25c0be545c5a2647` (the GRIP and SLING effects); items
`173..268` `6ae3869a3317ee94` -> `17777e466f80fc29` (six items back over the
5 % floor under the flight law) -> `bfc80fdf55b64617` (Empty Chamber 20 ->
25 %) -> `ef1306c885aae256` (Loader Sigil 0.5 -> 0.6 s); build tuning
`90af4a2ca532d113` -> `c5ea14c28212923a` (Orbit, Supernova, Repel and
Attractor retuned to the flight law); weapon and Matrix economy
`6e61ebea0bcb73f7` -> `120fe0d55a06ce89` (Impact Dampers and Gravity Surfer
capped); active effects, Apex, upgrades and tempo `443334dcc0d91f0d` ->
`3301c1592ee84a8c` (four skills retuned to the flight speeds) ->
`e73bd70a41ae42f0` (Mirror Stance's single parry). New slices: the terrain
items `287..301` `ebcd1818d76810e4` -> `7199c257d9d5f681` (Glass Harvest at
Reborn 1); run variety `0ee882129c87121f` -> `24a8c347d62bc2dd` (Keynote
element share) -> `bb958301a03293ab` (four omens become trades) ->
`c40c7ebecf2f1d41` (Still Water); the compiled structure walls
`2_595_552_187_804_222_925`. The actives (`a73030c02a213d44`), Rebirth
(`461252b1f47728a5`) and Relics (`c8c02a43bec87968`) are unchanged. The
seeded intent trace was re-recorded for the flight law (f4aeba0f) and for the
omen offer the production start opens after its first fixed step, which the
trace declines (c92f9426); both meet in the run-variety merge (29d41caa).

The gate base is the 0.6.0 tree 15b23096 (policy, balance gate populations).
Population gates over `PopulationSeeds` (n 300 unless named; SE binomial for
shares, bootstrap for medians and ratios):

| Gate | 0.6.0 base (SE) | Threshold |
|---|---|---|
| A, fresh tier-0 wins | 0.687 (0.027) | 0.62, unchanged (design) |
| I, median clear with / without actives | 0.904 (0.035) | 0.80, unchanged |
| D, strong tier-10 median survival | 704.23 s (72.06); window M 877.85 s (75.28) | 427.87 -> 480 s |
| H, strong + Data tier-10 median survival | 900.01 s (1.34); window M 900.01 s (39.92) | 431.46 -> 480 s |
| NoPoi, wins as the reference | 0.620 (0.028) | 0.46 -> 0.56 |
| NoPoi, POI-free wins | 0.640 (0.028) | 0.47 -> 0.50 |
| fresh tier-0 alive at 720 s | 0.710 (0.026) | 0.66 -> 0.65 |
| alive runs outside level 22-30 (n 213) | 0.066 (0.017) | 0.06 -> 0.10 |
| Singularity Slingshot contact ratio | 1.328 (0.089) | 1.29 -> 1.51 |
| Singularity Spin Turbine | 1.189 (0.081) | 1.24 -> 1.36 |
| Singularity Gravity Assist | 1.029 (0.071) | 1.15 -> 1.18 |
| Singularity Crest Rider | 1.026 (0.068) | 1.18 -> 1.17 |
| Singularity Recoil Spring | 1.010 (0.066) | 1.18 -> 1.15 |
| Singularity, the other ten arms | 0.836 to 1.001 | 1.15, unchanged |
| added singularity contacts, every arm | 0 of 300 | Slingshot 0.02 -> 0 |
| active share, median (211 judged runs) | 0.090 (0.0061) | [0.08, 0.20] -> [0.07, 0.20] |
| active share, owned by 360 s / spread top | 0.963 (0.011) / Storm Coil 0.193 (0.026) | 0.25 / 0.30, unchanged |
| V2 Orb / Prism / Star / Hexagon | 0.720 / 0.735 / 0.743 / 0.768 (0.018-0.021) | 0.78 / 0.79 / 0.79 / 0.81 -> 0.76 / 0.78 / 0.79 / 0.81 |
| Matter P1, 55 / fresh median 2,325 | 0.024 (0.0003) | 0.03, unchanged |
| Matter P2, Refit 10 / Honing 10 of 23,238 | 1.212 (0.014) / 0.881 (0.010) | [1, 1.5] / [0.7, 1], unchanged |
| J1, maxed strong median survival | 900.01 s (0.00) | 480 s, unchanged |
| Apex casts (312 fights) | 1.000 (0) | 0.99 -> 1.00 |
| Apex offence median / per character | 0.850 (0.005) / 0.87 to 1.39 of the median benefit | design bands, unchanged |
| Apex defence Prism / Diamond | 0.684 (0.052) / 0.695 (0.035) | [0.55, 0.85], unchanged |

The window runs i = 2,801..3,400, never tuned on, are bimodal: 164 of 600
end before 420 s in each arm and about half reach the 900 s cap (means
655.30 s for D and 656.93 s for H), so M − 2 SE (727.30 s and 820.17 s) puts
both floors at the design's 480 s, far from the 420 s stop. Every seeded
reward-stream gate passes its own seeds with probability at least 0.94 on the
0.6.0 tree: the Data-seeking arms at tiers 3 and 6 read 0.94, at tier 0 0.97,
the Data-avoiding arms at tiers 3 and 6 0.98, Matter P3 0.996, and Rebirth
B, C, E, F, G, the D and H win bounds, J1's win bound, J2-J4, V1, V3, V4 and
the Apex contract 0.998 or more. Active frequency keeps its design thresholds
(fresh highest early level 0.159, SE 0.010). The restatement loosened the
fresh level band (0.06 -> 0.10), fresh survival (0.66 -> 0.65), the
Slingshot, Spin Turbine and Gravity Assist ceilings and the active share's
floor (0.08 -> 0.07; the 0.6.0 median sits 1.6 SE above 0.08). It tightened
D and H (to 480 s), the no-POI shares, V2 for the Orb and the Prism, Crest
Rider and Recoil Spring, the Apex cast (every one of the 312 fights) and
added singularity contacts (none in any arm). Thin margins: the Apex
character band (Crescent 1.39 against 1.40, 0.13 SE; Crescent and Shard
reach 1.56 at base + 2 SE, and a design band never widens), the Still Water
omen (+0.093, SE 0.032, against 0.12) and Hungry Dark (+0.080, SE 0.035).

### Final verification 2f7d649a

The affected gates were measured again on `2f7d649a`: 19 classes and 60 tests
passed, with no failures, errors or skipped tests. This is the targeted gate
measurement; the full local CI is recorded separately. The threshold base
`15b23096`, its SEs above and every existing threshold remain unchanged.
The values below are current observations, not a replacement threshold base.
SE is included only where this run printed it; an unprinted SE is not inherited
from the historical measurement.

| Gate | Measured on 2f7d649a | Unchanged threshold |
|---|---|---|
| A, fresh tier-0 wins | 207/300 = 0.690 | >= 0.62 |
| I, median clear with / without actives | 82.5562 / 90.8428 s = 0.9088 | >= 0.80 |
| D, strong tier-10 median survival | 878.37 s | >= 480 s |
| H, strong + Data tier-10 median survival | 900.0104 s | >= 480 s |
| NoPoi, wins as the reference / POI-free wins | 187/300 = 0.6233 / 193/300 = 0.6433 | >= 0.56 / >= 0.50 |
| Fresh tier-0 alive at 720 s / alive runs outside level 22-30 | 213/300 = 0.710 / 14/213 = 0.0657 | >= 0.65 / <= 0.10 |
| Singularity Slingshot / Spin Turbine / Gravity Assist ratios | 1.325 / 1.186 / 1.026 | <= 1.51 / <= 1.36 / <= 1.18 |
| Singularity Crest Rider / Recoil Spring / other ten arms | 1.023 / 1.007 / 0.833 to 0.998 | <= 1.17 / <= 1.15 / <= 1.15 |
| Added singularity contacts, every arm | 0/300 | 0 |
| Active share median / owned by 360 s / spread top | 0.090 (211 judged) / 289/300 = 0.9633 / Storm Coil 0.193 | [0.07, 0.20] / >= 0.25 / <= 0.30 |
| V2 Orb / Prism / Star / Hexagon | 0.720 / 0.735 / 0.743 / 0.768 | <= 0.76 / <= 0.78 / <= 0.79 / <= 0.81 |
| Matter P1, first upgrade / fresh median | 55/2,328 = 0.0236 | <= 0.03 |
| Matter P2, Refit 10 / Honing 10 of median 23,239 | 1.2118 / 0.8813 | [1, 1.5] / [0.7, 1] |
| J1, maxed strong median survival | 900.0104 s | >= 480 s |
| Active frequency, unowned cards fresh / veteran | 0.058 / 0.050 | [0.04, 0.10] each |
| Active frequency, highest level-2-to-10 share fresh / veteran | 0.159 / 0.068 | <= 0.20 each |
| Active frequency, shown by level 10 fresh / 16 veteran | 246/266 = 0.9248 / 286/300 = 0.9533 | >= 0.80 each |
| Active frequency, median held at 720 s / highest POI reward share | 3 in both arms / 0.118 | [1, 3] / <= 0.20 |
| Dungeon fresh clear / died inside (120 attempts) | 0.6750 (SE 0.0423) / 0.2750 (SE 0.0405) | [0.35, 0.85] / <= 0.30 |
| Dungeon strong clear / clear gap / median clear | 1.0000 / 0.3250 / 28.3514 s (SE 1.3877) | >= 0.85 / >= 0.15 / <= 150 s |
| Bounty fresh / veteran clear | 0.8366 (343/410) / 0.8293 (408/492) | [0.40, 0.85] / [0.40, 0.90] |
| Bounty fresh / veteran median fight | 47.8883 / 45.1223 s | <= 75 s each |
| Sigil summoning / duel clear | 125/150 = 0.8333 / 77/125 = 0.6160 | summoning >= 0.50; duel clear recorded only |
| Zenith tier-0 Ascent / Judgement completion | 0.7544 / 0.8475 | [0.55, 0.92] / [0.45, 0.88] |
| Nadir tier-0 Harvest / Pyre completion | 0.8772 / 0.6271 | [0.50, 0.90] / [0.40, 0.85] |
| Zenith / Nadir tier-10 survival and completion | 0.9800 / 0.9300 survival; 0.6300 / 0.8000 completion | survival >= 0.75; completion >= 0.50 |
| Archive tier-3 / tier-0 survival and defeat | 0.9917 / 0.9126 survival; 0.2667 / 0.0971 defeat | survival >= 0.65 / >= 0.45; defeat [0.20, 0.65] / <= 0.35 |
| Dimension win shift, Zenith / Nadir | 0.0000 (SE 0.0389) / +0.0167 (SE 0.0457) | [-0.06, +0.15] each |
| Event-signal reach / win shift / Matter ratio | 795/798 = 0.9962 / +0.0367 / 1.0060 | >= 0.85 / [-0.08, +0.12] / >= 0.80 |

These measurements include the earlier run-Matter banking fix (`3cbb4ad6`)
and the later world-interaction fixes together. Differences from the previous
local CI at `f8415014` are not isolated effects of any single fix. Ambient
spawns are now kept out of sealed rooms; `garrisonScale` changed from 1.0 to
0.8 on the structures calibration window i = 2,301..2,400 (fresh clear 0.720,
died 0.250), before confirming the unchanged gate bands. Untouched sigils
survive dimension visits, Quiet Signals wait during engagements and site
nudges finish outside structure footprints. Roaming Elites and Heralds now
return after far travel beyond the greater of 6,000 u and the ordinary
viewport-scaled leash, using a salted hash without new spawn-stream draws.

The measured 0.6.0 design gates meet their unchanged content bands. On this
verification, Zenith tier-0 completion is 0.8017 (Ascent 0.7544, Judgement
0.8475), Nadir 0.7500 (Harvest 0.8772, Pyre 0.6271), and the corresponding
win shifts are 0.0000 and +0.0167. Fresh dungeon clear is 0.6750 and bounty
clear is 0.8366 fresh / 0.8293 veteran. The omen arms remain within their
bands (Still Water win shift +0.0900, SE 0.0323; Hungry Dark +0.0700,
SE 0.0345), run skeleton variety is 0.8807, and event-signal reach is 0.9962.
The engaging-site win share is 0.7600 against its no-POI control's 0.7067.
The Apex and director-budget values above remain historical measurements;
those classes are checked in the full local CI rather than this targeted run.

Flagged for the owner's playtest: the coast when easing the pointer to the
centre (designed as half speed in 1.84 s instead of 0.69 s); `scrapeShare` 0.3; the
Golden Drifter's gold glint, an easter-egg exception to gold meaning Rebirth;
the Baby mode look; the dimension themes; the Core ribbon; the Dipole's
unlock pace at 40 ricochets now that every ricochet hurts; Mirror Stance's
single parry; the 1.25 s ember life of the Harvest; the 60 s Nadir visit.
