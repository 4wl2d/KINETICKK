// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.*
import kotlin.test.*

/** Controlled encounters compare behavior and measured outcomes, not seeded-state hashes.
 * Build resources are held equal. These scenarios do not prove acquisition timing or a full-run win.
 */
class ComparativeBuildScenarioTest {
    @Test
    fun economicItemsImproveEarnedProgressAndDefenseExtendsSurvivalUnderEqualDamage() {
        fun fresh() = MutableGameState(canonicalGameplayContent, seed = 81_006).apply {
            startRun()
            settings = settings.copy(simulationSpeed = 1f)
        }
        fun MutableGameState.addSix(effect: ItemEffect) {
            val item = content.items.first { it.primary.effect == effect && it.maxStacks >= 6 }
            repeat(6) { acquireItem(item.id) }
        }
        val baseline = fresh()
        val economic = fresh().apply { addSix(ItemEffect.DATA_GAIN); addSix(ItemEffect.MATTER_GAIN) }
        listOf(baseline, economic).forEach { game ->
            // The same eighty defeated drifters produce the same base rewards and pickup locations.
            repeat(80) { game.killEnemyForTesting(EnemyType.DRIFTER, game.coreX, game.coreY) }
            game.resolvePickupCollection()
        }
        assertEquals(80, baseline.kills)
        assertEquals(baseline.kills, economic.kills)
        assertTrue(economic.level > baseline.level)
        assertTrue(economic.runMatter > baseline.runMatter)
        assertEquals(baseline.content.tempo.dataRequiredForLevel(baseline.level), baseline.nextLevelData)
        assertEquals(economic.content.tempo.dataRequiredForLevel(economic.level), economic.nextLevelData)

        fun surviveHits(defended: Boolean): Pair<Int, Float> {
            val game = fresh().apply {
                if (defended) { addSix(ItemEffect.SHIELD_CAPACITY); addSix(ItemEffect.DAMAGE_REDUCTION) }
                enemies.clear(); spawnClock = Float.MAX_VALUE; nextEliteAt = Float.MAX_VALUE
                screenWidth = 1920f; screenHeight = 1080f
            }
            var survived = 0
            repeat(10) {
                if (game.phase != GamePhase.RUNNING) return@repeat
                game.takeDamage(12f)
                if (game.hp > 0f) survived++
                repeat(15) {
                    game.updatePointer(game.coreX + 350f - game.cameraX + game.screenWidth / 2f,
                        game.coreY - game.cameraY + game.screenHeight / 2f)
                    game.update(1f / 60f)
                    game.takeVisualFxCues(); game.takeSoundCues()
                }
            }
            return survived to game.hp
        }
        val unprotected = surviveHits(false)
        val defended = surviveHits(true)
        assertTrue(defended.first > unprotected.first)
        assertTrue(defended.second > 0f)
        println("ROLE_EFFICIENCY equalKills=80 baselineLevel=${baseline.level} economicLevel=${economic.level} baselineMatter=${baseline.runMatter} economicMatter=${economic.runMatter} baselineHits=${unprotected.first} defendedHits=${defended.first}")
    }

    @Test
    fun allSeventyTwoCharacterWeaponPairsExecuteRealInputCombatWithFiniteState() {
        for (shape in CoreShape.entries) for (weapon in WeaponId.entries) {
            val result = runEncounter(shape, BuildDirection.KINETIC, ProfileCondition.FRESH,
                Encounter.CROWD, weaponOverride = weapon, secondsOverride = 5)
            assertTrue(result.damage > 0f, "$shape/$weapon produced no combat damage in its crowd fixture")
            assertTrue(result.damage.isFinite() && result.integrity.isFinite(), "$shape/$weapon")
            assertTrue(result.maximumEnemies <= canonicalGameplayContent.rebirth.maxActiveEnemies)
        }
    }

