package app.genrix.android.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherTest {
    @Test
    fun oneLineSummary() {
        val forecast = JSONObject(
            """{"current":{"temperature_2m":29.4,"weather_code":2},
               "daily":{"temperature_2m_min":[21.0],"temperature_2m_max":[33.1],"precipitation_probability_max":[10]}}""",
        )
        assertEquals(
            "Pune: 29.4°C now, partly cloudy; today 21.0–33.1°C, 10% chance of rain.",
            Weather.summary("Pune", forecast),
        )
    }

    @Test
    fun missingRainChanceIsLeftOut() {
        val forecast = JSONObject("""{"current":{"temperature_2m":5,"weather_code":0},"daily":{}}""")
        assertEquals("Oslo: 5°C now, clear.", Weather.summary("Oslo", forecast))
    }
}
