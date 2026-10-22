// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.mapToImmutableList
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*


internal fun MutableGameState.toRenderModel(
    reusableCollections: GameplayRenderModel? = null,
    identitySource: MutableGameState? = null,
): GameplayRenderModel {
    require(identitySource == null || identitySource.content === content) {
        "Identity-based Gameplay projection reuse requires matching content"
    }
    require(
        reusableCollections == null ||
            identitySource == null ||
            reusableCollections.content === identitySource.content,
    ) {
        "Reusable Gameplay projection does not match its identity source"
    }
    return GameplayRenderModel(
        content = content,
        phase = phase,
        settings = settings,
        rebirthLevel = rebirthLevel,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        coreX = coreX,
        coreY = coreY,
        velocityX = velocityX,
        velocityY = velocityY,
        cameraX = cameraX,
        cameraY = cameraY,
        pointerX = pointerX,
        pointerY = pointerY,
        pointerActive = pointerActive,
        braking = braking,
        elapsed = elapsed,
        heat = heat,
        overheated = overheated && !build.upside(kinetickk.ball.content.api.RelicId.FURNACE_COVENANT),
        dashPhaseTime = dashPhaseTime,
        hp = hp,
        maxHp = maxHp,
        shield = shield,
        maxShield = shieldCapacity(),
        level = level,
        data = data,
        nextLevelData = nextLevelData,
        keys = keys,
        kills = kills,
        runStatistics = runStatisticsProjection(reusableCollections?.runStatistics),
        combo = combo,
        comboTime = comboTime,
        runMatter = runMatter,
        totalMatter = totalMatter,
        lastImpact = lastImpact,
        lastImpactTime = lastImpactTime,
        damageFlash = damageFlash,
        runGrace = runGrace,
        screenShake = screenShake,
        message = message,
        messageTime = messageTime,
        mass = mass,
        damageMultiplier = damageMultiplier,
        weaponPower = weaponPower,
        effectiveWeaponPower = effectiveWeaponPower(),
        rewardPreviews = if (phase == GamePhase.CHOICE && identitySource?.phase == GamePhase.CHOICE &&
            choices === identitySource.choices && reusableCollections != null &&
            buildStats() == identitySource.buildStats() &&
            equippedRelics === identitySource.equippedRelics && weapon == identitySource.weapon && elapsed == identitySource.elapsed &&
            itemStacks.sharesStorageWith(identitySource.itemStacks) && build === identitySource.build &&
            discoveredPairingMask == identitySource.discoveredPairingMask &&
            discoveredReactionMask == identitySource.discoveredReactionMask
        ) reusableCollections.rewardPreviews else rewardPreviews(),
        coolingRate = coolingRate,
        magnetStrength = magnetStrength,
        dashImpulse = dashImpulse,
        dashHeatCost = effectiveDashHeatCost(),
        regenPerSecond = regenPerSecond,
        critChance = critChance,
        critMultiplier = critMultiplier,
        pickupRadius = effectivePickupRadius(),
        luck = effectiveLuck(),
        dataGain = dataGain,
        matterGain = matterGain,
        attackSpeed = attackSpeed,
        damageReduction = damageReduction,
        comboWindow = effectiveComboWindow(),
        overdriveGain = overdriveGain,
        dragCoefficient = dragCoefficient,
        polarityStability = polarityStability,
        weapon = weapon,
        weaponLevel = weaponLevel,
        overdriveCharge = overdriveCharge,
        overdriveTime = overdriveTime,
        rerollsRemaining = rerollsRemaining,
        acquiredItemCount = acquiredItemCount,
        recentItem = recentItem,
        equippedRelics = equippedRelics.reuseIfIdentical(
            identitySource?.equippedRelics,
            reusableCollections?.equippedRelics,
        ) ?: equippedRelics.reuseIfContentEqual(reusableCollections?.equippedRelics)
            ?: equippedRelics.toImmutableList(),
        morningstarAngle = morningstarAngle,
        morningstarX = morningstarX,
        morningstarY = morningstarY,
        weaponBeamTime = weaponBeamTime,
        weaponBeamStartX = weaponBeamStartX,
        weaponBeamStartY = weaponBeamStartY,
        weaponBeamEndX = weaponBeamEndX,
        weaponBeamEndY = weaponBeamEndY,
        totem = totem?.let { value ->
            reusableCollections?.totem?.takeIf { previous ->
                value === identitySource?.totem ||
                    (value.x.sameBitsAs(previous.x) &&
                        value.y.sameBitsAs(previous.y) &&
                        value.pulse.sameBitsAs(previous.pulse))
            } ?: TotemProjection(value.x, value.y, value.pulse)
        },
        coreShape = coreShape,
        enemies = enemies.reuseIfIdentical(identitySource?.enemies, reusableCollections?.enemies)
            ?: enemies.reuseEmptyProjection(reusableCollections?.enemies)
            ?: enemies.reuseProjectionIfContentEqual(reusableCollections?.enemies) { value, previous ->
                value.id == previous.id &&
                    value.type == previous.type &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.vx.sameBitsAs(previous.vx) &&
                    value.vy.sameBitsAs(previous.vy) &&
                    value.hp.sameBitsAs(previous.hp) &&
                    value.maxHp.sameBitsAs(previous.maxHp) &&
                    value.radius.sameBitsAs(previous.radius) &&
                    value.actionTimer.sameBitsAs(previous.actionTimer) &&
                    value.flash.sameBitsAs(previous.flash) &&
                    value.contactCooldown.sameBitsAs(previous.contactCooldown) &&
                    value.weaponCooldown.sameBitsAs(previous.weaponCooldown) &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY) &&
                    value.dead == previous.dead &&
                    statusBits(value) == previous.statusBits &&
                    affinityBits(value) == previous.affinityBits &&
                    value.mode == previous.behavior &&
                    behaviorRemaining(value).sameBitsAs(previous.behaviorRemaining) &&
                    value.facing.sameBitsAs(previous.facing) &&
                    linkedTo(value) == previous.linkedTo &&
                    shielded(value) == previous.shielded &&
                    value.armorIntact == previous.armored &&
                    controlBits(value) == previous.controlBits
            }
            ?: enemies.mapToImmutableList { value ->
                EnemyProjection(
                    id = value.id,
                    type = value.type,
                    x = value.x,
                    y = value.y,
                    vx = value.vx,
                    vy = value.vy,
                    hp = value.hp,
                    maxHp = value.maxHp,
                    radius = value.radius,
                    actionTimer = value.actionTimer,
                    flash = value.flash,
                    contactCooldown = value.contactCooldown,
                    weaponCooldown = value.weaponCooldown,
                    previousX = value.previousX,
                    previousY = value.previousY,
                    dead = value.dead,
                    statusBits = statusBits(value),
                    affinityBits = affinityBits(value),
                    behavior = value.mode,
                    behaviorRemaining = behaviorRemaining(value),
                    facing = value.facing,
                    linkedTo = linkedTo(value),
                    shielded = shielded(value),
                    armored = value.armorIntact,
                    controlBits = controlBits(value),
                )
            },
        projectiles = projectiles.reuseEmptyProjection(reusableCollections?.projectiles)
            ?: projectiles.reuseIfIdentical(
                identitySource?.projectiles,
                reusableCollections?.projectiles,
            )
            ?: projectiles.reuseProjectionIfContentEqual(
                reusableCollections?.projectiles,
            ) { value, previous ->
                value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.vx.sameBitsAs(previous.vx) &&
                    value.vy.sameBitsAs(previous.vy) &&
                    value.radius.sameBitsAs(previous.radius) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.hostile == previous.hostile &&
                    value.damage.sameBitsAs(previous.damage) &&
                    value.pierce == previous.pierce &&
                    value.colorIndex == previous.colorIndex &&
                    value.sourceWeapon == previous.sourceWeapon &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY) &&
                    value.kind == previous.kind
            }
            ?: projectiles.mapToImmutableList { value ->
                ProjectileProjection(
                    x = value.x,
                    y = value.y,
                    vx = value.vx,
                    vy = value.vy,
                    radius = value.radius,
                    life = value.life,
                    hostile = value.hostile,
                    damage = value.damage,
                    pierce = value.pierce,
                    colorIndex = value.colorIndex,
                    sourceWeapon = value.sourceWeapon,
                    previousX = value.previousX,
                    previousY = value.previousY,
                    kind = value.kind,
                )
            },
        pickups = pickups.reuseEmptyProjection(reusableCollections?.pickups)
            ?: pickups.reuseIfIdentical(identitySource?.pickups, reusableCollections?.pickups)
            ?: pickups.reuseProjectionIfContentEqual(reusableCollections?.pickups) { value, previous ->
                value.type == previous.type &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.vx.sameBitsAs(previous.vx) &&
                    value.vy.sameBitsAs(previous.vy) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY)
            }
            ?: pickups.mapToImmutableList { value ->
                PickupProjection(
                    value.type,
                    value.x,
                    value.y,
                    value.vx,
                    value.vy,
                    value.life,
                    value.previousX,
                    value.previousY,
                )
            },
        trail = trail.reuseEmptyProjection(reusableCollections?.trail)
            ?: trail.reuseIfIdentical(identitySource?.trail, reusableCollections?.trail)
            ?: trail.reuseProjectionIfContentEqual(reusableCollections?.trail) { value, previous ->
                value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.age.sameBitsAs(previous.age) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.scale.sameBitsAs(previous.scale)
            }
            ?: trail.mapToImmutableList { value -> TrailPointProjection(value.x, value.y, value.age, value.life, value.scale) },
        weaponNodes = weaponNodes.reuseEmptyProjection(reusableCollections?.weaponNodes)
            ?: weaponNodes.reuseIfIdentical(
                identitySource?.weaponNodes,
                reusableCollections?.weaponNodes,
            )
            ?: weaponNodes.reuseProjectionIfContentEqual(
                reusableCollections?.weaponNodes,
            ) { value, previous ->
                value.type == previous.type &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.maxLife.sameBitsAs(previous.maxLife) &&
                    value.radius.sameBitsAs(previous.radius)
            }
            ?: weaponNodes.mapToImmutableList { value ->
                WeaponNodeProjection(value.type, value.x, value.y, value.life, value.maxLife, value.radius)
            },
        weaponOrbitals = weaponOrbitals.reuseEmptyProjection(reusableCollections?.weaponOrbitals)
            ?: weaponOrbitals.reuseIfIdentical(
                identitySource?.weaponOrbitals,
                reusableCollections?.weaponOrbitals,
            )
            ?: weaponOrbitals.reuseProjectionIfContentEqual(
                reusableCollections?.weaponOrbitals,
            ) { value, previous ->
                value.index == previous.index &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.radius.sameBitsAs(previous.radius) &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY)
            }
            ?: weaponOrbitals.mapToImmutableList { value ->
                WeaponOrbitalProjection(value.index, value.x, value.y, value.radius, value.previousX, value.previousY)
            },
        choices = choices.reuseIfIdentical(identitySource?.choices, reusableCollections?.choices)
            ?: choices.reuseIfContentEqual(reusableCollections?.choices)
            ?: choices.toImmutableList(),
        choiceType = activeChoiceType,
        pendingRelicChoiceCount = pendingRelicChoices,
        directedChoice = directedReward != null,
        characterAbility = characterAbilityProjection(
            reusable = reusableCollections?.characterAbility,
            identityRuntime = identitySource?.characterRuntime,
        ),
        pointsOfInterest = pointsOfInterestProjection(
            reusable = reusableCollections?.pointsOfInterest,
            identitySource = identitySource,
        ),
        itemStacks = itemStacks.reuseIfStorageShared(
            identitySource?.itemStacks,
            reusableCollections?.itemStacks,
        ) ?: itemStacks.reuseIfContentEqual(reusableCollections?.itemStacks)
            ?: itemStacks.toImmutableList(),
        discoveredRelicMask = discoveredRelicMask,
        discoveredItemIds = discoveredItemIds.reuseIfStorageShared(
            identitySource?.discoveredItemIds,
            reusableCollections?.discoveredItemIds,
        ) ?: discoveredItemIds.reuseIfContentEqual(reusableCollections?.discoveredItemIds)
            ?: discoveredItemIds.toImmutableSet(),
        relicRanks = relicRanks.reuseIfStorageShared(
            identitySource?.relicRanks,
            reusableCollections?.relicRanks,
        ) ?: relicRanks.reuseIfContentEqual(reusableCollections?.relicRanks)
            ?: relicRanks.toImmutableList(),
        build = buildProjection(reusableCollections?.build, identitySource),
        discoveredReactionMask = discoveredReactionMask,
        discoveredPairingMask = discoveredPairingMask,
        coreRadius = coreRadius,
        abilityPower = abilityPower(),
        interpolationAlpha = renderInterpolationAlpha(),
        previousCoreX = previousCoreX,
        previousCoreY = previousCoreY,
        previousCameraX = previousCameraX,
        previousCameraY = previousCameraY,
        previousMorningstarX = previousMorningstarX,
        previousMorningstarY = previousMorningstarY,
        viewportDensity = viewportDensity,
        obstacles = obstacleProjection(reusableCollections?.obstacles, identitySource?.obstacleField),
        minimapRange = minimapRange(),
        weaponEntities = weaponEntityProjection(reusableCollections?.weaponEntities, identitySource?.weaponRuntime),
        // A scalar-input sibling shares the weapon runtime and changes no synergy input.
        weaponSynergy = reusableCollections?.weaponSynergy?.takeIf { identitySource != null && weaponRuntime === identitySource.weaponRuntime }
            ?: weaponSynergyProjection(reusableCollections?.weaponSynergy),
        rebirthRank = rebirthRank,
        volatileBursts = volatileBursts,
        maxLevel = content.tempo.maximumLevel,
        skillBar = skillBarProjection(reusableCollections?.skillBar),
        apex = apexProjection(reusableCollections?.apex),
        combatFields = combatFieldProjection(reusableCollections?.combatFields, identitySource?.combatFieldTable),
        coreState = coreStateProjection(reusableCollections?.coreState),
    )
}

