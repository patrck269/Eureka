package org.valkyrienskies.eureka.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckVehicleTick;

/**
 * Deck landing must run from a vanilla Entity mixin. Injecting into Immersive
 * Aircraft / Simple Planes copies Eureka class refs into those mods' modules,
 * which Forge's ModuleClassLoader cannot load (dedicated CNFDE).
 *
 * Entity.tick TAIL is inside VehicleEntity.tick, before IA updateVelocity/move.
 * This pass zeroes world velocity so IA cannot inherit last-tick ship speed
 * (5+ blocks/tick at 100 m/s). MixinServerLevelShipDeckWeld re-pins after move.
 */
@Mixin(Entity.class)
public abstract class MixinEntityShipDeckLanding {

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("TAIL"))
    private void eureka$shipDeckLanding(final CallbackInfo ci) {
        ShipDeckVehicleTick.onTick((Entity) (Object) this);
    }
}
