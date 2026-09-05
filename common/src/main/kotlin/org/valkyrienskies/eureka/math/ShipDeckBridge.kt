package org.valkyrienskies.eureka.math

import org.joml.Matrix4dc
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc

/**
 * Minecraft / Valkyrien Skies unit and carry policy for [ShipDeckLanding].
 *
 * VS ship [org.valkyrienskies.core.api.ships.Ship.velocity] is blocks/second.
 * Entity [net.minecraft.world.entity.Entity.getDeltaMovement] is blocks/tick.
 * VS itself converts with `* 0.05` in `VSGameUtils.applyShipVelocity`.
 *
 * EntityDragger also transforms standing entities through prevTick worldToShip →
 * shipToWorld. Writing ship velocity into deltaMovement **and** leaving drag on
 * would carry the plane twice. This bridge picks one path: convert to tick units,
 * integrate kinematic carry, and suppress VS drag while landed.
 */
object ShipDeckBridge {

    /** Seconds per Minecraft tick; same factor VS uses in applyShipVelocity. */
    const val SECONDS_PER_TICK = 0.05

    /**
     * VS EntityDragger must not also carry a landed IA plane: we write ship
     * velocity into deltaMovement (tick units) and integrate the missing carry.
     */
    fun vsDragSuppressedForLandedPlane(): Boolean = true

    fun integrateKinematicPositionCarry(): Boolean = vsDragSuppressedForLandedPlane()

    fun toDeltaMovement(blocksPerSecond: Vector3dc): Vector3d {
        return Vector3d(blocksPerSecond).mul(SECONDS_PER_TICK)
    }

    fun toBlocksPerSecond(deltaMovement: Vector3dc): Vector3d {
        return Vector3d(deltaMovement).div(SECONDS_PER_TICK)
    }

    fun shipFrameForEntityTick(
        shipToWorld: Matrix4dc,
        worldToShip: Matrix4dc,
        rotation: Quaterniondc,
        linearVelocityBlocksPerSecond: Vector3dc,
        angularVelocityBlocksPerSecond: Vector3dc,
        comWorld: Vector3dc,
        deckYInShip: Double
    ): ShipDeckLanding.ShipFrame {
        return ShipDeckLanding.ShipFrame(
            shipToWorld = shipToWorld,
            worldToShip = worldToShip,
            rotation = rotation,
            linearVelocity = toDeltaMovement(linearVelocityBlocksPerSecond),
            angularVelocity = toDeltaMovement(angularVelocityBlocksPerSecond),
            comWorld = Vector3d(comWorld),
            deckYInShip = deckYInShip
        )
    }

    /**
     * Shipped entity-tick entry: converts VS blocks/s into deltaMovement units,
     * then runs [ShipDeckLanding.correct]. This is what the applier calls.
     */
    fun correctLandedPlaneForEntityTick(
        plane: ShipDeckLanding.PlaneState,
        shipToWorld: Matrix4dc,
        worldToShip: Matrix4dc,
        rotation: Quaterniondc,
        linearVelocityBlocksPerSecond: Vector3dc,
        angularVelocityBlocksPerSecond: Vector3dc,
        comWorld: Vector3dc,
        deckYInShip: Double,
        groundPitchDeg: Double
    ): ShipDeckLanding.Result {
        val frame = shipFrameForEntityTick(
            shipToWorld,
            worldToShip,
            rotation,
            linearVelocityBlocksPerSecond,
            angularVelocityBlocksPerSecond,
            comWorld,
            deckYInShip
        )
        return ShipDeckLanding.correct(
            plane,
            frame,
            ShipDeckLanding.Params(
                groundPitchDeg = groundPitchDeg,
                integrateShipCarryIntoPosition = integrateKinematicPositionCarry()
            )
        )
    }

    fun deltaMovementToWrite(result: ShipDeckLanding.Result): Vector3d {
        return Vector3d(result.velocity)
    }
}
