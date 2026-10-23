// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.SkillCueCodes
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class InteractionFxReducerTest {
    @Test
    fun buildNotificationsAreGroupedBoundedDetachedAndExpireOnlyWhenEffectsAdvance() {
        val reducer = InteractionFxReducer(seed = 7)
        repeat(4) { index -> reducer.apply(listOf(VisualFxCue.BuildChanged("Item $index",
            kinetickk.foundation.collections.immutableListOf("Weapon power +0.1×", "New resonance")))) }
        val retained = reducer.snapshot()
        assertEquals(listOf("Item 1", "Item 2", "Item 3"), retained.buildNotifications.map { it.title })
        assertSame(retained, reducer.snapshot())
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(3f)))
        assertEquals(6f, retained.buildNotifications.first().life)
        assertEquals(3f, reducer.snapshot().buildNotifications.first().life)
        reducer.apply(listOf(VisualFxCue.ClearAll))
        assertTrue(reducer.snapshot().buildNotifications.isEmpty())
        reducer.apply(listOf(VisualFxCue.BuildChanged("Relic", kinetickk.foundation.collections.immutableListOf())))
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(6.1f)))
        assertTrue(reducer.snapshot().buildNotifications.isEmpty())
    }

    @Test
    fun snapshotIsReusedUntilTheVisibleProjectionChanges() {
        val reducer = InteractionFxReducer(seed = 46)
        val empty = reducer.snapshot()
        reducer.apply(
            listOf(
                VisualFxCue.EffectsAdvanced(1f / 120f),
                VisualFxCue.WeaponArcsAdvanced(1f / 120f),
                VisualFxCue.MotionSample(1f / 120f, 0f, 0f, 0f, 0f),
                VisualFxCue.VisualCuesDropped(1),
                VisualFxCue.ClearAll,
            ),
        )
        assertSame(empty, reducer.snapshot())

        reducer.apply(listOf(VisualFxCue.Burst(0f, 0f, 1, 0, ParticleDensity.NORMAL)))
        val populated = reducer.snapshot()
        assertNotSame(empty, populated)
        assertSame(populated, reducer.snapshot())

        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(1f / 120f)))
        assertNotSame(populated, reducer.snapshot())
    }

    @Test
    fun categorySnapshotsAreSharedAndPreviouslyPublishedValuesRemainImmutable() {
        val reducer = InteractionFxReducer(seed = 460)
        reducer.apply(
            listOf(
                VisualFxCue.Burst(10f, 20f, 1, 0, ParticleDensity.NORMAL),
                VisualFxCue.WeaponArcAdded(0f, 0f, 1f, 1f, 1f),
            ),
        )
        val before = reducer.snapshot()
        val beforeParticle = before.particles.single()

        reducer.apply(listOf(VisualFxCue.WeaponArcsAdvanced(0.1f)))
        val arcsAdvanced = reducer.snapshot()
        assertSame(before.particles, arcsAdvanced.particles)
        assertNotSame(before.weaponArcs, arcsAdvanced.weaponArcs)

        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(0.1f)))
        val particlesAdvanced = reducer.snapshot()
        assertNotSame(arcsAdvanced.particles, particlesAdvanced.particles)
        assertSame(arcsAdvanced.weaponArcs, particlesAdvanced.weaponArcs)
        assertEquals(beforeParticle, before.particles.single())
        assertTrue(particlesAdvanced.particles.single().life < beforeParticle.life)
    }

    @Test
    fun identicalSeedsAndCuesProduceIdenticalVisualSnapshots() {
        val cues = listOf(
            VisualFxCue.Burst(10f, 20f, 12, 3, ParticleDensity.HIGH),
            VisualFxCue.DirectionalBurst(5f, 6f, 7, 2, 1f, -1f, ParticleDensity.NORMAL),
            VisualFxCue.EffectsAdvanced(1f / 120f),
        )
        val first = InteractionFxReducer(seed = 47).applyAndSnapshot(cues)
        val second = InteractionFxReducer(seed = 47).applyAndSnapshot(cues)

        assertEquals(first, second)
    }

    @Test
    fun particlesAcceptSevenHundredAndRejectSevenHundredFirst() {
        val reducer = InteractionFxReducer(seed = 48)
        reducer.apply(
            listOf(
                VisualFxCue.Burst(
                    x = 0f,
                    y = 0f,
                    requestedCount = InteractionFxLimits.MAX_PARTICLES,
                    colorIndex = 1,
                    density = ParticleDensity.NORMAL,
                ),
            ),
        )
        val accepted = reducer.snapshot().particles

        reducer.apply(
            listOf(VisualFxCue.Burst(1f, 1f, 1, 2, ParticleDensity.NORMAL)),
        )

        assertEquals(InteractionFxLimits.MAX_PARTICLES, accepted.size)
        assertEquals(accepted, reducer.snapshot().particles)
    }

    @Test
    fun directionalParticlesAcceptSevenHundredAndRejectSevenHundredFirst() {
        val reducer = InteractionFxReducer(seed = 49)
        reducer.apply(
            listOf(
                VisualFxCue.DirectionalBurst(
                    x = 0f,
                    y = 0f,
                    requestedCount = InteractionFxLimits.MAX_PARTICLES,
                    colorIndex = 1,
                    directionX = 1f,
                    directionY = 0f,
                    density = ParticleDensity.NORMAL,
                ),
            ),
        )
        val accepted = reducer.snapshot().particles

        reducer.apply(
            listOf(
                VisualFxCue.DirectionalBurst(
                    x = 1f,
                    y = 1f,
                    requestedCount = 1,
                    colorIndex = 2,
                    directionX = 1f,
                    directionY = 0f,
                    density = ParticleDensity.NORMAL,
                ),
            ),
        )

        assertEquals(InteractionFxLimits.MAX_PARTICLES, accepted.size)
        assertEquals(accepted, reducer.snapshot().particles)
    }

    @Test
    fun motionEchoesAcceptThirtySixAndTrimOldestOnThirtySeventh() {
        val reducer = InteractionFxReducer(seed = 50)
        repeat(InteractionFxLimits.MAX_MOTION_ECHOES) { index ->
            reducer.apply(listOf(motionSample(index)))
        }

        val accepted = reducer.snapshot().motionEchoes
        assertEquals(InteractionFxLimits.MAX_MOTION_ECHOES, accepted.size)
        assertEquals(0f, accepted.first().x)

        reducer.apply(listOf(motionSample(InteractionFxLimits.MAX_MOTION_ECHOES)))

        val overflow = reducer.snapshot().motionEchoes
        assertEquals(InteractionFxLimits.MAX_MOTION_ECHOES, overflow.size)
        assertEquals(1f, overflow.first().x)
        assertEquals(InteractionFxLimits.MAX_MOTION_ECHOES.toFloat(), overflow.last().x)
    }

    @Test
    fun shockwavesAcceptFortyEightAndTrimOldestOnFortyNinth() {
        val reducer = InteractionFxReducer(seed = 51)
        repeat(InteractionFxLimits.MAX_SHOCKWAVES) { index ->
            reducer.apply(listOf(shockwave(index)))
        }

        val accepted = reducer.snapshot().shockwaves
        assertEquals(InteractionFxLimits.MAX_SHOCKWAVES, accepted.size)
        assertEquals(0f, accepted.first().x)

        reducer.apply(listOf(shockwave(InteractionFxLimits.MAX_SHOCKWAVES)))

        val overflow = reducer.snapshot().shockwaves
        assertEquals(InteractionFxLimits.MAX_SHOCKWAVES, overflow.size)
        assertEquals(1f, overflow.first().x)
        assertEquals(InteractionFxLimits.MAX_SHOCKWAVES.toFloat(), overflow.last().x)
    }

    @Test
    fun damageNumbersAcceptOneHundredFortyAndRejectOneHundredFortyFirst() {
        val reducer = InteractionFxReducer(seed = 52)
        repeat(InteractionFxLimits.MAX_DAMAGE_NUMBERS) { index ->
            reducer.apply(listOf(damageNumber(index)))
        }
        val accepted = reducer.snapshot().damageNumbers

        reducer.apply(listOf(damageNumber(InteractionFxLimits.MAX_DAMAGE_NUMBERS)))

        assertEquals(InteractionFxLimits.MAX_DAMAGE_NUMBERS, accepted.size)
        assertEquals(accepted, reducer.snapshot().damageNumbers)
    }

    @Test
    fun weaponArcsAcceptOneHundredTwentyEightAndTrimOldestOnOneHundredTwentyNinth() {
        val reducer = InteractionFxReducer(seed = 53)
        repeat(InteractionFxLimits.MAX_WEAPON_ARCS) { index ->
            reducer.apply(listOf(weaponArc(index)))
        }

        val accepted = reducer.snapshot().weaponArcs
        assertEquals(InteractionFxLimits.MAX_WEAPON_ARCS, accepted.size)
        assertEquals(0f, accepted.first().toX)

        reducer.apply(listOf(weaponArc(InteractionFxLimits.MAX_WEAPON_ARCS)))

        val overflow = reducer.snapshot().weaponArcs
        assertEquals(InteractionFxLimits.MAX_WEAPON_ARCS, overflow.size)
        assertEquals(1f, overflow.first().toX)
        assertEquals(InteractionFxLimits.MAX_WEAPON_ARCS.toFloat(), overflow.last().toX)
    }

    @Test
    fun motionEchoesAndCueOrderingPreserveTheirFiniteLifecycle() {
        val reducer = InteractionFxReducer(seed = 54)
        repeat(100) { index ->
            reducer.apply(
                listOf(
                    VisualFxCue.MotionSample(
                        deltaSeconds = 0.02f,
                        previousCoreX = index.toFloat(),
                        previousCoreY = 0f,
                        speed = 2_000f,
                        dashPhaseTime = 0.1f,
                    ),
                ),
            )
        }
        assertEquals(36, reducer.snapshot().motionEchoes.size)

        reducer.apply(
            listOf(
                VisualFxCue.WeaponArcAdded(0f, 0f, 1f, 1f, 0.14f),
                VisualFxCue.WeaponArcsAdvanced(0.04f),
                VisualFxCue.WeaponArcAdded(0f, 0f, 2f, 2f, 0.14f),
            ),
        )
        val arcs = reducer.snapshot().weaponArcs
        assertEquals(0.10f, arcs.first().life, absoluteTolerance = 0.000_001f)
        assertEquals(0.14f, arcs.last().life)
    }

    @Test
    fun clearAndWorldRebaseFollowDeclaredPresentationPolicy() {
        val reducer = InteractionFxReducer(seed = 55)
        reducer.apply(
            listOf(
                VisualFxCue.Burst(100f, 200f, 1, 1, ParticleDensity.NORMAL),
                VisualFxCue.DamageNumberAdded(100f, 200f, 5, false),
                VisualFxCue.ShockwaveAdded(100f, 200f, 1f, 20f, 2),
                VisualFxCue.MotionSample(0.02f, 100f, 200f, 2_000f, 0.1f),
                VisualFxCue.WeaponArcAdded(100f, 200f, 300f, 400f, 1f),
                VisualFxCue.WorldRebased(40f, 50f),
            ),
        )
        val rebased = reducer.snapshot()
        assertEquals(60f, rebased.particles.single().x)
        assertEquals(150f, rebased.particles.single().y)
        assertEquals(60f, rebased.damageNumbers.single().x)
        assertEquals(150f, rebased.damageNumbers.single().y)
        assertEquals(100f, rebased.shockwaves.single().x)
        assertEquals(100f, rebased.motionEchoes.single().x)
        assertEquals(100f, rebased.weaponArcs.single().fromX)

        reducer.apply(listOf(VisualFxCue.ClearAll))
        val cleared = reducer.snapshot()
        assertTrue(cleared.boundedSizes().all { it == 0 })
    }

    @Test
    fun damageNumbersDriftAwayFromTheLastSampledCore() {
        val reducer = InteractionFxReducer(seed = 56)
        reducer.apply(listOf(VisualFxCue.DamageNumberAdded(10f, 20f, 12, false)))
        val beforeSample = reducer.snapshot().damageNumbers.single()
        assertEquals(0f, beforeSample.driftX)
        assertEquals(-1f, beforeSample.driftY, "Without a Core sample numbers rise")
        assertEquals(InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS, beforeSample.life)

        reducer.apply(
            listOf(
                VisualFxCue.MotionSample(0.01f, 100f, 100f, 0f, 0f),
                VisualFxCue.DamageNumberAdded(130f, 60f, 12, true),
                VisualFxCue.DamageNumberAdded(100f, 100f, 12, false),
                VisualFxCue.WorldRebased(1_000f, 0f),
                VisualFxCue.DamageNumberAdded(-900f, 140f, 12, false),
            ),
        )
        val numbers = reducer.snapshot().damageNumbers
        assertEquals(0.6f, numbers[1].driftX, 0.0001f)
        assertEquals(-0.8f, numbers[1].driftY, 0.0001f)
        assertEquals(-1f, numbers[2].driftY, "A hit on the Core itself rises")
        assertEquals(1f, numbers[3].driftY, 0.0001f, "The sampled Core moves with a world rebase")
        assertEquals(0f, numbers[3].driftX, 0.0001f)

        reducer.apply(listOf(VisualFxCue.ClearAll, VisualFxCue.DamageNumberAdded(500f, 500f, 12, false)))
        assertEquals(-1f, reducer.snapshot().damageNumbers.single().driftY, "Clearing forgets the Core sample")
    }

    @Test
    fun burstsLeadWithShardsWithoutChangingTheParticleCount() {
        listOf(ParticleDensity.LOW, ParticleDensity.NORMAL, ParticleDensity.HIGH).forEach { density ->
            val reducer = InteractionFxReducer(seed = 57)
            reducer.apply(
                listOf(
                    VisualFxCue.Burst(0f, 0f, 12, 1, density),
                    VisualFxCue.DirectionalBurst(0f, 0f, 8, 3, 1f, 0f, density),
                ),
            )
            val particles = reducer.snapshot().particles
            val multiplier = when (density) {
                ParticleDensity.LOW -> 0.45f
                ParticleDensity.NORMAL -> 1f
                ParticleDensity.HIGH -> 1.4f
            }
            val burst = (12 * multiplier).toInt().coerceAtLeast(1)
            val directional = (8 * multiplier).toInt().coerceAtLeast(1)
            assertEquals(burst + directional, particles.size)
            listOf(particles.take(burst), particles.drop(burst)).forEach { group ->
                group.forEachIndexed { index, particle ->
                    assertEquals(index < InteractionFxLimits.SHARDS_PER_BURST, particle.size >= InteractionFxLimits.SHARD_MIN_SIZE,
                        "$density particle $index")
                }
            }
        }
    }

    @Test
    fun reactionFxAcceptFourOfAKindAndTrimTheOldestOnTheFifth() {
        val reducer = InteractionFxReducer(seed = 70)
        val shock = ReactionId.THERMAL_SHOCK.ordinal
        // Four Thermal Shocks 200 apart (outside the merge distance) and one Fusion.
        reducer.apply((0 until InteractionFxLimits.MAX_BURSTS_PER_REACTION).map {
            VisualFxCue.ReactionTriggered(it * 200f, 0f, shock, 60f, false)
        } + VisualFxCue.ReactionTriggered(0f, 500f, ReactionId.FUSION.ordinal, 150f, false))
        val four = reducer.snapshot().elementBursts
        assertEquals(listOf(0f, 200f, 400f, 600f), four.filter { it.reaction == shock }.map { it.x })
        // The fifth replaces that reaction's oldest; the other reaction keeps its flash.
        reducer.apply(listOf(VisualFxCue.ReactionTriggered(800f, 0f, shock, 60f, false)))
        val trimmed = reducer.snapshot().elementBursts
        assertEquals(listOf(200f, 400f, 600f, 800f), trimmed.filter { it.reaction == shock }.map { it.x })
        assertEquals(1, trimmed.count { it.reaction == ReactionId.FUSION.ordinal })
        // A fifth within the merge distance of a live one merges instead of trimming.
        reducer.apply(listOf(VisualFxCue.ReactionTriggered(810f, 0f, shock, 60f, false)))
        val merged = reducer.snapshot().elementBursts.filter { it.reaction == shock }
        assertEquals(4, merged.size)
        assertEquals(2, merged.last().weight)
        // Breaks have no per-kind cap: five Ignites 200 apart all stay.
        val breaks = InteractionFxReducer(seed = 71)
        breaks.apply((0..4).map { VisualFxCue.StatusBroken(it * 200f, 0f, StatusElement.THERMAL.ordinal) })
        assertEquals(5, breaks.snapshot().elementBursts.size)
    }

    @Test
    fun reactionLooksCarryTheirSpecLifeReachDirectionFlagsAndSeed() {
        val reducer = InteractionFxReducer(seed = 72)
        val flags = ReactionCueFlags.VERB or ReactionCueFlags.KEYSTONE
        reducer.apply(listOf(
            VisualFxCue.ReactionTriggered(0f, 0f, ReactionId.GLACIER_CRUSH.ordinal, 30f, false, direction = 1.25f, flags = flags),
            VisualFxCue.ReactionTriggered(500f, 0f, ReactionId.MAGNETIZE.ordinal, 400f, false),
            VisualFxCue.ReactionTriggered(900f, 0f, ReactionId.BLACKFIRE.ordinal, 10f, false),
        ))
        val (glacier, magnetize, blackfire) = reducer.snapshot().elementBursts
        assertEquals(ReactionFxSpecs[ReactionId.GLACIER_CRUSH].life, glacier.maxLife)
        assertEquals(220f, glacier.radius, "Glacier Crush reaches its fixed shard length")
        assertEquals(240f, magnetize.radius, "an area look stops at 240")
        assertEquals(48f, blackfire.radius, "a target look starts at 48")
        assertEquals(1.25f, glacier.direction)
        assertEquals(flags, glacier.flags)
        assertTrue(magnetize.direction.isNaN())
        assertNotEquals(glacier.seed, magnetize.seed, "each look hashes its own seed")
        // A merge keeps the seed and takes the newest direction and flags; the flash starts again.
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(0.2f),
            VisualFxCue.ReactionTriggered(10f, 0f, ReactionId.GLACIER_CRUSH.ordinal, 30f, false, direction = -2f, flags = 0)))
        val merged = reducer.snapshot().elementBursts.first()
        assertEquals(-2f, merged.direction)
        assertEquals(0, merged.flags)
        assertEquals(glacier.seed, merged.seed)
        assertEquals(merged.maxLife, merged.life)
        // Each look ends on its own life.
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(ReactionFxSpecs[ReactionId.GLACIER_CRUSH].life + 0.001f)))
        assertTrue(reducer.snapshot().elementBursts.none { it.reaction == ReactionId.GLACIER_CRUSH.ordinal })
    }

    @Test
    fun skillBurstsAcceptEightAndTrimTheOldestOnTheNinth() {
        val reducer = InteractionFxReducer(seed = 73)
        reducer.apply((0 until InteractionFxLimits.MAX_SKILL_BURSTS).map { skillCast(it.toFloat()) })
        val accepted = reducer.snapshot().skillBursts
        assertEquals(InteractionFxLimits.MAX_SKILL_BURSTS, accepted.size)
        assertEquals(0f, accepted.first().x)
        reducer.apply(listOf(skillCast(InteractionFxLimits.MAX_SKILL_BURSTS.toFloat())))
        val trimmed = reducer.snapshot().skillBursts
        assertEquals(InteractionFxLimits.MAX_SKILL_BURSTS, trimmed.size)
        assertEquals(1f, trimmed.first().x)
        assertEquals(InteractionFxLimits.MAX_SKILL_BURSTS.toFloat(), trimmed.last().x)
        // A burst keeps the cue's facts and its look's life and reach; a phase without a look is ignored.
        val impact = InteractionFxReducer(seed = 74)
        impact.apply(listOf(
            VisualFxCue.SkillCast(SkillId.METEOR, 40f, 50f, 0.6f, 0.8f, 150f, 2, SkillCueCodes.PHASE_IMPACT, SkillCueCodes.FLAG_CORE_INSIDE),
            VisualFxCue.SkillCast(SkillId.SOLAR_FLARE, 0f, 0f, 0f, 0f, 200f, 1, SkillCueCodes.PHASE_IMPACT),
            VisualFxCue.SkillCast(SkillId.VOID_STEP, 0f, 0f, 1f, 0f, 240f, 3, SkillCueCodes.PHASE_IMPACT),
        ))
        val (meteor, step) = impact.snapshot().skillBursts
        assertEquals(SkillId.METEOR.ordinal, meteor.skill)
        assertEquals(SkillCueCodes.PHASE_IMPACT, meteor.phase)
        assertEquals(150f, meteor.radius)
        assertEquals(2, meteor.rank)
        assertEquals(SkillCueCodes.FLAG_CORE_INSIDE, meteor.flags)
        assertEquals(SkillFxSpecs.of(SkillId.METEOR, 1)!!.life, meteor.maxLife)
        assertEquals(90f, step.radius, "the Void Step rift has its fixed reach")
        assertEquals(2, impact.snapshot().skillBursts.size, "Solar Flare has no impact look")
    }

    @Test
    fun apexBurstsAcceptTwoAndTrimTheOldestOnTheThird() {
        val reducer = InteractionFxReducer(seed = 75)
        reducer.apply(listOf(VisualFxCue.ApexActivated(CoreShape.ORB, 0f, 0f), VisualFxCue.ApexActivated(CoreShape.STAR, 1f, 0f)))
        assertEquals(listOf(CoreShape.ORB.ordinal, CoreShape.STAR.ordinal), reducer.snapshot().apexBursts.map { it.shape })
        reducer.apply(listOf(VisualFxCue.ApexActivated(CoreShape.HEXAGON, 2f, 0f)))
        val trimmed = reducer.snapshot().apexBursts
        assertEquals(InteractionFxLimits.MAX_APEX_BURSTS, trimmed.size)
        assertEquals(listOf(1f, 2f), trimmed.map { it.x })
        assertEquals(ApexFxSpec.activation.life, trimmed.last().maxLife)
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(ApexFxSpec.activation.life + 0.001f)))
        assertTrue(reducer.snapshot().apexBursts.isEmpty())
    }

    @Test
    fun skillAndApexLooksMoveWithARebaseAndLeaveWithClearAll() {
        val reducer = InteractionFxReducer(seed = 76)
        reducer.apply(listOf(
            VisualFxCue.SkillCast(SkillId.FROST_SNAP, 100f, 50f, 0f, 0f, 180f, 1),
            VisualFxCue.ApexActivated(CoreShape.DIPOLE, 300f, 70f),
            VisualFxCue.WorldRebased(40f, 20f),
        ))
        val rebased = reducer.snapshot()
        assertEquals(60f to 30f, rebased.skillBursts.single().let { it.x to it.y })
        assertEquals(260f to 50f, rebased.apexBursts.single().let { it.x to it.y })
        reducer.apply(listOf(VisualFxCue.ClearAll))
        val cleared = reducer.snapshot()
        assertTrue(cleared.skillBursts.isEmpty() && cleared.apexBursts.isEmpty())
    }

    @Test
    fun styledLinksStayUpForTheirLookAndWeaponLinksKeepTheCueLife() {
        val reducer = InteractionFxReducer(seed = 77)
        reducer.apply(listOf(
            VisualFxCue.WeaponArcAdded(0f, 0f, 1f, 1f, 0.12f),
            VisualFxCue.WeaponArcAdded(0f, 0f, 1f, 1f, 0.12f, LinkStyleCode.reaction(ReactionId.BLACKFIRE)),
            VisualFxCue.WeaponArcAdded(0f, 0f, 1f, 1f, 0.5f, LinkStyleCode.skill(SkillId.ARC_HOOK)),
        ))
        val arcs = reducer.snapshot().weaponArcs
        assertEquals(listOf(0.12f, 0.35f, 0.5f), arcs.map { it.life })
        assertEquals(arcs.map { it.life }, arcs.map { it.maxLife })
        assertEquals(listOf(0, LinkStyleCode.reaction(ReactionId.BLACKFIRE), LinkStyleCode.skill(SkillId.ARC_HOOK)), arcs.map { it.style })
    }

    @Test
    fun identicalSeedsAndCuesProduceIdenticalReactionSkillAndApexLooks() {
        val cues = listOf(
            VisualFxCue.ReactionTriggered(10f, 20f, ReactionId.SEISMIC.ordinal, 180f, false, flags = ReactionCueFlags.CASCADE),
            VisualFxCue.StatusBroken(30f, 40f, StatusElement.VOID.ordinal),
            VisualFxCue.SkillCast(SkillId.SONIC_BOOM, 5f, 6f, 1f, 0f, 300f, 3),
            VisualFxCue.ApexActivated(CoreShape.SPIRAL, 0f, 0f),
            VisualFxCue.EffectsAdvanced(1f / 120f),
        )
        assertEquals(InteractionFxReducer(seed = 78).applyAndSnapshot(cues), InteractionFxReducer(seed = 78).applyAndSnapshot(cues))
    }

    private fun skillCast(x: Float) = VisualFxCue.SkillCast(SkillId.STORM_COIL, x, 0f, 0f, 0f, 170f, 1)

    private fun InteractionFxReducer.applyAndSnapshot(cues: Iterable<VisualFxCue>) =
        apply(cues).let { snapshot() }

    private fun kinetickk.ball.gameplay.interaction.fx.VisualFxProjection.boundedSizes() = listOf(
        particles.size,
        motionEchoes.size,
        shockwaves.size,
        damageNumbers.size,
        weaponArcs.size,
        elementBursts.size,
        skillBursts.size,
        apexBursts.size,
    )

    private fun motionSample(index: Int) = VisualFxCue.MotionSample(
        deltaSeconds = 0.02f,
        previousCoreX = index.toFloat(),
        previousCoreY = 0f,
        speed = 2_000f,
        dashPhaseTime = 0.1f,
    )

    private fun shockwave(index: Int) = VisualFxCue.ShockwaveAdded(
        x = index.toFloat(),
        y = 0f,
        life = 10f,
        maxRadius = 10f,
        colorIndex = 1,
    )

    private fun damageNumber(index: Int) = VisualFxCue.DamageNumberAdded(
        x = index.toFloat(),
        y = 0f,
        amount = index.toLong(),
        critical = false,
    )

    private fun weaponArc(index: Int) = VisualFxCue.WeaponArcAdded(
        fromX = 0f,
        fromY = 0f,
        toX = index.toFloat(),
        toY = 1f,
        life = 10f,
    )
}
