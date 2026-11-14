package com.atmosphere.tvhome

import android.graphics.Outline
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Карточки приложений. large = крупные карточки избранного, иначе компактные значки.
 * k — размер «условной единицы» в пикселях (подстраивается под экран ТВ).
 */
class AppAdapter(
    private val k: Float,
    private val large: Boolean,
    private val onLaunch: (AppEntry) -> Unit,
    private val onFavorite: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    var items: List<AppEntry> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var favorites: Set<String> = emptySet()

    var atmo: AtmosphereTheme = Themes.all[0]
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VH(
        val card: FrameLayout,
        val banner: ImageView,
        val icon: ImageView,
        val label: TextView,
        val star: TextView
    ) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val w = if (large) (160f * k).toInt() else (100f * k).toInt()
        val h = (90f * k).toInt()

        val card = FrameLayout(ctx)
        val lp = RecyclerView.LayoutParams(w, h)
        lp.setMargins((8f * k).toInt(), 0, (8f * k).toInt(), 0)
        card.layoutParams = lp
        card.isFocusable = true
        card.isClickable = true
        card.isLongClickable = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 14f * k)
            }
        }
        card.clipToOutline = true

        val banner = ImageView(ctx)
        banner.scaleType = ImageView.ScaleType.CENTER_CROP
        card.addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val iconSize = if (large) (40f * k).toInt() else (46f * k).toInt()
        val icon = ImageView(ctx)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        val ilp = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        ilp.topMargin = (9f * k).toInt()
        card.addView(icon, ilp)

        val label = TextView(ctx)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14f * k)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        label.setPadding((6f * k).toInt(), 0, (6f * k).toInt(), 0)
        val llp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        llp.bottomMargin = (8f * k).toInt()
        card.addView(label, llp)

        val star = TextView(ctx)
        star.text = "★"
        star.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f * k)
        val slp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        slp.topMargin = (4f * k).toInt()
        slp.rightMargin = (8f * k).toInt()
        card.addView(star, slp)

        return VH(card, banner, icon, label, star)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val t = atmo
        holder.card.tag = e

        holder.label.text = e.label
        holder.label.setTextColor(t.text)
        if (t.textIsLight) {
            holder.label.setShadowLayer(5f * k, 0f, 2f * k, 0x73000000)
        } else {
            holder.label.setShadowLayer(0f, 0f, 0f, 0)
        }

        val b = e.banner
        if (large && b != null) {
            holder.banner.setImageDrawable(b)
            holder.banner.visibility = View.VISIBLE
            holder.icon.visibility = View.GONE
            holder.label.visibility = View.GONE
        } else {
            holder.banner.setImageDrawable(null)
            holder.banner.visibility = View.GONE
            holder.icon.setImageDrawable(e.icon)
            holder.icon.visibility = View.VISIBLE
            holder.label.visibility = View.VISIBLE
        }

        holder.star.visibility = if (!large && favorites.contains(e.pkg)) View.VISIBLE else View.GONE
        holder.star.setTextColor(t.accent)

        UiKit.style(holder.card, t, k, 14f, 1.10f)
        holder.card.setOnClickListener { onLaunch(e) }
        holder.card.setOnLongClickListener {
            onFavorite(e)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
