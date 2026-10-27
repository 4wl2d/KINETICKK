// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.BuffSource
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.MarkKind
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.impactTaken
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.CoreStateProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.EnemyMarkVisual
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Combat foundations: marks, control bits, Core state, forced and echoed reactions, erasure, skill damage, impulses. */
class CombatFoundationsTest {
    @Test
    fun statusLayoutHoldsSeventyFourCells() {
        assertEquals(74, StatusCell.COUNT)
        assertEquals(StatusCell.COUNT, buildTestState().dummy().status.size)
        assertEquals(MarkKind.entries.map { it.name }, EnemyMarkVisual.entries.map { it.name })

        val state = buildTestState()
        val enemy = state.dummy()
        assertNull(state.markOf(enemy))
        state.setMark(enemy, MarkKind.SPALL, 3f)
        assertTrue(state.markIs(enemy, MarkKind.SPALL))
        // A newer mark replaces the older one, whatever its kind.
        state.setMark(enemy, MarkKind.CRACKED, 4f)
        assertEquals(MarkKind.CRACKED, state.markOf(enemy))
        assertFalse(state.consumeMark(enemy, MarkKind.SPALL), "Only the live kind is cashed")
        assertTrue(state.markIs(enemy, MarkKind.CRACKED))
        assertTrue(state.consumeMark(enemy, MarkKind.CRACKED))
        assertNull(state.markOf(enemy))
        assertFalse(state.consumeMark(enemy, MarkKind.CRACKED))

        // A mark lapses at its time.
        state.setMark(enemy, MarkKind.SPORED, 1f)
        state.elapsed += 0.99f
        assertEquals(MarkKind.SPORED, state.markOf(enemy))
        state.elapsed += 0.02f
        assertNull(state.markOf(enemy))
        assertFalse(state.consumeMark(enemy, MarkKind.SPORED))
    }

    @Test
    fun controlBitsRoundTripEveryControlStateAndTheMark() {
        val state = buildTestState()
        state.elapsed = 20f
        val plain = state.dummy(x = 300f)
        val busy = state.dummy(x = 500f)
        val boss = state.dummy(x = 700f, type = EnemyType.ARCHITECT)
        busy.disabledUntil = 21f
        busy.silencedUntil = 21f
        busy.thrownUntil = 21f
        busy.status[StatusCell.BLACKFIRE_UNTIL] = 21f
        busy.status[StatusCell.UNMAKE_AT] = 20.5f
        state.setMark(busy, MarkKind.BOOMED, 0.8f)
        state.voidAnchorId = busy.id
        state.voidAnchorUntil = 22f
        state.addField(CombatFieldKind.ATTRACTOR, FieldSource.REACTION, ReactionId.MAGNETIZE.ordinal, busy.x, busy.y,
            radius = 200f, seconds = 3f, damagePerSecond = 0f, anchorId = busy.id)
        boss.disabledUntil = 21f

        val bits = state.toRenderModel().enemies.associate { it.id to it.controlBits }
        assertEquals(0, bits.getValue(plain.id))
        val busyBits = bits.getValue(busy.id)
        listOf(
            EnemyControlBits.STOPPED, EnemyControlBits.SILENCED, EnemyControlBits.THROWN, EnemyControlBits.BLACKFIRE,
            EnemyControlBits.UNMAKE_PENDING, EnemyControlBits.VOID_ANCHOR, EnemyControlBits.ATTRACTOR_ANCHOR,
        ).forEach { bit -> assertTrue(busyBits and bit != 0, "bit $bit") }
        assertEquals(0, busyBits and EnemyControlBits.APEX_STRIKE_TARGET)
        assertEquals(MarkKind.BOOMED.ordinal, EnemyControlBits.markKind(busyBits))
        assertEquals(EnemyMarkVisual.BOOMED, EnemyControlBits.mark(busyBits))
        assertEquals(0, bits.getValue(boss.id) and EnemyControlBits.STOPPED, "A boss is never shown stopped")

        // Every state lapses with time; the anchor lapses with its own time.
        state.elapsed = 21.5f
        busy.status[StatusCell.UNMAKE_AT] = 0f
        assertEquals(EnemyControlBits.VOID_ANCHOR or EnemyControlBits.ATTRACTOR_ANCHOR,
            state.toRenderModel().enemies.single { it.id == busy.id }.controlBits)
    }

