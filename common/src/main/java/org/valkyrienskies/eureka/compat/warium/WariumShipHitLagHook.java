package org.valkyrienskies.eureka.compat.warium;

import java.lang.reflect.Field;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.eureka.math.WariumShipHitLag;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Minecraft/VS boundary for [WariumShipHitLag]. Mixins targeting Warium
 * classes must call this via Level's classloader.
 */
public final class WariumShipHitLagHook {

    private WariumShipHitLagHook() {
    }

    public static boolean shouldSkipExtraHitProjectiles(
            final Entity projectile,
            final Level level,
            final double x,
            final double y,
            final double z
    ) {
        if (projectile == null || level == null) {
            return false;
        }
        final AABB box = new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5);
        final boolean onShip = WariumShipHitLag.INSTANCE.isOnShip(
                intersectsShip(level, box),
                VSGameUtilsKt.getShipManaging(projectile) != null
                        || VSGameUtilsKt.getShipManagingPos(level, x, y, z) != null
        );
        final Ship ship = shipAt(level, projectile, x, y, z, box);
        return WariumShipHitLag.INSTANCE.shouldSkipExtraHitProjectiles(
                projectile.getClass().getName(),
                onShip,
                onShip && ship != null && playerAboard(level, ship)
        );
    }

    public static boolean shouldSkipOccupiedParticleBurst(
            final Entity projectile,
            final Level level,
            final double x,
            final double y,
            final double z
    ) {
        if (projectile == null || level == null) {
            return false;
        }
        final AABB box = new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5);
        final boolean onShip = WariumShipHitLag.INSTANCE.isOnShip(
                intersectsShip(level, box),
                VSGameUtilsKt.getShipManaging(projectile) != null
                        || VSGameUtilsKt.getShipManagingPos(level, x, y, z) != null
        );
        final Ship ship = shipAt(level, projectile, x, y, z, box);
        return WariumShipHitLag.INSTANCE.shouldSkipOccupiedParticleBurst(
                projectile.getClass().getName(),
                onShip,
                onShip && ship != null && playerAboard(level, ship)
        );
    }

    private static long occupiedSoundTick = Long.MIN_VALUE;
    private static int occupiedSoundsThisTick;

    public static boolean shouldSkipOccupiedSound(
            final Entity projectile,
            final Level level,
            final double x,
            final double y,
            final double z
    ) {
        if (!shouldSkipOccupiedParticleBurst(projectile, level, x, y, z)) {
            return false;
        }
        final long t = level.getGameTime();
        if (t != occupiedSoundTick) {
            occupiedSoundTick = t;
            occupiedSoundsThisTick = 0;
        }
        if (occupiedSoundsThisTick >= 2) {
            return true;
        }
        occupiedSoundsThisTick++;
        return false;
    }

    public static boolean shouldSkipShipyardMove(final Entity entity) {
        if (entity == null) {
            return false;
        }
        return WariumShipHitLag.INSTANCE.shouldSkipShipyardMove(entity.getClass().getName());
    }

    public static boolean shouldSkipPlayerHit(final Entity projectile, final Entity target) {
        if (projectile == null || !(target instanceof Player) || target.level() == null) {
            return false;
        }
        final AABB box = target.getBoundingBox().inflate(0.5);
        return WariumShipHitLag.INSTANCE.shouldSkipPlayerHit(
                projectile.getClass().getName(),
                playerAboardAt(target.level(), box)
        );
    }

    public static boolean abortAndDiscard(final Entity entity) {
        if (entity == null || entity.level() == null) {
            return false;
        }
        final boolean warium = WariumShipHitLag.INSTANCE.isWariumMunition(entity.getClass().getName());
        if (!warium) {
            return false;
        }
        final boolean stuck = isStuck(entity);
        if (!WariumShipHitLag.INSTANCE.shouldLookupShipForStuckDiscard(true, stuck)) {
            return false;
        }
        final Level level = entity.level();
        final AABB box = entity.getBoundingBox().inflate(0.25);
        final double x = entity.getX();
        final double y = entity.getY();
        final double z = entity.getZ();
        final boolean onShip = WariumShipHitLag.INSTANCE.isOnShip(
                intersectsShip(level, box),
                VSGameUtilsKt.getShipManaging(entity) != null
                        || VSGameUtilsKt.getShipManagingPos(level, x, y, z) != null
        );
        final boolean discard = WariumShipHitLag.INSTANCE.shouldDiscardStuckOnShip(
                entity.getClass().getName(),
                onShip,
                stuck
        );
        if (!discard) {
            return false;
        }
        // Cancel vanilla tick only. discard() during C2ME/VS section flush
        // leaves a null EntityAccess and NPEs PersistentEntitySectionManager.
        return true;
    }

    private static Ship shipAt(
            final Level level,
            final Entity entity,
            final double x,
            final double y,
            final double z,
            final AABB box
    ) {
        if (entity != null) {
            final Ship managed = VSGameUtilsKt.getShipManaging(entity);
            if (managed != null) {
                return managed;
            }
        }
        final Ship byPos = VSGameUtilsKt.getShipManagingPos(level, x, y, z);
        if (byPos != null) {
            return byPos;
        }
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, box)) {
            return ship;
        }
        return null;
    }

    private static boolean playerAboardAt(final Level level, final AABB box) {
        final Ship atBox = shipAt(level, null, (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5, box);
        if (atBox != null && playerAboard(level, atBox)) {
            return true;
        }
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, box)) {
            if (playerAboard(level, ship)) {
                return true;
            }
        }
        return false;
    }

    private static boolean playerAboard(final Level level, final Ship ship) {
        final AABBdc world = ship.getWorldAABB();
        final AABB shipWorld = new AABB(
                world.minX(),
                world.minY(),
                world.minZ(),
                world.maxX(),
                world.maxY(),
                world.maxZ()
        ).inflate(1.0);
        for (final Player player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (shipWorld.intersects(player.getBoundingBox())) {
                return true;
            }
            final Ship riding = VSGameUtilsKt.getShipManaging(player);
            if (riding != null && riding.getId() == ship.getId()) {
                return true;
            }
        }
        return false;
    }

    private static volatile Field inGroundField;

    private static boolean isStuck(final Entity entity) {
        if (!(entity instanceof AbstractArrow)) {
            return false;
        }
        try {
            Field field = inGroundField;
            if (field == null) {
                try {
                    field = AbstractArrow.class.getDeclaredField("inGround");
                } catch (final NoSuchFieldException e) {
                    field = AbstractArrow.class.getDeclaredField("f_36703_");
                }
                field.setAccessible(true);
                inGroundField = field;
            }
            return field.getBoolean(entity);
        } catch (final ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean intersectsShip(final Level level, final AABB box) {
        return VSGameUtilsKt.getShipsIntersecting(level, box).iterator().hasNext();
    }
}
