package org.valkyrienskies.eureka.mixin.compat;

import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HVParticle is a second Damages+FX+extra-arrow pass after HugeBulletHit.
 * Occupied ships skip it; the original round still bounced with hit sounds.
 */
@Pseudo
@Mixin(targets = "net.mcreator.crustychunks.procedures.HVParticleProjectileHitsBlockProcedure")
public abstract class MixinWariumHvParticleHit {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void eureka$skipOccupiedHv(
            final LevelAccessor world,
            final double x,
            final double y,
            final double z,
            final Entity projectile,
            final CallbackInfo ci
    ) {
        if (world instanceof Level level && skipBurst(projectile, level, x, y, z)) {
            ci.cancel();
        }
    }

    private static Method burstMethod;

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
                final Class<?> hook = Class.forName(
                        "org.valkyrienskies.eureka.compat.warium.WariumShipHitLagHook",
                        true,
                        Level.class.getClassLoader()
                );
                method = hook.getMethod(
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
}
