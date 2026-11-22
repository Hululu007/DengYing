// 根构建脚本 —— 只声明插件，不写具体配置。
//
// 一律 `apply false`：版本在 libs.versions.toml 里解析一次，子模块按需应用。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