/**
 * Control and mark bits of [enemy] ([EnemyControlBits]): stopped (never a boss), silenced, thrown, burning
 * with Blackfire, an Unmake pending, its live mark, the Void Anchor and an attractor's anchor.
 */
private fun MutableGameState.controlBits(enemy: Enemy): Int {
    var bits = 0
    if (enemy.disabledUntil > elapsed && !enemy.isBossClass) bits = bits or EnemyControlBits.STOPPED
    if (enemy.silencedUntil > elapsed) bits = bits or EnemyControlBits.SILENCED
    if (enemy.thrownUntil > elapsed) bits = bits or EnemyControlBits.THROWN
    if (enemy.status[StatusCell.BLACKFIRE_UNTIL] > elapsed) bits = bits or EnemyControlBits.BLACKFIRE
    if (enemy.status[StatusCell.UNMAKE_AT] > 0f) bits = bits or EnemyControlBits.UNMAKE_PENDING
    val mark = markOf(enemy)
    if (mark != null) bits = bits or ((mark.ordinal + 1) shl EnemyControlBits.MARK_SHIFT)
    if (voidAnchorId == enemy.id && voidAnchorUntil > elapsed) bits = bits or EnemyControlBits.VOID_ANCHOR
    if (anchorsAttractor(enemy.id)) bits = bits or EnemyControlBits.ATTRACTOR_ANCHOR
    if (enemy.isBossClass && apexActive() && apexStrikesLeft > 0) bits = bits or EnemyControlBits.APEX_STRIKE_TARGET
    return bits
}

