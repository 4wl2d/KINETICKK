// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two minutes of the ambient director in each layer a run can stand in: every enemy it spawns comes from that layer's
 * habitat roster, the mix holds at least two kinds, and a seed replays its spawns exactly.
 */
class HabitatPacingTest {
    @Test
    fun eachLayerSpawnsOnlyItsHabitatRosterForTwoMinutes() {
        for ((layer, roster) in LAYER_ROSTERS) {
            for (seed in SEEDS) {
                val spawns = twoMinutesIn(layer, seed)
                val ordinary = spawns.filter { it.type.profile.rank == EnemyRank.ORDINARY }.map { it.type }.toSet()
                assertTrue(roster.containsAll(ordinary), "$layer seed=$seed spawned ${ordinary - roster}")
                assertTrue(ordinary.size >= 2, "$layer seed=$seed spawned only $ordinary")
                if (layer != WorldLayer.BASE) {
                    // Elites and Heralds stay in realspace; nothing but the layer's ordinary kinds arrives.
                    assertEquals(spawns.size, spawns.count { it.type.profile.rank == EnemyRank.ORDINARY }, "$layer seed=$seed")
                }
                assertEquals(spawns, twoMinutesIn(layer, seed), "$layer seed=$seed replays its spawns")
            }
        }
    }

    private data class Spawn(val type: EnemyType, val x: Int, val y: Int, val second: Int)

    /**
     * A tier-0 run flies realspace for a minute (a rift opens from the 45 s window), then stands in [layer] for two
     * minutes. Entering clears the hostiles and drops an event planned in realspace, as a layer switch does.
     */
    private fun twoMinutesIn(layer: WorldLayer, seed: Int): List<Spawn> {
        val state = MutableGameState(canonicalGameplayContent, seed = seed, initialMatter = 0).apply {
            resize(1_280f, 720f)
            startRun()
            invulnerableUntil = Float.MAX_VALUE
            runGrace = Float.MAX_VALUE
        }
        if (layer != WorldLayer.BASE) {
            state.drive(ENTRY_SECONDS) {}
            state.enemies.clear()
            state.projectiles.removeAll { it.hostile }
            state.worldLayer = layer
            state.onWorldLayerChanged(WorldLayer.BASE, layer)
            state.pendingEventKind = -1
            state.pendingEventSize = 0
            state.pendingEventType = -1
        }
        val spawns = mutableListOf<Spawn>()
        val seen = mutableSetOf<Int>()
        state.drive(HABITAT_SECONDS) {
            state.enemies.forEach { enemy ->
                if (seen.add(enemy.id)) spawns += Spawn(enemy.type, enemy.x.toInt(), enemy.y.toInt(), state.elapsed.toInt())
            }
        }
        return spawns
    }

    /** The Core circles; enemies reaching it are cleared like kills, so the caps keep room for events. */
    private fun MutableGameState.drive(seconds: Float, observe: () -> Unit) {
        val end = elapsed + seconds
        while (elapsed < end) {
            val angle = elapsed * 0.6f
            updatePointer(640f + cos(angle) * 260f, 360f + sin(angle) * 260f)
            simulateStep(MutableGameState.FIXED_STEP)
            observe()
            enemies.removeAll { enemy -> abs(enemy.x - coreX) + abs(enemy.y - coreY) < 320f && !enemy.isMiniBoss }
            pickups.clear()
            if (worldLayer == WorldLayer.BASE) quietArenaForTesting()
            takeVisualFxCues()
            takeSoundCues()
        }
    }

    private companion object {
        val SEEDS = listOf(101, 202, 303)
        const val ENTRY_SECONDS = 60f
        const val HABITAT_SECONDS = 120f

        val LAYER_ROSTERS = mapOf(
            WorldLayer.BASE to setOf(
                EnemyType.DRIFTER, EnemyType.SHOOTER, EnemyType.CHARGER, EnemyType.INTERCEPTOR, EnemyType.WEAVER,
                EnemyType.WARDEN, EnemyType.SPLITTER, EnemyType.SWARMLING, EnemyType.BOMBER, EnemyType.LANCER,
                EnemyType.SNIPER, EnemyType.AEGIS, EnemyType.MINELAYER, EnemyType.PHANTOM, EnemyType.BROODMOTHER,
                EnemyType.REFLECTOR, EnemyType.COMET, EnemyType.PYLON,
            ),
            WorldLayer.ZENITH to setOf(
                EnemyType.SHOOTER, EnemyType.WEAVER, EnemyType.SNIPER, EnemyType.AEGIS, EnemyType.SERAPH, EnemyType.CHOIR,
            ),
            WorldLayer.NADIR to setOf(
                EnemyType.CHARGER, EnemyType.SPLITTER, EnemyType.BOMBER, EnemyType.MINELAYER, EnemyType.IMP, EnemyType.HOUND,
            ),
            WorldLayer.ARCHIVE to setOf(
                EnemyType.DRIFTER, EnemyType.SNIPER, EnemyType.PHANTOM, EnemyType.REFLECTOR, EnemyType.CIPHER, EnemyType.MIRROR,
            ),
        )
    }
}
