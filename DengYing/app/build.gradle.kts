plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dengying.player"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dengying.player"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1000300
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 只保留中文资源：应用界面全中文，无需其他语言包，可减小产物体积。
    // 用 `localeFilters`（AGP 8.13 起的现行 API）；旧的 `resourceConfigurations`
    // 已被标记待移除，构建时会报 deprecation。
    androidResources {
        localeFilters += listOf("zh", "zh-rCN")
    }

    // 单元测试用真 org.json 实现 —— Android 运行时是系统内置的桩，
    // JVM 单测（src/test）里调用会抛 "not mocked"，故测试期换成真实现。
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnit()
        }
    }

    buildTypes {
        release {
            // 保持关闭：本项目不做混淆验收，且源清单/规则里含大量正则与常量，
            // 混淆收益有限、排障成本高。需要时再单独开。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 【本地安装用 debug 签名】本项目只做本地装机，不上架，所以不需要
            // 正式发布密钥。但**未签名的 APK 装不上**（`apksigner verify` 报
            // Missing META-INF/MANIFEST.MF），因此 release 复用 AGP 自动创建的
            // `debug` 签名配置——它的 keystore 就在 `ANDROID_SDK_HOME/.android/`，
            // 与 debug 包同一张证书（CN=Android Debug，有效期到 2055-11-04）。
            //
            // 收益：包体比 debug 小 8 MB（31.2 vs 39.3），且不带 debug 构建开销。
            //
            // 代价：release 的包名是 `com.dengying.player`（无 .debug 后缀）。
            // 将来若换成正式密钥，必须**先卸载再装**——签名变更 Android 不允许覆盖安装。
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        // minSdk 31 用不到 desugar 的多数能力，但 java.time / 流式 API 在
        // 31 上已原生可用；此处保持 Java 17 与 AGP/JDK 18 工具链一致。
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // 不因 lint 阻断构建；问题以报告形式呈现。
        abortOnError = false
    }
}

dependencies {
    // ---- 跨平台逻辑层（KMP P1）----
    // core/ 与 domain/ 已全部下沉到 :shared/commonMain。:app 通过它访问这些类型，
    // 包名不变（com.dengying.player.core / .domain），故调用处无需改动。
    implementation(project(":shared"))

    // ---- 跨平台 UI 层（KMP P4-3）----
    // 依赖图 :app → :ui → :shared（docs/09 §3）。:ui 用 `api` 暴露 :shared，
    // 故这里不需要再重复声明；上面那条保留是因为 :app 直接用到 :shared 的
    // 装配类型（SourceRepository / Store / PlaybackEngineHost），显式写出来
    // 比依赖传递更清楚。
    implementation(project(":ui"))

    // ---- AndroidX 基础 ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // ---- Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // 【刻意不引入 material-icons-extended】它是 45.6 MB 的 aar（约 5000 个图标），
    // 而本应用只需二十来个图形。它会拖长每次构建的 `mergeExtDexDebug`（实测
    // 16.6s），且让 debug APK 多 20 MB。
    //
    // 【图标来源：自绘矢量】全部图标在 `ui/components/DyIcons.kt` 用 Compose Path
    // 手绘（24×24 网格），零依赖、任意尺寸清晰。原先用字形文本
    // （⏸ ▶ ⏮ ⏭ ⤢ ⤡ ⏪ ⏩ ☀ 🔊 ‹ ⌕ ★ ☆ × 😕）的做法已全部替换——
    // 字形粗细随系统字重漂移，部分 ROM 缺字会渲染成方框，且尺寸受行高约束，
    // FR-709 要求的“图标视觉 ≥24dp”无法保证。
    // 新增图标请加到 DyIcons，不要为了一个图形引整包图标库。
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- 导航 / 生命周期 ----
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ---- Media3：播放引擎（替代 media.AVPlayer）----
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.session)

    // ---- 投屏（替代 AVSessionKit 投播）----
    implementation(libs.androidx.mediarouter)

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- 单元测试（纯 JVM，不进 APK）----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    // ---- 真机测试（androidTest）----
    // 为什么需要它：`HttpClient` 的连接复用行为在 JVM 与 Android 上**不同**。
    // JVM 的 `sun.net.www.protocol.http.HttpURLConnection` 在响应读完后
    // `disconnect()` 是空操作（实测：3 次请求仍只 1 条 TCP 连接）；
    // Android 的 `HttpURLConnectionImpl` 走 OkHttp 内核，`disconnect()` 会
    // 主动拆掉 socket 且**禁用**该连接的复用。所以"连接是否复用"这个结论
    // 必须在真机上测，JVM 单测无法代表。
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
