package org.valkyrienskies.eureka.mixin.compat;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckLandingApplier;

/**
 * Optional mixin: only applied when Immersive Aircraft is loaded (see EurekaMixinConfigPlugin).
 */
@Pseudo
@Mixin(targets = "immersive_aircraft.entity.VehicleEntity")
public abstract class MixinImmersiveAircraftVehicle {

    @Shadow(remap = false)
    public float roll;

    @Shadow(remap = false)
    public abstract void setZRot(float rot);

    @Shadow(remap = false)
    public abstract List<AABB> getAdditionalShapes();

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void vs$correctLandedPlaneOnShip(final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        final float currentRoll = this.roll;
        final Consumer<Float> setRoll = this::setZRot;
        final Supplier<List<AABB>> extras = this::getAdditionalShapes;
        ShipDeckLandingApplier.apply(entity, currentRoll, setRoll, extras);
    }
}
