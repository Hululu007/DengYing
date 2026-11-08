// 根构建脚本 —— 只声明插件，不写具体配置。
//
// 插件一律 `apply false`：版本在这里解析一次，由子模块按需应用。
// 这样 `libs.versions.toml` 是版本的唯一来源，子模块不重复写版本号。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // KMP P4-3：CMP 插件（:ui 模块用）
    alias(libs.plugins.compose.multiplatform) apply false
}
