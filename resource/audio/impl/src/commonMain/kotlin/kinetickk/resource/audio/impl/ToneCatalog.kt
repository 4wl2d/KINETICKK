// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.SoundId
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave

/** The one table of effect tones: what every [SoundId] sounds like and how it ranks when effects compete. */
internal object ToneCatalog {
    private val requests: List<ToneRequest> = SoundId.entries.map(::createToneRequest)

    /** Every effect tone in [SoundId] order, for startup preparation. */
    val effectTones: List<ToneRequest>
        get() = requests

    fun request(sound: SoundId): ToneRequest = requests[sound.ordinal]

    fun priority(sound: SoundId): Int = when (sound) {
        SoundId.GAME_OVER, SoundId.VICTORY -> 100
        SoundId.HURT, SoundId.OVERHEAT -> 90
        SoundId.APEX, SoundId.LEGENDARY_ACQUIRED -> 85
        SoundId.DASH, SoundId.OVERDRIVE -> 80
        SoundId.STRUCTURE_COLLAPSE -> 75
        SoundId.WEAPON_HEAVY, SoundId.IMPACT, SoundId.SKILL -> 70
        SoundId.LEVEL_UP,
        SoundId.WEAPON_ACQUIRED,
        SoundId.RECOVERED,
        -> 60
        SoundId.WEAPON_LIGHT, SoundId.PICKUP -> 40
        // Menu confirm and refusal play alone; they rank with the click.
        SoundId.ENEMY_DESTROYED, SoundId.UI_CLICK, SoundId.UI_CONFIRM, SoundId.UI_REFUSED -> 20
    }
}

private fun createToneRequest(sound: SoundId): ToneRequest = when (sound) {
    SoundId.UI_CLICK -> ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)
    SoundId.UI_CONFIRM -> ToneRequest(490f, 0.1f, 0.16f, ToneWave.SINE)
    SoundId.UI_REFUSED -> ToneRequest(260f, 0.06f, 0.12f, ToneWave.SINE)
    SoundId.DASH -> ToneRequest(185f, 0.11f, 0.23f, ToneWave.SAW)
    SoundId.WEAPON_LIGHT -> ToneRequest(420f, 0.055f, 0.1f, ToneWave.TRIANGLE)
    SoundId.WEAPON_HEAVY -> ToneRequest(132f, 0.12f, 0.18f, ToneWave.SAW)
    SoundId.IMPACT -> ToneRequest(92f, 0.07f, 0.2f, ToneWave.SQUARE)
    SoundId.ENEMY_DESTROYED -> ToneRequest(330f, 0.06f, 0.13f, ToneWave.TRIANGLE)
    SoundId.PICKUP -> ToneRequest(710f, 0.055f, 0.13f, ToneWave.SINE)
    SoundId.LEVEL_UP -> ToneRequest(560f, 0.16f, 0.18f, ToneWave.TRIANGLE)
    SoundId.OVERHEAT -> ToneRequest(118f, 0.22f, 0.2f, ToneWave.SAW)
    SoundId.RECOVERED -> ToneRequest(445f, 0.09f, 0.13f, ToneWave.SINE)
    SoundId.HURT -> ToneRequest(76f, 0.13f, 0.22f, ToneWave.SQUARE)
    SoundId.OVERDRIVE -> ToneRequest(820f, 0.2f, 0.2f, ToneWave.SAW)
    SoundId.WEAPON_ACQUIRED -> ToneRequest(640f, 0.18f, 0.2f, ToneWave.TRIANGLE)
    SoundId.GAME_OVER -> ToneRequest(64f, 0.34f, 0.24f, ToneWave.SAW)
    SoundId.VICTORY -> ToneRequest(784f, 0.32f, 0.2f, ToneWave.TRIANGLE)
    SoundId.SKILL -> ToneRequest(480f, 0.09f, 0.16f, ToneWave.TRIANGLE)
    SoundId.APEX -> ToneRequest(988f, 0.3f, 0.22f, ToneWave.SAW)
    SoundId.STRUCTURE_COLLAPSE -> ToneRequest(58f, 0.40f, 0.22f, ToneWave.SAW)
    SoundId.LEGENDARY_ACQUIRED -> ToneRequest(1_047f, 0.42f, 0.22f, ToneWave.TRIANGLE)
}
