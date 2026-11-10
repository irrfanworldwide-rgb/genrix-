package app.genrix.android.tools

import android.content.Context
import android.content.Intent
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Something the model can call. Same names as desktop where the job is the same. */
interface PhoneTool {
    val name: String

    /** Gemini function declaration: name, description, parameters (JSON schema). */
    fun declaration(): JSONObject

    suspend fun run(args: JSONObject, host: ToolHost): JSONObject
}

/** What tools may do on the phone. Implemented by the voice session. */
interface ToolHost {
    val context: Context
    val http: OkHttpClient

    /** Ask the user for a runtime permission on screen. Returns at once. */
    fun requestPermission(permission: String)

    fun hasPermission(permission: String): Boolean

    /**
     * Opens another app once the spoken reply has finished, one per turn, and returns the
     * tool result: [done] when it will open, or why the user must act first. [then] runs
     * after the app opens; a non-null answer is passed to the model as a note.
     */
    fun openApp(intent: Intent, label: String, done: String, then: (suspend () -> String?)? = null): JSONObject

    /**
     * Ends the conversation once the current reply has been spoken, e.g. after music starts:
     * listening over music doesn't work and would stream the song to Gemini.
     */
    fun endSessionSoon()

    /** Starts something that shows no screen (Clock app) right away; returns the tool result. */
    fun startNow(intent: Intent, label: String, done: String): JSONObject
}

/** Tool results the model reads. `status` is always one of these words. */
object Results {
    fun done(message: String) = JSONObject().put("status", "done").put("message", message)

    /** The user must choose; the model asks them, it never picks for them. */
    fun ask(message: String, choices: List<String>) = JSONObject()
        .put("status", "needs_clarification")
        .put("message", message)
        .put("choices", JSONArray(choices))

    fun failed(code: String, message: String) =
        JSONObject().put("status", "failed").put("code", code).put("message", message)

    /** Android blocked opening an app from the background; a notification waits for a tap. */
    fun needsTap(label: String) = JSONObject()
        .put("status", "needs_user_tap")
        .put("message", "Android needs a tap: tell the user to tap the \"Open $label\" notification.")

    fun needsPermission(what: String) = JSONObject()
        .put("status", "needs_permission")
        .put("message", "Ask the user to tap Allow for $what on screen, then say the request again.")
}

/** Small builders for function declarations. */
object Schema {
    fun function(name: String, description: String, vararg params: Param): JSONObject {
        val properties = JSONObject()
        params.forEach { properties.put(it.name, it.schema) }
        val parameters = JSONObject().put("type", "object").put("properties", properties)
        val required = params.filter { it.required }.map { it.name }
        if (required.isNotEmpty()) parameters.put("required", JSONArray(required))
        return JSONObject().put("name", name).put("description", description).put("parameters", parameters)
    }

    class Param(val name: String, val schema: JSONObject, val required: Boolean)

    fun string(name: String, description: String, required: Boolean = false, choices: List<String>? = null) =
        Param(
            name,
            JSONObject().put("type", "string").put("description", description)
                .apply { if (choices != null) put("enum", JSONArray(choices)) },
            required,
        )

    fun integer(name: String, description: String, required: Boolean = false) =
        Param(name, JSONObject().put("type", "integer").put("description", description), required)

    fun strings(name: String, description: String) = Param(
        name,
        JSONObject().put("type", "array").put("description", description)
            .put("items", JSONObject().put("type", "string")),
        false,
    )
}

fun JSONObject.text(key: String): String = optString(key).trim().takeUnless { it == "null" }.orEmpty()
