package com.dengying.player.ui.account

/** release 上登录通道还没接通时的统一提示。 */
const val AUTH_UNAVAILABLE_TEXT: String = "登录暂不可用"

/**
 * [AhAuth.Outcome.Error] → 用户能读懂的一句话。
 *
 * AhAuth 的原始 message 是写给维护者的：里面有 `app_auth_token`、HTTP 状态码、
 * 接口路径这类内部信息，整句丢到界面上，用户既不知道该做什么，也看到了实现细节。
 * 这里只保留三类结果——输入有误、网络不可用、登录暂时不可用；
 * 原始 message 由调用方写进日志。
 */
fun authErrorMessage(raw: String): String {
    val m = raw.trim()
    return when {
        m.isEmpty() -> AUTH_UNAVAILABLE_TEXT
        m.contains("网络") -> "网络不可用，稍后再试"
        m.contains("注册次数") -> "注册太频繁，稍后再试"
        m.contains("app_auth_token", ignoreCase = true) ||
            m.contains("令牌") ||
            m.contains("HTTP") ||
            m.contains("服务端") ||
            m.contains("接口") ||
            // 带请求路径或 appid 的原文一定来自传输层，不能给用户看
            m.contains("/auth/") ||
            m.contains("appid") -> AUTH_UNAVAILABLE_TEXT
        // 本地校验文案（请输入账号 / 密码长度不能少于6位 …）本来就是给用户看的，原样保留
        m.startsWith("请输入") ||
            m.contains("密码长度") ||
            m.contains("账号只能是") ||
            m.contains("不一致") -> m
        else -> "账号或密码不对"
    }
}
