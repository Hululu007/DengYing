// 根脚本只做插件声明（apply false）：KGP/Compose 插件若在两个兄弟子项目里各自
// 带 version 应用，Gradle 会给每个项目建独立的插件 ClassLoader，而 KGP 的
// kotlinNativeBundleBuildService 是跨项目共享的 BuildService —— 类型不兼容
// （实测报 "Cannot set the value of task ... 'kotlinNativeBundleBuildService'"）。
// 声明在这里、子项目 `id(...)` 不带 version，即可共用同一个 ClassLoader。
plugins {
    kotlin("multiplatform") version "2.2.21-1.1.0" apply false
    id("org.jetbrains.compose") version "1.9.2-1.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21-1.1.0" apply false
}
