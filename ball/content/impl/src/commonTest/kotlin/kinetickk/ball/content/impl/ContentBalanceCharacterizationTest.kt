// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentBalanceCharacterizationTest {
    private val catalog = createContentCatalog()
    private val ui = catalog.uiCatalog()

    @Test
    fun itemCatalogCardinalityOrderingAndContentAreGolden() {
        val items = ui.items

        assertEquals(TERRAIN_060_END, items.size)
        assertEquals((0 until items.size).toList(), items.map(ItemDefinition::id))
        // The items shipped through 0.4.0 keep their own pin: a release only appends to the catalog. The pin also hashes
        // every ItemEffect, so it moved once when 0.6.0 appended GRIP and SLING (68d4572118b9586d -> 25c0be545c5a2647).
        val shipped = items.subList(0, ITEMS_THROUGH_040)
        assertEquals(
            "25c0be545c5a2647",
            goldenFingerprint {
                add("item-effects")
                ItemEffect.entries.forEach { effect ->
                    add(effect.name)
                    add(effect.displayLabel)
                    add(effect.unit.name)
                }
                add("item-rarities")
                ItemRarity.entries.forEach { rarity ->
                    add(rarity.name)
                    add(rarity.displayLabel)
                    add(rarity.rank)
                }
                add("items")
                add(shipped.size)
                shipped.forEach(::addItem)
            },
        )
        // The 0.5.0 items, appended from id 173 and pinned on their own: always exactly ids 173..268. The pin moved once
        // when 0.6.0 retuned seven of them back over the 5 % perceptible floor under the new flight law
        // (6ae3869a3317ee94 -> 17777e466f80fc29).
        val appended = items.subList(ITEMS_THROUGH_040, EXPANSION_050_END)
        assertEquals(
            "17777e466f80fc29",
            goldenFingerprint {
                add("items-0.5.0")
                add(appended.size)
                appended.forEach(::addItem)
            },
        )
        // The active items (ids 269..286) with their effects, the offer factors that weight them and every
        // character's lane.
        val actives = items.subList(EXPANSION_050_END, ACTIVES_END)
        assertEquals(
            "a73030c02a213d44",
            goldenFingerprint {
                add("actives-0.5.0")
                add(actives.size)
                actives.forEach { item ->
                    addItem(item)
                    add(item.active?.name ?: "-")
                }
                with(ui.build.policy.offerLanes) {
                    add(activeWeight)
                    add(characterActiveWeight)
                    add(ownedActiveWeight)
                }
                ui.coreShapes.forEach { shape ->
                    add(shape.id.name)
                    shape.activeLane.forEach { add(it.name) }
                }
            },
        )
        // The 0.6.0 terrain items, appended after the actives and pinned on their own: always exactly ids 287..301
        // (re-pinned when Glass Harvest moved to Reborn 1, off the rank-0 Data ladder).
        val terrain = items.subList(ACTIVES_END, TERRAIN_060_END)
        assertEquals(
            "7199c257d9d5f681",
            goldenFingerprint {
                add("items-0.6.0-terrain")
                add(terrain.size)
                terrain.forEach(::addItem)
            },
        )
    }

    @Test
    fun buildCatalogTuningIsGolden() {
        val build = ui.build

        assertEquals(
            "90af4a2ca532d113",
            goldenFingerprint {
                build.elements.forEach { element ->
                    add(element.element.name)
                    add(element.statusDescription)
                    add(element.breakDescription)
                    add(element.breakThreshold)
                    add(element.decaySeconds)
                    add(element.perStack)
                    add(element.breakPower)
                    add(element.breakRadius)
                    add(element.breakFloor)
                }
                build.reactions.forEach { reaction ->
                    add(reaction.id.name)
                    add(reaction.name)
                    add(reaction.description)
                    add(reaction.power)
                    add(reaction.radius)
                    add(reaction.residue?.name ?: "-")
                    add(reaction.residueStacks)
                    reaction.values.forEach(::add)
                    add(reaction.verb)
                    add(reaction.cue)
                    add(reaction.keystone)
                    add(reaction.keystoneDescription)
                }
                build.resonances.forEach { resonance ->
                    add(resonance.tag.name)
                    add(resonance.description)
                    resonance.tiers.forEach(::add)
                }
                build.affinityTiers.forEach { add(it.description) }
                build.pairings.forEach { pairing ->
                    add(pairing.id.name)
                    add(pairing.first.name)
                    add(pairing.second.name)
                    add(pairing.kind.name)
                    add(pairing.name)
                    add(pairing.description)
                }
                with(build.policy) {
                    resonanceThresholds.forEach(::add)
                    affinityThresholds.forEach(::add)
                    stackMagnitudes.forEach(::add)
                    listOf(
                        rebornMagnitude, rebornChancePerTier, maximumRebornChance, attunementBonus, severanceSeconds,
                        harmonyScale, dissonanceScale, amplificationSoftCap, criticalMultiplierCap, vulnerabilityCap,
                        overdriveGainCap, reactionCooldownSeconds, reactionPairCooldownSeconds,
                    ).forEach(::add)
                    listOf(breaksPerSecond, reactionsPerSecond, maximumFields, masteredTagPoints).forEach(::add)
                }
            },
        )
    }

    @Test
    fun weaponAndMatrixEconomyPoliciesAreGolden() {
        assertEquals(
            listOf(
                WeaponId.FLUX_WAKE to 0,
                WeaponId.MORNINGSTAR to 25,
                WeaponId.PHASE_LATTICE to 55,
                WeaponId.NULL_LANCE to 95,
                WeaponId.GRAVITY_MINES to 145,
                WeaponId.ION_SWARM to 215,
                WeaponId.RIFT_BLADES to 305,
                WeaponId.ARC_COIL to 430,
                WeaponId.QUASAR_CANNON to 610,
                WeaponId.ENTROPY_FIELD to 860,
                WeaponId.SINGULARITY_SPEAR to 1_200,
                WeaponId.PRISM_RELAY to 1_650,
                WeaponId.GYRE_DISC to 120,
                WeaponId.SCATTER_BLOOM to 260,
                WeaponId.TETHER_ARC to 900,
                WeaponId.SENTRY_GRID to 380,
                WeaponId.HUNTER_SALVO to 1_300,
                WeaponId.CONCUSSION_ENGINE to 520,
                WeaponId.GLACIER_BELL to 700,
                WeaponId.EVENT_LENS to 1_100,
                WeaponId.SOLAR_SWEEP to 1_500,
                WeaponId.AEGIS_SHARDS to 1_800,
                WeaponId.HOLLOW_RIFT to 2_100,
                WeaponId.RESONANCE_DRUM to 2_600,
            ),
            ui.weapons.map { weapon -> weapon.id to weapon.permanentUnlockCost },
        )
        assertEquals(
            mapOf(
                WeaponId.GLACIER_BELL to 1,
                WeaponId.TETHER_ARC to 2,
                WeaponId.EVENT_LENS to 3,
                WeaponId.HUNTER_SALVO to 4,
                WeaponId.SOLAR_SWEEP to 5,
                WeaponId.AEGIS_SHARDS to 6,
                WeaponId.HOLLOW_RIFT to 7,
                WeaponId.RESONANCE_DRUM to 9,
            ),
            ui.weapons.filter { it.minimumRebirth > 0 }.associate { it.id to it.minimumRebirth },
        )
        assertEquals(
            listOf(
                MatrixNodeId.IMPACT_DRIVER to listOf(150, 400, 900),
                MatrixNodeId.WEAPON_CALIBRATION to listOf(200, 500, 1_000),
                MatrixNodeId.CRITICAL_LATTICE to listOf(600, 1_400),
                MatrixNodeId.ARMED_START to listOf(800, 2_000),
                MatrixNodeId.CATALYST_CORE to listOf(1_600),
                MatrixNodeId.OVERDRIVE_COIL to listOf(1_800),
                MatrixNodeId.REBORN_ARSENAL to listOf(4_200),
                MatrixNodeId.GLASS_ENGINE to listOf(3_500),
                MatrixNodeId.REINFORCED_HULL to listOf(120, 350, 800),
                MatrixNodeId.HARDLIGHT_RESERVE to listOf(300, 800),
                MatrixNodeId.IMPACT_DAMPERS to listOf(400, 1_000),
                MatrixNodeId.SECOND_WIND to listOf(2_200),
                MatrixNodeId.NANOFORGE to listOf(700, 1_600),
                MatrixNodeId.BASTION_PROTOCOL to listOf(2_600),
                MatrixNodeId.AEGIS_LATTICE to listOf(4_500),
                MatrixNodeId.JUGGERNAUT to listOf(3_800),
                MatrixNodeId.MAGNET_ARRAY to listOf(100, 300, 700),
                MatrixNodeId.CRYO_LOOP to listOf(250, 700),
                MatrixNodeId.VECTOR_THRUSTERS to listOf(400, 1_000),
                MatrixNodeId.NEAR_MISS to listOf(900),
                MatrixNodeId.RICOCHET_RIG to listOf(1_500),
                MatrixNodeId.OVERCLOCKED_REFLEXES to listOf(1_200, 2_600),
                MatrixNodeId.GRAVITY_SURFER to listOf(5_000),
                MatrixNodeId.PERPETUAL_DRIFT to listOf(4_000),
                MatrixNodeId.SALVAGE_PROTOCOL to listOf(150, 400, 900),
                MatrixNodeId.DATA_ARCHIVE to listOf(150, 400, 900),
                MatrixNodeId.REROLL_CACHE to listOf(500, 1_500),
                MatrixNodeId.WIDE_OFFER to listOf(1_200),
                MatrixNodeId.LUCKY_CHARM to listOf(800, 2_000),
                MatrixNodeId.ELITE_BOUNTY to listOf(2_400),
                MatrixNodeId.HEIRLOOM_VAULT to listOf(3_000),
                MatrixNodeId.PROSPECTOR to listOf(3_200),
            ),
            ui.matrixNodes.map { node -> node.id to node.costs.toList() },
        )
        assertEquals(
            "120fe0d55a06ce89",
            goldenFingerprint {
                add("weapons")
                ui.weapons.forEach { weapon ->
                    add(weapon.id.name)
                    add(weapon.name)
                    add(weapon.description)
                    add(weapon.family.name)
                    add(weapon.element?.name ?: "-")
                    add(weapon.tags.size)
                    weapon.tags.forEach { add(it.name) }
                    add(weapon.key.name)
                    add(weapon.signature.name)
                    add(weapon.scaling.name)
                    add(weapon.cadence.name)
                    add(weapon.permanentUnlockCost)
                    add(weapon.minimumRebirth)
                    weapon.masteryForms.forEach(::add)
                    add(weapon.signatureText)
                }
                add("matrix")
                ui.matrixNodes.forEach { node ->
                    add(node.id.name)
                    add(node.name)
                    add(node.description)
                    add(node.branch.name)
                    add(node.row)
                    add(node.maxRanks)
                    node.costs.forEach(::add)
                    node.prerequisites.forEach { add(it.name) }
                    add(node.minimumRebirth)
                    add(node.keystone)
                    node.effects.forEach { addMatrixEffect(it) }
                }
            },
        )
    }

    @Test
    fun rebirthLevelsZeroThroughTenAreGolden() {
        val rebirth = ui.rebirth
        val profiles = rebirth.profiles

        assertEquals(10, rebirth.maximumLevel)
        assertEquals(120, rebirth.maxActiveEnemies)
        assertEquals(0.09f, rebirth.minSpawnIntervalSeconds)
        assertEquals(24f, rebirth.minEliteIntervalSeconds)
        assertEquals((0..10).toList(), profiles.map(RebirthProfile::tier))
        assertEquals(
            "461252b1f47728a5",
            goldenFingerprint {
                profiles.forEach { profile ->
                    add(profile.tier)
                    add(profile.displayName)
                    add(profile.openingEnemyCount)
                    add(profile.enemyCapMultiplier)
                    add(profile.spawnRateMultiplier)
                    add(profile.enemyHealthMultiplier)
                    add(profile.enemySpeedMultiplier)
                    add(profile.incomingDamageMultiplier)
                    add(profile.eliteRateMultiplier)
                    add(profile.threatTimeOffsetSeconds)
                    add(profile.playerPowerMultiplier)
                    add(profile.playerIntegrityBonus)
                    add(profile.matterGainMultiplier)
                    add(profile.bonusRerolls)
                    add(profile.mutators.size)
                    profile.mutators.forEach { mutator -> add(mutator.name) }
                    add(profile.packSizeBonus)
                    add(profile.obstacleDensityMultiplier)
                }
            },
        )
    }

    @Test
    fun skillApexAndUpgradeContentIsGolden() {
        assertEquals(18, ui.skills.size)
        assertEquals(
            "3301c1592ee84a8c",
            goldenFingerprint {
                add("skills")
                ui.skills.forEach { skill ->
                    add(skill.id.name)
                    add(skill.name)
                    add(skill.summary)
                    add(skill.rankThree)
                    add(skill.element?.name ?: "-")
                    skill.tags.forEach { add(it.name) }
                    add(skill.targeting.name)
                    add(skill.reach)
                    add(skill.family.name)
                    skill.ranks.forEach { rank ->
                        add(rank.cooldownSeconds)
                        add(rank.values.size)
                        rank.values.forEach(::add)
                    }
                }
                add("apex")
                ui.coreShapes.forEach { shape ->
                    val apex = shape.apex
                    add(shape.id.name)
                    add(apex.name)
                    add(apex.description)
                    add(apex.durationSeconds)
                    add(apex.cooldownSeconds)
                    add(apex.minimumCooldownSeconds)
                    add(apex.bossStrikes)
                    add(apex.boltBudget)
                    add(apex.values.size)
                    apex.values.forEach(::add)
                    add(shape.upgradeBaseCost)
                    add(shape.upgradeSignature.perLevel)
                    add(shape.upgradeSignature.secondaryPerLevel)
                }
                add("upgrades")
                add(ui.upgrades.maxLevel)
                add(ui.upgrades.characterIntegrityPerLevel)
                add(ui.upgrades.characterPowerPerLevel)
                add(ui.upgrades.weaponPowerPerLevel)
                ui.weapons.forEach { weapon ->
                    add(weapon.id.name)
                    add(weapon.upgradeBaseCost)
                }
                add("tempo")
                add(ui.tempo.maximumLevel)
                ui.tempo.levelRequirements.forEach(::add)
                add(ui.tempo.levelIntegrity)
                add(ui.tempo.levelRepair)
                add(ui.tempo.rebirthScaleFromLevel)
                ui.rebirth.profiles.forEach { add(it.dataRequirementScale) }
            },
        )
    }

    @Test
    fun relicLimitsOrderingAndContentIdentityAreGolden() {
        val relics = ui.relics

        assertEquals(RelicId.entries.size, relics.size)
        assertEquals(true, relics.size <= ContentBounds.MAX_RELICS)
        assertEquals(4, ui.relicPolicy.maxSlots)
        assertEquals(3, ui.relicPolicy.maxRank)
        assertEquals(RelicId.entries.toList(), relics.map(RelicDefinition::id))
        assertEquals(
            "c8c02a43bec87968",
            goldenFingerprint {
                add(relics.size)
                relics.forEach { relic ->
                    add(relic.id.name)
                    add(relic.name)
                    add(relic.aspect.name)
                    relic.keys.forEach { add(it.name) }
                    add("|")
                    relic.attunedTags.forEach { add(it.name) }
                    add("|")
                    add(relic.upside)
                    add(relic.liability)
                    relic.values.forEach { value ->
                        add(value.base)
                        add(value.perRank)
                    }
                    add(relic.minimumRebirth)
                    add(relic.meldable)
                }
            },
        )
    }

    @Test
    fun runVarietyContentIsGolden() {
        // Slice variety-0.6.0: every run-variety identity, definition, policy number and notice (pinned when 0.6.0
        // shipped the run-variety content; re-pinned when the Keynote element share moved 0.8 -> 0.7).
        val variety = catalog.gameplayContent().variety
        assertEquals(
            "24a8c347d62bc2dd",
            goldenFingerprint {
                add("variety-0.6.0")
                kinetickk.ball.content.api.EliteAffix.entries.forEach { add(it.name); add(it.displayName); add(it.signalNotice) }
                kinetickk.ball.content.api.WorldEventKind.entries.forEach { add(it.name); add(it.displayName) }
                kinetickk.ball.content.api.SignalKind.entries.forEach { add(it.name); add(it.displayName) }
                kinetickk.ball.content.api.SignalOption.entries.forEach { add(it.name); add(it.kind?.name ?: "-") }

                val omens = variety.omens
                add("omens")
                add(omens.enabled)
                add(omens.offers)
                omens.definitions.forEach { omen ->
                    add(omen.id.name)
                    add(omen.name)
                    add(omen.upside)
                    add(omen.liability)
                    add(omen.gains.size)
                    omen.gains.forEach { addRunModifier(it) }
                    add(omen.costs.size)
                    omen.costs.forEach { addRunModifier(it) }
                    add(omen.minimumRank)
                    add(omen.weight)
                }

                val director = variety.director
                add("director")
                add(director.personalitiesEnabled)
                director.personalities.forEach { personality ->
                    add(personality.id.name)
                    add(personality.name)
                    add(personality.description)
                    add(personality.weight)
                    add(personality.budgetScale)
                    listOf(personality.phaseRate, personality.phaseDuration).forEach { scales ->
                        add(scales.calm)
                        add(scales.build)
                        add(scales.surge)
                        add(scales.breather)
                    }
                    with(personality.eventWeights) { listOf(single, pack, flank, swarm, wall, encircle).forEach(::add) }
                    add(personality.ambushCooldownScale)
                    add(personality.encircleCooldownScale)
                    add(personality.wallCooldownScale)
                    add(personality.packSizeBonus)
                    add(personality.typeBias.size)
                    personality.typeBias.forEach { bias ->
                        add(bias.type.name)
                        add(bias.multiplier)
                    }
                    add(personality.featuresAspect)
                    add(personality.aspectWeight)
                }
                add(director.surgeThemesEnabled)
                add(director.surgeThemeWeight)
                add(director.surgeThemeMinimumKinds)
                add(director.announceAtSeconds)
                add(director.keynoteAnnounceAtSeconds)

                with(variety.affixes) {
                    add("affixes")
                    add(enabled)
                    listOf(
                        fromThreatSeconds, baseChance, chancePerTier, shieldSeconds, shieldIntervalSeconds, firstShieldSeconds,
                        wellStrength,
                    ).forEach(::add)
                    add(linkCount)
                    add(splitFragments)
                    listOf(burstRadius, burstDelaySeconds, hasteSpeed, hasteCadence).forEach(::add)
                }
                with(variety.champions) {
                    add("champions")
                    add(enabled)
                    listOf(fromThreatSeconds, baseChance, chancePerTier, integrityScale).forEach(::add)
                    add(bonusData)
                    add(matterScale)
                }
                with(variety.keynote) {
                    add("keynote")
                    add(enabled)
                    listOf(tagShare, elementShare, eliteAspectBias, stormEliteAspectBias).forEach(::add)
                }
                with(variety.worldEvents) {
                    add("world-events")
                    add(enabled)
                    add(minPerRun)
                    add(maxPerRun)
                    listOf(extraEventChance, firstAtSeconds, lastEndSeconds, telegraphSeconds, gapSeconds).forEach(::add)
                    weights.forEach(::add)
                    with(meteor) {
                        listOf(
                            durationSeconds, intervalSeconds, armSeconds, minDistance, distanceSpread, angleSpread, radius,
                            coreDamage, enemyDamageScale, pushSpeed,
                        ).forEach(::add)
                    }
                    with(dataStorm) {
                        listOf(durationSeconds, intervalSeconds, minDistance, maxDistance, angleSpread).forEach(::add)
                        add(killBonusData)
                    }
                    with(gravity) { listOf(durationSeconds, turnRate, rampSeconds, coreAcceleration, enemyAcceleration).forEach(::add) }
                    with(ion) {
                        listOf(durationSeconds, intervalSeconds, armSeconds, minDistance, maxDistance, radius).forEach(::add)
                        add(ionStacks)
                        listOf(enemyDamageScale, coreRadius, coreDamage).forEach(::add)
                    }
                    with(solar) {
                        listOf(durationSeconds, pulseSeconds, coolingPenalty, weaponBonus, buffSeconds, sweepIntervalSeconds).forEach(::add)
                        add(sweepStacks)
                        add(sweepRadius)
                    }
                }
                with(variety.signals) {
                    add("signals")
                    add(enabled)
                    add(firstAtSeconds)
                    add(intervalSeconds)
                    add(slots)
                    add(perRun)
                    listOf(lifetimeSeconds, distance, sideAngle, touchRadius, lostDistance, trialDelaySeconds).forEach(::add)
                    weights.forEach(::add)
                    with(tuning) {
                        listOf(merchantRareCost, merchantEpicCost, merchantRerollCost).forEach(::add)
                        add(merchantRerolls)
                        listOf(
                            shrineBloodShare, shrineBloodImpact, shrineBloodWeapon, shrineWardIntegrity, shrineWardMinimumIntegrity,
                            shrineWardShield, shrineWardReduction, shrineRelicShare, gambleRerollChance,
                        ).forEach(::add)
                        add(gambleRerollWin)
                        add(gambleMatterShare)
                        add(gambleMatterMinimum)
                        listOf(
                            gambleMatterChance, gambleMatterPayout, gambleIntegrityShare, gambleIntegrityChance, crateCurse,
                            crateCurseSeconds, reliquaryCurse, reliquaryCurseSeconds, idolCurse, idolCurseSeconds,
                            derelictRepairShare, derelictCargoMatter, priceBase, priceRampSeconds,
                        ).forEach(::add)
                        add(priceStep)
                    }
                }
                add("notices")
                kinetickk.ball.content.api.RunVarietyNotices.forEach(::add)
            },
        )
    }
}

