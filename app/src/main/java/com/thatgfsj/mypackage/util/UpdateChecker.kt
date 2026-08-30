package com.thatgfsj.mypackage.util

import java.net.HttpURLConnection
import java.net.URL

/** 通过 GitHub Releases 检查应用更新 */
object UpdateChecker {

    const val REPO_URL = "https://github.com/Thatgfsj/MyPackage"

    /** 应用版本号（与 gradle 里的 versionName 保持一致；此处用常量避免 BuildConfig 生成问题） */
    const val APP_VERSION = "0.9.1"

    private const val API_URL = "https://api.github.com/repos/Thatgfsj/MyPackage/releases/latest"

    /**
     * 返回最新发布版本的 tag（如 "v0.9"）。
     * 仓库还没有 Release 或网络失败时返回 null。
     */
    fun latestTag(): String? = try {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "MyPackage-App")
            val stream = if (conn.responseCode in 200..299) conn.inputStream else null
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            text?.let { Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** 语义化版本比较：remote 是否比 current 新 */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").removePrefix("V")
            .split(".").map { it.trim().toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
