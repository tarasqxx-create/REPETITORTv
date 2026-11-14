package com.atmosphere.tvhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Пытается открыть оболочку после включения ТВ.
 * Основной (надёжный) способ автозапуска — сделать приложение главным экраном (Home).
 * Эта функция — запасной вариант: на Android 10+ система может запретить запуск из фона.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == "android.intent.action.QUICKBOOT_POWERON") {
            try {
                val i = Intent(context, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            } catch (e: Exception) {
                // Система не разрешила запуск из фона — это нормально.
            }
        }
    }
}
