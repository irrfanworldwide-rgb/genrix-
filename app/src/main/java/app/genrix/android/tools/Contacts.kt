package app.genrix.android.tools

import android.Manifest
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone

data class PhoneNumber(val label: String, val number: String)

data class Contact(val name: String, val numbers: List<PhoneNumber>, val emails: List<String> = emptyList())

/** Name matching, kept pure so it is unit-tested: exact, whole words, word starts, contains. */
object ContactMatch {
    fun find(query: String, all: List<Contact>): List<Contact> {
        val q = normalise(query)
        if (q.isEmpty()) return emptyList()
        val queryWords = q.split(' ')
        fun words(contact: Contact) = normalise(contact.name).split(' ')
        val tiers = listOf<(Contact) -> Boolean>(
            { normalise(it.name) == q },
            { contact -> queryWords.all { it in words(contact) } },
            { contact -> queryWords.all { qw -> words(contact).any { it.startsWith(qw) } } },
            { q in normalise(it.name) },
        )
        for (matches in tiers) {
            val found = all.filter(matches)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** Numbers that differ only in spaces, dashes or a leading country code count once. */
    fun distinctNumbers(numbers: List<PhoneNumber>): List<PhoneNumber> =
        numbers.distinctBy { it.number.filter(Char::isDigit).takeLast(10) }

    private fun normalise(text: String) = text.lowercase().trim().replace(Regex("\\s+"), " ")
}

/** Reads the phone's contacts (READ_CONTACTS). Only names, numbers and email addresses; nothing is stored. */
class ContactsReader(private val context: Context) {
    fun search(query: String): List<Contact> {
        val firstWord = query.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (firstWord.isEmpty()) return emptyList()
        val byName = linkedMapOf<String, MutableList<PhoneNumber>>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$firstWord%"),
            "${Phone.DISPLAY_NAME} ASC",
        )?.use { rows ->
            while (rows.moveToNext()) {
                val name = rows.getString(0) ?: continue
                val number = rows.getString(1) ?: continue
                val label = Phone.getTypeLabel(context.resources, rows.getInt(2), rows.getString(3)).toString()
                byName.getOrPut(name) { mutableListOf() } += PhoneNumber(label.lowercase(), number)
            }
        }
        val all = byName.map { (name, numbers) -> Contact(name, ContactMatch.distinctNumbers(numbers)) }
        return ContactMatch.find(query, all)
    }

    /** Contacts matching [query] that have an email address, with their addresses. */
    fun searchEmails(query: String): List<Contact> {
        val firstWord = query.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (firstWord.isEmpty()) return emptyList()
        val byName = linkedMapOf<String, MutableSet<String>>()
        context.contentResolver.query(
            Email.CONTENT_URI,
            arrayOf(Email.DISPLAY_NAME_PRIMARY, Email.ADDRESS),
            "${Email.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%$firstWord%"),
            "${Email.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { rows ->
            while (rows.moveToNext()) {
                val name = rows.getString(0) ?: continue
                val address = rows.getString(1)?.trim()?.lowercase()?.takeIf { it.contains('@') } ?: continue
                byName.getOrPut(name) { linkedSetOf() } += address
            }
        }
        return ContactMatch.find(query, byName.map { (name, emails) -> Contact(name, emptyList(), emails.toList()) })
    }

    companion object {
        const val PERMISSION = Manifest.permission.READ_CONTACTS
    }
}
