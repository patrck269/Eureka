package org.valkyrienskies.eureka.math

import org.joml.Matrix4d
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives the shipped [MtsShipCollision.worldQueryToShip] against a fixture
 * ship + solid deck block. Does not reimplement the transform or hardcode
 * matrices that ignore the fixture.
 */
class MtsShipCollisionTest {

    @Test
    fun deckWorldQueryHitsShipBlockAndBesideMisses() {
        val originX = 100.0
        val originY = 64.0
        val originZ = 200.0
        val shipToWorld = Matrix4d().translation(originX, originY, originZ)
        val worldToShip = Matrix4d(shipToWorld).invert()
        val deckBlockX = 0
        val deckBlockY = 0
        val deckBlockZ = 0
        val ship = MtsShipCollision.ShipQueryFrame(
            id = 7L,
            worldToShip = worldToShip,
            shipToWorld = shipToWorld,
            worldMinX = originX - 1.0,
            worldMinY = originY - 1.0,
            worldMinZ = originZ - 1.0,
            worldMaxX = originX + 1.0,
            worldMaxY = originY + 1.0,
            worldMaxZ = originZ + 1.0
        )
        val isSolid: (MtsShipCollision.ShipBlockQuery) -> Boolean = { query ->
            query.blockX == deckBlockX && query.blockY == deckBlockY && query.blockZ == deckBlockZ
        }

        val onDeck = MtsShipCollision.worldQueryToShip(
            originX + 0.5,
            originY + 0.5,
            originZ + 0.5,
            listOf(ship),
            isSolid
        )
        assertNotNull(onDeck, "world query on the fixture deck must hit the shipyard block")
        assertEquals(ship.id, onDeck!!.shipId)
        assertEquals(deckBlockX, onDeck.blockX)
        assertEquals(deckBlockY, onDeck.blockY)
        assertEquals(deckBlockZ, onDeck.blockZ)

        val redirected = MtsShipCollision.redirectedBlock(
            originX + 0.5,
            originY + 0.5,
            originZ + 0.5,
            listOf(ship),
            isSolid
        )
        assertEquals(onDeck.shipX, redirected.first, 1e-9)
        assertEquals(onDeck.shipY, redirected.second, 1e-9)
        assertEquals(onDeck.shipZ, redirected.third, 1e-9)

        val beside = MtsShipCollision.worldQueryToShip(
            originX + 10.5,
            originY + 0.5,
            originZ + 0.5,
            listOf(ship),
            isSolid
        )
        assertNull(beside, "query beside the fixture ship must not report the deck")

        val besideRedirect = MtsShipCollision.redirectedBlock(
            originX + 10.5,
            originY + 0.5,
            originZ + 0.5,
            listOf(ship),
            isSolid
        )
        assertEquals(originX + 10.5, besideRedirect.first, 1e-9)
        assertEquals(originY + 0.5, besideRedirect.second, 1e-9)
        assertEquals(originZ + 0.5, besideRedirect.third, 1e-9)
    }

    @Test
    fun gluePolicyUsesMtsRiderNotOnlyVanillaIsVehicle() {
        assertFalse(
            ShipDeckLanding.isOccupied(vanillaIsVehicle = false, mtsRiderPresent = false)
        )
        assertTrue(
            ShipDeckLanding.isOccupied(vanillaIsVehicle = false, mtsRiderPresent = true),
            "MTS linked-seat rider must count as occupied"
        )
        assertTrue(ShipDeckLanding.isOccupied(vanillaIsVehicle = true, mtsRiderPresent = false))
        assertTrue(
            ShipDeckLanding.shouldApplyDeckGlue(
                ShipDeckLanding.isOccupied(false, false)
            ),
            "unoccupied on-deck cars still glue"
        )
        assertFalse(
            ShipDeckLanding.shouldApplyDeckGlue(
                ShipDeckLanding.isOccupied(false, true)
            ),
            "occupied MTS must skip glue so the car can drive"
        )
        assertFalse(ShipDeckLanding.vsShouldDrag(landedOnShip = true))
        assertTrue(ShipDeckLanding.vsShouldDrag(landedOnShip = false))
        assertTrue(ShipDeckLanding.isMtsClass("mcinterface1201.BuilderEntityExisting"))
        assertTrue(ShipDeckLanding.isMtsClass("minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics"))
        assertTrue(ShipDeckLanding.isMtsWrapperEntity("mcinterface1201.BuilderEntityExisting"))
        assertFalse(ShipDeckLanding.isMtsWrapperEntity("mcinterface1201.BuilderEntityLinkedSeat"))
        assertFalse(
            ShipDeckLanding.shouldApplyDeckGlue(occupied = true),
            "occupied MTS must not be welded"
        )
        assertTrue(
            ShipDeckLanding.shouldApplyOccupiedDeckCarry(
                occupied = true,
                isMtsWrapper = true,
                onShipDeck = true
            )
        )
        assertFalse(
            ShipDeckLanding.shouldApplyOccupiedDeckCarry(
                occupied = true,
                isMtsWrapper = true,
                onShipDeck = false
            ),
            "occupied cars not on a deck are not carried"
        )
        assertFalse(
            ShipDeckLanding.shouldApplyOccupiedDeckCarry(
                occupied = false,
                isMtsWrapper = true,
                onShipDeck = true
            ),
            "unoccupied cars use glue, not occupied carry"
        )
        assertFalse(
            ShipDeckLanding.shouldApplyOccupiedDeckCarry(
                occupied = true,
                isMtsWrapper = false,
                onShipDeck = true
            ),
            "IA occupied still uses vanilla+VS drag, not MTS physics carry"
        )
        assertTrue(ShipDeckLanding.vsShouldDrag(false), "occupied carry must leave drag on")
    }

