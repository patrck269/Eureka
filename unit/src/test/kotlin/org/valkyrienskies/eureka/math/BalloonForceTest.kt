package org.valkyrienskies.eureka.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BalloonForceTest {

    @Test
    fun zeroBalloonsDoesNotNaN() {
        val force = BalloonForce.poweredLift(
            balloons = 0,
            extraForceLinear = 0.0,
            maxBalloonsPerEngine = 5.0,
            enginePowerLinear = 500000.0,
            forcePerBalloon = 50000.0
        )
        assertTrue(force.isFinite())
        assertEquals(0.0, force, 0.0)
    }

    @Test
    fun zeroEnginePowerDoesNotNaN() {
        val force = BalloonForce.poweredLift(
            balloons = 4,
            extraForceLinear = 10.0,
            maxBalloonsPerEngine = 5.0,
            enginePowerLinear = 0.0,
            forcePerBalloon = 50000.0
        )
        assertTrue(force.isFinite())
        assertEquals(0.0, force, 0.0)
    }

    @Test
    fun fullyPoweredBalloonsReturnFullLift() {
        val per = 50000.0
        val force = BalloonForce.poweredLift(
            balloons = 2,
            extraForceLinear = 500000.0,
            maxBalloonsPerEngine = 5.0,
            enginePowerLinear = 500000.0,
            forcePerBalloon = per
        )
        assertEquals(2 * per, force, 1e-6)
    }
}