    @Test
    fun coreStateProjectsLiveScalarsAndOtherwiseStaysNeutral() {
        val state = buildTestState()
        state.elapsed = 5f
        assertSame(CoreStateProjection.NEUTRAL, state.toRenderModel().coreState)

        state.ablazeUntil = 6f
        state.blastJumpUntil = 5.5f
        state.guardShards = 4
        state.guardUntil = 9f
        state.phaseUntil = 5.2f
        state.primedDashUntil = 7f
        state.coreFieldMask = 1 shl CombatFieldKind.NULL_WELL.ordinal
        val model = state.toRenderModel()
        val core = model.coreState
        // Ablaze shows the share left of the three seconds Wildfire grants.
        assertEquals(1f / 3f, core.ablaze01, 1e-6f)
        assertEquals(0f, core.frictionless01)
        assertTrue(core.blastJumpReady)
        assertEquals(4, core.guardShards)
        assertTrue(core.phased)
        assertTrue(core.primedDash)
        assertEquals(1 shl CombatFieldKind.NULL_WELL.ordinal, core.inFieldMask)
        assertSame(core, state.toRenderModel(reusableCollections = model, identitySource = state).coreState)

        state.elapsed = 9.5f
        state.coreFieldMask = 0
        assertSame(CoreStateProjection.NEUTRAL, state.toRenderModel().coreState, "Lapsed states and spent shards show nothing")
    }

    @Test
    fun forcedReactionsIgnoreTheEnemyCooldownButKeepThePairCooldownTokensAndShifts() {
        val state = buildTestState()
        val target = state.dummy()
        target.stack(StatusElement.THERMAL, 3)
        target.stack(StatusElement.CRYO, 1)
        target.status[StatusCell.REACTION_READY_AT] = state.elapsed + 10f
        val random = state.gameplayRandom.snapshot()

        assertTrue(state.forceReaction(target, generation = 0))
        assertEquals(ReactionId.THERMAL_SHOCK.ordinal + 1, target.status[StatusCell.LAST_REACTION].toInt())
        assertEquals(0, target.stacksOf(StatusElement.THERMAL))
        assertEquals(0, target.stacksOf(StatusElement.CRYO))
        assertTrue(target.hp < target.maxHp)
        assertEquals(random, state.gameplayRandom.snapshot(), "A forced reaction draws nothing")

        // The pair waits for its own cooldown.
        target.stack(StatusElement.THERMAL, 3)
        target.stack(StatusElement.CRYO, 1)
        assertFalse(state.forceReaction(target, generation = 0))
        assertEquals(3, target.stacksOf(StatusElement.THERMAL), "A refused force spends nothing")

        // It needs two stacks of one element and one of another.
        val single = state.dummy(x = -500f)
        single.stack(StatusElement.ION, 4)
        assertFalse(state.forceReaction(single, generation = 0))
        single.stack(StatusElement.ENTROPY, 1)
        assertTrue(state.forceReaction(single, generation = 0))
        assertEquals(ReactionId.FEEDBACK.ordinal + 1, single.status[StatusCell.LAST_REACTION].toInt())

        // A cascade needs a token; the generation cap and a shifting Architect refuse.
        val cascade = state.dummy(x = 900f)
        cascade.stack(StatusElement.GRAVITIC, 2)
        cascade.stack(StatusElement.SONIC, 1)
        state.reactionTokens = 0.5f
        assertFalse(state.forceReaction(cascade, generation = 1))
        state.reactionTokens = 5f
        cascade.status[StatusCell.GENERATION + StatusElement.SONIC.ordinal] = 2f
        assertFalse(state.forceReaction(cascade, generation = 0))
        val architect = state.dummy(x = 1_500f, type = EnemyType.ARCHITECT)
        architect.stack(StatusElement.VOID, 2)
        architect.stack(StatusElement.CRYO, 1)
        architect.shiftUntil = state.elapsed + 5f
        assertFalse(state.forceReaction(architect, generation = 0))
    }

