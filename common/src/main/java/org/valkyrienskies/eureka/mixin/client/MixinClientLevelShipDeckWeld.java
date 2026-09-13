package org.valkyrienskies.eureka.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckVehicleTick;

/**
 * Client VehicleEntity.tick lerps toward the server packet after super.tick().
 * Re-pin after that lerp so parked planes stay in ship space.
 */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelShipDeckWeld {

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void eureka$afterVehicleTick(final Entity entity, final CallbackInfo ci) {
        ShipDeckVehicleTick.onTick(entity);
    }
}
