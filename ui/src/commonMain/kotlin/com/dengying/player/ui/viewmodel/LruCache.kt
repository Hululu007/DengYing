package com.dengying.player.ui.viewmodel

/**
 * 定容 LRU 缓存（访问序淘汰）。
 *
 * ## 为什么需要它
 *
 * `LinkedHashMap` 默认保持**插入序**。只用 `map[key] = value` + 淘汰
 * `keys.first()` 得到的是 **FIFO**：命中不会改变顺序，于是一部常看的片
 * 只因第一次看得早，反而比刚看过的那部先被淘汰。
 *
 * 真正的 LRU 要在命中时把键移到表尾。本类把这两步（命中重排、超限淘汰）
 * 收在一处，使语义可被单测固定——`DetailViewModel.altCache` 原先手写这十几行，
 * 却因为要构造 ViewModel（依赖 `viewModelScope` / `PlayerEngineHost`）而无法断言。
 *
 * 泛型与载荷无关，测的时候用 `String` 即可。
 */
class LruCache<K, V>(private val max: Int) {

    init {
        require(max > 0) { "max 必须为正：容量 0 的退化情形应直接不要这个缓存" }
    }

    // LinkedHashMap 保插入序；我们把「最近使用」记在表尾
    private val map = LinkedHashMap<K, V>()

    val size: Int get() = map.size

    /** 当前键的访问序（最久未用在前）。仅供调试与测试 */
    fun keysInOrder(): List<K> = map.keys.toList()

    /**
     * 取值；命中则把它移到表尾（标记为最近使用）。
     */
    fun get(key: K): V? {
        val v = map.remove(key) ?: return null
        map[key] = v
        return v
    }

    /**
     * 写入；超限时淘汰表头（最久未用）直到回到上限。
     *
     * 同键重复写入按「更新」处理：只改值，位置刷新到表尾
     */
    fun put(key: K, value: V) {
        map.remove(key)
        map[key] = value
        while (map.size > max) {
            val oldest = map.keys.firstOrNull() ?: break
            map.remove(oldest)
        }
    }

    fun clear() = map.clear()
}
