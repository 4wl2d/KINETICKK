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
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopPanel
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage

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

    /** The Characters tab as the Workshop hosts it, inspecting [inspected] with the detail on [segment]. */
    @Composable
    fun Content(
        model: HangarRenderModel,
        inspected: CoreShape,
        textScale: Float,
        detail: HangarDetailProbe? = null,
        segment: WorkshopSegment = WorkshopSegment.UPGRADE,
        selectionAvailable: Boolean = true,
    ) {
        val language = LocalAppLanguage.current
        val holder = detail?.holder ?: remember { HangarLayoutHolder() }
        val scroll = remember { ScrollState(0) }
        WorkshopPanel(
            tab = WorkshopTab.CHARACTERS,
            count = language.text(ProfileScreensRedesignText.CountOf, model.unlockedCount, model.shapes.size),
            info = language.text(ProfileScreensRedesignText.WorkshopCharactersInfo),
            matter = model.matter,
            scale = profileTextScale(textScale),
            onBack = {},
            onAction = {},
            background = { frame -> drawHangarBackground(frame, holder.layout) },
        ) { frame, _ ->
            HangarBody(frame, HangarState(model, inspected, selectionAvailable), segment, textScale, scroll, holder) {}
        }
    }

    /** The detail panel's geometry of the last frame (detail viewport and signature block). */
    class HangarDetailProbe {
        internal val holder = HangarLayoutHolder()
        val viewport get() = holder.layout?.detailViewport
        val signature get() = holder.layout?.signature
    }
}
