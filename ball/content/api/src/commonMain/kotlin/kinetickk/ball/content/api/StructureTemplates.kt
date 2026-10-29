// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.ball.content.api.EnemyType.BROOD_QUEEN
import kinetickk.ball.content.api.EnemyType.AEGIS
import kinetickk.ball.content.api.EnemyType.BOMBER
import kinetickk.ball.content.api.EnemyType.CHOIR
import kinetickk.ball.content.api.EnemyType.CRAWLER
import kinetickk.ball.content.api.EnemyType.LANCER
import kinetickk.ball.content.api.EnemyType.MINELAYER
import kinetickk.ball.content.api.EnemyType.SENTRY
import kinetickk.ball.content.api.EnemyType.SHOOTER
import kinetickk.ball.content.api.EnemyType.SNIPER
import kinetickk.ball.content.api.EnemyType.WARDEN
import kinetickk.ball.content.api.EnemyType.WARDEN_PRIME
import kinetickk.ball.content.api.EnemyType.WEAVER
import kinetickk.ball.content.api.GarrisonPlacement.CENTER
import kinetickk.ball.content.api.GarrisonPlacement.FLOOR
import kinetickk.ball.content.api.GarrisonPlacement.WALL
import kinetickk.ball.content.api.StructureRewardKind.DATA
import kinetickk.ball.content.api.StructureRewardKind.ITEM_OFFER
import kinetickk.ball.content.api.StructureRewardKind.MATTER
import kinetickk.ball.content.api.StructureRewardKind.REPAIR
import kinetickk.ball.content.api.WallAxis.HORIZONTAL
import kinetickk.ball.content.api.WallAxis.VERTICAL
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/**
 * The shipped structure templates in [StructureTemplateId] order (weights total 103 from 12,000 u). Template space:
 * origin at the footprint centre, +x east, +y south; room indices are list positions; a door joins rooms `a` and `b`.
 */
fun defaultStructureTemplates(): ImmutableList<StructureTemplate> = listOf(
    derelictStation(), vault(), spire(), labyrinth(), foundry(), reliquary(), architectsDoodle(),
).toImmutableList()

/** Airlock, long hall, side bay, reactor: `out -D0- 0 -D1- 1 -D2- 3 -D3- 2`. */
private fun derelictStation() = StructureTemplate(
    id = StructureTemplateId.DERELICT_STATION,
    name = "Derelict Station",
    weight = 30f,
    minimumDistance = 4_000f,
    element = StatusElement.ION,
    trialDescription = "Ion trial: shooters and lancers turn the station's long halls into firing lanes. Cross their aim to close the distance.",
    garrisonTypes = immutableListOf(SHOOTER, LANCER),
    rooms = immutableListOf(
        room(-1100, -300, -500, 300, habitat(3)), // 0 Airlock (entrance)
        room(-500, -700, 400, 700, habitat(4), fixed(SENTRY, 2, WALL)), // 1 Hall
        room(400, -700, 1100, 100, habitat(4), fixed(SENTRY, 2, WALL)), // 2 Reactor (final)
        room(400, 100, 1100, 700, fixed(CRAWLER, 4), habitat(2)), // 3 Bay
    ),
    doors = immutableListOf(
        door(VERTICAL, -1100, 0, 0, OUTSIDE),
        door(VERTICAL, -500, 0, 0, 1),
        door(VERTICAL, 400, 400, 1, 3),
        door(HORIZONTAL, 100, 750, 3, 2),
    ),
    entranceRoom = 0,
    finalRoom = 2,
    rewards = immutableListOf(aspect(64), reward(DATA, 4)),
)

/**
 * Antechamber, two galleries and a vault that opens only after both galleries:
 * `out -D0- 0`, `0 -D1- 1`, `0 -D2- 2`, `1 -D3(lock 1,2)- 3`, `2 -D4(lock 1,2)- 3`.
 */
