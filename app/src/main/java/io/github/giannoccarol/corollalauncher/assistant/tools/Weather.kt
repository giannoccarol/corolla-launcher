package io.github.giannoccarol.corollalauncher.assistant.tools

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Weather from Open-Meteo (https://open-meteo.com): free, no API key, data under CC BY 4.0.
 * The network calls are thin; [summarize] turns the forecast into the compact JSON the model
 * reads, and is unit tested.
 */
object Weather {

    data class Place(val name: String, val latitude: Double, val longitude: Double)

    /** First match for a place name, in the driver's language; null when nothing matches. */
    fun geocode(name: String, language: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json" +
            "&language=${Http.encode(language)}&name=${Http.encode(name)}"
        val first = JSONObject(Http.get(url)).optJSONArray("results")?.optJSONObject(0) ?: return null
        val label = listOf(first.optString("name"), first.optString("admin1"), first.optString("country"))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        return Place(label, first.getDouble("latitude"), first.getDouble("longitude"))
    }

    fun forecast(latitude: Double, longitude: Double, days: Int): JSONObject {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&timezone=auto" +
                "&forecast_days=%d&current=temperature_2m,apparent_temperature,weather_code," +
                "wind_speed_10m,precipitation&daily=weather_code,temperature_2m_max," +
                "temperature_2m_min,precipitation_probability_max",
            latitude, longitude, days.coerceIn(1, MAX_DAYS),
        )
        return JSONObject(Http.get(url))
    }

    /** The parts of an Open-Meteo forecast worth saying in a car, with readable conditions. */
    fun summarize(forecast: JSONObject, placeName: String?): JSONObject {
        val result = JSONObject()
        placeName?.let { result.put("place", it) }
        forecast.optJSONObject("current")?.let { current ->
            result.put("now", JSONObject().apply {
                put("temperature_c", current.optDouble("temperature_2m"))
                put("feels_like_c", current.optDouble("apparent_temperature"))
                put("conditions", describe(current.optInt("weather_code", -1)))
                put("wind_kmh", current.optDouble("wind_speed_10m"))
                put("precipitation_mm", current.optDouble("precipitation"))
            })
        }
        forecast.optJSONObject("daily")?.let { daily ->
            val dates = daily.optJSONArray("time") ?: JSONArray()
            val days = JSONArray()
            for (i in 0 until dates.length()) {
                days.put(JSONObject().apply {
                    put("date", dates.optString(i))
                    put("conditions", describe(daily.optJSONArray("weather_code")?.optInt(i, -1) ?: -1))
                    put("min_c", daily.optJSONArray("temperature_2m_min")?.optDouble(i))
                    put("max_c", daily.optJSONArray("temperature_2m_max")?.optDouble(i))
                    daily.optJSONArray("precipitation_probability_max")?.let {
                        if (!it.isNull(i)) put("rain_chance_percent", it.optInt(i))
                    }
                })
            }
            result.put("days", days)
        }
        return result
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them. */
    fun describe(code: Int): String = when (code) {
        0 -> "clear sky"
        1 -> "mainly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"
        80, 81 -> "rain showers"
        82 -> "violent rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown"
    }

    private const val MAX_DAYS = 3
}