/**
 * The Apex from its runtime cells: the shared [ApexProjection.LOCKED] below the level cap; an unchanged projection
 * keeps the previous instance. Strikes and bolts are those of the running activation (0 outside one).
 */
private fun MutableGameState.apexProjection(previous: ApexProjection?): ApexProjection {
    if (!apexUnlocked) return ApexProjection.LOCKED
    val runtime = skillRuntime
    val definition = apexDefinition
    val readyAt = runtime.apex(ApexCell.READY_AT)
    val refunded = runtime.apex(ApexCell.REFUNDED)
    val ready = elapsed >= readyAt
    // The cast's cooldown: what it set, before refunds took seconds off.
    val cooldown = readyAt + refunded - runtime.apex(ApexCell.LAST_CAST)
    val active = apexActive()
    val refundable = definition.cooldownSeconds - definition.minimumCooldownSeconds
    val projection = ApexProjection(
        unlocked = true,
        ready = ready,
        cooldown01 = if (ready || cooldown <= 0f) 0f else ((readyAt - elapsed) / cooldown).coerceIn(0f, 1f),
        active01 = if (active) remainingShare(runtime.apex(ApexCell.ACTIVE_UNTIL), definition.durationSeconds) else 0f,
        strikesLeft = if (active) apexStrikesLeft else 0,
        boltsLeft = if (active) runtime.apex(ApexCell.BOLTS_LEFT).toInt() else 0,
        held = runtime.apex(ApexCell.HELD).toInt(),
        refunded = refunded,
        dataCharge01 = if (refundable > 0f) (refunded / refundable).coerceIn(0f, 1f) else 0f,
    )
    return if (projection == previous) checkNotNull(previous) else projection
}

private fun MutableGameState.anchorsAttractor(enemyId: Int): Boolean {
    val table = combatFieldTable
    for (index in 0 until table.count) {
        if (table.kind(index) == CombatFieldKind.ATTRACTOR && table[index, FieldCell.ANCHOR_ID].toInt() == enemyId) return true
    }
    return false
}

