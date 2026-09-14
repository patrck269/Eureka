package org.valkyrienskies.eureka.math

import org.joml.Matrix4dc
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import kotlin.math.abs

/**
 * Ship-space landing kinematics for Immersive Aircraft vehicles on Valkyrien Skies ships.
 *
 * Immersive Aircraft assumes world-Y ground: it lerps pitch toward `-groundPitch` and roll
 * toward 0, then moves with vanilla `onGround` plus extra world-AABB volumes that VS entity
 * collision does not see. On a transformed ship that produces a backflip (world-space pitch
 * fighting ship omega / glide) and persistent deck clipping.
 *
 * This function is the shipped correction: deck-relative orientation, contact along ship-up,
 * and velocity matched to the ship so a landed idle plane is carried instead of flipped.
 */
object ShipDeckLanding {

    data class LocalBox(
        val x: Double,
        val y: Double,
        val z: Double,
        val width: Double,
        val height: Double
    )

    data class PlaneState(
        val position: Vector3d,
        val velocity: Vector3d,
        val yawDeg: Double,
        val pitchDeg: Double,
        val rollDeg: Double,
        val aabbHalfWidth: Double,
        val aabbHeight: Double,
        val extraBoxes: List<LocalBox>,
        val enginesIdle: Boolean
    )

    data class ShipFrame(
        val shipToWorld: Matrix4dc,
        val worldToShip: Matrix4dc,
        val rotation: Quaterniondc,
        val linearVelocity: Vector3dc,
        val angularVelocity: Vector3dc,
        val comWorld: Vector3dc,
        val deckYInShip: Double
    )

    data class Params(
        val groundPitchDeg: Double,
        val orientationLerp: Double = ORIENTATION_LERP,
        /**
         * When true, add `(vShip - vel)` to position this tick (both in blocks/tick).
         * Must be true only when VS EntityDragger is suppressed for the entity;
         * otherwise the plane is carried twice. Policy lives in [ShipDeckBridge].
         */
        val integrateShipCarryIntoPosition: Boolean = ShipDeckBridge.integrateKinematicPositionCarry()
    )

    data class Result(
        val position: Vector3d,
        val velocity: Vector3d,
        val yawDeg: Double,
        val pitchDeg: Double,
        val rollDeg: Double,
        val onGround: Boolean,
        val signedPenetration: Double,
        val deckRelativePitchDeg: Double,
        val deckRelativeRollDeg: Double
    )

    /**
     * Apply one tick of ship-space landing correction to a plane that has already had
     * Immersive Aircraft's world-space landing step (and/or VS drag) applied.
     */
    fun correct(plane: PlaneState, ship: ShipFrame, params: Params): Result {
        val shipUpWorld = ship.rotation.transform(Vector3d(0.0, 1.0, 0.0))

        val oriented = if (plane.enginesIdle) {
            val qVehicle = iaRotation(plane.yawDeg, plane.pitchDeg, plane.rollDeg)
            val qRel = Quaterniond(ship.rotation).invert().mul(qVehicle)
            val rel = extractIaEuler(qRel)
            val newPitchRel = (rel.pitchDeg + params.groundPitchDeg) * params.orientationLerp -
                params.groundPitchDeg
            val newRollRel = rel.rollDeg * params.orientationLerp
            val qRelNew = iaRotation(rel.yawDeg, newPitchRel, newRollRel)
            val qVehicleNew = Quaterniond(ship.rotation).mul(qRelNew)
            val worldEuler = extractIaEuler(qVehicleNew)
            plane.copy(
                yawDeg = worldEuler.yawDeg,
                pitchDeg = worldEuler.pitchDeg,
                rollDeg = worldEuler.rollDeg
            )
        } else {
            // Taxi/takeoff: do not slam pose. Rewriting yaw/pitch every tick fights IA input.
            plane
        }

        val r = Vector3d(oriented.position).sub(ship.comWorld)
        val vShip = Vector3d(ship.linearVelocity).add(Vector3d(ship.angularVelocity).cross(r))
        val newVelocity = if (oriented.enginesIdle) {
            Vector3d(vShip)
        } else {
            val relVel = Vector3d(oriented.velocity).sub(vShip)
            val intoDeck = relVel.dot(shipUpWorld)
            if (intoDeck < 0.0) {
                relVel.add(Vector3d(shipUpWorld).mul(-intoDeck))
            }
            Vector3d(vShip).add(relVel)
        }

        val newPosition = Vector3d(oriented.position)
        if (oriented.enginesIdle && params.integrateShipCarryIntoPosition) {
            // Velocities are blocks/tick. IA already moved by `oriented.velocity`;
            // add the missing ship-carry so net motion this tick is vShip.
            newPosition.add(Vector3d(vShip).sub(oriented.velocity))
        }
        val carried = oriented.copy(position = newPosition, velocity = newVelocity)
        val penetrationBefore = signedPenetration(carried, ship)
        if (penetrationBefore > 0.0) {
            newPosition.add(Vector3d(shipUpWorld).mul(penetrationBefore))
        } else if (penetrationBefore > -CONTACT_SNAP && oriented.enginesIdle) {
            newPosition.add(Vector3d(shipUpWorld).mul(penetrationBefore))
        }

        val settled = oriented.copy(position = newPosition, velocity = newVelocity)
        val penetration = signedPenetration(settled, ship)
        val relAfter = extractIaEuler(
            Quaterniond(ship.rotation).invert().mul(
                iaRotation(settled.yawDeg, settled.pitchDeg, settled.rollDeg)
            )
        )

        return Result(
            position = Vector3d(settled.position),
            velocity = Vector3d(settled.velocity),
            yawDeg = wrapDeg(settled.yawDeg),
            pitchDeg = wrapDeg(settled.pitchDeg),
            rollDeg = wrapDeg(settled.rollDeg),
            onGround = abs(penetration) <= ON_GROUND_SLOP,
            signedPenetration = penetration,
            deckRelativePitchDeg = wrapDeg(relAfter.pitchDeg),
            deckRelativeRollDeg = wrapDeg(relAfter.rollDeg)
        )
    }