private fun vault() = StructureTemplate(
    id = StructureTemplateId.VAULT,
    name = "Vault",
    weight = 22f,
    minimumDistance = 6_000f,
    element = StatusElement.CRYO,
    trialDescription = "Cryo trial: weavers and snipers cover the galleries. Clear both wings before confronting the brood guardian.",
    garrisonTypes = immutableListOf(WEAVER, SNIPER),
    rooms = immutableListOf(
        room(-400, 300, 400, 1100, habitat(3)), // 0 Antechamber (entrance)
        room(-1100, -1100, -400, 1100, habitat(4), fixed(SENTRY, 2, WALL)), // 1 West gallery
        room(400, -1100, 1100, 1100, habitat(3), fixed(CRAWLER, 4)), // 2 East gallery
        room(-400, -1100, 400, 300, fixed(BROOD_QUEEN, 1, CENTER), fixed(CRAWLER, 3)), // 3 Vault (final)
    ),
    doors = immutableListOf(
        door(HORIZONTAL, 1100, 0, 0, OUTSIDE),
        door(VERTICAL, -400, 700, 0, 1),
        door(VERTICAL, 400, 700, 0, 2),
        door(VERTICAL, -400, -700, 1, 3, lock = 0b0110),
        door(VERTICAL, 400, -700, 2, 3, lock = 0b0110),
    ),
    entranceRoom = 0,
    finalRoom = 3,
    rewards = immutableListOf(aspect(65), reward(ITEM_OFFER, floor = ItemRarity.RARE)),
)

/** Four stacked halls with alternating doors and a fin in each middle hall; linear `0 -> 1 -> 2 -> 3`. */
private fun spire() = StructureTemplate(
    id = StructureTemplateId.SPIRE,
    name = "Spire",
    weight = 16f,
    minimumDistance = 5_000f,
    element = StatusElement.SONIC,
    trialDescription = "Sonic trial: choirs fill narrow halls with radial pulses and pushback. Use the fins to break their lines of fire.",
    garrisonTypes = immutableListOf(CHOIR),
    rooms = immutableListOf(
        room(-500, 600, 500, 1200, habitat(4)), // 0 Base (entrance)
        room(-500, 0, 500, 600, fixed(CHOIR, 2, WALL), habitat(2)), // 1 Lower hall
        room(-500, -600, 500, 0, fixed(CHOIR, 2, WALL), fixed(CRAWLER, 3)), // 2 Upper hall
        room(-500, -1200, 500, -600, fixed(CHOIR, 3, WALL), habitat(2)), // 3 Crown (final)
    ),
    doors = immutableListOf(
        door(HORIZONTAL, 1200, 0, 0, OUTSIDE),
        door(HORIZONTAL, 600, -250, 0, 1),
        door(HORIZONTAL, 0, 250, 1, 2),
        door(HORIZONTAL, -600, -250, 2, 3),
    ),
    extraWalls = immutableListOf(
        StructureCapsule(-150f, 300f, 150f, 300f, 24f),
        StructureCapsule(-150f, -300f, 150f, -300f, 24f),
    ),
    entranceRoom = 0,
    finalRoom = 3,
    rewards = immutableListOf(aspect(66), reward(DATA, 4)),
)

/**
 * 3x3 cells of 700 (index = row x 3 + col, row 0 north), a maze tree with two optional side rooms and 260 u doors:
 * `out -D0- 6 -D1- 7 -D2- 4 -D3- 3 -D4- 0 -D5- 1 -D8- 2`, side branch `4 -D6- 5 -D7- 8`.
 */
private fun labyrinth() = StructureTemplate(
    id = StructureTemplateId.LABYRINTH,
    name = "Labyrinth",
    weight = 14f,
    minimumDistance = 7_000f,
    element = StatusElement.ENTROPY,
    trialDescription = "Entropy trial: crawlers pursue through the maze while minelayers deny corners. Leave room to turn before entering a side chamber.",
    garrisonTypes = immutableListOf(CRAWLER, MINELAYER),
    rooms = immutableListOf(
        room(-1050, -1050, -350, -350, habitat(2), fixed(SENTRY, 1, WALL)), // 0 NW
        room(-350, -1050, 350, -350, fixed(CRAWLER, 4)), // 1 N
        room(350, -1050, 1050, -350, habitat(4), fixed(CRAWLER, 3)), // 2 NE (final)
        room(-1050, -350, -350, 350), // 3 W (passage)
        room(-350, -350, 350, 350, habitat(3), fixed(CRAWLER, 2)), // 4 Centre
        room(350, -350, 1050, 350, habitat(3)), // 5 E (side)
        room(-1050, 350, -350, 1050, fixed(CRAWLER, 3)), // 6 SW (entrance)
        room(-350, 350, 350, 1050), // 7 S (passage)
        room(350, 350, 1050, 1050, fixed(CRAWLER, 5)), // 8 SE (side)
    ),
    doors = immutableListOf(
        door(HORIZONTAL, 1050, -700, 6, OUTSIDE, 260),
        door(VERTICAL, -350, 700, 6, 7, 260),
        door(HORIZONTAL, 350, 0, 7, 4, 260),
        door(VERTICAL, -350, 0, 4, 3, 260),
        door(HORIZONTAL, -350, -700, 3, 0, 260),
        door(VERTICAL, -350, -700, 0, 1, 260),
        door(VERTICAL, 350, 0, 4, 5, 260),
        door(HORIZONTAL, 350, 700, 5, 8, 260),
        door(VERTICAL, 350, -700, 1, 2, 260),
    ),
    entranceRoom = 6,
    finalRoom = 2,
    rewards = immutableListOf(aspect(67), reward(MATTER, 40)),
)

