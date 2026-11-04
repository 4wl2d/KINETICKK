// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * The Core's flight law: gradual forward thrust with independent turning and counter-steering authority.
 * Reaches are the centre-to-edge measure of [FatigueTuning.edgeStrainStart] along the viewport's long axis;
 * speeds derived from them follow the viewport like the obstacle bands. [LEGACY] flies the 0.5.0 law.
 */
data class SteeringTuning(
    /** Thrust toward the singularity that does not depend on its distance (dp/s²); 92 through 0.5.0. */
    val basePull: Float = 130f,
    /** Positive along-track thrust only; turning, counter-thrust and the active Brake retain full authority. */
    val forwardThrustScale: Float = 0.36f,
    /** Share of the backward along-track pull dropped unless the pointer points behind the screen centre. */
    val counterThrustGate: Float = 1f,
    /** Pointer offsets shorter than this reach carry no backward intent. */
    val counterThrustDeadzoneReach: Float = 0.15f,
    /** Turning pull at full grip: lateral × (1 + gripGain × grip stat). */
    val gripGain: Float = 0.7f,
    /** Reach whose straight steady speed gives full grip; grip grows linearly from rest. */
    val gripFullReach: Float = 0.5f,
    /** Share of the turning acceleration that becomes forward thrust. */
    val slingShare: Float = 0.1f,
    /** The sling stops this share above the straight steady speed of the pointer's offset... */
    val slingHeadroom: Float = 0.15f,
    /** ...fading linearly from this share of that stop speed. */
    val slingKneeShare: Float = 0.7f,
    /** Launch assist below the straight steady speed of the forward pointer offset (× magnetism × offset). */
    val spoolGain: Float = 0.15f,
    /** Drag rises by this share once the heading has stayed straight. */
    val straightDrag: Float = 0f,
    /** Heading turn rate (rad/s) at and above which flight counts as fully curved. */
    val straightCurvatureRate: Float = 0.6f,
    val straightRiseSeconds: Float = 1.2f,
    val straightFallSeconds: Float = 0.2f,
    /** Upper bound of the grip and sling build stats (1 = base). */
    val maximumStatMultiplier: Float = 3f,
) {
    init {
        require(basePull.isFinite() && basePull in 0f..1_000f) { "Base pull must be in 0..1000" }
        require(forwardThrustScale.isFinite() && forwardThrustScale > 0f && forwardThrustScale <= 1f) {
            "Forward thrust scale must be in (0, 1]"
        }
        require(counterThrustGate in 0f..1f) { "Counter-thrust gate must be in 0..1" }
        require(counterThrustDeadzoneReach > 0f && counterThrustDeadzoneReach < 1f) { "Counter-thrust deadzone must be in (0, 1)" }
        require(gripGain in 0f..2f) { "Grip gain must be in 0..2" }
        require(gripFullReach > 0f && gripFullReach <= 1f) { "Full-grip reach must be in (0, 1]" }
        require(slingShare in 0f..0.6f) { "The sling converts a bounded share of the turning pull" }
        require(slingHeadroom in 0f..0.5f) { "Sling headroom must be in 0..0.5" }
        require(slingKneeShare >= 0f && slingKneeShare < 1f) { "Sling knee share must be in [0, 1)" }
        require(spoolGain in 0f..2f) { "Spool gain must be in 0..2" }
        require(straightDrag in 0f..1.5f) { "Straight drag must be in 0..1.5" }
        require(straightCurvatureRate.isFinite() && straightCurvatureRate > 0f && straightCurvatureRate <= 10f) {
            "Straight curvature rate must be in (0, 10]"
        }
        require(straightRiseSeconds.isFinite() && straightRiseSeconds > 0f) { "Straightness rise time must be positive" }
        require(straightFallSeconds.isFinite() && straightFallSeconds > 0f) { "Straightness fall time must be positive" }
        require(maximumStatMultiplier in 1f..5f) { "Steering stat bound must be in 1..5" }
    }

    companion object {
        /** The 0.5.0 law: base pull 92, no gate, grip, sling, launch assist or straight-line fatigue. */
        val LEGACY: SteeringTuning = SteeringTuning(
            basePull = 92f, forwardThrustScale = 1f, counterThrustGate = 0f, gripGain = 0f,
            slingShare = 0f, spoolGain = 0f, straightDrag = 0f,
        )
    }
}
