package com.atmosphere.tvhome

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Главный экран (Launcher). Весь интерфейс строится кодом, управление — только пультом.
 */
@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())
    private val ioPool = Executors.newSingleThreadExecutor()
    private val netPool = Executors.newSingleThreadExecutor()

    /** Размер «условной единицы» интерфейса в пикселях (подстраивается под экран ТВ). */
    private var k = 1f

    private var atmo: AtmosphereTheme = Themes.all[0]
    private var apps: List<AppEntry> = emptyList()
    private var firstFocusDone = false

    private lateinit var root: FrameLayout
    private lateinit var atmosphere: AtmosphereView
    private lateinit var scrim: View
    private lateinit var content: LinearLayout
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var weatherBox: LinearLayout
    private lateinit var weatherMain: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var weatherCity: TextView
    private lateinit var favLabel: TextView
    private lateinit var favRow: RecyclerView
    private lateinit var appsLabel: TextView
    private lateinit var appsHint: TextView
    private lateinit var appsRow: RecyclerView
    private lateinit var favAdapter: AppAdapter
    private lateinit var appAdapter: AppAdapter
    private lateinit var panel: AtmospherePanel
    private val pills = ArrayList<TextView>()
    private var focusBeforePanel: View? = null
    private var lastClock = ""

    private fun px(v: Float): Int = (v * k).toInt()

    private fun sizeOf(tv: TextView, units: Float) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, units * k)
    }

    // ------------------------------------------------------------------ жизненный цикл

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val dm = resources.displayMetrics
        val heightUnits = dm.heightPixels / dm.density
        k = dm.density * (heightUnits / 540f).coerceIn(0.75f, 2.5f)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        buildUi()
        setContentView(root)

        atmo = Themes.byId(prefs.themeId)
        applyTheme(false)
        showCityHint()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        main.removeCallbacks(clockTask)
        main.post(clockTask)
        main.removeCallbacks(weatherTask)
        main.post(weatherTask)
        loadApps()
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(clockTask)
        main.removeCallbacks(weatherTask)
    }

    override fun onDestroy() {
        super.onDestroy()
        ioPool.shutdown()
        netPool.shutdown()
    }

    /** Нажатие Home, пока оболочка уже открыта: возвращаемся в начало. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (panel.isOpen) closePanel()
        favRow.scrollToPosition(0)
        appsRow.scrollToPosition(0)
        focusFirst()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (panel.isOpen) closePanel()
            return true // на главном экране «Назад» ничего не закрывает
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && !panel.isOpen) {
            val entry = currentFocus?.tag as? AppEntry
            if (entry != null) toggleFavorite(entry) else openPanel()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------ построение интерфейса

    private fun buildUi() {
        root = FrameLayout(this)

        atmosphere = AtmosphereView(this)
        root.addView(atmosphere, FrameLayout.LayoutParams(MATCH, MATCH))

        scrim = View(this)
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, px(300f), Gravity.BOTTOM))

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(px(60f), px(50f), px(60f), px(28f))
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- верхний ряд: часы слева, погода справа ---
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL

        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        clockView = TextView(this)
        sizeOf(clockView, 66f)
        clockView.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        clockView.includeFontPadding = false
        left.addView(clockView)
        dateView = TextView(this)
        sizeOf(dateView, 21f)
        dateView.includeFontPadding = false
        dateView.setPadding(px(4f), px(2f), 0, 0)
        left.addView(dateView)
        top.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))

        weatherBox = LinearLayout(this)
        weatherBox.orientation = LinearLayout.VERTICAL
        weatherBox.gravity = Gravity.END
        weatherBox.isFocusable = true
        weatherBox.isClickable = true
        weatherBox.setPadding(px(20f), px(10f), px(20f), px(10f))
        weatherBox.setOnClickListener { showCityDialog() }
        weatherMain = TextView(this)
        sizeOf(weatherMain, 36f)
        weatherMain.gravity = Gravity.END
        weatherMain.includeFontPadding = false
        weatherDesc = TextView(this)
        sizeOf(weatherDesc, 17f)
        weatherDesc.gravity = Gravity.END
        weatherCity = TextView(this)
        sizeOf(weatherCity, 15f)
        weatherCity.gravity = Gravity.END
        weatherBox.addView(weatherMain)
        weatherBox.addView(weatherDesc)
        weatherBox.addView(weatherCity)
        top.addView(weatherBox, LinearLayout.LayoutParams(WRAP, WRAP))
        content.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        // --- кнопки быстрого доступа ---
        val pillRow = LinearLayout(this)
        pillRow.orientation = LinearLayout.HORIZONTAL
        pillRow.clipChildren = false
        addPill(pillRow, "🎨  Атмосфера") { openPanel() }
        addPill(pillRow, "🔄  Сменить тему") { cycleTheme() }
        addPill(pillRow, "⚙  Настройки ТВ") { openSystemSettings() }
        addPill(pillRow, "📍  Город") { showCityDialog() }
        val plp = LinearLayout.LayoutParams(MATCH, WRAP)
        plp.topMargin = px(12f)
        content.addView(pillRow, plp)

        // --- гибкая пустота ---
        content.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))

        // --- избранное ---
        favLabel = TextView(this)
        favLabel.text = "Избранное"
        sizeOf(favLabel, 18f)
        favLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        favLabel.setPadding(px(6f), 0, 0, px(2f))
        content.addView(favLabel, LinearLayout.LayoutParams(WRAP, WRAP))

        favAdapter = AppAdapter(k, true, { launch(it) }, { toggleFavorite(it) })
        favRow = makeRow(favAdapter)
        content.addView(favRow, LinearLayout.LayoutParams(MATCH, WRAP))

        // --- все приложения ---
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.BOTTOM
        appsLabel = TextView(this)
        appsLabel.text = "Все приложения"
        sizeOf(appsLabel, 18f)
        appsLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        appsLabel.setPadding(px(6f), 0, 0, px(2f))
        header.addView(appsLabel, LinearLayout.LayoutParams(0, WRAP, 1f))
        appsHint = TextView(this)
        appsHint.text = "Удерживайте OK — добавить в избранное  ★"
        sizeOf(appsHint, 14f)
        header.addView(appsHint, LinearLayout.LayoutParams(WRAP, WRAP))
        val hlp = LinearLayout.LayoutParams(MATCH, WRAP)
        hlp.topMargin = px(8f)
        content.addView(header, hlp)

        appAdapter = AppAdapter(k, false, { launch(it) }, { toggleFavorite(it) })
        appsRow = makeRow(appAdapter)
        content.addView(appsRow, LinearLayout.LayoutParams(MATCH, WRAP))

        // --- панель «Атмосфера» поверх всего ---
        panel = AtmospherePanel(this, k, prefs) { onAtmosphereChanged() }
        root.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun makeRow(adapter: AppAdapter): RecyclerView {
        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = adapter
        rv.clipToPadding = false
        rv.clipChildren = false
        rv.setPadding(0, px(8f), 0, px(8f))
        rv.itemAnimator = null
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        return rv
    }

    private fun addPill(parent: LinearLayout, label: String, action: () -> Unit) {
        val tv = TextView(this)
        tv.text = label
        sizeOf(tv, 18f)
        tv.gravity = Gravity.CENTER
        tv.setPadding(px(22f), px(9f), px(22f), px(9f))
        tv.isFocusable = true
        tv.isClickable = true
        tv.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.rightMargin = px(14f)
        parent.addView(tv, lp)
        pills.add(tv)
    }

    // ------------------------------------------------------------------ темы

    private fun applyTheme(animate: Boolean) {
        val t = atmo
        val shadow = t.textIsLight

        fun tint(tv: TextView, color: Int) {
            tv.setTextColor(color)
            if (shadow) tv.setShadowLayer(6f * k, 0f, 2f * k, 0x73000000) else tv.setShadowLayer(0f, 0f, 0f, 0)
        }

        tint(clockView, t.text)
        tint(dateView, t.textDim)
        tint(weatherMain, t.text)
        tint(weatherDesc, t.text)
        tint(weatherCity, t.textDim)
        tint(favLabel, t.textDim)
        tint(appsLabel, t.textDim)
        tint(appsHint, t.textDim)
        for (p in pills) {
            tint(p, t.text)
            UiKit.style(p, t, k, 22f, 1.07f)
        }
        UiKit.style(weatherBox, t, k, 18f, 1.05f)

        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00000000, t.scrim)
        )

        favAdapter.atmo = t
        appAdapter.atmo = t

        atmosphere.configure(t, prefs.intensity, prefs.snowOn, prefs.rainOn, prefs.decorOn, prefs.glow, animate)
    }

    private fun onAtmosphereChanged() {
        atmo = Themes.byId(prefs.themeId)
        applyTheme(true)
    }

    private fun cycleTheme() {
        prefs.themeId = Themes.next(prefs.themeId).id
        onAtmosphereChanged()
        Toast.makeText(this, "Атмосфера: " + atmo.title, Toast.LENGTH_SHORT).show()
    }

    private fun openPanel() {
        focusBeforePanel = currentFocus
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        panel.show()
    }

    private fun closePanel() {
        panel.hide()
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        (focusBeforePanel ?: pills.firstOrNull())?.requestFocus()
    }

    private fun openSystemSettings() {
        try {
            val i = Intent(Settings.ACTION_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть настройки ТВ", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ приложения

    private fun loadApps() {
        ioPool.execute {
            val list = try {
                AppRepository.load(this)
            } catch (e: Exception) {
                emptyList()
            }
            main.post { onAppsLoaded(list) }
        }
    }

    private fun onAppsLoaded(list: List<AppEntry>) {
        if (isDestroyed) return
        val same = list.size == apps.size && list.map { it.pkg } == apps.map { it.pkg }
        if (same && firstFocusDone) return
        apps = list
        seedFavoritesIfNeeded()
        refreshLists()
        if (!firstFocusDone) {
            firstFocusDone = true
            root.postDelayed({ if (currentFocus == null) focusFirst() }, 200L)
        }
    }

    private fun seedFavoritesIfNeeded() {
        if (prefs.favSeeded || apps.isEmpty()) return
        val installed = apps.map { it.pkg }.toSet()
        val picks = AppRepository.KNOWN_FAVORITES.filter { installed.contains(it) }
        prefs.favorites = if (picks.isNotEmpty()) picks else apps.take(5).map { it.pkg }
        prefs.favSeeded = true
    }

    private fun refreshLists() {
        val byPkg = apps.associateBy { it.pkg }
        val favs = prefs.favorites.mapNotNull { byPkg[it] }
        appAdapter.favorites = favs.map { it.pkg }.toSet()
        favAdapter.items = favs
        appAdapter.items = apps
        val vis = if (favs.isEmpty()) View.GONE else View.VISIBLE
        favLabel.visibility = vis
        favRow.visibility = vis
    }

    private fun toggleFavorite(e: AppEntry) {
        val favs = prefs.favorites.toMutableList()
        val msg: String
        if (favs.contains(e.pkg)) {
            favs.remove(e.pkg)
            msg = "Убрано из избранного: " + e.label
        } else {
            favs.add(e.pkg)
            msg = "Добавлено в избранное: " + e.label
        }
        prefs.favorites = favs
        refreshLists()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        root.postDelayed({ if (currentFocus == null) focusFirst() }, 150L)
    }

    private fun launch(e: AppEntry) {
        try {
            val i = Intent(Intent.ACTION_MAIN)
            i.component = e.component
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(i)
        } catch (ex: Exception) {
            Toast.makeText(this, "Не удалось запустить: " + e.label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun focusFirst() {
        val target: View? = when {
            favRow.visibility == View.VISIBLE && favRow.childCount > 0 -> favRow.getChildAt(0)
            appsRow.childCount > 0 -> appsRow.getChildAt(0)
            else -> pills.firstOrNull()
        }
        target?.requestFocus()
    }

    // ------------------------------------------------------------------ часы

    private val clockTask = object : Runnable {
        override fun run() {
            updateClock()
            main.postDelayed(this, 1000L)
        }
    }

    private fun updateClock() {
        val now = Date()
        val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        val t = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        if (t == lastClock) return
        lastClock = t
        clockView.text = t
        val d = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        dateView.text = d.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    // ------------------------------------------------------------------ погода

    private val weatherTask = Runnable { fetchWeather() }

    private fun showCityHint() {
        if (prefs.hasCity) {
            weatherMain.text = "…"
            weatherDesc.text = "Загрузка погоды"
            weatherCity.text = prefs.cityName
        } else {
            weatherMain.text = "📍"
            weatherDesc.text = "Выберите город"
            weatherCity.text = "Нажмите OK"
        }
    }

    private fun fetchWeather() {
        if (!prefs.hasCity) {
            showCityHint()
            return
        }
        val lat = prefs.cityLat
        val lon = prefs.cityLon
        netPool.execute {
            try {
                val r = WeatherRepository.fetch(lat, lon)
                main.post {
                    showWeather(r)
                    main.removeCallbacks(weatherTask)
                    main.postDelayed(weatherTask, 30L * 60L * 1000L)
                }
            } catch (e: Exception) {
                main.post {
                    weatherMain.text = "—"
                    weatherDesc.text = "Нет связи"
                    weatherCity.text = prefs.cityName
                    main.removeCallbacks(weatherTask)
                    main.postDelayed(weatherTask, 60L * 1000L)
                }
            }
        }
    }

    private fun showWeather(r: WeatherResult) {
        val d = WeatherRepository.describe(r.code, r.isDay)
        val sign = if (r.temp > 0) "+" else ""
        weatherMain.text = d.first + "  " + sign + r.temp + "°"
        weatherDesc.text = d.second
        weatherCity.text = prefs.cityName
    }

    private fun showCityDialog() {
        val input = EditText(this)
        input.hint = "Например: Москва"
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        val wrap = FrameLayout(this)
        wrap.setPadding(px(20f), px(10f), px(20f), px(0f))
        wrap.addView(input, FrameLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle("Город для погоды")
            .setView(wrap)
            .setPositiveButton("Найти") { _, _ -> searchCity(input.text.toString().trim()) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun searchCity(query: String) {
        if (query.isEmpty()) return
        Toast.makeText(this, "Ищу город…", Toast.LENGTH_SHORT).show()
        netPool.execute {
            try {
                val found = WeatherRepository.search(query)
                main.post {
                    if (found.isEmpty()) {
                        Toast.makeText(this, "Город не найден", Toast.LENGTH_LONG).show()
                    } else {
                        val labels = found.map { it.label }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Выберите город")
                            .setItems(labels) { _, which ->
                                val c = found[which]
                                prefs.setCity(c.name, c.lat, c.lon)
                                showCityHint()
                                main.removeCallbacks(weatherTask)
                                main.post(weatherTask)
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                main.post { Toast.makeText(this, "Нет связи с интернетом", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