    /**
     * True only when the plane is in deck contact. Nearby / above a ship must
     * not run landing correction (that glues velocity to the ship).
     */
    fun shouldApplyLandingCorrection(
        signedPenetration: Double,
        captureMeters: Double = CONTACT_SNAP
    ): Boolean {
        return signedPenetration >= -captureMeters
    }

    /**
     * Occupied planes used vanilla IA + VS drag before the backflip glue.
     * Only empty vehicles get deck correction. Catapult launch skips glue so
     * the pad impulse is not eaten.
     */
    fun shouldApplyDeckGlue(occupied: Boolean, catapultLaunch: Boolean = false): Boolean {
        return !occupied && !catapultLaunch
    }

    fun weldWorldPosition(localShip: Vector3dc, shipToWorld: Matrix4dc): Vector3d {
        return shipToWorld.transformPosition(Vector3d(localShip))
    }

    /**
     * Rest Y in ship space is the collision top of the supporting block, not
     * always {@code blockY + 1}. A 0.5-high catapult pad is {@code blockY + 0.5}.
     */
    fun collisionTopY(blockY: Int, collisionMaxYInBlock: Double): Double {
        return blockY + collisionMaxYInBlock
    }

    /**
     * Parked-weld world position is always the live ship transform. Kinematic
     * carry from [correct] is first-order in velocity and lags a rotating
     * >100 m/s deck by meters per tick.
     */
    fun positionToWriteForParkedWeld(
        weldLocal: Vector3dc,
        shipToWorld: Matrix4dc
    ): Vector3d {
        return weldWorldPosition(weldLocal, shipToWorld)
    }

    fun shouldOverwriteWeldWithKinematicCarry(): Boolean = false

    fun unoccupiedWeldFreezesWorldVelocity(): Boolean = true

    fun enginesIdle(
        enginePower: Double = 0.0,
        engineTarget: Double = 0.0,
        taxiInput: Double = 0.0,
        throttle: Double = 0.0,
        occupied: Boolean = true,
        relativeSpeed: Double = 0.0
    ): Boolean {
        if (!occupied) {
            return true
        }
        if (relativeSpeed >= PARK_RELATIVE_SPEED) {
            return false
        }
        return enginePower <= IDLE_EPS &&
            engineTarget <= IDLE_EPS &&
            abs(taxiInput) <= IDLE_EPS &&
            throttle <= IDLE_EPS
    }

    fun enginesIdleFromVehicle(
        vehicle: Any,
        occupied: Boolean = true,
        relativeSpeed: Double = 0.0
    ): Boolean {
        val enginePower = numberMethod(vehicle, "getEnginePower")
        val engineTarget = numberMethod(vehicle, "getEngineTarget")
        val throttle = numberMethod(vehicle, "getThrottle")
        val taxiInput = interpolatedAxis(vehicle, "pressingInterpolatedZ")
        return enginesIdle(enginePower, engineTarget, taxiInput, throttle, occupied, relativeSpeed)
    }

    fun signedPenetration(plane: PlaneState, ship: ShipFrame): Double {
        var minShipY = Double.POSITIVE_INFINITY
        for (aabb in worldCollisionAabbs(plane)) {
            forEachCorner(aabb) { x, y, z ->
                val shipPos = ship.worldToShip.transformPosition(Vector3d(x, y, z))
                if (shipPos.y < minShipY) {
                    minShipY = shipPos.y
                }
            }
        }
        if (minShipY == Double.POSITIVE_INFINITY) {
            return 0.0
        }
        return ship.deckYInShip - minShipY
    }

