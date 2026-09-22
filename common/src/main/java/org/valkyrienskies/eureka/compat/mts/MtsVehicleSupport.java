package org.valkyrienskies.eureka.compat.mts;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;

/**
 * Runtime MTS occupancy and physics-position sync. Uses reflection so Eureka
 * does not compile against Immersive Vehicles. Occupancy is linked-seat
 * riders / controllerCount, not only vanilla {@link Entity#isVehicle()}.
 */
public final class MtsVehicleSupport {

    private MtsVehicleSupport() {
    }

    public static boolean isWrapper(final Entity entity) {
        return entity != null && "mcinterface1201.BuilderEntityExisting".equals(entity.getClass().getName());
    }

    public static boolean hasRider(final Entity wrapper) {
        if (wrapper == null) {
            return false;
        }
        if (wrapper.isVehicle()) {
            return true;
        }
        if (!isMtsName(wrapper.getClass().getName())) {
            return false;
        }
        final Object mts = read(wrapper, "entity");
        if (mts == null) {
            return false;
        }
        if (read(mts, "rider") != null) {
            return true;
        }
        if (read(mts, "lastController") != null) {
            return true;
        }
        final Object count = read(mts, "controllerCount");
        if (count instanceof Number && ((Number) count).intValue() > 0) {
            return true;
        }
        if (anyPartRider(read(mts, "allParts")) || anyPartRider(read(mts, "parts"))) {
            return true;
        }
        return false;
    }

    public static void writePhysicsPosition(
            final Entity wrapper,
            final double x,
            final double y,
            final double z,
            final boolean freezeMotion
    ) {
        if (wrapper == null || !isMtsName(wrapper.getClass().getName())) {
            return;
        }
        final Object mts = read(wrapper, "entity");
        if (mts == null) {
            return;
        }
        writePoint(read(mts, "position"), x, y, z);
        writePoint(read(mts, "prevPosition"), x, y, z);
        if (freezeMotion) {
            writePoint(read(mts, "motion"), 0.0, 0.0, 0.0);
            writePoint(read(mts, "prevMotion"), 0.0, 0.0, 0.0);
        }
    }

    /**
     * Occupied-deck carry: write physics position and motion without freezing
     * taxi. Does not pin a weld local.
     */
    public static void writePhysicsCarry(
            final Entity wrapper,
            final double x,
            final double y,
            final double z,
            final double mx,
            final double my,
            final double mz
    ) {
        writePhysicsPosition(wrapper, x, y, z, false);
        if (wrapper == null || !isMtsName(wrapper.getClass().getName())) {
            return;
        }
        final Object mts = read(wrapper, "entity");
        if (mts == null) {
            return;
        }
        writePoint(read(mts, "motion"), mx, my, mz);
        writePoint(read(mts, "prevMotion"), mx, my, mz);
    }

    public static double[] readPhysicsPosition(final Entity wrapper) {
        return readPoint(wrapper, "position");
    }

    public static double[] readPhysicsMotion(final Entity wrapper) {
        return readPoint(wrapper, "motion");
    }

    private static double[] readPoint(final Entity wrapper, final String fieldName) {
        if (wrapper == null || !isMtsName(wrapper.getClass().getName())) {
            return null;
        }
        final Object mts = read(wrapper, "entity");
        if (mts == null) {
            return null;
        }
        final Object point = read(mts, fieldName);
        if (point == null) {
            return null;
        }
        return new double[] {
            getDouble(point, "x"),
            getDouble(point, "y"),
            getDouble(point, "z")
        };
    }

    private static double getDouble(final Object target, final String fieldName) {
        try {
            final Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return field.getDouble(target);
        } catch (final ReflectiveOperationException e) {
            return 0.0;
        }
    }

    private static boolean isMtsName(final String name) {
        return name.startsWith("mcinterface1201.") || name.startsWith("minecrafttransportsimulator.");
    }

    private static boolean anyPartRider(final Object parts) {
        if (!(parts instanceof Iterable<?> iterable)) {
            return false;
        }
        for (final Object part : iterable) {
            if (read(part, "rider") != null) {
                return true;
            }
        }
        return false;
    }

    private static void writePoint(final Object point, final double x, final double y, final double z) {
        if (point == null) {
            return;
        }
        try {
            final Method set = findMethod(point.getClass(), "set", new Class<?>[] {
                double.class, double.class, double.class
            });
            if (set != null) {
                set.setAccessible(true);
                set.invoke(point, x, y, z);
                return;
            }
        } catch (final ReflectiveOperationException ignored) {
        }
        writeDouble(point, "x", x);
        writeDouble(point, "y", y);
        writeDouble(point, "z", z);
    }

    private static Object read(final Object target, final String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            final Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    private static void writeDouble(final Object target, final String fieldName, final double value) {
        try {
            final Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.setDouble(target, value);
        } catch (final ReflectiveOperationException ignored) {
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

    private static Method findMethod(Class<?> type, final String name, final Class<?>[] args) {
        while (type != null && type != Object.class) {
            try {
                return type.getDeclaredMethod(name, args);
            } catch (final NoSuchMethodException e) {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}
