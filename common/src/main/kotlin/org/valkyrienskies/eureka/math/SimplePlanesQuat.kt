package org.valkyrienskies.eureka.math

import org.joml.Quaternionf
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simple Planes [xyz.przemyk.simpleplanes.misc.MathUtil.toQuaternionf]
 * (yaw, pitch, roll in degrees). Shipped so the deck-landing mixin writes Q
 * in PlaneEntity's convention, not JOML rotateY(-yaw).rotateX(+pitch).
 *
 * Bytecode: yawRad = toRadians(yaw); pitchRad = -toRadians(pitch);
 * rollRad = toRadians(roll); then Tait-Bryan half-angles into Quaternionf(x,y,z,w).
 */
object SimplePlanesQuat {

    @JvmStatic
    fun fromYawPitchRoll(yawDeg: Double, pitchDeg: Double, rollDeg: Double): Quaternionf {
        val yaw = Math.toRadians(yawDeg)
        val pitch = -Math.toRadians(pitchDeg)
        val roll = Math.toRadians(rollDeg)
        val cy = cos(yaw * 0.5)
        val sy = sin(yaw * 0.5)
        val cp = cos(pitch * 0.5)
        val sp = sin(pitch * 0.5)
        val cr = cos(roll * 0.5)
        val sr = sin(roll * 0.5)
        val w = (cr * cp * cy + sr * sp * sy).toFloat()
        val z = (sr * cp * cy - cr * sp * sy).toFloat()
        val x = (cr * sp * cy + sr * cp * sy).toFloat()
        val y = (cr * cp * sy - sr * sp * cy).toFloat()
        return Quaternionf(x, y, z, w)
    }
}