/** The same table (a scalar-input sibling) or equal records keep the previous projection. */
private fun MutableGameState.combatFieldProjection(
    reusable: ImmutableList<CombatFieldProjection>?,
    identityTable: CombatFieldTable?,
): ImmutableList<CombatFieldProjection> {
    val table = combatFieldTable
    if (reusable != null) {
        if (table === identityTable) return reusable
        if (matches(reusable, table)) return reusable
    }
    if (table.count == 0) return immutableListOf()
    return immutableListOfSize(table.count) { index ->
        CombatFieldProjection(
            kind = CombatFieldVisual.entries[table.kind(index).ordinal],
            source = CombatFieldSource.entries[table.source(index).ordinal],
            x = table[index, FieldCell.X],
            y = table[index, FieldCell.Y],
            x2 = table[index, FieldCell.X2],
            y2 = table[index, FieldCell.Y2],
            radius = table[index, FieldCell.RADIUS],
            element = table[index, FieldCell.ELEMENT].toInt(),
            reaction = fieldReaction(table, index),
            createdAt = table[index, FieldCell.CREATED_AT],
            expiresAt = table[index, FieldCell.EXPIRES_AT],
            charge01 = fieldCharge01(table, index),
            anchorId = table[index, FieldCell.ANCHOR_ID].toInt(),
            followsCore = table.hasFlag(index, FieldFlags.FOLLOWS_CORE),
        )
    }
}

private fun fieldReaction(table: CombatFieldTable, index: Int): Int =
    if (table.source(index) == FieldSource.REACTION) table.owner(index) else -1

/**
 * The kind counter as a 0..1 fill: shots a Maw, a Time Bubble, Dead Air or a Frost Line ate of its budget, a Rot
 * Well's growths, a Resonator's echoes; 0 for the other kinds.
 */
private fun MutableGameState.fieldCharge01(table: CombatFieldTable, index: Int): Float {
    if (table.source(index) == FieldSource.SKILL) {
        val budget = when (table.kind(index)) {
            CombatFieldKind.FROST_LINE -> table[index, FieldCell.POWER]
            CombatFieldKind.ECHO_RING -> table[index, FieldCell.STACKS]
            else -> 0f
        }
        return if (budget <= 0f) 0f else (table[index, FieldCell.CHARGE] / budget).coerceIn(0f, 1f)
    }
    if (table.source(index) != FieldSource.REACTION) return 0f
    val keystone = table.hasFlag(index, FieldFlags.KEYSTONE)
    val budget = when (table.kind(index)) {
        CombatFieldKind.NULL_WELL -> verbValueOrZero(ReactionId.NULL_WELL, MAW_SHOTS_VALUE)
        CombatFieldKind.TIME_BUBBLE -> verbValueOrZero(ReactionId.STASIS, BUBBLE_SHOTS_VALUE)
        CombatFieldKind.HUSH -> if (keystone) DEAD_AIR_SHOTS.toFloat() else 0f
        CombatFieldKind.ROT_WELL -> if (keystone) 0f else verbValueOrZero(ReactionId.ROT_WELL, COMPOST_GROWTHS_VALUE)
        else -> 0f
    }
    return if (budget <= 0f) 0f else (table[index, FieldCell.CHARGE] / budget).coerceIn(0f, 1f)
}

private fun MutableGameState.verbValueOrZero(reaction: ReactionId, index: Int): Float =
    content.build.reaction(reaction).values.getOrNull(index) ?: 0f

private fun MutableGameState.matches(projection: ImmutableList<CombatFieldProjection>, table: CombatFieldTable): Boolean {
    if (projection.size != table.count) return false
    for (index in 0 until projection.size) if (!projection[index].matches(this, table, index)) return false
    return true
}

private fun CombatFieldProjection.matches(state: MutableGameState, table: CombatFieldTable, index: Int): Boolean =
    kind.ordinal == table.kind(index).ordinal &&
        source.ordinal == table.source(index).ordinal &&
        x.sameBitsAs(table[index, FieldCell.X]) &&
        y.sameBitsAs(table[index, FieldCell.Y]) &&
        x2.sameBitsAs(table[index, FieldCell.X2]) &&
        y2.sameBitsAs(table[index, FieldCell.Y2]) &&
        radius.sameBitsAs(table[index, FieldCell.RADIUS]) &&
        element == table[index, FieldCell.ELEMENT].toInt() &&
        reaction == fieldReaction(table, index) &&
        createdAt.sameBitsAs(table[index, FieldCell.CREATED_AT]) &&
        expiresAt.sameBitsAs(table[index, FieldCell.EXPIRES_AT]) &&
        charge01.sameBitsAs(state.fieldCharge01(table, index)) &&
        anchorId == table[index, FieldCell.ANCHOR_ID].toInt() &&
        followsCore == table.hasFlag(index, FieldFlags.FOLLOWS_CORE)

/**
 * Lingering Core state from committed scalars. The remaining shares of Ablaze, Frictionless and Arc Weave
 * are measured against the duration their reaction grants (its keystone form included); with no such value
 * a live state shows full. The shared [CoreStateProjection.NEUTRAL] stands for no state at all.
 */
private fun MutableGameState.coreStateProjection(previous: CoreStateProjection?): CoreStateProjection {
    val ablaze = remainingShare(ablazeUntil, if (hasVerb(ReactionId.WILDFIRE)) ablazeSeconds() else 0f)
    val frictionless = remainingShare(frictionlessUntil, verbValueOrZero(ReactionId.SUPERCONDUCT, FRICTIONLESS_SECONDS_VALUE))
    val weave = remainingShare(weaveUntil, if (hasVerb(ReactionId.RESONANT_ARC)) weaveSeconds() else 0f)
    val blastJumpReady = blastJumpUntil > elapsed
    val guard = if (guardUntil > elapsed) guardShards else 0
    val phased = phaseUntil > elapsed
    val primed = primedDashUntil > elapsed
    val stored = shroudStored()
    val coiled = dashCoiled()
    if (ablaze == 0f && frictionless == 0f && weave == 0f && !blastJumpReady && guard == 0 && !phased && !primed &&
        coreFieldMask == 0 && stored == 0 && !coiled
    ) return CoreStateProjection.NEUTRAL
    val projection = CoreStateProjection(
        ablaze01 = ablaze,
        frictionless01 = frictionless,
        blastJumpReady = blastJumpReady,
        weave01 = weave,
        guardShards = guard,
        shroudStored = stored,
        phased = phased,
        primedDash = primed,
        coiledDash = coiled,
        inFieldMask = coreFieldMask,
    )
    return if (projection == previous) previous else projection
}