    @Test
    fun aForcedReactionScalesItsMagnitudeUpToTheBound() {
        fun loss(scale: Float): Float {
            val state = buildTestState()
            // A small integrity keeps the measured loss exact in Float.
            val target = state.dummy(hp = 1_000f)
            target.stack(StatusElement.THERMAL, 3)
            target.stack(StatusElement.CRYO, 1)
            assertTrue(state.forceReaction(target, generation = 0, scale = scale))
            return target.maxHp - target.hp
        }
        val nominal = loss(1f)
        assertEquals(nominal * 1.2f, loss(1.2f), nominal * 1e-4f)
        assertEquals(nominal * MAX_FORCED_REACTION_SCALE, loss(1.3f), nominal * 1e-4f)
        assertEquals(nominal * MAX_FORCED_REACTION_SCALE, loss(5f), nominal * 1e-4f)
    }

    @Test
    fun anEchoReplaysAReactionWithoutConsumingDiscoveringOrCoolingAnything() {
        val state = buildTestState()
        val target = state.dummy(hp = 1_000f)
        target.stack(StatusElement.ENTROPY, 1)
        val tokens = state.reactionTokens
        val discovered = state.discoveredReactionMask

        assertTrue(state.echoReaction(target, ReactionId.THERMAL_SHOCK))

        val echoed = target.maxHp - target.hp
        assertTrue(echoed > 0f)
        assertEquals(1, target.stacksOf(StatusElement.ENTROPY), "No stack is consumed")
        assertEquals(0f, target.status[StatusCell.LAST_REACTION])
        assertEquals(0f, target.status[StatusCell.REACTION_PAIR_READY_AT + ReactionId.THERMAL_SHOCK.ordinal])
        assertEquals(0f, target.status[StatusCell.REACTION_READY_AT])
        assertEquals(discovered, state.discoveredReactionMask)
        assertEquals(tokens - 1f, state.reactionTokens, "An echo is a token-gated cascade")
        val cues = state.takeVisualFxCues()
        assertTrue(cues.none { it is VisualFxCue.BuildChanged })
        val cue = cues.filterIsInstance<VisualFxCue.ReactionTriggered>().single()
        assertEquals(ReactionCueFlags.ECHO or ReactionCueFlags.CASCADE, cue.flags)
        assertFalse(cue.firstDiscovery)

        // It hits as hard as the same reaction forced at two plus two stacks.
        val reference = buildTestState()
        val forced = reference.dummy(hp = 1_000f)
        forced.stack(StatusElement.THERMAL, 2)
        forced.stack(StatusElement.CRYO, 2)
        forced.stack(StatusElement.ENTROPY, 1)
        assertTrue(reference.forceReaction(forced, generation = 0))
        assertEquals(forced.maxHp - forced.hp, echoed, echoed * 1e-4f)

        state.reactionTokens = 0.5f
        assertFalse(state.echoReaction(target, ReactionId.THERMAL_SHOCK), "No token, no echo")
    }

