package app.genrix.android.live

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProtocolTest {
    private fun setup(tools: JSONArray) = LiveProtocol.Setup(
        model = "m",
        systemInstruction = "x",
        voice = null,
        languages = emptyList(),
        resumeHandle = null,
        tools = tools,
    )

    @Test
    fun declaresToolsOnlyWhenThereAreSome() {
        val none = JSONObject(LiveProtocol.setupMessage(setup(JSONArray()))).getJSONObject("setup")
        assertFalse(none.has("tools"))

        val declared = JSONArray().put(JSONObject().put("name", "set_alarm"))
        val body = JSONObject(LiveProtocol.setupMessage(setup(declared))).getJSONObject("setup")
        val functions = body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
        assertEquals("set_alarm", functions.getJSONObject(0).getString("name"))
    }

    @Test
    fun parsesSeveralCallsInOrder() {
        val message = """
            {"toolCall":{"functionCalls":[
              {"id":"a","name":"set_alarm","args":{"hour":6,"minute":0}},
              {"id":"b","name":"whatsapp_send","args":{"name":"Rahul","message":"I'm late"}},
              {"id":"c"}
            ]}}
        """
        val event = LiveProtocol.parse(message).single() as LiveEvent.ToolCall
        assertEquals(listOf("set_alarm", "whatsapp_send"), event.calls.map { it.name })
        assertEquals(6, event.calls[0].args.getInt("hour"))
        assertEquals("Rahul", event.calls[1].args.getString("name"))
    }

    @Test
    fun parsesCancellation() {
        val event = LiveProtocol.parse("""{"toolCallCancellation":{"ids":["a","b"]}}""").single()
        assertEquals(listOf("a", "b"), (event as LiveEvent.ToolCancelled).ids)
    }

    @Test
    fun answersEveryCallById() {
        val message = LiveProtocol.toolResponseMessage(
            listOf(FunctionResult("a", "set_alarm", JSONObject().put("status", "done"))),
        )
        val response = JSONObject(message).getJSONObject("toolResponse")
            .getJSONArray("functionResponses").getJSONObject(0)
        assertEquals("a", response.getString("id"))
        assertEquals("set_alarm", response.getString("name"))
        assertTrue(response.getJSONObject("response").has("status"))
    }
}