/**
 * The three skill slots from the loadout and the skill runtime. The shared [SkillBarProjection.EMPTY] stands for
 * no skill drafted; an unchanged bar keeps the previous instance.
 */
private fun MutableGameState.skillBarProjection(previous: SkillBarProjection?): SkillBarProjection {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) return SkillBarProjection.EMPTY
    var changed = previous == null || previous === SkillBarProjection.EMPTY
    val first = skillSlotProjection(0)
    val second = skillSlotProjection(1)
    val third = skillSlotProjection(2)
    if (!changed) {
        val slots = checkNotNull(previous).slots
        changed = slots[0] != first || slots[1] != second || slots[2] != third
    }
    if (!changed) return checkNotNull(previous)
    return SkillBarProjection(kinetickk.foundation.collections.immutableListOf(first, second, third))
}

private fun MutableGameState.skillSlotProjection(slot: Int): SkillSlotProjection {
    val id = skillLoadout.skill(slot) ?: return SkillSlotProjection.EMPTY
    val runtime = skillRuntime
    val values = content.skill(id).rank(skillLoadout.rank(slot)).values
    val readyAt = runtime[slot, SkillCell.READY_AT]
    val cooldown = runtime[slot, SkillCell.LAST_COOLDOWN]
    val ready = elapsed >= readyAt
    val activeSeconds = when (id) {
        SkillId.MASS_DRIVER, SkillId.NULL_SHROUD, SkillId.DUAL_INFUSION -> values[0]
        SkillId.SLINGSHOT -> values[SLINGSHOT_WINDOW_VALUE]
        else -> 0f
    }
    val pendingAt = runtime[slot, SkillCell.PENDING_AT]
    val pendingSeconds = when (id) {
        SkillId.METEOR -> values[METEOR_DELAY_VALUE]
        SkillId.VOID_STEP -> values[VOID_STEP_RIFT_DELAY_VALUE]
        else -> 0f
    }
    val pending = id != SkillId.NULL_SHROUD && pendingAt >= 0f && pendingAt > elapsed
    return SkillSlotProjection(
        skill = id.ordinal,
        rank = skillLoadout.rank(slot),
        cooldown01 = if (ready || cooldown <= 0f) 0f else ((readyAt - elapsed) / cooldown).coerceIn(0f, 1f),
        ready = ready,
        charges = if (ready) 1 else 0,
        maxCharges = 1,
        active01 = if (activeSeconds > 0f) remainingShare(runtime[slot, SkillCell.ACTIVE_UNTIL], activeSeconds) else 0f,
        pending01 = if (pending && pendingSeconds > 0f) ((pendingAt - elapsed) / pendingSeconds).coerceIn(0f, 1f) else 0f,
        anchorX = if (pending) runtime[slot, SkillCell.ANCHOR_X] else 0f,
        anchorY = if (pending) runtime[slot, SkillCell.ANCHOR_Y] else 0f,
        refunded = runtime[slot, SkillCell.REFUNDED],
        refusal = runtime[slot, SkillCell.REFUSAL].toInt(),
        refusedAt = runtime[slot, SkillCell.REFUSED_AT],
    )
}

/** Indices of the skill values the skill bar reads (the content order of `defaultSkillDefinitions`). */
private const val SLINGSHOT_WINDOW_VALUE: Int = 3
private const val METEOR_DELAY_VALUE: Int = 4
private const val VOID_STEP_RIFT_DELAY_VALUE: Int = 5

private fun MutableGameState.remainingShare(until: Float, seconds: Float): Float = when {
    until <= elapsed -> 0f
    seconds <= 0f -> 1f
    else -> ((until - elapsed) / seconds).coerceIn(0f, 1f)
}

/** Relic gauges change during combat; the compiled build's immutable collections do not. */
private fun MutableGameState.buildProjection(
    previous: BuildProjection?,
    identitySource: MutableGameState?,
): BuildProjection {
    if (previous == null || identitySource == null || build !== identitySource.build ||
        discoveredPairingMask != identitySource.discoveredPairingMask ||
        !itemStacks.sharesStorageWith(identitySource.itemStacks) ||
        !rebornItems.sharesStorageWith(identitySource.rebornItems)
    ) return buildProjection()

    val severed = severedRelics.reuseProjectionIfContentEqual(previous.severed) { value, projection ->
        value.id == projection.id && kotlin.math.max(0f, value.until - elapsed).sameBitsAs(projection.remaining)
    } ?: severedRelics.mapToImmutableList { value ->
        SeveredRelicProjection(value.id, kotlin.math.max(0f, value.until - elapsed))
    }
    val meter = stasisMeter / MutableGameState.STASIS_SECONDS
    val invulnerable = invulnerableUntil > elapsed
    val burnout = overdriveBurnoutUntil > elapsed
    if (previous.severed === severed && previous.stormCharge.sameBitsAs(stormCharge) &&
        previous.stasisMeter.sameBitsAs(meter) && previous.stasisBank == stasisBank &&
        previous.ouroborosSpent == ouroborosSpent && previous.invulnerable == invulnerable &&
        previous.overdriveBurnout == burnout
    ) return previous

    return previous.copy(
        severed = severed,
        stormCharge = stormCharge,
        stasisMeter = meter,
        stasisBank = stasisBank,
        ouroborosSpent = ouroborosSpent,
        invulnerable = invulnerable,
        overdriveBurnout = burnout,
    )
}

