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

The frozen mutation matrix covers preference normalization, sound/music mute,
Matrix purchases and reset, core-shape selection, weapon purchase/equip, Refit and
Honing purchases, key bindings and display preferences, Rebirth advance,
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
and counter-thrust remain available at exhaustion. Gameplay owns twelve character
abilities, optional world-fixed trials and bounded relic synergies. Codex reads
one coherent immutable build summary and preserves pause/reward selection.

The catalog has 269 hand-authored items, 24 weapons, 32 Core Matrix nodes, 22
Relics, 19 enemy types, twelve characters, 18 skills, twelve Apex abilities and
Rebirth levels `0..10` (see the build-system, 0.4.0 and 0.5.0 intentional deltas
below). Existing stable declaration order is captured as explicit stable IDs
during the migration; consumers receive immutable versioned snapshots instead of
reaching global catalog objects.

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

### Release 0.5.0

Release 0.5.0 answers ten owner requests: a capped Data curve, 96 more items,
reaction behaviors, run skills, a level-42 Apex ability, permanent upgrades, one
Workshop for every upgrade screen, an evolving level badge, reaction visuals and
display and control settings. Seven elements and 21 reactions stay; no balance
gate was weakened, and every new feature has gates of its own. Each new Profile
field is optional and written only when it differs from its default, so every
earlier golden payload is byte-identical. Player text ships in English and
Russian.

- **Data curve and the level cap.** Content owns the level table. The run level
  stops at `RunTempoProfile.maximumLevel` 42 (Content bound 99); levels 1 to 15
  keep the 0.4.0 requirement, levels 16 to 20 ramp by up to `rampPlateau` 190
  and levels 31 to 35 by `topPlateau` 0, so reaching 42 takes 15,148 Data (31
  takes 7,898; the last level 746). From level 16 (`rebirthScaleFromLevel`)
  Rebirth tier t scales each requirement by `1 + 0.10t`, rounded half up and
  never below the base, so the totals at tiers 0, 3, 6 and 10 are 15,148,
  19,278, 23,415 and 28,922. Each level-up adds 2.5 maximum integrity and
  repairs 7.5 (`levelIntegrity`, `levelRepair`). Scaling the whole table instead
  flipped gate D (median 87.8 s), so the opening levels stay unscaled. Data
  collected at the cap still counts toward `dataCollected` and charges the Apex
  instead of leveling; Data past the cap owes no skill draft or rank-up card.
  The render model carries `maxLevel`. The Data levers are content numbers: Data
  Prospector GainData 2.5, Data Siphon DATA_GAIN 0.12 with 40 per Elite,
  Windfall Sensor 0.12, Magnet Lens 0.08, Jackpot Relay GainData 3 and Decay
  Spores up to 8 Data per cast. Each Data Archive rank gives +15 % Data and
  deals three more rank-0 Data items into the run's draft deck
  (`MatrixEffect.DataStratum(3)`; the deck's Data floor of 4 becomes all 13 at
  rank 3).
- **Items.** The catalog grows from 173 to 269 (bound 320). Items `173..268` are
  appended in eight themed batches: 21 reaction keystones (exactly one Epic or
  better per reaction, declaring both its elements), casting and Apex items,
  marks, fields and terrain, Core movement, one item per weapon family, and
  wardens, wagers and legends; 23 of them are gated by Rebirth rank. They use
  the new rule vocabulary instead of flat stats. Items whose effects wait for a
  skill or another weapon are Dormant until the run can use them; offers weigh
  them down and the Codex says so. Each run deals a draft deck from its own
  stream (run seed xor `0x0DEC_C0DE`), so reward draws keep their count and
  order while the offers of a seed change; a rank-0 deck holds 117 to 130 of 203
  items and the Codex marks the rest Not in this run. A tier refills from the
  reserve only when it runs out of dealt items the run can still take. Offer
  lanes are softer: the aligned lane is taken 55 % of the time, fit weighs `1 +
  0.6 × min(fit, 3)`, the loadout point and an unpicked weapon family leave the
  fit, saturated tags count half and risky lifts pass a 0.36 filter. The Profile
  remembers the newest 64 first acquisitions; a remembered item is dealt with
  weight `max(0.35, 1/(1 + 0.8p))` and offered with its square root. Launch Rail
  DAMAGE 0.3 for 3 s, Echo Capacitor SkillCooldown 0.15, Overcharge Glyph
  ACTIVATION 0.3 for 3 s, Tidewater Hull DR 0.2, Homing Rifling 3 s with a 2 s
  cooldown and Like Poles FLAT 40 are calibrated numbers; Scavenger Protocol
  (DATA_GAIN 0.035, GainData 0.5), Lodestar Ledger (0.04), Mycelial Ledger
  (0.02, 0.3) and Rote Memory (0.05) carry halved Data amounts so a fresh run
  does not level past 30 by accident. Every item passes the 5 %
  perceptible-impact floor, the 173 older ones on the 0.4.0 setups alone; the
  impact arenas gained skill, field, mark, throw, Apex and family-weapon
  encounters, and an item whose clause needs a skill tag or a refreshed weapon
  is flown with one. The no-POI bot passes on mark, field and throw items it
  cannot aim and, when told to, seeks or avoids Data-economy items.
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
  replaces its oldest field. The enemy status layout grows from 71 to 73 cells
  for one mark per enemy. Wildfire's residue follows its declared values, Stasis
  no longer spends Minelayer mines, and Breaks and reactions draw in element
  colour indices 8 to 22, leaving the threat colour to damage the Core takes. A
  gate plays every verb on and off in a scripted arena and requires a 10 %
  change in its play metric (21 of 21 pass; for example Blast Jump distance
  ×2.80, Shard Guard erases 32 shots against 0, Erasure Data ×19.2). The
  Architect keeps its volleys and timers inside a Time Bubble, a Maw or an
  Attractor (24 of 24 volleys each) and keeps attack windows under any Hush (18
  of 18, quiet share 0.275).
