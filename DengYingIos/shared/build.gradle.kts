import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

/**
 * 跨平台逻辑层（KMP 迁移 P1，见 docs/09-KMP多平台迁移方案.md）
 *
 * ## 边界
 *
 * 本模块**只放与平台无关的代码**，硬约束两条，都由构建系统强制：
 *
 * 1. 不依赖 Compose —— 保证鸿蒙退路（P6：「只共享 :shared 逻辑 + ArkUI 壳」）始终可行。
 *    一旦 Compose 进来，这个退路就没了。
 * 2. `commonMain` 不出现 `android.*` / `java.*` —— 由编译器强制：Kotlin/Native 上
 *    根本没有这些包，写了就编不过。所以不需要额外的 lint 规则去检查。
 *
 * 当前包含 `core/`（源清单与请求结果模型）与 `domain/`（分页、搜索、播放规则等纯逻辑）。
 * `data/` 与 `ui/` 在 P2/P4 才进来：它们分别依赖 SharedPreferences / 系统加密 / Compose，
 * 需要先抽象平台服务。
 *
 * ## target 选择
 *
 * `androidTarget` + `jvm` + `iosArm64`/`iosSimulatorArm64`（P5-1 接线）。`jvm` 不是
 * 「桌面版」，它的价值是让这层逻辑能在**不启 Android 运行时**的纯 JVM 上跑单测
 * （`:shared:jvmTest`），比 `:app:testDebugUnitTest` 快且不依赖 AGP。
 *
 * iOS 两个 target 的作用不是「现在就能出 framework」——那需要 macOS + Xcode。
 * 它们的作用是**让编译器替我们守住 commonMain 的纯净性**：加 target 前 `:shared` 只有
 * android + jvm 两个同族 target，KGP 会跳过 metadata 编译（实测见 docs/04 §13.12.8），
 * 于是 commonMain 里的 JVM 泄漏根本没人管。加 target 后 `compileKotlinIosArm64` 会逐条
 * 报出 `@Volatile` / `javaClass` 这类**无 import 的**泄漏（静态扫描抓不到，见 §16.3）。
 */
kotlin {
    // `expect`/`actual 的 object 形式在 Kotlin 2.4 仍标记为 Beta，会在每个
    // expect/actual 文件上报 KT-61573 警告。本项目用 object 而非 class 是有意的：
    // 这些门面（Log / Clock / Hash / AesCipher / RsaCipher）都是无状态单例，
    // 用 object 才不用在每个调用点写 `.instance`。该 API 自 1.9 稳定可用，
    // 只是官方未去掉 Beta 标记，故显式抑制。
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            // 与 :app 保持一致（app/build.gradle.kts 用 JVM_17）。两侧 jvmTarget 不一致时
            // AGP 会报 "Inconsistent JVM-target compatibility"，故此处必须显式对齐。
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
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        // 中间源集：androidTarget 与 jvm 都跑在 JVM 上，`javax.crypto` / `java.security` /
        // `java.net` / `java.util.Base64` 这些 API 两端完全一致，actual 实现可以只写一份。
        //
        // 不写成 androidMain/jvmMain 各一份的原因：两份逐字相同的文件迟早会漂移，
        // 而它们的正确性由同一批算法测试守护（见 commonTest）。
        // 仅 `Log` 与 `Clock` 因为用到真正的平台 API（android.util.Log vs java.util.logging）
        // 才各自保留 actual。
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

        // 注意：本工程**不含鸿蒙侧**，`src/ohosMain/` 目录不存在，这是有意的。
        //
        // ohos_arm64 target 只存在于 CPF（毕昇）定制的 Kotlin 2.2.21-1.1.0 里，
        // 而本工程用官方 Kotlin 2.4.21（带 iOS 但没有 ohos）。两条版本线互斥，
        // 无法塞进同一个 Gradle 构建 —— 这正是拆成两个工程的原因。
        //
        // 鸿蒙侧的 shared/src/ohosMain 与 harmony/ 壳工程在**同级工程**
        // `D:/workspace/dengY/DengYingOhos`（CPF 线，自包含，不再用 srcDir 跨仓引用）。

        commonMain.dependencies {
            // 跨平台逻辑只该依赖 -core；-android 只是 -core 的 Android 附加件，
            // 由 :app 自己按需引入（它要的是 Dispatchers.Main 在 Android 上的实现）。
            implementation(libs.kotlinx.coroutines.core)
            // KMP P3（docs/09 §5.1）：只引运行时库，不引 serialization 编译插件。
            // 迁移走 `JsonElement` 遍历式（JsonX 门面），不用 @Serializable。
            //
            // 用 `api` 而非 `implementation`：:app 里的 adapter 仍要自己构造
            // `JsonX.buildObject(...)` 并接收 `JsonX.parseObject(...)` 的返回值，
            // 那些函数的签名里带 `JsonObject` / `JsonElement`。用 implementation
            // 则 :app 拿不到这些类型，编译报
            // "Cannot access class 'kotlinx.serialization.json.JsonObject'"。
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            // kotlin.test 会自动按 target 选变体，jvm 侧落到 kotlin-test-junit。
            // 断言 API 与 JUnit 有差异（消息参数位置相反），迁移时已逐项核对。
            implementation(libs.kotlin.test)
        }

        // KMP P4-1c：Media3 只出现在 androidMain —— `MediaCacheHost` 的 Android actual
        // 要构造 `SimpleCache` / `StandaloneDatabaseProvider`。
        //
        // 用 `implementation` 而非 `api`：Media3 类型绝不进 commonMain 签名
        // （跨平台契约只交 `Any?` 窄口子，由 :app 的 `MediaCacheDataSource` 解释）。
        // 既然是纯内部实现细节，就没有理由把它暴露给 :app 的编译类路径——
        // :app 自己已经声明了 Media3 依赖。
        //
        // 只加在 androidMain：jvm target 没有 Media3（那是 Android 库），
        // 它的 actual 如实返回「无缓存」。
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

    // 本模块只产出 Kotlin 代码，没有资源，也不需要 BuildConfig。
    buildFeatures {
        buildConfig = false
    }
}
