package com.atmosphere.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val BULB_COLORS = intArrayOf(
    col(0xFFFFC46B), col(0xFFFF6F59), col(0xFFFFE8B0), col(0xFF86D3A8), col(0xFF9CCBFF)
)
private val BOKEH_COLORS = intArrayOf(col(0xFFFFD9A0), col(0xFFFFB86B), col(0xFFFFF0D0))
private val GLOW_LEVELS = floatArrayOf(0f, 0.5f, 0.85f, 1.2f)

private const val SNOW_MAX = 260
private const val RAIN_MAX = 220
private const val GLASS_MAX = 40
private const val STAR_MAX = 200
private const val CLOUD_MAX = 10
private const val BOKEH_MAX = 30
private const val BULB_MAX = 48
private const val TWO_PI = 6.2831855f
private const val RAIN_SLANT = -0.12f

private fun makeSoftDot(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(
        size / 2f, size / 2f, size / 2f,
        intArrayOf(col(0xFFFFFFFF), col(0xE6FFFFFF), col(0x00FFFFFF)),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP
    )
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    return bmp
}

/**
 * Рисует фон выбранной «Атмосферы».
 * Статичные детали (градиент, холмы, ёлки, сугробы, провод гирлянды) рисуются один раз в Bitmap,
 * а в каждом кадре рисуются только движущиеся частицы — так нагрузка на ТВ минимальна.
 */
class AtmosphereView(context: Context, private val preview: Boolean = false) : View(context) {

    private var atmo: AtmosphereTheme = Themes.all[0]
    private var intensity = 2
    private var snowOn = true
    private var rainOn = true
    private var decorOn = true
    private var glow = 2

    private val rnd = Random(System.nanoTime())
    private var s = 1f
    private var time = 0f
    private var lastTime = 0L
    private var frameMs = 33L

    private var layer: Bitmap? = null
    private var prevLayer: Bitmap? = null
    private var transition = 1f
    private var transStart = 0L