- **Skills.** Eighteen skills are drafted during the run into slots Z, X and C,
  which open at run levels 2, 8 and 16; a rank-up card every four levels takes
  the experimental card's place and raises a skill one rank, up to III. A draft
  costs three reward draws (a reroll three more) and a rank-up card none. Skills
  are cooldown-based (never below 2.5 s), refunds never exceed half a cast's
  cooldown, and reactions, items and Catalysis refund them. Skill damage goes
  through `dealSkillDamage` and the Architect gate, at most once per boss per
  cast. Every character has a skill hook (for example the Prism casts 15 %
  faster and the Spiral's ghost repeats damage skills at 35 %). Skills fold into
  build tags and affinity. Movement skills keep the Core clear of obstacles: Arc
  Hook needs a clear line 200 past its target, Slingshot swings at most 1,100
  u/s and no farther than its clear path, and Void Step lands at least 80 beyond
  the Core's radius from a rock. Skill Charges holds two casts, each recharging
  on its own clock. The no-POI bot drafts and casts skills (Data-seeking
  profiles take Decay Spores and Gravity Well; Catalyze waits for three enemies
  carrying an element). Skill numbers come from a phase-free 96-seed balance
  arena that holds every rank III a third above its rank I and every family
  within 40 % of its median at both ranks; the skills golden pins them.
- **Apex abilities.** Each character has one Apex ability (Perihelion, Monolith,
  Vivisect, Corona, Mirror Stance, Hypercube, Hypernova, Twin Echo, Magnetar,
  Crescendo, Total Eclipse, Swarm Queen) that unlocks at level 42 with an "APEX
  ONLINE" banner, is ready at once and casts on V or the level badge. Its
  cooldown is 60 s; Data collected at the cap takes 0.03 s per point off it,
  never below 30 s after the last cast. Against the Architect and Heralds it
  spends at most `bossStrikes` (6) strikes per activation, each through the
  Architect gate or clamped to a Herald's hit cap and never during a shift, and
  it erases at most 96 bolts.
- **Permanent upgrades.** Refit (per character) and Honing (per owned weapon)
  run from level 0 to 10; the next level costs `base × 2^level` Matter
  (characters 50 and weapons 35, 20 % more per Rebirth rank of their gate),
  saturating, and is never refunded. Refit adds 2 maximum integrity and 2 %
  character-mechanic damage per level and moves one signature parameter (never
  below half its base); Honing adds 3 % to the current weapon's own hits and
  follows a mid-run weapon switch. The run captures the levels at start and
  rejects out-of-range ones (`UPGRADE_LEVEL_OUT_OF_RANGE`); the Build summary
  and the Codex show them. Loading clamps a level above a lowered maximum and
  refunds it. Profile adds the pulses `PurchaseCharacterUpgrade` and
  `PurchaseWeaponUpgrade` and the refusals `UpgradeMaxLevel` and
  `WeaponNotOwned`. Morningstar's speed share fell from 0.036 to 0.031 to keep
  Honing 10 inside the weapon band.
- **Workshop.** One Workshop overlay with Characters, Weapons and Core Matrix
  tabs replaces the Lab, the Armory and the Hangar, so the app has six routes
  and four overlays. W opens it; L, A and H open it on the Matrix, Weapons and
  Characters tabs (H over Home only). It opens over a paused run as well;
  characters change only over Home, and purchases apply from the next run. Each
  character and weapon detail has an upgrade block with the level and the next
  price, and a character's block names its Refit signature. Home has five rows
  (Play, Workshop, Rebirth, Codex, Settings) and a Characters card, two chips
  and a Workshop stamp that is the Profile's affordability read; the pause menu
  offers Resume, Settings, Workshop, Codex and Exit. The pinned
  `AppSessionApiContractTest.shellProjectionRetainsExactlyEightRoutes…` became
  `…ExactlySixRoutesAndOnlySessionOwnedWorkflowState`.
- **Level badge.** Tiers T1 to T5 keep their thresholds (1, 10, 20, 30, 40) and
  T6 APEX starts at the level cap with its own look and the Apex states; the
  Data bar and line change per tier and become the Apex meter at the cap; tier,
  unlock, ready, cast and refund edges play once. The skill row (Z, X, C) shows
  in every layout with key caps while a keyboard is in use, touch accepts five
  pointers with one steering, and the badge is the Apex control.
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
  session and the run. The interface scale applies to menus and to the run's
  HUD, while the world keeps the raw density. The worst-case Profile payload is
  7,678 UTF-16 code units of the 8,192 one Desktop Preferences value admits.

The performance suites are `gameplay-core-v4` (the reaction workload fills the
field table) and `profile-persistence-current-schema-v5` (the business maximum
carries every 0.5.0 field). Content goldens: items `0..172` `68d4572118b9586d`
(only the Data levers moved), items `173..268` `c11c59b06e52e3dc`, build tuning
`d6fa6a5d0a26712d`, weapon and Matrix economy `a668ef80e74bd333`, skills, Apex,
upgrades and tempo `e21686b5e4452b5e`; Rebirth `461252b1f47728a5` and Relics
`c8c02a43bec87968` are unchanged, and the seeded baseline trace still passes
unchanged.

The gates were calibrated on population seeds disjoint from the fixed gate seeds
and confirmed on the gate seeds. On the release candidate (`cecffe5a`) the
desktop suite ran 2,274 tests with 2 failures:

- Rebirth gates: A tier 0 fresh 4/5 (floor 4), B 5/5, C 0/5, D tier 10 strong 0
  wins with a median of 900 s, E 5/5 at tier 2 and 0/5 at tier 8, F 5/5 at tier
  3 and never at tier 10, G 0/5. New gate H (the strong build with every Matrix
  rank, seeking Data with the Apex at tier 10) wins 0/5 with a median of 666 s.
  New gate I (skills help a fresh tier-0 run without trivialising it) is judged
  on 31 seeds: 76.10 s against 81.25 s without skills, ratio 0.937 (bound 0.8);
  over 310 seeds it is 0.805.
- Level gate: fresh runs 10 of 11 alive at 720 s at levels 25 to 29 (band 22 to
  30); the no-POI reference run wins at 803.2 s at level 28. Full Matrix without
  Data items never reaches 42 before 720 s (levels 33 to 37 at tier 0; the few
  tier-3 and tier-6 runs that get there arrive after 740 s). Seeking Data
  reaches 42 in its window at tier 3 on 5 of 5 seeds (571 to 660 s) and at tier
  6 on 4 of 5 (569 to 608 s). At tier 0 it arrives at 646.7 to 724.1 s on four
  seeds and not at all on the fifth, 0 of 5 inside [510, 630] s: this gate
  fails.
- Apex fight gate: every Apex keeps the Architect contract at tiers 0 and 10
  (the volleys its own shots hold back stay under the bound), and an activation
  never lowers a character's damage to the Architect. The benefit arm fails: the
  median time-to-kill ratio is 0.9685 against the band [0.75, 0.95], and the
  Prism's ratio is 1.0 against [0.55, 0.85].
- Skill share: skills cause a median 0.111 of a fresh run's damage over the 25
  of 31 runs that reach 720 s (band 0.08 to 0.20); no single skill deals more
  than 0.280 of all skill damage (bound 0.30). Singularity contacts stay at 0
  with every Core-moving skill over 30 seeds.
- Item variety: the 12-seed gate reads Orb 0.728, Prism 0.721, Star 0.668 and
  Hexagon 0.692 of the overlap under a full deck and the 0.4.0 lanes (bound
  0.75), and 194 of 199 rank-0 items are shown. Over 48 seeds per character the
  ratios are Orb 0.697, Prism 0.692, Star 0.739 and Hexagon 0.794.
- Economy: median banked Matter is 2,619 fresh at tier 0, 5,256 with a full
  Matrix, 12,519 at tier 3, 22,800 at tier 6 and 44,771 for the strong tier-10
  build; the last Refit level costs 1.12 and the last Honing level 0.79 of the
  tier-6 median. With Refit and Honing at 10 the strong tier-10 build wins 0 of
  5, the half Matrix still loses all five at tier 8 and a fresh tier-6 run still
  never wins, while the Orb and the Pentagon fight 1.15 to 1.27 times stronger
  in the J5 encounters. Refit 10 keeps every character in its band (median
  1,807.7) and Honing 10 keeps every weapon in its family band (Morningstar
  crowd 1.32).

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