    @Test
    fun occupiedCarryFollowsShipWithoutPinningTaxiOffset() {
        val originY = 64.0
        val prevShipToWorld = Matrix4d().translation(0.0, originY, 0.0)
        val prevWorldToShip = Matrix4d(prevShipToWorld).invert()
        val dx = 5.0
        val nowShipToWorld = Matrix4d().translation(dx, originY, 0.0)
        val taxiX = 1.25
        val taxiZ = -2.0
        val worldPos = Vector3d(taxiX, originY + 0.05, taxiZ)
        val taxiMotion = Vector3d(0.18, 0.0, 0.04)
        val carried = MtsShipCollision.occupiedDeckCarry(
            worldPos,
            taxiMotion,
            prevWorldToShip,
            nowShipToWorld
        )
        assertEquals(taxiX + dx, carried.position.x, 1e-9, "physics pos must take the ship translation")
        assertEquals(originY + 0.05, carried.position.y, 1e-9)
        assertEquals(taxiZ, carried.position.z, 1e-9)
        assertEquals(dx, carried.delta.x, 1e-9)
        assertEquals(0.18, carried.motion.x, 1e-9, "taxi motion must not be frozen")
        assertEquals(0.04, carried.motion.z, 1e-9)

        val localBefore = prevWorldToShip.transformPosition(Vector3d(worldPos))
        val nowWorldToShip = Matrix4d(nowShipToWorld).invert()
        val localAfter = nowWorldToShip.transformPosition(Vector3d(carried.position))
        assertEquals(localBefore.x, localAfter.x, 1e-9, "carry is not a weld pin; ship-local taxi offset is kept")
        assertEquals(localBefore.y, localAfter.y, 1e-9)
        assertEquals(localBefore.z, localAfter.z, 1e-9)

        val identity = MtsShipCollision.occupiedDeckCarry(
            Vector3d(worldPos).add(0.18, 0.0, 0.0),
            taxiMotion,
            prevWorldToShip,
            prevShipToWorld
        )
        assertEquals(taxiX + 0.18, identity.position.x, 1e-9, "with a still ship, taxi displacement is kept")
        assertEquals(0.18, identity.motion.x, 1e-9)
    }

    @Test
    fun unguardedOccupiedCarryTwiceDoublesTranslationAndGuardSkipsSecond() {
        val originY = 64.0
        val prevShipToWorld = Matrix4d().translation(0.0, originY, 0.0)
        val prevWorldToShip = Matrix4d(prevShipToWorld).invert()
        val dx = 5.0
        val nowShipToWorld = Matrix4d().translation(dx, originY, 0.0)
        val start = Vector3d(1.0, originY + 0.05, -2.0)
        val motion = Vector3d(0.1, 0.0, 0.0)
        val once = MtsShipCollision.occupiedDeckCarry(start, motion, prevWorldToShip, nowShipToWorld)
        assertEquals(dx, once.delta.x, 1e-9)
        val twice = MtsShipCollision.occupiedDeckCarry(
            once.position,
            once.motion,
            prevWorldToShip,
            nowShipToWorld
        )
        assertEquals(
            start.x + 2.0 * dx,
            twice.position.x,
            1e-9,
            "same prev/now matrices applied twice is T∘T and doubles the ship delta"
        )
        assertFalse(
            MtsShipCollision.alreadyAppliedOccupiedCarry(
                prevWorldToShip,
                nowShipToWorld,
                null,
                null
            )
        )
        assertTrue(
            MtsShipCollision.alreadyAppliedOccupiedCarry(
                prevWorldToShip,
                nowShipToWorld,
                prevWorldToShip,
                nowShipToWorld
            ),
            "guard must recognize this physics frame's matrices"
        )
        val guardedSecond = MtsShipCollision.occupiedDeckCarryGuarded(
            once.position,
            once.motion,
            prevWorldToShip,
            nowShipToWorld,
            prevWorldToShip,
            nowShipToWorld
        )
        assertEquals(once.position.x, guardedSecond.position.x, 1e-9)
        assertEquals(0.0, guardedSecond.delta.x, 1e-9, "guarded second apply must not double the 5-block move")
        val guardedFirst = MtsShipCollision.occupiedDeckCarryGuarded(
            start,
            motion,
            prevWorldToShip,
            nowShipToWorld,
            null,
            null
        )
        assertEquals(once.position.x, guardedFirst.position.x, 1e-9)
        assertEquals(dx, guardedFirst.delta.x, 1e-9)
    }
}
