package org.valkyrienskies.eureka.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckLandingApplier;

/**
 * VS EntityDragger runs from a TAIL inject (default priority 1000) after ship
 * physics. Lower priority applies later, so this TAIL runs after that drag and
 * pins parked planes to the live ship transform — required at >100 m/s.
 */
@Mixin(value = MinecraftServer.class, priority = 400)
public abstract class MixinMinecraftServerShipDeckWeld {

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void eureka$pinDeckWeldsAfterPhysics(final CallbackInfo ci) {
        ShipDeckLandingApplier.pinAllWelds();
    }
}
