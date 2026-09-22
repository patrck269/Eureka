package org.valkyrienskies.eureka.mixin.compat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Optional mixin: only applied when Immersive Vehicles is loaded.
 * Must not reference Eureka types — Forge merges this into the
 * mcinterface1201 module.
 */
@Pseudo
@Mixin(targets = "mcinterface1201.BuilderEntityExisting")
public abstract class MixinImmersiveVehiclesVehicle {

    public boolean vs$shouldDrag() {
        return !((Entity) (Object) this).getTags().contains("eureka_landed_on_ship");
    }
}
