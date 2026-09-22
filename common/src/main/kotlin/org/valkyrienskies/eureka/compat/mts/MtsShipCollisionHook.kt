package org.valkyrienskies.eureka.compat.mts

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import org.joml.Matrix4d
import org.valkyrienskies.eureka.math.MtsShipCollision
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.world.clipIncludeShips

/**
 * Minecraft/VS boundary for [MtsShipCollision]. Called from the MTS
 * WrapperWorld mixin via reflection so that mixin does not reference Eureka
 * types (Forge ModuleClassLoader CNFDE).
 */
object MtsShipCollisionHook {

    @JvmStatic
    fun mappedBlockPos(level: Level, pos: BlockPos): BlockPos {
        val x = pos.x + 0.5
        val y = pos.y + 0.5
        val z = pos.z + 0.5
        val frames = framesAround(level, x, y, z)
        if (frames.isEmpty()) {
            return pos
        }
        val hit = MtsShipCollision.worldQueryToShip(x, y, z, frames) { query ->
            !level.getBlockState(
                BlockPos.containing(query.shipX, query.shipY, query.shipZ)
            ).isAir
        } ?: return pos
        return BlockPos.containing(hit.shipX, hit.shipY, hit.shipZ)
    }

    @JvmStatic
    fun getBlockState(level: Level, pos: BlockPos): BlockState {
        return level.getBlockState(mappedBlockPos(level, pos))
    }

    @JvmStatic
    fun isEmptyBlock(level: Level, pos: BlockPos): Boolean {
        return getBlockState(level, pos).isAir
    }

    @JvmStatic
    fun clip(level: Level, ctx: ClipContext): BlockHitResult {
        return level.clipIncludeShips(ctx)
    }

    private fun framesAround(level: Level, x: Double, y: Double, z: Double): List<MtsShipCollision.ShipQueryFrame> {
        val aabb = AABB(x, y, z, x, y, z).inflate(0.25)
        return level.getShipsIntersecting(aabb).map { ship ->
            val world = ship.worldAABB
            MtsShipCollision.ShipQueryFrame(
                id = ship.id,
                worldToShip = Matrix4d(ship.worldToShip),
                shipToWorld = Matrix4d(ship.shipToWorld),
                worldMinX = world.minX() - 0.25,
                worldMinY = world.minY() - 0.25,
                worldMinZ = world.minZ() - 0.25,
                worldMaxX = world.maxX() + 0.25,
                worldMaxY = world.maxY() + 0.25,
                worldMaxZ = world.maxZ() + 0.25
            )
        }
    }
}
