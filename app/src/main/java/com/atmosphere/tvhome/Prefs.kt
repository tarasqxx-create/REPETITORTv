package com.atmosphere.tvhome

import android.content.Context

/** Все настройки хранятся в SharedPreferences и переживают перезагрузку ТВ. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("atmosphere_prefs", Context.MODE_PRIVATE)

    var themeId: String
        get() = sp.getString("theme", "christmas") ?: "christmas"
        set(v) {
            sp.edit().putString("theme", v).apply()
        }

    /** 0 — выключено, 1 — низкая, 2 — средняя, 3 — высокая */
    var intensity: Int
        get() = sp.getInt("intensity", 2)
        set(v) {
            sp.edit().putInt("intensity", v).apply()
        }

    var snowOn: Boolean
        get() = sp.getBoolean("snow_on", true)
        set(v) {
            sp.edit().putBoolean("snow_on", v).apply()
        }

    var rainOn: Boolean
        get() = sp.getBoolean("rain_on", true)
        set(v) {
            sp.edit().putBoolean("rain_on", v).apply()
        }

    var decorOn: Boolean
        get() = sp.getBoolean("decor_on", true)
        set(v) {
            sp.edit().putBoolean("decor_on", v).apply()
        }

    /** 0 — выкл, 1 — мягкое, 2 — среднее, 3 — яркое */
    var glow: Int
        get() = sp.getInt("glow", 2)
        set(v) {
            sp.edit().putInt("glow", v).apply()
        }

    var favorites: List<String>
        get() = (sp.getString("favorites", "") ?: "").split("|").filter { it.isNotEmpty() }
        set(v) {
            sp.edit().putString("favorites", v.joinToString("|")).apply()
        }

    var favSeeded: Boolean
        get() = sp.getBoolean("fav_seeded", false)
        set(v) {
            sp.edit().putBoolean("fav_seeded", v).apply()
        }

    val hasCity: Boolean
        get() = sp.contains("city_lat") && sp.contains("city_lon")

    val cityName: String
        get() = sp.getString("city_name", "") ?: ""

    val cityLat: Double
        get() = (sp.getString("city_lat", "0") ?: "0").toDoubleOrNull() ?: 0.0

    val cityLon: Double
        get() = (sp.getString("city_lon", "0") ?: "0").toDoubleOrNull() ?: 0.0

    fun setCity(name: String, lat: Double, lon: Double) {
        sp.edit()
            .putString("city_name", name)
            .putString("city_lat", lat.toString())
            .putString("city_lon", lon.toString())
            .apply()
    }
}
