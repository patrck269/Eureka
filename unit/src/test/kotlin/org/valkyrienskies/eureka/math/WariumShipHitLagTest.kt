package org.valkyrienskies.eureka.math

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives [WariumShipHitLag] occupancy predicates — the same functions the
 * live hook/mixins call. Hull block type is not an input.
 */
class WariumShipHitLagTest {

    @Test
    fun occupiedShipSkipsClientFloodUnoccupiedBounceAndFlyingIsNotDiscarded() {
        val ciws = "net.mcreator.crustychunks.entity.HugeAIBulletEntity"
        val onShip = true
        val empty = false
        val occupied = true
        val flying = false
        val stuck = true

        assertFalse(
            WariumShipHitLag.shouldSkipClientFlood(ciws, onShip, occupied),
            "do not intercept DespawningBullet; per-particle VS/reflection made occupied lag worse"
        )
        assertFalse(
            WariumShipHitLag.shouldSkipClientFlood(ciws, onShip, empty),
            "empty hull: keep bounce/FX path"
        )
        assertFalse(
            WariumShipHitLag.shouldCullAfterHullHit(ciws, onShip, occupied),
            "occupied hull hit must bounce with stock FX and sounds"
        )
        assertTrue(
            WariumShipHitLag.shouldSkipExtraHitProjectiles(ciws, onShip, occupied),
            "occupied: skip extra getArrow spawns, keep original bounce/FX/sounds"
        )
        assertFalse(
            WariumShipHitLag.shouldSkipExtraHitProjectiles(ciws, onShip, empty),
            "empty hull still spawns stock extras"
        )
        assertTrue(
            WariumShipHitLag.shouldSkipOccupiedParticleBurst(ciws, onShip, occupied),
            "occupied: skip sendParticles/HVParticle flood, keep bounce and some sounds"
        )
        assertFalse(WariumShipHitLag.shouldSkipOccupiedParticleBurst(ciws, onShip, empty))
        assertFalse(
            WariumShipHitLag.shouldCullAfterHullHit(ciws, onShip, empty),
            "empty hull still bounces"
        )
        assertFalse(
            WariumShipHitLag.shouldDiscardStuckOnShip(ciws, onShip, flying),
            "flying overlap must not discard (host-ship CIWS)"
        )
        assertTrue(WariumShipHitLag.shouldDiscardStuckOnShip(ciws, onShip, stuck))
        assertFalse(
            WariumShipHitLag.shouldLookupShipForStuckDiscard(true, flying),
            "flying CIWS must not pay VS ship queries every tick"
        )
        assertFalse(WariumShipHitLag.shouldLookupShipForStuckDiscard(false, stuck))
        assertTrue(WariumShipHitLag.shouldLookupShipForStuckDiscard(true, stuck))
        assertFalse(
            WariumShipHitLag.shouldSkipHitFx(onShip, occupied),
            "occupied hull must still take Warium block damage"
        )
        assertFalse(
            WariumShipHitLag.shouldSkipHitFx(onShip, empty),
            "unoccupied bounce FX stays"
        )
        assertTrue(WariumShipHitLag.shouldSkipPlayerHit(ciws, occupied))
        assertFalse(WariumShipHitLag.shouldSkipPlayerHit(ciws, empty))
        assertFalse(
            WariumShipHitLag.shouldSkipClientFlood(
                "net.minecraft.world.entity.projectile.Arrow",
                onShip,
                occupied
            )
        )
        assertFalse(WariumShipHitLag.isWariumMunition("projecte:dark_matter_block"))
        val occupiedSteel = WariumShipHitLag.shouldSkipClientFlood(ciws, onShip, occupied)
        val occupiedDarkMatter = WariumShipHitLag.shouldSkipClientFlood(ciws, onShip, occupied)
        assertTrue(occupiedSteel == occupiedDarkMatter)
        assertTrue(
            WariumShipHitLag.shouldSkipShipyardMove(ciws),
            "CIWS must not be moved into shipyard next to an occupant"
        )
        assertFalse(
            WariumShipHitLag.shouldSkipShipyardMove("net.minecraft.world.entity.projectile.Arrow"),
            "vanilla arrows still stick/render on ships"
        )
    }

    @Test
    fun shipyardManagedRoundCountsAsOnShipWhenWorldAabbMisses() {
        val ciws = "net.mcreator.crustychunks.entity.HugeAIBulletEntity"
        val worldAabbHitsShip = false
        val shipyardManaged = true
        val occupied = true
        val empty = false

        assertTrue(
            WariumShipHitLag.isOnShip(worldAabbHitsShip, shipyardManaged),
            "VS moves CIWS arrows into shipyard; world AABB miss must still count as on-ship"
        )
        assertFalse(WariumShipHitLag.isOnShip(false, false))
        assertFalse(
            WariumShipHitLag.shouldSkipClientFlood(
                ciws,
                WariumShipHitLag.isOnShip(worldAabbHitsShip, shipyardManaged),
                occupied
            ),
            "do not intercept in-flight tracers"
        )
        assertFalse(
            WariumShipHitLag.shouldCullAfterHullHit(
                ciws,
                WariumShipHitLag.isOnShip(worldAabbHitsShip, shipyardManaged),
                occupied
            ),
            "shipyard-managed occupied hull still bounces with FX/sounds"
        )
        assertFalse(
            WariumShipHitLag.shouldSkipClientFlood(
                ciws,
                WariumShipHitLag.isOnShip(worldAabbHitsShip, shipyardManaged),
                empty
            ),
            "empty hull bounce stays"
        )
        assertFalse(
            WariumShipHitLag.shouldSkipClientFlood(
                ciws,
                WariumShipHitLag.isOnShip(false, false),
                occupied
            ),
            "world miss and not shipyard-managed is not on the ship"
        )
        assertTrue(
            WariumShipHitLag.shouldDiscardStuckOnShip(
                ciws,
                WariumShipHitLag.isOnShip(false, true),
                true
            ),
            "stuck shipyard rounds must still discard"
        )
        assertFalse(
            WariumShipHitLag.shouldDiscardStuckOnShip(
                ciws,
                WariumShipHitLag.isOnShip(false, true),
                false
            ),
            "flying host-ship CIWS must not discard"
        )
        assertFalse(
            WariumShipHitLag.shouldSkipHitFx(
                WariumShipHitLag.isOnShip(false, true),
                occupied
            ),
            "shipyard occupied hull still takes damage"
        )
        assertTrue(
            WariumShipHitLag.shouldSkipPlayerHit(
                ciws,
                occupied
            ),
            "occupied lag path is player-hit, not hull FX"
        )
    }
}
