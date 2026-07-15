package kinetickk.model

import kotlin.math.roundToInt

enum class ParticleDensity { LOW, NORMAL, HIGH }

enum class DamageNumberSize(val scale: Float) {
    SMALL(0.8f),
    NORMAL(1f),
    LARGE(1.25f),
    HUGE(1.55f),
}

enum class DamageNumberFormat { COMPACT, FULL }

enum class DamageNumberTier { STANDARD, STRONG, POWERFUL, DEVASTATING }

internal const val DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD = 50
internal const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
internal const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

internal val DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS = listOf(
    10,
    25,
    50,
    100,
    250,
    500,
    1_000,
    2_500,
    5_000,
    10_000,
    25_000,
    50_000,
    100_000,
    250_000,
    500_000,
    1_000_000,
    2_500_000,
    5_000_000,
    10_000_000,
    25_000_000,
    50_000_000,
    100_000_000,
)

fun formatDamageNumber(amount: Long, format: DamageNumberFormat): String = when (format) {
    DamageNumberFormat.COMPACT -> abbreviateNumber(amount)
    DamageNumberFormat.FULL -> amount.toString()
}

fun damageNumberTier(
    amount: Long,
    firstThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
    critical: Boolean = false,
): DamageNumberTier {
    val threshold = firstThreshold.coerceIn(
        DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(),
        DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
    ).toLong()
    val magnitudeTier = when {
        amount >= threshold * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER -> DamageNumberTier.DEVASTATING
        amount >= threshold * DAMAGE_NUMBER_POWERFUL_MULTIPLIER -> DamageNumberTier.POWERFUL
        amount >= threshold -> DamageNumberTier.STRONG
        else -> DamageNumberTier.STANDARD
    }
    return if (critical && magnitudeTier < DamageNumberTier.POWERFUL) {
        DamageNumberTier.POWERFUL
    } else {
        magnitudeTier
    }
}

data class GameSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = 0.65f,
    val simulationSpeed: Float = 1.15f,
    val textScale: Float = 1.25f,
    val screenShake: Boolean = true,
    val particleDensity: ParticleDensity = ParticleDensity.NORMAL,
    val damageNumbers: Boolean = true,
    val damageNumberSize: DamageNumberSize = DamageNumberSize.NORMAL,
    val damageNumberFormat: DamageNumberFormat = DamageNumberFormat.COMPACT,
    val damageNumberTierThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
) {
    fun normalized(): GameSettings = copy(
        masterVolume = masterVolume.coerceIn(0f, 1f),
        simulationSpeed = simulationSpeed.coerceIn(0.75f, 2f),
        textScale = textScale.coerceIn(1f, 1.75f),
        damageNumberTierThreshold = damageNumberTierThreshold.coerceIn(
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(),
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        ),
    )
}

data class StoredProgress(
    val matter: Long = 0,
    val lifetimeMatter: Long = matter,
    val coreShapeIndex: Int = 0,
    val selectedWeaponIndex: Int = 0,
    val unlockedWeaponIndices: Set<Int> = setOf(0),
    val metaLevels: List<Int> = List(8) { 0 },
    val discoveredItemIds: Set<Int> = emptySet(),
    val settings: GameSettings = GameSettings(),
    val rebirthLevel: Int = 0,
    val highestClearedRebirth: Int = -1,
)

object ProgressCodec {
    private const val VERSION = 3
    private const val LEGACY_VERSION = 2

    fun encode(progress: StoredProgress): String {
        val normalizedSettings = progress.settings.normalized()
        val rebirthLevel = progress.rebirthLevel.coerceIn(0, RebirthProgression.MAX_LEVEL)
        val highestClearedRebirth = progress.highestClearedRebirth.coerceIn(-1, rebirthLevel)
        val weaponMask = progress.unlockedWeaponIndices
            .filter { it in 0..30 }
            .fold(0) { mask, index -> mask or (1 shl index) }
        val settingsValue = listOf(
            normalizedSettings.soundEnabled.asInt(),
            normalizedSettings.musicEnabled.asInt(),
            (normalizedSettings.masterVolume * 100f).roundToInt(),
            (normalizedSettings.simulationSpeed * 100f).roundToInt(),
            normalizedSettings.screenShake.asInt(),
            normalizedSettings.particleDensity.ordinal,
            normalizedSettings.damageNumbers.asInt(),
            (normalizedSettings.textScale * 100f).roundToInt(),
            normalizedSettings.damageNumberSize.ordinal,
            normalizedSettings.damageNumberFormat.ordinal,
            normalizedSettings.damageNumberTierThreshold,
        ).joinToString(",")
        return listOf(
            VERSION,
            progress.matter.coerceAtLeast(0L),
            progress.lifetimeMatter.coerceAtLeast(progress.matter.coerceAtLeast(0L)),
            progress.coreShapeIndex.coerceAtLeast(0),
            progress.selectedWeaponIndex.coerceAtLeast(0),
            weaponMask,
            progress.metaLevels.joinToString(",") { it.coerceAtLeast(0).toString() },
            progress.discoveredItemIds.filter { it >= 0 }.sorted().joinToString(","),
            settingsValue,
            rebirthLevel,
            highestClearedRebirth,
        ).joinToString("|")
    }

