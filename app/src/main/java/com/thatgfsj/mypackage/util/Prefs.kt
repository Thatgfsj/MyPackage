package com.thatgfsj.mypackage.util

import android.content.Context

/** 轻量键值持久化（跨启动记忆用户偏好） */
object Prefs {
    private const val FILE = "mypackage_prefs"
    const val KEY_VIEW_MODE = "view_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getInt(context: Context, key: String, def: Int): Int =
        prefs(context).getInt(key, def)

    fun putInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }
}
