package com.atmosphere.tvhome

import android.graphics.Color

/** Превращает 0xAARRGGBB в Int-цвет. */
internal fun col(v: Long): Int = v.toInt()

/**
 * Описание одной «Атмосферы».
 * Чтобы добавить новую тему — добавьте ещё один AtmosphereTheme в список Themes.all (см. ниже).
 */
class AtmosphereTheme(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradient: IntArray,      // цвета фона сверху вниз
    val text: Int,
    val textDim: Int,
    val card: Int,               // цвет карточки
    val cardFocus: Int,          // цвет карточки в фокусе
    val accent: Int,             // цвет рамки фокуса
    val scrim: Int,              // затемнение внизу экрана для читаемости
    val snow: Boolean = false,
    val snowAmount: Float = 1f,
    val rain: Boolean = false,
    val glass: Boolean = false,  // капли на стекле
    val stars: Boolean = false,
    val moon: Boolean = false,
    val clouds: Boolean = false,
    val cloudColor: Int = Color.WHITE,
    val cloudAlpha: Float = 0.2f,
    val bokeh: Boolean = false,  // мягкие тёплые огоньки
    val lamp: Float = 0f,        // сила тёплого свечения по углам
    val garland: Boolean = false,
    val drifts: Boolean = false,
    val trees: Boolean = false,
    val hills: Boolean = false,
    val hillFarTop: Int = 0,
    val hillFarBottom: Int = 0,
    val hillNearTop: Int = 0,
    val hillNearBottom: Int = 0,
    val treeBody: Int = 0,
    val treeSnow: Int = Color.WHITE
) {
    val textIsLight: Boolean
        get() = (Color.red(text) + Color.green(text) + Color.blue(text)) / 3 > 128
}

object Themes {
    val all: List<AtmosphereTheme> = listOf(
        // 1. Новый год
        AtmosphereTheme(
            id = "christmas",
            title = "Новый год",
            subtitle = "Снег, гирлянды, тёплый свет",
            gradient = intArrayOf(col(0xFF08121F), col(0xFF10263D), col(0xFF1B3550)),
            text = col(0xFFFFF3E0),
            textDim = col(0xBFFFE6C2),
            card = col(0x26FFFFFF),
            cardFocus = col(0x4DFFFFFF),
            accent = col(0xFFFFC46B),
            scrim = col(0x80050A12),
            snow = true,
            snowAmount = 1f,
            garland = true,
            drifts = true,
            trees = true,
            lamp = 0.22f,
            hills = true,
            hillFarTop = col(0xFF223F5E),
            hillFarBottom = col(0xFF152A42),
            hillNearTop = col(0xFF2C4C70),
            hillNearBottom = col(0xFF16304A),
            treeBody = col(0xFF0C1B2B),
            treeSnow = col(0xFFDDE8F3)
        ),
        // 2. Дождливый день
        AtmosphereTheme(
            id = "rainy",
            title = "Дождливый день",
            subtitle = "Дождь и капли на стекле",
            gradient = intArrayOf(col(0xFF1B2733), col(0xFF2A3E50), col(0xFF3A5468)),
            text = col(0xFFE8F0F5),
            textDim = col(0xB3D5E3EE),
            card = col(0x26FFFFFF),
            cardFocus = col(0x4DFFFFFF),
            accent = col(0xFF8FC7F0),
            scrim = col(0x66000000),
            rain = true,
            glass = true,
            clouds = true,
            cloudColor = col(0xFF8A9DAD),
            cloudAlpha = 0.22f
        ),
        // 3. Уютный вечер
        AtmosphereTheme(
            id = "cozy",
            title = "Уютный вечер",
            subtitle = "Тёплые кремовые тона",
            gradient = intArrayOf(col(0xFFF6E7D0), col(0xFFF0CFA4), col(0xFFDF9A62)),
            text = col(0xFF3A2314),
            textDim = col(0xB33A2314),
            card = col(0x59FFFFFF),
            cardFocus = col(0xD9FFFFFF),
            accent = col(0xFFB5481F),
            scrim = col(0x00000000),
            bokeh = true,
            lamp = 0.45f
        ),
        // 4. Зима
        AtmosphereTheme(
            id = "winter",
            title = "Зима",
            subtitle = "Холодные голубые тона и лёгкий снег",
            gradient = intArrayOf(col(0xFF2E5C86), col(0xFF5B8DB6), col(0xFF9DC4DE)),
            text = col(0xFFFFFFFF),
            textDim = col(0xCCFFFFFF),
            card = col(0x33FFFFFF),
            cardFocus = col(0x66FFFFFF),
            accent = col(0xFFFFFFFF),
            scrim = col(0x66143049),
            snow = true,
            snowAmount = 0.65f,
            drifts = true,
            trees = true,
            hills = true,
            hillFarTop = col(0xFF9CC3DE),
            hillFarBottom = col(0xFF7EA9C9),
            hillNearTop = col(0xFFC4DDEE),
            hillNearBottom = col(0xFF9CC3DE),
            treeBody = col(0xFF2B5A7E),
            treeSnow = col(0xFFF2F8FC)
        ),
        // 5. Облачно
        AtmosphereTheme(
            id = "cloudy",
            title = "Облачно",
            subtitle = "Спокойное серо-голубое небо",
            gradient = intArrayOf(col(0xFF465C72), col(0xFF6B8399), col(0xFF8FA5B8)),
            text = col(0xFFFFFFFF),
            textDim = col(0xCCFFFFFF),
            card = col(0x2EFFFFFF),
            cardFocus = col(0x59FFFFFF),
            accent = col(0xFFFFFFFF),
            scrim = col(0x59000000),
            clouds = true,
            cloudColor = col(0xFFFFFFFF),
            cloudAlpha = 0.2f
        ),
        // 6. Ночь
        AtmosphereTheme(
            id = "night",
            title = "Ночь",
            subtitle = "Звёзды и лунный свет",
            gradient = intArrayOf(col(0xFF03081A), col(0xFF0A1838), col(0xFF132A58)),
            text = col(0xFFEAF0FF),
            textDim = col(0xB3C8D6FF),
            card = col(0x1FFFFFFF),
            cardFocus = col(0x47FFFFFF),
            accent = col(0xFFB8C8FF),
            scrim = col(0x66000000),
            stars = true,
            moon = true
        ),
        // 7. Светлая
        AtmosphereTheme(
            id = "light",
            title = "Светлая",
            subtitle = "Минимализм, молочный фон",
            gradient = intArrayOf(col(0xFFFBF8F3), col(0xFFF4EFE7)),
            text = col(0xFF1D1D1F),
            textDim = col(0x991D1D1F),
            card = col(0xFFFFFFFF),
            cardFocus = col(0xFFFFFFFF),
            accent = col(0xFF2F6FED),
            scrim = col(0x00000000)
        ),
        // 8. Тёмная
        AtmosphereTheme(
            id = "dark",
            title = "Тёмная",
            subtitle = "Графит и контрастные акценты",
            gradient = intArrayOf(col(0xFF0A0A0C), col(0xFF16161A)),
            text = col(0xFFFFFFFF),
            textDim = col(0x99FFFFFF),
            card = col(0x1AFFFFFF),
            cardFocus = col(0x40FFFFFF),
            accent = col(0xFF5AA9FF),
            scrim = col(0x33000000)
        )
    )

    fun byId(id: String): AtmosphereTheme = all.firstOrNull { it.id == id } ?: all[0]

    fun next(id: String): AtmosphereTheme {
        val i = all.indexOfFirst { it.id == id }
        return all[(i + 1).mod(all.size)]
    }
}