/** Two smelting halls (two doors between them, pillars, a conveyor fin) and a furnace: `out -D0- 0 -D1,D2- 1 -D3- 2`. */
private fun foundry() = StructureTemplate(
    id = StructureTemplateId.FOUNDRY,
    name = "Foundry",
    weight = 12f,
    minimumDistance = 6_000f,
    element = StatusElement.THERMAL,
    trialDescription = "Thermal trial: sentry volleys and exploding bombers pressure the smelting halls. Pillars offer cover before the furnace fight.",
    garrisonTypes = immutableListOf(SENTRY, BOMBER),
    rooms = immutableListOf(
        room(-1200, -800, -100, 800, habitat(6)), // 0 West hall (entrance)
        room(-100, -800, 700, 800, habitat(4), fixed(SENTRY, 2, WALL)), // 1 East hall
        room(700, -400, 1200, 400, fixed(WARDEN, 2), habitat(4)), // 2 Furnace (final)
    ),
    doors = immutableListOf(
        door(VERTICAL, -1200, 0, 0, OUTSIDE),
        door(VERTICAL, -100, -500, 0, 1),
        door(VERTICAL, -100, 500, 0, 1),
        door(VERTICAL, 700, 0, 1, 2),
    ),
    extraWalls = immutableListOf(
        pillar(-850, -350), pillar(-450, -350), pillar(-850, 350), pillar(-450, 350),
        StructureCapsule(100f, 0f, 500f, 0f, 24f), pillar(300, -450), pillar(300, 450),
    ),
    entranceRoom = 0,
    finalRoom = 2,
    rewards = immutableListOf(aspect(68), reward(REPAIR)),
)

/**
 * 3x3 cells of 800: four guarded chapels in the corners, four empty walks and a sanctum whose four doors open only when
 * every chapel is cleared. Ring `7 -D1- 6 -D3- 3 -D5- 0 -D7- 1 -D8- 2 -D6- 5 -D4- 8 -D2- 7`, `out -D0- 7`, sanctum
 * `7/3/5/1 -D9..D12 (chapels)- 4`.
 */
private fun reliquary() = StructureTemplate(
    id = StructureTemplateId.RELIQUARY,
    name = "Reliquary",
    weight = 6f,
    minimumDistance = 9_000f,
    element = StatusElement.GRAVITIC,
    trialDescription = "Gravitic trial: aegis guards and wardens hold four chapels. Break their defensive formations to unlock Warden Prime's sanctum.",
    garrisonTypes = immutableListOf(AEGIS, WARDEN),
    rooms = immutableListOf(
        room(-1200, -1200, -400, -400, habitat(3), fixed(SENTRY, 1, WALL)), // 0 NW chapel
        room(-400, -1200, 400, -400), // 1 North walk
        room(400, -1200, 1200, -400, habitat(2), fixed(CRAWLER, 3)), // 2 NE chapel
        room(-1200, -400, -400, 400), // 3 West walk
        room(-400, -400, 400, 400, fixed(WARDEN_PRIME, 1, CENTER), habitat(3)), // 4 Sanctum (final)
        room(400, -400, 1200, 400), // 5 East walk
        room(-1200, 400, -400, 1200, habitat(2), fixed(CRAWLER, 3)), // 6 SW chapel
        room(-400, 400, 400, 1200), // 7 Nave (entrance)
        room(400, 400, 1200, 1200, habitat(3), fixed(SENTRY, 1, WALL)), // 8 SE chapel
    ),
    doors = immutableListOf(
        door(HORIZONTAL, 1200, 0, 7, OUTSIDE),
        door(VERTICAL, -400, 800, 7, 6),
        door(VERTICAL, 400, 800, 7, 8),
        door(HORIZONTAL, 400, -800, 6, 3),
        door(HORIZONTAL, 400, 800, 8, 5),
        door(HORIZONTAL, -400, -800, 3, 0),
        door(HORIZONTAL, -400, 800, 5, 2),
        door(VERTICAL, -400, -800, 0, 1),
        door(VERTICAL, 400, -800, 2, 1),
        door(HORIZONTAL, 400, 0, 7, 4, lock = CHAPELS),
        door(VERTICAL, -400, 0, 3, 4, lock = CHAPELS),
        door(VERTICAL, 400, 0, 5, 4, lock = CHAPELS),
        door(HORIZONTAL, -400, 0, 1, 4, lock = CHAPELS),
    ),
    entranceRoom = 7,
    finalRoom = 4,
    rewards = immutableListOf(aspect(69), reward(ITEM_OFFER, floor = ItemRarity.EPIC)),
)

