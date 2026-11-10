package io.github.giannoccarol.corollalauncher.trip

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Trip computer fed with GPS fixes. Pure logic, no Android types, so it is unit tested.
 *
 * A trip starts with the first movement and ends when the car has been still for
 * [resetAfterMs]; the next movement then starts a new trip. Times are monotonic milliseconds
 * (from `Location.elapsedRealtimeNanos`), so clock changes never break the numbers.
 */
class TripStats(private val resetAfterMs: Long = DEFAULT_RESET_MS) {

    /** Metres travelled in the current trip. */
    var distanceM: Double = 0.0
        private set

    /** Time spent moving, which is what the average speed is based on. */
    var movingMs: Long = 0L
        private set

    private var startMs: Long? = null
    private var lastMovingMs: Long? = null
    private var lastFixMs: Long? = null
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var hasPosition = false

    /** Speed of the latest fix, in metres per second; null before the first fix. */
    var speedMps: Float? = null
        private set

    val hasTrip: Boolean get() = startMs != null

    /** From the first to the last moving fix, so a final stop does not stretch it. */
    val durationMs: Long
        get() {
            val start = startMs ?: return 0L
            return (lastMovingMs ?: start) - start
        }

    /** Average while moving, in km/h; null until there is a minute of driving to average. */
    val averageKmh: Double?
        get() = if (movingMs < MIN_AVERAGE_MS) null else distanceM / movingMs * MS_TO_KMH

    /** True when the latest fix was moving and recent. */
    fun isMoving(nowMs: Long): Boolean {
        val last = lastFixMs ?: return false
        return nowMs - last <= STALE_FIX_MS && (speedMps ?: 0f) >= MOVING_MPS
    }

    /**
     * Adds a fix. [speedMps] is the receiver's own speed when it reports one; otherwise it is
     * derived from the distance to the previous fix. Fixes worse than [MAX_ACCURACY_M] update
     * the speed but not the distance.
     */
    fun add(lat: Double, lon: Double, timeMs: Long, speedMps: Float?, accuracyM: Float?) {
        val previousFix = lastFixMs
        if (previousFix != null && timeMs <= previousFix) return

        val accurate = accuracyM == null || accuracyM <= MAX_ACCURACY_M
        val stepM = if (hasPosition && accurate) distanceM(lastLat, lastLon, lat, lon) else 0.0
        val dtMs = if (previousFix != null) timeMs - previousFix else 0L
        val impliedMps = if (dtMs > 0) (stepM / (dtMs / 1000.0)).toFloat() else 0f
        val speed = speedMps ?: impliedMps
        val moving = speed >= MOVING_MPS

        // The fix that starts a trip only marks the start: whatever happened before it
        // (a long stop, the drive that ended the previous trip) is not part of this one.
        var startsTrip = false
        if (moving) {
            val lastMoving = lastMovingMs
            if (lastMoving != null && timeMs - lastMoving > resetAfterMs) reset()
            if (startMs == null) {
                startMs = timeMs
                startsTrip = true
            }
        }

        // A jump faster than any car is a bad fix: keep the position, drop the distance.
        val plausible = impliedMps <= MAX_PLAUSIBLE_MPS
        if (!startsTrip && startMs != null && moving && plausible) {
            distanceM += stepM
            movingMs += dtMs.coerceAtMost(MAX_STEP_MS)
        }
        if (moving) lastMovingMs = timeMs

        if (accurate) {
            lastLat = lat
            lastLon = lon
            hasPosition = true
        }
        lastFixMs = timeMs
        this.speedMps = speed
    }

    fun reset() {
        distanceM = 0.0
        movingMs = 0L
        startMs = null
        lastMovingMs = null
    }

    companion object {
        const val DEFAULT_RESET_MS = 20 * 60_000L
        const val MOVING_MPS = 1.4f
        const val MAX_ACCURACY_M = 50f
        const val MAX_PLAUSIBLE_MPS = 70f
        const val MAX_STEP_MS = 10_000L
        const val STALE_FIX_MS = 10_000L
        const val MIN_AVERAGE_MS = 60_000L
        const val MS_TO_KMH = 3_600.0
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Great-circle distance in metres (haversine). */
        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}
