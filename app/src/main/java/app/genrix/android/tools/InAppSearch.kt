package app.genrix.android.tools

import android.view.accessibility.AccessibilityNodeInfo
import app.genrix.android.control.ControlRules
import app.genrix.android.control.ControlService
import kotlinx.coroutines.delay

/**
 * Searching inside the app in front, done by the phone rather than step by step by the
 * model: tap the app's Search, type the words, open the result whose name matches. Only
 * safe taps (a search box and a result's name); anything risky is left to the model's
 * Allow-checked tools. Returns what happened, for the model.
 */
object InAppSearch {
    suspend fun open(control: ControlService, words: String): String {
        val wanted = words.trim().lowercase()
        if (wanted.isEmpty()) return ""
        val field = control.findFirst { it.isEditable } ?: run {
            val search = control.findFirst { !it.isEditable && control.labelOf(it).trim().lowercase().startsWith("search") }
                ?: return "Couldn't find the app's search; use ui_read and search for \"$words\" yourself."
            control.click(search)
            delay(SETTLE_MS)
            control.focusedField() ?: control.findFirst { it.isEditable }
        } ?: return "The search box didn't open; use ui_read and search for \"$words\" yourself."
        if (!control.type(field, words)) return "Typing in the search box didn't work; try ui_type."
        delay(RESULTS_MS)
        val hit = best(control, wanted) ?: return "Searched for \"$words\" but no result has that name; read the results to the user."
        if (ControlRules.clickRisk(control.labelOf(hit), hit.viewIdResourceName.orEmpty()) != ControlRules.Risk.Safe) {
            return "Searched for \"$words\"; open the right result with ui_click."
        }
        control.click(hit)
        delay(SETTLE_MS)
        return "Searched for \"$words\" and opened \"${control.labelOf(hit).take(60)}\"."
    }

    /** The result named most like [wanted]: exact name first, then the shortest name containing it. */
    private fun best(control: ControlService, wanted: String): AccessibilityNodeInfo? {
        val hits = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        control.findFirst { node ->
            val label = control.labelOf(node).trim().lowercase()
            if (!node.isEditable && wanted in label) hits += node to label
            false // keep walking the whole screen
        }
        return hits.minByOrNull { (_, label) -> if (label == wanted) 0 else label.length }?.first
    }

    private const val SETTLE_MS = 1_200L
    private const val RESULTS_MS = 2_500L
}
