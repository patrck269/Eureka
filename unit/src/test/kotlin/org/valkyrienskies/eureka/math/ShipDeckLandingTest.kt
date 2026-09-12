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
