// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList

/**
 * A started run with an empty, quiet arena: no enemies, no spawns, no grace period and drained outputs.
 * Build behavior tests drive the owning systems directly against it.
 */
internal fun buildTestState(
    vararg relics: RelicId,
    seed: Int = 5_150,
    rebirth: Int = 0,
    shape: CoreShape = CoreShape.ORB,
    content: GameplayContentSnapshot = canonicalGameplayContent,
    /** The run's weapon; its Relic key harmonizes with bound Relics carrying it. */
    weapon: WeaponId = WeaponId.FLUX_WAKE,
): MutableGameState = MutableGameState(
    content,
    seed = seed,
    initialMatter = 0,
    initialRebirthLevel = rebirth,
).apply {
    coreShape = shape
    unlockWeaponsForTesting(listOf(weapon))
    startingWeapon = weapon
    resize(1_280f, 720f)
    startRun()
    enemies.clear()
    projectiles.clear()
    pickups.clear()
    spawnClock = Float.MAX_VALUE
    nextEliteAt = Float.MAX_VALUE
    runGrace = 0f
    relics.forEach(::acquireRelic)
    takeVisualFxCues()
    takeSoundCues()
    takeProgressUpdate()
}

internal fun MutableGameState.itemNamed(name: String): ItemDefinition = content.items.single { it.name == name }

/** Acquires [stacks] stacks of the item called [name]. */
internal fun MutableGameState.own(name: String, stacks: Int = 1, reborn: Boolean = false): ItemDefinition {
    val item = itemNamed(name)
    repeat(stacks) { acquireItem(item.id, reborn) }
    return item
}

internal fun Enemy.stacksOf(element: StatusElement): Int = status[StatusCell.STACKS + element.ordinal].toInt()

internal fun Enemy.generationOf(element: StatusElement): Int = status[StatusCell.GENERATION + element.ordinal].toInt()

/** A sturdy target that survives every proc in these tests. */
internal fun MutableGameState.dummy(
    x: Float = 400f,
    y: Float = 0f,
    hp: Float = 1_000_000f,
    type: kinetickk.ball.gameplay.nucleus.render.EnemyType = kinetickk.ball.gameplay.nucleus.render.EnemyType.DRIFTER,
): Enemy = addEnemyForTesting(x = x, y = y, hp = hp, type = type)

/** The canonical catalog with the clauses of the item called [name] replaced, for dispatch rules no item combines. */
internal fun contentWithClauses(name: String, vararg clauses: EffectClause): GameplayContentSnapshot =
    canonicalGameplayContent.copy(
        items = canonicalGameplayContent.items.map { item ->
            if (item.name == name) item.copy(clauses = clauses.toList().toImmutableList()) else item
        }.toImmutableList(),
    )
