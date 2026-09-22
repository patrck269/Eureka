package org.valkyrienskies.eureka.mixin.compat;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons ProxyUtil_forge.getLevelWrapper resolves a client-only
 * world type after ServerLevel. On dedicated that floods RuntimeDistCleaner.
 * Handle ServerLevel only.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.util.ProxyUtil_forge")
public abstract class MixinDhProxyUtilServer {

    private static Method wrapperMethod;

    @Inject(method = "getLevelWrapper", at = @At("HEAD"), cancellable = true, remap = false)
    private static void eureka$serverLevelOnly(
            final LevelAccessor level,
            final CallbackInfoReturnable<Object> cir
    ) {
        if (level instanceof ServerLevel serverLevel) {
            cir.setReturnValue(wrapServer(serverLevel));
            return;
        }
        cir.setReturnValue(null);
    }

    private static Object wrapServer(final ServerLevel level) {
        try {
            Method method = wrapperMethod;
            if (method == null) {
                final Class<?> clazz = Class.forName(
                        "com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper_forge",
                        true,
                        level.getClass().getClassLoader()
                );
                method = clazz.getMethod("getWrapper", ServerLevel.class);
                wrapperMethod = method;
            }
            return method.invoke(null, level);
        } catch (final Throwable ignored) {
            return null;
        }
    }
}
