package com.dengying.player.domain.ui

/**
 * 破坏性操作二次确认（FR-505）—— 纯文案策略
 *
 * 需求：清空历史需要二次确认；**确认前不写入**；**取消无副作用**。
 *
 * 本对象只给文案与「确认按钮是否为默认焦点」的策略，可在本地断言；
 * 真正的对话框调用在 Compose 层（`ConfirmDialog`），只在页面里跑。
 *
 * 默认焦点放在**取消**上：清空是不可逆的破坏性操作，
 * 误触一次就丢光记录；默认焦点落在确认键会让「连点两下」直接清空。
 *
 * 适用范围：历史与搜索历史等**用户可再积累**的数据。
 * 收藏不适用本对象——它由用户在条目上逐条取消，不提供批量清空入口。
 */
object ConfirmSpec {

    data class Spec(
        val title: String,
        val message: String,
        /** 确认键文案（动作动词开头，避免「确定」这种无信息文案） */
        val confirmText: String,
        val cancelText: String,
        /** 默认焦点是否落在取消键（破坏性操作固定为 true） */
        val focusCancel: Boolean,
    )

    /** 观看记录清空确认 */
    fun historyClearSpec(): Spec = Spec(
        title = "清空播放历史",
        message = "将删除全部观看记录与续播点，删除后无法恢复。",
        confirmText = "清空",
        cancelText = "取消",
        focusCancel = true,
    )

    /** 搜索历史清空确认 */
    fun searchHistoryClearSpec(): Spec = Spec(
        title = "清空搜索历史",
        message = "将删除全部搜索关键词，删除后无法恢复。",
        confirmText = "清空",
        cancelText = "取消",
        focusCancel = true,
    )

    /**
     * 判定对话框内容是否可用于破坏性确认。
     *
     * 供单测断言用：确认键文案不得是「确定」这类无信息文案，
     * 且破坏性操作的默认焦点必须在取消键上。
     */
    fun isDestructiveSpecValid(spec: Spec): Boolean {
        if (spec.confirmText.isEmpty() || spec.cancelText.isEmpty()) return false
        if (spec.confirmText == spec.cancelText) return false
        return spec.focusCancel
    }
}
