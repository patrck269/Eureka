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
 * CIWS HugeAIBulletEntity.m_5790_ calls super then HugeBulletEntityHitProcedure.
 * Cancelling AbstractArrow.onHitEntity does not skip this. Occupied-player
 * hits must cancel here so armor-bypass/FX/extra arrows do not flood.
 */
@Pseudo
@Mixin(targets = "net.mcreator.crustychunks.procedures.HugeBulletEntityHitProcedure")
public abstract class MixinWariumHugeBulletEntityHit {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void eureka$skipOccupiedPlayerHit(
            final LevelAccessor world,
            final double x,
            final double y,
            final double z,
            final Entity target,
            final Entity projectile,
            final CallbackInfo ci
    ) {
        if (skipPlayerHit(projectile, target)) {
            ci.cancel();
        }
    }

    private static boolean skipPlayerHit(final Entity projectile, final Entity target) {
        try {
            final Class<?> hook = Class.forName(
                    "org.valkyrienskies.eureka.compat.warium.WariumShipHitLagHook",
                    true,
                    Level.class.getClassLoader()
            );
            final Method method = hook.getMethod("shouldSkipPlayerHit", Entity.class, Entity.class);
            return Boolean.TRUE.equals(method.invoke(null, projectile, target));
        } catch (final Throwable ignored) {
            return false;
        }
    }
}
