package com.atmosphere.tvhome

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Экран «Атмосфера»: выбор темы с предпросмотром и настройки эффектов.
 * Управляется только стрелками, OK и «Назад».
 */
class AtmospherePanel(
    ctx: Context,
    private val k: Float,
    private val prefs: Prefs,
    private val onChange: () -> Unit
) : FrameLayout(ctx) {

    private val cards = ArrayList<FrameLayout>()
    private val checks = ArrayList<TextView>()

    val isOpen: Boolean
        get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(col(0xF2070B13))
        buildContent()
    }

    private fun px(v: Float): Int = (v * k).toInt()

    private fun buildContent() {
        val scroll = ScrollView(context)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(px(60f), px(34f), px(60f), px(24f))
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(context)
        title.text = "Атмосфера"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 34f * k)
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        column.addView(title)

        val sub = TextView(context)
        sub.text = "Выберите настроение интерфейса. Стрелки — выбор, OK — применить, Назад — закрыть."
        sub.setTextColor(col(0x99FFFFFF))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16f * k)
        column.addView(sub)

        // --- ряд с темами ---
        val hs = HorizontalScrollView(context)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipToPadding = false
        hs.clipChildren = false
        hs.setPadding(0, px(14f), 0, px(14f))
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        hs.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(hs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        for (t in Themes.all) {
            val card = makeThemeCard(t)
            val lp = LinearLayout.LayoutParams(px(210f), px(106f))
            lp.rightMargin = px(16f)
            row.addView(card, lp)
        }

        // --- настройки ---
        addRow(column, "Интенсивность анимаций", listOf("Выключено", "Низкая", "Средняя", "Высокая"), prefs.intensity) {
            prefs.intensity = it
            onChange()
        }
        addRow(column, "Снежинки", listOf("Выкл", "Вкл"), if (prefs.snowOn) 1 else 0) {
            prefs.snowOn = it == 1
            onChange()
        }
        addRow(column, "Дождь и капли на стекле", listOf("Выкл", "Вкл"), if (prefs.rainOn) 1 else 0) {
            prefs.rainOn = it == 1
            onChange()
        }
        addRow(column, "Декор: гирлянды, сугробы, ёлки", listOf("Выкл", "Вкл"), if (prefs.decorOn) 1 else 0) {
            prefs.decorOn = it == 1
            onChange()
        }
        addRow(column, "Яркость свечения", listOf("Выкл", "Мягкое", "Среднее", "Яркое"), prefs.glow) {
            prefs.glow = it
            onChange()
        }
        refreshSelection()
    }

    private fun makeThemeCard(t: AtmosphereTheme): FrameLayout {
        val card = FrameLayout(context)
        card.isFocusable = true
        card.isClickable = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f * k)
            }
        }
        card.clipToOutline = true

        val pv = AtmosphereView(context, true)
        pv.configure(t, 2, true, true, true, 2, false)
        card.addView(pv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val label = TextView(context)
        label.text = t.title
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16f * k)
        label.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        label.setPadding(px(12f), px(16f), px(12f), px(8f))
        label.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(col(0x00000000), col(0xB3000000))
        )
        card.addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        val check = TextView(context)
        check.text = "✓"
        check.setTextColor(Color.BLACK)
        check.gravity = Gravity.CENTER
        check.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f * k)
        check.typeface = Typeface.DEFAULT_BOLD
        check.background = UiKit.rounded(t.accent, 100f * k)
        val clp = FrameLayout.LayoutParams(px(24f), px(24f), Gravity.TOP or Gravity.END)
        clp.topMargin = px(8f)
        clp.rightMargin = px(8f)
        card.addView(check, clp)
        checks.add(check)

        val ring = UiKit.rounded(Color.TRANSPARENT, 16f * k, col(0xFFFFC46B), px(4f))
        card.setOnFocusChangeListener { v, has ->
            v.foreground = if (has) ring else null
            val sc = if (has) 1.06f else 1f
            v.animate().scaleX(sc).scaleY(sc).setDuration(160L).start()
        }
        card.setOnClickListener {
            prefs.themeId = t.id
            refreshSelection()
            onChange()
        }
        cards.add(card)
        return card
    }

    private fun addRow(parent: LinearLayout, label: String, options: List<String>, index: Int, onChanged: (Int) -> Unit) {
        val r = OptionRow(context, k, label, options, index, onChanged)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(42f))
        lp.topMargin = px(6f)
        parent.addView(r, lp)
    }

    private fun refreshSelection() {
        val cur = prefs.themeId
        for (i in Themes.all.indices) {
            checks[i].visibility = if (Themes.all[i].id == cur) View.VISIBLE else View.GONE
        }
    }

    fun show() {
        refreshSelection()
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(180L).start()
        post {
            val idx = Themes.all.indexOfFirst { it.id == prefs.themeId }.coerceAtLeast(0)
            cards[idx].requestFocus()
        }
    }

    fun hide() {
        animate().cancel()
        alpha = 1f
        visibility = View.GONE
    }

    /** Строка настройки: влево/вправо — изменить значение, OK — следующее значение. */
    private class OptionRow(
        ctx: Context,
        private val k: Float,
        label: String,
        private val options: List<String>,
        private var index: Int,
        private val onChanged: (Int) -> Unit
    ) : LinearLayout(ctx) {

        private val valueView: TextView
        private val normal = UiKit.rounded(col(0x14FFFFFF), 12f * k)
        private val focused = UiKit.rounded(col(0x33FFFFFF), 12f * k, col(0xFFFFC46B), (3f * k).toInt().coerceAtLeast(2))

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding((18f * k).toInt(), 0, (18f * k).toInt(), 0)
            background = normal

            val l = TextView(ctx)
            l.text = label
            l.setTextColor(Color.WHITE)
            l.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * k)
            addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            valueView = TextView(ctx)
            valueView.setTextColor(col(0xFFFFC46B))
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * k)
            valueView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            refreshValue(false)

            setOnFocusChangeListener { _, has ->
                background = if (has) focused else normal
                refreshValue(has)
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            step(-1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            step(1)
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        private fun step(d: Int) {
            index = (index + d).mod(options.size)
            refreshValue(true)
            onChanged(index)
        }

        private fun refreshValue(withArrows: Boolean) {
            valueView.text = if (withArrows) "◀  " + options[index] + "  ▶" else options[index]
        }
    }
}
