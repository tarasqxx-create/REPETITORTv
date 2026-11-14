package com.atmosphere.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

class AppEntry(
    val pkg: String,
    val label: String,
    val component: ComponentName,
    val icon: Drawable?,
    val banner: Drawable?
)

/** Находит установленные приложения через системный PackageManager (никаких выдуманных ссылок). */
object AppRepository {

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val tvApps = ArrayList<AppEntry>()
        val otherApps = ArrayList<AppEntry>()

        fun collect(category: String, target: MutableList<AppEntry>) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            val list = try {
                pm.queryIntentActivities(query, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                if (!seen.add(ai.packageName)) continue
                val label = try {
                    ri.loadLabel(pm).toString()
                } catch (e: Exception) {
                    ai.packageName
                }
                val icon = try {
                    ri.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }
                var banner: Drawable? = try {
                    ai.loadBanner(pm)
                } catch (e: Exception) {
                    null
                }
                if (banner == null) {
                    banner = try {
                        ai.applicationInfo?.loadBanner(pm)
                    } catch (e: Exception) {
                        null
                    }
                }
                target.add(AppEntry(ai.packageName, label, ComponentName(ai.packageName, ai.name), icon, banner))
            }
        }

        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, tvApps)
        collect(Intent.CATEGORY_LAUNCHER, otherApps)

        val cmp = compareBy<AppEntry> { it.label.lowercase() }
        return tvApps.sortedWith(cmp) + otherApps.sortedWith(cmp)
    }

    /** Пакеты популярных ТВ-сервисов: используются только если они реально установлены. */
    val KNOWN_FAVORITES = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "org.xbmc.kodi",
        "org.videolan.vlc",
        "com.plexapp.android",
        "com.spotify.tv.android"
    )
}
