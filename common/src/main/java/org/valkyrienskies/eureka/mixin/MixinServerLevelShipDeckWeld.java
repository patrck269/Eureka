package org.valkyrienskies.eureka.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckVehicleTick;

/**
 * VehicleEntity.tick calls super.tick() (Entity mixin) then Immersive Aircraft
 * updateVelocity/move. Re-pin after that move so parked planes are not left in
 * world space on a >100 m/s deck.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelShipDeckWeld {

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void eureka$afterVehicleTick(final Entity entity, final CallbackInfo ci) {
        ShipDeckVehicleTick.onTick(entity);
    }
}