    @Test
    fun sixFormsHaveThreeMeasuredBuildDirectionsAcrossEncounterAndProfileConditions() {
        val results = mutableListOf<EncounterResult>()
        for (profile in ProfileCondition.entries) for (shape in CoreShape.entries) for (build in BuildDirection.entries) {
            for (encounter in Encounter.entries) {
                val result = runEncounter(shape, build, profile, encounter)
                results += result
                assertTrue(result.damage.isFinite() && result.damage >= 0f, "$shape/$build/$profile/$encounter")
                assertTrue(result.integrity.isFinite(), "$shape/$build/$profile/$encounter")
                assertTrue(result.maximumEnemies <= canonicalGameplayContent.rebirth.maxActiveEnemies)
                if (encounter == Encounter.BOSS) assertTrue(result.seconds <= 180f)
            }
        }
        val report = mutableListOf<String>()
        report += "BALANCE_COLUMNS profile,shape,build,crowdKills/12,eliteDamagePercent,mobileKills/6,bossDamagePercent,bossSurvivalSeconds,matter,dataLevels,endReason,bossIntegrityPercent"
        for (profile in ProfileCondition.entries) for (shape in CoreShape.entries) for (build in BuildDirection.entries) {
            val rows = results.filter { it.profile == profile && it.shape == shape && it.build == build }
            fun row(encounter: Encounter) = rows.single { it.encounter == encounter }
            val crowd = row(Encounter.CROWD)
            val elite = row(Encounter.ELITE)
            val mobile = row(Encounter.MOBILE)
            val boss = row(Encounter.BOSS)
            report += "BALANCE ${profile.name},${shape.name},${build.name},${crowd.targetKills},${elite.percent.toInt()},${mobile.targetKills},${boss.percent.toInt()},${boss.seconds.toInt()},${rows.sumOf { it.matter }},${rows.sumOf { it.levels }},${boss.endReason},${(boss.integrity * 100f).toInt()}"
        }
        for (profile in ProfileCondition.entries) for (shape in CoreShape.entries) {
            val viable = BuildDirection.entries.filter { build ->
                val rows = results.filter { it.profile == profile && it.shape == shape && it.build == build }
                rows.all { it.percent >= 99.9f && it.integrity > 0f }
            }
            report += "COVERAGE ${profile.name},${shape.name},${viable.size},${viable.joinToString { it.name }}"
        }
        report.forEach(::println)
        java.io.File(System.getProperty("java.io.tmpdir"), "kinetickk-comparative-results.txt").writeText(report.joinToString("\n"))
        for (profile in ProfileCondition.entries) for (shape in CoreShape.entries) {
            val viable = BuildDirection.entries.count { build ->
                results.filter { it.profile == profile && it.shape == shape && it.build == build }
                    .all { it.percent >= 99.9f && it.integrity > 0f }
            }
            assertTrue(viable >= 3, "$profile/$shape has $viable viable combat directions; need at least three")
        }
        // Repeated equal inputs must yield the same measured combat result.
        assertEquals(runEncounter(CoreShape.ORB, BuildDirection.KINETIC, ProfileCondition.FRESH, Encounter.CROWD),
            runEncounter(CoreShape.ORB, BuildDirection.KINETIC, ProfileCondition.FRESH, Encounter.CROWD))
        assertEquals(CoreShape.entries.size * BuildDirection.entries.size * ProfileCondition.entries.size * Encounter.entries.size, results.size)
    }
}

