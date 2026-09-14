package org.valkyrienskies.eureka.math

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MixinWiringTest {

    @Test
    fun entityMixinRunsShipDeckTickSoIaModuleDoesNotLoadEurekaClasses() {
        val json = Files.readString(mixinJson())
        assertTrue(json.contains("MixinEntityShipDeckLanding"))
        assertTrue(json.contains("MixinEntitySectionStorageSafe"))
        val entityMixin = Files.readString(entityMixin())
        assertTrue(entityMixin.contains("net.minecraft.world.entity.Entity"))
        assertTrue(entityMixin.contains("tick()V"))
        assertTrue(entityMixin.contains("m_8119_()V"))
        assertTrue(entityMixin.contains("ShipDeckVehicleTick"))
        val ia = Files.readString(iaMixin())
        assertFalse(
            ia.contains("org.valkyrienskies.eureka.compat"),
            "IA mixin is merged into immersive_aircraft; referencing Eureka classes CNFDEs on dedicated Forge"
        )
        assertFalse(
            ia.contains("ShipDeckLandingApplier"),
            "IA mixin is merged into immersive_aircraft; referencing Eureka classes CNFDEs on dedicated Forge"
        )
        assertTrue(ia.contains("vs\$shouldDrag"))
        val sp = Files.readString(simplePlanesMixin())
        assertFalse(
            sp.contains("ShipDeckLandingApplier"),
            "Simple Planes mixin is merged into simpleplanes; same JPMS CNFDE as IA"
        )
        assertTrue(sp.contains("vs\$shouldDrag"))
    }

    @Test
    fun commonMixinJsonRegistersImmersiveAircraftCompat() {
        val json = Files.readString(mixinJson())
        assertTrue(json.contains("compat.MixinImmersiveAircraftVehicle"))
        assertTrue(json.contains("EurekaMixinConfigPlugin"))
        val mixin = Files.readString(iaMixin())
        assertTrue(mixin.contains("vs\$shouldDrag"))
    }

    @Test
    fun commonMixinJsonRegistersSimplePlanesCompat() {
        val json = Files.readString(mixinJson())
        assertTrue(json.contains("compat.MixinSimplePlanesVehicle"))
        val plugin = Files.readString(pluginJava())
        assertTrue(plugin.contains("MixinSimplePlanesVehicle"))
        assertTrue(plugin.contains("xyz.przemyk.simpleplanes.entities.PlaneEntity"))
        val mixin = Files.readString(simplePlanesMixin())
        assertTrue(mixin.contains("vs\$shouldDrag"))
        assertTrue(!mixin.contains("rotateY((float) Math.toRadians(-entity.getYRot()))"))
        val tick = Files.readString(vehicleTick())
        assertTrue(tick.contains("5.0"))
        assertTrue(tick.contains("SimplePlanesQuat.fromYawPitchRoll"))
        assertTrue(tick.contains("immersive_aircraft."))
        assertTrue(tick.contains("xyz.przemyk.simpleplanes."))
    }

    @Test
    fun applierGluesUnoccupiedPlanesEvenWhenNotLocalInstance() {
        val applier = Files.readString(applierKt())
        assertTrue(applier.contains("isVehicle"), "empty planes must glue on dedicated/client without a rider")
        assertTrue(applier.contains("shouldApplyDeckGlue"))
        assertFalse(
            applier.contains("occupied && !entity.isControlledByLocalInstance"),
            "occupied planes must skip glue entirely, not only on the non-controlling side"
        )
    }

    @Test
    fun applierWeldsUnoccupiedPlanesAndSkipsOccupied() {
        val applier = Files.readString(applierKt())
        assertTrue(applier.contains("shouldApplyDeckGlue"))
        assertTrue(
            applier.contains("positionToWriteForParkedWeld"),
            "parked write must use live shipToWorld*local, not last-tick velocity"
        )
        assertTrue(applier.contains("enginesIdle = true"), "empty parked planes are always idle")
        assertTrue(applier.contains("LAUNCH_TAG"))
        assertTrue(applier.contains("pinAllWelds"))
        assertTrue(applier.contains("Vec3.ZERO"), "IA must not inherit ship velocity before its own move()")
        assertFalse(
            applier.contains("integrateShipCarryIntoPosition = true"),
            "kinematic carry after the pin is what slides parked planes at 100 m/s"
        )
        assertTrue(applier.contains("collisionTopY"), "0.5 pads must use voxel top, not a full block")
        assertTrue(applier.contains("isCatapultPad"), "catapult pads use 0.5 rest")
        assertTrue(applier.contains("weldSitY"), "catapult sit Y must match IA dock")
        assertTrue(applier.contains("CATAPULT_HEIGHT"))
        assertTrue(applier.contains("dx in -1..1"), "must find a catapult in a 3x3, not only the entity column")
        assertFalse(
            Regex("isCatapultPad\\(support\\.blockId\\)\\) \\{\\s*welds\\.remove\\(entity\\)\\s*return false").containsMatchIn(applier),
            "skipping glue on a catapult fights VS drag and the pad clamp"
        )
        assertFalse(
            applier.contains("bp.y + 1.0"),
            "unoccupied lock must not rest at blockY+1 on a half-high catapult"
        )
        assertFalse(
            applier.contains("if (shape.isEmpty) 1.0"),
            "empty shipyard collision must not be treated as a full cube"
        )
    }

    @Test
    fun weldRunsAfterAircraftMoveAndAfterShipPhysics() {
        val json = Files.readString(mixinJson())
        assertTrue(json.contains("MixinServerLevelShipDeckWeld"))
        assertTrue(json.contains("MixinClientLevelShipDeckWeld"))
        assertTrue(json.contains("MixinMinecraftServerShipDeckWeld"))
        assertTrue(json.contains("MixinMinecraftShipDeckWeld"))
        val serverLevel = Files.readString(serverLevelMixin())
        assertTrue(serverLevel.contains("tickNonPassenger"))
        assertTrue(serverLevel.contains("ShipDeckVehicleTick"))
        val clientLevel = Files.readString(clientLevelMixin())
        assertTrue(clientLevel.contains("tickNonPassenger"))
        val server = Files.readString(minecraftServerMixin())
        assertTrue(server.contains("priority = 400"))
        assertTrue(server.contains("pinAllWelds"))
        val client = Files.readString(minecraftMixin())
        assertTrue(client.contains("priority = 400"))
        assertTrue(client.contains("pinAllWelds"))
        val entityMixin = Files.readString(entityMixin())
        assertTrue(entityMixin.contains("tick()V"), "Entity.tick TAIL still zeroes velocity before IA move()")
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

    private fun entityMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinEntityShipDeckLanding.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinEntityShipDeckLanding.java")
    )

    private fun serverLevelMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinServerLevelShipDeckWeld.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinServerLevelShipDeckWeld.java")
    )

    private fun clientLevelMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "client", "MixinClientLevelShipDeckWeld.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "client", "MixinClientLevelShipDeckWeld.java")
    )

    private fun minecraftServerMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinMinecraftServerShipDeckWeld.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinMinecraftServerShipDeckWeld.java")
    )

    private fun minecraftMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "client", "MixinMinecraftShipDeckWeld.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "client", "MixinMinecraftShipDeckWeld.java")
    )

    private fun vehicleTick(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "compat", "immersiveaircraft", "ShipDeckVehicleTick.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "compat", "immersiveaircraft", "ShipDeckVehicleTick.java")
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