/** The item catalog's size through 0.4.0: ids 0..172 (0.5.0 appends from 173). */
private const val ITEMS_THROUGH_040 = 173

/** One past the 0.5.0 expansion (ids 173..268); the active items start here. */
private const val EXPANSION_050_END = 269

/** One past the active items (ids 269..286); the 0.6.0 terrain items start here. */
private const val ACTIVES_END = 287

/** One past the 0.6.0 terrain items (ids 287..301). */
private const val TERRAIN_060_END = 302

/** Every Matrix effect kind with its numbers; floats enter by bits, so JVM and Wasm agree. */
private fun GoldenFingerprint.addMatrixEffect(effect: MatrixEffect) {
    when (effect) {
        is MatrixEffect.Stat -> { add("stat"); add(effect.delta.effect.name); add(effect.delta.amount) }
        is MatrixEffect.DamageMultiplier -> { add("damage"); add(effect.fraction) }
        is MatrixEffect.ReactionPower -> { add("reaction"); add(effect.fraction) }
        is MatrixEffect.StatusPotency -> { add("potency"); add(effect.fraction) }
        is MatrixEffect.DashHeat -> { add("dash-heat"); add(effect.multiplier) }
        is MatrixEffect.Drag -> { add("drag"); add(effect.multiplier) }
        is MatrixEffect.StartingWeaponLevel -> { add("weapon-level"); add(effect.levels) }
        is MatrixEffect.StartingOverdrive -> { add("overdrive"); add(effect.charge) }
        is MatrixEffect.SecondWind -> { add("second-wind"); add(effect.integrityFraction); add(effect.invulnerableSeconds) }
        is MatrixEffect.ObstacleGuard -> { add("obstacle-guard"); add(effect.damageMultiplier); add(effect.lethalSpeedBonus) }
        is MatrixEffect.RicochetRig -> { add("ricochet-rig"); add(effect.restitution); add(effect.burstRadius) }
        is MatrixEffect.NearMiss -> {
            add("near-miss"); add(effect.distance); add(effect.minSpeed); add(effect.overdrive); add(effect.cooldown)
        }
        is MatrixEffect.OfferCards -> { add("offer-cards"); add(effect.everyNthOffer) }
        is MatrixEffect.RarityPity -> { add("pity"); add(effect.offers) }
        is MatrixEffect.PickupValue -> { add("pickup-value"); add(effect.fraction) }
        MatrixEffect.EliteBounty -> add("elite-bounty")
        is MatrixEffect.HeirloomOffers -> { add("heirlooms"); add(effect.extra) }
        is MatrixEffect.RebornChance -> { add("reborn"); add(effect.bonus) }
        is MatrixEffect.EnemyIntegrity -> { add("enemy-integrity"); add(effect.multiplier) }
        is MatrixEffect.ContactDamage -> { add("contact"); add(effect.multiplier) }
        is MatrixEffect.FreeDashInterval -> { add("free-dash"); add(effect.seconds) }
        is MatrixEffect.Rerolls -> { add("rerolls"); add(effect.count) }
        is MatrixEffect.DataStratum -> { add("data-stratum"); add(effect.items) }
        is MatrixEffect.DataOfferWeight -> { add("data-offer-weight"); add(effect.fraction) }
        is MatrixEffect.WeaponRunGrowth -> { add("weapon-growth"); add(effect.bonusPerProgress) }
        is MatrixEffect.ShieldRegenDelay -> { add("shield-delay"); add(effect.multiplier) }
        is MatrixEffect.RicochetPhase -> { add("ricochet-phase"); add(effect.seconds) }
    }
}

