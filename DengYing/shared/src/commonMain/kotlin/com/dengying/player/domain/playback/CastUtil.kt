package com.dengying.player.domain.playback

/**
 * 源可投性判定与投屏失败分流（FR-419、FR-423）—— 纯函数，不依赖 Android
 *
 * ## 为什么需要可投性判定
 *
 * 投播是把媒体地址交给接收端，由接收端自己拉流；平台接口的描述结构里
 * 只有媒体地址，没有请求头字段。因此需要 Referer/UA 才能取流的源投过去必然被拒。
 * 与其让用户在电视上看到黑屏转圈，不如在点击之前就把结论说清楚
 * （与 FR-406「失败原因可行动」同一口径）。
 *
 * 本对象不发起网络请求，不读播放器状态，不解析地址内容——故可进本地单测（NFR-009）。
 */
object CastUtil {

    /** 可投性结论 */
    enum class CastabilityKind {
        /** 可投：地址即最终媒体流，接收端直拉即可 */
        Castable,

        /** 不可投：播放依赖自定义请求头，或地址尚不可用 */
        NotCastable,

        /** 可试：地址来自源适配器换链，形态未定，不预先剥夺 */
        Trial,
    }

    /** 不可投的具体原因（用于选择文案；文案由页面层映射） */
    enum class BlockReason {
        None,

        /** 该源需要专属请求头 */
        NeedsHeaders,

        /** 地址为空或非 http(s) */
        AddressNotReady,
    }

    data class Castability(val kind: CastabilityKind, val reason: BlockReason)

    /** 地址是否为可直接交给接收端的 http(s) 地址 */
    fun isCastableUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /**
     * 判定顺序有约束：
     *  1. 地址不可用先判——空地址谈不上"能不能投"，给「地址未就绪」比给「该源不支持」更准确；
     *  2. 再判请求头——需要请求头的源投出去必被拒，这是硬边界；
     *  3. 最后判来源形态——换链结果形态未定，放行并在失败时给可行动提示。
     *
     * @param mediaUrl 解析后的媒体地址（判定只看协议，不解析内容）
     * @param requiresHeaders 播放该地址是否需要自定义请求头
     * @param fromSourceChain 地址是否来自源适配器换链结果
     */
    fun decideCastability(
        mediaUrl: String,
        requiresHeaders: Boolean,
        fromSourceChain: Boolean,
    ): Castability {
        if (!isCastableUrl(mediaUrl)) {
            return Castability(CastabilityKind.NotCastable, BlockReason.AddressNotReady)
        }
        if (requiresHeaders) {
            return Castability(CastabilityKind.NotCastable, BlockReason.NeedsHeaders)
        }
        if (fromSourceChain) {
            return Castability(CastabilityKind.Trial, BlockReason.None)
        }
        return Castability(CastabilityKind.Castable, BlockReason.None)
    }

    /** 入口是否可点：可投与可试都放行，不可投禁用 */
    fun isCastEntryEnabled(c: Castability): Boolean = c.kind != CastabilityKind.NotCastable

    /** 不可投时的理由文案（用户可读；可投/可试返回空串，不占用界面） */
    fun castBlockText(reason: BlockReason): String = when (reason) {
        BlockReason.NeedsHeaders -> "该源需要专属请求头，电视无法直接播放"
        BlockReason.AddressNotReady -> "播放地址未就绪，稍后再试"
        BlockReason.None -> ""
    }

    // ---------------- FR-423 失败分流 ----------------

    /**
     * 投屏失败的三类。分类的意义是让用户知道下一步做什么：
     * 是去检查网络，是换一条线路，还是重新解析地址。
     */
    enum class FailureKind {
        /** 接收端不可达：连接没建立，或建立后又断开 */
        DeviceUnreachable,

        /** 接收端拉流失败：命令下发了，但远端拉不到或解不开 */
        RemotePullFail,

        /** 地址失效：还没下发就发现地址不可用 */
        AddressGone,
    }

    /** 失败发生的位置 */
    enum class Stage {
        /** 下发前 */
        Precheck,

        /** 连接阶段 */
        Link,

        /** 下发阶段 */
        Handoff,

        /** 远端播放阶段 */
        Remote,
    }

    /**
     * 判定顺序有约束：地址问题优先于链路问题。
     *
     * 理由：地址不可用时，任何链路动作都是白费；先归到「地址失效」，
     * 用户才会去重解析，而不是反复重连。
     *
     * @param hasAddress 此刻是否持有可用于下发的地址
     */
    fun classifyCastFailure(stage: Stage, hasAddress: Boolean): FailureKind {
        if (!hasAddress) return FailureKind.AddressGone
        if (stage == Stage.Precheck || stage == Stage.Link) return FailureKind.DeviceUnreachable
        return FailureKind.RemotePullFail
    }

    /** 三类失败各自的界面文案。三者互不相同——用户据此判断下一步动作不同 */
    fun castFailureText(kind: FailureKind): String = when (kind) {
        FailureKind.DeviceUnreachable -> "未连接到投屏设备，请检查电视与手机是否在同一网络"
        FailureKind.RemotePullFail -> "电视无法播放该地址，可换线路或在本机播放"
        FailureKind.AddressGone -> "播放地址已失效，请返回重进该集"
    }
}