    @Test
    fun reactionCuesMarkCascadesAndReactionLinksCarryTheirStyle() {
        val state = buildTestState()
        val target = state.dummy(x = 400f)
        state.dummy(x = 500f)
        state.applyStatus(target, StatusElement.CRYO, 3, generation = 1)
        state.applyStatus(target, StatusElement.ION, 1, generation = 1)
        val cues = state.takeVisualFxCues()
        val reaction = cues.filterIsInstance<VisualFxCue.ReactionTriggered>().single()
        assertEquals(ReactionId.SUPERCONDUCT.ordinal, reaction.reaction)
        // A cascade, and near the Core its Frictionless verb fires too.
        assertEquals(ReactionCueFlags.CASCADE or ReactionCueFlags.VERB, reaction.flags)
        val links = cues.filterIsInstance<VisualFxCue.WeaponArcAdded>()
        assertTrue(links.isNotEmpty())
        assertTrue(links.all { it.style == LinkStyleCode.reaction(ReactionId.SUPERCONDUCT) })

        // An Overload chains in its Break style; an Ion arc from a hit keeps the weapon look.
        val overload = buildTestState()
        val charged = overload.dummy(x = 400f)
        overload.dummy(x = 480f)
        charged.stack(StatusElement.ION, 4)
        overload.forceElementBreak(charged, StatusElement.ION)
        val chain = overload.takeVisualFxCues().filterIsInstance<VisualFxCue.WeaponArcAdded>()
        assertTrue(chain.isNotEmpty())
        assertTrue(chain.all { it.style == LinkStyleCode.elementBreak(StatusElement.ION) })
        val arcing = buildTestState()
        val struck = arcing.dummy(x = 400f)
        arcing.dummy(x = 480f)
        struck.stack(StatusElement.ION, 2)
        arcing.arcCharge(struck)
        assertEquals(listOf(LinkStyleCode.WEAPON), arcing.takeVisualFxCues().filterIsInstance<VisualFxCue.WeaponArcAdded>().map { it.style })
    }

    @Test
    fun breaksAndQuakesUseTheirElementColourIndices() {
        StatusElement.entries.filter { it != StatusElement.ION }.forEach { element ->
            val state = buildTestState()
            val target = state.dummy()
            target.stack(element, 4)
            target.status[StatusCell.HOLLOW_MEMORY] = 50f
            state.forceElementBreak(target, element)
            val rings = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>().map { it.colorIndex }
            assertTrue(FxColorCode.elementTone(element) in rings, "$element Break rings $rings")
            assertTrue(rings.none { it == 4 }, "$element Break no longer uses the threat colour")
        }
        val expected = mapOf(
            ReactionId.THUNDERCLAP to 13,
            ReactionId.STASIS to 9,
            ReactionId.SEISMIC to FxColorCode.QUAKE_BASE + StatusElement.GRAVITIC.ordinal,
        )
        expected.forEach { (reaction, colour) ->
            val state = buildTestState()
            val target = state.dummy()
            target.stack(reaction.first, 3)
            target.stack(reaction.second, 1)
            assertTrue(state.forceReaction(target, generation = 0))
            assertEquals(listOf(colour), state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>().map { it.colorIndex }, "$reaction")
        }
        assertEquals(19, FxColorCode.quake(StatusElement.GRAVITIC))
        assertEquals(15, FxColorCode.APEX_GOLD)
    }

