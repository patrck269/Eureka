package org.valkyrienskies.eureka.compat.immersiveaircraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.valkyrienskies.eureka.math.SimplePlanesQuat;

/**
 * Runs ship-deck landing from a vanilla {@link Entity} mixin. Cross-mod mixins
 * into Immersive Aircraft / Simple Planes cannot reference Eureka classes on
 * Forge's ModuleClassLoader (dedicated CNFDE).
 */
public final class ShipDeckVehicleTick {

    public static final String LANDED_TAG = "eureka_landed_on_ship";

    private ShipDeckVehicleTick() {
    }

    public static void onTick(final Entity entity) {
        final String name = entity.getClass().getName();
        final boolean ia = name.startsWith("immersive_aircraft.");
        final boolean sp = name.startsWith("xyz.przemyk.simpleplanes.");
        if (!ia && !sp) {
            return;
        }
        final float roll = ia ? readFloat(entity, "roll") : readFloat(entity, "rotationRoll");
        final Consumer<Float> setRoll = ia
                ? value -> invoke(entity, "setZRot", new Class<?>[] {float.class}, value)
                : value -> writeFloat(entity, "rotationRoll", value);
        final Supplier<List<AABB>> extras = ia
                ? () -> extraShapes(entity)
                : Collections::emptyList;
        final double groundPitch = sp ? 5.0 : 4.0;
        final boolean landed = ShipDeckLandingApplier.apply(entity, roll, setRoll, extras, groundPitch);
        if (landed) {
            entity.addTag(LANDED_TAG);
            if (sp) {
                applySimplePlanesQuat(entity);
            }
        } else {
            entity.removeTag(LANDED_TAG);
        }
    }

    private static void applySimplePlanesQuat(final Entity entity) {
        final float roll = readFloat(entity, "rotationRoll");
        final Quaternionf q = SimplePlanesQuat.fromYawPitchRoll(
                entity.getYRot(), entity.getXRot(), roll);
        invoke(entity, "setQ", new Class<?>[] {Quaternionf.class}, q);
        invoke(entity, "setQ_Client", new Class<?>[] {Quaternionf.class}, q);
    }

    @SuppressWarnings("unchecked")
    private static List<AABB> extraShapes(final Entity entity) {
        final Object result = invoke(entity, "getAdditionalShapes", new Class<?>[0]);
        if (result instanceof List<?> list) {
            return (List<AABB>) list;
        }
        return Collections.emptyList();
    }

    private static float readFloat(final Entity entity, final String fieldName) {
        try {
            final Field field = findField(entity.getClass(), fieldName);
            field.setAccessible(true);
            return field.getFloat(entity);
        } catch (final ReflectiveOperationException e) {
            return 0.0f;
        }
    }

    private static void writeFloat(final Entity entity, final String fieldName, final float value) {
        try {
            final Field field = findField(entity.getClass(), fieldName);
            field.setAccessible(true);
            field.setFloat(entity, value);
        } catch (final ReflectiveOperationException ignored) {
        }
    }

    private static Object invoke(
            final Entity entity,
            final String methodName,
            final Class<?>[] args,
            final Object... values
    ) {
        try {
            final Method method = findMethod(entity.getClass(), methodName, args);
            method.setAccessible(true);
            return method.invoke(entity, values);
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    private static Field findField(Class<?> type, final String name) throws NoSuchFieldException {
        while (type != null && type != Object.class) {
            try {
                return type.getDeclaredField(name);
            } catch (final NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> type, final String name, final Class<?>[] args)
            throws NoSuchMethodException {
        while (type != null && type != Object.class) {
            try {
                return type.getDeclaredMethod(name, args);
            } catch (final NoSuchMethodException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
