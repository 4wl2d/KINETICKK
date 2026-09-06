// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import kinetickk.ball.profile.api.ParticleDensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CosmicPresentationTest {
    @Test
    fun localLensHasFiniteCenterAndNeverDisplacesStarsOutsideItsRadius() {
        val singularity = Offset(400f, 300f)
        assertEquals(singularity, singularityLensPoint(singularity, singularity))
        listOf(220f, 221f, 10_000f).forEach { distance ->
            val point = singularity + Offset(distance, 0f)
            assertEquals(point, singularityLensPoint(point, singularity))
        }
        (1..219).forEach { distance ->
            val point = singularity + Offset(distance.toFloat(), 0f)
            val displaced = singularityLensPoint(point, singularity)
            assertTrue(displaced.x.isFinite() && displaced.y.isFinite())
            assertTrue((displaced - point).getDistance() <= 10.001f)
            assertEquals(displaced, singularityLensPoint(point, singularity))
        }
    }

    @Test
    fun decorativeStarWorkIsBoundedAndRespectsParticleDensity() {
        assertEquals(listOf(24, 48, 72), ParticleDensity.entries.map(::distantStarCount))
    }
}
