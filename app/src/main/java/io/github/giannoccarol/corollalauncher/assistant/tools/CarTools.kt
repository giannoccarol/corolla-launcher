package io.github.giannoccarol.corollalauncher.assistant.tools

import io.github.giannoccarol.corollalauncher.assistant.AppMatcher
import io.github.giannoccarol.corollalauncher.assistant.AssistantAction
import io.github.giannoccarol.corollalauncher.assistant.CarContext
import io.github.giannoccarol.corollalauncher.assistant.MediaCommand
import java.io.IOException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One tool the model may call: its name, what it is for, and its JSON Schema input. */
data class ToolSpec(val name: String, val description: String, val inputSchema: JSONObject)

/** What the agent loop needs from a tool set. */
interface ToolBox {
    val specs: List<ToolSpec>

    /** Runs a tool and returns its result; `error` in the result tells the model it failed. */
    fun run(name: String, input: JSONObject): JSONObject
}

/**
 * The assistant's tools in the car. Information tools (weather, places, location) run here on
 * the worker thread. Action tools (navigate, music, apps, volume, theme) are checked and queued
 * in [actions]; the panel carries them out on the main thread once the model has answered, so a
 * reply never fires half its actions or recreates the screen mid-conversation.
 */
class CarTools(private val car: CarContext, private val language: String) : ToolBox {

    val actions = mutableListOf<AssistantAction>()

    /** Short labels of the information tools used this turn, for the panel's status line. */
    var onStep: (String) -> Unit = {}

