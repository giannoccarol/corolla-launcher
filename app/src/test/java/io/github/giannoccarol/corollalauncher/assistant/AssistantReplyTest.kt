package io.github.giannoccarol.corollalauncher.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantReplyTest {

    @Test
    fun readsSayAndActions() {
        val reply = AssistantReply.parse(
            """{"say": "Ok, si parte.", "actions": [{"type": "navigate", "destination": "Lecce"}, {"type": "media", "command": "pause"}]}""",
        )
        assertEquals("Ok, si parte.", reply.say)
        assertEquals(
            listOf(AssistantAction.Navigate("Lecce"), AssistantAction.Media(MediaCommand.PAUSE)),
            reply.actions,
        )
    }

    @Test
    fun toleratesCodeFencesAndSurroundingText() {
        val reply = AssistantReply.parse(
            "Here you go:\n```json\n{\"say\": \"Done\", \"actions\": [{\"type\": \"navigate_home\"}]}\n```",
        )
        assertEquals("Done", reply.say)
        assertEquals(listOf(AssistantAction.NavigateHome), reply.actions)
    }

    @Test
    fun plainTextIsSpokenAsIs() {
        val reply = AssistantReply.parse("Sono le tre e mezza.")
        assertEquals("Sono le tre e mezza.", reply.say)
        assertTrue(reply.actions.isEmpty())
    }

    @Test
    fun brokenJsonFallsBackToText() {
        val reply = AssistantReply.parse("{\"say\": \"oops\"")
        assertEquals("{\"say\": \"oops\"", reply.say)
        assertTrue(reply.actions.isEmpty())
    }

    @Test
    fun unknownOrIncompleteActionsAreDropped() {
        val reply = AssistantReply.parse(
            """{"say": "x", "actions": [{"type": "call", "who": "Mum"}, {"type": "navigate"}, {"type": "media", "command": "rewind"}, {"type": "open_app", "app": "Spotify"}]}""",
        )
        assertEquals(listOf(AssistantAction.OpenApp("Spotify")), reply.actions)
    }

    @Test
    fun volumeIsClampedAndRelativeChangesWork() {
        val reply = AssistantReply.parse(
            """{"say": "", "actions": [{"type": "volume", "percent": 140}, {"type": "volume", "change": "down"}]}""",
        )
        assertEquals(
            listOf(AssistantAction.Volume(100, 0), AssistantAction.Volume(null, -1)),
            reply.actions,
        )
    }

    @Test
    fun themeModes() {
        val reply = AssistantReply.parse(
            """{"say": "", "actions": [{"type": "theme", "mode": "LIGHT"}, {"type": "theme", "mode": "sepia"}]}""",
        )
        assertEquals(listOf(AssistantAction.Theme(dark = false)), reply.actions)
    }
}
