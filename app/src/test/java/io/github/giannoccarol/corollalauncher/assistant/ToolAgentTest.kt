package io.github.giannoccarol.corollalauncher.assistant

import io.github.giannoccarol.corollalauncher.assistant.ai.AiConfig
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProviders
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmException
import io.github.giannoccarol.corollalauncher.assistant.tools.ToolBox
import io.github.giannoccarol.corollalauncher.assistant.tools.ToolSpec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAgentTest {

    /** Replays canned replies and records every request body. */
    private class FakeTransport(vararg replies: String) : Transport {
        private val queue = ArrayDeque(replies.map { JSONObject(it) })
        val requests = mutableListOf<JSONObject>()
        override fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
            // Copy: the agent keeps mutating the transcript it sends.
            requests += JSONObject(body.toString())
            return queue.removeFirst()
        }
    }

    private class FakeTools : ToolBox {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        override val specs = listOf(ToolSpec("get_weather", "Weather", JSONObject().put("type", "object")))
        override fun run(name: String, input: JSONObject): JSONObject {
            calls += name to input
            return if (name == "get_weather") JSONObject().put("temperature_c", 14) else JSONObject().put("error", "nope")
        }
    }

    private val anthropic = AiConfig(AiProviders.byId("anthropic"), "key", "claude-opus-5-5", "session")
    private val openAi = AiConfig(AiProviders.byId("openai"), "key", "gpt-5-mini", "session")

    @Test
    fun anthropicRunsToolsThenAnswers() {
        val transport = FakeTransport(
            """{"stop_reason":"tool_use","content":[{"type":"thinking","thinking":"","signature":"sig"},
               {"type":"tool_use","id":"t1","name":"get_weather","input":{"place":"Lecce"}}]}""",
            """{"stop_reason":"end_turn","content":[{"type":"text","text":"A Lecce ci sono 14 gradi."}]}""",
        )
        val tools = FakeTools()
        val transcript = JSONArray()
        val answer = ToolAgent(transport, tools).anthropic(
            anthropic, emptyMap(), "system", transcript, "Che tempo fa a Lecce?", webSearch = true, countryCode = "IT",
        )

        assertEquals("A Lecce ci sono 14 gradi.", answer)
        assertEquals(listOf("get_weather"), tools.calls.map { it.first })
        assertEquals("Lecce", tools.calls[0].second.getString("place"))

        // Second request: user, assistant (thinking kept unchanged), user with the tool result.
        val messages = transport.requests[1].getJSONArray("messages")
        assertEquals(3, messages.length())
        val assistant = messages.getJSONObject(1).getJSONArray("content")
        assertEquals("sig", assistant.getJSONObject(0).getString("signature"))
        val result = messages.getJSONObject(2).getJSONArray("content").getJSONObject(0)
        assertEquals("tool_result", result.getString("type"))
        assertEquals("t1", result.getString("tool_use_id"))
        assertFalse(result.has("is_error"))

        // The final answer is in the transcript for the next question.
        assertEquals(4, transcript.length())
    }

    @Test
    fun anthropicRequestUsesLowEffortFallbacksCacheAndWebSearch() {
        val transport = FakeTransport("""{"stop_reason":"end_turn","content":[{"type":"text","text":"Ciao"}]}""")
        ToolAgent(transport, FakeTools()).anthropic(
            anthropic, emptyMap(), "system", JSONArray(), "Ciao", webSearch = true, countryCode = "IT",
        )
        val body = transport.requests[0]
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        assertEquals("default", body.getString("fallbacks"))
        assertEquals("auto", body.getJSONObject("tool_choice").getString("type"))
        assertEquals("ephemeral", body.getJSONArray("system").getJSONObject(0).getJSONObject("cache_control").getString("type"))
        val tools = body.getJSONArray("tools")
        val search = (0 until tools.length()).map { tools.getJSONObject(it) }.first { it.optString("name") == "web_search" }
        assertEquals("web_search_20260209", search.getString("type"))
        assertEquals("IT", search.getJSONObject("user_location").getString("country"))
    }

    @Test
    fun failedToolsAreMarkedAsErrors() {
        val transport = FakeTransport(
            """{"stop_reason":"tool_use","content":[{"type":"tool_use","id":"t1","name":"open_app","input":{}}]}""",
            """{"stop_reason":"end_turn","content":[{"type":"text","text":"Non trovo l'app."}]}""",
        )
        ToolAgent(transport, FakeTools()).anthropic(anthropic, emptyMap(), "s", JSONArray(), "x", false, null)
        val result = transport.requests[1].getJSONArray("messages").getJSONObject(2)
            .getJSONArray("content").getJSONObject(0)
        assertTrue(result.getBoolean("is_error"))
    }

    @Test
    fun pauseTurnResendsTheConversation() {
        val transport = FakeTransport(
            """{"stop_reason":"pause_turn","content":[{"type":"server_tool_use","id":"s1","name":"web_search","input":{"query":"x"}}]}""",
            """{"stop_reason":"end_turn","content":[{"type":"text","text":"Fatto."}]}""",
        )
        val steps = mutableListOf<String>()
        val agent = ToolAgent(transport, FakeTools()).apply { onStep = { steps += it } }
        assertEquals("Fatto.", agent.anthropic(anthropic, emptyMap(), "s", JSONArray(), "x", true, null))
        // The paused assistant turn is the last message of the second request.
        val messages = transport.requests[1].getJSONArray("messages")
        assertEquals("assistant", messages.getJSONObject(messages.length() - 1).getString("role"))
        assertEquals(listOf("web"), steps)
    }

    @Test(expected = LlmException::class)
    fun refusalBecomesAnError() {
        val transport = FakeTransport("""{"stop_reason":"refusal","content":[]}""")
        ToolAgent(transport, FakeTools()).anthropic(anthropic, emptyMap(), "s", JSONArray(), "x", false, null)
    }

    @Test(expected = LlmException::class)
    fun endlessToolCallsStop() {
        val loop = """{"stop_reason":"tool_use","content":[{"type":"tool_use","id":"t","name":"get_weather","input":{}}]}"""
        val transport = FakeTransport(loop, loop, loop)
        ToolAgent(transport, FakeTools(), maxSteps = 3).anthropic(anthropic, emptyMap(), "s", JSONArray(), "x", false, null)
    }

    @Test
    fun openAiRunsToolCallsThenAnswers() {
        val transport = FakeTransport(
            """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
               {"id":"c1","type":"function","function":{"name":"get_weather","arguments":"{\"place\":\"Bari\"}"}}]}}]}""",
            """{"choices":[{"message":{"role":"assistant","content":"A Bari 14 gradi."}}]}""",
        )
        val tools = FakeTools()
        val answer = ToolAgent(transport, tools).openAi(openAi, emptyMap(), "system", JSONArray(), "Meteo Bari?")
        assertEquals("A Bari 14 gradi.", answer)
        assertEquals("Bari", tools.calls[0].second.getString("place"))
        val messages = transport.requests[1].getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val toolMessage = messages.getJSONObject(3)
        assertEquals("tool", toolMessage.getString("role"))
        assertEquals("c1", toolMessage.getString("tool_call_id"))
        assertEquals("function", transport.requests[0].getJSONArray("tools").getJSONObject(0).getString("type"))
    }
}
