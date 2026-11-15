import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

/**
 * 跨平台逻辑层 —— Android + 鸿蒙（CPF 线）
 *
 * ## 边界（与 DengYingIos 完全一致，docs/03-架构与模块边界.md）
 *
 * 1. 不依赖 Compose。Compose 只在 :ui，:shared 保持「无 UI 也能被 ArkUI 壳复用」。
 * 2. `commonMain` 不出现 `android.*` / `java.*` / `javax.*` / `org.json` ——
 *    编译器强制：Kotlin/Native（含 ohosArm64）根本没有这些包，写了就编不过。
 *
 * ## target 选择
 *
 * `androidTarget` + `jvm` + `ohosArm64` + `ohosX64`。
 *
 * - `jvm` 只为跑 :shared:jvmTest（不启 Android 运行时，比 :app:testDebugUnitTest 快，
 *   也不依赖 AGP）。
 * - `ohosArm64` = 真机，`ohosX64` = DevEco 模拟器。两个都声明：模拟器是 x86_64，
 *   只声明 arm64 的话本地验证链路会断。
 * - **本仓没有 iOS target** —— CPF 的 2.2.21-1.1.0 与 iOS 所需的官方 2.4.21
 *   互斥，iOS 侧在 DengYingIos 工程。这意味着 `shared/src/iosMain/` 不在本仓，
 *   commonMain 的 expect 由 `ohosMain` + `jvmSharedMain` + `androidMain` 覆盖。
 *
 * ## 为什么必须用 CPF 编译器
 *
 * `ohos_arm64` / `ohos_x64` 两个 K/N target 只存在于毕昇（BiSheng）定制发行版，
 * 官方 Kotlin 无此 target，也没有 `platform.ArkTS.*` 这些 interop 库。
 */
kotlin {
    // expect/actual 的 object 形式在 Kotlin 2.x 仍标 Beta（KT-61573 每个文件一条警告）。
    // 这里刻意用 object：Log / Clock / Hash / AesCipher / RsaCipher 都是无状态门面，
    // 用 class 就得多一个 .instance，调用点全变。该 API 自 1.9 起稳定可用，只是没去 Beta 标记。
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            // 必须与 :app 的 JVM_17 一致，否则 AGP 报 "Inconsistent JVM-target compatibility"。
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // OH_AVPlayer **不需要自建 cinterop**：CPF 发行版已随 ohos target 提供
    // platform klib `org.jetbrains.kotlin.native.platform.AVPlayer`
    // （package platform.MediaKit.AVPlayer，manifest 自带 linkerOpts=-lavplayer）。
    // 自建 cinterop 反而会因 CPF 的 klib 布局报
    // "Could not find org.jetbrains.kotlin.native.platform.ohos"（实测）。
    ohosArm64()
    ohosX64()

    sourceSets {
        // 中间源集：androidTarget 与 jvm 都跑 JVM，javax.crypto / java.security /
        // java.net / java.util.Base64 两端逐字相同，actual 只写一份。
        // 两份相同的文件迟早漂移，而它们的正确性由同一批 commonTest 算法测试守护。
        // 只有 Log / Clock 因为用到真正的平台 API（android.util.Log vs java.util.logging）
        // 才各自保留 actual。
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        // K/N 中间源集：ohosArm64 与 ohosX64 共享同一份 ArkTS interop actual。
        // 不开默认层级模板（gradle.properties），所以必须手工接线；漏一行就报
        // 「expect 缺 actual」。文件名带 .ohos.kt 只是约定，不参与匹配。
        val ohosMain by creating {
            dependsOn(commonMain.get())
        }
        ohosArm64Main.get().dependsOn(ohosMain)
        ohosX64Main.get().dependsOn(ohosMain)

        commonMain.dependencies {
            // 跨平台逻辑只依赖 -core；-android 是 -core 的 Android 附加件，由 :app 自己引。
            implementation(libs.kotlinx.coroutines.core)
            // 用 api 不用 implementation：:app 的 adapter 直接构造/接收
            // kotlinx.serialization.json.JsonObject / JsonElement，
            // 用 implementation 会报 "Cannot access class 'kotlinx.serialization.json.JsonObject'"。
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // Media3 只在 androidMain：MediaCacheHost 的 Android actual 要构造
        // SimpleCache / StandaloneDatabaseProvider。ohosMain 的实现是「无缓存」降级
        // （鸿蒙侧缓存由 AVPlayer 承担）。
        androidMain.dependencies {
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.hls)
        }
    }
}

android {
    namespace = "com.dengying.player.shared"
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
