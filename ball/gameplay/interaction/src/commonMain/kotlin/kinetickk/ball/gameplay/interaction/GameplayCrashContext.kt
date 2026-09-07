// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.gameplay.api.GameplayInteractionPulse

internal fun GameplayInteractionPulse.crashDescription(): String = when (this) {
    is GameplayInteractionPulse.FrameElapsed -> "FrameElapsed(realDeltaSeconds=$realDeltaSeconds)"
    is GameplayInteractionPulse.PointerMoved -> "PointerMoved(x=$x, y=$y, active=$active)"
    is GameplayInteractionPulse.ViewportChanged -> "ViewportChanged(width=$width, height=$height, density=$density)"
    is GameplayInteractionPulse.ChoiceSelected -> "ChoiceSelected(index=$index)"
    else -> toString()
}

/** Formats an already published immutable projection, without entering a gameplay decision. */
internal fun GameplayRenderSnapshot.crashContext(): String = buildString {
    appendLine("instance=$instanceId revision=$revision")
    val model = renderModel ?: return@buildString
    with(model) {
        appendLine("phase=$phase elapsed=$elapsed speed=${settings.simulationSpeed} fixedStep=${kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel.FIXED_STEP}")
        appendLine("settings=$settings")
        appendLine("screen=${screenWidth}x$screenHeight uiScale=$uiScale")
        appendLine("core=$coreShape position=($coreX,$coreY) velocity=($velocityX,$velocityY) camera=($cameraX,$cameraY)")
        appendLine("pointer=($pointerX,$pointerY) active=$pointerActive braking=$braking")
        appendLine("hp=$hp/$maxHp shield=$shield/$maxShield heat=$heat overheated=$overheated dashPhase=$dashPhaseTime polarity=$polarityStability")
        appendLine("level=$level data=$data/$nextLevelData keys=$keys kills=$kills combo=$combo comboTime=$comboTime")
        appendLine("runMatter=$runMatter totalMatter=$totalMatter rebirth=$rebirthLevel grace=$runGrace")
        appendLine("weapon=$weapon level=$weaponLevel power=$weaponPower effectivePower=$effectiveWeaponPower overdrive=$overdriveCharge time=$overdriveTime")
        appendLine("items=$itemStacksSnapshot relics=$equippedRelics recentItem=$recentItem")
        appendLine("mass=$mass damageMultiplier=$damageMultiplier attackSpeed=$attackSpeed crit=$critChance/$critMultiplier reduction=$damageReduction")
        appendLine("cooling=$coolingRate magnet=$magnetStrength dash=$dashImpulse/$dashHeatCost regen=$regenPerSecond pickupRadius=$pickupRadius luck=$luck")
        appendLine("dataGain=$dataGain matterGain=$matterGain comboWindow=$comboWindow overdriveGain=$overdriveGain drag=$dragCoefficient")
        appendLine("choiceType=$choiceType directed=$directedChoice rerolls=$rerollsRemaining pendingRelics=$pendingRelicChoiceCount choices=$choices")
        appendLine("characterAbility=$characterAbility pointsOfInterest=$pointsOfInterest totem=$totem")
        appendLine("enemies(${enemies.size})=$enemies")
        appendLine("projectiles(${projectiles.size})=$projectiles")
        appendLine("pickups(${pickups.size})=$pickups")
        appendLine("trail(${trail.size})=$trail")
        appendLine("weaponNodes=$weaponNodes weaponOrbitals=$weaponOrbitals")
        appendLine("message=$message messageTime=$messageTime")
    }
}
