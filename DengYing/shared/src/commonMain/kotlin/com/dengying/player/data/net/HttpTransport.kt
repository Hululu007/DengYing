package com.dengying.player.data.net

/**
 * 一次原始 HTTP 往返的结果（**不含**耗时与闸门信息）
 *
 * [HttpClient] 在闸门内拿到它，再自己补上 `elapsedMs`——耗时必须由调用侧
 * 用 [com.dengying.player.platform.Clock.elapsedMs] 计算，因为 `t0` 取在
 * **进闸门之前**（排队时间也要算进耗时里），而 transport 只看得见闸门内。
 *
 * [text] 与 [bytes] 只有一个是有效载荷：`binary = false` 时填 [text]，
 * `binary = true` 时填 [bytes]。不合并成一个 `ByteArray` 再按需解码，是因为
 * 桔子的 v5 接口响应是 protobuf——先当字符串解码再编回字节会损坏内容。
 */
data class RawResponse(
    val status: Int,
    val text: String,
    val bytes: ByteArray,
) {
    // data class 里含 ByteArray 必须手写 equals/hashCode，否则是引用比较。
    // 这里内容比较才有意义（测试会比对同一响应的两次结果）。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawResponse) return false
        return status == other.status && text == other.text && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + text.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * 平台 HTTP 传输（KMP P3 批 D）
 *
 * ## 为什么要有这一层
 *
 * 批 C 把 10 个 adapter 全部搬进了 `commonMain`，它们都调 [HttpClient]。
 * 而 `commonMain` **不能引用** `jvmSharedMain`（源集依赖是单向的：jvmSharedMain
 * dependsOn commonMain，反过来不成立）。所以 `HttpClient` 自己必须先升舱，
 * 它唯一真正碰平台的那一小块——`HttpURLConnection`——必须被抽成 expect。
 *
 * ## 职责边界（分得很细，是有意的）
 *
 * 留在 `commonMain`（[HttpClient]）的：
 *  - 全局并发闸门（`ConcurrencyGate.outbound`）——「所有出网都受约束」这条
 *    契约必须与具体传输实现无关，否则每换一个平台就要重新证明一次
 *  - 请求头合并与两个 UA 常量——纯数据，各端必须完全一致
 *  - 超时默认值、`elapsedMs` 的取点与语义、`status = 0` 的失败折叠
 *  - `safeJson` / `safeJsonArray` 的判空规则
 *
 * 落到 actual 的：
 *  - 真正发请求、读响应流
 *  - 失败路径的排障日志（要打 DNS 解析结果，那是平台 API）
 *
 * ## 为什么是 `object` 而不是 `class`
 *
 * 无状态：没有连接池、没有配置对象——连接复用由系统栈自己管（见 [HttpClient]
 * 关于 `disconnect()` 的实测结论）。有状态会诱使调用方 new 出多个实例，
 * 那正好会绕开系统连接池。
 */
expect object HttpTransport {

    /**
     * 发一次请求并读完响应体。
     *
     * ## 契约（actual 必须逐条满足，否则上层行为会漂）
     *
     *  1. **不抛异常**：任何网络错误折叠成 `status = 0`（`HttpClient` 的调用方
     *     靠这个值区分「请求失败」与「拿到响应但内容不对」）。
     *  2. **不跟随重定向**：3xx 原样返回状态码。源站重定向常指向验证页/广告页，
     *     跟过去只会拿到 200 的垃圾（FR-113 的同一口径）。
     *  3. **2xx 读 `inputStream`，其余读 `errorStream`**：否则拿不到错误页正文，
     *     也就无法区分「网关劫持页」与「站点改版」。
     *  4. **无条件 `disconnect()`**：读干净的场景是空操作（流 close 时连接已回
     *     系统池），没读干净的场景正好丢弃这条带残渣的 socket。详见 [HttpClient]
     *     的实测结论——**不要**把它当伪优化删掉。
     *  5. **不设 `Accept-Encoding`**：系统栈在调用方未设置时会自动加 gzip 并透明
     *     解压；自己设了这个头就关掉了透明解压，得自己 gunzip——那是有害改动。
     *  6. 在**后台线程**上执行：`commonMain` 拿不到 `Dispatchers.IO`（它只存在于
     *     JVM/Native 侧，不在 coroutines 的 common 元数据里），故切换必须发生在
     *     actual 内部。
     *
     * @param method `GET` / `POST` 等，直接作为 `requestMethod`
     * @param body 请求体；`null` 表示不带 body（GET）
     * @param headers 已由 [HttpClient.mergeHeaders] 合并好的完整请求头
     * @param binary `true` 时填 [RawResponse.bytes]，`false` 时填 [RawResponse.text]
     */
    suspend fun send(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        connectMs: Int,
        readMs: Int,
        binary: Boolean,
    ): RawResponse
}
