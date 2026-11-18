package com.dengying.player.ui.update

/**
 * GitHub Releases 中的一条更新信息。
 *
 * [tag] 保留原始 release tag（通常带 `v` 前缀），用于「忽略此版本」的持久化键；
 * [version] 是去掉前缀后的三段数字，用于展示与比较。两者不混用：tag 变了但
 * 数字没变时，仍应被视为同一个版本。
 */
data class UpdateInfo(
    val tag: String,
    val version: String,
    val publishedDate: String,
    val notes: String,
    val url: String,
)

/** 单次更新检查的结果。 */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Failed : UpdateCheckResult
    data object Disabled : UpdateCheckResult
}

/** 页面展示用的检查状态；失败不会打断当前播放或导航。 */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data object Failed : UpdateCheckState
    data object Disabled : UpdateCheckState
    data class Available(val info: UpdateInfo) : UpdateCheckState
}