/** The same runtime (a scalar-input sibling) or equal entities keep the previous projection. */
private fun MutableGameState.weaponEntityProjection(
    reusable: ImmutableList<WeaponEntityProjection>?,
    identityRuntime: WeaponRuntime?,
): ImmutableList<WeaponEntityProjection> {
    val entities = weaponRuntime.entities
    if (reusable != null) {
        if (weaponRuntime === identityRuntime) return reusable
        if (entities.isEmpty() && reusable.isEmpty()) return reusable
        if (entities.reuseProjectionIfContentEqual(reusable) { value, previous -> previous.matches(value) } != null) return reusable
    }
    if (entities.isEmpty()) return immutableListOf()
    return entities.mapToImmutableList { value ->
        WeaponEntityProjection(
            kind = value.kind,
            x = value.x,
            y = value.y,
            previousX = value.previousX,
            previousY = value.previousY,
            endX = value.endX,
            endY = value.endY,
            previousEndX = value.previousEndX,
            previousEndY = value.previousEndY,
            angle = value.angle,
            previousAngle = value.previousAngle,
            radius = value.radius,
            extent = value.extent,
            life = value.life,
            maxLife = value.maxLife,
            phase = value.phase,
        )
    }
}

private fun WeaponEntityProjection.matches(entity: WeaponEntity): Boolean =
    kind == entity.kind &&
        x.sameBitsAs(entity.x) &&
        y.sameBitsAs(entity.y) &&
        previousX.sameBitsAs(entity.previousX) &&
        previousY.sameBitsAs(entity.previousY) &&
        endX.sameBitsAs(entity.endX) &&
        endY.sameBitsAs(entity.endY) &&
        previousEndX.sameBitsAs(entity.previousEndX) &&
        previousEndY.sameBitsAs(entity.previousEndY) &&
        angle.sameBitsAs(entity.angle) &&
        previousAngle.sameBitsAs(entity.previousAngle) &&
        radius.sameBitsAs(entity.radius) &&
        extent.sameBitsAs(entity.extent) &&
        life.sameBitsAs(entity.life) &&
        maxLife.sameBitsAs(entity.maxLife) &&
        phase == entity.phase

/** The window changes only when the Core changes chunk (or a rock shatters); otherwise it is reused. */
private fun MutableGameState.obstacleProjection(
    reusable: ImmutableList<ObstacleProjection>?,
    identityField: ObstacleField?,
): ImmutableList<ObstacleProjection> {
    val field = obstacleField
    if (reusable != null) {
        if (field === identityField) return reusable
        if (field.obstacles.reuseProjectionIfContentEqual(reusable) { value, previous -> previous.matches(value) } != null) {
            return reusable
        }
    }
    return field.obstacles.mapToImmutableList { value ->
        ObstacleProjection(value.id, value.kind, value.x, value.y, value.radius, value.shapeSeed)
    }
}

private fun ObstacleProjection.matches(obstacle: Obstacle): Boolean =
    id == obstacle.id &&
        kind == obstacle.kind &&
        x.sameBitsAs(obstacle.x) &&
        y.sameBitsAs(obstacle.y) &&
        radius.sameBitsAs(obstacle.radius) &&
        shapeSeed == obstacle.shapeSeed

/**
 * How far the accumulated real time has advanced past the last simulated state, in fixed steps.
 * Only a running simulation accumulates time; every other phase presents its exact state, and so
 * does a run (re)entering RUNNING until its first step makes the previous positions current.
 */
internal fun MutableGameState.renderInterpolationAlpha(): Float =
    if (phase == GamePhase.RUNNING && interpolationPrimed) {
        (accumulator / MutableGameState.FIXED_STEP).coerceIn(0f, 1f)
    } else {
        1f
    }

/** Run totals change rarely; an unchanged set keeps the previous immutable projection. */
private fun MutableGameState.runStatisticsProjection(reusable: RunStatistics?): RunStatistics {
    if (reusable != null &&
        reusable.damageDealt.toRawBits() == damageDealt.toRawBits() &&
        reusable.damageTaken.toRawBits() == damageTaken.toRawBits() &&
        reusable.damageAbsorbed.toRawBits() == damageAbsorbed.toRawBits() &&
        reusable.dataCollected == dataCollected &&
        reusable.pickupsCollected == pickupsCollected &&
        reusable.keysCollected == keysCollected &&
        reusable.eliteKills == eliteKills &&
        reusable.bestCombo == bestCombo
    ) {
        return reusable
    }
    return RunStatistics(
        damageDealt = damageDealt,
        damageTaken = damageTaken,
        damageAbsorbed = damageAbsorbed,
        dataCollected = dataCollected,
        pickupsCollected = pickupsCollected,
        keysCollected = keysCollected,
        eliteKills = eliteKills,
        bestCombo = bestCombo,
    )
}

