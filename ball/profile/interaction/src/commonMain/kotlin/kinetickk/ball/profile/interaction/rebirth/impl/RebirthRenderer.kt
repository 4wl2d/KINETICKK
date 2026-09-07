// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.*
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

@Composable
internal fun RebirthContent(model: RebirthRenderModel, confirmationArmed: Boolean, scale: Float, onAction: (RebirthAction) -> Unit) {
    val language = LocalAppLanguage.current
    val current = model.current
    val next = model.next
    ProfilePanel(language.text(ProfileText.RebirthTitle), language.text(ProfileText.ThreatTier, current.tier, next.tier),
        scale, "profile-rebirth", onBack = { onAction(RebirthAction.Back) }) { wide ->
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TierSummary(current, language.text(ProfileText.CurrentCycle), scale, Modifier.weight(1f))
            TierSummary(next, language.text(ProfileText.NextCycle), scale, Modifier.weight(1f))
        } else {
            TierSummary(current, language.text(ProfileText.CurrentCycle), scale)
            TierSummary(next, language.text(ProfileText.NextCycle), scale)
        }
        ProfileDivider()
        ProfileLabel(language.text(ProfileText.HostileEscalation), scale, bold = true)
        RebirthStat(ProfileText.OpeningHostiles, current.openingEnemyCount.toString(), next.openingEnemyCount.toString(), scale, Orange)
        RebirthStat(ProfileText.EnemyCap, formatMultiplier(current.enemyCapMultiplier, language), formatMultiplier(next.enemyCapMultiplier, language), scale, Orange)
        RebirthStat(ProfileText.SpawnRate, formatMultiplier(current.spawnRateMultiplier, language), formatMultiplier(next.spawnRateMultiplier, language), scale, Orange)
        RebirthStat(ProfileText.EnemyIntegrity, formatMultiplier(current.enemyHealthMultiplier, language), formatMultiplier(next.enemyHealthMultiplier, language), scale, Orange)
        RebirthStat(ProfileText.EnemySpeed, formatMultiplier(current.enemySpeedMultiplier, language), formatMultiplier(next.enemySpeedMultiplier, language), scale, Orange)
        RebirthStat(ProfileText.IncomingDamage, formatMultiplier(current.incomingDamageMultiplier, language), formatMultiplier(next.incomingDamageMultiplier, language), scale, Orange)
        ProfileDivider()
        ProfileLabel(language.text(ProfileText.CycleCompensation), scale, bold = true)
        RebirthStat(ProfileText.ThreatAdvance, language.text(ProfileText.Seconds, current.threatTimeOffsetSeconds.toInt()), language.text(ProfileText.Seconds, next.threatTimeOffsetSeconds.toInt()), scale, Orange)
        RebirthStat(ProfileText.PlayerPower, formatMultiplier(current.playerPowerMultiplier, language), formatMultiplier(next.playerPowerMultiplier, language), scale, Cyan)
        RebirthStat(ProfileText.CoreIntegrity, "+${current.playerIntegrityBonus.toInt()}", "+${next.playerIntegrityBonus.toInt()}", scale, Cyan)
        RebirthStat(ProfileText.KineticMatter, formatMultiplier(current.matterGainMultiplier, language), formatMultiplier(next.matterGainMultiplier, language), scale, Cyan)
        RebirthStat(ProfileText.BonusRerolls, "+${current.bonusRerolls}", "+${next.bonusRerolls}", scale, Cyan)
        ProfileDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileLabel(language.text(ProfileText.ResetBuild), scale, Orange)
            ProfileLabel(language.text(ProfileText.Kept), scale, Muted, size = 11f)
            ProfileLabel(language.text(ProfileText.LifetimeKept), scale, Muted, size = 11f)
        }
        val action = when {
            model.isMaximumTier -> language.text(ProfileText.MaxRebirth)
            !model.canAdvance -> language.text(ProfileText.LockedRebirth)
            confirmationArmed -> language.text(ProfileText.ConfirmRebirth, next.tier)
            else -> language.text(ProfileText.ArmRebirth, current.tier, next.tier)
        }
        ProfileLabel(language.text(when {
            model.isMaximumTier -> ProfileText.AllDirectives
            !model.canAdvance -> ProfileText.DefeatArchitect
            confirmationArmed -> ProfileText.SecondPress
            else -> ProfileText.FirstPress
        }, current.tier), scale, Muted, size = 11f)
        ProfileButton(action, scale, "profile-rebirth-advance", Modifier.fillMaxWidth(),
            enabled = model.canAdvance && !model.isMaximumTier, accent = if (confirmationArmed) Red else Cyan) {
            onAction(RebirthAction.AdvanceRequested)
        }
    }
}

@Composable
private fun TierSummary(profile: RebirthProfile, label: String, scale: Float, modifier: Modifier = Modifier) {
    val language = LocalAppLanguage.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileLabel(label, scale, Muted, size = 10f)
        ProfileLabel(language.text(ProfileText.TierDirective, profile.tier, profile.directive.displayName.localizedContent(language)), scale, bold = true)
        ProfileLabel(profile.directive.description.localizedContent(language), scale, Muted, size = 11f)
    }
}

@Composable
private fun RebirthStat(label: ProfileText, current: String, next: String, scale: Float, accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileLabel(LocalAppLanguage.current.text(label), scale, Muted, size = 11f, modifier = Modifier.weight(1f))
        ProfileLabel(current, scale, Muted, size = 11f)
        ProfileLabel("→", scale, Muted, size = 11f)
        ProfileLabel(next, scale, if (current == next) White else accent, size = 11f, bold = true)
    }
}
