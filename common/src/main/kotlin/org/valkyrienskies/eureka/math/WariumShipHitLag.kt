package org.valkyrienskies.eureka.math

/**
 * Occupied high-volume CIWS: skip VS shipyard moves. Hull hits bounce
 * with stock FX and sounds. Do not intercept in-flight tracers. Tests
 * and the live hook share these predicates.
 */
object WariumShipHitLag {

    fun isWariumMunition(className: String): Boolean {
        if (!className.startsWith("net.mcreator.crustychunks.entity.")) {
            return false
        }
        return className.contains("Bullet") ||
            className.contains("Projectile") ||
            className.contains("Fragment") ||
            className.contains("Flak") ||
            className.contains("Shell")
    }

    /**
     * VS moves colliding arrows into shipyard space. World-AABB ship queries
     * miss those rounds; shipyard chunk ownership still counts as on-ship.
     */
    fun isOnShip(worldAabbHitsShip: Boolean, shipyardManaged: Boolean): Boolean {
        return worldAabbHitsShip || shipyardManaged
    }

    /**
     * Do not intercept DespawningBulletProcedure. Per-particle occupancy
     * lookups made occupied CIWS lag worse than the shipyard skip alone.
     */
    fun shouldSkipClientFlood(
        className: String,
        intersectsShip: Boolean,
        playerAboard: Boolean
    ): Boolean {
        return false
    }

    fun shouldDiscardStuckOnShip(
        className: String,
        intersectsShip: Boolean,
        stuck: Boolean
    ): Boolean {
        return isWariumMunition(className) && intersectsShip && stuck
    }

    /**
     * AbstractArrow.tick runs this path for every arrow. Flying CIWS must
     * not query VS ships; only stuck Warium rounds need that lookup.
     */
    fun shouldLookupShipForStuckDiscard(isWarium: Boolean, stuck: Boolean): Boolean {
        return isWarium && stuck
    }

    fun shouldSkipHitFx(intersectsShip: Boolean, playerAboard: Boolean): Boolean {
        return false
    }

    fun shouldSkipPlayerHit(className: String, playerAboard: Boolean): Boolean {
        return isWariumMunition(className) && playerAboard
    }

    /**
     * VS Projectile.onHitBlock sends arrows into shipyard so they stick.
     * CIWS volume next to an occupant freezes the client render thread.
     * Keep hull DamagesProcedure; do not move Warium rounds into shipyard.
     */
    fun shouldSkipShipyardMove(className: String): Boolean {
        return isWariumMunition(className)
    }

    /**
     * Occupied hull hits must bounce with stock FX and sounds. Never discard
     * or cancel HugeBulletHitProcedure on impact.
     */
    fun shouldCullAfterHullHit(
        className: String,
        intersectsShip: Boolean,
        playerAboard: Boolean
    ): Boolean {
        return false
    }

    /**
     * HugeBulletHitProcedure calls Projectile.getArrow + addFreshEntity.
     * Those extras multiply the volley at the camera. Skip only the spawn;
     * original bounce, sounds, and hit particles stay.
     */
    fun shouldSkipExtraHitProjectiles(
        className: String,
        intersectsShip: Boolean,
        playerAboard: Boolean
    ): Boolean {
        return isWariumMunition(className) && intersectsShip && playerAboard
    }

    /**
     * One hull hit plays sendParticles twice plus HVParticle. Occupied
     * volleys freeze the occupant client. Skip those bursts; bounce and
     * a throttled sound stay.
     */
    fun shouldSkipOccupiedParticleBurst(
        className: String,
        intersectsShip: Boolean,
        playerAboard: Boolean
    ): Boolean {
        return isWariumMunition(className) && intersectsShip && playerAboard
    }
}
