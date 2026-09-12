package org.valkyrienskies.eureka.compat.immersiveaircraft

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.eureka.math.ShipDeckBridge
import org.valkyrienskies.eureka.math.ShipDeckLanding
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.util.toJOML
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Minecraft/VS boundary for [ShipDeckLanding]. Looks up the ship under an Immersive Aircraft
 * vehicle and applies the pure kinematics. IA is only touched through the duck-style callbacks
 * so this compiles without Immersive Aircraft on the classpath.
 */
object ShipDeckLandingApplier {

    /**
     * @return true if the plane was treated as landed on a ship this tick
     * (VS EntityDragger must then be suppressed for this entity).
     */
    @JvmStatic
    @JvmOverloads
    fun apply(
        entity: Entity,
        roll: Float,
        setRoll: Consumer<Float>,
        extraShapes: Supplier<List<AABB>>,
        groundPitchDeg: Double = 4.0
    ): Boolean {
        val occupied = entity.isVehicle
        // Unoccupied planes must glue on dedicated and on the client. Occupied
        // planes stay on the controlling instance so rider input is not fought.
        if (occupied && !entity.isControlledByLocalInstance) {
            return false
        }
        val level = entity.level()
        val probe = entity.boundingBox.inflate(3.0, 2.0, 3.0)
        val ship = level.getShipsIntersecting(probe).firstOrNull() ?: return false

        val deckY = findDeckYInShip(entity, ship) ?: return false
        val transform = ship.transform
        val shipToWorld = Matrix4d(transform.shipToWorld)
        val worldToShip = Matrix4d(transform.worldToShip)
        val rotation = Quaterniond(transform.shipToWorldRotation)
        val com = Vector3d(transform.positionInWorld)

        val extras = extraShapes.get().map { aabb ->
            worldAabbToLocal(aabb, entity.x, entity.y, entity.z, entity.yRot.toDouble(), entity.xRot.toDouble(), roll.toDouble())
        }

        val plane = ShipDeckLanding.PlaneState(
            position = Vector3d(entity.x, entity.y, entity.z),
            velocity = entity.deltaMovement.toJOML(),
            yawDeg = entity.yRot.toDouble(),
            pitchDeg = entity.xRot.toDouble(),
            rollDeg = roll.toDouble(),
            aabbHalfWidth = entity.bbWidth / 2.0,
            aabbHeight = entity.bbHeight.toDouble(),
            extraBoxes = extras,
            enginesIdle = ShipDeckLanding.enginesIdleFromVehicle(entity, occupied = occupied)
        )

        val probeFrame = ShipDeckBridge.shipFrameForEntityTick(
            shipToWorld, worldToShip, rotation, ship.velocity, ship.angularVelocity, com, deckY
        )
        if (!ShipDeckLanding.shouldApplyLandingCorrection(
                ShipDeckLanding.signedPenetration(plane, probeFrame)
            )
        ) {
            return false
        }

        val result = ShipDeckBridge.correctLandedPlaneForEntityTick(
            plane = plane,
            shipToWorld = shipToWorld,
            worldToShip = worldToShip,
            rotation = rotation,
            linearVelocityBlocksPerSecond = ship.velocity,
            angularVelocityBlocksPerSecond = ship.angularVelocity,
            comWorld = com,
            deckYInShip = deckY,
            groundPitchDeg = groundPitchDeg
        )
        val delta = ShipDeckBridge.deltaMovementToWrite(result)
        entity.deltaMovement = Vec3(delta.x, delta.y, delta.z)
        if (plane.enginesIdle) {
            entity.setPos(result.position.x, result.position.y, result.position.z)
            entity.yRot = result.yawDeg.toFloat()
            entity.xRot = result.pitchDeg.toFloat()
            setRoll.accept(result.rollDeg.toFloat())
        } else {
            val dy = result.position.y - entity.y
            val dx = result.position.x - entity.x
            val dz = result.position.z - entity.z
            if (dx * dx + dy * dy + dz * dz > 1.0e-8) {
                entity.setPos(result.position.x, result.position.y, result.position.z)
            }
        }
        entity.setOnGround(result.onGround)
        return result.onGround && ShipDeckBridge.vsDragSuppressedForLandedPlane()
    }

    private fun findDeckYInShip(entity: Entity, ship: Ship): Double? {
        val shipPos = ship.worldToShip.transformPosition(Vector3d(entity.x, entity.y + 0.1, entity.z))
        val level = entity.level()
        for (dy in 0..5) {
            val bp = BlockPos.containing(shipPos.x, shipPos.y - dy, shipPos.z)
            val state = level.getBlockState(bp)
            if (!state.isAir) {
                return bp.y + 1.0
            }
        }
        return null
    }

    private fun worldAabbToLocal(
        aabb: AABB,
        x: Double,
        y: Double,
        z: Double,
        yawDeg: Double,
        pitchDeg: Double,
        rollDeg: Double
    ): ShipDeckLanding.LocalBox {
        val center = aabb.center
        val offset = Vector3d(center.x - x, center.y - y, center.z - z)
        val local = ShipDeckLanding.iaRotation(yawDeg, pitchDeg, rollDeg).invert().transform(offset)
        return ShipDeckLanding.LocalBox(
            x = local.x,
            y = local.y,
            z = local.z,
            width = aabb.xsize,
            height = aabb.ysize
        )
    }
}
