package org.valkyrienskies.eureka.math

import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * Drives the shipped [ShipDeckLanding.correct] from a rest-on-deck start, after applying
 * Immersive Aircraft's world-space landing lerp (the real hostile input). Does not
 * reimplement the correction, mock it, or start from an already-fixed pose.
 */
class ShipDeckLandingTest {

    @Test
    fun identityShipIdlePlaneStaysOnDeckWithoutFlipping() {
        val ship = shipFrame()
        var plane = restOnDeck(ship)
        val params = PARAMS
        var maxAbsRelPitchErr = 0.0
        var maxAbsRelRoll = 0.0
        var maxAbsPen = 0.0

        repeat(TICKS) {
            plane = hostileThenCorrect(plane, ship, params)
            val rel = ShipDeckLanding.deckRelativeEuler(
                plane.yawDeg, plane.pitchDeg, plane.rollDeg, ship.rotation
            )
            maxAbsRelPitchErr = max(maxAbsRelPitchErr, abs(rel.pitchDeg - (-GROUND_PITCH)))
            maxAbsRelRoll = max(maxAbsRelRoll, abs(rel.rollDeg))
            maxAbsPen = max(maxAbsPen, abs(ShipDeckLanding.signedPenetration(plane, ship)))
        }

        assertTrue(maxAbsRelPitchErr < BACKFLIP_BOUND, "pitch err $maxAbsRelPitchErr")
        assertTrue(maxAbsRelRoll < BACKFLIP_BOUND, "roll $maxAbsRelRoll")
        assertTrue(maxAbsPen < PENETRATION_BOUND, "penetration $maxAbsPen")
        assertEquals(0.0, plane.position.x, 0.2)
        assertEquals(0.0, plane.position.z, 0.2)
    }

