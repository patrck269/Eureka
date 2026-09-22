package org.valkyrienskies.eureka.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips Immersive Aircraft / Simple Planes / Immersive Vehicles compat mixins
 * when those mods are not present.
 */
public class EurekaMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String IA_VEHICLE = "immersive_aircraft.entity.VehicleEntity";
    private static final String SIMPLE_PLANES = "xyz.przemyk.simpleplanes.entities.PlaneEntity";
    private static final String MTS_VEHICLE = "mcinterface1201.BuilderEntityExisting";
    private static final String WARIUM_HUGE_HIT =
            "net.mcreator.crustychunks.procedures.HugeBulletEntityHitProcedure";
    private static final String WARIUM_BLOCK_HIT =
            "net.mcreator.crustychunks.procedures.HugeBulletHitProcedure";
    private static final String WARIUM_HV =
            "net.mcreator.crustychunks.procedures.HVParticleProjectileHitsBlockProcedure";
    private static final String DH_PROXY =
            "com.seibel.distanthorizons.common.util.ProxyUtil_forge";


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
        if (mixinClassName.contains("MixinImmersiveVehiclesVehicle")
                || mixinClassName.contains("MixinWrapperWorldShipCollision")) {
            return classPresent(MTS_VEHICLE);
        }
        if (mixinClassName.contains("MixinWariumHugeBulletEntityHit")) {
            return classPresent(WARIUM_HUGE_HIT);
        }
        if (mixinClassName.contains("MixinWariumHugeBulletHit")) {
            return classPresent(WARIUM_BLOCK_HIT);
        }
        if (mixinClassName.contains("MixinWariumHvParticleHit")) {
            return classPresent(WARIUM_HV);
        }
        if (mixinClassName.contains("MixinDhProxyUtilServer")) {
            return classPresent(DH_PROXY);
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
