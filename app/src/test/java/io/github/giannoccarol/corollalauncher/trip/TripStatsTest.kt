package io.github.giannoccarol.corollalauncher.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStatsTest {

    /** About 0.0001 degrees of latitude per 11.1 metres. */
    private val stepDeg = 0.0001

    private fun TripStats.drive(fixes: Int, startMs: Long = 0L, speed: Float = 11.1f) {
        for (i in 0..fixes) {
            add(45.0 + i * stepDeg, 9.0, startMs + i * 1_000L, speed, 5f)
        }
    }

    @Test
    fun haversineMatchesAKnownDistance() {
        // Milan Duomo to Turin Porta Nuova: about 126 km in a straight line.
        val metres = TripStats.distanceM(45.4642, 9.1900, 45.0621, 7.6782)
        assertEquals(126_000.0, metres, 2_000.0)
    }

    @Test
    fun distanceAndMovingTimeAccumulate() {
        val trip = TripStats()
        trip.drive(fixes = 100)
        assertEquals(1_112.0, trip.distanceM, 15.0)
        assertEquals(100_000L, trip.movingMs)
        assertEquals(100_000L, trip.durationMs)
        assertEquals(40.0, trip.averageKmh!!, 1.0)
    }

    @Test
    fun noAverageBeforeAMinuteOfDriving() {
        val trip = TripStats()
        trip.drive(fixes = 30)
        assertNull(trip.averageKmh)
    }

    @Test
    fun standingStillDoesNotStartATrip() {
        val trip = TripStats()
        for (i in 0..30) trip.add(45.0, 9.0, i * 1_000L, 0f, 5f)
        assertFalse(trip.hasTrip)
        assertEquals(0.0, trip.distanceM, 0.0)
    }

    @Test
    fun inaccurateFixesAddNoDistance() {
        val trip = TripStats()
        for (i in 0..20) trip.add(45.0 + i * stepDeg, 9.0, i * 1_000L, 11f, 120f)
        assertEquals(0.0, trip.distanceM, 0.0)
    }

    @Test
    fun impossibleJumpsAreIgnored() {
        val trip = TripStats()
        trip.add(45.0, 9.0, 0L, 11f, 5f)
        trip.add(45.0 + stepDeg, 9.0, 1_000L, 11f, 5f)
        // One degree (about 111 km) in one second.
        trip.add(46.0, 9.0, 2_000L, 11f, 5f)
        assertEquals(11.1, trip.distanceM, 1.0)
    }

    @Test
    fun longStopStartsANewTrip() {
        val trip = TripStats(resetAfterMs = 60_000L)
        trip.drive(fixes = 50)
        val firstTrip = trip.distanceM
        trip.drive(fixes = 10, startMs = 200_000L)
        assertTrue(trip.distanceM < firstTrip)
        assertEquals(10_000L, trip.durationMs)
    }

    @Test
    fun shortStopKeepsTheSameTrip() {
        val trip = TripStats(resetAfterMs = 60_000L)
        trip.drive(fixes = 50)
        val firstTrip = trip.distanceM
        trip.drive(fixes = 10, startMs = 80_000L)
        assertTrue(trip.distanceM > firstTrip)
    }

    @Test
    fun movingNeedsARecentMovingFix() {
        val trip = TripStats()
        trip.drive(fixes = 5)
        assertTrue(trip.isMoving(nowMs = 6_000L))
        assertFalse(trip.isMoving(nowMs = 60_000L))
    }

    @Test
    fun outOfOrderFixesAreDropped() {
        val trip = TripStats()
        trip.drive(fixes = 10)
        val before = trip.distanceM
        trip.add(44.0, 9.0, 5_000L, 11f, 5f)
        assertEquals(before, trip.distanceM, 0.0)
    }
}