private enum class ProfileCondition(val labRank: Int, val rebirth: Int) { FRESH(0, 0), LAB(6, 0), REBIRTH(6, 3) }
private enum class BuildDirection(val weapon: WeaponId, val relics: List<RelicId>, val effects: List<ItemEffect>) {
    KINETIC(WeaponId.MORNINGSTAR,
        listOf(RelicId.MASS_ECHO, RelicId.ORBITAL_NAIL, RelicId.SLIPSTREAM_RELAY, RelicId.BORROWED_MOMENT),
        listOf(ItemEffect.MASS, ItemEffect.IMPACT_DAMAGE, ItemEffect.DAMAGE_REDUCTION)),
    DISCHARGE(WeaponId.PRISM_RELAY,
        listOf(RelicId.VOLTAIC_FILAMENT, RelicId.STATIC_CHORUS, RelicId.GLASS_WITNESS, RelicId.BORROWED_MOMENT),
        listOf(ItemEffect.WEAPON_POWER, ItemEffect.ATTACK_SPEED, ItemEffect.CRIT_CHANCE)),
    ORBITAL(WeaponId.ION_SWARM,
        listOf(RelicId.ECHO_CHAMBER, RelicId.PALIMPSEST_ROUND, RelicId.MASS_ECHO, RelicId.ORBITAL_NAIL),
        listOf(ItemEffect.WEAPON_POWER, ItemEffect.ATTACK_SPEED, ItemEffect.SHIELD_CAPACITY)),
    BLADES(WeaponId.RIFT_BLADES,
        listOf(RelicId.GHOST_VECTOR, RelicId.SLIPSTREAM_RELAY, RelicId.STATIC_CHORUS, RelicId.ION_DEBT),
        listOf(ItemEffect.WEAPON_POWER, ItemEffect.MASS, ItemEffect.COOLING)),
    SALVAGE(WeaponId.FLUX_WAKE,
        listOf(RelicId.GHOST_VECTOR, RelicId.SLIPSTREAM_RELAY, RelicId.ECHO_CHAMBER, RelicId.PALIMPSEST_ROUND),
        listOf(ItemEffect.DATA_GAIN, ItemEffect.MATTER_GAIN, ItemEffect.PICKUP_RADIUS)),
}
private enum class Encounter(val seconds: Int) { CROWD(25), ELITE(45), MOBILE(30), BOSS(180) }
private data class EncounterResult(
    val shape: CoreShape, val build: BuildDirection, val profile: ProfileCondition, val encounter: Encounter,
    val damage: Float, val percent: Float, val targetKills: Int, val integrity: Float, val seconds: Float,
    val matter: Long, val levels: Int, val maximumEnemies: Int, val endReason: String,
)

