package app.genrix.android.tools

import app.genrix.android.local.LocalActions
import app.genrix.android.local.LocalCommand
import org.json.JSONObject

/**
 * The phone's own controls for Gemini: the same [LocalActions] that local commands use, so
 * "torch off" or "volume 30" works the same whether the phone or Gemini heard it.
 */
object DeviceTools {
    fun all(): List<PhoneTool> = listOf(DeviceControl)

    private val ACTIONS = listOf(
        "volume_set", "volume_up", "volume_down", "mute", "unmute", "torch_on", "torch_off", "battery",
    )

    private object DeviceControl : PhoneTool {
        override val name = "device_control"

        override fun declaration() = Schema.function(
            name,
            "Phone controls: media volume (set a percent, up, down), mute or unmute (calls vibrate while muted), " +
                "flashlight on or off, and battery level and charging.",
            Schema.string("action", "What to do.", required = true, choices = ACTIONS),
            Schema.integer("percent", "For volume_set: 0 to 100."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val command = command(args.text("action"), args.optInt("percent", -1))
                ?: return Results.failed("bad_action", "Ask what to change: volume, mute, flashlight or battery.")
            val result = LocalActions(host.context).run(command)
            return Results.done(result.reply.ifEmpty { "Done." })
        }
    }

    /** The tool's action → the shared command; null when it can't be done as asked. */
    fun command(action: String, percent: Int): LocalCommand? = when (action) {
        "volume_set" -> if (percent in 0..100) LocalCommand.SetVolume(percent) else null
        "volume_up" -> LocalCommand.StepVolume(true)
        "volume_down" -> LocalCommand.StepVolume(false)
        "mute" -> LocalCommand.Mute(true)
        "unmute" -> LocalCommand.Mute(false)
        "torch_on" -> LocalCommand.Torch(true)
        "torch_off" -> LocalCommand.Torch(false)
        "battery" -> LocalCommand.Battery
        else -> null
    }
}
