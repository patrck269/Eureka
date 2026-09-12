package org.valkyrienskies.eureka.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips Immersive Aircraft / Simple Planes compat mixins when those mods are not present.
 */
public class EurekaMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String IA_VEHICLE = "immersive_aircraft.entity.VehicleEntity";
    private static final String SIMPLE_PLANES = "xyz.przemyk.simpleplanes.entities.PlaneEntity";

    @Override
    public void onLoad(final String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (mixinClassName.contains("MixinImmersiveAircraftVehicle")) {
            return classPresent(IA_VEHICLE);
        }
        if (mixinClassName.contains("MixinSimplePlanesVehicle")) {
            return classPresent(SIMPLE_PLANES);
        }
        return true;
    }

    private static boolean classPresent(final String name) {
        return MixinModPresence.present(EurekaMixinConfigPlugin.class.getClassLoader(), name);
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
                         final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
                          final IMixinInfo mixinInfo) {
    }
}
