// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.foundation.common.input.BindableKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val MAX_PROFILE_PAYLOAD_BYTES: Int = 65_536

internal sealed interface ProfileEncodeResult {
    data class Encoded(val payload: String) : ProfileEncodeResult
    data class Rejected(val reason: ProfileSnapshotRejection) : ProfileEncodeResult
}

internal sealed interface ProfileDecodeResult {
    data class Decoded(val snapshot: ProfileSnapshot) : ProfileDecodeResult
    data class Rejected(val reason: ProfileSnapshotRejection) : ProfileDecodeResult
}

/** Strict, canonical codec; profiles predating language selection retain the Russian default. */
internal object ProfileCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
        prettyPrint = false
    }

    fun encode(snapshot: ProfileSnapshot): ProfileEncodeResult {
        val dto = try {
            snapshot.toDto()
        } catch (rejection: ProfileCodecRejection) {
            return ProfileEncodeResult.Rejected(rejection.reason)
        }
        val payload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileEncodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileEncodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }
        return when (payload.utf8Validation()) {
            Utf8Validation.Accepted -> ProfileEncodeResult.Encoded(payload)
            Utf8Validation.TooLarge -> ProfileEncodeResult.Rejected(ProfileSnapshotRejection.PAYLOAD_TOO_LARGE)
            Utf8Validation.Invalid -> ProfileEncodeResult.Rejected(ProfileSnapshotRejection.INVALID_UTF8)
        }
    }

    fun decode(payload: String): ProfileDecodeResult {
        when (payload.utf8Validation()) {
            Utf8Validation.TooLarge -> {
                return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.PAYLOAD_TOO_LARGE)
            }
            Utf8Validation.Invalid -> {
                return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.INVALID_UTF8)
            }
            Utf8Validation.Accepted -> Unit
        }

        val dto = try {
            json.decodeFromString<ProfileSnapshotDto>(payload)
        } catch (_: SerializationException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }

        val canonicalPayload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }
        if (canonicalPayload != payload) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD)
        }

        return try {
            ProfileDecodeResult.Decoded(dto.toSnapshot())
        } catch (rejection: ProfileCodecRejection) {
            ProfileDecodeResult.Rejected(rejection.reason)
        }
    }
}

/**
 * An optional preference: absence is [default]; a stored value equal to it is never written, so it
 * is refused rather than silently accepted.
 */
