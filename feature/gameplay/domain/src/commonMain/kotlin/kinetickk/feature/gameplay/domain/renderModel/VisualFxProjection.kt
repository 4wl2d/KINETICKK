// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.renderModel

import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.immutableListOf

/** Immutable Interaction-owned visual snapshot attached after the stamped Game read. */
data class VisualFxProjection(
    val particles: ImmutableList<ParticleProjection>,
    val motionEchoes: ImmutableList<MotionEchoProjection>,
    val shockwaves: ImmutableList<ShockwaveProjection>,
    val damageNumbers: ImmutableList<DamageNumberProjection>,
    val weaponArcs: ImmutableList<WeaponArcProjection>,
) {
    companion object {
        val EMPTY = VisualFxProjection(
            particles = immutableListOf(),
            motionEchoes = immutableListOf(),
            shockwaves = immutableListOf(),
            damageNumbers = immutableListOf(),
            weaponArcs = immutableListOf(),
        )
    }
}
