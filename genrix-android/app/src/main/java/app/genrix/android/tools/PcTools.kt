package app.genrix.android.tools

import app.genrix.android.control.ControlService
import app.genrix.android.pc.PcBridge
import org.json.JSONObject

/**
 * The user's own computer, through the PC helper on their home network. A fixed set of
 * actions only: nothing here can run an arbitrary command on the PC. Power actions wait for
 * the user's Allow tap on the phone, and the other risky ones follow the same planted-text
 * rule as every other tool (TurnTrust).
 */
object PcTools {
    fun all(): List<PhoneTool> = listOf(PcStatus, PcMedia, PcOpen, PcPower)

    private const val NOT_SET =
        "The PC bridge isn't set up. Tell the user to run the helper on the PC and add its address and token in Settings, PC bridge."

    private fun reply(r: PcBridge.Reply): JSONObject =
        if (r.ok) Results.done(r.message.ifEmpty { "Done." }) else Results.failed("pc_error", r.message.ifEmpty { "The PC didn't do it." })

    private object PcStatus : PhoneTool {
        override val name = "pc_status"

        override fun declaration() = Schema.function(
            name,
            "Checks that the user's PC is on and reachable, and says its name, system and battery if it has one.",
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            if (!PcBridge.configured(host.context)) return Results.failed("pc_not_set", NOT_SET)
            return reply(PcBridge.call(host.http, host.context, "status"))
        }
    }

    private val KEYS = listOf("volume_up", "volume_down", "mute", "play_pause", "next", "previous")

    private object PcMedia : PhoneTool {
        override val name = "pc_media"

        override fun declaration() = Schema.function(
            name,
            "PC volume and media keys: volume up or down, mute, play or pause, next, previous track.",
            Schema.string("key", "What to press.", required = true, choices = KEYS),
            Schema.integer("times", "Repeat count for volume steps, 1 to 10. Default 1."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            if (!PcBridge.configured(host.context)) return Results.failed("pc_not_set", NOT_SET)
            val key = args.text("key")
            if (key !in KEYS) return Results.failed("bad_key", "Ask what to do: volume, mute, play, pause, next or previous.")
            val times = args.optInt("times", 1).coerceIn(1, 10)
            return reply(PcBridge.call(host.http, host.context, "media", JSONObject().put("key", key).put("times", times)))
        }
    }

    private object PcOpen : PhoneTool {
        override val name = "pc_open"

        override fun declaration() = Schema.function(
            name,
            "Opens something on the user's PC: an app from the list the user set up on the PC (chrome, notepad, calculator...) " +
                "or an http(s) link in the PC's browser.",
            Schema.string("target", "An app name, or a full http or https link.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            if (!PcBridge.configured(host.context)) return Results.failed("pc_not_set", NOT_SET)
            val target = args.text("target").take(300)
            if (target.isEmpty()) return Results.failed("empty", "Ask what to open on the PC.")
            return reply(PcBridge.call(host.http, host.context, "open", JSONObject().put("target", target)))
        }
    }

    private val POWER = listOf("lock", "sleep", "restart", "shutdown")

    private object PcPower : PhoneTool {
        override val name = "pc_power"

        override fun declaration() = Schema.function(
            name,
            "Locks, sleeps, restarts or shuts down the user's PC. The user taps Allow on the phone first. " +
                "Sleep, restart and shutdown also have to be switched on in the PC helper.",
            Schema.string("action", "What to do.", required = true, choices = POWER),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            if (!PcBridge.configured(host.context)) return Results.failed("pc_not_set", NOT_SET)
            val action = args.text("action")
            if (action !in POWER) return Results.failed("bad_action", "Ask whether to lock, sleep, restart or shut down.")
            // The Allow card is shown by the Accessibility service, so this needs Phone control on.
            val overlay = ControlService.instance?.overlay?.takeUnless { it.stopped }
                ?: return Results.failed(
                    "needs_phone_control",
                    "PC power actions need the Allow card, which needs Phone control turned on in the app. Tell the user.",
                )
            if (!overlay.confirm("$action the PC")) {
                return Results.failed("cancelled", "The user didn't allow it. Say so and don't try again.")
            }
            return reply(PcBridge.call(host.http, host.context, "power", JSONObject().put("action", action)))
        }
    }
}
