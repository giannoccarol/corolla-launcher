package io.github.giannoccarol.corollalauncher.assistant.tools

import io.github.giannoccarol.corollalauncher.trip.TripStats
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Nearby places from OpenStreetMap: categories through the Overpass API, free text and
 * addresses through Nominatim. Both are free and keyless, with fair-use limits a single car
 * never approaches. Data © OpenStreetMap contributors, ODbL.
 *
 * The parsers are pure and unit tested; the fetches are thin.
 */
object Places {

    data class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val distanceKm: Double?,
        val address: String?,
        val openingHours: String?,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("name", name)
            put("coordinates", String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude))
            distanceKm?.let { put("distance_km", Math.round(it * 10) / 10.0) }
            address?.let { put("address", it) }
            openingHours?.let { put("opening_hours", it) }
        }
    }

    /** Spoken categories and the OpenStreetMap tag that holds each. */
    val CATEGORIES: Map<String, Pair<String, String>> = linkedMapOf(
        "fuel" to ("amenity" to "fuel"),
        "ev_charging" to ("amenity" to "charging_station"),
        "parking" to ("amenity" to "parking"),
        "cafe" to ("amenity" to "cafe"),
        "restaurant" to ("amenity" to "restaurant"),
        "fast_food" to ("amenity" to "fast_food"),
        "pharmacy" to ("amenity" to "pharmacy"),
        "hospital" to ("amenity" to "hospital"),
        "supermarket" to ("shop" to "supermarket"),
        "atm" to ("amenity" to "atm"),
        "toilets" to ("amenity" to "toilets"),
        "car_repair" to ("shop" to "car_repair"),
    )

    const val MAX_RESULTS = 6

    fun nearbyCategory(category: String, latitude: Double, longitude: Double, radiusKm: Double): List<Place> {
        val (key, value) = CATEGORIES[category] ?: return emptyList()
        val radius = (radiusKm.coerceIn(0.5, 25.0) * 1000).toInt()
        val query = String.format(
            Locale.ROOT,
            // Overpass returns matches in no particular order: fetch plenty, sort by distance here.
            "[out:json][timeout:10];nwr[\"%s\"=\"%s\"](around:%d,%.6f,%.6f);out center tags 150;",
            key, value, radius, latitude, longitude,
        )
        val body = Http.get("https://overpass-api.de/api/interpreter?data=${Http.encode(query)}")
        return parseOverpass(JSONObject(body), latitude, longitude, fallbackName = category.replace('_', ' '))
    }

    /** Free-text search, biased to the area around the car when its position is known. */
    fun search(text: String, latitude: Double?, longitude: Double?, language: String): List<Place> {
        val area = if (latitude != null && longitude != null) {
            String.format(
                Locale.ROOT, "&viewbox=%.4f,%.4f,%.4f,%.4f",
                longitude - AREA_DEGREES, latitude + AREA_DEGREES, longitude + AREA_DEGREES, latitude - AREA_DEGREES,
            )
        } else {
            ""
        }
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1" +
            "&limit=$MAX_RESULTS&accept-language=${Http.encode(language)}&q=${Http.encode(text)}$area"
        return parseNominatim(JSONArray(Http.get(url)), latitude, longitude)
    }

    /** The street address at a position, for "where am I?". */
    fun reverse(latitude: Double, longitude: Double, language: String): String? {
        val url = String.format(
            Locale.ROOT,
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&lat=%.6f&lon=%.6f&accept-language=%s",
            latitude, longitude, Http.encode(language),
        )
        return JSONObject(Http.get(url)).optString("display_name").takeIf { it.isNotBlank() }
    }

    fun parseOverpass(root: JSONObject, latitude: Double, longitude: Double, fallbackName: String): List<Place> {
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val places = mutableListOf<Place>()
        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            val center = element.optJSONObject("center")
            val lat = if (element.has("lat")) element.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lon = if (element.has("lon")) element.optDouble("lon") else center?.optDouble("lon") ?: continue
            val tags = element.optJSONObject("tags") ?: JSONObject()
            val name = tags.optString("name").ifBlank { tags.optString("brand") }.ifBlank { fallbackName }
            places += Place(
                name, lat, lon,
                TripStats.distanceM(latitude, longitude, lat, lon) / 1000.0,
                address(tags), tags.optString("opening_hours").takeIf { it.isNotBlank() },
            )
        }
        return places.sortedBy { it.distanceKm }.take(MAX_RESULTS)
    }

    fun parseNominatim(results: JSONArray, latitude: Double?, longitude: Double?): List<Place> {
        val places = mutableListOf<Place>()
        for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: continue
            val lat = result.optString("lat").toDoubleOrNull() ?: continue
            val lon = result.optString("lon").toDoubleOrNull() ?: continue
            val name = result.optString("name").ifBlank { result.optString("display_name").substringBefore(',') }
            val distance = if (latitude != null && longitude != null) {
                TripStats.distanceM(latitude, longitude, lat, lon) / 1000.0
            } else {
                null
            }
            places += Place(name, lat, lon, distance, result.optString("display_name").takeIf { it.isNotBlank() }, null)
        }
        return if (latitude != null) places.sortedBy { it.distanceKm } else places
    }

    private fun address(tags: JSONObject): String? {
        val street = listOf(tags.optString("addr:street"), tags.optString("addr:housenumber"))
            .filter { it.isNotBlank() }.joinToString(" ")
        return listOf(street, tags.optString("addr:city")).filter { it.isNotBlank() }
            .joinToString(", ").takeIf { it.isNotBlank() }
    }

    /** About 30 km around the car for free-text searches. */
    private const val AREA_DEGREES = 0.3
}
