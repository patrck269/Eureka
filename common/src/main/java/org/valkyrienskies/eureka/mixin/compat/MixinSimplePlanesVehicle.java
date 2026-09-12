package org.valkyrienskies.eureka.mixin.compat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Optional mixin: only applied when Simple Planes is loaded.
 * Must not reference Eureka types — Forge merges this into the simpleplanes module.
 */
@Pseudo
@Mixin(targets = "xyz.przemyk.simpleplanes.entities.PlaneEntity")
public abstract class MixinSimplePlanesVehicle {

    public boolean vs$shouldDrag() {
        return !((Entity) (Object) this).getTags().contains("eureka_landed_on_ship");
    }
}
