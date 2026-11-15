import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    // Compose 编译器插件自 Kotlin 2.0 起随 Kotlin 发布，故版本 ref = kotlin（2.2.21-1.1.0），
    // 不是 CMP 插件的版本。两者不配对会在 @Composable 解析阶段崩。
    alias(libs.plugins.kotlin.compose)
}

/**
 * 跨平台 UI 层 —— Android + 鸿蒙（同一份 Compose 源码）
 *
 * ## 依赖方向
 *
 * `:app (Android 壳) → :ui → :shared`，单向。:ui 不依赖 :app；平台能力
 * （引擎装配、亮度/音量、全屏控制器、PlayerView 互操作）靠 expect/actual 或
 * :app 注入的 interface 取得。`commonMain` 与 `shared/commonMain` 同一纯净口径，
 * 例外只有 `androidx.compose.*`（CMP 的包名，org.jetbrains.compose 只改构件不改包名）
 * 和 `androidx.lifecycle.*`（CPF 的 KMP 移植沿用 AndroidX 包名）。
 *
 * ## target 与产物
 *
 * - `androidTarget` / `jvm`：JVM 侧，跑 :app 与 :ui:commonTest。
 * - `ohosArm64` / `ohosX64`：**各自产出一个 sharedLib**（`libdengyui.so`），
 *   由 harmony 壳工程用 dlopen/dlsym 加载。这就是「复用 Compose UI」在鸿蒙侧
 *   的落地形态 —— 不是重新实现一遍 UI，而是把同一份 commonMain 编成 K/N .so。
 *
 * ## 两个必须同时成立的导出契约
 *
 * 1. `binaries.sharedLib { export(compose-export) }`：让 link 阶段把
 *    `androidx_compose_ui_arkui_init` 与 44 个 `androidx_compose_ui_arkui_ArkUIViewController_*`
 *    的 `@CName` 保留在动态符号表里。
 * 2. `commonMain.dependencies { api(compose-export) }`：让 Kotlin 侧能看到
 *    `ComposeArkUIViewController`。
 *
 * 只做第 1 步会得到「链接成功、运行时 dlsym 返回 null」的 .so —— 静默失败，
 * 现象是页面创建不出 controller。这条是实测踩出来的（docs/04 P6 段）。
 *
 * ## SpikeScreen 的存在理由
 *
 * `ui/src/ohosMain/.../ComposeHost.ohos.kt` 目前渲染的是 SpikeScreen（一个
 * material3 计数器），不是正式 app 导航。鸿蒙首帧渲染问题尚未解决（详见
 * README「当前鸿蒙侧状态」），先用最小 Compose 树把「CMP 能否在鸿蒙出图」
 * 与「业务代码能否在鸿蒙跑」两件事解耦。业务接线要等首帧打通之后。
 */
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // ArkUI 侧要用到 CMP 的 internal API（ComposeArkUIViewController 等）。
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.annotation.InternalComposeApi")
        // @CName / ExperimentalForeignApi 在 2.2.x 仍是实验 API。
        freeCompilerArgs.add("-opt-in=kotlin.experimental.ExperimentalNativeApi")
    }

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    val targets = listOf(ohosArm64(), ohosX64())
    targets.forEach { target ->
        target.binaries {
            sharedLib {
                // 产物名 = libdengyui.so；compose.har 的 ArkTS 侧与 napi_init.cpp 都
                // 按这个名字 dlopen，改名要三处一起改。
                baseName = "dengyui"
                // 见上面「两个必须同时成立的导出契约」第 1 条。
                export(libs.compose.export)
                // 第二条导出契约的鸿蒙业务侧：AppFacade.ohos.kt 的 13 个
                // `@CName("dengy_*")` 在 :shared 里。拆分前 :ohosShared 是独立模块时，
                // 这些入口被链接器 DCE 掉（产物里只剩 krefs/kifacetable，没有
                // dengy_* 动态符号），napi_init.cpp 的 dlsym 全部返回 null。
                // 本工程把 :shared 合成 :ui 的 project 依赖后，必须显式 export 它。
                export(project(":shared"))

                // OH_AVPlayer：:shared 的 cinterop 只产 klib，最终 libdengyui.so
                // 仍需链系统库 libavplayer.so。sysroot 路径运行时探测（同
                // shared/build.gradle.kts 的 cinterop 配置）。
                val ohosLibRoot = File(System.getProperty("user.home"), ".konan/dependencies")
                    .listFiles { f -> f.isDirectory && f.name.startsWith("sysroot-ohos-aarch64-") }
                    ?.maxByOrNull { it.name }
                    ?.resolve("usr/lib")
                if (ohosLibRoot != null) {
                    val archDir =
                        if (target.name.contains("Arm64")) "aarch64-linux-ohos" else "x86_64-linux-ohos"
                    linkerOpts("-L${ohosLibRoot.path}/$archDir", "-lavplayer")
                }
            }
        }
    }

    sourceSets {
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        val ohosMain by creating {
            dependsOn(commonMain.get())
        }
        ohosArm64Main.get().dependsOn(ohosMain)
        ohosX64Main.get().dependsOn(ohosMain)

        // 导出契约第 2 条（api，不是 implementation）：ComposeHost.ohos.kt 要用
        // androidx.compose.ui.window.ComposeArkUIViewController，它来自这个构件。
        //
        // 必须放在 ohosMain 而不是 commonMain：`compose.export:export:1.9.2-1.1.0`
        // **只有 ohos 变体**，没有 androidJvm / jvm 变体。写在 commonMain 会让它沿
        // `:app → :ui` 传播到 Android 运行时类路径，解析直接失败：
        //   No matching variant of org.jetbrains.compose.export:export:1.9.2-1.1.0
        //   (consumer needed androidJvm, only metadata/ohos variants exist)
        // 实测于 2026-11-05 拆分后首次构建。
        ohosMain.dependencies {
            api(libs.compose.export)
        }

        commonMain.dependencies {
            // api：:app 直接构造 :shared 的装配类型（SourceRepository / Store /
            // PlaybackEngineHost），经由 :ui 暴露比让 :app 重复声明更符合 docs/03 的依赖图。
            api(project(":shared"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)

            // CPF 的 KMP lifecycle / navigation / savedstate —— 包名仍是
            // androidx.lifecycle.* / androidx.navigation.*，所以页面里的 import 不用改。
            implementation(libs.cpf.lifecycle.viewmodel.compose)
            implementation(libs.cpf.lifecycle.runtime.compose)
            implementation(libs.cpf.navigation.compose)
            implementation(libs.cpf.savedstate.compose)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // PosterLoader 的调度语义（单飞、取消兜底、并发上限）只能靠可控时钟 +
            // 可控调度验证。
            implementation(libs.kotlinx.coroutines.test)
        }

        // Media3 只出现在 androidMain：VideoSurface 的 Android actual 要用 PlayerView
        // （视图绑定，不是引擎装配 —— 引擎仍在 :app，符合 docs/03 边界）。
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

    buildFeatures {
        buildConfig = false
    }
}
