package io.github.giannoccarol.corollalauncher.assistant.tools

import io.github.giannoccarol.corollalauncher.assistant.AssistantAction
import io.github.giannoccarol.corollalauncher.assistant.CarContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {

    private val car = CarContext(
        localTime = "Tuesday 10 November 2026, 15:47", speedKmh = 50, tripKm = 3.2, nowPlaying = null,
        homeSaved = true, workSaved = false, darkTheme = true,
        appNames = listOf("Spotify", "Google Maps", "Radio FM"), position = null,
    )

    @Test
    fun weatherSummaryIsReadable() {
        val forecast = JSONObject(
            """{"current":{"temperature_2m":14.2,"apparent_temperature":12.9,"weather_code":61,
                "wind_speed_10m":18.0,"precipitation":0.4},
               "daily":{"time":["2026-11-10","2026-11-11"],"weather_code":[61,2],
                "temperature_2m_max":[16.1,18.0],"temperature_2m_min":[9.0,10.2],
                "precipitation_probability_max":[80,null]}}""",
        )
        val summary = Weather.summarize(forecast, "Lecce, Puglia, Italia")
        assertEquals("Lecce, Puglia, Italia", summary.getString("place"))
        assertEquals("light rain", summary.getJSONObject("now").getString("conditions"))
        val days = summary.getJSONArray("days")
        assertEquals(2, days.length())
        assertEquals("partly cloudy", days.getJSONObject(1).getString("conditions"))
        assertEquals(80, days.getJSONObject(0).getInt("rain_chance_percent"))
        assertTrue(!days.getJSONObject(1).has("rain_chance_percent"))
    }

    @Test
    fun weatherCodes() {
        assertEquals("clear sky", Weather.describe(0))
        assertEquals("thunderstorm with hail", Weather.describe(99))
        assertEquals("unknown", Weather.describe(-1))
    }

    @Test
    fun overpassResultsAreSortedByDistanceWithAddresses() {
        val root = JSONObject(
            """{"elements":[
               {"type":"node","lat":40.3600,"lon":18.1800,"tags":{"amenity":"fuel","brand":"Q8"}},
               {"type":"way","center":{"lat":40.3535,"lon":18.1705},
                "tags":{"amenity":"fuel","name":"Eni","addr:street":"Via Roma","addr:housenumber":"3","addr:city":"Lecce","opening_hours":"24/7"}},
               {"type":"node","tags":{"name":"no position"}}]}""",
        )
        val places = Places.parseOverpass(root, 40.3530, 18.1700, fallbackName = "fuel")
        assertEquals(listOf("Eni", "Q8"), places.map { it.name })
        assertEquals("Via Roma 3, Lecce", places[0].address)
        assertEquals("24/7", places[0].openingHours)
        assertTrue(places[0].distanceKm!! < places[1].distanceKm!!)
        assertEquals("40.353500,18.170500", places[0].toJson().getString("coordinates"))
    }

    @Test
    fun nominatimResultsWithoutPositionKeepTheirOrder() {
        val results = JSONArray(
            """[{"lat":"45.46","lon":"9.19","name":"Esselunga","display_name":"Esselunga, Viale Piave, Milano"},
               {"lat":"bad","lon":"9.1"},
               {"lat":"45.47","lon":"9.20","name":"","display_name":"Esselunga Corso Buenos Aires, Milano"}]""",
        )
        val places = Places.parseNominatim(results, null, null)
        assertEquals(listOf("Esselunga", "Esselunga Corso Buenos Aires"), places.map { it.name })
        assertNull(places[0].distanceKm)
    }

    @Test
    fun actionToolsAreCheckedAndQueued() {
        val tools = CarTools(car, "it")
        assertTrue(tools.run("navigate", JSONObject().put("destination", "Lecce")).has("status"))
        assertTrue(tools.run("navigate_saved", JSONObject().put("place", "work")).has("error"))
        assertTrue(tools.run("open_app", JSONObject().put("app", "Netflix")).has("error"))
        assertTrue(tools.run("play_music", JSONObject().put("query", "Vasco").put("app", "spotify")).has("status"))
        assertTrue(tools.run("set_volume", JSONObject()).has("error"))
        assertTrue(tools.run("unknown", JSONObject()).has("error"))
        assertEquals(
            listOf(AssistantAction.Navigate("Lecce"), AssistantAction.PlayMusic("Vasco", "spotify")),
            tools.actions,
        )
    }

    @Test
    fun lookupsWithoutPositionExplainWhy() {
        val tools = CarTools(car, "it")
        assertTrue(tools.run("get_location", JSONObject()).getString("error").contains("position"))
        assertTrue(tools.run("get_weather", JSONObject()).getString("error").contains("position"))
        assertTrue(tools.run("find_places", JSONObject().put("category", "fuel")).getString("error").contains("position"))
    }

    @Test
    fun everyToolHasAnObjectSchema() {
        CarTools(car, "it").specs.forEach {
            assertEquals("object", it.inputSchema.getString("type"))
            assertTrue(it.description.isNotBlank())
        }
    }
}
