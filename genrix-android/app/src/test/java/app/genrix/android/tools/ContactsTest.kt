package app.genrix.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {
    private fun contact(name: String, vararg numbers: Pair<String, String>) =
        Contact(name, numbers.map { PhoneNumber(it.first, it.second) })

    private val book = listOf(
        contact("Rahul Sharma", "mobile" to "+91 98765 43210"),
        contact("Rahul Verma", "mobile" to "98111 22233", "work" to "022 4000 1234"),
        contact("Mummy", "mobile" to "99999 00000"),
        contact("Pooja Rahulkar", "mobile" to "90000 11111"),
    )

    @Test
    fun exactNameWins() {
        assertEquals(listOf("Mummy"), ContactMatch.find("mummy", book).map { it.name })
    }

    @Test
    fun severalPeopleWithTheSameFirstNameAreAllReturned() {
        assertEquals(listOf("Rahul Sharma", "Rahul Verma"), ContactMatch.find("Rahul", book).map { it.name })
        assertEquals(listOf("Rahul Verma"), ContactMatch.find("rahul v", book).map { it.name })
    }

    @Test
    fun sameNumberWrittenTwoWaysCountsOnce() {
        val numbers = listOf(PhoneNumber("mobile", "+91 98765 43210"), PhoneNumber("mobile", "098765-43210"))
        assertEquals(1, ContactMatch.distinctNumbers(numbers).size)
    }

    @Test
    fun oneNumberIsUsedDirectly() {
        val found = Recipients.pick(book[0], "") as Recipients.Result.Found
        assertEquals("+91 98765 43210", found.number)
    }

    @Test
    fun severalNumbersAskUnlessTheUserSaidWhich() {
        val asked = Recipients.pick(book[1], "") as Recipients.Result.Answer
        assertEquals("needs_clarification", asked.json.getString("status"))
        assertEquals(2, asked.json.getJSONArray("choices").length())
        assertTrue(asked.json.getJSONArray("choices").getString(1).endsWith("1234"))

        assertEquals("022 4000 1234", (Recipients.pick(book[1], "work") as Recipients.Result.Found).number)
        assertEquals("98111 22233", (Recipients.pick(book[1], "2233") as Recipients.Result.Found).number)
    }
}