private fun runEncounter(
    shape: CoreShape, build: BuildDirection, profile: ProfileCondition, encounter: Encounter,
    weaponOverride: WeaponId = build.weapon, secondsOverride: Int = encounter.seconds,
): EncounterResult {
    val game = MutableGameState(canonicalGameplayContent, seed = 73_199, initialRebirthLevel = profile.rebirth).apply {
        coreShape = shape
        startingWeapon = weaponOverride
        metaRanks.fill(profile.labRank)
        startRun()
        settings = settings.copy(simulationSpeed = 1f)
        if (encounter == Encounter.BOSS) {
            elapsed = content.tempo.bossAtSeconds
            runGrace = 0f
        }
        screenWidth = 1920f
        screenHeight = 1080f
        level = 18
        nextLevelData = content.tempo.dataRequiredForLevel(level)
        weaponLevel = 10
        // Equal twenty-item budget, four relics at rank three; no named special pair.
        listOf(ItemEffect.MAX_INTEGRITY, ItemEffect.MAX_INTEGRITY, ItemEffect.REGEN, ItemEffect.COOLING).forEach { effect ->
            val item = content.items.first { it.primary.effect == effect }
            repeat(2) { acquireItem(item.id) }
        }
        build.effects.forEach { effect ->
            val item = content.items.first { it.primary.effect == effect }
            repeat(4) { acquireItem(item.id) }
        }
        build.relics.forEach { relic -> repeat(3) { acquireRelic(relic) } }
        enemies.clear()
        spawnClock = Float.MAX_VALUE
        nextEliteAt = Float.MAX_VALUE
        bossSpawned = true
        nextPointOfferIndex = ((content.pointsOfInterest.lastOfferAt - content.pointsOfInterest.firstOfferAt) / content.pointsOfInterest.offerInterval).toInt() + 1
        coreX = 220f
        previousCoreX = coreX
        velocityY = 420f
        cameraX = coreX
        cameraY = coreY
        pointerX = screenWidth / 2f + 360f
        pointerY = screenHeight / 2f
        previousSingularityX = coreX + 360f
        previousSingularityY = coreY
    }
    assertFalse(game.content.synergies.filter { it.requiredRelics.isNotEmpty() }.any { definition ->
        definition.requiredRelics.all { game.relicRank(it) > 0 }
    }, "Fixture accidentally depends on a special pair")
    val startedAt = game.elapsed
    if (encounter == Encounter.BOSS) assertEquals(game.content.tempo.bossAtSeconds, startedAt)
    val count = when (encounter) { Encounter.CROWD -> 12; Encounter.MOBILE -> 6; else -> 1 }
    val targets = (0 until count).map { index ->
        val angle = index * TAU / count
        val type = when (encounter) { Encounter.CROWD -> EnemyType.DRIFTER; Encounter.ELITE -> EnemyType.ELITE; Encounter.MOBILE -> EnemyType.INTERCEPTOR; Encounter.BOSS -> EnemyType.ARCHITECT }
        val hp = when (encounter) { Encounter.CROWD -> 100f; Encounter.ELITE -> 1100f; Encounter.MOBILE -> 195f; Encounter.BOSS -> 5400f }
        game.addEnemyForTesting(cos(angle) * if (count > 1) 170f else 0f, sin(angle) * if (count > 1) 170f else 0f,
            hp = game.rebirthProfile.enemyHealth(hp), radius = if (encounter == Encounter.BOSS) 74f else if (encounter == Encounter.ELITE) 38f else 18f, type = type).apply {
                if (encounter == Encounter.BOSS) actionTimer = 1.2f
            }
    }
    var heading = 0f
    var maxEnemies = count
    repeat(secondsOverride * 60) { frame ->
        if (game.phase != GamePhase.RUNNING) {
            if (game.phase == GamePhase.CHOICE && game.choices.isNotEmpty()) game.choose(0) else return@repeat
        }
        val t = frame / 60f
        // A moving desired orbit, damping and a bounded cursor slew produce real core movement.
        val liveTargets = targets.filter { !it.dead && it.hp > 0f }
        val centerX = if (liveTargets.isEmpty()) 0f else liveTargets.sumOf { it.x.toDouble() }.toFloat() / liveTargets.size
        val centerY = if (liveTargets.isEmpty()) 0f else liveTargets.sumOf { it.y.toDouble() }.toFloat() / liveTargets.size
        val targetVx = if (liveTargets.isEmpty()) 0f else liveTargets.sumOf { it.vx.toDouble() }.toFloat() / liveTargets.size
        val targetVy = if (liveTargets.isEmpty()) 0f else liveTargets.sumOf { it.vy.toDouble() }.toFloat() / liveTargets.size
        val radius = when (shape) {
            CoreShape.RING -> 145f // The expanded annulus, outside its dead zone.
            CoreShape.TESSERACT -> 200f // Spatially separated dash origins can form a lattice.
            else -> when (build) {
                BuildDirection.KINETIC, BuildDirection.BLADES -> 140f
                BuildDirection.DISCHARGE, BuildDirection.ORBITAL -> 370f
                BuildDirection.SALVAGE -> 100f
            }
        }
        val angularSpeed = if (shape == CoreShape.ORB) 1.8f else 1.3f
        val desiredX = centerX + cos(t * angularSpeed) * radius
        val desiredY = centerY + sin(t * angularSpeed) * radius
        val desiredVx = targetVx - sin(t * angularSpeed) * radius * angularSpeed
        val desiredVy = targetVy + cos(t * angularSpeed) * radius * angularSpeed
        var accelerationX = (desiredX - game.coreX) * 5f + (desiredVx - game.velocityX) * 3f - cos(t * angularSpeed) * radius * angularSpeed * angularSpeed
        var accelerationY = (desiredY - game.coreY) * 5f + (desiredVy - game.velocityY) * 3f - sin(t * angularSpeed) * radius * angularSpeed * angularSpeed
        var urgentThreat = false
        for (projectile in game.projectiles) if (projectile.hostile) {
            val relativeVx = projectile.vx - game.velocityX
            val relativeVy = projectile.vy - game.velocityY
            val dx = projectile.x - game.coreX
            val dy = projectile.y - game.coreY
            val closestTime = (-(dx * relativeVx + dy * relativeVy) / max(1f, relativeVx * relativeVx + relativeVy * relativeVy)).coerceIn(0f, 0.4f)
            val closestX = dx + relativeVx * closestTime
            val closestY = dy + relativeVy * closestTime
            val closestDistance = length(closestX, closestY)
            if (dx * relativeVx + dy * relativeVy < 0f && closestDistance < 60f) {
                val push = (60f - closestDistance) / 60f * 1_200f
                if (build == BuildDirection.KINETIC || build == BuildDirection.BLADES) {
                    val speed = max(1f, length(relativeVx, relativeVy))
                    val side = if (-relativeVy * game.velocityX + relativeVx * game.velocityY >= 0f) 1f else -1f
                    accelerationX += -relativeVy / speed * side * push
                    accelerationY += relativeVx / speed * side * push
                } else {
                    accelerationX -= closestX / max(1f, closestDistance) * push
                    accelerationY -= closestY / max(1f, closestDistance) * push
                }
                if (closestTime < 0.13f && closestDistance < 28f) urgentThreat = true
            }
        }
        val desiredHeading = atan2(accelerationY, accelerationX)
        heading += atan2(sin(desiredHeading - heading), cos(desiredHeading - heading)).coerceIn(-0.08f, 0.08f)
        // Full polarity needs a shorter tether for tight orbits than the old fatigued thrust did.
        val distance = ((length(accelerationX, accelerationY) - 92f) / game.magnetStrength / (if (game.overdriveTime > 0f) 1.3f else 1f)).coerceIn(65f, 480f)
        // Use the ordinary recovery area instead of straining the field at screen edges.
        val edgeMargin = (1f - game.content.tempo.fatigue.edgeStrainStart) * 0.5f
        game.updatePointer(
            (game.coreX + cos(heading) * distance - game.cameraX + game.screenWidth / 2f)
                .coerceIn(game.screenWidth * edgeMargin, game.screenWidth * (1f - edgeMargin)),
            (game.coreY + sin(heading) * distance - game.cameraY + game.screenHeight / 2f)
                .coerceIn(game.screenHeight * edgeMargin, game.screenHeight * (1f - edgeMargin)),
        )
        val brake = when (shape) {
            CoreShape.PRISM -> frame % 180 in 120..160
            CoreShape.SHARD -> frame % 140 in 16..38 // Recover after a through-target dash before the revealing hit.
            CoreShape.DIAMOND -> game.characterRuntime.parryWindow > 0f ||
                (urgentThreat && game.characterRuntime.parryCooldown <= 0f && game.speed >= 200f)
            CoreShape.RING -> frame % 240 in 200..220
            CoreShape.TESSERACT -> frame % 360 in 320..340
            else -> game.speed > 850f
        }
        game.setBrake(brake)
        val abilityDash = (shape == CoreShape.SHARD || shape == CoreShape.TESSERACT || shape == CoreShape.PRISM || build == BuildDirection.DISCHARGE || build == BuildDirection.ORBITAL) && frame % 140 == 0
        if ((urgentThreat || abilityDash) && !brake && game.heat < 45f) game.requestDash()
        game.update(1f / 60f)
        game.takeVisualFxCues()
        game.takeSoundCues()
        maxEnemies = max(maxEnemies, game.enemies.size)
    }
    val damage = targets.sumOf { (it.maxHp - max(0f, it.hp)).toDouble() }.toFloat()
    val total = targets.sumOf { it.maxHp.toDouble() }.toFloat()
    return EncounterResult(shape, build, profile, encounter, damage, 100f * damage / total,
        targets.count { it.dead || it.hp <= 0f }, game.hp / game.maxHp, game.elapsed - startedAt, game.runMatter, game.level - 18, maxEnemies, game.message)
}
