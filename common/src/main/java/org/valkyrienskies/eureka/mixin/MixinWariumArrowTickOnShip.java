package org.valkyrienskies.eureka.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.compat.warium.WariumShipHitLagHook;

/**
 * Discard Warium munitions only after they are stuck on a ship. Flying
 * rounds that overlap a host-ship AABB must keep ticking.
 */
@Mixin(AbstractArrow.class)
public abstract class MixinWariumArrowTickOnShip {

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("HEAD"), cancellable = true)
    private void eureka$abortWariumShipTick(final CallbackInfo ci) {
        final AbstractArrow self = (AbstractArrow) (Object) this;
        if (Float.isNaN(self.getYRot()) || Float.isNaN(self.getXRot())) {
            self.setYRot(0.0F);
            self.setXRot(0.0F);
        }
        if (WariumShipHitLagHook.abortAndDiscard(self)) {
            ci.cancel();
        }
    }

    @Inject(method = {"onHitEntity", "m_5790_"}, at = @At("HEAD"), cancellable = true)
    private void eureka$passThroughPlayerOnOccupiedShip(
            final net.minecraft.world.phys.EntityHitResult hit,
            final CallbackInfo ci
    ) {
        final AbstractArrow self = (AbstractArrow) (Object) this;
        if (WariumShipHitLagHook.shouldSkipPlayerHit(self, hit.getEntity())) {
            ci.cancel();
        }
    }
}
