package org.valkyrienskies.eureka.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.eureka.math.ShipDeckLanding;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * VS {@code setupWithShipMounted} pulls F5 back by ship AABB * 1.5. On a Eureka
 * carrier that is tens of blocks, the camera leaves loaded chunks / DH fade
 * and the ship vanishes. Cap zoom to {@link ShipDeckLanding#SHIP_THIRD_PERSON_MAX}.
 */
@Mixin(value = Camera.class, priority = 1100)
public abstract class MixinCameraShipThirdPerson {

    @Shadow
    private boolean detached;

    @Shadow
    private Entity entity;

    @Shadow
    private Vec3 position;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(
        method = "setupWithShipMounted",
        at = @At("TAIL"),
        remap = false
    )
    private void eureka$capShipThirdPersonZoom(
        final net.minecraft.world.level.BlockGetter level,
        final Entity renderViewEntity,
        final boolean thirdPerson,
        final boolean thirdPersonReverse,
        final float partialTicks,
        final ClientShip shipMountedTo,
        final org.joml.Vector3dc inShipPlayerPosition,
        final CallbackInfo ci
    ) {
        if (!thirdPerson || this.entity == null || this.position == null) {
            return;
        }
        final Vec3 eye = this.entity.getEyePosition(partialTicks);
        final double dist = this.position.distanceTo(eye);
        final double cap = ShipDeckLanding.capShipThirdPersonDistance(dist);
        if (cap >= dist || dist < 1.0e-4) {
            return;
        }
        final double s = cap / dist;
        this.setPosition(
            eye.x + (this.position.x - eye.x) * s,
            eye.y + (this.position.y - eye.y) * s,
            eye.z + (this.position.z - eye.z) * s
        );
    }
}