internal fun <T : Any> T?.storedOr(default: T): T = when (this) {
    null -> default
    default -> reject(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    else -> this
}

/**
 * Strict mapping of the rebound actions over [KeyBindings.DEFAULT]: strictly sorted by action wire id
 * (no duplicates), every id known, no record repeating its default, and the resulting map binds each
 * key at most once.
 */
internal fun List<KeyBindingDto>.toKeyBindings(): KeyBindings {
    rejectUnless(map(KeyBindingDto::action).isStrictlySorted(), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    rejectUnless(size <= GameAction.entries.size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    val keys = KeyBindings.DEFAULT.keys.toMutableList()
    forEach { record ->
        val action = record.action.gameAction()
        val key = BindableKey.fromWireId(record.key) ?: reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
        rejectUnless(key != KeyBindings.DEFAULT.key(action), ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        keys[action.ordinal] = key
    }
    rejectUnless(KeyBindings.isValid(keys), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    return KeyBindings.of(keys)
}

/**
 * Strict mapping of a Core Matrix record list: strictly sorted by wire id (no duplicates), every id
 * known and every rank within 1..MAX_MATRIX_NODE_RANKS (a zero rank is never written).
 */
internal fun MatrixProgressDto.toRanks(): List<Int> {
    rejectUnless(ranks.map(MatrixNodeRankDto::id).isStrictlySorted(), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    rejectUnless(ranks.size <= MatrixNodeId.entries.size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    val result = MutableList(MatrixNodeId.entries.size) { 0 }
    ranks.forEach { record ->
        val id = record.id.matrixNodeId()
        rejectUnless(record.rank in 1..ContentBounds.MAX_MATRIX_NODE_RANKS, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        result[id.ordinal] = record.rank
    }
    return result
}

/**
 * Character slots of `upgrades.c` (Refit) and `rebirthProgress.characterClears`: positions are
 * persisted, so this list is append-only and never reordered.
 */
internal val CHARACTER_SLOTS: List<CoreShape> = listOf(
    CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD, CoreShape.RING, CoreShape.DIAMOND, CoreShape.TESSERACT,
    CoreShape.STAR, CoreShape.SPIRAL, CoreShape.DIPOLE, CoreShape.PENTAGON, CoreShape.CRESCENT, CoreShape.HEXAGON,
)

/** Honing slots of `upgrades.w`: positions are persisted, so this list is append-only and never reordered. */
internal val WEAPON_UPGRADE_SLOTS: List<WeaponId> = listOf(
    WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR, WeaponId.PHASE_LATTICE, WeaponId.NULL_LANCE, WeaponId.GRAVITY_MINES,
    WeaponId.ION_SWARM, WeaponId.RIFT_BLADES, WeaponId.ARC_COIL, WeaponId.QUASAR_CANNON, WeaponId.ENTROPY_FIELD,
    WeaponId.SINGULARITY_SPEAR, WeaponId.PRISM_RELAY, WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM, WeaponId.TETHER_ARC,
    WeaponId.SENTRY_GRID, WeaponId.HUNTER_SALVO, WeaponId.CONCUSSION_ENGINE, WeaponId.GLACIER_BELL, WeaponId.EVENT_LENS,
    WeaponId.SOLAR_SWEEP, WeaponId.AEGIS_SHARDS, WeaponId.HOLLOW_RIFT, WeaponId.RESONANCE_DRUM,
)

private const val POSITIONAL_DIGIT_RADIX: Int = 36

/** Values (already validated to 0..35) as lowercase base-36 digits without trailing zeros. */
internal fun List<Int>.toPositionalDigits(): String =
    joinToString(separator = "") { value -> value.toString(POSITIONAL_DIGIT_RADIX) }.trimEnd('0')

/** One lowercase base-36 positional digit. */
private fun Char.positionalDigit(): Int = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'z' -> this - 'a' + 10
    else -> reject(ProfileSnapshotRejection.INVALID_DECIMAL)
}

/**
 * Strict mapping of the positional clear digits to tiers in [CoreShape] order: at most one digit per
 * character slot, each the tier plus one within 0..MAX_REBIRTH_LEVEL + 1, and no trailing zero. The
 * Profile invariant then bounds each tier by the highest clear.
 */
internal fun String.toCharacterClears(): List<Int> {
    rejectUnless(length <= CHARACTER_SLOTS.size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    rejectUnless(!endsWith('0'), ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD)
    val clears = MutableList(CoreShape.entries.size) { -1 }
    forEachIndexed { slot, digit ->
        val value = digit.positionalDigit()
        rejectUnless(value <= ContentBounds.MAX_REBIRTH_LEVEL + 1, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        clears[CHARACTER_SLOTS[slot].ordinal] = value - 1
    }
    return clears
}

/**
 * Strict mapping of the positional upgrade digits: at most one lowercase base-36 digit per slot, every
 * level within 0..MAX_UPGRADE_LEVEL, and no trailing zero (it is never written).
 */
internal fun PermanentUpgradesDto.toUpgrades(): PermanentUpgrades {
    fun <Id> String.toLevels(slots: List<Id>, size: Int, ordinal: (Id) -> Int): List<Int> {
        rejectUnless(length <= slots.size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        rejectUnless(!endsWith('0'), ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD)
        val levels = MutableList(size) { 0 }
        forEachIndexed { slot, digit ->
            val level = digit.positionalDigit()
            rejectUnless(level <= ContentBounds.MAX_UPGRADE_LEVEL, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
            levels[ordinal(slots[slot])] = level
        }
        return levels
    }
    return PermanentUpgrades(
        characterLevels = c.toLevels(CHARACTER_SLOTS, CoreShape.entries.size, CoreShape::ordinal),
        weaponLevels = w.toLevels(WEAPON_UPGRADE_SLOTS, WeaponId.entries.size, WeaponId::ordinal),
    )
}

internal fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private class ProfileCodecRejection(
    val reason: ProfileSnapshotRejection,
) : RuntimeException()

internal fun reject(reason: ProfileSnapshotRejection): Nothing = throw ProfileCodecRejection(reason)

internal fun rejectUnless(condition: Boolean, reason: ProfileSnapshotRejection) {
    if (!condition) reject(reason)
}

private enum class Utf8Validation { Accepted, TooLarge, Invalid }

private fun String.utf8Validation(): Utf8Validation {
    var byteCount = 0
    var index = 0
    while (index < length) {
        val character = this[index]
        val encodedBytes = when {
            character.code <= 0x7F -> 1
            character.code <= 0x7FF -> 2
            character.isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) {
                    return Utf8Validation.Invalid
                }
                index += 1
                4
            }
            character.isLowSurrogate() -> return Utf8Validation.Invalid
            else -> 3
        }
        if (byteCount > MAX_PROFILE_PAYLOAD_BYTES - encodedBytes) return Utf8Validation.TooLarge
        byteCount += encodedBytes
        index += 1
    }
    return Utf8Validation.Accepted
}
