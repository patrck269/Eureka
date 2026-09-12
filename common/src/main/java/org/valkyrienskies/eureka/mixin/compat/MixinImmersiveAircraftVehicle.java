package org.valkyrienskies.eureka.mixin.compat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Optional mixin: only applied when Immersive Aircraft is loaded.
 * Tick/landing runs on vanilla Entity (see MixinEntityShipDeckLanding) so this
 * class must not reference Eureka types — Forge merges it into the
 * immersive_aircraft module.
 */
@Pseudo
@Mixin(targets = "immersive_aircraft.entity.VehicleEntity")
public abstract class MixinImmersiveAircraftVehicle {

    /**
     * Overrides VS MixinEntity.vs$shouldDrag. The landed flag is a vanilla
     * entity tag set from the Entity mixin tick.
     */
    public boolean vs$shouldDrag() {
        return !((Entity) (Object) this).getTags().contains("eureka_landed_on_ship");
    }
}
