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
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import java.util.WeakHashMap
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Minecraft/VS boundary for [ShipDeckLanding]. Looks up the ship under an Immersive Aircraft
 * vehicle and applies the pure kinematics. IA is only touched through the duck-style callbacks
 * so this compiles without Immersive Aircraft on the classpath.
 */
object ShipDeckLandingApplier {

    private data class Weld(val shipId: Long, val local: Vector3d)

    private val welds = WeakHashMap<Entity, Weld>()


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
        val launched = entity.tags.contains(ShipDeckLanding.LAUNCH_TAG)
        if (!ShipDeckLanding.shouldApplyDeckGlue(occupied, launched)) {
            if (occupied || launched) {
                welds.remove(entity)
            }
            if (launched) {
                val far = entity.level().getShipsIntersecting(entity.boundingBox.inflate(2.0, 2.0, 2.0)).none()
                if (far) {
                    entity.removeTag(ShipDeckLanding.LAUNCH_TAG)
                }
            }
            return false
        }
        val level = entity.level()
        val existing = welds[entity]
        val ship = existing?.let { level.shipObjectWorld.allShips.getById(it.shipId) }
            ?: level.getShipsIntersecting(entity.boundingBox.inflate(48.0, 32.0, 48.0)).firstOrNull()
        if (ship == null) {
            welds.remove(entity)
            return false
        }

        val support = findSupportInShip(entity, ship)
        val onCatapult = support != null && ShipDeckLanding.isCatapultPad(support.blockId)
        val deckY = support?.let { ShipDeckLanding.collisionTopY(it.blockY, it.collisionMaxY) }
            ?: existing?.local?.y
            ?: return false
        val transform = ship.transform
        val shipToWorld = Matrix4d(transform.shipToWorld)
        val worldToShip = Matrix4d(transform.worldToShip)
        val rotation = Quaterniond(transform.shipToWorldRotation)
        val com = Vector3d(transform.positionInWorld)

