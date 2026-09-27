// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.localization

import kinetickk.foundation.common.localization.TextResource

internal enum class ProfileText : TextResource {
    PreviousPage,
    NextPage,
    SettingsTitle,
    SettingsGame,
    SettingsSound,
    SettingsGraphics,
    SettingsInterface,
    RunStatisticsSide,
    LeftSide,
    RightSide,
    VolumePercent,
    Language,
    Sfx,
    Music,
    MasterVolume,
    SimulationSpeed,
    TextSize,
    ScreenShake,
    Particles,
    DamageNumbers,
    DamageNumberSize,
    DamageNumberFormat,
    DamageColorTiers,
    On,
    Off,
    Low,
    Normal,
    High,
    Small,
    Large,
    Huge,
    Compact,
    Full,
    ArmoryTitle,
    LabTitle,
    MaximumSynchrony,
    RebirthTitle,
    HostileEscalation,
    OpeningHostiles,
    EnemyCap,
    SpawnRate,
    EnemyIntegrity,
    EnemySpeed,
    IncomingDamage,
    CycleCompensation,
    ThreatAdvance,
    PlayerPower,
    CoreIntegrity,
    KineticMatter,
    BonusRerolls,
    Seconds;

    override val english: String get() = EnglishProfileText[this]
    override val russian: String get() = RussianProfileText[this]
}
