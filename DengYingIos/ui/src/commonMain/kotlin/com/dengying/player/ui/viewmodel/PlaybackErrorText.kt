package com.dengying.player.ui.viewmodel

/**
 * 播放失败的用户文案。
 *
 * 源对象的 `playGap` 是维护者写给自己看的（「播放链未闭环：…」「需要 xx 登录」），
 * 直接渲染等于把内部术语丢给用户。用户只需要知道「能不能看、下一步做什么」，
 * 所以详情页提示、内嵌播放器、全屏播放器统一用这一句，具体原因只进日志与探针。
 */
const val PLAYBACK_UNAVAILABLE_TEXT: String = "这条线路暂时不能播放，请换一条线路。"