private fun GoldenFingerprint.addItem(item: ItemDefinition) {
    add(item.id)
    add(item.name)
    add(item.description)
    add(item.rarity.name)
    item.tags.forEach { add(it.name) }
    add("|")
    item.elements.forEach { add(it.name) }
    add("|")
    item.stats.forEach { stat ->
        add(stat.effect.name)
        add(stat.amount)
    }
    add("|")
    item.clauses.forEach(::addClause)
    add("|")
    item.rules.forEach(::addRule)
    add("|")
    add(item.maxStacks)
    add(item.unlockLevel)
    add(item.minimumRebirth)
    add(item.risk)
}

private fun GoldenFingerprint.addClause(clause: EffectClause) {
    add(clause.trigger.name)
    clause.conditions.forEach(::addCondition)
    add("actions")
    clause.actions.forEach(::addAction)
    add(clause.cooldownSeconds)
    add(clause.everyNth)
    add(clause.luckShortened)
}

private fun GoldenFingerprint.addCondition(condition: EffectCondition) {
    when (condition) {
        is EffectCondition.SpeedAtLeast -> { add("speed-at-least"); add(condition.speed) }
        is EffectCondition.SpeedBelow -> { add("speed-below"); add(condition.speed) }
        is EffectCondition.IntegrityBelow -> { add("integrity-below"); add(condition.fraction) }
        is EffectCondition.HeatAtLeast -> { add("heat-at-least"); add(condition.heat) }
        EffectCondition.ShieldUp -> add("shield-up")
        EffectCondition.OverdriveActive -> add("overdrive-active")
        EffectCondition.Braking -> add("braking")
        is EffectCondition.ComboAtLeast -> { add("combo-at-least"); add(condition.combo) }
        is EffectCondition.TargetHas -> { add("target-has"); add(condition.element?.name ?: "-"); add(condition.stacks) }
        EffectCondition.TargetElite -> add("target-elite")
        EffectCondition.TargetFrozen -> add("target-frozen")
        is EffectCondition.TargetBelow -> { add("target-below"); add(condition.fraction) }
        EffectCondition.SourceImpact -> add("source-impact")
        EffectCondition.SourceWeapon -> add("source-weapon")
        is EffectCondition.ElementIs -> { add("element-is"); add(condition.element.name) }
        is EffectCondition.ReactionIs -> { add("reaction-is"); add(condition.reaction.name) }
        is EffectCondition.SkillIs -> { add("skill-is"); add(condition.skill.name) }
        is EffectCondition.SkillTagged -> { add("skill-tagged"); add(condition.tag.name) }
        EffectCondition.CoreInField -> add("core-in-field")
        EffectCondition.TargetMarked -> add("target-marked")
        EffectCondition.TargetControlled -> add("target-controlled")
        EffectCondition.TargetThrown -> add("target-thrown")
        EffectCondition.ApexActive -> add("apex-active")
        is EffectCondition.SkillsCharging -> { add("skills-charging"); add(condition.count) }
        is EffectCondition.WeaponFamilyIs -> { add("weapon-family-is"); add(condition.family.name) }
        is EffectCondition.ObstacleIs -> { add("obstacle-is"); add(condition.kind.name) }
        is EffectCondition.NearObstacle -> { add("near-obstacle"); add(condition.distance) }
    }
}

