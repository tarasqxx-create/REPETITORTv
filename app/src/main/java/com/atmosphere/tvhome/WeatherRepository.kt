package com.atmosphere.tvhome

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherResult(val temp: Int, val code: Int, val isDay: Boolean)

class CityResult(val name: String, val admin: String, val country: String, val lat: Double, val lon: Double) {
    val label: String
        get() = listOf(name, admin, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/**
 * Погода: Open-Meteo (https://open-meteo.com) — бесплатно, БЕЗ API-ключа.
 * Поиск города: Open-Meteo Geocoding API (тоже без ключа).
 */
object WeatherRepository {

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "AtmosphereTV/1.0")
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun fetch(lat: Double, lon: Double): WeatherResult {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
            "&current=temperature_2m,weather_code,is_day&timezone=auto"
        val cur = JSONObject(httpGet(url)).getJSONObject("current")
        return WeatherResult(
            cur.getDouble("temperature_2m").roundToInt(),
            cur.getInt("weather_code"),
            cur.optInt("is_day", 1) == 1
        )
    }

    fun search(query: String): List<CityResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=6&language=ru&format=json"
        val arr = JSONObject(httpGet(url)).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<CityResult>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                CityResult(
                    o.optString("name"),
                    o.optString("admin1"),
                    o.optString("country"),
                    o.getDouble("latitude"),
                    o.getDouble("longitude")
                )
            )
        }
        return out
    }

    /** Коды погоды WMO → значок и описание. */
    fun describe(code: Int, day: Boolean): Pair<String, String> = when (code) {
        0 -> (if (day) "☀️" else "🌙") to "Ясно"
        1 -> (if (day) "🌤️" else "🌙") to "Преимущественно ясно"
        2 -> "⛅" to "Переменная облачность"
        3 -> "☁️" to "Пасмурно"
        45, 48 -> "🌫️" to "Туман"
        51, 53, 55 -> "🌦️" to "Морось"
        56, 57 -> "🌧️" to "Ледяная морось"
        61, 63, 65 -> "🌧️" to "Дождь"
        66, 67 -> "🌧️" to "Ледяной дождь"
        71, 73, 75 -> "🌨️" to "Снег"
        77 -> "🌨️" to "Снежная крупа"
        80, 81, 82 -> "🌦️" to "Ливень"
        85, 86 -> "🌨️" to "Снегопад"
        95 -> "⛈️" to "Гроза"
        96, 99 -> "⛈️" to "Гроза с градом"
        else -> "🌡️" to "Нет данных"
    }
}
