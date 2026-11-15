package io.github.giannoccarol.corollalauncher.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelListTest {

    @Test
    fun readsAnthropicStyle() {
        val models = ModelList.parse(
            """{"data":[{"type":"model","id":"claude-sonnet-5-5","display_name":"Claude Sonnet 5.5"},
               {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5"}],"has_more":false}""",
        )!!
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5-5"), models.map { it.id })
        assertEquals("Claude Opus 5.5", models[0].label)
    }

    @Test
    fun readsOpenAiStyleAndDropsNonChatModels() {
        val models = ModelList.parse(
            """{"object":"list","data":[{"id":"gpt-5-mini"},{"id":"text-embedding-3-large"},
               {"id":"whisper-1"},{"id":"gpt-image-1"},{"id":"gpt-5"},{"id":"gpt-5"}]}""",
        )!!
        assertEquals(listOf("gpt-5", "gpt-5-mini"), models.map { it.id })
        assertEquals("gpt-5", models[0].label)
    }

    @Test
    fun readsGeminiNames() {
        val models = ModelList.parse(
            """{"models":[{"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash"}]}""",
        )!!
        assertEquals(ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash"), models.single())
    }

    @Test
    fun readsABareArray() {
        assertEquals(listOf("a-model"), ModelList.parse("""[{"id":"a-model"}]""")!!.map { it.id })
    }

    @Test
    fun refusesWhatIsNotAList() {
        assertNull(ModelList.parse("<html>Not found</html>"))
        assertNull(ModelList.parse("""{"error":{"message":"bad key"}}"""))
    }

    @Test
    fun searchMatchesEveryWordInAnyOrder() {
        val models = listOf(
            ModelInfo("claude-opus-5-5", "Claude Opus 5.5"),
            ModelInfo("claude-haiku-5-5", "Claude Haiku 5.5"),
            ModelInfo("openai/gpt-5"),
        )
        assertEquals(listOf("claude-opus-5-5"), ModelList.search(models, "opus claude").map { it.id })
        assertEquals(listOf("openai/gpt-5"), ModelList.search(models, "GPT").map { it.id })
        assertEquals(models, ModelList.search(models, "  "))
    }
}
