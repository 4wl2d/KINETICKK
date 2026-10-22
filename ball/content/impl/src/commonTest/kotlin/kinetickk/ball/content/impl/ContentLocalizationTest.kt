// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.RuleTextContext
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.ruleLines
import kinetickk.ball.content.api.upsideText
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContentLocalizationTest {
    private val content = createContentCatalog().uiCatalog()

    @Test
    fun everyPublishedCatalogTextHasRussianAndPreservesItsEnglishSource() {
        val build = content.build
        val phrases = buildList {
            content.items.forEach { addAll(listOf(it.name, it.description)) }
            content.weapons.forEach {
                addAll(listOf(it.name, it.description, it.signatureText, it.family.displayLabel, it.scaling.displayLabel))
                addAll(it.masteryForms)
            }
            addAll(kinetickk.ball.content.api.WeaponFamily.entries.map { it.displayLabel })
            addAll(kinetickk.ball.content.api.WeaponScaling.entries.map { it.displayLabel })
            content.matrixNodes.forEach { addAll(listOf(it.name, it.description)) }
            addAll(MatrixBranch.entries.map { it.displayLabel })
            content.relics.forEach { addAll(listOf(it.name, it.upside, it.liability)) }
            content.coreShapes.forEach {
                addAll(listOf(it.displayName, it.mechanicDescription, it.unlockDescription, it.mechanicName))
                addAll(listOf(it.apex.name, it.apex.description))
            }
            content.skills.forEach { addAll(listOf(it.name, it.summary, it.rankThree)) }
            addAll(kinetickk.ball.content.api.CharacterTriggerKind.entries.map { it.displayLabel })
            content.pointsOfInterest.definitions.forEach { addAll(listOf(it.name, it.instruction)) }
            content.rebirth.profiles.forEach { add(it.displayName) }
            RebirthMutator.entries.forEach { addAll(listOf(it.displayName, it.description)) }
            build.elements.forEach { addAll(listOf(it.statusDescription, it.breakDescription)) }
            build.reactions.forEach { addAll(listOf(it.name, it.description)) }
            build.resonances.forEach { add(it.description); addAll(it.tiers) }
            build.affinityTiers.forEach { add(it.description) }
            build.pairings.forEach { addAll(listOf(it.name, it.description)) }
            addAll(content.weaponMasteries.map { it.displayLabel })
            addAll(ItemEffect.entries.map { it.displayLabel })
            addAll(ItemRarity.entries.map { it.displayLabel })
            addAll(RelicAspect.entries.map { it.displayLabel })
            addAll(RelicKey.entries.map { it.displayLabel })
            addAll(RewardFocus.entries.map { it.label })
            addAll(BuildTag.entries.map { it.displayLabel })
            addAll(BuffStat.entries.map { it.displayLabel })
            addAll(EffectTrigger.entries.map { it.displayLabel })
            StatusElement.entries.forEach { addAll(listOf(it.displayLabel, it.statusLabel, it.breakLabel)) }
        }

        phrases.forEach(::assertFullyLocalized)
        assertDistinctInRussian(content.items.map { it.name })
        assertDistinctInRussian(content.items.map { it.description })
        assertDistinctInRussian(content.relics.map { it.name })
        assertDistinctInRussian(content.weapons.map { it.name })
        assertDistinctInRussian(kinetickk.ball.content.api.WeaponFamily.entries.map { it.displayLabel })
        assertDistinctInRussian(kinetickk.ball.content.api.WeaponScaling.entries.map { it.displayLabel })
        assertDistinctInRussian(build.pairings.map { it.name })
        assertDistinctInRussian(build.reactions.map { it.name })
        assertDistinctInRussian(content.skills.map { it.name })
        assertDistinctInRussian(content.coreShapes.map { it.apex.name })
        assertDistinctInRussian(RebirthMutator.entries.map { it.displayName })
        assertDistinctInRussian(BuildTag.entries.map { it.displayLabel })
        assertDistinctInRussian(StatusElement.entries.flatMap { listOf(it.displayLabel, it.statusLabel, it.breakLabel) })
    }

    @Test
    fun relicTemplatesKeepTheirPlaceholdersInBothLanguages() {
        content.relics.forEach { relic ->
            val russian = relic.upside.localizedContent(AppLanguage.Russian)
            assertEquals(placeholders(relic.upside), placeholders(russian), "${relic.name} changes its placeholders in Russian")
            assertEquals(relic.values.indices.toSet(), placeholders(russian))
        }
    }

    @Test
    fun everyNameTagAndLabelAlsoSupportsUppercaseCanvasHeadings() {
        val labels = content.items.map { it.name } + content.weapons.map { it.name } + content.relics.map { it.name } +
            content.build.reactions.map { it.name } + content.build.pairings.map { it.name } +
            content.weaponMasteries.map { it.displayLabel } + ItemRarity.entries.map { it.displayLabel } +
            RelicAspect.entries.map { it.displayLabel } + RelicKey.entries.map { it.displayLabel } +
            BuildTag.entries.map { it.displayLabel } + StatusElement.entries.flatMap { listOf(it.displayLabel, it.breakLabel) }

        labels.forEach { source ->
            assertEquals(
                source.localizedContent(AppLanguage.Russian).uppercase(),
                source.uppercase().localizedContent(AppLanguage.Russian),
                "Uppercase heading for $source",
            )
        }
    }

    @Test
    fun everyItemRuleLineReadsInBothLanguagesForEveryStackAndAffinity() {
        val policy = content.build.policy
        val contexts = buildList {
            (1..3).forEach { stacks -> add(RuleTextContext(stackMagnitude = policy.stackMagnitude(stacks))) }
            add(RuleTextContext(stackMagnitude = policy.rebornMagnitude))
            StatusElement.entries.forEach { dominant ->
                add(
                    RuleTextContext(
                        stackMagnitude = policy.stackMagnitude(2),
                        dominant = dominant,
                        secondary = StatusElement.entries.first { it != dominant },
                        flatScale = 2.6f,
                        weaponUnit = 58f,
                        shieldCapacity = 75f,
                    ),
                )
            }
        }

        content.items.forEach { item ->
            contexts.forEach { context ->
                val english = item.ruleLines(AppLanguage.English, context)
                val russian = item.ruleLines(AppLanguage.Russian, context)
                assertTrue(english.isNotEmpty(), "${item.name} explains nothing")
                assertEquals(english.map { it.kind }, russian.map { it.kind }, "${item.name} lines differ by language")
                assertEquals(english.map { it.element }, russian.map { it.element })
                assertEquals(english.map { it.negative }, russian.map { it.negative })
                english.forEach { line -> assertDisplayText(item.name, line.text, AppLanguage.English) }
                russian.forEach { line -> assertDisplayText(item.name, line.text, AppLanguage.Russian) }
                val firstCost = english.indexOfFirst { it.kind == RuleLineKind.COST }
                if (firstCost >= 0) assertTrue(english.drop(firstCost).all { it.kind == RuleLineKind.COST }, "${item.name} costs come last")
                assertEquals(english.filter { it.kind == RuleLineKind.TRIGGER }.size, item.clauses.size)
                assertEquals(english.filter { it.kind == RuleLineKind.EFFECT }.size, item.clauses.size)
            }
            if (item.risk) {
                assertTrue(item.ruleLines(AppLanguage.English).any { it.kind == RuleLineKind.COST && it.negative }, "${item.name} shows its cost")
            }
        }
    }

    @Test
    fun everyRelicUpsideAndLiabilityReadsInBothLanguagesAtEveryRank() {
        content.relics.forEach { relic ->
            (1..content.relicPolicy.maxRank).forEach { rank ->
                assertDisplayText(relic.name, relic.upsideText(AppLanguage.English, rank), AppLanguage.English)
                assertDisplayText(relic.name, relic.upsideText(AppLanguage.Russian, rank), AppLanguage.Russian)
            }
            assertDisplayText(relic.name, relic.liabilityText(AppLanguage.English), AppLanguage.English)
            assertDisplayText(relic.name, relic.liabilityText(AppLanguage.Russian), AppLanguage.Russian)
            assertEquals(relic.liability, relic.liabilityText(AppLanguage.English))
        }
        val perpetual = content.relics.first { it.name == "Perpetual Vector" }
        assertTrue(perpetual.upsideText(AppLanguage.English, 1).contains("adds 12 % damage"))
        assertTrue(perpetual.upsideText(AppLanguage.English, 3).contains("adds 18 % damage"))
        val verdant = content.relics.first { it.name == "Verdant Rot" }
        assertTrue(verdant.upsideText(AppLanguage.English, 2).contains("heals you 0.3 per second"))
        assertTrue(verdant.upsideText(AppLanguage.Russian, 1).contains("лечит вас на 0,25 в секунду"))
    }

    @Test
    fun rewardChoicesAndBuildNotificationsTranslateTheirWholeBoundedGrammar() {
        val phrases = buildList {
            content.weapons.forEach { weapon ->
                add("Amplify ${weapon.name}")
                add("${weapon.name} mastery advanced")
                add("${weapon.name.uppercase()} SYNCHRONIZED")
                add("${weapon.name.uppercase()} // LEVEL 11")
                content.weaponMasteries.forEach { add("${weapon.name.uppercase()} // ${it.displayLabel.uppercase()}") }
            }
            content.items.forEach { item ->
                add("${item.name.uppercase()} SALVAGED")
                add("${item.name.uppercase()} ACQUIRED")
            }
            content.relics.forEach { relic ->
                add("Replace ${relic.name}")
                add("Meld ${relic.name}")
                add("Break slot 4 and bind ${relic.name} at rank 1.")
                add("${relic.name.uppercase()} // RESONANCE SALVAGED")
                add("${relic.name.uppercase()} // RANK 3")
                add("${relic.name.uppercase()} // BOUND")
                add("${relic.name.uppercase()} // SLOT 4 BOUND")
            }
            content.build.pairings.forEach { add("+ ${it.name}"); add("− ${it.name}") }
            addAll(listOf(
                "Advance the current system from level 2 to 3 immediately.",
                "Merge the duplicate resonance and advance rank 2 to 3.",
                "This resonance is already rank 3; selecting it salvages Kinetic Matter.",
                "Collapse this offering into one of the four bound Relics and raise its rank.",
                "Collapse the offering into slot 4 and advance rank 2 to 3.",
                "Slot 4 is already rank 3; salvage the excess resonance.",
                "SLOT 4 // REPLACE", "SLOT 4 // RANK 3", "LEVEL 20",
                "Impact power +0.05×", "Cooling +1.5/s", "Combo window +0.08s",
                "+4.5% Impact damage  //  +0.2/s Integrity regeneration",
                "−20% Dash power",
            ))
        }
        phrases.forEach(::assertFullyLocalized)
        assertEquals("Сила рывка: −20%", "−20% Dash power".localizedContent(AppLanguage.Russian))
    }

    @Test
    fun unknownAndAlreadyTranslatedStringsArePreservedWithoutWordSubstitution() {
        listOf("", "Unknown custom weapon", "My Ion name", "Своя реликвия", "+4.5% Unknown effect").forEach { source ->
            AppLanguage.entries.forEach { assertEquals(source, source.localizedContent(it)) }
        }
    }

    @Test
    fun allVelocityTiersEnemiesAndRunNoticesAreLocalized() {
        listOf(
            "DRIFT", "SURGE", "HYPER", "OVERDRIVE", "TRANSCENDENT", "Overdrive",
            "DRIFTER", "SHOOTER", "CHARGER", "INTERCEPTOR", "WEAVER", "WARDEN", "SPLITTER", "ELITE", "ARCHITECT",
            "SWARMLING", "BOMBER", "LANCER", "SNIPER", "AEGIS", "MINELAYER", "PHANTOM", "BROODMOTHER", "REFLECTOR", "HERALD",
            "DRIFT PHASE", "THE ARCHITECT", "THE ARCHITECT ENRAGES", "KINETIC OVERDRIVE", "DASH ONLINE", "OVERHEAT",
            "SINGULARITY CONTACT", "POLARITY FIELD STRAIN", "ARCHITECT DISMANTLED",
            "CATALOG COMPLETE // MATTER SALVAGED", "CORE FRACTURED", "ELITE KEY ACQUIRED",
            "RELIC RESONANCE", "TOTEM DETECTED", "ELITE SIGNAL", "HERALD APPROACHES",
            "TWO ANOMALIES DETECTED // CHOOSE A COURSE", "ANOMALY LOST", "ANOMALY RESOLVED",
            "SECOND WIND", "OBSTACLE COLLISION",
        ).forEach(::assertFullyLocalized)
    }

    @Test
    fun everyEnemyNameAndDescriptionIsLocalizedAndDistinct() {
        val roster = createContentCatalog().uiCatalog().enemies
        assertEquals(EnemyType.entries.toList(), roster.map { it.type })
        roster.forEach { enemy ->
            assertFullyLocalized(enemy.name)
            assertFullyLocalized(enemy.description)
        }
        assertDistinctInRussian(roster.map { it.name })
        assertDistinctInRussian(roster.map { it.description })
    }

    private fun assertFullyLocalized(source: String) {
        assertEquals(source, source.localizedContent(AppLanguage.English))
        val russian = source.localizedContent(AppLanguage.Russian)
        assertNotEquals(source, russian, "Missing Russian translation: $source")
        assertFalse(russian.any(Char::isLatinLetter), "Untranslated text: $source -> $russian")
        assertFalse(ForbiddenMarks.any { it in russian }, "Forbidden mark in: $russian")
    }

    private fun assertDistinctInRussian(sources: List<String>) {
        val russian = sources.map { it.localizedContent(AppLanguage.Russian) }
        assertEquals(sources.size, russian.toSet().size, "Russian collapses distinct names: ${russian.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    private fun assertDisplayText(owner: String, text: String, language: AppLanguage) {
        assertTrue(text.isNotBlank(), "$owner has a blank line")
        assertFalse('{' in text || '}' in text, "$owner leaves a placeholder: $text")
        assertFalse(ForbiddenMarks.any { it in text }, "$owner uses a forbidden mark: $text")
        assertEquals(text.trim(), text, "$owner line is padded: '$text'")
        if (language == AppLanguage.Russian) {
            assertFalse(text.any(Char::isLatinLetter), "$owner has Latin letters in Russian: $text")
        } else {
            assertFalse(text.any { it in 'Ѐ'..'ӿ' }, "$owner has Cyrillic in English: $text")
        }
    }

    private fun placeholders(text: String): Set<Int> =
        Regex("\\{([0-9]+)\\}").findAll(text).map { it.groupValues[1].toInt() }.toSet()

    private companion object {
        val ForbiddenMarks = listOf("·", "→", "←", "↑", "↓", "›", "‹")
    }
}

private fun Char.isLatinLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'
