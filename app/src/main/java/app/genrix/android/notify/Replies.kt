package app.genrix.android.notify

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.genrix.android.Diag

/**
 * Hands-free replies through a chat notification's own Reply box (WhatsApp, Messages…),
 * without opening the app. Kept in memory only. Sending takes two steps: [prepare] (Genrix
 * reads the reply back) and [send], which goes through only if the user's own next words
 * are a clear yes ([VoiceConsent]); the model can't approve it by itself.
 */
object Replies {
    private class Target(val key: String, val who: String, val app: String, val pkg: String, val action: Notification.Action, val at: Long)
    private class Pending(val id: Int, val text: String, val at: Long, val readBack: String)

    private val targets = linkedMapOf<Int, Target>()
    private val ids = mutableMapOf<String, Int>()
    private var next = 1
    private var pending: Pending? = null
    private val heard = StringBuilder()

    /** The notification's reply action, if it has one; returns the short id the model uses. */
    @Synchronized
    fun register(key: String, who: String, app: String, pkg: String, n: Notification, at: Long): Int? {
        val action = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true } ?: return null
        val id = ids.getOrPut(key) { next++ }
        targets[id] = Target(key, who, app, pkg, action, at)
        while (targets.size > MAX_TARGETS) targets.remove(targets.keys.first())?.let { ids.remove(it.key) }
        return id
    }

    /**
     * The reply id of a recent message from [name] in one of [packages], so "message Mom" answers
     * her notification hands-free instead of opening the app (which would clear it).
     */
    @Synchronized
    fun idFor(name: String, packages: Set<String>, now: Long): Int? {
        val wanted = name.trim().lowercase().takeIf { it.length >= 2 } ?: return null
        return targets.entries.lastOrNull { (_, t) ->
            t.pkg in packages && now - t.at < FRESH_MS && sameName(t.who.lowercase(), wanted)
        }?.key
    }

    /** "Mom" matches "Mom", and "rahul" matches "Rahul Sharma" (whole words). */
    fun sameName(who: String, wanted: String): Boolean =
        who == wanted || who.split(Regex("\\s+")).containsAll(wanted.split(Regex("\\s+")))

    /** Stores the reply to confirm; returns the line Genrix reads back, or null for an unknown id. */
    @Synchronized
    fun prepare(id: Int, text: String, now: Long): String? {
        val target = targets[id] ?: return null
        val readBack = "Send \"$text\" to ${target.who} on ${target.app}?"
        pending = Pending(id, text, now, readBack)
        heard.clear()
        return readBack
    }

    /** Everything the user says or types; only words after a read-back count for consent. */
    @Synchronized
    fun heard(text: String) {
        if (pending != null && text.isNotBlank()) heard.append(' ').append(text)
    }

    sealed interface Outcome {
        data class Sent(val who: String, val app: String) : Outcome
        data class Refused(val why: String) : Outcome
    }

    @Synchronized
    fun send(context: Context, now: Long): Outcome {
        val p = pending ?: return Outcome.Refused("Nothing is waiting to be sent. Prepare the reply first.")
        val target = targets[p.id]
        pending = null
        if (target == null) return Outcome.Refused("That chat's notification is gone, so it can't be answered from here.")
        if (now - p.at > CONSENT_MS) return Outcome.Refused("The confirmation timed out. Ask again if the user still wants it.")
        if (!VoiceConsent.confirms(heard.toString(), p.text, p.readBack)) {
            return Outcome.Refused("The user didn't clearly say yes, so nothing was sent. Don't retry unless they ask.")
        }
        val inputs = target.action.remoteInputs ?: return Outcome.Refused("This chat can't be answered from the notification.")
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, p.text) } }
        val fill = Intent().also { RemoteInput.addResultsToIntent(inputs, it, results) }
        return runCatching { target.action.actionIntent.send(context, 0, fill) }.fold(
            onSuccess = {
                Diag.i("reply: sent through the notification")
                Outcome.Sent(target.who, target.app)
            },
            onFailure = {
                Diag.w("reply: notification refused (${it.javaClass.simpleName})")
                Outcome.Refused("The chat app didn't accept the reply. Offer to open the chat instead.")
            },
        )
    }

    private const val MAX_TARGETS = 30
    private const val CONSENT_MS = 2 * 60_000L
    private const val FRESH_MS = 2 * 60 * 60_000L
}