private fun GoldenFingerprint.addAction(action: EffectAction) {
    when (action) {
        is EffectAction.ApplyStatus -> { add("apply"); add(action.pick.name); add(action.stacks); add(action.radius) }
        is EffectAction.Burst -> { add("burst"); add(action.radius); add(action.basis.name); add(action.scale) }
        is EffectAction.Strike -> { add("strike"); add(action.basis.name); add(action.scale) }
        is EffectAction.Chain -> {
            add("chain"); add(action.count); add(action.range); add(action.basis.name); add(action.scale)
            add(action.pick?.name ?: "-"); add(action.stacks)
        }
        is EffectAction.Shards -> { add("shards"); add(action.count); add(action.basis.name); add(action.scale); add(action.pick?.name ?: "-") }
        is EffectAction.Pull -> { add("pull"); add(action.radius); add(action.strength) }
        is EffectAction.Push -> { add("push"); add(action.radius); add(action.strength) }
        is EffectAction.Heal -> { add("heal"); add(action.amount) }
        is EffectAction.Shield -> { add("shield"); add(action.amount) }
        is EffectAction.Vent -> { add("vent"); add(action.heat) }
        is EffectAction.Overdrive -> { add("overdrive"); add(action.charge) }
        is EffectAction.Buff -> { add("buff"); add(action.stat.name); add(action.amount); add(action.seconds); add(action.maxStacks) }
        is EffectAction.ExtendCombo -> { add("extend-combo"); add(action.seconds) }
        is EffectAction.RefreshWeapon -> { add("refresh-weapon"); add(action.seconds) }
        is EffectAction.GainData -> { add("gain-data"); add(action.amount) }
        is EffectAction.Detonate -> { add("detonate"); action.elements.forEach { add(it.name) }; add(action.scale) }
        is EffectAction.Well -> {
            add("well"); add(action.radius); add(action.seconds); add(action.basis.name); add(action.scale)
            add(action.pick?.name ?: "-")
        }
        is EffectAction.RefundSkills -> { add("refund-skills"); add(action.seconds); add(action.pick.name) }
        is EffectAction.ChargeApex -> { add("charge-apex"); add(action.seconds) }
        is EffectAction.ForceReaction -> { add("force-reaction"); add(action.radius); add(action.maxTargets) }
        is EffectAction.Zone -> { add("zone"); add(action.kind.name); add(action.radius); add(action.seconds) }
        is EffectAction.EraseShots -> { add("erase-shots"); add(action.radius); add(action.maximum) }
        is EffectAction.CoreImpulse -> { add("core-impulse"); add(action.speed); add(action.direction.name) }
        is EffectAction.ApplyMark -> { add("apply-mark"); add(action.mark.name); add(action.seconds) }
        is EffectAction.Throw -> { add("throw"); add(action.speed) }
        EffectAction.PrimeDash -> add("prime-dash")
    }
}