/**
 * The Home logo lockup (bone planet r 21 on a 64 grid, an ink slash and the colinear accent slash) scaled by 32 u per
 * grid unit about (32, 32): a 16-chord ring of radius 672 with the four chords the ink band crosses removed (two ring
 * openings), and the accent slash as one long wall through the planet. The chords are literals, never runtime
 * trigonometry. One room, the inscribed square, holds no garrison; reaching it records the secret.
 */
private fun architectsDoodle() = StructureTemplate(
    id = StructureTemplateId.ARCHITECTS_DOODLE,
    name = "The Architect's Doodle",
    weight = 3f,
    minimumDistance = 12_000f,
    element = StatusElement.VOID,
    rooms = immutableListOf(room(-441, -441, 441, 441)),
    extraWalls = immutableListOf(
        chord(659.1f, 131.1f, 558.7f, 373.3f),
        chord(558.7f, 373.3f, 373.3f, 558.7f),
        chord(373.3f, 558.7f, 131.1f, 659.1f),
        chord(131.1f, 659.1f, -131.1f, 659.1f),
        chord(-131.1f, 659.1f, -373.3f, 558.7f),
        chord(-659.1f, 131.1f, -659.1f, -131.1f),
        chord(-659.1f, -131.1f, -558.7f, -373.3f),
        chord(-558.7f, -373.3f, -373.3f, -558.7f),
        chord(-373.3f, -558.7f, -131.1f, -659.1f),
        chord(-131.1f, -659.1f, 131.1f, -659.1f),
        chord(131.1f, -659.1f, 373.3f, -558.7f),
        chord(659.1f, -131.1f, 659.1f, 131.1f),
        chord(-1088f, 566.4f, 1088f, -566.4f),
    ),
    roomWalls = false,
    entranceRoom = 0,
    finalRoom = 0,
    rewards = immutableListOf(reward(DATA, 12)),
    secret = true,
)

private const val OUTSIDE: Int = STRUCTURE_OUTSIDE

/** The Reliquary's four corner chapels (rooms 0, 2, 6 and 8). */
private const val CHAPELS: Int = (1 shl 0) or (1 shl 2) or (1 shl 6) or (1 shl 8)

private fun room(minX: Int, minY: Int, maxX: Int, maxY: Int, vararg garrison: GarrisonEntry) =
    StructureRoom(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat(), garrison.toList().toImmutableList())

private fun habitat(count: Int) = GarrisonEntry(GarrisonSelector.HABITAT, count)

private fun fixed(type: EnemyType, count: Int, placement: GarrisonPlacement = FLOOR) =
    GarrisonEntry(GarrisonSelector.FIXED, count, type, placement)

private fun door(axis: WallAxis, line: Int, center: Int, a: Int, b: Int, width: Int = 280, lock: Int = 0) =
    StructureDoor(axis, line.toFloat(), center.toFloat(), width.toFloat(), a, b, lock)

private fun reward(kind: StructureRewardKind, amount: Int = 1, floor: ItemRarity? = null) =
    StructureReward(kind, amount, floor)

private fun aspect(id: Int) = StructureReward(StructureRewardKind.ASPECT, boonId = id)

private fun pillar(x: Int, y: Int) = StructureCapsule(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), 70f)

private fun chord(ax: Float, ay: Float, bx: Float, by: Float) = StructureCapsule(ax, ay, bx, by, 24f)
