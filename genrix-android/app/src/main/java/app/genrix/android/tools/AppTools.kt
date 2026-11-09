package app.genrix.android.tools

import android.content.Intent
import app.genrix.android.Branding
import app.genrix.android.control.ControlRules
import app.genrix.android.control.ControlService
import app.genrix.android.control.ScreenText
import kotlinx.coroutines.delay
import org.json.JSONObject

data class InstalledApp(val label: String, val packageName: String)

/** App-name matching, pure so it is unit-tested: exact, starts with, then contains. */
object AppMatch {
    fun find(query: String, apps: List<InstalledApp>): List<InstalledApp> {
        val q = query.lowercase().trim().removeSuffix(" app")
        if (q.isEmpty()) return emptyList()
        val tiers = listOf<(String) -> Boolean>({ it == q }, { it.startsWith(q) }, { q in it })
        for (matches in tiers) {
            val found = apps.filter { matches(it.label.lowercase().trim()) }.distinctBy { it.packageName }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }
}

/** Opening any installed app by name. */
object AppTools {
    fun all(): List<PhoneTool> = listOf(OpenApp)

    private object OpenApp : PhoneTool {
        override val name = "open_app"

        override fun declaration() = Schema.function(
            name,
            "Opens an installed app by name. With phone control on it opens at once and returns " +
                "the app's screen, so you can carry on with ui_click / ui_type.",
            Schema.string("name", "App name as the user said it, e.g. WhatsApp, Instagram, Camera.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val pm = host.context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0).map {
                InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName)
            }
            val matches = AppMatch.find(args.text("name"), apps)
            val app = when {
                matches.isEmpty() -> return Results.failed("not_found", "No app called ${args.text("name")} is installed.")
                matches.size > 1 -> return Results.ask("Several apps match. Ask which.", matches.take(6).map { it.label })
                else -> matches.single()
            }
            val intent = pm.getLaunchIntentForPackage(app.packageName)
                ?: return Results.failed("no_launch", "${app.label} can't be opened from outside.")
            return openAndRead(host, intent, app, "Opened ${app.label}.")
                ?: host.openApp(intent, app.label, "${app.label} will open after you speak.")
        }
    }

    /**
     * Opens [app] at once and returns its screen so the model can carry on with ui_* tools;
     * null when phone control is off (the caller opens it after the reply instead).
     */
    suspend fun openAndRead(host: ToolHost, intent: Intent, app: InstalledApp, done: String): JSONObject? {
        val control = ControlService.instance?.takeIf { !it.overlay.stopped } ?: return null
        val started = host.startNow(intent, app.label, done)
        if (started.optString("status") != "done") return started
        if (!control.waitFor(app.packageName, OPEN_WAIT_MS)) {
            return Results.done("$done ${app.label} is still loading. Call ui_read in a moment.")
        }
        delay(SETTLE_MS)
        control.overlay.showActive()
        if (ControlRules.blocked(app.packageName)) {
            return Results.done("Opened ${app.label}. ${Branding.ASSISTANT_NAME} doesn't read or touch this app; the user takes it from here.")
        }
        val snap = control.snapshot() ?: return Results.done(done)
        return Results.done("$done Screen now:\n" + ScreenText.describe(snap.appLabel, snap.elements, snap.more))
    }

    private const val OPEN_WAIT_MS = 6_000L
    private const val SETTLE_MS = 800L
}