private fun GoldenFingerprint.addRule(rule: ItemRule) {
    when (rule) {
        is ItemRule.WhileModifier -> { add("while"); addCondition(rule.condition); add(rule.stat.name); add(rule.amount) }
        is ItemRule.ElementPotency -> { add("potency"); add(rule.element.name); add(rule.amount) }
        is ItemRule.StatusDuration -> { add("duration"); add(rule.element.name); add(rule.multiplier) }
        is ItemRule.BreakThreshold -> { add("threshold"); add(rule.element.name); add(rule.reduction) }
        is ItemRule.BreakPower -> { add("break-power"); add(rule.element.name); add(rule.amount) }
        is ItemRule.BreakSpread -> { add("break-spread"); add(rule.element.name); add(rule.stacks); add(rule.radius) }
        is ItemRule.ReactionPower -> { add("reaction-power"); add(rule.amount) }
        is ItemRule.ReactionResidue -> { add("reaction-residue"); add(rule.stacks) }
        is ItemRule.ReactionRecoil -> { add("reaction-recoil"); add(rule.damage); add(rule.radius) }
        is ItemRule.AlwaysCrit -> { add("always-crit"); addCondition(rule.condition) }
        is ItemRule.CritDamageVersus -> { add("crit-damage-versus"); addCondition(rule.condition); add(rule.amount) }
        is ItemRule.DamageVersus -> { add("damage-versus"); addCondition(rule.condition); add(rule.amount) }
        is ItemRule.NonCriticalDamage -> { add("non-critical"); add(rule.amount) }
        is ItemRule.ComboDamage -> { add("combo-damage"); add(rule.perCombo); add(rule.cap) }
        is ItemRule.BonusRerolls -> { add("rerolls"); add(rule.count) }
        is ItemRule.GuaranteedCritAfter -> { add("guaranteed-crit"); add(rule.misses) }
        is ItemRule.FreeDash -> { add("free-dash"); add(rule.heat); add(rule.cooldownSeconds) }
        is ItemRule.OfferCards -> { add("offer-cards"); add(rule.count) }
        is ItemRule.Convert -> { add("convert"); add(rule.kind.name); add(rule.amount) }
        is ItemRule.ReactionKeystone -> { add("keystone"); add(rule.reaction.name) }
        is ItemRule.ReactionTuning -> { add("reaction-tuning"); add(rule.reaction?.name ?: "-"); add(rule.aspect.name); add(rule.amount) }
        is ItemRule.FieldTuning -> { add("field-tuning"); add(rule.aspect.name); add(rule.amount) }
        is ItemRule.SkillCooldown -> { add("skill-cooldown"); add(rule.element?.name ?: "-"); add(rule.amount) }
        is ItemRule.SkillPower -> { add("skill-power"); add(rule.element?.name ?: "-"); add(rule.amount) }
        ItemRule.SkillCharges -> add("skill-charges")
        is ItemRule.SkillAttunement -> { add("skill-attunement"); add(rule.pick.name) }
        is ItemRule.CatalysisRate -> { add("catalysis-rate"); add(rule.amount) }
        is ItemRule.ApexCooldown -> { add("apex-cooldown"); add(rule.amount) }
        is ItemRule.CoreLinkRadius -> { add("core-link-radius"); add(rule.bonus) }
        is ItemRule.ObstacleTuning -> { add("obstacle-tuning"); add(rule.aspect.name); add(rule.amount) }
    }
}

