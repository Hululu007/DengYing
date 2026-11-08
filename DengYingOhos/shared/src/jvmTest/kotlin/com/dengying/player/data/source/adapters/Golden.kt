package com.dengying.player.data.source.adapters

import com.dengying.player.core.RequestOutcome

/**
 * 金样本读取助手（`:shared` 侧）
 *
 * 样本存放在 `shared/src/jvmTest/resources/golden/`，是**真实线上响应**逐字节落盘
 * 的结果（上游与字节数记在 `docs/04-验证记录.md` §13.1.1 与样本目录的 `_meta.json`）。
 *
 * 作用是把「解析规则」钉死：P3 把 JSON 实现从 org.json 换到了
 * kotlinx-serialization，同一批样本必须仍然产出同样的数据模型。
 *
 * **为什么样本不能现场抓**：这些源随时会掉线或改协议（录制当天 10 个源里就有
 * 2 个返回 502、1 个超时）。用固定字节做基准，才能把「实现变了」与「上游变了」
 * 两类失败区分开。
 *
 * **为什么放在 `jvmTest` 而不是 `commonTest`**：`Class.getResourceAsStream` 是 JVM
 * API，`commonTest` 里没有它，而把 31KB 的 HTML 样本塞成字符串常量既难看又会
 * 让编译变慢。`commonMain` 的代码对 jvm 与 android 两端**编译产物相同**，
 * 因此 jvmTest 的覆盖等价于覆盖两端（与 `PlatformServicesTest` 的取舍一致，
 * 见 `docs/04` §12.9）。
 */
object Golden {

    /**
     * 读样本文本。
     *
     * 用 `Class.getResourceAsStream` 而非 `File`：单测工作目录由 Gradle 决定，
     * 不是模块根目录，走类路径稳定。
     *
     * 显式 `trimEnd()`：样本末尾可能带换行，解析器对尾随空白不敏感，但断言里
     * 比对字符串时白字符会干扰，故统一去掉尾部空白。
     */
    fun text(name: String): String {
        val stream = Golden::class.java.getResourceAsStream("/golden/$name.txt")
            ?: error("金样本缺失: /golden/$name.txt")
        return stream.use { it.readBytes().decodeToString() }.trimEnd()
    }
}

/**
 * 把「四态判定」与「载体为空」的矛盾显式暴露出来。
 *
 * `core/RequestOutcome` 的既有约定：`SuccessWithData` 必须有条目、
 * `SuccessNoData` 必须没有条目、两种失败都必须没有条目。适配器测试里逐个校验，
 * 避免迁移时把「判定」和「数据」对不上的 bug 悄悄带过去。
 */
fun assertOutcomeConsistent(outcome: RequestOutcome, itemCount: Int, where: String) {
    when (outcome) {
        RequestOutcome.SuccessWithData -> require(itemCount > 0) {
            "$where：判定 SuccessWithData 但 items 为空"
        }
        RequestOutcome.SuccessNoData -> require(itemCount == 0) {
            "$where：判定 SuccessNoData 但 items 有 $itemCount 条"
        }
        RequestOutcome.ParseFailed, RequestOutcome.RequestFailed -> require(itemCount == 0) {
            "$where：判定 $outcome 却带出了 $itemCount 条结果"
        }
    }
}
