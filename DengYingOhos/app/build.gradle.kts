plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Android 壳 —— 本工程里的「参照实现」。
 *
 * 与 DengYingIos/app 逐字相同。保留它有两个作用：
 * 1. 鸿蒙侧的对拍基准 ——「完全对齐安卓」这句话要有可运行的安卓版本才成立。
 * 2. :shared/:ui 的 androidMain actual、commonTest 都要靠 androidTarget 才有效。
 */
android {
    namespace = "com.dengying.player"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dengying.player"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1000100
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 只保留中文资源：界面全中文，其余语言包是纯体积。
    // localeFilters 是 AGP 8.13 起的现行 API（resourceConfigurations 已标待移除）。
    androidResources {
        localeFilters += listOf("zh", "zh-rCN")
    }

    testOptions {
        // JVM 单测里 org.json 是桩（会抛 "not mocked"），测试期换成真实现。
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.useJUnit() }
    }

    buildTypes {
        release {
            // 不做混淆验收：源清单/规则里正则与常量很多，收益有限、排障成本高。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 只本地装机、不上架，但未签名 APK 装不上（apksigner 报
            // Missing META-INF/MANIFEST.MF），故 release 复用 AGP 自动创建的 debug
            // 签名配置（同一张 CN=Android Debug 证书）。
            // 代价：包名不带 .debug；将来换正式密钥必须先卸载再装。
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    lint {
        // 不因 lint 阻断构建；问题以报告形式呈现。
        abortOnError = false
    }
}

/**
 * 【CPF lifecycle-common 在 Android 上制造重复类 —— 实测定案，2026-11-05】
 *
 * 变体普查（读 Gradle 缓存里的 .module 元数据，脚本 DengYing/.tmp/pro/cpfvariants.js）：
 *   org.jetbrains.androidx.lifecycle:lifecycle-common:2.9.4-1.1.0 只发布
 *     non-jvm/common、non-jvm/native、standard-jvm/jvm 三种变体，**没有 androidJvm**，
 *   于是 Android 侧解析落到 standard-jvm → lifecycle-common-jvm-2.9.4-1.1.0.jar；
 *   而真实的 androidx.lifecycle:lifecycle-common-jvm:2.9.4 又经
 *     androidx.navigation:navigation-runtime-android:2.9.4（CPF navigation 的 android 变体引入）
 *     以及 androidx.activity:activity:1.10.1 → androidx.compose.ui:ui-android:1.9.4 进来，
 *   两者包名同为 androidx.lifecycle.* ⇒ checkDebugAarMetadata 报 Duplicate class 全套
 *   （Lifecycle / Lifecycle$Event / LifecycleOwner / Lifecycling / PausingDispatcher …）。
 *
 * 同族的 savedstate、savedstate-compose、lifecycle-runtime、lifecycle-viewmodel
 * 都有 androidJvm 变体（空 files、纯依赖载体，靠依赖真实 androidx 来「转指」），
 * 只有 lifecycle-common 缺 androidJvm ⇒ 只需排掉这一个模块，Android 上的
 * androidx.lifecycle.* 全部由真 androidx 提供，不会缺类。
 * 鸿蒙侧不受影响（ohosX64/ohosArm64 走 non-jvm/native 变体，且这里是 Android 壳的配置）。
 */
configurations.configureEach {
    exclude(group = "org.jetbrains.androidx.lifecycle", module = "lifecycle-common")
    exclude(group = "org.jetbrains.androidx.lifecycle", module = "lifecycle-common-jvm")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose 由 CMP 构件提供（本工程不走 androidx compose-bom ——
    // BOM 里的 1.11.x 是用 Kotlin 2.4 编的，与本仓的 2.2.21-1.1.0 不匹配）。
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // 【刻意不引 material-icons-extended】45.6 MB aar（约 5000 图标），本应用只需二十来个。
    // 它会把 mergeExtDexDebug 拖长（实测 16.6s）、debug APK 多 20 MB。
    // 图标全部在 ui/components/DyIcons.kt 用 Compose Path 手绘（24×24 网格）：
    // 零依赖、任意尺寸清晰，且能保证 FR-709 的「图标视觉 ≥24dp」。新增图标加到 DyIcons。

    implementation(libs.cpf.lifecycle.runtime.compose)
    implementation(libs.cpf.lifecycle.viewmodel.compose)
    implementation(libs.cpf.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.session)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    // 真机测试存在的原因：HttpClient 的连接复用行为在 JVM 与 Android 上不同 ——
    // JVM 的 sun.net HttpURLConnection disconnect() 是空操作（3 次请求仍 1 条 TCP），
    // Android 的 HttpURLConnectionImpl 走 OkHttp 内核，disconnect() 会主动拆 socket
    // 且禁用复用。所以「连接是否复用」必须在真机上测。
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
