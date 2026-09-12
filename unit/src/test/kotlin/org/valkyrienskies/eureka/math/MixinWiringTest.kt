package org.valkyrienskies.eureka.math

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MixinWiringTest {

    @Test
    fun commonMixinJsonRegistersImmersiveAircraftCompat() {
        val json = Files.readString(mixinJson())
        assertTrue(json.contains("compat.MixinImmersiveAircraftVehicle"))
        assertTrue(json.contains("EurekaMixinConfigPlugin"))
        val mixin = Files.readString(iaMixin())
        assertTrue(mixin.contains("tick()V"))
        assertTrue(mixin.contains("m_8119_()V"), "Forge dedicated IA VehicleEntity.tick is SRG m_8119_")
    }

    @Test
    fun commonMixinJsonRegistersSimplePlanesCompat() {
        val json = Files.readString(mixinJson())
        assertTrue(json.contains("compat.MixinSimplePlanesVehicle"))
        val plugin = Files.readString(pluginJava())
        assertTrue(plugin.contains("MixinSimplePlanesVehicle"))
        assertTrue(plugin.contains("xyz.przemyk.simpleplanes.entities.PlaneEntity"))
        val mixin = Files.readString(simplePlanesMixin())
        assertTrue(mixin.contains("ShipDeckLandingApplier.apply"))
        assertTrue(mixin.contains("vs\$shouldDrag"))
        assertTrue(mixin.contains("5.0"))
        assertTrue(mixin.contains("SimplePlanesQuat.fromYawPitchRoll"))
        assertTrue(!mixin.contains("rotateY((float) Math.toRadians(-entity.getYRot()))"))
        assertTrue(mixin.contains("tick()V"))
        assertTrue(mixin.contains("m_8119_()V"), "Forge dedicated PlaneEntity.tick is SRG m_8119_")
    }

    @Test
    fun applierReadsEngineIdleFromVehicleInsteadOfForcingParked() {
        val applier = Files.readString(applierKt())
        assertFalse(
            applier.contains("enginesIdle = true"),
            "hardcoding enginesIdle=true discards IA/SP thrust and blocks takeoff from the deck"
        )
        assertTrue(applier.contains("enginesIdleFromVehicle"))
    }

    @Test
    fun shippedLandingEntryPointExists() {
        val result = ShipDeckLanding.correct(
            ShipDeckLanding.PlaneState(
                position = org.joml.Vector3d(),
                velocity = org.joml.Vector3d(),
                yawDeg = 0.0,
                pitchDeg = -4.0,
                rollDeg = 0.0,
                aabbHalfWidth = 0.8,
                aabbHeight = 0.8,
                extraBoxes = emptyList(),
                enginesIdle = true
            ),
            ShipDeckLanding.ShipFrame(
                shipToWorld = org.joml.Matrix4d(),
                worldToShip = org.joml.Matrix4d(),
                rotation = org.joml.Quaterniond(),
                linearVelocity = org.joml.Vector3d(),
                angularVelocity = org.joml.Vector3d(),
                comWorld = org.joml.Vector3d(),
                deckYInShip = 0.0
            ),
            ShipDeckLanding.Params(groundPitchDeg = 4.0)
        )
        assertTrue(result.onGround)
        assertTrue(kotlin.math.abs(result.signedPenetration) < 0.08)
    }

    private fun applierKt(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "kotlin", "org", "valkyrienskies", "eureka", "compat", "immersiveaircraft", "ShipDeckLandingApplier.kt"),
        Path.of("common", "src", "main", "kotlin", "org", "valkyrienskies", "eureka", "compat", "immersiveaircraft", "ShipDeckLandingApplier.kt")
    )

    private fun mixinJson(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "resources", "vs_eureka-common.mixins.json"),
        Path.of("common", "src", "main", "resources", "vs_eureka-common.mixins.json")
    )

    private fun pluginJava(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "EurekaMixinConfigPlugin.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "EurekaMixinConfigPlugin.java")
    )

    private fun iaMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinImmersiveAircraftVehicle.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinImmersiveAircraftVehicle.java")
    )

    private fun simplePlanesMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinSimplePlanesVehicle.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinSimplePlanesVehicle.java")
    )

    private fun firstExisting(vararg candidates: Path): Path {
        for (p in candidates) {
            if (Files.exists(p)) return p
        }
        return candidates[0]
    }
}
