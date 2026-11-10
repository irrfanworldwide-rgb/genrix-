package app.genrix.android.live

import org.json.JSONObject

/** What the Gemini Live server tells us, already decoded from JSON. */
sealed interface LiveEvent {
    data object SetupComplete : LiveEvent
    class Audio(val pcm: ByteArray, val sampleRate: Int) : LiveEvent
    data class UserText(val text: String) : LiveEvent
    data class AssistantText(val text: String) : LiveEvent
    /** The user started speaking over the reply; drop what is still queued. */
    data object Interrupted : LiveEvent
    data object TurnComplete : LiveEvent
    data class ResumeHandle(val handle: String) : LiveEvent
    /** The server will close soon; reconnect with the last resume handle. */
    data object GoAway : LiveEvent
    /** The model wants tools run; answer every id with LiveProtocol.toolResponseMessage. */
    data class ToolCall(val calls: List<FunctionCall>) : LiveEvent
    data class ToolCancelled(val ids: List<String>) : LiveEvent
}

class FunctionCall(val id: String, val name: String, val args: JSONObject)

class FunctionResult(val id: String, val name: String, val response: JSONObject)
