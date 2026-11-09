package app.genrix.android.tools

import android.content.Intent
import android.net.Uri
import app.genrix.android.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Who an email goes to, decided without guessing. Pure, so it is unit-tested. */
object MailPick {
    sealed interface Pick {
        data class Address(val address: String, val name: String) : Pick
        data class Ask(val message: String, val choices: List<String>) : Pick
        data class NotFound(val message: String) : Pick
    }

    private val ADDRESS = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    fun isAddress(text: String) = ADDRESS.matches(text.trim())

    fun choose(query: String, matches: List<Contact>): Pick = when {
        isAddress(query) -> Pick.Address(query.trim().lowercase(), query.trim())
        matches.isEmpty() -> Pick.NotFound("No contact called $query has an email address. Ask the user for the address.")
        matches.size > 1 -> Pick.Ask("Several contacts match \"$query\". Ask which one.", matches.take(6).map { it.name })
        matches.single().emails.size > 1 -> Pick.Ask(
            "${matches.single().name} has several email addresses. Ask which one.",
            matches.single().emails.take(6),
        )
        else -> Pick.Address(matches.single().emails.single(), matches.single().name)
    }
}

/**
 * Email through the phone's email app (Gmail, Outlook…): no Google sign-in or Gmail API, so
 * no restricted scopes or verification. The compose screen opens filled in and the user taps
 * Send. Reading a particular email is done in the app with the screen tools.
 */
object MailTools {
    fun all(): List<PhoneTool> = listOf(EmailCompose)

    private object EmailCompose : PhoneTool {
        override val name = "email_compose"

        override fun declaration() = Schema.function(
            name,
            "Opens the email app's compose screen with the recipient, subject and text filled in; the user " +
                "checks it and taps Send. To reply to an email the user is reading, use the app's Reply with the ui tools.",
            Schema.string("to", "Contact name or email address.", required = true),
            Schema.string("subject", "Subject line."),
            Schema.string("body", "The email text, in the user's words.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val to = args.text("to").ifEmpty { return Results.failed("no_recipient", "Ask who the email is for.") }
            val matches = if (MailPick.isAddress(to)) {
                emptyList()
            } else {
                if (!host.hasPermission(ContactsReader.PERMISSION)) {
                    host.requestPermission(ContactsReader.PERMISSION)
                    return Results.needsPermission("Contacts")
                }
                withContext(Dispatchers.IO) { ContactsReader(host.context).searchEmails(to) }
            }
            val address = when (val pick = MailPick.choose(to, matches)) {
                is MailPick.Pick.Address -> pick
                is MailPick.Pick.Ask -> return Results.ask(pick.message, pick.choices)
                is MailPick.Pick.NotFound -> return Results.failed("not_found", pick.message)
            }
            val uri = Uri.parse(
                "mailto:" + Uri.encode(address.address, "@") +
                    "?subject=" + Uri.encode(args.text("subject")) + "&body=" + Uri.encode(args.text("body")),
            )
            // Some apps (Gmail) read the extras and ignore the mailto query, others the reverse: send both.
            val intent = Intent(Intent.ACTION_SENDTO, uri)
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(address.address))
                .putExtra(Intent.EXTRA_SUBJECT, args.text("subject"))
                .putExtra(Intent.EXTRA_TEXT, args.text("body"))
            Diag.i("email: compose, subject ${args.text("subject").length} chars, body ${args.text("body").length} chars")
            return host.openApp(intent, "Email", "The email to ${address.name} is ready in the email app; the user taps Send.")
        }
    }
}
