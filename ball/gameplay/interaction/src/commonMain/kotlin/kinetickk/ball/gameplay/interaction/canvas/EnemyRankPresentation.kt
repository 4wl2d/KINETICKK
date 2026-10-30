// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel

// How the HUD reads an enemy's content rank: which boss its top block tracks, how wide that block is, how many parts
// its bar has, and whether the block shows at all. Pure reads of the content roster; no allocation.

/** The order in which the HUD tracks a boss: the Architect, a secret boss, a mini-boss, an Elite; never an ordinary kind. */
internal fun bossTargetPriority(rank: EnemyRank): Int = when (rank) {
    EnemyRank.BOSS -> 4
    EnemyRank.SECRET_BOSS -> 3
    EnemyRank.MINI_BOSS -> 2
    EnemyRank.ELITE -> 1
    EnemyRank.ORDINARY -> 0
}

/** The wide boss block (the Architect's bar width, its name in the wide type, its weakness in the icon slot). */
internal fun wideBossBlock(rank: EnemyRank?): Boolean = rank == EnemyRank.BOSS || rank == EnemyRank.SECRET_BOSS

/**
 * Parts of [type]'s boss bar at Rebirth tier [rebirthLevel]: its phases, plus its enraged phases while their mutator is
 * selected (the Architect 3, 4 at the Singularity tier; mini-bosses 3; the Antecedent 4). 0 for a kind without an
 * encounter spec (the Elite, the Herald), whose bar stays the 16-segment meter.
 */
internal fun bossBarParts(content: GameplayContentSnapshot, rebirthLevel: Int, type: EnemyType): Int {
    val spec = content.enemy(type).encounter ?: return 0
    val mutator = spec.enrageMutator
    val enraged = mutator != null && content.rebirth.profile(rebirthLevel).has(mutator)
    return spec.phases + if (enraged) spec.enragePhases else 0
}

/** A secret boss's minimap ring: its radius in minimap dots, around the big pin of every boss rank (3 dots). */
internal const val SECRET_RING_DOTS: Float = 4.4f

/** Whether a live enemy of any rank above ordinary shows the boss block, which the world keeps clear of. */
internal fun bossBlockShown(engine: GameplayRenderModel): Boolean {
    val enemies = engine.enemies
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead && engine.content.enemy(enemy.type).rank != EnemyRank.ORDINARY) return true
    }
    return false
}
