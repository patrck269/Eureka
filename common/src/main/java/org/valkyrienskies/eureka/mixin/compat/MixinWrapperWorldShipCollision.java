package org.valkyrienskies.eureka.mixin.compat;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional mixin: MTS WrapperWorld block/clip queries use world coordinates.
 * Redirect those queries onto Valkyrien Skies shipyard blocks. Loaded via
 * Level's classloader so this file must not reference Eureka types.
 */
@Pseudo
@Mixin(targets = "mcinterface1201.WrapperWorld")
public abstract class MixinWrapperWorldShipCollision {

    @Shadow
    protected Level world;

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState eureka$shipGetBlockState(final Level level, final BlockPos pos) {
        final Object mapped = invokeHook(
                "getBlockState",
                new Class<?>[] {Level.class, BlockPos.class},
                level,
                pos
        );
        if (mapped instanceof BlockState state) {
            return state;
        }
        return level.getBlockState(pos);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean eureka$shipIsEmptyBlock(final Level level, final BlockPos pos) {
        final Object mapped = invokeHook(
                "isEmptyBlock",
                new Class<?>[] {Level.class, BlockPos.class},
                level,
                pos
        );
        if (mapped instanceof Boolean empty) {
            return empty;
        }
        return level.isEmptyBlock(pos);
    }

    @Redirect(
            method = "getBlockHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private BlockHitResult eureka$shipClip(final Level level, final ClipContext ctx) {
        final Object mapped = invokeHook(
                "clip",
                new Class<?>[] {Level.class, ClipContext.class},
                level,
                ctx
        );
        if (mapped instanceof BlockHitResult hit) {
            return hit;
        }
        return level.clip(ctx);
    }

    private static Object invokeHook(final String name, final Class<?>[] args, final Object... values) {
        try {
            final Class<?> helper = Class.forName(
                    "org.valkyrienskies.eureka.compat.mts.MtsShipCollisionHook",
                    true,
                    Level.class.getClassLoader()
            );
            final Method method = helper.getMethod(name, args);
            return method.invoke(null, values);
        } catch (final Throwable ignored) {
            return null;
        }
    }
}
