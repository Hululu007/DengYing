package com.dengying.player.ui.components

/**
 * 海报字节下载（KMP P4-3c）。
 *
 * ## 为什么单独拆一个门面
 *
 * `Poster.kt` 里与平台无关的部分是「内存缓存 + 单飞 + https 失败降级 http + 占位显示」，
 * 与平台绑定的只有两件事：**取字节**（`HttpURLConnection` 的超时/重定向/UA 细节）与
 * **解码**（`BitmapFactory` vs `ImageIO`）。拆成两个 expect 后，缓存与降级逻辑留在
 * `commonMain` 一份，只有解码需要按 target 各写一份。
 *
 * ## 为什么不复用 `:shared` 的 `HttpTransport`
 *
 * `HttpTransport` 的契约是**不跟随重定向**（3xx 原样返回，且 `RawResponse` 不带响应头，
 * 调用方无法自行跟随）。海报加载需要 `instanceFollowRedirects = true` —— 图床普遍用 302
 * 把请求转到实际 CDN 节点，不跟随就取不到图。两者语义冲突，故这里独立实现。
 *
 * ## 契约（actual 必须满足）
 *
 * 1. 不抛异常：非 2xx、空体、超时、任何异常一律返回 `null`，由调用方决定是否换协议重试。
 * 2. 跟随重定向（`instanceFollowRedirects = true`）。
 * 3. 无条件 `disconnect()`：响应体没读干净时它丢弃带残渣的 socket；读干净时是空操作
 *    （`use { }` 已把连接还回 keep-alive 池）。
 * 4. 内部切后台线程（`HttpURLConnection` 是阻塞 IO）。
 */
expect object PosterFetch {
    /**
     * 取一张海报的原始字节。
     *
     * @param url 绝对 URL（`http://` 或 `https://`）
     * @return 响应体；失败返回 `null`
     */
    suspend fun fetch(url: String): ByteArray?
}

/**
 * 该图床上必须带的 `Referer`，不需要时返回 `null`。
 *
 * 【为什么需要】豆瓣图床（`*.doubanio.com`）做防盗链：**只校验 Referer 是否存在**，
 * 不看 UA。2026-11-01 实测同一张 `s_ratio_poster` 图：
 *
 * | 请求头 | 结果 |
 * |---|---|
 * | 无 Referer（任意 UA，含浏览器 UA） | **418** `text/html`，0 字节 |
 * | 带 `Referer: https://movie.douban.com/` | 200 `image/jpeg` |
 * | 带 `Referer: https://www.douban.com/` | 200 `image/jpeg` |
 *
 * 所以首页六分类榜单（[com.dengying.player.data.source.discover.DiscoverAdapter.doubanRanking]）
 * 的海报全部是灰块——元数据拿到了，图被 418 拦掉。
 *
 * 【为什么按域名逐个给】其余源站图床不带 Referer 也正常；统一加一个假 Referer
 * 可能反而被别的图床判为盗链。只对已知需要的域名开口子。
 */
internal fun posterReferer(url: String): String? {
    val host = url.substringAfter("://", url).substringBefore('/')
    return when {
        host == "doubanio.com" || host.endsWith(".doubanio.com") -> "https://movie.douban.com/"
        else -> null
    }
}
