// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.CombatFieldRecord
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.FieldSource
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

/** Deals the whole catalog into the run's draft deck, for checks of what the other offer gates allow. */
internal fun MutableGameState.dealWholeCatalog(): MutableGameState = apply {
    itemDeck = kinetickk.ball.gameplay.nucleus.model.ItemDeck.EVERYTHING
}

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

/** Admits a field from its natural source (items own Dark Stars, skills the skill kinds, reactions the rest). */
internal fun MutableGameState.addTestField(
    kind: CombatFieldKind,
    x: Float,
    y: Float,
    radius: Float,
    seconds: Float = 1f,
    damagePerSecond: Float = 0f,
    element: StatusElement? = null,
    stacksPerTick: Int = 0,
    generation: Int = 1,
    pull: Float = 0f,
    tickClock: Float = 0f,
    source: FieldSource = when (kind) {
        CombatFieldKind.DARK_STAR -> FieldSource.ITEM
        CombatFieldKind.FROST_LINE, CombatFieldKind.STORM_COIL, CombatFieldKind.GRAVITY_WELL -> FieldSource.SKILL
        else -> FieldSource.REACTION
    },
    owner: Int = 0,
): Int = addField(kind, source, owner, x, y, radius, seconds, damagePerSecond, element, stacksPerTick, generation, pull, tickClock)

/** Detached copies of the live fields in table order. */
internal val MutableGameState.fieldRecords: List<CombatFieldRecord> get() = combatFieldTable.records()

/** The canonical catalog with every reaction's verb values removed: each reaction keeps only its base effect. */
internal val contentWithoutReactionVerbs: GameplayContentSnapshot by lazy {
    canonicalGameplayContent.copy(
        build = canonicalGameplayContent.build.copy(
            reactions = canonicalGameplayContent.build.reactions.map {
                it.copy(values = kinetickk.foundation.collections.immutableListOf())
            }.toImmutableList(),
        ),
    )
}

/**
 * [content] whose offers never show an active item (the lane policy's active weight 0) while the catalog still holds
 * them: for the zero weight's own tests and for arms that acquire exactly the actives they measure. A run without active
 * items is [withoutActiveItemCatalog].
 */
internal fun withoutActiveItems(content: GameplayContentSnapshot = canonicalGameplayContent): GameplayContentSnapshot {
    val policy = content.build.policy
    return content.copy(
        build = content.build.copy(policy = policy.copy(offerLanes = policy.offerLanes.copy(activeWeight = 0f))),
    )
}

/**
 * [content] without its active items: the catalog ends before the first of them (they are appended after every passive
 * item) and the snapshot is rebuilt from it, so a run deals, offers and draws exactly as runs did before active items
 * existed. The "no actives" arm of the active-item gates.
 */
internal fun withoutActiveItemCatalog(content: GameplayContentSnapshot = canonicalGameplayContent): GameplayContentSnapshot {
    val end = content.items.indexOfFirst(ItemDefinition::isActive).takeIf { it >= 0 } ?: return content
    check(content.items.drop(end).all(ItemDefinition::isActive)) { "Active items must follow every passive item" }
    return content.copy(items = content.items.subList(0, end).toImmutableList())
}

/**
 * Takes [stacks] stacks of the active item carrying [id], as offers deal it: the first binds the effect into the lowest
 * empty slot at rank I, each later one ranks it. Fails when the effect did not end up bound (no free slot).
 */
internal fun MutableGameState.acquireActive(id: SkillId, stacks: Int = 1): ItemDefinition {
    val item = checkNotNull(content.activeItem(id)) { "no item carries $id" }
    repeat(stacks) { acquireItem(item.id) }
    check(skillLoadout.owns(id)) { "$id found no free slot" }
    return item
}

/** Every bound slot's rank equals its active item's stacks, and every owned active item is bound. */
internal fun MutableGameState.activeLoadoutMismatch(): String? {
    for (slot in 0 until kinetickk.ball.gameplay.nucleus.model.SkillRuntime.SLOTS) {
        val effect = skillLoadout.skill(slot) ?: continue
        val item = content.activeItem(effect) ?: return "slot $slot holds $effect, which no item carries"
        if (skillLoadout.rank(slot) != itemStacks[item.id]) {
            return "slot $slot holds $effect at rank ${skillLoadout.rank(slot)}, its item has ${itemStacks[item.id]} stacks"
        }
    }
    content.items.forEach { item ->
        val effect = item.active ?: return@forEach
        if (itemStacks[item.id] > 0 && !skillLoadout.owns(effect)) return "${item.name} is owned but its effect is not bound"
    }
    return null
}

/** Sets off [reaction] on [target]: [olderStacks] of its first element, then one of its second. */
internal fun MutableGameState.setOff(reaction: kinetickk.ball.content.api.ReactionId, target: Enemy, olderStacks: Int = 3) {
    applyStatus(target, reaction.first, olderStacks, generation = 0)
    applyStatus(target, reaction.second, 1, generation = 0)
}

/** Switches the keystone forms of [reactions] on, on top of the compiled build. */
internal fun MutableGameState.useKeystones(vararg reactions: kinetickk.ball.content.api.ReactionId) {
    build = compileBuild(keystoneMask = reactions.fold(0) { mask, reaction -> mask or (1 shl reaction.ordinal) })
}

/** A hostile bolt resting at ([x], [y]). */
internal fun MutableGameState.hostileBolt(x: Float, y: Float, damage: Float = 10f): kinetickk.ball.gameplay.nucleus.model.Projectile =
    kinetickk.ball.gameplay.nucleus.model.Projectile(x = x, y = y, vx = 0f, vy = 0f, radius = 5f, life = 3f, hostile = true,
        damage = damage).also { projectiles += it }

/** Aims the singularity at the world point ([x], [y]) (the camera sits on the Core). */
internal fun MutableGameState.aimAt(x: Float, y: Float) {
    cameraX = coreX
    cameraY = coreY
    pointerX = screenWidth * 0.5f + (x - coreX)
    pointerY = screenHeight * 0.5f + (y - coreY)
}

/** A started run whose Profile remembers [recent] (oldest first). */
internal fun startedWithHistory(seed: Int, recent: List<Int>): MutableGameState = MutableGameState(
    canonicalGameplayContent,
    seed = seed,
    bootstrapProgress = kinetickk.ball.profile.api.GameplayProfileSnapshot(
        preferences = kinetickk.ball.profile.api.PlayerPreferences(),
        economy = kinetickk.ball.profile.api.PlayerEconomy(),
        loadout = kinetickk.ball.profile.api.PlayerLoadout(),
        matrix = kinetickk.ball.profile.api.MatrixProgress(),
        collection = kinetickk.ball.profile.api.PlayerCollection(discoveredItemIds = emptySet(), recentItemIds = recent),
        rebirthProgress = kinetickk.ball.profile.api.RebirthProgress(level = 0),
    ),
).apply { startRun() }