    override val specs: List<ToolSpec> = listOf(
        spec(
            "navigate", "Start turn-by-turn navigation in the car's maps app. Use the coordinates of a place " +
                "found with find_places when there are some.",
            props("destination" to string("Address, place name, or \"lat,lon\" coordinates")),
            required = listOf("destination"),
        ),
        spec(
            "navigate_saved", "Navigate to the driver's saved home or work address.",
            props("place" to enum("home", "work")), required = listOf("place"),
        ),
        spec(
            "media_control", "Control what is playing now.",
            props("command" to enum("play", "pause", "next", "previous")), required = listOf("command"),
        ),
        spec(
            "play_music", "Search for and play music: an artist, song, album, playlist, genre or radio " +
                "station. Optionally in a specific installed app.",
            props(
                "query" to string("What to play"),
                "app" to string("Optional app name from the installed apps, e.g. Spotify"),
            ),
            required = listOf("query"),
        ),
        spec(
            "open_app", "Open an installed app by name.",
            props("app" to string("App name from the installed apps")), required = listOf("app"),
        ),
        spec(
            "set_volume", "Change the media volume, either to a percentage or one step up or down.",
            props(
                "percent" to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100),
                "change" to enum("up", "down"),
            ),
        ),
        spec(
            "set_theme", "Switch the screen between the dark and the light theme.",
            props("mode" to enum("dark", "light")), required = listOf("mode"),
        ),
        spec(
            "get_location", "Where the car is now: street address and coordinates.",
            props(),
        ),
        spec(
            "get_weather", "Current weather and forecast. Without a place, for where the car is.",
            props(
                "place" to string("Optional town or city"),
                "days" to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 3),
            ),
        ),
        spec(
            "find_places", "Find places near the car, nearest first: by category, or by name or address " +
                "with query. Returns names, distances, addresses and coordinates.",
            props(
                "category" to enum(*Places.CATEGORIES.keys.toTypedArray()),
                "query" to string("Name, brand or address to look for, e.g. \"Esselunga\""),
                "radius_km" to JSONObject().put("type", "number").put("minimum", 0.5).put("maximum", 25),
            ),
        ),
    )

    override fun run(name: String, input: JSONObject): JSONObject = try {
        when (name) {
            "navigate" -> queue(input.optString("destination").trim().takeIf { it.isNotEmpty() }
                ?.let { AssistantAction.Navigate(it) })
            "navigate_saved" -> when (input.optString("place")) {
                "home" -> if (car.homeSaved) queue(AssistantAction.NavigateHome) else error("home address not saved")
                "work" -> if (car.workSaved) queue(AssistantAction.NavigateWork) else error("work address not saved")
                else -> error("unknown place")
            }
            "media_control" -> queue(
                when (input.optString("command")) {
                    "play" -> AssistantAction.Media(MediaCommand.PLAY)
                    "pause" -> AssistantAction.Media(MediaCommand.PAUSE)
                    "next" -> AssistantAction.Media(MediaCommand.NEXT)
                    "previous" -> AssistantAction.Media(MediaCommand.PREVIOUS)
                    else -> null
                },
            )
            "play_music" -> playMusic(input)
            "open_app" -> openApp(input.optString("app"))
            "set_volume" -> setVolume(input)
            "set_theme" -> when (input.optString("mode")) {
                "dark" -> queue(AssistantAction.Theme(dark = true))
                "light" -> queue(AssistantAction.Theme(dark = false))
                else -> error("unknown mode")
            }
            "get_location" -> location()
            "get_weather" -> weather(input)
            "find_places" -> places(input)
            else -> error("unknown tool $name")
        }
    } catch (e: IOException) {
        error("network error: ${e.message}")
    } catch (e: JSONException) {
        error("unexpected reply from the data service")
    }

    private fun playMusic(input: JSONObject): JSONObject {
        val query = input.optString("query").trim()
        if (query.isEmpty()) return error("empty query")
        val app = input.optString("app").trim().takeIf { it.isNotEmpty() }
        if (app != null && AppMatcher.find(car.appNames, app) == null) return error("no installed app called $app")
        return queue(AssistantAction.PlayMusic(query, app))
    }

    private fun openApp(name: String): JSONObject =
        if (AppMatcher.find(car.appNames, name) == null) error("no installed app called $name")
        else queue(AssistantAction.OpenApp(name))

    private fun setVolume(input: JSONObject): JSONObject = when {
        input.has("percent") -> queue(AssistantAction.Volume(input.optInt("percent").coerceIn(0, 100), 0))
        input.optString("change") == "up" -> queue(AssistantAction.Volume(null, 1))
        input.optString("change") == "down" -> queue(AssistantAction.Volume(null, -1))
        else -> error("give percent or change")
    }

    private fun location(): JSONObject {
        val (lat, lon) = car.position ?: return noPosition()
        onStep(STEP_LOCATION)
        return JSONObject().apply {
            put("coordinates", String.format(Locale.ROOT, "%.6f,%.6f", lat, lon))
            Places.reverse(lat, lon, language)?.let { put("address", it) }
            car.speedKmh?.let { put("speed_kmh", it) }
        }
    }

    private fun weather(input: JSONObject): JSONObject {
        onStep(STEP_WEATHER)
        val days = input.optInt("days", 1).coerceIn(1, 3)
        val placeName = input.optString("place").trim()
        return if (placeName.isNotEmpty()) {
            val place = Weather.geocode(placeName, language) ?: return error("place not found: $placeName")
            Weather.summarize(Weather.forecast(place.latitude, place.longitude, days), place.name)
        } else {
            val (lat, lon) = car.position ?: return noPosition()
            Weather.summarize(Weather.forecast(lat, lon, days), null)
        }
    }

    private fun places(input: JSONObject): JSONObject {
        val category = input.optString("category").trim()
        val query = input.optString("query").trim()
        val radius = input.optDouble("radius_km", DEFAULT_RADIUS_KM).takeUnless { it.isNaN() } ?: DEFAULT_RADIUS_KM
        val position = car.position
        onStep(STEP_PLACES)
        val found = when {
            category.isNotEmpty() && query.isEmpty() -> {
                val (lat, lon) = position ?: return noPosition()
                Places.nearbyCategory(category, lat, lon, radius)
            }
            query.isNotEmpty() -> Places.search(query, position?.first, position?.second, language)
            else -> return error("give category or query")
        }
        return JSONObject().put("places", JSONArray().apply { found.forEach { put(it.toJson()) } })
            .apply { if (found.isEmpty()) put("note", "nothing found; try a larger radius_km") }
    }

    private fun queue(action: AssistantAction?): JSONObject {
        if (action == null) return error("missing or invalid input")
        actions += action
        return JSONObject().put("status", "will be done when you reply")
    }

    private fun noPosition() = error("the car's position is unknown: location is off or there is no GPS fix")

    private fun error(message: String) = JSONObject().put("error", message)

    companion object {
        const val STEP_LOCATION = "location"
        const val STEP_WEATHER = "weather"
        const val STEP_PLACES = "places"
        const val STEP_WEB = "web"
        private const val DEFAULT_RADIUS_KM = 5.0

        private fun spec(name: String, description: String, properties: JSONObject, required: List<String> = emptyList()) =
            ToolSpec(
                name, description,
                JSONObject().apply {
                    put("type", "object")
                    put("properties", properties)
                    if (required.isNotEmpty()) put("required", JSONArray(required))
                },
            )

        private fun props(vararg entries: Pair<String, JSONObject>) =
            JSONObject().apply { entries.forEach { (key, value) -> put(key, value) } }

        private fun string(description: String) =
            JSONObject().put("type", "string").put("description", description)

        private fun enum(vararg values: String) =
            JSONObject().put("type", "string").put("enum", JSONArray(values.toList()))
    }
}
