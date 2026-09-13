package org.valkyrienskies.eureka.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckLandingApplier;

/**
 * Client ship physics + EntityDragger also run at Minecraft.tick TAIL. Pin
 * after that so rendered parked planes match the live deck at >100 m/s.
 */
@Mixin(value = Minecraft.class, priority = 400)
public abstract class MixinMinecraftShipDeckWeld {

    @Inject(method = "tick", at = @At("TAIL"))
    private void eureka$pinDeckWeldsAfterPhysics(final CallbackInfo ci) {
        ShipDeckLandingApplier.pinAllWelds();
    }
}