/** One run modifier with its kind and numbers (omens, Blessings and Pacts share the vocabulary). */
private fun GoldenFingerprint.addRunModifier(effect: RunModifierEffect) {
    when (effect) {
        is RunModifierEffect.Stat -> { add("stat"); add(effect.effect.name); add(effect.amount) }
        is RunModifierEffect.MatterGainScale -> { add("matter-gain"); add(effect.factor) }
        is RunModifierEffect.Rerolls -> { add("rerolls"); add(effect.delta) }
        is RunModifierEffect.HostileScale -> {
            add("hostile")
            listOf(effect.health, effect.speed, effect.incomingDamage, effect.spawnRate, effect.eliteRate).forEach(::add)
        }
        is RunModifierEffect.Mutator -> { add("mutator"); add(effect.mutator.name) }
        is RunModifierEffect.KeynoteFloor -> { add("keynote-floor"); add(effect.share) }
    }
}

private fun goldenFingerprint(values: GoldenFingerprint.() -> Unit): String =
    GoldenFingerprint().apply(values).finish()

private class GoldenFingerprint {
    private var hash = FNV_OFFSET_BASIS

    fun add(value: String) {
        value.forEach { character -> mix(character.code) }
        mix(FIELD_SEPARATOR)
    }

    fun add(value: Int) = add(value.toString())

    fun add(value: Float) = add(value.toBits())

    fun add(value: Boolean) = add(if (value) 1 else 0)

    fun finish(): String = hash.toString(radix = 16).padStart(16, '0')

    private fun mix(value: Int) {
        hash = (hash xor value.toULong()) * FNV_PRIME
    }

    private companion object {
        const val FIELD_SEPARATOR = 0x1F
        const val FNV_OFFSET_BASIS = 14_695_981_039_346_656_037uL
        const val FNV_PRIME = 1_099_511_628_211uL
    }
}
