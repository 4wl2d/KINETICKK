// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.model

import kotlin.math.max


internal fun saturatedAdd(left: Long, right: Long): Long {
    if (right <= 0L) return left
    return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

fun formatRunTime(seconds: Float): String {
    val total = max(0, seconds.toInt())
    val minutes = total / 60
    val remaining = total % 60
    return minutes.toString().padStart(2, '0') + ":" + remaining.toString().padStart(2, '0')
}

internal fun enemyTypeForElapsed(elapsed: Float, roll: Float): EnemyType {
    val normalizedRoll = clamp(roll, 0f, 0.999_999f)
    return when {
        elapsed < 38f -> EnemyType.DRIFTER
        elapsed < 90f -> when {
            normalizedRoll < 0.70f -> EnemyType.DRIFTER
            else -> EnemyType.SHOOTER
        }
        elapsed < 150f -> when {
            normalizedRoll < 0.50f -> EnemyType.DRIFTER
            normalizedRoll < 0.78f -> EnemyType.SHOOTER
            else -> EnemyType.INTERCEPTOR
        }
        elapsed < 240f -> when {
            normalizedRoll < 0.34f -> EnemyType.DRIFTER
            normalizedRoll < 0.58f -> EnemyType.SHOOTER
            normalizedRoll < 0.76f -> EnemyType.CHARGER
            normalizedRoll < 0.92f -> EnemyType.INTERCEPTOR
            else -> EnemyType.WEAVER
        }
        elapsed < 360f -> when {
            normalizedRoll < 0.26f -> EnemyType.DRIFTER
            normalizedRoll < 0.46f -> EnemyType.SHOOTER
            normalizedRoll < 0.62f -> EnemyType.CHARGER
            normalizedRoll < 0.77f -> EnemyType.INTERCEPTOR
            normalizedRoll < 0.87f -> EnemyType.WEAVER
            normalizedRoll < 0.95f -> EnemyType.SPLITTER
            else -> EnemyType.WARDEN
        }
        else -> when {
            normalizedRoll < 0.22f -> EnemyType.DRIFTER
            normalizedRoll < 0.39f -> EnemyType.SHOOTER
            normalizedRoll < 0.53f -> EnemyType.CHARGER
            normalizedRoll < 0.67f -> EnemyType.INTERCEPTOR
            normalizedRoll < 0.79f -> EnemyType.WEAVER
            normalizedRoll < 0.91f -> EnemyType.SPLITTER
            else -> EnemyType.WARDEN
        }
    }
}
