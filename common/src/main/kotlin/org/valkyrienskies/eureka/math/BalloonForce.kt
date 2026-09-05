package org.valkyrienskies.eureka.math

/**
 * Balloon lift used by Eureka ship control. Extracted so a zero-balloon or
 * zero-engine-power ship cannot divide-by-zero into NaN world forces.
 */
object BalloonForce {

    fun poweredLift(
        balloons: Int,
        extraForceLinear: Double,
        maxBalloonsPerEngine: Double,
        enginePowerLinear: Double,
        forcePerBalloon: Double
    ): Double {
        if (balloons <= 0) {
            return 0.0
        }
        if (maxBalloonsPerEngine <= 0.0) {
            return balloons * forcePerBalloon
        }
        val denom = enginePowerLinear * balloons
        if (denom == 0.0 || !denom.isFinite()) {
            return 0.0
        }
        val ratio = (extraForceLinear * maxBalloonsPerEngine) / denom
        val scale = if (!ratio.isFinite()) 0.0 else ratio.coerceIn(0.0, 1.0)
        val force = balloons * forcePerBalloon * scale
        return if (force.isFinite()) force else 0.0
    }
}
