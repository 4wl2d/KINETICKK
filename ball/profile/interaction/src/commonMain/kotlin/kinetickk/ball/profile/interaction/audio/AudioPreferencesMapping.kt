// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences

/** The persisted preference slice that application audio observes. */
fun PlayerPreferences.toAudioPreferences(): AudioPreferences =
    AudioPreferences(
        soundEnabled = soundEnabled,
        musicEnabled = musicEnabled,
        masterVolume = masterVolume,
    )
