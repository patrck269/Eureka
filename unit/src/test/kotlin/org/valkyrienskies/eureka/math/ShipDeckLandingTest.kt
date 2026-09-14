package org.valkyrienskies.eureka.math

import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * Drives the shipped [ShipDeckBridge.correctLandedPlaneForEntityTick] from a rest-on-deck
 * start, after applying Immersive Aircraft's world-space landing lerp (the real hostile
 * input). Does not reimplement the correction, mock it, or start from an already-fixed pose.
 */
class ShipDeckLandingTest {

    @Test
    fun parkedVehicleIsIdleAndTakeoffCommandsAreNot() {
        assertTrue(ShipDeckLanding.enginesIdle())
        assertTrue(
            ShipDeckLanding.enginesIdleFromVehicle(Any()),
            "unknown vehicle with no engine API must park (idle)"
        )
        assertFalse(
            ShipDeckLanding.enginesIdle(engineTarget = 1.0),
            "IA full throttle is takeoff, same as land"
        )
        assertFalse(
            ShipDeckLanding.enginesIdle(taxiInput = 1.0),
            "IA ground push (W) is taxi, same as land"
        )
        assertFalse(
            ShipDeckLanding.enginesIdle(throttle = 3.0),
            "Simple Planes throttle > 0 is takeoff"
        )
        assertFalse(
            ShipDeckLanding.enginesIdleFromVehicle(FakeImmersiveAircraft(engineTarget = 1.0f))
        )
        assertFalse(
            ShipDeckLanding.enginesIdleFromVehicle(FakeSimplePlanes(throttle = 3))
        )
        assertFalse(
            ShipDeckLanding.enginesIdleFromVehicle(FakeTaxiInput(1.0f))
        )
        assertTrue(
            ShipDeckLanding.enginesIdle(engineTarget = 1.0, occupied = false),
            "unoccupied plane is parked even if engines are still spinning down"
        )
        assertTrue(
            ShipDeckLanding.enginesIdle(occupied = false, relativeSpeed = 5.0),
            "empty planes stay parked even when the ship is at 100 m/s (5 blocks/tick)"
        )
        assertFalse(
            ShipDeckLanding.shouldApplyDeckGlue(occupied = true),
            "occupied planes used vanilla IA+VS before the backflip glue; do not correct them"
        )
        assertTrue(
            ShipDeckLanding.shouldApplyDeckGlue(occupied = false),
            "empty parked planes still need deck glue"
        )
        assertFalse(
            ShipDeckLanding.shouldApplyDeckGlue(occupied = false, catapultLaunch = true),
            "catapult launch tag skips glue so the impulse is not eaten"
        )
        assertTrue(
            ShipDeckLanding.shouldApplyLandingCorrection(-2.0, ShipDeckLanding.UNOCCUPIED_CAPTURE),
            "empty planes 2m off the deck after a 100 m/s tick must still be recaptured"
        )
        assertFalse(
            ShipDeckLanding.shouldApplyLandingCorrection(-2.0),
            "occupied/default contact is still 8cm"
        )
    }

    @Test
    fun unoccupiedLockOnHalfBlockPadRestsAtCollisionTopNotFullBlock() {
        assertEquals(0.5, ShipDeckLanding.collisionTopY(0, 0.5), 1e-9)
        assertEquals(64.5, ShipDeckLanding.collisionTopY(64, 0.5), 1e-9)
        assertEquals(65.0, ShipDeckLanding.collisionTopY(64, 1.0), 1e-9)
        assertTrue(
            kotlin.math.abs(ShipDeckLanding.collisionTopY(64, 0.5) - 65.0) > 0.4,
            "0.5 pad must not sit at blockY+1"
        )
        assertEquals(null, ShipDeckLanding.collisionMaxYOrSkip(true, 1.0),
            "empty collision must not become a full-block rest")
        assertEquals(0.5, ShipDeckLanding.collisionMaxYOrSkip(false, 0.5)!!, 1e-9)
        assertTrue(ShipDeckLanding.isCatapultPad("immersive_aircraft:catapult"))
        assertTrue(ShipDeckLanding.isCatapultPad("catapult"))
        assertFalse(ShipDeckLanding.isCatapultPad("minecraft:oak_slab"))
        assertTrue(ShipDeckLanding.isFlushSit(0.5, false))
        assertTrue(ShipDeckLanding.isFlushSit(1.0, true))
        assertFalse(ShipDeckLanding.isFlushSit(1.0, false))
        val padTop = ShipDeckLanding.collisionTopY(
            10,
            ShipDeckLanding.collisionMaxYOrSkip(false, 0.5)!!
        )
        assertEquals(10.5, padTop, 1e-9)
        assertEquals(10.5, ShipDeckLanding.catapultRestY(10), 1e-9)
        assertEquals(10.5, ShipDeckLanding.weldSitY(10.5, true), 1e-9)
        assertEquals(10.55, ShipDeckLanding.weldSitY(10.5, false), 1e-9)
        assertTrue(
            kotlin.math.abs(ShipDeckLanding.weldSitY(10.5, true) - 11.0) > 0.4,
            "catapult weld must match IA dockY, not blockY+1"
        )
    }

