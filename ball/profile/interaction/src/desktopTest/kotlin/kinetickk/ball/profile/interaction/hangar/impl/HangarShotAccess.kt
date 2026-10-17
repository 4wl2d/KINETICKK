// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.interaction.CatalogHeirlooms
import kinetickk.ball.profile.interaction.CatalogRebirthRelics
import kinetickk.ball.profile.interaction.CatalogWeapons
import kinetickk.ball.profile.interaction.hangar.api.HangarRenderModel

/** Screenshot access to the Hangar's internal content for the profile shot suite. */
internal object HangarShotAccess {
    /**
     * The roster with the shipped weapons in the signature rows: Flux Wake (starter), Morningstar and
     * Ion Swarm owned, the Rebirth gates at rank 0, and the four catalog heirloom candidates.
     */
    fun HangarProjection.model(matter: Long): HangarRenderModel = toRenderModel(defaultCoreShapeDefinitions(), matter,
        hangarWeapons(CatalogWeapons, LoadoutProfileSnapshot(PlayerEconomy(matter),
            PlayerLoadout(selected, WeaponId.FLUX_WAKE, setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR, WeaponId.ION_SWARM))),
            CatalogHeirlooms, CatalogRebirthRelics))

    @Composable
    fun Content(model: HangarRenderModel, inspected: CoreShape, textScale: Float, detail: HangarDetailProbe? = null) {
        HangarContent(HangarState(model, inspected), textScale, remember { ScrollState(0) }, detail?.holder ?: remember { HangarLayoutHolder() }) {}
    }

    /** The detail panel's geometry of the last frame (detail viewport and signature block). */
    class HangarDetailProbe {
        internal val holder = HangarLayoutHolder()
        val viewport get() = holder.layout?.detailViewport
        val signature get() = holder.layout?.signature
    }
}
