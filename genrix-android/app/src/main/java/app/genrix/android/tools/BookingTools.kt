package app.genrix.android.tools

import android.content.Intent
import android.net.Uri
import app.genrix.android.control.ControlRules
import app.genrix.android.control.ControlService
import app.genrix.android.control.ScreenText
import app.genrix.android.control.SightAim
import app.genrix.android.data.KeyVault
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Ticket booking. Indian ticket apps offer no public booking API, so Genrix opens the
 * user's ticket app and, with phone control on, works through it on screen (movie, city,
 * date, show, seats) up to the payment step, where it stops: taps that pay are refused
 * (ControlRules.Risk.Money) and payment apps are blocked, so the user always pays. Without
 * phone control it's a handoff: the user does it all in the app. Never "booked".
 */
object BookingTools {
    fun all(): List<PhoneTool> = listOf(BookTickets)

    /** Apps tried in order, matched by their name on the phone (no guessed package names). Paytm is a
     *  payments app, so it's only opened for the user, never controlled. */
    private val APPS = listOf("BookMyShow", "District", "Paytm")

    /**
     * Ticket apps search in the city they're set to, so a wrong city gives the wrong cinemas.
     * When the asked-for city isn't on the app's screen, the phone changes it: it finds the
     * city name at the top by sight, taps it, and searches the new one. Null when it's right
     * now; otherwise what the model should do.
     */
    private suspend fun setCity(control: ControlService, host: ToolHost, city: String): JSONObject? {
        if (city.isBlank() || shows(control, city)) return null
        val key = KeyVault(host.context).read(KeyVault.GEMINI_KEY)
        val shot = control.screenshot()
        val seen = if (key != null && shot != null) SightAim(host.http, key).locate(shot, CITY_TARGET) else null
        if (seen is SightAim.Outcome.Found) {
            val there = control.nodeAt(seen.x, seen.y)
            val label = there?.let(control::labelOf).orEmpty()
            if (ControlRules.clickRisk(label.ifEmpty { seen.sees }, "") == ControlRules.Risk.Safe && control.tapAt(seen.x, seen.y)) {
                delay(CITY_MS)
                InAppSearch.open(control, city)
                delay(CITY_MS)
                if (shows(control, city)) return null
            }
        }
        val snap = control.snapshot() ?: return Results.failed("no_screen", "The screen couldn't be read.")
        return Results.done(
            "The app isn't set to $city and changing it didn't work. Change it yourself with ui_click / ui_type now (the city " +
                "name is near the top), then call book_tickets again. Don't ask the user to do it. Screen now:\n" +
                ScreenText.describe(snap.appLabel, snap.elements, snap.more),
        )
    }

    private fun shows(control: ControlService, city: String): Boolean =
        control.snapshot()?.elements?.any { city.lowercase() in it.label.lowercase() } == true

    private const val CITY_TARGET = "the current city name near the top of the screen (tapping it changes the city)"
    private const val CITY_MS = 1_500L

    private object BookTickets : PhoneTool {
        override val name = "book_tickets"

        override fun declaration() = Schema.function(
            name,
            "Starts a movie ticket booking in the user's ticket app (or a web search). With phone control on " +
                "it returns the app's screen and you carry on in it up to the payment step; the user pays. " +
                "If the user hasn't picked a movie, call it without one to see what's showing.",
            Schema.string("movie", "Movie name; leave empty if the user wants to see what's showing."),
            Schema.string("city", "City or area."),
            Schema.string("date", "When, as the user said it, e.g. tomorrow evening."),
            Schema.integer("tickets", "How many tickets."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val request = BookingRequest(args.text("movie"), args.text("city"), args.text("date"), args.optInt("tickets", 0))
            val pm = host.context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val installed = pm.queryIntentActivities(launcher, 0).map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            val app = APPS.firstNotNullOfOrNull { name -> AppMatch.find(name, installed).singleOrNull() }
            // A fresh start on the app's home screen, not wherever an earlier booking was left.
            val intent = app?.let { pm.getLaunchIntentForPackage(it.packageName) }
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (intent != null) {
                val control = ControlService.instance?.takeIf { !it.overlay.stopped }
                if (control != null && request.movie.isNotEmpty() && !ControlRules.blocked(app.packageName)) {
                    // The phone searches for the film itself; the model takes over from its page.
                    val opened = AppTools.openAndRead(host, intent, app, "Opened ${app.label}.")
                    if (opened?.optString("status") != "done") return opened ?: Results.failed("not_opened", "${app.label} didn't open.")
                    setCity(control, host, request.city)?.let { return it }
                    val searched = InAppSearch.open(control, request.movie)
                    val snap = control.snapshot() ?: return Results.done("Opened ${app.label}. $searched ${request.plan(searched = true)}")
                    return Results.done(
                        "Opened ${app.label}. $searched ${request.plan(searched = true)} Screen now:\n" +
                            ScreenText.describe(snap.appLabel, snap.elements, snap.more),
                    )
                }
                AppTools.openAndRead(host, intent, app, "Opened ${app.label}. ${request.plan()}")?.let { return it }
            }
            // Without phone control there's no screen to read the films from.
            if (request.movie.isEmpty()) return Results.failed("no_movie", "Ask which movie.")
            return if (intent != null) {
                host.openApp(
                    intent,
                    app.label,
                    "${app.label} will open after you speak. Tell the user to search \"${request.movie}\" there, then " +
                        "pick ${request.summary()}, check the total and pay in ${app.label}. Nothing is booked yet.",
                )
            } else {
                val search = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(request.query())}"))
                host.openApp(
                    search,
                    "browser",
                    "No ticket app is installed, so the browser will open showtimes for \"${request.movie}\". The user books " +
                        "and pays on the site they choose. Nothing is booked yet.",
                )
            }
        }
    }
}

/** What the user asked for; only their words, never invented shows or prices. */
data class BookingRequest(val movie: String = "", val city: String = "", val date: String = "", val tickets: Int = 0) {
    fun query() = listOf(movie, "movie tickets", city, date).filter { it.isNotBlank() }.joinToString(" ")

    /** What the model does next on screen, from what the user said; asks for what's missing. */
    fun plan(searched: Boolean = false): String = buildString {
        append("Do these taps yourself with the ui tools; the user only chooses and pays. ")
        append(
            when {
                movie.isBlank() -> "Now, on screen: find the movies showing"
                searched -> "Now, on screen: open the film's booking"
                else -> "Now, on screen: search \"$movie\""
            },
        )
        append(if (city.isBlank()) ", check the city shown (ask the user if it looks wrong)" else ", make sure the city is $city")
        append(if (date.isBlank()) ", keep today unless the user said a day" else ", pick $date")
        if (movie.isBlank()) append(". Read out a few of the movies and ask which one")
        append(". Read out the cinemas and show times you see and let the user choose, unless they already said one.")
        append(if (tickets > 0) " Choose $tickets ticket" + (if (tickets > 1) "s" else "") + "." else " Ask how many tickets.")
        append(" Pick seats together near the middle unless the user says otherwise.")
        append(" Stop at the payment step: tell the user the total you see and that they pay. Never say booked.")
    }

    fun summary(): String = listOfNotNull(
        tickets.takeIf { it > 0 }?.let { "$it ticket" + if (it > 1) "s" else "" },
        date.ifBlank { null },
        city.ifBlank { null }?.let { "in $it" },
    ).joinToString(", ").ifEmpty { "the show and seats" }
}
