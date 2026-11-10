package app.genrix.android.tools

import android.content.Intent
import android.net.Uri
import app.genrix.android.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Date and time, weather, and opening a web search. */
object InfoTools {
    fun all(): List<PhoneTool> = listOf(DateTime, GetWeather, OpenWebSearch)

    private object DateTime : PhoneTool {
        override val name = "get_current_datetime"

        override fun declaration() = Schema.function(name, "The phone's current local date, time and time zone.")

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject =
            Results.done(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm VV")))
    }

    private object GetWeather : PhoneTool {
        override val name = "get_weather"

        override fun declaration() = Schema.function(
            name,
            "Current weather and today's forecast for a city (default: the last city asked about).",
            Schema.string("city", "City; empty for the usual one."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val settings = Settings(host.context)
            val city = args.text("city").ifEmpty { settings.homeCity }
            if (city.isEmpty()) return Results.failed("needs_city", "Ask which city.")
            return try {
                val line = withContext(Dispatchers.IO) { Weather.fetch(host.http, city) }
                    ?: return Results.failed("not_found", "I couldn't find a place called $city.")
                settings.homeCity = city
                Results.done(line)
            } catch (_: IOException) {
                Results.failed("weather_failed", "The weather service didn't answer.")
            }
        }
    }

    private object OpenWebSearch : PhoneTool {
        override val name = "open_web_search"

        override fun declaration() = Schema.function(
            name,
            "Opens Google search results in the browser for the user to read. Use for news, prices, " +
                "scores and anything recent.",
            Schema.string("query", "The search words.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val query = args.text("query")
            if (query.isEmpty()) return Results.failed("no_query", "Ask what to search for.")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
            return host.openApp(intent, "browser", "The browser will open with results for \"$query\" after you speak.")
        }
    }
}

/** Open-Meteo, the same free source as desktop (non-commercial; see docs/PROVENANCE.md). */
object Weather {
    private val CODES = mapOf(
        0 to "clear", 1 to "mostly clear", 2 to "partly cloudy", 3 to "cloudy", 45 to "foggy", 48 to "foggy",
        51 to "light drizzle", 53 to "drizzle", 55 to "heavy drizzle", 61 to "light rain", 63 to "rain",
        65 to "heavy rain", 71 to "light snow", 73 to "snow", 75 to "heavy snow", 80 to "rain showers",
        81 to "rain showers", 82 to "heavy showers", 95 to "thunderstorm", 96 to "thunderstorm with hail",
        99 to "thunderstorm with hail",
    )

    /** One line, or null if the place isn't found. Only the city name is sent. */
    fun fetch(http: OkHttpClient, city: String): String? {
        val geo = get(
            http,
            "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", city).addQueryParameter("count", "1").build().toString(),
        )
        val place = geo.optJSONArray("results")?.optJSONObject(0) ?: return null
        val forecast = get(
            http,
            "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", place.optDouble("latitude").toString())
                .addQueryParameter("longitude", place.optDouble("longitude").toString())
                .addQueryParameter("current", "temperature_2m,weather_code")
                .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                .addQueryParameter("forecast_days", "1")
                .addQueryParameter("timezone", "auto")
                .build().toString(),
        )
        return summary(place.optString("name", city), forecast)
    }

    fun summary(name: String, forecast: JSONObject): String {
        val current = forecast.optJSONObject("current") ?: JSONObject()
        val daily = forecast.optJSONObject("daily") ?: JSONObject()
        fun first(key: String) = daily.optJSONArray(key)?.opt(0)?.takeUnless { it == JSONObject.NULL }
        val sky = CODES[current.optInt("weather_code", -1)].orEmpty()
        val rain = first("precipitation_probability_max")
        val now = current.opt("temperature_2m")
        val low = first("temperature_2m_min")
        val high = first("temperature_2m_max")
        val today = if (low != null && high != null) "; today $low–$high°C" else ""
        return "$name: $now°C now, $sky$today" + if (rain != null) ", $rain% chance of rain." else "."
    }

    private fun get(http: OkHttpClient, url: String): JSONObject =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse { throw IOException("bad JSON") }
        }
}
