package org.valkyrienskies.eureka.mixin.compat;

import java.lang.reflect.Method;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Occupied hull hits still run DamagesProcedure, sounds, and particles.
 * Skip only extra getArrow addFreshEntity spawns that multiply the volley.
 */
@Pseudo
@Mixin(targets = "net.mcreator.crustychunks.procedures.HugeBulletHitProcedure")
public abstract class MixinWariumHugeBulletHit {

    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;m_7967_(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static boolean eureka$skipExtraObf(
            final ServerLevel invokeWorld,
            final Entity toSpawn,
            final LevelAccessor methodWorld,
            final double x,
            final double y,
            final double z,
            final Entity projectile
    ) {
        return spawnExtra(invokeWorld, toSpawn, methodWorld, x, y, z, projectile);
    }

    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            ),
            remap = false,
            require = 0
    )
    private static boolean eureka$skipExtra(
            final Level invokeWorld,
            final Entity toSpawn,
            final LevelAccessor methodWorld,
            final double x,
            final double y,
            final double z,
            final Entity projectile
    ) {
        return spawnExtra(invokeWorld, toSpawn, methodWorld, x, y, z, projectile);
    }

    private static boolean spawnExtra(
            final Level invokeWorld,
            final Entity toSpawn,
            final LevelAccessor methodWorld,
            final double x,
            final double y,
            final double z,
            final Entity projectile
    ) {
        if (methodWorld instanceof Level level && skipExtra(projectile, level, x, y, z)) {
            return false;
        }
        return invokeWorld.addFreshEntity(toSpawn);
    }

    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;m_8767_(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static int eureka$skipBurstObf(
            final ServerLevel invokeWorld,
            final ParticleOptions particle,
            final double x,
            final double y,
            final double z,
            final int count,
            final double xd,
            final double yd,
            final double zd,
            final double speed,
            final LevelAccessor methodWorld,
            final double hx,
            final double hy,
            final double hz,
            final Entity projectile
    ) {
        if (methodWorld instanceof Level level && skipBurst(projectile, level, hx, hy, hz)) {
            return 0;
        }
        return invokeWorld.sendParticles(particle, x, y, z, count, xd, yd, zd, speed);
    }

    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_7785_(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void eureka$throttleSound(
            final Level invokeWorld,
            final double x,
            final double y,
            final double z,
            final SoundEvent sound,
            final SoundSource source,
            final float volume,
            final float pitch,
            final boolean distanceDelay,
            final LevelAccessor methodWorld,
            final double hx,
            final double hy,
            final double hz,
            final Entity projectile
    ) {
        if (methodWorld instanceof Level level && skipSound(projectile, level, hx, hy, hz)) {
            return;
        }
        invokeWorld.playLocalSound(x, y, z, sound, source, volume, pitch, distanceDelay);
    }

    private static Method skipMethod;
    private static Method burstMethod;
    private static Method soundMethod;

    private static boolean skipBurst(
            final Entity projectile,
            final Level level,
            final double x,
            final double y,
            final double z
    ) {
        try {
            Method method = burstMethod;
            if (method == null) {
                method = hookClass().getMethod(
                        "shouldSkipOccupiedParticleBurst",
                        Entity.class,
                        Level.class,
                        double.class,
                        double.class,
                        double.class
                );
                burstMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(null, projectile, level, x, y, z));
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static boolean skipSound(
            final Entity projectile,
            final Level level,
            final double x,
            final double y,
            final double z
    ) {
        try {
            Method method = soundMethod;
            if (method == null) {
                method = hookClass().getMethod(
                        "shouldSkipOccupiedSound",
                        Entity.class,
                        Level.class,
                        double.class,
                        double.class,
                        double.class
                );
                soundMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(null, projectile, level, x, y, z));
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static Class<?> hookClass() throws ClassNotFoundException {
        return Class.forName(
                "org.valkyrienskies.eureka.compat.warium.WariumShipHitLagHook",
                true,
                Level.class.getClassLoader()
        );
    }

    private static boolean skipExtra(
            final Entity projectile,
            final Level level,
            final double x,
            final double y,
            final double z
    ) {
        try {
            Method method = skipMethod;
            if (method == null) {
                method = hookClass().getMethod(
                        "shouldSkipExtraHitProjectiles",
                        Entity.class,
                        Level.class,
                        double.class,
                        double.class,
                        double.class
                );
                skipMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(null, projectile, level, x, y, z));
        } catch (final Throwable ignored) {
            return false;
        }
    }
}
