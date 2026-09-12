package org.valkyrienskies.eureka.mixin.compat;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.immersiveaircraft.ShipDeckLandingApplier;
import org.valkyrienskies.eureka.math.SimplePlanesQuat;

/**
 * Optional mixin: only applied when Simple Planes is loaded (see EurekaMixinConfigPlugin).
 * PlaneEntity lerps pitch toward world-space getGroundPitch() (5°) and roll toward 0
 * while on ground — the same world-space landing class as Immersive Aircraft.
 */
@Pseudo
@Mixin(targets = "xyz.przemyk.simpleplanes.entities.PlaneEntity")
public abstract class MixinSimplePlanesVehicle {

    @Shadow(remap = false)
    public float rotationRoll;

    @Shadow(remap = false)
    public abstract void setQ(Quaternionf q);

    @Shadow(remap = false)
    public abstract void setQ_Client(Quaternionf q);

    @Unique
    private boolean eureka$landedOnShip;

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("TAIL"))
    private void vs$correctLandedPlaneOnShip(final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        final float currentRoll = this.rotationRoll;
        final Consumer<Float> setRoll = r -> this.rotationRoll = r;
        final Supplier<List<AABB>> extras = Collections::emptyList;
        this.eureka$landedOnShip = ShipDeckLandingApplier.apply(
                entity, currentRoll, setRoll, extras, 5.0);
        if (this.eureka$landedOnShip) {
            final Quaternionf q = SimplePlanesQuat.fromYawPitchRoll(
                    entity.getYRot(), entity.getXRot(), this.rotationRoll);
            this.setQ(q);
            this.setQ_Client(q);
        }
    }

    /**
     * Overrides VS MixinEntity.vs$shouldDrag. While landed we write ship velocity
     * into deltaMovement (blocks/tick) and integrate carry ourselves.
     */
    public boolean vs$shouldDrag() {
        return !this.eureka$landedOnShip;
    }
}
