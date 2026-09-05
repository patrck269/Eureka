package org.valkyrienskies.eureka.math

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class StabilizeMathTest {

    @Test
    fun invertedShipProducesFinitePerpendicularAcceleration() {
        val shipUp = Vector3d(0.0, -1.0, 0.0)
        val worldUp = Vector3d(0.0, 1.0, 0.0)
        val omega = Vector3d(0.0, 0.0, 0.0)
        val acc = StabilizeMath.idealAngularAcceleration(shipUp, worldUp, omega, dampYaw = true)
        assertTrue(StabilizeMath.isFinite(acc), "inverted stabilize produced $acc")
        val len = acc.length()
        assertTrue(len > 1.0, "expected a real righting acceleration, got $acc")
        assertEquals(0.0, acc.dot(shipUp), 1e-6)
    }

    @Test
    fun alreadyUprightDampsOmegaWithoutNaN() {
        val shipUp = Vector3d(0.0, 1.0, 0.0)
        val worldUp = Vector3d(0.0, 1.0, 0.0)
        val omega = Vector3d(0.4, 0.2, -0.3)
        val acc = StabilizeMath.idealAngularAcceleration(shipUp, worldUp, omega, dampYaw = true)
        assertTrue(StabilizeMath.isFinite(acc))
        assertEquals(-0.4, acc.x, 1e-9)
        assertEquals(-0.2, acc.y, 1e-9)
        assertEquals(0.3, acc.z, 1e-9)
    }

    @Test
    fun slightTiltAxisIsFiniteAndInTheRightHemisphere() {
        val shipUp = Vector3d(0.1, 1.0, 0.0).normalize()
        val worldUp = Vector3d(0.0, 1.0, 0.0)
        val acc = StabilizeMath.idealAngularAcceleration(shipUp, worldUp, Vector3d(), dampYaw = false)
        assertTrue(StabilizeMath.isFinite(acc))
        assertTrue(abs(acc.z) > 0.05)
        assertTrue(sqrt(acc.x * acc.x + acc.y * acc.y + acc.z * acc.z) < 1.0)
    }
}
