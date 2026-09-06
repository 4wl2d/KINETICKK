// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.localizedContent
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ContentLocalizationTest {
    @Test
    fun everyPublishedCatalogTextHasRussianAndPreservesItsEnglishSource() {
        val content = createContentCatalog().uiCatalog()
        val phrases = buildList {
            content.items.forEach { addAll(listOf(it.name, it.description, it.family)) }
            content.weapons.forEach { addAll(listOf(it.name, it.description)); addAll(it.tags) }
            content.metaUpgrades.forEach { addAll(listOf(it.name, it.description)) }
            content.relics.forEach { addAll(listOf(it.name, it.description, it.rankEffect)) }
            content.coreShapes.forEach { addAll(listOf(it.displayName, it.mechanicDescription, it.unlockDescription)) }
            content.synergies.forEach { addAll(listOf(it.name, it.description)) }
            content.pointsOfInterest.definitions.forEach { addAll(listOf(it.name, it.instruction)) }
            content.rebirth.profiles.forEach { addAll(listOf(it.directive.displayName, it.directive.description)) }
            addAll(content.weaponMasteries.map { it.displayLabel })
            addAll(ItemEffect.entries.map { it.displayLabel })
            addAll(ItemRarity.entries.map { it.displayLabel })
            addAll(RelicAspect.entries.map { it.displayLabel })
            addAll(RewardFocus.entries.map { it.label })
        }

        phrases.forEach(::assertFullyLocalized)
        assertEquals(400, content.items.map { it.name.localizedContent(AppLanguage.Russian) }.distinct().size)
        assertEquals(400, content.items.map { it.description.localizedContent(AppLanguage.Russian) }.distinct().size)
    }

    @Test
    fun everyNameTagAndMasteryAlsoSupportsUppercaseCanvasHeadings() {
        val content = createContentCatalog().uiCatalog()
        val labels = content.items.map { it.name } + content.items.map { it.family } +
            content.weapons.map { it.name } + content.relics.map { it.name } +
            content.weaponMasteries.map { it.displayLabel } + ItemRarity.entries.map { it.displayLabel } +
            RelicAspect.entries.map { it.displayLabel }

        labels.forEach { source ->
            assertEquals(source.localizedContent(AppLanguage.Russian).uppercase(), source.uppercase().localizedContent(AppLanguage.Russian))
        }
    }

    @Test
    fun rewardChoicesAndBuildNotificationsTranslateTheirWholeBoundedGrammar() {
        val content = createContentCatalog().uiCatalog()
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
                add("${item.family.uppercase()} RESONANCE")
            }
            content.relics.forEach { relic ->
                add("Replace ${relic.name}")
                add("Meld ${relic.name}")
                add("Break slot 4 and bind ${relic.name} at rank 1.")
                add("${relic.name.uppercase()} // RESONANCE SALVAGED")
                add("${relic.name.uppercase()} // RANK 5")
                add("${relic.name.uppercase()} // BOUND")
                add("${relic.name.uppercase()} // SLOT 4 BOUND")
            }
            RelicAspect.entries.forEach { aspect ->
                add("1 different ${aspect.displayLabel} relic")
                add("2 different ${aspect.displayLabel} relics")
            }
            content.synergies.forEach { add("+ ${it.name}"); add("− ${it.name}") }
            addAll(listOf(
                "Advance the current system from level 2 to 3 immediately.",
                "Merge the duplicate resonance and advance rank 2 to 3.",
                "This resonance is already rank 5; selecting it salvages Kinetic Matter.",
                "Collapse this offering into one of the four bound Relics and raise its rank.",
                "Collapse the offering into slot 4 and advance rank 2 to 3.",
                "Slot 4 is already rank 5; salvage the excess resonance.",
                "SLOT 4 // REPLACE", "SLOT 4 // RANK 5", "LEVEL 20",
                "Impact power +0.05×", "Cooling +1.5/s", "Combo window +0.08s",
                "+4.5% Impact damage  //  +0.2/s Integrity regeneration",
            ))
        }
        phrases.forEach(::assertFullyLocalized)
    }

    @Test
    fun unknownAndAlreadyTranslatedStringsArePreservedWithoutWordSubstitution() {
        listOf("", "Unknown custom weapon", "My Ion name", "Своя реликвия").forEach { source ->
            AppLanguage.entries.forEach { assertEquals(source, source.localizedContent(it)) }
        }
    }

    @Test
    fun allVelocityTiersEnemiesAndRunNoticesAreLocalized() {
        listOf(
            "DRIFT", "SURGE", "HYPER", "OVERDRIVE", "TRANSCENDENT",
            "DRIFTER", "SHOOTER", "CHARGER", "INTERCEPTOR", "WEAVER", "WARDEN", "SPLITTER", "ELITE", "ARCHITECT",
            "DRIFT PHASE", "THE ARCHITECT", "KINETIC OVERDRIVE", "DASH ONLINE", "OVERHEAT",
            "SINGULARITY CONTACT", "POLARITY FIELD STRAIN", "ARCHITECT DISMANTLED",
            "CATALOG COMPLETE // MATTER SALVAGED", "CORE FRACTURED", "ELITE KEY ACQUIRED",
            "RELIC RESONANCE", "TOTEM DETECTED", "ELITE SIGNAL",
            "TWO ANOMALIES DETECTED // CHOOSE A COURSE", "ANOMALY LOST", "ANOMALY RESOLVED",
        ).forEach(::assertFullyLocalized)
    }

    private fun assertFullyLocalized(source: String) {
        assertEquals(source, source.localizedContent(AppLanguage.English))
        val russian = source.localizedContent(AppLanguage.Russian)
        assertNotEquals(source, russian, "Missing Russian translation: $source")
        assertFalse(russian.any { it in 'A'..'Z' || it in 'a'..'z' }, "Untranslated text: $source -> $russian")
    }
}
