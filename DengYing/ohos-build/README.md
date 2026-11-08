# OHOS 独立索引/编译构建

本目录是一个**独立的 Gradle 构建**，唯一目的是让 Android Studio 能对
`shared/src/ohosMain/` 里的 Kotlin 源码做正常的语义索引（Ctrl+点击跳转、
重构、补全），并能真正编译出 ohos klib。

## 为什么要独立成另一个构建

本仓主构建用 **Kotlin 2.4.21**（`gradle/libs.versions.toml:22`），而
`ohos_arm64` target **只存在于 CPF（毕昇）定制的 Kotlin/Native 里**，版本线是
**2.2.21-1.1.0**。两条版本线互斥，无法在同一个 Gradle 构建里共存：

| | 主构建 | 本构建 |
|---|---|---|
| Kotlin | 2.4.21 | 2.2.21-1.1.0（CPF） |
| 平台库 | iOS ✅ / ohos ❌ | ohos ✅ / iOS ❌（实测 `platform.Foundation` 报 unresolved） |
| 产物 | `:app` 双 APK + `:shared` iOS k | ohos klib |

所以：

- **`shared/src/ohosMain/` 的日常编译和索引 → 用本构建**
- **Android / iOS 的日常开发 → 用主构建（仓根）**
- 两者源码目录**物理共享**（见 `build.gradle.kts` 里的 `srcDir` 指向），
  不存在副本，改一处两边同时生效。

## 如何让 Android Studio 索引 ohosMain

在 Android Studio 里 **File → Open** 选择 `ohos-build/` 这个目录（不是仓根），
等 Gradle Sync 完成。之后 `shared/src/ohosMain/**.ohos.kt` 里的
`actual` 声明、`platform.CryptoArchitectureKit.*` 等符号都能 Ctrl+点击跳转，
因为本构建把这些平台 klib 放进了 classpath。

> 为什么不直接在仓根开着一个窗口就够：仓根窗口加载的是主构建（2.4.21），
> 它没有 ohos 平台 klib，`platform.*` 一律未解析。要索引就得开这个窗口。

## 编译命令

```bash
# 在仓根执行（复用主构建的 gradle wrapper）
./gradlew.bat --project-dir ohos-build :compileKotlinOhosArm64 --console=plain
```

产物：`ohos-build/build/classes/kotlin/ohosArm64/main/`（klib）

## 已验证的等价性

本构建产出的 klib 与 `D:/workspace/dengY/.p3tmp/p6/build_ohos.py` 用裸 konanc
编出的那份（667,565 B）编译的是**完全相同的源码集合**：

- `shared/src/commonMain/kotlin/**/*.kt`（83 个文件）
- `shared/src/ohosMain/kotlin/**/*.kt`（12 个文件）

区别只是「谁驱动编译器」：裸 konanc 手工拼参数（`-Xcommon-sources` 等），
本构建由 KGP 自动完成源集接线。

## 边界

- **不影响主构建**：本目录不是主构建的子项目（`settings.gradle.kts` 是独立的，
  主构建的 `settings.gradle.kts` 只 `include(":app"/":shared"/ui")`）。
- **不提交 `build/`、`.gradle/`**：见 `.gitignore`。
- `docs/04` 的记录由用户过门，本目录只带这份 README。
