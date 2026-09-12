package org.valkyrienskies.eureka.math

import org.joml.Quaternionf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives shipped [SimplePlanesQuat.fromYawPitchRoll] against Simple Planes
 * MathUtil.toQuaternionf's half-angle formula (from 5.3.3 bytecode: +yaw,
 * -pitch, +roll). Does not grep mixin source or mock the helper.
 */
class SimplePlanesQuatTest {

    @Test
    fun matchesSimplePlanesHalfAngleFormulaOnRepresentativeAngles() {
        val samples = listOf(
            Triple(0.0, 0.0, 0.0),
            Triple(90.0, 0.0, 0.0),
            Triple(-90.0, 0.0, 0.0),
            Triple(0.0, 5.0, 0.0),
            Triple(0.0, -5.0, 12.0),
            Triple(45.0, -4.0, 8.0),
            Triple(180.0, 30.0, -15.0)
        )
        for ((yaw, pitch, roll) in samples) {
            val shipped = SimplePlanesQuat.fromYawPitchRoll(yaw, pitch, roll)
            val spec = simplePlanesToQuaternionf(yaw, pitch, roll)
            assertTrue(
                quatDotAbs(shipped, spec) > 0.999,
                "yaw=$yaw pitch=$pitch roll=$roll shipped=$shipped spec=$spec dot=${quatDotAbs(shipped, spec)}"
            )
        }
    }

    @Test
    fun yaw90MatchesFormulaAndDisagreesWithRotateYNegYaw() {
        val shipped = SimplePlanesQuat.fromYawPitchRoll(90.0, 0.0, 0.0)
        val spec = simplePlanesToQuaternionf(90.0, 0.0, 0.0)
        val wrong = Quaternionf().rotateY(Math.toRadians(-90.0).toFloat())
        assertTrue(quatDotAbs(shipped, spec) > 0.999, "shipped vs spec ${quatDotAbs(shipped, spec)}")
        assertTrue(
            quatDotAbs(spec, wrong) < 0.05,
            "wrong rotateY(-yaw) must be opposite Simple Planes at yaw=90, |dot|=${quatDotAbs(spec, wrong)}"
        )
        assertTrue(
            quatDotAbs(shipped, wrong) < 0.05,
            "shipped must not use rotateY(-yaw) at yaw=90, |dot|=${quatDotAbs(shipped, wrong)}"
        )
    }

    @Test
    fun identityIsUnit() {
        val q = SimplePlanesQuat.fromYawPitchRoll(0.0, 0.0, 0.0)
        assertEquals(0f, q.x, 1e-6f)
        assertEquals(0f, q.y, 1e-6f)
        assertEquals(0f, q.z, 1e-6f)
        assertEquals(1f, q.w, 1e-6f)
    }

    /**
     * Independent transcription of MathUtil.toQuaternionf 5.3.3 bytecode
     * (not a call to [SimplePlanesQuat]).
     */
    private fun simplePlanesToQuaternionf(yawDeg: Double, pitchDeg: Double, rollDeg: Double): Quaternionf {
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

    private fun quatDotAbs(a: Quaternionf, b: Quaternionf): Float {
        return abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w)
    }
}