    @Test
    fun erasureSpendsOnlyLiveHostileBoltsAndShowsAtMostFourPerStep() {
        val state = buildTestState()
        val friendly = Projectile(0f, 0f, 0f, 0f, 4f, 2f, hostile = false)
        val spent = Projectile(0f, 0f, 0f, 0f, 4f, 0f)
        val mine = Projectile(0f, 0f, 0f, 0f, 6f, 5f, kind = ProjectileKind.MINE)
        val bolt = Projectile(0f, 0f, 0f, 0f, 5f, 3f)
        assertFalse(state.eraseHostileShot(friendly, EraseCause.SKILL, 0f, 0f))
        assertFalse(state.eraseHostileShot(spent, EraseCause.SKILL, 0f, 0f))
        assertFalse(state.eraseHostileShot(mine, EraseCause.SKILL, 0f, 0f))
        assertFalse(state.eraseHostileShot(bolt, EraseCause.APEX, 0f, 0f), "No Apex budget yet")
        assertEquals(listOf(2f, 0f, 5f, 3f), listOf(friendly, spent, mine, bolt).map { it.life })
        assertTrue(state.eraseHostileShot(bolt, EraseCause.REACTION, 0f, 0f))
        assertTrue(bolt.spent)
        assertFalse(state.eraseHostileShot(bolt, EraseCause.REACTION, 0f, 0f), "A spent bolt is erased once")

        state.takeVisualFxCues()
        state.shotErasedDispatches = 0
        repeat(6) { assertTrue(state.eraseHostileShot(Projectile(0f, 0f, 0f, 0f, 5f, 3f), EraseCause.ITEM, 1f, 2f)) }
        assertEquals(MAX_SHOT_ERASURES_SHOWN_PER_STEP, state.takeVisualFxCues().count { it is VisualFxCue.Burst })

        // Stasis spends the bolts in reach; mines and player shots stay.
        val stasis = buildTestState()
        val target = stasis.dummy()
        val reach = stasis.content.build.reaction(ReactionId.STASIS).radius * 0.5f
        val shots = listOf(
            Projectile(target.x + reach, target.y, 0f, 0f, 5f, 3f),
            Projectile(target.x, target.y + reach, 0f, 0f, 6f, 5f, kind = ProjectileKind.MINE),
            Projectile(target.x - reach, target.y, 0f, 0f, 4f, 2f, hostile = false),
            Projectile(target.x + 10_000f, target.y, 0f, 0f, 5f, 3f),
        )
        stasis.projectiles += shots
        target.stack(StatusElement.CRYO, 3)
        target.stack(StatusElement.VOID, 1)
        assertTrue(stasis.forceReaction(target, generation = 0))
        assertEquals(listOf(true, false, false, false), shots.map { it.spent })
    }

    @Test
    fun skillDamageNeverCritsMeetsTheReceiverAndCountsAsASkillKill() {
        val state = buildTestState()
        val target = state.dummy(hp = 10_000f)
        target.stack(StatusElement.ENTROPY, 2)
        val random = state.gameplayRandom.snapshot()
        val expected = 100f * state.relicSurge() * (1f + state.vulnerability(target, HitSource.SKILL)) *
            state.matrix.damageMultiplier * target.weaponTaken
        val dealt = state.dealSkillDamage(target, 100f, element = null)
        assertEquals(expected, dealt.amount, expected * 1e-5f)
        assertFalse(dealt.critical)
        assertEquals(random, state.gameplayRandom.snapshot(), "Skill damage rolls nothing")

        // A kinetic skill meets the impact frame and impact vulnerability.
        val sonic = state.dummy(x = -400f, hp = 10_000f)
        sonic.stack(StatusElement.SONIC, 3)
        val kinetic = state.dealSkillDamage(sonic, 100f, element = null, kinetic = true).amount
        val kineticExpected = 100f * state.relicSurge() * (1f + state.vulnerability(sonic, HitSource.IMPACT)) *
            state.matrix.damageMultiplier * sonic.impactTaken
        assertEquals(kineticExpected, kinetic, kineticExpected * 1e-5f)

        // An immune aspect refuses its element only.
        val warded = state.dummy(x = 800f, hp = 10_000f, type = EnemyType.ELITE)
        assignAspect(warded, StatusElement.THERMAL.ordinal, immune = true)
        assertEquals(0f, state.dealSkillDamage(warded, 100f, StatusElement.THERMAL).amount)
        assertTrue(state.dealSkillDamage(warded, 100f, StatusElement.CRYO).amount > 0f)

        // A skill kill fires kill clauses with no source.
        val weak = state.dummy(x = 1_200f, hp = 10f)
        state.dealSkillDamage(weak, 100f, StatusElement.VOID)
        assertTrue(weak.hp <= 0f)
        assertTrue(weak.killClausesEligible)
        assertEquals(HitSource.SKILL.ordinal + 1, weak.status[StatusCell.LAST_SOURCE].toInt())
        assertEquals(7, HitSource.SKILL.ordinal + 1)

        // The Architect's gate holds: one capped instance, nothing while it shifts.
        val architect = state.dummy(x = 2_000f, hp = 1_000_000f, type = EnemyType.ARCHITECT)
        val capped = state.dealSkillDamage(architect, 1_000_000f, element = null).amount
        assertTrue(capped > 0f && capped <= state.architectHitCap(architect) + 1e-3f)
        architect.shiftUntil = state.elapsed + 8f
        assertEquals(0f, state.dealSkillDamage(architect, 1_000f, element = null).amount)
    }

