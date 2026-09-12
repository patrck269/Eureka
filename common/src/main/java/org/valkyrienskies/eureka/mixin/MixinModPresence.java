package org.valkyrienskies.eureka.mixin;

/**
 * Mixin-plugin presence probe. Must not define/load the probed class (or Entity)
 * through the transforming loader.
 */
public final class MixinModPresence {

    private MixinModPresence() {
    }

    public static boolean present(final ClassLoader loader, final String binaryName) {
        if (loader == null || binaryName == null || binaryName.isEmpty()) {
            return false;
        }
        final String resource = binaryName.replace('.', '/') + ".class";
        return loader.getResource(resource) != null;
    }
}
