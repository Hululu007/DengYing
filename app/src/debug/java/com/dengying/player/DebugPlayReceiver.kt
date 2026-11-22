package com.dengying.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dengying.player.app.DengYingApp

/**
 * 调试用自动化入口（**仅 debug 构建可用**）。
 *
 * ## 为什么需要它
 *
 * 真机验证时，靠 `adb shell input tap` 的坐标点击不可靠：详情页的版式会随
 * 「其他资源站」候选从「查找中…」变为「N 个」而整体位移，同一次坐标点击
 * 在不同时刻命中不同控件——这会让"按钮点不动"与"坐标没命中"两类结论混淆，
 * 排查时无法区分。
 *
 * 这个 receiver 提供一个**坐标无关**的触发路径：直接下发播放意图，
 * 让验证能落在"播放链路是否工作"这个真问题上，而不是"我点没点中"。
 *
 * ## 纪律
 *  - 只在 `BuildConfig.DEBUG` 下注册（见 AndroidManifest 的 debug 源集清单）
 *  - 不进 release 产物：SEC-001 的"无第三方入口"口径不受影响，
 *    因为它不联网、不上报，只是一个本地 Intent 入口
 *  - 参数与 `Routes.player` 一致，便于把真机现象与代码路径对上
 */
class DebugPlayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        // 【写文件而不是打日志】实测（vivo Android 16）：本应用的日志被 ROM 全量过滤，
        // `Log.w` 也读不到。排查要的第一手证据不能依赖日志策略，
        // 故把观测点落到应用私有目录的文件里，用 `adb shell run-as` 读回。
        probe(context, "receiver-enter", "url=${url.take(80)}")
        Log.w("DY/Debug", "play intent received url=${url.take(60)}")

        val app = context.applicationContext as DengYingApp
        probe(context, "app", "hash=${app.hashCode()} store=${app.store.storeState()}")
        // 直接构造播放页路由并交给 MainActivity
        val route = com.dengying.player.ui.navigation.Routes.player(
            url = url,
            title = intent.getStringExtra("title") ?: "调试播放",
            vodId = intent.getLongExtra("vodId", 0L),
            source = intent.getStringExtra("source") ?: "",
            vodName = intent.getStringExtra("vodName") ?: "",
            vodPic = "",
            vodUri = "",
            lineFrom = intent.getStringExtra("lineFrom") ?: "",
            episodeIndex = 0,
            episodeName = "正片",
            altUrls = emptyList(),
            epUrls = emptyList(),
            epNames = emptyList(),
        )
        val open = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_DEBUG_ROUTE
            putExtra(MainActivity.EXTRA_DEBUG_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(open)
        probe(context, "route-dispatched", "len=${route.length}")
        Log.w("DY/Debug", "route dispatched len=${route.length} app=${app.hashCode()}")
    }

    /** 把一行观测写进 files/probe.log（覆盖写，只关心最后一次） */
    private fun probe(context: Context, tag: String, detail: String) {
        runCatching {
            context.getFileStreamPath("probe.log")
                .writeText("[$tag] $detail\n", Charsets.UTF_8)
        }
    }
}
