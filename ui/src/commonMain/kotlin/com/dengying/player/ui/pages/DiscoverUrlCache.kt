package com.dengying.player.ui.pages

/**
 * 发现页「已解析可播地址」的进程级缓存。
 *
 * 【为什么不放在 DiscoverResume】resume 随 nav entry 存活，换批（feedKey 变）
 * 会清空按下标落的解析缓存；用户滑回同一部片、或换批后又抽到同一部片，仍会
 * 重新走一遍解析链（详情 + 换链 + 跨源搜索，1-10s）。这里改用「源 + vodId +
 * 集序号」做键，跨 nav entry、跨批次、同进程内任意页面切换都能命中。
 *
 * 【只存成功结果】失败结果不落这里，交给 UI 的按项失败标记和中心卡重试逻辑
 * 处理（计划 17 §10：6 秒快照不能写成最终失败）。试看链是已校验的可播地址，
 * 一并缓存，后台换全片成功后由 [put] 覆盖。
 *
 * 调用点都在主线程（Compose 的 resolveChainOf），不做额外同步。
 */
internal object DiscoverUrlCache {
    fun key(source: String, vodId: Long, episodeIndex: Int): String =
        UrlCacheStore.key(source, vodId, episodeIndex)

    fun get(key: String): UrlCacheStore.Entry? = store.get(key)

    fun put(key: String, url: String, headers: String, previewOnly: Boolean) =
        store.put(key, url, headers, previewOnly)

    // 进程级单例；容量与 LRU 行为在 [UrlCacheStore] 里，便于单测。
    private val store = UrlCacheStore()
}

/** 有容量上限的「地址 → 可播信息」表，最近命中/写入的排在后面，超出容量淘汰最旧。 */
internal class UrlCacheStore(private val maxEntries: Int = 120) {

    class Entry(val url: String, val headers: String, val previewOnly: Boolean)

    private val entries = LinkedHashMap<String, Entry>()

    fun get(key: String): Entry? {
        // 命中后移到队尾，近似 LRU；LinkedHashMap 的 accessOrder 不在 common 里。
        val hit = entries.remove(key) ?: return null
        entries[key] = hit
        com.dengying.player.player.PlaybackProbe.note("discover-url-cache", "hit key=$key")
        return hit
    }

    fun put(key: String, url: String, headers: String, previewOnly: Boolean) {
        if (url.isEmpty()) return
        com.dengying.player.player.PlaybackProbe.note(
            "discover-url-cache",
            "put key=$key preview=$previewOnly",
        )
        entries.remove(key)
        while (entries.size >= maxEntries) {
            entries.keys.firstOrNull()?.let { entries.remove(it) }
        }
        entries[key] = Entry(url, headers, previewOnly)
    }

    companion object {
        fun key(source: String, vodId: Long, episodeIndex: Int): String =
            "$source|$vodId|$episodeIndex"
    }
}
