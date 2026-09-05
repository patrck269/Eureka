package org.valkyrienskies.eureka.math

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

    private fun mixinJson(): Path {
        val fromUnit = Path.of("..", "common", "src", "main", "resources", "vs_eureka-common.mixins.json")
        if (Files.exists(fromUnit)) return fromUnit
        return Path.of("common", "src", "main", "resources", "vs_eureka-common.mixins.json")
    }
}
