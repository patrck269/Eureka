package org.valkyrienskies.eureka.math

import org.joml.Matrix4dc
import org.joml.Vector3d
import kotlin.math.floor

/**
 * Pure world→shipyard collision transform for Immersive Vehicles (MTS).
 *
 * MTS [WrapperWorld] queries [Level.getBlockState] with world coordinates.
 * VS does not globally mixin that path, so a flying Eureka deck is air and
 * cars fall through. This is the shipped mapping the WrapperWorld mixin calls:
 * if a ship occupies the world point and the shipyard cell is solid, query
 * that shipyard block instead.
 */
object MtsShipCollision {

    data class ShipQueryFrame(
        val id: Long,
        val worldToShip: Matrix4dc,
        val shipToWorld: Matrix4dc,
        val worldMinX: Double,
        val worldMinY: Double,
        val worldMinZ: Double,
        val worldMaxX: Double,
        val worldMaxY: Double,
        val worldMaxZ: Double
    ) {
        fun containsWorld(x: Double, y: Double, z: Double): Boolean {
            return x >= worldMinX && x <= worldMaxX &&
                y >= worldMinY && y <= worldMaxY &&
                z >= worldMinZ && z <= worldMaxZ
        }
    }

    data class ShipBlockQuery(
        val shipId: Long,
        val shipX: Double,
        val shipY: Double,
        val shipZ: Double,
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int
    )

    /**
     * Map a world-space block/ground query onto a solid shipyard cell, or
     * null when the point is not on a ship (beside / empty world).
     */
    fun worldQueryToShip(
        worldX: Double,
        worldY: Double,
        worldZ: Double,
        ships: List<ShipQueryFrame>,
        isSolid: (ShipBlockQuery) -> Boolean
    ): ShipBlockQuery? {
        for (ship in ships) {
            if (!ship.containsWorld(worldX, worldY, worldZ)) {
                continue
            }
            val shipPos = ship.worldToShip.transformPosition(Vector3d(worldX, worldY, worldZ))
            val query = ShipBlockQuery(
                shipId = ship.id,
                shipX = shipPos.x,
                shipY = shipPos.y,
                shipZ = shipPos.z,
                blockX = floor(shipPos.x).toInt(),
                blockY = floor(shipPos.y).toInt(),
                blockZ = floor(shipPos.z).toInt()
            )
            if (isSolid(query)) {
                return query
            }
        }
        return null
    }

    fun redirectedBlock(
        worldX: Double,
        worldY: Double,
        worldZ: Double,
        ships: List<ShipQueryFrame>,
        isSolid: (ShipBlockQuery) -> Boolean
    ): Triple<Double, Double, Double> {
        val hit = worldQueryToShip(worldX, worldY, worldZ, ships, isSolid)
        return if (hit == null) {
            Triple(worldX, worldY, worldZ)
        } else {
            Triple(hit.shipX, hit.shipY, hit.shipZ)
        }
    }

    data class OccupiedCarry(
        val position: Vector3d,
        val motion: Vector3d,
        val delta: Vector3d
    )

    /**
     * VS EntityDragger formula: `shipToWorld * prevWorldToShip * worldPos`.
     * Occupied MTS cars cannot rely on that drag — [BuilderEntityExisting]
     * copies `entity.position` over the wrapper each baseTick — so this same
     * delta must be written into MTS physics position/motion. Not a weld:
     * ship-local position can still change (taxi).
     */
    fun occupiedDeckCarry(
        worldPos: Vector3d,
        worldMotion: Vector3d,
        prevWorldToShip: Matrix4dc,
        shipToWorld: Matrix4dc
    ): OccupiedCarry {
        val local = prevWorldToShip.transformPosition(Vector3d(worldPos))
        val carried = shipToWorld.transformPosition(local)
        val delta = Vector3d(carried).sub(worldPos)
        val carriedMotion = shipToWorld.transformDirection(
            prevWorldToShip.transformDirection(Vector3d(worldMotion))
        )
        return OccupiedCarry(
            position = carried,
            motion = carriedMotion,
            delta = delta
        )
    }

    /**
     * [occupiedDeckCarry] is not idempotent: applying the same prev/now
     * matrices twice is T∘T and doubles a translating ship's delta.
     * True when this physics frame's matrices were already applied.
     */
    fun alreadyAppliedOccupiedCarry(
        prevWorldToShip: Matrix4dc,
        shipToWorld: Matrix4dc,
        lastPrevWorldToShip: Matrix4dc?,
        lastShipToWorld: Matrix4dc?
    ): Boolean {
        if (lastPrevWorldToShip == null || lastShipToWorld == null) {
            return false
        }
        return prevWorldToShip.equals(lastPrevWorldToShip, MATRIX_EPS) &&
            shipToWorld.equals(lastShipToWorld, MATRIX_EPS)
    }

    fun occupiedDeckCarryGuarded(
        worldPos: Vector3d,
        worldMotion: Vector3d,
        prevWorldToShip: Matrix4dc,
        shipToWorld: Matrix4dc,
        lastPrevWorldToShip: Matrix4dc?,
        lastShipToWorld: Matrix4dc?
    ): OccupiedCarry {
        if (alreadyAppliedOccupiedCarry(
                prevWorldToShip,
                shipToWorld,
                lastPrevWorldToShip,
                lastShipToWorld
            )
        ) {
            return OccupiedCarry(Vector3d(worldPos), Vector3d(worldMotion), Vector3d())
        }
        return occupiedDeckCarry(worldPos, worldMotion, prevWorldToShip, shipToWorld)
    }

    private const val MATRIX_EPS = 1.0e-9
}