/** The same runtime (a scalar-input sibling) or equal content keeps the previous projection. */
private fun MutableGameState.characterAbilityProjection(
    reusable: CharacterAbilityProjection?,
    identityRuntime: CharacterRuntime?,
): CharacterAbilityProjection {
    val runtime = characterRuntime
    if (reusable != null && (runtime === identityRuntime || reusable.matches(this, runtime))) return reusable
    val shape = coreShape
    return CharacterAbilityProjection(
        charge = runtime.charge,
        ringRadius = runtime.haloRadius,
        parryWindow = runtime.parryWindow,
        lattice = if (shape == CoreShape.TESSERACT) {
            immutableListOfSize(runtime.latticeCount) { CharacterLatticePoint(runtime.latticeX(it), runtime.latticeY(it)) }
        } else {
            immutableListOf()
        },
        facets = if (shape == CoreShape.PRISM) runtime.facets else 0,
        facetProgress = facetProgress(runtime),
        edge = if (shape == CoreShape.DIAMOND) runtime.edge else 0,
        stellarMass = if (shape == CoreShape.STAR) runtime.stellarMass else 0,
        novaFlash = runtime.novaFlash,
        haloBurstFlash = runtime.haloBurstFlash,
        polarity = if (shape == CoreShape.DIPOLE) runtime.polarity.ordinal else 0,
        polarityFlash = runtime.polarityFlash,
        beatPeriod = if (shape == CoreShape.PENTAGON) BEAT_PERIOD else 0f,
        beatWindow = if (shape == CoreShape.PENTAGON) BEAT_WINDOW else 0f,
        beatStreak = if (shape == CoreShape.PENTAGON) runtime.beatStreak else 0,
        blinkCharges = if (shape == CoreShape.CRESCENT) runtime.blinkCharges else 0,
        blinkRecharge = if (shape == CoreShape.CRESCENT) runtime.blinkRecharge / blinkRechargeSeconds() else 0f,
        scars = if (shape == CoreShape.CRESCENT) {
            immutableListOfSize(runtime.scarCount) { index -> scarProjection(runtime, index) }
        } else {
            immutableListOf()
        },
        drones = if (shape == CoreShape.HEXAGON) {
            immutableListOfSize(runtime.droneCount) { index ->
                CharacterDroneProjection(
                    x = runtime.droneValue(index, CharacterRuntime.DRONE_X),
                    y = runtime.droneValue(index, CharacterRuntime.DRONE_Y),
                    previousX = runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_X),
                    previousY = runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_Y),
                    darting = runtime.droneValue(index, CharacterRuntime.DRONE_DART) > 0f,
                )
            }
        } else {
            immutableListOf()
        },
        ghostActive = ghostActive,
        ghostX = if (ghostActive) ghostX else 0f,
        ghostY = if (ghostActive) ghostY else 0f,
        previousGhostX = if (ghostActive) previousGhostX else 0f,
        previousGhostY = if (ghostActive) previousGhostY else 0f,
        secondGhostActive = secondGhostActive,
        secondGhostX = if (secondGhostActive) secondGhostX else 0f,
        secondGhostY = if (secondGhostActive) secondGhostY else 0f,
    )
}

private fun MutableGameState.facetProgress(runtime: CharacterRuntime): Float =
    if (coreShape == CoreShape.PRISM && runtime.facets < CharacterRuntime.MAX_FACETS) {
        (runtime.facetTimer / facetRegrowthSeconds()).coerceIn(0f, 1f)
    } else {
        0f
    }

private fun MutableGameState.scarProjection(runtime: CharacterRuntime, index: Int): CharacterScarProjection =
    CharacterScarProjection(
        x0 = runtime.scarValue(index, CharacterRuntime.SCAR_X0),
        y0 = runtime.scarValue(index, CharacterRuntime.SCAR_Y0),
        x1 = runtime.scarValue(index, CharacterRuntime.SCAR_X1),
        y1 = runtime.scarValue(index, CharacterRuntime.SCAR_Y1),
        fuse = scarFuse(runtime, index),
        fade = scarFade(runtime, index),
    )

/** Fuse progress of scar [index]: 0 when laid, 1 from its detonation on. */
private fun MutableGameState.scarFuse(runtime: CharacterRuntime, index: Int): Float {
    if (runtime.scarValue(index, CharacterRuntime.SCAR_AGE) >= 0f) return 1f
    return (1f - (runtime.scarValue(index, CharacterRuntime.SCAR_DETONATE_AT) - elapsed) / SCAR_FUSE_SECONDS).coerceIn(0f, 1f)
}

/** Seconds since scar [index] detonated (0 before). */
private fun scarFade(runtime: CharacterRuntime, index: Int): Float =
    runtime.scarValue(index, CharacterRuntime.SCAR_AGE).coerceAtLeast(0f)

/** Compares every projected field bit for bit against what [characterAbilityProjection] would build now. */
private fun CharacterAbilityProjection.matches(state: MutableGameState, runtime: CharacterRuntime): Boolean {
    val shape = state.coreShape
    if (!charge.sameBitsAs(runtime.charge) || !ringRadius.sameBitsAs(runtime.haloRadius) ||
        !parryWindow.sameBitsAs(runtime.parryWindow) ||
        facets != (if (shape == CoreShape.PRISM) runtime.facets else 0) ||
        !facetProgress.sameBitsAs(state.facetProgress(runtime)) ||
        edge != (if (shape == CoreShape.DIAMOND) runtime.edge else 0) ||
        stellarMass != (if (shape == CoreShape.STAR) runtime.stellarMass else 0) ||
        !novaFlash.sameBitsAs(runtime.novaFlash) || !haloBurstFlash.sameBitsAs(runtime.haloBurstFlash) ||
        polarity != (if (shape == CoreShape.DIPOLE) runtime.polarity.ordinal else 0) ||
        !polarityFlash.sameBitsAs(runtime.polarityFlash) ||
        !beatPeriod.sameBitsAs(if (shape == CoreShape.PENTAGON) BEAT_PERIOD else 0f) ||
        !beatWindow.sameBitsAs(if (shape == CoreShape.PENTAGON) BEAT_WINDOW else 0f) ||
        beatStreak != (if (shape == CoreShape.PENTAGON) runtime.beatStreak else 0) ||
        blinkCharges != (if (shape == CoreShape.CRESCENT) runtime.blinkCharges else 0) ||
        !blinkRecharge.sameBitsAs(if (shape == CoreShape.CRESCENT) runtime.blinkRecharge / state.blinkRechargeSeconds() else 0f) ||
        ghostActive != state.ghostActive || secondGhostActive != state.secondGhostActive
    ) {
        return false
    }
    if (ghostActive && (!ghostX.sameBitsAs(state.ghostX) || !ghostY.sameBitsAs(state.ghostY) ||
            !previousGhostX.sameBitsAs(state.previousGhostX) || !previousGhostY.sameBitsAs(state.previousGhostY))
    ) {
        return false
    }
    if (secondGhostActive && (!secondGhostX.sameBitsAs(state.secondGhostX) || !secondGhostY.sameBitsAs(state.secondGhostY))) {
        return false
    }
    val latticeCount = if (shape == CoreShape.TESSERACT) runtime.latticeCount else 0
    if (lattice.size != latticeCount) return false
    for (index in lattice.indices) {
        val point = lattice[index]
        if (!point.x.sameBitsAs(runtime.latticeX(index)) || !point.y.sameBitsAs(runtime.latticeY(index))) return false
    }
    val scarCount = if (shape == CoreShape.CRESCENT) runtime.scarCount else 0
    if (scars.size != scarCount) return false
    for (index in scars.indices) {
        val scar = scars[index]
        // Field by field from the runtime: the check builds no projection.
        if (!scar.x0.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_X0)) ||
            !scar.y0.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_Y0)) ||
            !scar.x1.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_X1)) ||
            !scar.y1.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_Y1)) ||
            !scar.fuse.sameBitsAs(state.scarFuse(runtime, index)) || !scar.fade.sameBitsAs(scarFade(runtime, index))
        ) {
            return false
        }
    }
    val droneCount = if (shape == CoreShape.HEXAGON) runtime.droneCount else 0
    if (drones.size != droneCount) return false
    for (index in drones.indices) {
        val drone = drones[index]
        if (!drone.x.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_X)) ||
            !drone.y.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_Y)) ||
            !drone.previousX.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_X)) ||
            !drone.previousY.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_Y)) ||
            drone.darting != (runtime.droneValue(index, CharacterRuntime.DRONE_DART) > 0f)
        ) {
            return false
        }
    }
    return true
}

