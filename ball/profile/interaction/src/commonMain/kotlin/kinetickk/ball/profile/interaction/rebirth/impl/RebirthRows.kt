// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.formatCompactNumber
import kinetickk.foundation.common.localization.text
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One comparison row: label, current and next values and the direction of the change. */
internal class RebirthRow(val label: String, val current: String, val next: String, val change: RebirthChange)

/** Hostile modifiers for the current and next tier, from the game's tier profiles. */
internal fun rebirthHostileRows(current: RebirthProfile, next: RebirthProfile, language: AppLanguage): List<RebirthRow> = listOf(
    RebirthRow(language.text(ProfileText.OpeningHostiles), current.openingEnemyCount.toString(), next.openingEnemyCount.toString(),
        rebirthChange(current.openingEnemyCount.toFloat(), next.openingEnemyCount.toFloat())),
    multiplierRow(language.text(ProfileText.EnemyCap), current.enemyCapMultiplier, next.enemyCapMultiplier, language),
    multiplierRow(language.text(ProfileText.SpawnRate), current.spawnRateMultiplier, next.spawnRateMultiplier, language),
    multiplierRow(language.text(ProfileText.EliteRate), current.eliteRateMultiplier, next.eliteRateMultiplier, language),
    multiplierRow(language.text(ProfileText.EnemyIntegrity), current.enemyHealthMultiplier, next.enemyHealthMultiplier, language),
    multiplierRow(language.text(ProfileText.EnemySpeed), current.enemySpeedMultiplier, next.enemySpeedMultiplier, language),
    multiplierRow(language.text(ProfileText.IncomingDamage), current.incomingDamageMultiplier, next.incomingDamageMultiplier, language),
    RebirthRow(language.text(ProfileText.ThreatAdvance),
        language.text(ProfileText.Seconds, current.threatTimeOffsetSeconds.roundToInt()),
        language.text(ProfileText.Seconds, next.threatTimeOffsetSeconds.roundToInt()),
        rebirthChange(current.threatTimeOffsetSeconds, next.threatTimeOffsetSeconds)),
)

/** What the player gets for the current and next tier: the number scale its values read at, Matter and rerolls. */
internal fun rebirthCompensationRows(current: RebirthProfile, next: RebirthProfile, language: AppLanguage): List<RebirthRow> = listOf(
    RebirthRow(language.text(ProfileText.NumberScale), rebirthNumberScale(current.numberScale, language),
        rebirthNumberScale(next.numberScale, language), rebirthChange(current.numberScale.toFloat(), next.numberScale.toFloat())),
    multiplierRow(language.text(ProfileText.KineticMatter), current.matterGainMultiplier, next.matterGainMultiplier, language),
    RebirthRow(language.text(ProfileText.BonusRerolls), "+${current.bonusRerolls}", "+${next.bonusRerolls}",
        rebirthChange(current.bonusRerolls.toFloat(), next.bonusRerolls.toFloat())),
)

private fun multiplierRow(label: String, current: Float, next: Float, language: AppLanguage): RebirthRow =
    RebirthRow(label, rebirthMultiplier(current, language), rebirthMultiplier(next, language), rebirthChange(current, next))

/** The number scale a tier's values read at: `×1.50` below 10, then the compact form (`×21`, `×3.6K`). */
internal fun rebirthNumberScale(scale: Double, language: AppLanguage): String =
    if (scale < 10.0) rebirthMultiplier(scale.toFloat(), language) else "×" + formatCompactNumber(scale.roundToLong(), language)

/** `×1.10`: two decimals with the language's decimal separator. */
internal fun rebirthMultiplier(value: Float, language: AppLanguage): String {
    val hundredths = (value * 100f).roundToLong()
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return "×${hundredths / 100L}$separator${(100L + hundredths % 100L).toString().substring(1)}"
}
