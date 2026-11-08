// OHOS 独立构建的模块脚本 —— 见同目录 README.md。
//
// 与主构建（仓根 :shared）的关系：
//  - 本构建 **不复用** :shared 的 build.gradle.kts，因为主构建的 KGP 是 2.4.21，
//    不认识 ohos_arm64。本构建用 CPF 的 2.2.21-1.1.0 从头声明 target。
//  - 但源码目录**物理共享**：下面 srcDir 直指 ../../shared/src/commonMain 与
//    ../../shared/src/ohosMain（本模块从 ohos-build/ 根移入 ohos-build/shared/
//    后多了一层，路径已同步修正），不存在副本，改一处两边同时生效。

plugins {
    // version 由根 build.gradle.kts 声明（apply false）：两个子项目必须共用
    // 同一个插件 ClassLoader，否则 KGP 的 kotlinNativeBundleBuildService 跨项目
    // 传递时类型不兼容（实测 BUILD FAILED）。
    kotlin("multiplatform")
}

kotlin {
    // 两个 ohos target 都声明：
    //  - ohosArm64 = 鸿蒙真机 ABI
    //  - ohosX64   = DevEco 模拟器 ABI（实测 Pura 90 Pro Max 的 hw.cpu.arch = x86_64）
    // 两者共用同一份 commonMain/ohosMain 源码，只是产出不同 ABI 的 klib。
    ohosArm64()
    ohosX64()

    // 链接成动态库（.so），供 ArkTS 侧通过 NAPI 加载。
    // 不声明 binaries 的话 linkOhosX64 是 NO-SOURCE，只有 klib 没有可装载产物。
    listOf(ohosArm64(), ohosX64()).forEach { t ->
        t.binaries {
            sharedLib {
                baseName = "dengying"
            }
        }
    }

    // 主构建（shared/build.gradle.kts:42-44）已有这个 flag，本构建漏了会导致
    // 每个 expect/actual 都刷一条 Beta 警告。加在 target 外层即对所有编译生效。
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val commonMain by getting {
            // 源码目录指向主仓的共享层，不复制文件
            kotlin.setSrcDirs(
                listOf(
                    file("../../shared/src/commonMain/kotlin"),
                )
            )
            dependencies {
                // ABI 2.2.0 批次：1.11.0 系 coroutines 的 klib ABI = 2.4.0，
                // CPF 2.2.21 编译器会报 incompatible ABI 并拒收（实测）。
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2-OH-110")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.1-1.1.0-02")
            }
        }

        // ohosArm64 与 ohosX64 各自有独立的 source set（KGP 不会自动合并同层级
        // 的 platform source set），故两份都要显式指向同一批 actual 文件。
        val ohosArm64Main by getting {
            kotlin.setSrcDirs(
                listOf(
                    // 主仓的 ohos 平台实现：12 个 actual（P6-3 交付）
                    file("../../shared/src/ohosMain/kotlin"),
                )
            )
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2-OH-110")
            }
        }

        val ohosX64Main by getting {
            kotlin.setSrcDirs(
                listOf(
                    file("../../shared/src/ohosMain/kotlin"),
                )
            )
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2-OH-110")
            }
        }
    }
}