    @Test
    fun shipThirdPersonZoomIsNotCapped() {
        assertEquals(75.0, ShipDeckLanding.capShipThirdPersonDistance(75.0), 1e-9)
        assertEquals(4.0, ShipDeckLanding.capShipThirdPersonDistance(4.0), 1e-9)
        assertEquals(60.0, ShipDeckLanding.capShipThirdPersonDistance(60.0), 1e-9)
    }

    @Test
    fun unoccupiedWeldTracksShipAtHundredMetersPerSecond() {
        val start = physicsShip()
        val local = Vector3d(1.0, start.deckY + 0.05, -2.0)
        val world0 = ShipDeckLanding.weldWorldPosition(local, start.shipToWorld())
        val moved = physicsShip(linearBps = Vector3d(100.0, 0.0, 0.0))
        // Same local coords on a ship that has translated 100 m/s * 1s = 100 blocks in world
        // if we only had 1s of translation on the transform. Use an explicit translated matrix.
        val translated = Matrix4d(start.shipToWorld()).translate(100.0, 0.0, 0.0)
        val world1 = ShipDeckLanding.weldWorldPosition(local, translated)
        assertEquals(100.0, world1.x - world0.x, 1e-6, "weld must follow ship transform, not last-tick velocity")
        assertEquals(world0.y, world1.y, 1e-6)
        assertEquals(world0.z, world1.z, 1e-6)
        assertTrue(moved.linearBps.x >= 100.0)
    }

    @Test
    fun parkedWeldIgnoresKinematicCarryAtExtremeSpeedAndRotation() {
        val local = Vector3d(3.0, 0.05, 1.0)
        val phys = physicsShip(
            com = Vector3d(10.0, 64.0, -5.0),
            rotation = Quaterniond().rotateZ(Math.toRadians(8.0)).rotateY(Math.toRadians(40.0)),
            linearBps = Vector3d(120.0, 15.0, -80.0),
            angularBps = Vector3d(0.5, 4.0, -0.3)
        )
        val weldPos = ShipDeckLanding.weldWorldPosition(local, phys.shipToWorld())
        val leftoverIa = Vector3d(2.0, -0.4, 1.5)
        val plane = restOnDeck(phys).copy(
            position = Vector3d(weldPos),
            velocity = leftoverIa,
            enginesIdle = true
        )
        val kinematic = correctOnDeck(plane, phys)
        assertTrue(
            kinematic.position.distance(weldPos) > 0.5,
            "sanity: 100 m/s kinematic carry is not a weld (drift=${kinematic.position.distance(weldPos)})"
        )
        val written = ShipDeckLanding.positionToWriteForParkedWeld(
            local,
            phys.shipToWorld()
        )
        assertEquals(
            0.0,
            written.distance(weldPos),
            1e-9,
            "parked write must be live shipToWorld*local, not velocity integration"
        )
        assertTrue(ShipDeckLanding.unoccupiedWeldFreezesWorldVelocity())
        assertFalse(ShipDeckLanding.shouldOverwriteWeldWithKinematicCarry())
    }