    @Test
    fun tiltedShipIdlePlaneDoesNotBackflipOrClip() {
        val tilt = Quaterniond().rotateZ(Math.toRadians(12.0))
        val ship = shipFrame(rotation = tilt)
        var plane = restOnDeck(ship)
        val params = PARAMS
        var maxAbsRelPitchErr = 0.0
        var maxAbsRelRoll = 0.0
        var maxAbsPen = 0.0

        repeat(TICKS) {
            plane = hostileThenCorrect(plane, ship, params)
            val rel = ShipDeckLanding.deckRelativeEuler(
                plane.yawDeg, plane.pitchDeg, plane.rollDeg, ship.rotation
            )
            maxAbsRelPitchErr = max(maxAbsRelPitchErr, abs(rel.pitchDeg - (-GROUND_PITCH)))
            maxAbsRelRoll = max(maxAbsRelRoll, abs(rel.rollDeg))
            maxAbsPen = max(maxAbsPen, abs(ShipDeckLanding.signedPenetration(plane, ship)))
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
        val tilt = Quaterniond().rotateZ(Math.toRadians(12.0))
        val ship = shipFrame(rotation = tilt)
        var plane = restOnDeck(ship)
        repeat(TICKS) {
            plane = ShipDeckLanding.immersiveAircraftWorldSpaceLandingStep(
                plane, GROUND_PITCH, onGround = true
            )
        }
        val rel = ShipDeckLanding.deckRelativeEuler(
            plane.yawDeg, plane.pitchDeg, plane.rollDeg, ship.rotation
        )
        assertTrue(
            abs(rel.rollDeg) > BACKFLIP_BOUND,
            "hostile world-space lerp should leave deck-relative roll > bound, was ${rel.rollDeg}"
        )
    }

    @Test
    fun translatingShipCarriesIdlePlaneWithoutFlipOrClip() {
        val vel = Vector3d(4.0, 0.0, 0.0)
        var com = Vector3d(0.0, 0.0, 0.0)
        var ship = shipFrame(com = com, linearVelocity = vel)
        var plane = restOnDeck(ship)
        val params = PARAMS
        var maxAbsRelPitchErr = 0.0
        var maxAbsRelRoll = 0.0
        var maxAbsPen = 0.0
        var maxShipSpaceDrift = 0.0

        repeat(TICKS) {
            plane = hostileThenCorrect(plane, ship, params)
            val shipPos = ship.worldToShip.transformPosition(Vector3d(plane.position))
            maxShipSpaceDrift = max(maxShipSpaceDrift, abs(shipPos.x) + abs(shipPos.z))
            val rel = ShipDeckLanding.deckRelativeEuler(
                plane.yawDeg, plane.pitchDeg, plane.rollDeg, ship.rotation
            )
            maxAbsRelPitchErr = max(maxAbsRelPitchErr, abs(rel.pitchDeg - (-GROUND_PITCH)))
            maxAbsRelRoll = max(maxAbsRelRoll, abs(rel.rollDeg))
            maxAbsPen = max(maxAbsPen, abs(ShipDeckLanding.signedPenetration(plane, ship)))

            com = Vector3d(com).add(Vector3d(vel).mul(DT))
            ship = shipFrame(com = com, linearVelocity = vel)
        }

        assertTrue(maxAbsRelPitchErr < BACKFLIP_BOUND, "translating pitch err $maxAbsRelPitchErr")
        assertTrue(maxAbsRelRoll < BACKFLIP_BOUND, "translating roll $maxAbsRelRoll")
        assertTrue(maxAbsPen < PENETRATION_BOUND, "translating penetration $maxAbsPen")
        assertTrue(
            maxShipSpaceDrift < 0.75,
            "plane was not carried with the ship; ship-space drift $maxShipSpaceDrift"
        )
        assertEquals(vel.x, plane.velocity.x, 0.15)
    }

    private fun hostileThenCorrect(
        plane: ShipDeckLanding.PlaneState,
        ship: ShipDeckLanding.ShipFrame,
        params: ShipDeckLanding.Params
    ): ShipDeckLanding.PlaneState {
        // IA world-space landing (wrong on a ship) then vanilla-style integrate, then the
        // shipped correction. Starts from rest-on-deck, not from a pre-fixed pose.
        val afterIa = ShipDeckLanding.immersiveAircraftWorldSpaceLandingStep(
            plane, params.groundPitchDeg, onGround = true
        )
        // Unoccupied IA ground decay is 0.75 and damps WORLD velocity, so a translating
        // ship will leave the plane behind unless the shipped correction re-matches it.
        val damped = afterIa.copy(velocity = Vector3d(afterIa.velocity).mul(0.75))
        val moved = damped.copy(
            position = Vector3d(damped.position).add(Vector3d(damped.velocity).mul(DT))
        )
        val result = ShipDeckLanding.correct(moved, ship, params)
        return moved.copy(
            position = result.position,
            velocity = result.velocity,
            yawDeg = result.yawDeg,
            pitchDeg = result.pitchDeg,
            rollDeg = result.rollDeg
        )
    }

    private fun restOnDeck(ship: ShipDeckLanding.ShipFrame): ShipDeckLanding.PlaneState {
        val qRel = ShipDeckLanding.iaRotation(0.0, -GROUND_PITCH, 0.0)
        val qWorld = Quaterniond(ship.rotation).mul(qRel)
        val euler = ShipDeckLanding.extractIaEuler(qWorld)
        val pos = ship.shipToWorld.transformPosition(Vector3d(0.0, ship.deckYInShip, 0.0))
        val r = Vector3d(pos).sub(ship.comWorld)
        val vel = Vector3d(ship.linearVelocity).add(Vector3d(ship.angularVelocity).cross(r))
        return ShipDeckLanding.PlaneState(
            position = pos,
            velocity = vel,
            yawDeg = euler.yawDeg,
            pitchDeg = euler.pitchDeg,
            rollDeg = euler.rollDeg,
            aabbHalfWidth = 0.875,
            aabbHeight = 0.85,
            extraBoxes = BIPLANE_EXTRAS,
            enginesIdle = true
        )
    }

    private fun shipFrame(
        com: Vector3d = Vector3d(),
        rotation: Quaterniond = Quaterniond(),
        linearVelocity: Vector3d = Vector3d(),
        angularVelocity: Vector3d = Vector3d(),
        deckYInShip: Double = 0.0
    ): ShipDeckLanding.ShipFrame {
        val shipToWorld = Matrix4d().translationRotate(com.x, com.y, com.z, rotation)
        val worldToShip = Matrix4d(shipToWorld).invert()
        return ShipDeckLanding.ShipFrame(
            shipToWorld = shipToWorld,
            worldToShip = worldToShip,
            rotation = Quaterniond(rotation),
            linearVelocity = Vector3d(linearVelocity),
            angularVelocity = Vector3d(angularVelocity),
            comWorld = Vector3d(com),
            deckYInShip = deckYInShip
        )
    }

    companion object {
        private const val GROUND_PITCH = 4.0
        private const val TICKS = 80
        private const val DT = 1.0 / 20.0
        private const val BACKFLIP_BOUND = 8.0
        private const val PENETRATION_BOUND = 0.08
        private val PARAMS = ShipDeckLanding.Params(groundPitchDeg = GROUND_PITCH)
        private val BIPLANE_EXTRAS = listOf(
            ShipDeckLanding.LocalBox(3.0, 0.65, 1.125, 1.0, 0.7),
            ShipDeckLanding.LocalBox(-3.0, 0.65, 1.125, 1.0, 0.7),
            ShipDeckLanding.LocalBox(0.0, 0.65, -2.0, 0.6, 0.5)
        )
    }
}