        val weld = existing ?: Weld(
            shipId = ship.id,
            local = worldToShip.transformPosition(Vector3d(entity.x, entity.y, entity.z))
        )
        weld.local.y = ShipDeckLanding.weldSitY(deckY, onCatapult)
        if (existing == null) {
            val probe = entityToPlane(entity, roll, extraShapes, enginesIdle = true)
            val probeFrame = ShipDeckBridge.shipFrameForEntityTick(
                shipToWorld, worldToShip, rotation, ship.velocity, ship.angularVelocity, com, deckY
            )
            if (!ShipDeckLanding.shouldApplyLandingCorrection(
                    ShipDeckLanding.signedPenetration(probe, probeFrame),
                    ShipDeckLanding.UNOCCUPIED_CAPTURE
                )
            ) {
                return false
            }
        }
        welds[entity] = weld
        pinToWeld(entity, ship, weld, shipToWorld, freezeWorldVelocity = true)

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
            enginesIdle = true
        )

        val result = ShipDeckLanding.correct(
            plane,
            ShipDeckBridge.shipFrameForEntityTick(
                shipToWorld, worldToShip, rotation, ship.velocity, ship.angularVelocity, com, deckY
            ),
            ShipDeckLanding.Params(
                groundPitchDeg = groundPitchDeg,
                integrateShipCarryIntoPosition = ShipDeckLanding.shouldOverwriteWeldWithKinematicCarry()
            )
        )
        val parked = ShipDeckLanding.positionToWriteForParkedWeld(weld.local, shipToWorld)
        entity.setPos(parked.x, parked.y, parked.z)
        if (ShipDeckLanding.unoccupiedWeldFreezesWorldVelocity()) {
            entity.deltaMovement = Vec3.ZERO
        }
        entity.yRot = result.yawDeg.toFloat()
        entity.xRot = result.pitchDeg.toFloat()
        setRoll.accept(result.rollDeg.toFloat())
        entity.setOnGround(true)
        return true
    }

    /**
     * Absolute ship-space pin after VS physics. Velocity matching is first-order
     * and cannot keep a parked plane on a maneuvering >100 m/s deck.
     */
    @JvmStatic
    fun pinAllWelds() {
        val snapshot = welds.entries.toList()
        for ((entity, weld) in snapshot) {
            if (!entity.isAlive || entity.isRemoved || entity.isVehicle) {
                welds.remove(entity)
                continue
            }
            if (entity.tags.contains(ShipDeckLanding.LAUNCH_TAG)) {
                welds.remove(entity)
                continue
            }
            val ship = entity.level().shipObjectWorld.allShips.getById(weld.shipId)
            if (ship == null) {
                welds.remove(entity)
                continue
            }
            pinToWeld(entity, ship, weld, Matrix4d(ship.transform.shipToWorld), freezeWorldVelocity = false)
        }
    }

    private fun pinToWeld(
        entity: Entity,
        ship: Ship,
        weld: Weld,
        shipToWorld: Matrix4d,
        freezeWorldVelocity: Boolean
    ) {
        val parked = ShipDeckLanding.positionToWriteForParkedWeld(weld.local, shipToWorld)
        entity.setPos(parked.x, parked.y, parked.z)
        if (freezeWorldVelocity && ShipDeckLanding.unoccupiedWeldFreezesWorldVelocity()) {
            entity.deltaMovement = Vec3.ZERO
        } else {
            val com = Vector3d(ship.transform.positionInWorld)
            val r = Vector3d(parked).sub(com)
            val vTick = Vector3d(ship.velocity)
                .add(Vector3d(ship.angularVelocity).cross(r))
                .mul(ShipDeckBridge.SECONDS_PER_TICK)
            entity.deltaMovement = Vec3(vTick.x, vTick.y, vTick.z)
        }
        entity.setOnGround(true)
    }

    private fun entityToPlane(
        entity: Entity,
        roll: Float,
        extraShapes: Supplier<List<AABB>>,
        enginesIdle: Boolean
    ): ShipDeckLanding.PlaneState {
        val extras = extraShapes.get().map { aabb ->
            worldAabbToLocal(
                aabb,
                entity.x,
                entity.y,
                entity.z,
                entity.yRot.toDouble(),
                entity.xRot.toDouble(),
                roll.toDouble()
            )
        }
        return ShipDeckLanding.PlaneState(
            position = Vector3d(entity.x, entity.y, entity.z),
            velocity = entity.deltaMovement.toJOML(),
            yawDeg = entity.yRot.toDouble(),
            pitchDeg = entity.xRot.toDouble(),
            rollDeg = roll.toDouble(),
            aabbHalfWidth = entity.bbWidth / 2.0,
            aabbHeight = entity.bbHeight.toDouble(),
            extraBoxes = extras,
            enginesIdle = enginesIdle
        )
    }

    private data class Support(val blockY: Int, val collisionMaxY: Double, val blockId: String)

    private fun findSupportInShip(entity: Entity, ship: Ship): Support? {
        val shipPos = ship.worldToShip.transformPosition(Vector3d(entity.x, entity.y + 0.1, entity.z))
        val level = entity.level()
        for (dy in -2..16) {
            val bp = BlockPos.containing(shipPos.x, shipPos.y - dy, shipPos.z)
            val state = level.getBlockState(bp)
            if (state.isAir) {
                continue
            }
            val id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block).toString()
            if (ShipDeckLanding.isCatapultPad(id)) {
                return Support(bp.y, ShipDeckLanding.CATAPULT_HEIGHT, id)
            }
            val shape = state.getCollisionShape(level, bp)
            val top = ShipDeckLanding.collisionMaxYOrSkip(
                shape.isEmpty,
                if (shape.isEmpty) 0.0 else shape.max(net.minecraft.core.Direction.Axis.Y)
            ) ?: continue
            return Support(bp.y, top, id)
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
