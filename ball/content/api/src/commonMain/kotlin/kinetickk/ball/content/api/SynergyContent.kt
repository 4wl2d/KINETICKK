// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

enum class SynergyId {
    VECTOR_MANEUVER, GRAVITIC_GROUPING, ION_DISCHARGE, RIFT_ECHO, PRISM_REFRACTION, ENTROPY_DECAY,
    BRAKE_COMPRESSION, LINKED_ECHO, WITNESS_TRANSFER, GHOST_MIRROR, CHARGED_ANCHOR, FRACTURE_DECAY,
}

data class SynergyDefinition(
    val id: SynergyId,
    val name: String,
    val description: String,
    val requiredAspect: RelicAspect? = null,
    val requiredRelics: ImmutableList<RelicId> = immutableListOf(),
) {
    init {
        require(name.isNotBlank() && description.isNotBlank())
        require(if (requiredAspect != null) requiredAspect != RelicAspect.SOVEREIGN && requiredRelics.isEmpty()
            else requiredRelics.size == 2 && requiredRelics[0] != requiredRelics[1])
    }
}

fun defaultSynergyDefinitions(): ImmutableList<SynergyDefinition> = immutableListOf(
    SynergyDefinition(SynergyId.VECTOR_MANEUVER, "Vector maneuver", "Real turns charge the next primary hit and ease Dash heat.", RelicAspect.VECTOR),
    SynergyDefinition(SynergyId.GRAVITIC_GROUPING, "Gravitic grouping", "Primary hits periodically pull nearby enemies into a group.", RelicAspect.GRAVITIC),
    SynergyDefinition(SynergyId.ION_DISCHARGE, "Ion discharge", "Primary hits periodically discharge into up to two other enemies.", RelicAspect.ION),
    SynergyDefinition(SynergyId.RIFT_ECHO, "Rift echo", "Primary hits leave a bounded delayed hit on their target.", RelicAspect.RIFT),
    SynergyDefinition(SynergyId.PRISM_REFRACTION, "Prism refraction", "Primary hits periodically refract into a nearby second target.", RelicAspect.PRISM),
    SynergyDefinition(SynergyId.ENTROPY_DECAY, "Entropy decay", "Primary hits apply a brief decay that cannot trigger another synergy.", RelicAspect.ENTROPY),
    SynergyDefinition(SynergyId.BRAKE_COMPRESSION, "Brake compression", "A hit charged by real braking becomes a compression wave.", requiredRelics = immutableListOf(RelicId.BRAKEPOINT_MEMORY, RelicId.MASS_ECHO)),
    SynergyDefinition(SynergyId.LINKED_ECHO, "Linked echo", "A delayed echo repeats its saved discharge link, even if a nearer target appears.", requiredRelics = immutableListOf(RelicId.VOLTAIC_FILAMENT, RelicId.ECHO_CHAMBER)),
    SynergyDefinition(SynergyId.WITNESS_TRANSFER, "Witness transfer", "The death of a marked target transfers a bounded portion of its decay once.", requiredRelics = immutableListOf(RelicId.GLASS_WITNESS, RelicId.SCAR_TISSUE)),
    SynergyDefinition(SynergyId.GHOST_MIRROR, "Ghost mirror", "A real Dash leaves a short-lived edge that refracts the next primary hit.", requiredRelics = immutableListOf(RelicId.GHOST_VECTOR, RelicId.MIRROR_CUT)),
    SynergyDefinition(SynergyId.CHARGED_ANCHOR, "Charged anchor", "An anchor holds a dead target's remaining charge until a delayed collapse.", requiredRelics = immutableListOf(RelicId.EVENTIDE_ANCHOR, RelicId.ION_DEBT)),
    SynergyDefinition(SynergyId.FRACTURE_DECAY, "Fracture decay", "A target displaced by Fracture Gate leaves a brief slowing decay trail.", requiredRelics = immutableListOf(RelicId.FRACTURE_GATE, RelicId.QUIETUS_BLOOM)),
)
