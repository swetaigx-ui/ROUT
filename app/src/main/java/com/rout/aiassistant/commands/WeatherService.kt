package com.rout.aiassistant.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Weather via Open-Meteo (free, no API key required).
 * https://open-meteo.com/ - non-commercial free tier, real data, never fabricated.
 */
object WeatherService {

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 12000
        }
        val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()
        return text
    }

    private val wmo = mapOf(
        0 to "clear sky", 1 to "mainly clear", 2 to "partly cloudy", 3 to "overcast",
        45 to "fog", 48 to "depositing rime fog",
        51 to "light drizzle", 53 to "drizzle", 55 to "dense drizzle",
        61 to "light rain", 63 to "rain", 65 to "heavy rain",
        66 to "freezing rain", 67 to "heavy freezing rain",
        71 to "light snow", 73 to "snow", 75 to "heavy snow", 77 to "snow grains",
        80 to "light showers", 81 to "showers", 82 to "violent showers",
        85 to "snow showers", 86 to "heavy snow showers",
        95 to "thunderstorm", 96 to "thunderstorm with hail", 99 to "thunderstorm with heavy hail"
    )

    data class Weather(val place: String, val tempC: Double, val humidity: Int, val windKmh: Double, val summary: String)

    suspend fun current(lat: Double, lon: Double, placeLabel: String): Weather? = withContext(Dispatchers.IO) {
        try {
            val raw = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto")
            val cur = JSONObject(raw).getJSONObject("current")
            Weather(
                placeLabel,
                cur.getDouble("temperature_2m"),
                cur.getInt("relative_humidity_2m"),
                cur.getDouble("wind_speed_10m"),
                wmo[cur.getInt("weather_code")] ?: "unknown conditions"
            )
        } catch (e: Exception) { null }
    }

    suspend fun geocode(city: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(city, "UTF-8")
            val raw = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json")
            val r = JSONObject(raw).optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
            val label = buildString {
                append(r.getString("name"))
                r.optString("admin1").takeIf { it.isNotBlank() }?.let { append(", $it") }
                r.optString("country").takeIf { it.isNotBlank() }?.let { append(", $it") }
            }
            Triple(r.getDouble("latitude"), r.getDouble("longitude"), label)
        } catch (e: Exception) { null }
    }

    /** Sunrise and sunset times (local) for today, e.g. ("6:12 am", "5:48 pm"). */
    suspend fun sunriseSunset(lat: Double, lon: Double): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val raw = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=sunrise,sunset&forecast_days=1&timezone=auto")
            val daily = JSONObject(raw).getJSONObject("daily")
            val sr = daily.getJSONArray("sunrise").getString(0)
            val ss = daily.getJSONArray("sunset").getString(0)
            fun hhmm(iso: String): String {
                val t = iso.substringAfter("T")
                var h = t.substringBefore(":").toInt(); val m = t.substringAfter(":")
                val ampm = if (h >= 12) "pm" else "am"; if (h > 12) h -= 12; if (h == 0) h = 12
                return "$h:$m $ampm"
            }
            hhmm(sr) to hhmm(ss)
        } catch (e: Exception) { null }
    }

    /** US AQI for a location, or null when the air-quality service is unreachable. */
    suspend fun aqi(lat: Double, lon: Double): Int? = withContext(Dispatchers.IO) {
        try {
            val raw = get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi")
            JSONObject(raw).getJSONObject("current").optInt("us_aqi").takeIf { it > 0 }
        } catch (e: Exception) { null }
    }
}
