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
    fun failedDarkMatterAndArrowStickPatchesAreGone() {
        val json = Files.readString(mixinJson())
        assertFalse(json.contains("MixinWariumProjectileShipHit"))
        assertFalse(json.contains("MixinWariumCIWSDisable"))
        assertTrue(json.contains("MixinWariumArrowTickOnShip"))
        assertFalse(json.contains("compat.MixinWariumBulletTracer"))
        assertFalse(json.contains("compat.MixinWariumSmallBulletHit"))
        assertTrue(json.contains("compat.MixinWariumHugeBulletHit"))
        assertTrue(json.contains("compat.MixinWariumHvParticleHit"))
        assertTrue(json.contains("compat.MixinWariumHugeBulletEntityHit"))
        assertTrue(json.contains("MixinWariumSkipShipyardMove"))
        assertTrue(json.contains("compat.MixinDhProxyUtilServer"), "dedicated-server DH getLevelWrapper must not resolve ClientLevel")
        assertFalse(
            Files.exists(
                firstExisting(
                    Path.of("..", "common", "src", "main", "resources", "data", "valkyrienskies", "vs_mass", "compat", "projecte.json"),
                    Path.of("common", "src", "main", "resources", "data", "valkyrienskies", "vs_mass", "compat", "projecte.json")
                )
            )
        )
        assertFalse(
            Files.exists(
                firstExisting(
                    Path.of("..", "common", "src", "main", "resources", "data", "crusty_chunks", "tags", "blocks", "metals.json"),
                    Path.of("common", "src", "main", "resources", "data", "crusty_chunks", "tags", "blocks", "metals.json")
                )
            )
        )
        assertFalse(
            Files.exists(
                firstExisting(
                    Path.of("..", "common", "src", "main", "resources", "data", "crusty_chunks", "tags", "blocks", "immortal.json"),
                    Path.of("common", "src", "main", "resources", "data", "crusty_chunks", "tags", "blocks", "immortal.json")
                )
            )
        )
        val hook = Files.readString(firstExisting(
            Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "compat", "warium", "WariumShipHitLagHook.java"),
            Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "compat", "warium", "WariumShipHitLagHook.java")
        ))
        assertFalse(hook.contains("shouldAbortForEntity"), "per-particle occupancy lookup flooded the render thread")
        assertFalse(hook.contains("shouldSkipClientFlood"))
        assertTrue(hook.contains("shouldDiscardStuckOnShip"))
        assertTrue(hook.contains("shouldLookupShipForStuckDiscard"))
        val abort = hook.substring(hook.indexOf("abortAndDiscard"))
        assertTrue(
            abort.indexOf("shouldLookupShipForStuckDiscard") < abort.indexOf("getShipsIntersecting"),
            "flying CIWS ticks must not call getShipsIntersecting"
        )
        assertFalse(
            abort.contains("entity.discard()"),
            "discard during tick NPE PersistentEntitySectionManager"
        )
        assertFalse(json.contains("MixinPersistentEntitySectionNullSafe"))
        assertFalse(hook.contains("shouldSkipHitFx"), "do not cancel DamagesProcedure")
        assertFalse(hook.contains("applyOccupiedHullHit"), "discard-during-hit skipped sounds and crashed persistent fire")
        assertTrue(hook.contains("shouldSkipExtraHitProjectiles"))
        assertTrue(hook.contains("shouldSkipOccupiedParticleBurst"))
        assertTrue(hook.contains("shouldSkipOccupiedSound"))
        assertFalse(hook.contains("DamagesProcedure"))
        assertTrue(hook.contains("shouldSkipPlayerHit"))
        assertTrue(hook.contains("playerAboard"))
        assertTrue(hook.contains("getShipManagingPos"), "shipyard CIWS lookup must use chunk ownership, not world AABB")
        assertTrue(hook.contains("getShipManaging("))
        assertTrue(hook.contains("isOnShip"), "stuck discard still needs shipyard-managed rounds")
        assertTrue(hook.contains("shouldSkipShipyardMove"), "live hook must gate VS shipyard arrow moves")
        val tickMixin = Files.readString(firstExisting(
            Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinWariumArrowTickOnShip.java"),
            Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinWariumArrowTickOnShip.java")
        ))
        assertTrue(tickMixin.contains("abortAndDiscard"))
        assertTrue(tickMixin.contains("stuck on a ship"))
        val hugeHit = Files.readString(firstExisting(
            Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumHugeBulletEntityHit.java"),
            Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumHugeBulletEntityHit.java")
        ))
        assertTrue(hugeHit.contains("HugeBulletEntityHitProcedure"))
        assertTrue(hugeHit.contains("shouldSkipPlayerHit"))
        val shipyardMove = Files.readString(firstExisting(
            Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinWariumSkipShipyardMove.java"),
            Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "MixinWariumSkipShipyardMove.java")
        ))
        assertTrue(shipyardMove.contains("AbstractShipyardEntityHandler"))
        assertTrue(shipyardMove.contains("moveEntityFromWorldToShipyard"))
        assertTrue(shipyardMove.contains("cancellable = true"))
        assertTrue(shipyardMove.contains("Level.class.getClassLoader()"))
        assertFalse(shipyardMove.contains("import org.valkyrienskies.eureka.compat.warium"))
        val extra = Files.readString(firstExisting(
            Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumHugeBulletHit.java"),
            Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumHugeBulletHit.java")
        ))
        assertTrue(extra.contains("HugeBulletHitProcedure"))
        assertTrue(extra.contains("shouldSkipExtraHitProjectiles") || extra.contains("skipExtra"))
        assertTrue(extra.contains("m_7967_") || extra.contains("addFreshEntity"))
        assertTrue(extra.contains("m_8767_"))
        assertTrue(extra.contains("m_7785_"))
        assertTrue(extra.contains("invokeWorld") && extra.contains("methodWorld"))
        assertFalse(extra.contains("cancellable = true"), "must not cancel hull-hit execute")
        val hv = Files.readString(firstExisting(
            Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumHvParticleHit.java"),
            Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumHvParticleHit.java")
        ))
        assertTrue(hv.contains("HVParticleProjectileHitsBlockProcedure"))
        assertTrue(hv.contains("cancellable = true"))
        assertTrue(hv.contains("shouldSkipOccupiedParticleBurst"))
        assertTrue(extra.contains("Level.class.getClassLoader()"))
        assertFalse(extra.contains("import org.valkyrienskies.eureka.compat.warium"))
        assertFalse(
            Files.exists(
                firstExisting(
                    Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumBulletTracer.java"),
                    Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumBulletTracer.java")
                )
            )
        )
        assertFalse(
            Files.exists(
                firstExisting(
                    Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumSmallBulletHit.java"),
                    Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWariumSmallBulletHit.java")
                )
            )
        )
        val dh = Files.readString(firstExisting(
            Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinDhProxyUtilServer.java"),
            Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinDhProxyUtilServer.java")
        ))
        assertTrue(dh.contains("ProxyUtil_forge"))
        assertTrue(dh.contains("getLevelWrapper"))
        assertTrue(dh.contains("cancellable = true"))
        assertFalse(dh.contains("ClientLevel"), "dedicated mixin must not mention ClientLevel")
        assertTrue(dh.contains("ServerLevel"))
        assertTrue(dh.contains("getClassLoader()"))
        assertFalse(dh.contains("import org.valkyrienskies.eureka.compat.warium"))
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
    fun ciwsDisableMixinIsNotRegistered() {
        val json = Files.readString(mixinJson())
        assertFalse(json.contains("MixinWariumCIWSDisable"))
        assertFalse(json.contains("CIWSGunProcedure"))
    }

    @Test
    fun commonMixinJsonRegistersImmersiveVehiclesCompat() {
        val json = Files.readString(mixinJson())
        assertTrue(json.contains("compat.MixinImmersiveVehiclesVehicle"))
        assertTrue(json.contains("compat.MixinWrapperWorldShipCollision"))
        val plugin = Files.readString(pluginJava())
        assertTrue(plugin.contains("MixinImmersiveVehiclesVehicle"))
        assertTrue(plugin.contains("MixinWrapperWorldShipCollision"))
        assertTrue(plugin.contains("mcinterface1201.BuilderEntityExisting"))
        val mixin = Files.readString(mtsMixin())
        assertTrue(mixin.contains("vs\$shouldDrag"))
        assertFalse(
            mixin.contains("org.valkyrienskies.eureka.compat"),
            "MTS mixin is merged into mcinterface1201; referencing Eureka classes CNFDEs on dedicated Forge"
        )
        assertFalse(mixin.contains("ShipDeckLandingApplier"))
        val tick = Files.readString(vehicleTick())
        assertTrue(tick.contains("mcinterface1201."))
        assertTrue(tick.contains("minecrafttransportsimulator."))
        assertTrue(tick.contains("BuilderEntityExisting"))
        val entityMixin = Files.readString(entityMixin())
        assertTrue(entityMixin.contains("ShipDeckVehicleTick"))
        val worldMixin = Files.readString(mtsWorldMixin())
        assertTrue(worldMixin.contains("getBlockState"))
        assertTrue(worldMixin.contains("Level.class.getClassLoader()"))
        assertFalse(worldMixin.contains("import org.valkyrienskies.eureka.compat"))
        assertFalse(worldMixin.contains("ShipDeckLandingApplier"))
        val applier = Files.readString(applierKt())
        assertTrue(applier.contains("isOccupied"))
        assertTrue(applier.contains("MtsVehicleSupport.hasRider"))
        assertTrue(applier.contains("writePhysicsPosition"))
        assertTrue(applier.contains("shouldApplyOccupiedDeckCarry"))
        assertTrue(applier.contains("occupiedDeckCarryGuarded"))
        assertTrue(applier.contains("writePhysicsCarry"))
        assertTrue(applier.contains("carryOccupiedMtsAfterPhysics"))
        assertTrue(applier.contains("prevTickTransform"))
        assertTrue(applier.contains("occupiedCarries.clear()"), "consume occupied carry after one physics apply")
        assertFalse(
            Regex("if \\(ship == null \\|\\| findSupportInShip\\(entity, ship\\) == null\\)").containsMatchIn(applier),
            "post-physics support re-check drops carry when the uncarried pos maps off the column"
        )
        val forge = Files.readString(forgeMod())
        assertFalse(
            forge.contains("ShipDeckLandingApplier.pinAllWelds"),
            "Forge TickEvent.END plus mixin TAIL would apply occupied carry twice"
        )
        val hook = Files.readString(mtsHook())
        assertTrue(hook.contains("MtsShipCollision.worldQueryToShip"))
        assertTrue(hook.contains("clipIncludeShips"))
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
        assertTrue(tick.contains("SimplePlanesQuat"))
        assertTrue(tick.contains("fromYawPitchRoll"))
        assertTrue(tick.contains("Level.class.getClassLoader()"))
        assertFalse(
            tick.contains("ShipDeckLandingApplier.apply("),
            "VehicleTick must not invoke Applier directly; Forge CNFDEs ShipDeckLanding from Entity mixins"
        )
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
        assertTrue(applier.contains("catapultWeldLocal"), "catapult weld must pin pad center so clamp and weld agree")
        assertTrue(applier.contains("cancelInterpolation"), "weld must cancel IA 10-tick lerp under acceleration")
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
        assertFalse(json.contains("MixinCameraShipThirdPerson"), "do not cap VS ship F5 zoom")
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

    private fun mtsMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinImmersiveVehiclesVehicle.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinImmersiveVehiclesVehicle.java")
    )

    private fun mtsWorldMixin(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWrapperWorldShipCollision.java"),
        Path.of("common", "src", "main", "java", "org", "valkyrienskies", "eureka", "mixin", "compat", "MixinWrapperWorldShipCollision.java")
    )

    private fun mtsHook(): Path = firstExisting(
        Path.of("..", "common", "src", "main", "kotlin", "org", "valkyrienskies", "eureka", "compat", "mts", "MtsShipCollisionHook.kt"),
        Path.of("common", "src", "main", "kotlin", "org", "valkyrienskies", "eureka", "compat", "mts", "MtsShipCollisionHook.kt")
    )

    private fun forgeMod(): Path = firstExisting(
        Path.of("..", "forge", "src", "main", "kotlin", "org", "valkyrienskies", "eureka", "forge", "EurekaModForge.kt"),
        Path.of("forge", "src", "main", "kotlin", "org", "valkyrienskies", "eureka", "forge", "EurekaModForge.kt")
    )

    private fun firstExisting(vararg candidates: Path): Path {
        for (p in candidates) {
            if (Files.exists(p)) return p
        }
        return candidates[0]
    }
}
