package org.valkyrienskies.eureka.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URL
import java.net.URLClassLoader

/**
 * Drives the shipped mixin-plugin presence check (the same helper
 * [EurekaMixinConfigPlugin.shouldApplyMixin] uses). A transforming loader
 * that records [ClassLoader.loadClass] must not see Entity or the probe class.
 */
class MixinModPresenceTest {

    @Test
    fun presenceCheckDoesNotLoadEntityOrProbeClass() {
        val loader = RecordingLoader()
        val present = MixinModPresence.present(
            loader,
            "immersive_aircraft.entity.VehicleEntity"
        )
        assertTrue(present)
        assertTrue(
            loader.resources.contains("immersive_aircraft/entity/VehicleEntity.class"),
            "presence must be a resource lookup, got ${loader.resources}"
        )
        assertFalse(
            loader.loaded.any { it == ENTITY || it == "immersive_aircraft.entity.VehicleEntity" },
            "must not Class.forName/loadClass game classes, loaded=${loader.loaded}"
        )
    }

    @Test
    fun missingResourceIsAbsentWithoutLoadingEntity() {
        val loader = RecordingLoader(serve = false)
        assertFalse(MixinModPresence.present(loader, "xyz.przemyk.simpleplanes.entities.PlaneEntity"))
        assertFalse(loader.loaded.contains(ENTITY))
    }

    private class RecordingLoader(private val serve: Boolean = true) : URLClassLoader(emptyArray(), null) {
        val loaded = mutableListOf<String>()
        val resources = mutableListOf<String>()

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            loaded += name
            throw ClassNotFoundException(name)
        }

        override fun getResource(name: String): URL? {
            resources += name
            return if (serve && name.endsWith(".class")) URL("file:///probe/$name") else null
        }
    }

    companion object {
        private const val ENTITY = "net.minecraft.world.entity.Entity"
    }
}