    @Test
    fun coreImpulsesAreCappedAndReactionImpulsesShareOneGate() {
        val state = buildTestState()
        state.setVelocityForTesting(0f, 0f)
        assertTrue(state.applyCoreImpulse(1f, 0f, 1_000f, gated = false))
        assertEquals(MAX_CORE_IMPULSE_SPEED, state.velocityX)
        assertTrue(state.applyCoreImpulse(0f, 1f, 300f, gated = false), "A player-timed push is never gated")
        assertEquals(300f, state.velocityY)

        assertTrue(state.applyCoreImpulse(-1f, 0f, 200f, gated = true))
        assertFalse(state.applyCoreImpulse(-1f, 0f, 200f, gated = true))
        assertEquals(MAX_CORE_IMPULSE_SPEED - 200f, state.velocityX)
        state.elapsed += CORE_IMPULSE_GATE_SECONDS
        assertTrue(state.applyCoreImpulse(-1f, 0f, 200f, gated = true))
        assertFalse(state.applyCoreImpulse(1f, 0f, 0f, gated = false))
        assertFalse(state.applyCoreImpulse(1f, 0f, Float.NaN, gated = false))
    }

    @Test
    fun buffsAreKeyedBySourceSoASkillAndAnItemStackApart() {
        val keys = SkillId.entries.map(BuffSource::skill) + ReactionId.entries.map(BuffSource::reaction) + BuffSource.APEX
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it < 0 })
        assertEquals(5, BuffSource.item(5))

        val state = buildTestState()
        state.addBuff(BuffSource.item(0), BuffStat.DAMAGE, 0.1f, 2f, maxStacks = 3)
        state.addBuff(BuffSource.skill(SkillId.METEOR), BuffStat.DAMAGE, 0.2f, 2f, maxStacks = 3)
        state.addBuff(BuffSource.skill(SkillId.METEOR), BuffStat.DAMAGE, 0.2f, 2f, maxStacks = 3)
        assertEquals(2, state.buffs.size)
        assertEquals(listOf(1, 2), state.buffs.map { it.stacks })
        assertEquals(0.5f, state.buffAmount(BuffStat.DAMAGE), 1e-6f)
    }

    @Test
    fun aNewRunClearsTheFieldsAndTheCoreReactionState() {
        val state = buildTestState()
        state.addTestField(CombatFieldKind.FUSION, x = 0f, y = 0f, radius = 10f)
        state.coreFieldMask = 3
        state.ablazeUntil = 5f
        state.guardShards = 2
        state.voidAnchorId = 4
        state.erasureLatchId = 9
        state.skillBufferMask = 1
        state.pendingActiveItemId = state.content.activeItemIds.first { it >= 0 }
        state.pendingActiveReborn = true
        state.startRun()
        assertEquals(0, state.combatFieldTable.count)
        assertEquals(0, state.coreFieldMask)
        assertEquals(0f, state.ablazeUntil)
        assertEquals(0, state.guardShards)
        assertEquals(-1, state.voidAnchorId)
        assertEquals(-1, state.erasureLatchId)
        assertEquals(0, state.skillBufferMask)
        assertNull(state.pendingActiveItemId, "no active item waits for a slot in a new run")
        assertFalse(state.pendingActiveReborn)
    }

    private fun Enemy.stack(element: StatusElement, count: Int) {
        status[StatusCell.STACKS + element.ordinal] = count.toFloat()
        status[StatusCell.GENERATION + element.ordinal] = 0f
    }
}