    private val dst = RectF()
    private val tmp = RectF()
    private val path = Path()
    private val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val soft: Bitmap = makeSoftDot()
    private val softPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cloudPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val warmPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(col(0xFFFFA94D), PorterDuff.Mode.SRC_IN)
    }
    private val haloPaints = Array(BULB_COLORS.size) { i ->
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(BULB_COLORS[i], PorterDuff.Mode.SRC_IN)
        }
    }
    private val bokehPaints = Array(BOKEH_COLORS.size) { i ->
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(BOKEH_COLORS[i], PorterDuff.Mode.SRC_IN)
        }
    }
    private val rainFar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = col(0x59C8DCEB)
    }
    private val rainNearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = col(0x8CDCEAF5)
    }

    // --- снег ---
    private var snowN = 0
    private val sx = FloatArray(SNOW_MAX)
    private val sy = FloatArray(SNOW_MAX)
    private val sr = FloatArray(SNOW_MAX)
    private val sv = FloatArray(SNOW_MAX)
    private val sa = FloatArray(SNOW_MAX)
    private val sph = FloatArray(SNOW_MAX)
    private val sfq = FloatArray(SNOW_MAX)
    private val samp = FloatArray(SNOW_MAX)
    private val sd = FloatArray(SNOW_MAX)
    private val stype = IntArray(SNOW_MAX)

    // --- дождь ---
    private var rainN = 0
    private val rx = FloatArray(RAIN_MAX)
    private val ry = FloatArray(RAIN_MAX)
    private val rl = FloatArray(RAIN_MAX)
    private val rv = FloatArray(RAIN_MAX)
    private val rnearFlag = BooleanArray(RAIN_MAX)
    private val rainPtsFar = FloatArray(RAIN_MAX * 4)
    private val rainPtsNear = FloatArray(RAIN_MAX * 4)

    // --- капли на стекле ---
    private var glassN = 0
    private val gx = FloatArray(GLASS_MAX)
    private val gy = FloatArray(GLASS_MAX)
    private val gr = FloatArray(GLASS_MAX)
    private val gv = FloatArray(GLASS_MAX)
    private val gt = FloatArray(GLASS_MAX)

    // --- звёзды ---
    private var starN = 0
    private val stx = FloatArray(STAR_MAX)
    private val sty = FloatArray(STAR_MAX)
    private val sta = FloatArray(STAR_MAX)
    private val stf = FloatArray(STAR_MAX)
    private val stp = FloatArray(STAR_MAX)
    private val sts = FloatArray(STAR_MAX)

    // --- облака ---
    private var cloudN = 0
    private val clx = FloatArray(CLOUD_MAX)
    private val cly = FloatArray(CLOUD_MAX)
    private val clw = FloatArray(CLOUD_MAX)
    private val clv = FloatArray(CLOUD_MAX)
    private val cla = FloatArray(CLOUD_MAX)

    // --- тёплые огоньки ---
    private var bokehN = 0
    private val bkx = FloatArray(BOKEH_MAX)
    private val bky = FloatArray(BOKEH_MAX)
    private val bkr = FloatArray(BOKEH_MAX)
    private val bkv = FloatArray(BOKEH_MAX)
    private val bkp = FloatArray(BOKEH_MAX)
    private val bka = FloatArray(BOKEH_MAX)
    private val bkc = IntArray(BOKEH_MAX)

    // --- лампочки гирлянды ---
    private var bulbN = 0
    private val bulbX = FloatArray(BULB_MAX)
    private val bulbY = FloatArray(BULB_MAX)
    private val bulbC = IntArray(BULB_MAX)

    private val ticker = Runnable { invalidate() }

    // ------------------------------------------------------------------ настройка

    fun configure(
        t: AtmosphereTheme,
        intensityLevel: Int,
        snow: Boolean,
        rain: Boolean,
        decor: Boolean,
        glowLevel: Int,
        animateTransition: Boolean
    ) {
        val themeChanged = t.id != atmo.id
        val layerChanged = themeChanged || decor != decorOn
        atmo = t
        intensity = intensityLevel.coerceIn(0, 3)
        snowOn = snow
        rainOn = rain
        decorOn = decor
        glow = glowLevel.coerceIn(0, 3)
        frameMs = when (intensity) {
            1 -> 50L
            2 -> 33L
            else -> 25L
        }
        cloudPaint.colorFilter = PorterDuffColorFilter(t.cloudColor, PorterDuff.Mode.SRC_IN)
        if (width > 0 && height > 0) {
            if (layerChanged) {
                val old = layer
                layer = buildLayer()
                if (themeChanged && animateTransition && !preview && old != null) {
                    prevLayer?.recycle()
                    prevLayer = old
                    transition = 0f
                    transStart = SystemClock.uptimeMillis()
                } else {
                    old?.recycle()
                }
            }
            rebuildParticles()
        }
        lastTime = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layer?.recycle()
        prevLayer?.recycle()
        prevLayer = null
        transition = 1f
        if (w > 0 && h > 0) {
            s = if (preview) 0.5f else w / 1920f
            layer = buildLayer()
            rebuildParticles()
        } else {
            layer = null
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastTime = SystemClock.uptimeMillis()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            lastTime = SystemClock.uptimeMillis()
            invalidate()
        } else {
            removeCallbacks(ticker)
        }
    }

    // ------------------------------------------------------------------ статичный слой

    private fun buildLayer(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val ls = min(1f, 1920f / width)
        val lw = max(1, (width * ls).toInt())
        val lh = max(1, (height * ls).toInt())
        val bmp = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(ls, ls)
        drawStatic(c, width.toFloat(), height.toFloat())
        return bmp
    }

    private fun drawStatic(c: Canvas, w: Float, h: Float) {
        val g = atmo.gradient
        val pos = FloatArray(g.size) { it / (g.size - 1f) }
        fill.shader = LinearGradient(0f, 0f, 0f, h, g, pos, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        if (atmo.moon) drawMoon(c, w, h)

        if (atmo.hills) {
            drawHills(c, w, h, h * 0.70f, h * 0.030f, 0.8f, atmo.hillFarTop, atmo.hillFarBottom)
            if (atmo.trees && decorOn) drawTreeRow(c, w, h)
            drawHills(c, w, h, h * 0.80f, h * 0.035f, 2.6f, atmo.hillNearTop, atmo.hillNearBottom)
        }
        if (atmo.drifts && decorOn) {
            drawHills(c, w, h, h * 0.935f, h * 0.018f, 1.3f, col(0xFFC5D5E8), col(0xFFA3B8D2))
            drawHills(c, w, h, h * 0.965f, h * 0.014f, 3.9f, col(0xFFF4F8FC), col(0xFFD8E4F0))
        }
        if (atmo.garland && decorOn) {
            drawGarlandWire(c, w, h)
        } else {
            bulbN = 0
        }
    }

    private fun radialGlow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.shader = RadialGradient(cx, cy, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
    }

    private fun drawMoon(c: Canvas, w: Float, h: Float) {
        val cx = w * 0.60f
        val cy = h * 0.19f
        val r = h * 0.055f
        radialGlow(c, cx, cy, r * 5.5f, col(0x40CFE0FF))
        radialGlow(c, cx, cy, r * 2.4f, col(0x66E8F0FF))
        fill.color = col(0xFFF6F3E6)
        c.drawCircle(cx, cy, r, fill)
        fill.color = col(0x1A8C93A8)
        c.drawCircle(cx - r * 0.30f, cy - r * 0.20f, r * 0.22f, fill)
        c.drawCircle(cx + r * 0.35f, cy + r * 0.25f, r * 0.16f, fill)
        c.drawCircle(cx + r * 0.10f, cy - r * 0.45f, r * 0.10f, fill)
    }

    private fun hillY(x: Float, w: Float, base: Float, amp: Float, ph: Float): Float {
        val u = x / w * TWO_PI
        return base + amp * (sin(u * 1.1f + ph) * 0.6f + sin(u * 2.3f + ph * 1.7f) * 0.4f)
    }

    private fun drawHills(
        c: Canvas, w: Float, h: Float, base: Float, amp: Float, ph: Float, top: Int, bottom: Int
    ) {
        path.reset()
        path.moveTo(0f, h)
        val n = 96
        for (i in 0..n) {
            val x = w * i / n
            path.lineTo(x, hillY(x, w, base, amp, ph))
        }
        path.lineTo(w, h)
        path.close()
        fill.shader = LinearGradient(0f, base - amp, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
    }

    private fun drawTreeRow(c: Canvas, w: Float, h: Float) {
        for (i in 0 until 14) {
            val x = w * (0.03f + i * 0.072f) + sin(i * 7.3f) * w * 0.015f
            val hgt = h * (0.06f + 0.05f * (0.5f + 0.5f * sin(i * 3.1f)))
            val base = hillY(x, w, h * 0.70f, h * 0.030f, 0.8f) + h * 0.012f
            drawPine(c, x, base, hgt)
        }
    }

    private fun drawPine(c: Canvas, cx: Float, baseY: Float, hgt: Float) {
        fill.color = atmo.treeBody
        c.drawRect(cx - hgt * 0.03f, baseY - hgt * 0.10f, cx + hgt * 0.03f, baseY, fill)
        for (i in 0 until 4) {
            val bottomY = baseY - hgt * 0.07f - hgt * 0.20f * i
            val topY = bottomY - hgt * 0.34f
            val hw = hgt * 0.20f * (1f - 0.18f * i)
            path.reset()
            path.moveTo(cx, topY)
            path.lineTo(cx - hw, bottomY)
            path.lineTo(cx + hw, bottomY)
            path.close()
            fill.color = atmo.treeBody
            c.drawPath(path, fill)
            val midY = topY + (bottomY - topY) * 0.5f
            path.reset()
            path.moveTo(cx, topY)
            path.lineTo(cx - hw * 0.5f, midY)
            path.quadTo(cx, midY + hgt * 0.02f, cx + hw * 0.5f, midY)
            path.close()
            fill.color = atmo.treeSnow
            c.drawPath(path, fill)
        }
    }

    private fun drawGarlandWire(c: Canvas, w: Float, h: Float) {
        val swags = 5
        val perSwag = 6
        val y0 = h * 0.010f
        val sag = h * 0.050f
        var idx = 0
        stroke.color = col(0xFF0E1714)
        stroke.strokeWidth = max(1f, 2.4f * s)
        for (k in 0 until swags) {
            val x0 = w * k / swags
            val x1 = w * (k + 1) / swags
            val cx = (x0 + x1) / 2f
            val cy = y0 + sag * 2f
            path.reset()
            path.moveTo(x0, y0)
            path.quadTo(cx, cy, x1, y0)
            c.drawPath(path, stroke)
            for (j in 0 until perSwag) {
                val t = (j + 0.5f) / perSwag
                val mt = 1f - t
                val bx = mt * mt * x0 + 2f * mt * t * cx + t * t * x1
                val by = mt * mt * y0 + 2f * mt * t * cy + t * t * y0
                fill.color = col(0xFF16201C)
                c.drawRect(bx - 2.5f * s, by - 1f * s, bx + 2.5f * s, by + 7f * s, fill)
                if (idx < BULB_MAX) {
                    bulbX[idx] = bx
                    bulbY[idx] = by + 7f * s + 9f * s
                    bulbC[idx] = (idx * 3 + idx / perSwag) % BULB_COLORS.size
                    idx++
                }
            }
        }
        bulbN = idx
        fill.color = col(0xFF2B3A33)
        for (k in 0..swags) {
            c.drawCircle(w * k / swags, y0, 4f * s, fill)
        }
    }

    // ------------------------------------------------------------------ частицы

    private fun rr(a: Float, b: Float): Float = a + (b - a) * rnd.nextFloat()

    private fun rebuildParticles() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val f = if (preview) 0.6f else when (intensity) {
            1 -> 0.35f
            2 -> 0.7f
            else -> 1f
        }

        snowN = if (snowOn && atmo.snow) min(SNOW_MAX, (atmo.snowAmount * 170f * f).toInt()) else 0
        for (i in 0 until snowN) initFlake(i, true)

        rainN = if (rainOn && atmo.rain) min(RAIN_MAX, (150f * f).toInt()) else 0
        for (i in 0 until rainN) initDrop(i, true)
        rainFar.strokeWidth = max(1f, 1.4f * s)
        rainNearPaint.strokeWidth = max(1f, 2.1f * s)

        glassN = if (rainOn && atmo.glass) min(GLASS_MAX, max(10, (30f * f).toInt())) else 0
        for (i in 0 until glassN) initGlass(i, true)

        starN = if (atmo.stars) (if (preview) 70 else 150) else 0
        for (i in 0 until starN) {
            stx[i] = rnd.nextFloat() * w
            sty[i] = rnd.nextFloat() * h * 0.72f
            sta[i] = rr(0.35f, 1f)
            stf[i] = rr(0.6f, 2.8f)
            stp[i] = rnd.nextFloat() * TWO_PI
            sts[i] = max(0.6f, (0.8f + 1.8f * rnd.nextFloat() * rnd.nextFloat()) * s * 1.1f)
        }

        cloudN = if (atmo.clouds) (if (preview) 5 else 7) else 0
        for (i in 0 until cloudN) {
            clx[i] = rr(-0.1f * w, 1.1f * w)
            cly[i] = h * rr(0.05f, 0.55f)
            clw[i] = w * rr(0.22f, 0.47f)
            clv[i] = rr(6f, 20f) * s
            cla[i] = min(0.4f, atmo.cloudAlpha * rr(0.6f, 1.4f))
        }

        bokehN = if (atmo.bokeh) (if (preview) 12 else 22) else 0
        for (i in 0 until bokehN) initBokeh(i, true)
    }

    private fun initFlake(i: Int, anywhere: Boolean) {
        val d = rnd.nextFloat()
        sd[i] = d
        sr[i] = max(0.8f, (1.4f + d * d * 9f) * s)
        sv[i] = (38f + d * 95f) * s
        sa[i] = if (d > 0.85f) 0.45f else 0.30f + 0.50f * d
        sph[i] = rnd.nextFloat() * TWO_PI
        sfq[i] = rr(0.35f, 1.25f)
        samp[i] = rr(10f, 36f) * s
        stype[i] = if (d > 0.7f && rnd.nextFloat() < 0.4f) 1 else 0
        sx[i] = rr(-0.1f * width, 1.1f * width)
        sy[i] = if (anywhere) rnd.nextFloat() * height else -sr[i] * 3f
    }

    private fun initDrop(i: Int, anywhere: Boolean) {
        val near = rnd.nextFloat() < 0.4f
        rnearFlag[i] = near
        rl[i] = rr(26f, 66f) * s * (if (near) 1.4f else 1f)
        rv[i] = rr(900f, 1600f) * s * (if (near) 1.2f else 1f)
        rx[i] = rr(0f, 1.2f * width)
        ry[i] = if (anywhere) rnd.nextFloat() * height else -rl[i]
    }

    private fun initGlass(i: Int, anywhere: Boolean) {
        gx[i] = rnd.nextFloat() * width
        gy[i] = if (anywhere) rnd.nextFloat() * height else -20f * s
        gr[i] = rr(7f, 21f) * s * (if (preview) 1.4f else 1f)
        gv[i] = 0f
        gt[i] = rr(0.5f, 5.5f)
    }

    private fun initBokeh(i: Int, anywhere: Boolean) {
        bkx[i] = rnd.nextFloat() * width
        bky[i] = if (anywhere) rnd.nextFloat() * height else height + 80f * s
        bkr[i] = rr(14f, 60f) * s
        bkv[i] = rr(8f, 26f) * s
        bkp[i] = rnd.nextFloat() * TWO_PI
        bka[i] = rr(0.10f, 0.28f)
        bkc[i] = rnd.nextInt(BOKEH_COLORS.size)
    }

    private fun updateParticles(dt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()

        val wind = (sin(time * 0.11f) * 14f + 10f) * s
        for (i in 0 until snowN) {
            sy[i] += sv[i] * dt
            sx[i] += (wind * (0.3f + sd[i]) + cos(time * sfq[i] + sph[i]) * samp[i]) * dt
            val m = sr[i] * 3f
            if (sy[i] > h + m) {
                initFlake(i, false)
            } else if (sx[i] > w + m) {
                sx[i] = -m
            } else if (sx[i] < -m * 2f) {
                sx[i] = w + m
            }
        }

        for (i in 0 until rainN) {
            ry[i] += rv[i] * dt
            rx[i] += rv[i] * RAIN_SLANT * dt
            if (ry[i] - rl[i] > h) {
                ry[i] = -rl[i]
                rx[i] = rr(0f, 1.2f * w)
            }
            if (rx[i] < -0.2f * w) rx[i] += 1.4f * w
        }

        for (i in 0 until glassN) {
            gt[i] -= dt
            if (gt[i] <= 0f) {
                if (gv[i] == 0f) {
                    gv[i] = rr(40f, 130f) * s
                    gt[i] = rr(0.6f, 2.2f)
                } else {
                    gv[i] = 0f
                    gt[i] = rr(2f, 7f)
                }
            }
            gy[i] += gv[i] * dt
            if (gy[i] > h + gr[i] * 2f) initGlass(i, false)
        }

        for (i in 0 until cloudN) {
            clx[i] += clv[i] * dt
            if (clx[i] - clw[i] > w) clx[i] = -clw[i]
        }

        for (i in 0 until bokehN) {
            bky[i] -= bkv[i] * dt
            bkx[i] += sin(time * 0.5f + bkp[i]) * 6f * s * dt
            if (bky[i] < -bkr[i] * 2f) initBokeh(i, false)
        }
    }

    // ------------------------------------------------------------------ отрисовка

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        var dt = (now - lastTime) / 1000f
        lastTime = now
        if (dt < 0f) dt = 0f
        if (dt > 0.1f) dt = 0.1f

        val animating = !preview && intensity > 0
        if (animating) {
            time += dt
            updateParticles(dt)
        }
        if (transition < 1f) {
            transition = min(1f, (now - transStart) / 700f)
        }

        dst.set(0f, 0f, w, h)
        val cur = layer
        val prev = prevLayer
        if (cur != null) {
            if (transition < 1f && prev != null) {
                layerPaint.alpha = 255
                canvas.drawBitmap(prev, null, dst, layerPaint)
                layerPaint.alpha = (transition * 255f).toInt()
                canvas.drawBitmap(cur, null, dst, layerPaint)
                layerPaint.alpha = 255
            } else {
                layerPaint.alpha = 255
                canvas.drawBitmap(cur, null, dst, layerPaint)
            }
        }
        if (transition >= 1f && prev != null) {
            prev.recycle()
            prevLayer = null
        }

        var saved = -1
        if (transition < 1f) {
            saved = canvas.saveLayerAlpha(0f, 0f, w, h, (transition * 255f).toInt())
        }
        drawDynamic(canvas, animating)
        if (saved >= 0) canvas.restoreToCount(saved)

        scheduleNext()
    }

    private fun scheduleNext() {
        removeCallbacks(ticker)
        if (preview) return
        if (intensity > 0) {
            postDelayed(ticker, frameMs)
        } else if (transition < 1f) {
            postDelayed(ticker, 33L)
        }
    }

    private fun drawDynamic(canvas: Canvas, animating: Boolean) {
        val full = animating || preview
        if (atmo.stars) drawStars(canvas)
        if (full && atmo.clouds) drawClouds(canvas)
        if (atmo.lamp > 0f && glow > 0) drawLamp(canvas)
        if (full && atmo.bokeh) drawBokeh(canvas)
        if (full && snowN > 0) drawSnow(canvas)
        if (full && rainN > 0) drawRain(canvas)
        if (full && glassN > 0) drawGlass(canvas)
        if (atmo.garland && decorOn && bulbN > 0) drawBulbs(canvas, animating)
    }

    private fun drawStars(canvas: Canvas) {
        for (i in 0 until starN) {
            val a = sta[i] * (0.55f + 0.45f * sin(time * stf[i] + stp[i]))
            fill.color = Color.WHITE
            fill.alpha = (a * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(stx[i], sty[i], sts[i], fill)
        }
    }

    private fun drawClouds(canvas: Canvas) {
        for (i in 0 until cloudN) {
            cloudPaint.alpha = (cla[i] * 255f).toInt()
            for (j in 0 until 5) {
                val ow = clw[i] * 0.42f * (0.8f + 0.2f * cos(j * 1.7f))
                val oh = ow * 0.55f
                val ox = clx[i] + clw[i] * (j - 2) * 0.22f
                val oy = cly[i] + sin(j * 2.3f) * oh * 0.2f
                tmp.set(ox - ow / 2f, oy - oh / 2f, ox + ow / 2f, oy + oh / 2f)
                canvas.drawBitmap(soft, null, tmp, cloudPaint)
            }
        }
    }

    private fun drawLamp(canvas: Canvas) {
        val gf = GLOW_LEVELS[glow]
        val w = width.toFloat()
        val h = height.toFloat()
        val pulse = 0.85f + 0.15f * sin(time * 0.9f)
        warmPaint.alpha = (255f * min(1f, atmo.lamp * gf * pulse)).toInt()
        tmp.set(-0.30f * w, 0.55f * h, 0.45f * w, 1.55f * h)
        canvas.drawBitmap(soft, null, tmp, warmPaint)
        val pulse2 = 0.85f + 0.15f * sin(time * 0.7f + 2f)
        warmPaint.alpha = (255f * min(1f, atmo.lamp * gf * pulse2)).toInt()
        tmp.set(0.55f * w, 0.55f * h, 1.30f * w, 1.55f * h)
        canvas.drawBitmap(soft, null, tmp, warmPaint)
    }

    private fun drawBokeh(canvas: Canvas) {
        val gf = GLOW_LEVELS[glow]
        for (i in 0 until bokehN) {
            val a = bka[i] * (0.7f + 0.3f * sin(time * 0.8f + bkp[i])) * (0.4f + 0.6f * gf)
            val p = bokehPaints[bkc[i]]
            p.alpha = (255f * min(1f, a)).toInt()
            val e = bkr[i] * 1.4f
            tmp.set(bkx[i] - e, bky[i] - e, bkx[i] + e, bky[i] + e)
            canvas.drawBitmap(soft, null, tmp, p)
        }
    }

    private fun drawSnow(canvas: Canvas) {
        for (i in 0 until snowN) {
            val r = sr[i]
            val a = (sa[i] * 255f).toInt()
            if (stype[i] == 1) {
                stroke.color = Color.WHITE
                stroke.alpha = (a * 0.85f).toInt()
                stroke.strokeWidth = max(1f, r * 0.2f)
                val l = r * 1.3f
                canvas.save()
                canvas.translate(sx[i], sy[i])
                canvas.rotate(time * 14f + sph[i] * 30f)
                canvas.drawLine(-l, 0f, l, 0f, stroke)
                canvas.rotate(60f)
                canvas.drawLine(-l, 0f, l, 0f, stroke)
                canvas.rotate(60f)
                canvas.drawLine(-l, 0f, l, 0f, stroke)
                canvas.restore()
                val e = r * 0.8f
                tmp.set(sx[i] - e, sy[i] - e, sx[i] + e, sy[i] + e)
                softPaint.alpha = a
                canvas.drawBitmap(soft, null, tmp, softPaint)
            } else {
                val e = r * 1.7f
                tmp.set(sx[i] - e, sy[i] - e, sx[i] + e, sy[i] + e)
                softPaint.alpha = a
                canvas.drawBitmap(soft, null, tmp, softPaint)
            }
        }
    }

    private fun drawRain(canvas: Canvas) {
        var nf = 0
        var nn = 0
        for (i in 0 until rainN) {
            val x2 = rx[i] + RAIN_SLANT * rl[i]
            val y2 = ry[i] + rl[i]
            if (rnearFlag[i]) {
                rainPtsNear[nn++] = rx[i]
                rainPtsNear[nn++] = ry[i]
                rainPtsNear[nn++] = x2
                rainPtsNear[nn++] = y2
            } else {
                rainPtsFar[nf++] = rx[i]
                rainPtsFar[nf++] = ry[i]
                rainPtsFar[nf++] = x2
                rainPtsFar[nf++] = y2
            }
        }
        canvas.drawLines(rainPtsFar, 0, nf, rainFar)
        canvas.drawLines(rainPtsNear, 0, nn, rainNearPaint)
    }

    private fun drawGlass(canvas: Canvas) {
        stroke.strokeWidth = max(1f, 1.2f * s)
        for (i in 0 until glassN) {
            val r = gr[i]
            val sliding = gv[i] > 0f
            val ryy = if (sliding) r * 1.35f else r * 1.05f
            if (sliding) {
                stroke.color = col(0x1FFFFFFF)
                stroke.strokeWidth = max(1f, r * 0.35f)
                canvas.drawLine(gx[i], gy[i] - ryy, gx[i], gy[i] - ryy - r * 3.5f, stroke)
                stroke.strokeWidth = max(1f, 1.2f * s)
            }
            tmp.set(gx[i] - r * 0.78f, gy[i] - ryy, gx[i] + r * 0.78f, gy[i] + ryy)
            fill.color = col(0x22FFFFFF)
            canvas.drawOval(tmp, fill)
            stroke.color = col(0x4DFFFFFF)
            canvas.drawOval(tmp, stroke)
            fill.color = col(0x99FFFFFF)
            canvas.drawCircle(gx[i] - r * 0.25f, gy[i] - ryy * 0.4f, max(1f, r * 0.17f), fill)
        }
    }

    private fun drawBulbs(canvas: Canvas, animating: Boolean) {
        val gf = GLOW_LEVELS[glow]
        for (i in 0 until bulbN) {
            val tw = if (animating) 0.80f + 0.20f * sin(time * (1.0f + 0.13f * (i % 5)) + i * 1.9f) else 0.92f
            val ci = bulbC[i]
            val x = bulbX[i]
            val y = bulbY[i]
            if (gf > 0f) {
                val hr = 30f * s * (0.7f + 0.3f * gf)
                tmp.set(x - hr, y - hr, x + hr, y + hr)
                val p = haloPaints[ci]
                p.alpha = (255f * min(1f, 0.55f * gf * tw)).toInt()
                canvas.drawBitmap(soft, null, tmp, p)
            }
            fill.color = BULB_COLORS[ci]
            fill.alpha = (255f * (0.78f + 0.22f * tw)).toInt()
            tmp.set(x - 6f * s, y - 9f * s, x + 6f * s, y + 9f * s)
            canvas.drawOval(tmp, fill)
            fill.color = Color.WHITE
            fill.alpha = (150f * tw).toInt()
            canvas.drawCircle(x - 1.5f * s, y - 3f * s, 2.2f * s, fill)
        }
    }
}