    @Test
    fun parkedWeldTracksManeuveringShipAtHundredMetersPerSecond() {
        val local = Vector3d(2.0, 0.05, -3.0)
        var com = Vector3d()
        var yaw = 0.0
        val linearBps = Vector3d(120.0, 8.0, -90.0)
        val yawRate = 3.0
        repeat(20) { tick ->
            yaw += yawRate * ShipDeckBridge.SECONDS_PER_TICK
            com = Vector3d(com).add(Vector3d(linearBps).mul(ShipDeckBridge.SECONDS_PER_TICK))
            val phys = physicsShip(
                com = com,
                rotation = Quaterniond().rotateY(yaw),
                linearBps = linearBps,
                angularBps = Vector3d(0.0, yawRate, 0.0)
            )
            val weldPos = ShipDeckLanding.weldWorldPosition(local, phys.shipToWorld())
            val afterIa = Vector3d(weldPos).add(1.2, -0.3, 0.8)
            val plane = restOnDeck(phys).copy(
                position = afterIa,
                velocity = Vector3d(6.0, 0.4, -4.5),
                enginesIdle = true
            )
            val kinematic = correctOnDeck(plane, phys)
            assertTrue(
                kinematic.position.distance(weldPos) > 0.25,
                "tick $tick sanity: IA leftover + carry must not equal the weld"
            )
            val written = ShipDeckLanding.positionToWriteForParkedWeld(
                local,
                phys.shipToWorld()
            )
            assertEquals(
                0.0,
                written.distance(weldPos),
                1e-7,
                "tick $tick parked weld drifted ${written.distance(weldPos)} at 120 m/s"
            )
        }
    }

    @Test
    fun taxiingPlaneKeepsYawAndHorizontalPosition() {
        val phys = physicsShip()
        val taxiing = restOnDeck(phys).copy(
            enginesIdle = false,
            yawDeg = 45.0,
            pitchDeg = -12.0,
            rollDeg = 8.0,
            velocity = Vector3d(0.18, 0.0, 0.18)
        )
        val result = correctOnDeck(taxiing, phys)
        assertEquals(45.0, result.yawDeg, 1e-6, "taxi must not slam yaw; that stutters steering")
        assertEquals(-12.0, result.pitchDeg, 1e-6, "taxi must not slam pitch toward ground sit")
        assertEquals(8.0, result.rollDeg, 1e-6)
        assertEquals(taxiing.position.x, result.position.x, 1e-6, "setPos rewind of x/z is the hitch")
        assertEquals(taxiing.position.z, result.position.z, 1e-6)
        assertEquals(0.18, result.velocity.x, 1e-6)
        assertEquals(0.18, result.velocity.z, 1e-6)
    }

    @Test
    fun throttledPlaneOnDeckKeepsRelativeTakeoffVelocityAndCanLeave() {
        val phys = physicsShip()
        val takeoffVel = Vector3d(0.0, 0.05, 0.30)
        val idleGlued = correctOnDeck(
            restOnDeck(phys).copy(velocity = Vector3d(takeoffVel)),
            phys
        )
        assertEquals(0.0, idleGlued.velocity.z, 1e-6, "idle glue still overwrites thrust")
        assertEquals(0.0, idleGlued.velocity.y, 1e-6)

        var plane = restOnDeck(phys).copy(enginesIdle = false, velocity = Vector3d(takeoffVel))
        val throttled = correctOnDeck(plane, phys)
        assertEquals(takeoffVel.z, throttled.velocity.z, 1e-6)
        assertEquals(takeoffVel.y, throttled.velocity.y, 1e-6)

        var leftDeck = false
        repeat(TICKS) {
            plane = plane.copy(
                enginesIdle = false,
                velocity = Vector3d(takeoffVel),
                position = Vector3d(plane.position).add(takeoffVel)
            )
            val result = correctOnDeck(plane, phys)
            plane = plane.copy(
                position = result.position,
                velocity = ShipDeckBridge.deltaMovementToWrite(result),
                yawDeg = result.yawDeg,
                pitchDeg = result.pitchDeg,
                rollDeg = result.rollDeg,
                enginesIdle = false
            )
            val pen = ShipDeckLanding.signedPenetration(plane, frame(phys))
            if (!ShipDeckLanding.shouldApplyLandingCorrection(pen)) {
                leftDeck = true
            }
        }
        assertTrue(leftDeck, "throttled plane must be able to leave the deck like land takeoff")
    }