    fun decode(value: String?): StoredProgress? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val parts = value.split('|')
            val version = parts.firstOrNull()?.toIntOrNull() ?: return null
            if (version != LEGACY_VERSION && version != VERSION) return null
            if (parts.size < 9 || version == VERSION && parts.size < 11) return null
            val matter = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val lifetimeMatter = parts[2].toLongOrNull()?.coerceAtLeast(matter) ?: matter
            val coreShapeIndex = parts[3].toIntOrNull()?.coerceAtLeast(0) ?: 0
            val selectedWeaponIndex = parts[4].toIntOrNull()?.coerceAtLeast(0) ?: 0
            val weaponMask = parts[5].toIntOrNull() ?: 1
            val unlockedWeapons = (0..30).filterTo(mutableSetOf()) { index -> weaponMask and (1 shl index) != 0 }
                .ifEmpty { mutableSetOf(0) }
            val metaLevels = parts[6].split(',')
                .mapNotNull(String::toIntOrNull)
                .map { it.coerceAtLeast(0) }
                .let { levels -> List(8) { index -> levels.getOrElse(index) { 0 } } }
            val discoveries = parts[7].split(',')
                .mapNotNull(String::toIntOrNull)
                .filterTo(mutableSetOf()) { it >= 0 }
            val settingParts = parts[8].split(',')
            val settings = GameSettings(
                soundEnabled = settingParts.intAt(0, 1) != 0,
                musicEnabled = settingParts.intAt(1, 1) != 0,
                masterVolume = settingParts.intAt(2, 65) / 100f,
                simulationSpeed = settingParts.intAt(3, 115) / 100f,
                textScale = settingParts.intAt(7, 125) / 100f,
                screenShake = settingParts.intAt(4, 1) != 0,
                particleDensity = ParticleDensity.entries.getOrElse(settingParts.intAt(5, 1)) { ParticleDensity.NORMAL },
                damageNumbers = settingParts.intAt(6, 1) != 0,
                damageNumberSize = DamageNumberSize.entries.getOrElse(settingParts.intAt(8, DamageNumberSize.NORMAL.ordinal)) {
                    DamageNumberSize.NORMAL
                },
                damageNumberFormat = DamageNumberFormat.entries.getOrElse(settingParts.intAt(9, DamageNumberFormat.COMPACT.ordinal)) {
                    DamageNumberFormat.COMPACT
                },
                damageNumberTierThreshold = settingParts.intAt(10, DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD),
            ).normalized()
            val rebirthLevel = if (version >= VERSION) {
                parts[9].toIntOrNull()?.coerceIn(0, RebirthProgression.MAX_LEVEL) ?: 0
            } else {
                0
            }
            val highestClearedRebirth = if (version >= VERSION) {
                parts[10].toIntOrNull()?.coerceIn(-1, rebirthLevel) ?: -1
            } else {
                -1
            }
            StoredProgress(
                matter = matter,
                lifetimeMatter = lifetimeMatter,
                coreShapeIndex = coreShapeIndex,
                selectedWeaponIndex = selectedWeaponIndex,
                unlockedWeaponIndices = unlockedWeapons,
                metaLevels = metaLevels,
                discoveredItemIds = discoveries,
                settings = settings,
                rebirthLevel = rebirthLevel,
                highestClearedRebirth = highestClearedRebirth,
            )
        }.getOrNull()
    }

    private fun Boolean.asInt(): Int = if (this) 1 else 0
    private fun List<String>.intAt(index: Int, fallback: Int): Int = getOrNull(index)?.toIntOrNull() ?: fallback
}
