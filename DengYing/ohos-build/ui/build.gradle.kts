// :ohosUi —— 把主仓 :ui 的 Compose 源码编到 ohos（docs/04 §16.40）
//
// 版本线 = 社区 CMP **1.9.2-1.1.0**：实测其 klib manifest 的 compiler_version
// 就是 BiSheng 2.2.21-1.1.0（与本构建 KGP 一致），零升级成本。
// 1.12.00.1.0 线需要 BiSheng 2.4.20-0.1.0-12，留给 spike 通过后再评估。
//
// 源码物理共享：srcDir 直指 ../ui/src/commonMain（主仓那份），
// actual 只活在 ohos 线（主仓 :ui 的 android/ios/jvm actual 不进本源集）。
//
// ⚠ 本模块**不再**依赖 :ohosShared（docs/04 §16.41）：
// K/N 运行时是**静态**链进每个 .so 的。进程里同时装 libdengying.so 与 libdengyui.so
// 就等于两套运行时，二者有 50 个同名动态符号（Kotlin_mm_safePoint*、
// Kotlin_getStackTraceStrings、K2N/N2K stub personality …）。首帧渲染时
// `getFontWeightScale → toFloatOrNull → Regex.<init>` 跳到 0x300000070
// SIGSEGV——对象由 A 运行时分配、接口 dispatch 走 B 运行时的 trampoline。
// 对照实验：把 libdengying.so 从进程移除后，同一份 Compose UI 立刻正常渲染。
// 所以这里把 :ohosShared 的**源码**并进同一编译单元，全进程只留一套运行时。

plugins {
    // version 在根 build.gradle.kts 声明（apply false）；此处不带 version，
    // 保证两个子项目共用同一个插件 ClassLoader（见根脚本注释）。
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    val arm64 = ohosArm64()
    val x64 = ohosX64()

    // 每个 target 产出一个 .so：ArkTS 侧经 NAPI 加载后调 ComposeArkUIViewController。
    // 逻辑层（原 :ohosShared）与 UI 层现在同一个链接单元，只出一份 .so ——
    // 原因见文件头注释（两套 K/N 运行时会互踩，实测首帧 SIGSEGV）。
    listOf(arm64, x64).forEach { t ->
        t.binaries {
            sharedLib {
                baseName = "dengyui"
                // 社区 export 模块持有 androidx_compose_ui_arkui_init(env, exports) 这个
                // @CName 入口——它不在 ui-arkui 的 klib 里，只在 export 里。宿主
                // napi Init() 必须调它，否则 ArkTS 侧拿不到 controller 契约。
                //
                // ⚠ 1.9.2-OH.0.1.2-24 尝试史（11-04）：该线 export 含 6 个旧 fusion
                // 裸名 @CName（createRootRenderNode 等，与社区 compose.har 契约一致），
                // 但其平台库是旧代 platform.ohos + 需要 KGP 2.2.21-OH.0.1.0-04 的
                // 定制编译器——eazytec Nexus 260 个 prebuilt tar.gz 里 windows=NONE，
                // Windows 本机无法构建。故回退 1.1.0 线（platform.ArkTS，Windows
                // 可构建），fusion 契约由本项目自写 shim 补（见 ComposeHost.ohos.kt）。
                export(project.dependencies.create("org.jetbrains.compose.export:export:1.9.2-1.1.0"))
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // 宿主 shim 必须调用社区 @InternalComposeApi 标注的 _ArkUIViewController_*。
        // 该注解本身是 internal class，外部模块无法写 @OptIn(...)，只能走编译期开关。
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.annotation.InternalComposeApi")
        freeCompilerArgs.add("-opt-in=kotlin.experimental.ExperimentalNativeApi")
    }

    sourceSets {
        val commonMain by getting {
            kotlin.setSrcDirs(
                listOf(
                    // 主仓 :ui 的真实页面/VM/组件（32 个 .kt，零 java.* 泄漏，实测）
                    file("../../ui/src/commonMain/kotlin"),
                    // 主仓 :shared 的逻辑层（86 个 .kt）——原本是 :ohosShared 模块，
                    // 现在并进来让全进程只有一套 K/N 运行时（见文件头）。
                    file("../../shared/src/commonMain/kotlin"),
                    // spike 自留地：SpikeScreen 等 ohos 专属入口
                    file("src/commonMain/kotlin"),
                )
            )
            dependencies {
                // 必须 api（而非 implementation）：binary 的 export(...) 要求它确实是
                // 对应源集的 API 依赖，否则 link 阶段报
                // "Following dependencies exported in the debugShared binary are not specified as API-dependencies"
                api("org.jetbrains.compose.export:export:1.9.2-1.1.0")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                // :ui/commonMain 的 androidx 依赖，社区线版本（compiler 2.2.21-1.1.0 实证）
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4-1.1.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4-1.1.0")
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.4-1.1.0")
                implementation("org.jetbrains.androidx.savedstate:savedstate-compose:1.3.3-1.1.0")
                // 与社区样例 HarmonyKMP 对齐的 coroutines OH 线（样例用 1.10.2-OH-103）。
                // 1.10.2-1.1.0-02 的 MainHandler 在 runOnMainThread 与 Compose 首次组合
                // 交互时会让主线程栈无限递归并跳到 0x300000070（实测 SIGSEGV）。
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2-OH-110")
                // 原 :ohosShared 的依赖，随源码一起并进来
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.1-1.1.0-02")
            }
        }
        // 原 :ohosShared 的 ohos actual（15 个文件：网络/加密/存储/时间/日志）。
        // 两个 ohos target 各有独立 source set，KGP 不会自动合并同层级的 platform
        // source set，故两份都要显式把 shared 的 actual 目录加进来。
        val ohosArm64Main by getting {
            kotlin.srcDir(file("../../shared/src/ohosMain/kotlin"))
        }

        val ohosX64Main by getting {
            kotlin.srcDir(file("../../shared/src/ohosMain/kotlin"))
        }
    }
}