    @Test
    fun flyingNearOrAboveDeckIsNotTreatedAsLanded() {
        val phys = physicsShip()
        val onDeck = restOnDeck(phys)
        val onDeckPen = ShipDeckLanding.signedPenetration(onDeck, frame(phys))
        assertTrue(
            ShipDeckLanding.shouldApplyLandingCorrection(onDeckPen),
            "resting on deck must still land (pen=$onDeckPen)"
        )

        val twoMetersUp = onDeck.copy(
            position = Vector3d(onDeck.position).add(0.0, 2.0, 0.0)
        )
        val flyingPen = ShipDeckLanding.signedPenetration(twoMetersUp, frame(phys))
        assertTrue(flyingPen < -0.5, "sanity: 2m above deck is not contact, pen=$flyingPen")
        assertFalse(
            ShipDeckLanding.shouldApplyLandingCorrection(flyingPen),
            "flying 2m above a ship must not be glued (pen=$flyingPen)"
        )

        val beside = onDeck.copy(
            position = Vector3d(onDeck.position).add(4.0, 1.0, 0.0)
        )
        val besidePen = ShipDeckLanding.signedPenetration(beside, frame(phys))
        assertFalse(
            ShipDeckLanding.shouldApplyLandingCorrection(besidePen),
            "flying beside a ship must not be glued (pen=$besidePen)"
        )
    }

    @Test
    fun identityShipIdlePlaneStaysOnDeckWithoutFlipping() {
        val phys = physicsShip()
        var plane = restOnDeck(phys)
        var maxAbsRelPitchErr = 0.0
        var maxAbsRelRoll = 0.0
        var maxAbsPen = 0.0

        repeat(TICKS) {
            plane = hostileThenCorrect(plane, phys)
            val rel = ShipDeckLanding.deckRelativeEuler(
                plane.yawDeg, plane.pitchDeg, plane.rollDeg, phys.rotation
            )
            maxAbsRelPitchErr = max(maxAbsRelPitchErr, abs(rel.pitchDeg - (-GROUND_PITCH)))
            maxAbsRelRoll = max(maxAbsRelRoll, abs(rel.rollDeg))
            maxAbsPen = max(maxAbsPen, abs(ShipDeckLanding.signedPenetration(plane, frame(phys))))
        }

        assertTrue(maxAbsRelPitchErr < BACKFLIP_BOUND, "pitch err $maxAbsRelPitchErr")
        assertTrue(maxAbsRelRoll < BACKFLIP_BOUND, "roll $maxAbsRelRoll")
        assertTrue(maxAbsPen < PENETRATION_BOUND, "penetration $maxAbsPen")
        assertEquals(0.0, plane.position.x, 0.2)
        assertEquals(0.0, plane.position.z, 0.2)
    }

    @Test
    fun tiltedShipIdlePlaneDoesNotBackflipOrClip() {
        val phys = physicsShip(rotation = Quaterniond().rotateZ(Math.toRadians(12.0)))
        var plane = restOnDeck(phys)
        var maxAbsRelPitchErr = 0.0
        var maxAbsRelRoll = 0.0
        var maxAbsPen = 0.0

        repeat(TICKS) {
            plane = hostileThenCorrect(plane, phys)
            val rel = ShipDeckLanding.deckRelativeEuler(
                plane.yawDeg, plane.pitchDeg, plane.rollDeg, phys.rotation
            )
            maxAbsRelPitchErr = max(maxAbsRelPitchErr, abs(rel.pitchDeg - (-GROUND_PITCH)))
            maxAbsRelRoll = max(maxAbsRelRoll, abs(rel.rollDeg))
            maxAbsPen = max(maxAbsPen, abs(ShipDeckLanding.signedPenetration(plane, frame(phys))))
        }

        assertTrue(
            maxAbsRelPitchErr < BACKFLIP_BOUND,
            "tilted pitch err $maxAbsRelPitchErr would be a backflip/world-level sit"
        )
        assertTrue(
            maxAbsRelRoll < BACKFLIP_BOUND,
            "tilted roll $maxAbsRelRoll would be a backflip/world-level sit"
        )
        assertTrue(maxAbsPen < PENETRATION_BOUND, "tilted penetration $maxAbsPen")
    }

