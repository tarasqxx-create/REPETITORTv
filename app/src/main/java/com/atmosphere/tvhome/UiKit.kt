package com.atmosphere.tvhome

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

object UiKit {

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = radiusPx
        d.setColor(color)
        if (strokePx > 0) d.setStroke(strokePx, strokeColor)
        return d
    }

    /**
     * Единый стиль фокуса для пульта: плавное увеличение, подсветка фона и рамка акцентного цвета.
     */
    fun style(v: View, t: AtmosphereTheme, k: Float, radiusUnits: Float, scaleUp: Float) {
        val r = radiusUnits * k
        val normal = rounded(t.card, r)
        val focusBg = rounded(t.cardFocus, r)
        val ring = rounded(Color.TRANSPARENT, r, t.accent, (3f * k).toInt().coerceAtLeast(2))
        val apply: (Boolean) -> Unit = { focused ->
            v.background = if (focused) focusBg else normal
            v.foreground = if (focused) ring else null
            val sc = if (focused) scaleUp else 1f
            v.animate().scaleX(sc).scaleY(sc).setDuration(160L).start()
        }
        apply(v.hasFocus())
        v.setOnFocusChangeListener { _, has -> apply(has) }
    }
}
