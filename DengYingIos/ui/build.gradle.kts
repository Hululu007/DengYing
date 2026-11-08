import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    // Compose 编译器插件必须与 Kotlin 版本一致（自 Kotlin 2.0 起随 Kotlin 发布），
    // 故这里用的是 `kotlin-compose`（版本 ref = kotlin），不是 CMP 插件的版本。
    alias(libs.plugins.kotlin.compose)
}

/**
 * 跨平台 UI 层（KMP 迁移 P4，见 docs/09-KMP多平台迁移方案.md 与 docs/04 §14）
 *
 * ## 边界（docs/09 §3 目标架构）
 *
 * ```
 * :app (Android 壳) → :ui → :shared
 * ```
 *
 * - 依赖方向单向：`:ui` 依赖 `:shared`，`:app` 依赖两者。`:ui` **不依赖 `:app`** ——
 *   平台能力（引擎装配、系统亮度/音量、全屏控制器、`PlayerView` 互操作）靠
 *   expect/actual 或由 `:app` 注入的 interface 取得。
 * - `:shared` 不依赖 `:ui`：`docs/09` §3 明确 `:shared` 不许引 Compose，
 *   否则鸿蒙退路（P6「只共享 :shared 逻辑 + ArkUI 壳」）就没了。
 *
 * ## 纯净扫描口径
 *
 * `:ui/commonMain` 与 `:shared/commonMain` 同一口径（禁 `android.*` / `java.*` / `javax.*` /
 * `org.json`），**两个例外**：
 *
 * 1. `androidx.compose.*` —— CMP 的包名就是它（`org.jetbrains.compose` 只提供构件与
 *    Gradle 插件，不改包名）。
 * 2. `androidx.lifecycle.*` —— JB 的 KMP lifecycle 构件同样沿用 AndroidX 包名。
 *
 * 这两个例外由 docs/04 §14.0.4 记录在案，扫描脚本按此放行。
 *
 * ## target 选择
 *
 * 与 `:shared` 一致：`androidTarget` + `jvm` + `iosArm64`/`iosSimulatorArm64`（P5-1 接线）。
 * `jvm` 不是「桌面版」，它的价值是让 `:ui/commonTest` 能在不启 Android 运行时的情况下跑
 * （与 `:shared:jvmTest` 同口径）。iOS target 在 Windows 上只验到
 * `checkIosArm64MainComposeLibrariesCompatibility` + klib 编译，framework 链接需 macOS。
 */
kotlin {
    // 与 :shared 同一理由：`expect`/`actual` 的 object 形式在 Kotlin 2.4 仍标记为 Beta。
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            // 与 :app / :shared 保持一致（JVM_17），否则 AGP 报
            // "Inconsistent JVM-target compatibility"。
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // P5-1（docs/09 §9.3）：iOS target 正式接线。探针阶段已实测 Windows 上
    // `compileKotlinIosArm64` 可真实编译到 klib 级（Kotlin/Native 发行版可从 Maven 下载，
    // 不需要 Xcode）；只有 `linkDebugFramework` 与 simulator 测试需要 macOS。
    //
    // 两个 target 都声明：iosArm64 = 真机，iosSimulatorArm64 = Apple Silicon 模拟器。
    // 不声明 iosX64 —— 本机是 Windows，Intel Mac 的模拟器不在支持范围。
    //
    // P5-4b：两个 target 各自声明 framework。`embedAndSignAppleFrameworkForXcode`
    // 任务由 framework 声明派生（Xcode 的 Run Script 阶段调它），没有这个块就
    // 没有该任务，`iosApp/DengYing/build-phase.sh` 会找不到任务。
    //
    // `baseName = "DengYing"` 决定产物名 `DengYing.framework`，Swift 侧
    // `import DengYing` 与之对应。两个 target 用同一个 baseName 是 CMP 的标准做法：
    // Xcode 按 `PLATFORM_NAME` 只链接其中一个，不会同时出现两个同名 framework。
    iosArm64 {
        binaries {
            framework {
                baseName = "DengYing"
            }
        }
    }
    iosSimulatorArm64 {
        binaries {
            framework {
                baseName = "DengYing"
            }
        }
    }

    sourceSets {
        // 中间源集，与 :shared 同构：androidTarget 与 jvm 都跑在 JVM 上，
        // 但 UI 侧真正共用 actual 的只有「与 Android 无关」的部分
        // （当前只有 `expect fun` 的 JVM 降级实现）。
        //
        // 注意 gradle.properties 里 `kotlin.mpp.applyDefaultHierarchyTemplate=false`：
        // 默认层级模板已关闭，**P5 加 iOS target 时必须手动补 iosMain 的 dependsOn 接线**。
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
        }

        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        // P5-1：iOS 中间源集。gradle.properties 第 28 行
        // `kotlin.mpp.applyDefaultHierarchyTemplate=false` 关闭了默认层级模板，
        // 所以 iosMain 必须手工接线 —— 漏掉下面任一行，编译器要么报
        // 「expect 缺 actual」，要么找不到 iosMain 里的符号。
        val iosMain by creating {
            dependsOn(commonMain.get())
        }

        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)

        commonMain.dependencies {
            // UI 层要用 :shared 的 domain/data/player 契约。用 `api` 而非 `implementation`：
            // :app 装配 ViewModel 时直接构造 :shared 的类型（SourceRepository / Store /
            // PlaybackEngineHost），若这里用 implementation，:app 就得自己再声明一遍
            // :shared —— 依赖关系会变成「:app 直连 :shared」而不是经由 :ui，
            // 与 docs/09 §3 的依赖图不符。
            api(project(":shared"))

            // CMP 构件由 `compose.*` 访问器提供（插件自带，版本 = composeMultiplatform）。
            // 显式坐标而非 `compose.*` 访问器：CMP 1.10.3 起后者被标为
            // Deprecated(ERROR)，Kotlin DSL 里会直接编译失败（见 docs/04 §14.0.6 偏差）。
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            // material3 走独立版本线（1.9.0 稳定，1.10+ 只有 alpha）。
            implementation(libs.compose.material3)

            // JB 的 KMP lifecycle：ViewModel / viewModelScope 的跨平台实现。
            // 包名仍是 androidx.lifecycle.*（CMP 侧沿用），故页面里的 import 不用改。
            implementation(libs.compose.multiplatform.lifecycle.viewmodel.compose)
            implementation(libs.compose.multiplatform.lifecycle.runtime.compose)

            // JB 的 KMP navigation（P4-6 把 NavHost 搬进来时用）。
            implementation(libs.compose.multiplatform.navigation.compose)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // PosterLoader 的调度语义（单飞、取消兜底、并发上限）只能靠可控时钟+
            // 可控调度验证；runTest/TestScope 来自这里
            implementation(libs.kotlinx.coroutines.test)
        }

        // Media3 只出现在 androidMain：`PlayerSurface` 的 actual 要用 `PlayerView`
        // （视图绑定库，不是引擎装配 —— 引擎仍在 :app，符合 docs/09 §3 的边界）。
        androidMain.dependencies {
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.dengying.player.ui"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 本模块只有 Kotlin 代码与 Compose 资源，不需要 BuildConfig。
    buildFeatures {
        buildConfig = false
    }
}