    @Test
    fun worldSpaceLandingOnTiltedShipViolatesDeckBoundsWithoutCorrection() {
        val phys = physicsShip(rotation = Quaterniond().rotateZ(Math.toRadians(12.0)))
        var plane = restOnDeck(phys)
        repeat(TICKS) {
            plane = ShipDeckLanding.immersiveAircraftWorldSpaceLandingStep(
                plane, GROUND_PITCH, onGround = true
            )
        }
        val rel = ShipDeckLanding.deckRelativeEuler(
            plane.yawDeg, plane.pitchDeg, plane.rollDeg, phys.rotation
        )
        assertTrue(
            abs(rel.rollDeg) > BACKFLIP_BOUND,
            "hostile world-space lerp should leave deck-relative roll > bound, was ${rel.rollDeg}"
        )
    }

    @Test
    fun translatingShipWritesDeltaMovementInTickUnitsNotBlocksPerSecond() {
        val shipVelBps = Vector3d(4.0, 0.0, 0.0)
        val phys = physicsShip(linearBps = shipVelBps)
        val plane = restOnDeck(phys)
        val result = hostileThenCorrectResult(plane, phys)
        val written = ShipDeckBridge.deltaMovementToWrite(result)
        val expectedTick = shipVelBps.x * ShipDeckBridge.SECONDS_PER_TICK
        assertEquals(expectedTick, written.x, 1e-6)
        assertTrue(abs(written.x - shipVelBps.x) > 1.0, "wrote blocks/s ($written) not tick units")
        assertTrue(ShipDeckBridge.vsDragSuppressedForLandedPlane())
        assertEquals(
            ShipDeckBridge.vsDragSuppressedForLandedPlane(),
            ShipDeckBridge.integrateKinematicPositionCarry()
        )
    }

    @Test
    fun simplePlanesWorldSpaceLandingOnTiltedShipIsCorrected() {
        val phys = physicsShip(rotation = Quaterniond().rotateZ(Math.toRadians(12.0)))
        val spPitch = 5.0
        var plane = restOnDeck(phys).copy(extraBoxes = emptyList())
        var maxAbsRelPitchErr = 0.0
        var maxAbsRelRoll = 0.0
        var maxAbsPen = 0.0

        repeat(TICKS) {
            val afterSp = ShipDeckLanding.immersiveAircraftWorldSpaceLandingStep(
                plane, spPitch, onGround = true
            )
            val damped = afterSp.copy(velocity = Vector3d(afterSp.velocity).mul(0.75))
            val moved = damped.copy(position = Vector3d(damped.position).add(damped.velocity))
            val result = ShipDeckBridge.correctLandedPlaneForEntityTick(
                plane = moved,
                shipToWorld = phys.shipToWorld(),
                worldToShip = phys.worldToShip(),
                rotation = phys.rotation,
                linearVelocityBlocksPerSecond = phys.linearBps,
                angularVelocityBlocksPerSecond = phys.angularBps,
                comWorld = phys.com,
                deckYInShip = phys.deckY,
                groundPitchDeg = spPitch
            )
            val written = ShipDeckBridge.deltaMovementToWrite(result)
            plane = plane.copy(
                position = result.position,
                velocity = written,
                yawDeg = result.yawDeg,
                pitchDeg = result.pitchDeg,
                rollDeg = result.rollDeg,
                extraBoxes = emptyList()
            )
            val rel = ShipDeckLanding.deckRelativeEuler(
                plane.yawDeg, plane.pitchDeg, plane.rollDeg, phys.rotation
            )
            maxAbsRelPitchErr = max(maxAbsRelPitchErr, abs(rel.pitchDeg - (-spPitch)))
            maxAbsRelRoll = max(maxAbsRelRoll, abs(rel.rollDeg))
            maxAbsPen = max(maxAbsPen, abs(ShipDeckLanding.signedPenetration(plane, frame(phys))))
        }

        assertTrue(maxAbsRelPitchErr < BACKFLIP_BOUND, "simple planes pitch err $maxAbsRelPitchErr")
        assertTrue(maxAbsRelRoll < BACKFLIP_BOUND, "simple planes roll $maxAbsRelRoll")
        assertTrue(maxAbsPen < PENETRATION_BOUND, "simple planes penetration $maxAbsPen")
        assertTrue(ShipDeckBridge.vsDragSuppressedForLandedPlane())
    }

