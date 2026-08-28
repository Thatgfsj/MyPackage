package com.thatgfsj.mypackage.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.thatgfsj.mypackage.data.Platform
import com.thatgfsj.mypackage.data.StationEntity
import com.thatgfsj.mypackage.ui.components.Capsule
import java.net.URLEncoder

object StationLauncher {

    /**
     * 点击首页卡片：按平台定制候选顺序依次尝试，全部失败才复制链接降级。
     * 候选思路：
     *  - 淘宝/菜鸟：scheme 直换（社区验证可用）优先；
     *  - 拼多多：App Links 精确命中优先，其次路由内嵌完整 URL
     *    （pinduoduo://com.xunmeng.pinduoduo/<完整https链接>，与官方签到链接同构）；
     *  - 最后才是浏览器。
     */
    fun open(context: Context, station: StationEntity) {
        val platform = Platform.from(station.platform)
        val label = if (platform == Platform.CUSTOM) "应用" else platform.label
        Capsule.show("正在打开${label}…", Capsule.Kind.OPENING)

        val link = station.rawLink.trim()
        if (link.isNotBlank()) {
            val uri = Uri.parse(link)
            val stripped = link.replace(Regex("^https?://"), "")
            val packages = if (platform == Platform.CUSTOM) {
                if (station.customPackage.isNotBlank()) listOf(station.customPackage) else emptyList()
            } else {
                platform.packages
            }
            val encoded = URLEncoder.encode(link, "UTF-8")

            val schemeCandidates: List<String> = when (platform) {
                Platform.PDD -> listOf(
                    // 与官方"签到"H5 链接同构：路由段直接内嵌完整 URL
                    "pinduoduo://com.xunmeng.pinduoduo/$link",
                    "pinduoduo://com.xunmeng.pinduoduo/$stripped",
                    // 内嵌编码 URL，避免 query 被路由层截走
                    "pinduoduo://com.xunmeng.pinduoduo/$encoded",
                    // web_view 路由（版本相关，能用则用）
                    "pinduoduo://com.xunmeng.pinduoduo/web_view?embed_url=$encoded",
                    "pinduoduo://com.xunmeng.pinduoduo/web_view/?embed_url=$encoded"
                )
                Platform.TAOBAO -> listOf(
                    "taobao://$stripped",
                    "tbopen://$stripped"
                )
                Platform.CAINIAO -> listOf(
                    "cainiao://$stripped",
                    "taobao://$stripped"
                )
                Platform.CUSTOM -> emptyList()
            }

            // 拼多多在后台时对深链只恢复任务到首页、不处理新链接：
            // 加 CLEAR_TASK 清空其旧任务栈，强制像冷启动一样按链接重新路由
            val taskFix = if (platform == Platform.PDD) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0

            val attempts = buildList<Intent> {
                // 1) App Links 精确命中（若目标 App 注册了该域名）
                for (pkg in packages) {
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            data = uri
                            setPackage(pkg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or taskFix)
                        }
                    )
                }
                // 2) 平台 scheme
                for (candidate in schemeCandidates) {
                    add(
                        Intent(Intent.ACTION_VIEW, Uri.parse(candidate))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or taskFix)
                    )
                }
                // 3) 浏览器兜底
                add(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            for (intent in attempts) {
                if (tryStart(context, intent)) {
                    Capsule.show("已跳转到${label}", Capsule.Kind.SUCCESS)
                    return
                }
            }
        } else {
            val pkg = if (platform == Platform.CUSTOM) station.customPackage
            else platform.packages.firstOrNull()
            if (pkg != null) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null && tryStart(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                    Capsule.show("已打开${label}", Capsule.Kind.SUCCESS)
                    return
                }
            }
        }

        copyLink(context, link)
        Capsule.show("无法跳转，链接已复制，可到${label}中粘贴打开", Capsule.Kind.FALLBACK)
    }

    /** 长按菜单：强制用浏览器打开原始链接 */
    fun openInBrowser(context: Context, link: String) {
        if (link.isBlank()) {
            Capsule.show("该驿站没有保存链接", Capsule.Kind.FALLBACK)
            return
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            copyLink(context, link)
            Capsule.show("无法打开，链接已复制", Capsule.Kind.FALLBACK)
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    fun copyLink(context: Context, link: String) {
        if (link.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("取件码链接", link))
    }
}