    fun deckRelativeEuler(yawDeg: Double, pitchDeg: Double, rollDeg: Double, shipRotation: Quaterniondc): Euler {
        val qRel = Quaterniond(shipRotation).invert().mul(iaRotation(yawDeg, pitchDeg, rollDeg))
        return extractIaEuler(qRel)
    }

    fun iaRotation(yawDeg: Double, pitchDeg: Double, rollDeg: Double): Quaterniond {
        return Quaterniond()
            .rotateY(Math.toRadians(-yawDeg))
            .rotateX(Math.toRadians(pitchDeg))
            .rotateZ(Math.toRadians(rollDeg))
    }

    fun extractIaEuler(q: Quaterniondc): Euler {
        val v = Vector3d()
        q.getEulerAnglesYXZ(v)
        return Euler(
            yawDeg = wrapDeg(-Math.toDegrees(v.y)),
            pitchDeg = wrapDeg(Math.toDegrees(v.x)),
            rollDeg = wrapDeg(Math.toDegrees(v.z))
        )
    }

    fun wrapDeg(angle: Double): Double {
        var a = angle % 360.0
        if (a > 180.0) a -= 360.0
        if (a <= -180.0) a += 360.0
        return a
    }

    data class Euler(val yawDeg: Double, val pitchDeg: Double, val rollDeg: Double)

    data class WorldAabb(
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double
    )

    fun worldCollisionAabbs(plane: PlaneState): List<WorldAabb> {
        val boxes = ArrayList<WorldAabb>(plane.extraBoxes.size + 1)
        boxes.add(
            WorldAabb(
                plane.position.x - plane.aabbHalfWidth,
                plane.position.y,
                plane.position.z - plane.aabbHalfWidth,
                plane.position.x + plane.aabbHalfWidth,
                plane.position.y + plane.aabbHeight,
                plane.position.z + plane.aabbHalfWidth
            )
        )
        val rot = iaRotation(plane.yawDeg, plane.pitchDeg, plane.rollDeg)
        for (box in plane.extraBoxes) {
            val local = Vector3d(box.x, box.y, box.z)
            val center = rot.transform(local).add(plane.position)
            val hx = box.width * 0.5
            val hy = box.height * 0.5
            boxes.add(
                WorldAabb(
                    center.x - hx,
                    center.y - hy,
                    center.z - hx,
                    center.x + hx,
                    center.y + hy,
                    center.z + hx
                )
            )
        }
        return boxes
    }

    /**
     * Immersive Aircraft world-space landing lerp (the behavior that is wrong on a ship).
     * Exposed so tests can drive the real hostile input into [correct] rather than starting
     * from an already-fixed pose.
     */
    fun immersiveAircraftWorldSpaceLandingStep(
        plane: PlaneState,
        groundPitchDeg: Double,
        onGround: Boolean
    ): PlaneState {
        if (!onGround) {
            return plane
        }
        val newPitch = (plane.pitchDeg + groundPitchDeg) * ORIENTATION_LERP - groundPitchDeg
        val newRoll = plane.rollDeg * ORIENTATION_LERP
        return plane.copy(pitchDeg = newPitch, rollDeg = newRoll)
    }

    private fun numberMethod(target: Any, name: String): Double {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: return 0.0
        val value = method.invoke(target) ?: return 0.0
        return (value as? Number)?.toDouble() ?: 0.0
    }

    private fun interpolatedAxis(target: Any, fieldName: String): Double {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val field = try {
                cls.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                null
            }
            if (field != null) {
                field.isAccessible = true
                val holder = field.get(target) ?: return 0.0
                return numberMethod(holder, "getSmooth")
            }
            cls = cls.superclass
        }
        return 0.0
    }

    private fun forEachCorner(aabb: WorldAabb, consumer: (Double, Double, Double) -> Unit) {
        consumer(aabb.minX, aabb.minY, aabb.minZ)
        consumer(aabb.minX, aabb.minY, aabb.maxZ)
        consumer(aabb.minX, aabb.maxY, aabb.minZ)
        consumer(aabb.minX, aabb.maxY, aabb.maxZ)
        consumer(aabb.maxX, aabb.minY, aabb.minZ)
        consumer(aabb.maxX, aabb.minY, aabb.maxZ)
        consumer(aabb.maxX, aabb.maxY, aabb.minZ)
        consumer(aabb.maxX, aabb.maxY, aabb.maxZ)
    }

    const val ORIENTATION_LERP = 0.9
    const val CONTACT_SNAP = 0.08
    const val UNOCCUPIED_CAPTURE = 16.0
    const val ON_GROUND_SLOP = 0.06
    const val IDLE_EPS = 0.05
    const val PARK_RELATIVE_SPEED = 0.25
    const val LAUNCH_TAG = "ia_catapult_launch"
}