    @Test
    fun translatingShipCarriesIdlePlaneWithoutFlipOrClip() {
        val shipVelBps = Vector3d(4.0, 0.0, 0.0)
        var phys = physicsShip(linearBps = shipVelBps)
        var plane = restOnDeck(phys)
        var maxAbsRelPitchErr = 0.0
        var maxAbsRelRoll = 0.0
        var maxAbsPen = 0.0
        var maxShipSpaceDrift = 0.0
        var lastWrittenX = 0.0

        repeat(TICKS) {
            val result = hostileThenCorrectResult(plane, phys)
            val written = ShipDeckBridge.deltaMovementToWrite(result)
            lastWrittenX = written.x
            plane = plane.copy(
                position = result.position,
                velocity = written,
                yawDeg = result.yawDeg,
                pitchDeg = result.pitchDeg,
                rollDeg = result.rollDeg
            )
            val shipPos = phys.worldToShip().transformPosition(Vector3d(plane.position))
            maxShipSpaceDrift = max(maxShipSpaceDrift, abs(shipPos.x) + abs(shipPos.z))
            val rel = ShipDeckLanding.deckRelativeEuler(
                plane.yawDeg, plane.pitchDeg, plane.rollDeg, phys.rotation
            )
            maxAbsRelPitchErr = max(maxAbsRelPitchErr, abs(rel.pitchDeg - (-GROUND_PITCH)))
            maxAbsRelRoll = max(maxAbsRelRoll, abs(rel.rollDeg))
            maxAbsPen = max(maxAbsPen, abs(ShipDeckLanding.signedPenetration(plane, frame(phys))))

            phys = phys.copy(
                com = Vector3d(phys.com).add(Vector3d(shipVelBps).mul(ShipDeckBridge.SECONDS_PER_TICK))
            )
        }

        assertTrue(maxAbsRelPitchErr < BACKFLIP_BOUND, "translating pitch err $maxAbsRelPitchErr")
        assertTrue(maxAbsRelRoll < BACKFLIP_BOUND, "translating roll $maxAbsRelRoll")
        assertTrue(maxAbsPen < PENETRATION_BOUND, "translating penetration $maxAbsPen")
        assertTrue(
            maxShipSpaceDrift < 0.75,
            "plane was not carried with the ship; ship-space drift $maxShipSpaceDrift"
        )
        assertEquals(shipVelBps.x * ShipDeckBridge.SECONDS_PER_TICK, lastWrittenX, 0.02)
        assertTrue(abs(lastWrittenX - shipVelBps.x) > 1.0, "wrote blocks/s into deltaMovement")
    }

    private fun correctOnDeck(
        plane: ShipDeckLanding.PlaneState,
        phys: PhysicsShip
    ): ShipDeckLanding.Result {
        return ShipDeckBridge.correctLandedPlaneForEntityTick(
            plane = plane,
            shipToWorld = phys.shipToWorld(),
            worldToShip = phys.worldToShip(),
            rotation = phys.rotation,
            linearVelocityBlocksPerSecond = phys.linearBps,
            angularVelocityBlocksPerSecond = phys.angularBps,
            comWorld = phys.com,
            deckYInShip = phys.deckY,
            groundPitchDeg = GROUND_PITCH
        )
    }

    private fun hostileThenCorrect(plane: ShipDeckLanding.PlaneState, phys: PhysicsShip): ShipDeckLanding.PlaneState {
        val result = hostileThenCorrectResult(plane, phys)
        val written = ShipDeckBridge.deltaMovementToWrite(result)
        return plane.copy(
            position = result.position,
            velocity = written,
            yawDeg = result.yawDeg,
            pitchDeg = result.pitchDeg,
            rollDeg = result.rollDeg
        )
    }

