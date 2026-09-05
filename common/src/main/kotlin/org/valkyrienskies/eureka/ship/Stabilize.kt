package org.valkyrienskies.eureka.ship

import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.math.StabilizeMath
import kotlin.math.atan
import kotlin.math.max

fun stabilize(
    ship: PhysShip,
    omega: Vector3dc,
    vel: Vector3dc,
    forces: PhysShip,
    linear: Boolean,
    yaw: Boolean
) {
    val shipUp = Vector3d(0.0, 1.0, 0.0)
    val worldUp = Vector3d(0.0, 1.0, 0.0)
    ship.transform.shipToWorldRotation.transform(shipUp)

    val idealAngularAcceleration = StabilizeMath.idealAngularAcceleration(
        shipUp,
        worldUp,
        omega,
        dampYaw = yaw
    )
    if (!StabilizeMath.isFinite(idealAngularAcceleration)) {
        return
    }

    val stabilizationTorque = ship.transform.shipToWorldRotation.transform(
        ship.momentOfInertia.transform(
            ship.transform.shipToWorldRotation.transformInverse(idealAngularAcceleration)
        )
    )

    val speed = ship.velocity.length()

    stabilizationTorque.mul(EurekaConfig.SERVER.stabilizationTorqueConstant / max(1.0, speed * speed * EurekaConfig.SERVER.scaledInstability / ship.mass + speed * EurekaConfig.SERVER.unscaledInstability))
    forces.applyInvariantTorque(stabilizationTorque)

    if (linear) {
        val idealVelocity = Vector3d(vel).negate()
        idealVelocity.y = 0.0

        // ideally this should work the same way as input is scaled
        val s = EurekaConfig.SERVER.linearStabilizeMaxAntiVelocity * (1 - 1 / smoothingATanMax(EurekaConfig.SERVER.linearMaxMass, ship.mass * EurekaConfig.SERVER.linearMassScaling + 1.0)) / 10.0

        if (idealVelocity.lengthSquared() > s * s)
            idealVelocity.normalize(s)

        idealVelocity.mul(ship.mass * (10 - EurekaConfig.SERVER.antiVelocityMassRelevance))
        forces.applyInvariantForce(idealVelocity)
    }
}

private fun smoothingATan(smoothing: Double, x: Double): Double = atan(x * smoothing) / smoothing
private fun smoothingATanMax(max: Double, x: Double): Double = smoothingATan(1 / (max * 0.638), x)
