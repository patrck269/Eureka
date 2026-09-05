package org.valkyrienskies.eureka.math

import org.joml.Vector3d
import org.joml.Vector3dc

/**
 * Pure stabilization kinematics used by [org.valkyrienskies.eureka.ship.stabilize].
 *
 * When a ship is inverted, `shipUp × worldUp` is a near-zero vector; calling
 * [Vector3d.normalize] on it produced NaN torque and frozen/exploding ships.
 */
object StabilizeMath {

    /**
     * Ideal angular acceleration (world) that rotates [shipUpWorld] onto [worldUp],
     * minus the components of [omega] that should be damped.
     *
     * @param dampYaw when true, also damp omega.y (unmanned stabilize). When false,
     *   yaw rate is left alone so a helmsman can still turn.
     */
    fun idealAngularAcceleration(
        shipUpWorld: Vector3dc,
        worldUp: Vector3dc,
        omega: Vector3dc,
        dampYaw: Boolean
    ): Vector3d {
        val ideal = Vector3d()
        val angle = shipUpWorld.angle(worldUp)
        if (angle > ANGLE_EPS) {
            val axis = shipUpWorld.cross(worldUp, Vector3d())
            if (axis.lengthSquared() < AXIS_EPS_SQ) {
                // Parallel but opposite: cross product vanishes. Pick a stable perpendicular.
                val helper = if (kotlin.math.abs(shipUpWorld.x()) < 0.9) {
                    Vector3d(1.0, 0.0, 0.0)
                } else {
                    Vector3d(0.0, 0.0, 1.0)
                }
                shipUpWorld.cross(helper, axis)
            }
            if (axis.lengthSquared() > AXIS_EPS_SQ) {
                axis.normalize()
                ideal.add(axis.mul(angle, axis))
            }
        }

        ideal.sub(
            omega.x(),
            if (!dampYaw) 0.0 else omega.y(),
            omega.z()
        )
        return ideal
    }

    fun isFinite(v: Vector3dc): Boolean {
        return v.x().isFinite() && v.y().isFinite() && v.z().isFinite()
    }

    private const val ANGLE_EPS = 0.01
    private const val AXIS_EPS_SQ = 1.0e-12
}