    private fun hostileThenCorrectResult(
        plane: ShipDeckLanding.PlaneState,
        phys: PhysicsShip
    ): ShipDeckLanding.Result {
        val afterIa = ShipDeckLanding.immersiveAircraftWorldSpaceLandingStep(
            plane, GROUND_PITCH, onGround = true
        )
        val damped = afterIa.copy(velocity = Vector3d(afterIa.velocity).mul(0.75))
        val moved = damped.copy(
            position = Vector3d(damped.position).add(damped.velocity)
        )
        return ShipDeckBridge.correctLandedPlaneForEntityTick(
            plane = moved,
            shipToWorld = phys.shipToWorld(),
            worldToShip = phys.worldToShip(),
            rotation = phys.rotation,
            linearVelocityBlocksPerSecond = phys.linearBps,
            angularVelocityBlocksPerSecond = phys.angularBps,
            comWorld = phys.com,
            deckYInShip = phys.deckY,
            groundPitchDeg = GROUND_PITCH
        )
    }

    private fun restOnDeck(phys: PhysicsShip): ShipDeckLanding.PlaneState {
        val qRel = ShipDeckLanding.iaRotation(0.0, -GROUND_PITCH, 0.0)
        val qWorld = Quaterniond(phys.rotation).mul(qRel)
        val euler = ShipDeckLanding.extractIaEuler(qWorld)
        val pos = phys.shipToWorld().transformPosition(Vector3d(0.0, phys.deckY, 0.0))
        val tickVel = ShipDeckBridge.toDeltaMovement(phys.linearBps)
        return ShipDeckLanding.PlaneState(
            position = pos,
            velocity = tickVel,
            yawDeg = euler.yawDeg,
            pitchDeg = euler.pitchDeg,
            rollDeg = euler.rollDeg,
            aabbHalfWidth = 0.875,
            aabbHeight = 0.85,
            extraBoxes = BIPLANE_EXTRAS,
            enginesIdle = true
        )
    }

    private fun frame(phys: PhysicsShip): ShipDeckLanding.ShipFrame {
        return ShipDeckBridge.shipFrameForEntityTick(
            phys.shipToWorld(),
            phys.worldToShip(),
            phys.rotation,
            phys.linearBps,
            phys.angularBps,
            phys.com,
            phys.deckY
        )
    }

    private fun physicsShip(
        com: Vector3d = Vector3d(),
        rotation: Quaterniond = Quaterniond(),
        linearBps: Vector3d = Vector3d(),
        angularBps: Vector3d = Vector3d(),
        deckY: Double = 0.0
    ) = PhysicsShip(com, rotation, linearBps, angularBps, deckY)

    private data class PhysicsShip(
        val com: Vector3d,
        val rotation: Quaterniond,
        val linearBps: Vector3d,
        val angularBps: Vector3d,
        val deckY: Double
    ) {
        fun shipToWorld(): Matrix4d = Matrix4d().translationRotate(com.x, com.y, com.z, rotation)
        fun worldToShip(): Matrix4d = Matrix4d(shipToWorld()).invert()
    }

    private class FakeImmersiveAircraft(private val engineTarget: Float) {
        fun getEnginePower(): Float = 0.0f
        fun getEngineTarget(): Float = engineTarget
    }

    private class FakeSimplePlanes(private val throttle: Int) {
        fun getThrottle(): Int = throttle
    }

    private class FakeTaxiInput(z: Float) {
        @JvmField
        val pressingInterpolatedZ = SmoothAxis(z)
    }

    private class SmoothAxis(private val value: Float) {
        fun getSmooth(): Float = value
    }

    companion object {
        private const val GROUND_PITCH = 4.0
        private const val TICKS = 80
        private const val BACKFLIP_BOUND = 8.0
        private const val PENETRATION_BOUND = 0.08
        private val BIPLANE_EXTRAS = listOf(
            ShipDeckLanding.LocalBox(3.0, 0.65, 1.125, 1.0, 0.7),
            ShipDeckLanding.LocalBox(-3.0, 0.65, 1.125, 1.0, 0.7),
            ShipDeckLanding.LocalBox(0.0, 0.65, -2.0, 0.6, 0.5)
        )
    }
}
