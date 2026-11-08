package com.dengying.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dengying.player.platform.PlatformLock
import com.dengying.player.ui.theme.Palette

/**
 * 内存缓存上限。海报是可重建的派生数据，磁盘缓存要处理失效与容量，收益不抵复杂度。
 */
private const val CACHE_LIMIT = 120

/**
 * 远程海报图（KMP P4-3c 升舱到 `:ui/commonMain`）。
 *
 * 【为什么手写而不是用 Coil/Glide】
 * 产品契约 SEC-001 要求「不接入第三方 SDK」。海报加载需要的全部能力是：
 * 内存缓存 + 后台下载 + 失败占位，约 60 行。为省这 60 行引一个图片库，
 * 换来的是一份第三方依赖与它自带的线程池、磁盘缓存、生命周期观察者。
 *
 * ## 升舱时拆出去的三块平台能力
 *
 * - 下载：`PosterFetch`（expect，`jvmSharedMain` 一份 actual，Android/JVM 共用）
 * - 解码：`PosterDecoder`（expect，Android 走 `BitmapFactory`、JVM 走 Skia —— 两份实现）
 * - 锁：`PlatformLock`（`:shared` 的门面，JVM 侧是 `synchronized`）
 *
 * 留在本文件的只有跨平台可判定的部分：LRU 缓存、单飞去重、TLS 降级策略、占位渲染。
 *
 * ## 缓存只做内存（LRU 上限 120 张）
 *
 * 迁移前用 `LinkedHashMap(120, 0.75f, true)` 的 accessOrder + `removeEldestEntry`。
 * 后者是 **Java 特有 API**，commonMain 没有，故改为手工维护顺序：命中时把 key
 * 移到末尾（最近使用），超限时删掉表头（最久未使用）。语义等价，且不依赖平台集合。
 */
private object PosterCache {
    /** 访问顺序：表尾 = 最近使用，表头 = 最久未使用 */
    private val order = LinkedHashMap<String, ImageBitmap>()

    /** 与 `order` 同时读写，必须同锁；单独一把锁避免与 inflight 的等待互相阻塞 */
    private val lock = PlatformLock()

    fun get(url: String): ImageBitmap? = lock.withLock {
        val hit = order.remove(url)
        if (hit != null) order[url] = hit // 重新插入 = 移到表尾
        hit
    }

    fun put(url: String, bmp: ImageBitmap) = lock.withLock {
        order.remove(url)
        order[url] = bmp
        while (order.size > CACHE_LIMIT) {
            val eldest = order.keys.firstOrNull() ?: break
            order.remove(eldest)
        }
    }
}

/**
 * 海报下载并发上限。
 *
 * 【为什么需要】`PosterFetch` 不走 `ConcurrencyGate`（那是源站 API 的闸门，
 * 海报来自图床，语义不同），于是**没有任何上限**：一屏 30 张海报就是 30 条并发
 * 连接，与列表/详情的 API 请求抢同一个系统连接池，在弱网下把关键请求挤慢。
 *
 * 【为什么是 3 而不是最初的 6】真机冷启 A/B（docs/04 §16.18.6.2，三个源站图床
 * 各 9–49 张样本）显示源站图床对同源并发是**排队**而非并行：
 *
 * | 并发 | 源站图床 p50 | 豆瓣图床 p50 |
 * |---|---|---|
 * | 1 | 359–411 ms | 92 ms |
 * | 3 | 386–467 ms | 90 ms |
 * | 6 | 1307–1373 ms | 80 ms |
 *
 * 服务端把同源请求串行处理，第 k 张的等待 ≈ k × 单张耗时，所以并发 6 时
 * 每张都要排满一整轮，p50 涨到 3.5 倍；并发 3 仍有 3 倍吞吐而 p50 几乎不变。
 * 豆瓣图床不受影响（全程 80–92 ms），说明这是源站图床的特性，不是我们的调度问题。
 * 海报是装饰性资源，一点可接受，一屏首图晚 1.3 秒不可接受。
 */
private const val POSTER_CONCURRENCY = 3

/**
 * 单飞去重 + 并发限流 + 缓存回填。
 *
 * 调度语义整体在 [PosterLoader]（那里可测），本文件只负责「先查缓存、没有再取」。
 */
private val loader = PosterLoader<ImageBitmap>(POSTER_CONCURRENCY) { url ->
    fetchAndDecode(url)?.also { PosterCache.put(url, it) }
}

/**
 * 下载并解码一张海报。
 *
 * @return 解码结果；失败返回 null（由调用方显示占位）
 */
private suspend fun loadPoster(url: String): ImageBitmap? {
    PosterCache.get(url)?.let { return it }
    return loader.load(url)
}

/** 真正取字节 + 解码（并发上限由 [PosterLoader] 的信号量保证） */
private suspend fun fetchAndDecode(url: String): ImageBitmap? {
    return try {
        // 【TLS 握手失败 → 降级 http 再试一次】
        // 实测 hhmage.com（hhzy 的图床）有线路在 443 上返回**别的域名的证书**
        // （签的是 xinlangtupian.com），Android 严格校验下握手必败；而同域名
        // 80 端口取图正常（2026-10 实测 200）。故 https 拿不到图时用 http 重试：
        // 降级只发生在「https 已经失败」这一情形，正常线路仍走 https；海报是
        // 非敏感位图，network_security_config 本就整体放行明文（源站 API 同理）。
        val bytes = PosterFetch.fetch(url)
            ?: if (url.startsWith("https://")) PosterFetch.fetch("http://" + url.substring(8)) else null
        if (bytes == null || bytes.isEmpty()) return null

        val image = PosterDecoder.decode(bytes) ?: return null
        return image
    } catch (e: Exception) {
        // 单张海报失败不影响整屏：显示占位即可
        null
    }
}

/**
 * 海报位。
 *
 * 加载中与失败都显示**同一个暖灰底块**——原工程用 `alt` 占位，
 * 这里手写对应物：不给"加载中骨架屏"，因为海报位很小，闪一下骨架反而更花。
 */
@Composable
fun Poster(
    url: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 6,
) {
    var bitmap by remember(url) { mutableStateOf(PosterCache.get(url)) }

    LaunchedEffect(url) {
        if (bitmap == null && url.isNotEmpty()) {
            bitmap = loadPoster(url)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Palette.SearchBg),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