/**
 * Points of interest change a few times per second at most: the same list at the same time, or
 * projections that already carry every derived value, keep the previous immutable list.
 */
private fun MutableGameState.pointsOfInterestProjection(
    reusable: ImmutableList<PointOfInterestProjection>?,
    identitySource: MutableGameState?,
): ImmutableList<PointOfInterestProjection> {
    if (reusable != null) {
        if (pointsOfInterest === identitySource?.pointsOfInterest && elapsed.sameBitsAs(identitySource.elapsed)) return reusable
        if (reusable.size == pointsOfInterest.size) {
            var same = true
            for (index in pointsOfInterest.indices) {
                if (!pointProjectionMatches(reusable[index], pointsOfInterest[index])) {
                    same = false
                    break
                }
            }
            if (same) return reusable
        }
    }
    return pointsOfInterest.mapToImmutableList { point ->
        PointOfInterestProjection(point.kind, content.pointsOfInterest.definition(point.kind).name,
            point.x, point.y, point.active, pointRemaining(point), point.nextBeacon, pointProgress(point),
            point.defenderIds, point.warningRemaining, point.volleyAngle)
    }
}

private fun MutableGameState.pointRemaining(point: PointOfInterestState): Float =
    if (point.active) point.remaining else (point.expiresAt - elapsed).coerceAtLeast(0f)

private fun MutableGameState.pointProgress(point: PointOfInterestState): Float = when (point.kind) {
    kinetickk.ball.content.api.PointOfInterestKind.RESONANT_CIRCUIT -> (point.nextBeacon - 1) / 3f
    kinetickk.ball.content.api.PointOfInterestKind.SEALED_ANOMALY -> point.defeatedDefenders / 3f
    kinetickk.ball.content.api.PointOfInterestKind.COLLAPSING_ORBIT ->
        point.orbitSeconds / content.pointsOfInterest.orbitRequiredSeconds
}

private fun MutableGameState.pointProjectionMatches(
    projection: PointOfInterestProjection,
    point: PointOfInterestState,
): Boolean =
    projection.kind == point.kind &&
        projection.x.sameBitsAs(point.x) &&
        projection.y.sameBitsAs(point.y) &&
        projection.active == point.active &&
        projection.remaining.sameBitsAs(pointRemaining(point)) &&
        projection.nextBeacon == point.nextBeacon &&
        projection.progress.sameBitsAs(pointProgress(point)) &&
        (projection.defenderIds === point.defenderIds || projection.defenderIds == point.defenderIds) &&
        projection.warningRemaining.sameBitsAs(point.warningRemaining) &&
        projection.volleyAngle.sameBitsAs(point.volleyAngle)

private fun <Source : Any, Projection> Source.reuseIfIdentical(
    previousSource: Source?,
    previousProjection: Projection?,
): Projection? = previousProjection?.takeIf { this === previousSource }

private fun <Source, Projection> List<Source>.reuseEmptyProjection(
    previous: ImmutableList<Projection>?,
): ImmutableList<Projection>? =
    previous?.takeIf { isEmpty() && it.isEmpty() }

private inline fun <Source, Projection> List<Source>.reuseProjectionIfContentEqual(
    previous: ImmutableList<Projection>?,
    sameProjection: (Source, Projection) -> Boolean,
): ImmutableList<Projection>? {
    if (previous == null || size != previous.size) return null
    var index = 0
    while (index < size) {
        if (!sameProjection(this[index], previous[index])) return null
        index++
    }
    return previous
}

private fun Float.sameBitsAs(other: Float): Boolean = toRawBits() == other.toRawBits()

private fun <Element> List<Element>.reuseIfContentEqual(
    previous: ImmutableList<Element>?,
): ImmutableList<Element>? {
    if (previous == null || size != previous.size) return null
    var index = 0
    while (index < size) {
        if (this[index] != previous[index]) return null
        index++
    }
    return previous
}

private fun CopyOnWriteIntArray.reuseIfStorageShared(
    previousSource: CopyOnWriteIntArray?,
    previousProjection: ImmutableList<Int>?,
): ImmutableList<Int>? =
    previousProjection?.takeIf { previousSource != null && sharesStorageWith(previousSource) }

private fun <Element> CopyOnWriteMutableSet<Element>.reuseIfStorageShared(
    previousSource: CopyOnWriteMutableSet<Element>?,
    previousProjection: ImmutableSet<Element>?,
): ImmutableSet<Element>? =
    previousProjection?.takeIf { previousSource != null && sharesStorageWith(previousSource) }
