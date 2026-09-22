package org.valkyrienskies.eureka.mixin;

import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;

/**
 * VS sends every projectile that hits a ship into shipyard. CIWS volleys
 * next to an occupant freeze the client. No Eureka imports — this mixin
 * is merged into valkyrienskies.
 */
@Mixin(targets = "org.valkyrienskies.mod.common.entity.handling.AbstractShipyardEntityHandler")
public abstract class MixinWariumSkipShipyardMove {

    @Inject(
            method = "moveEntityFromWorldToShipyard(Lnet/minecraft/world/entity/Entity;Lorg/valkyrienskies/core/api/ships/Ship;DDD)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void eureka$skipWariumShipyardMove(
            final Entity entity,
            final Ship ship,
            final double x,
            final double y,
            final double z,
            final CallbackInfo ci
    ) {
        if (skip(entity)) {
            ci.cancel();
        }
    }

    private static boolean skip(final Entity entity) {
        try {
            final Class<?> hook = Class.forName(
                    "org.valkyrienskies.eureka.compat.warium.WariumShipHitLagHook",
                    true,
                    Level.class.getClassLoader()
            );
            final Method method = hook.getMethod("shouldSkipShipyardMove", Entity.class);
            return Boolean.TRUE.equals(method.invoke(null, entity));
        } catch (final Throwable ignored) {
            return false;
        }
    }
}
