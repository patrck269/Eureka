package org.valkyrienskies.eureka.mixin.compat;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private boolean eureka$landedOnShip;

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("TAIL"))
    private void vs$correctLandedPlaneOnShip(final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        final float currentRoll = this.roll;
        final Consumer<Float> setRoll = this::setZRot;
        final Supplier<List<AABB>> extras = this::getAdditionalShapes;
        this.eureka$landedOnShip = ShipDeckLandingApplier.apply(entity, currentRoll, setRoll, extras);
    }

    /**
     * Overrides VS MixinEntity.vs$shouldDrag for IA vehicles. While landed we write
     * ship velocity into deltaMovement (blocks/tick) and integrate carry ourselves;
     * EntityDragger must not also transform position.
     */
    public boolean vs$shouldDrag() {
        return !this.eureka$landedOnShip;
    }
}
