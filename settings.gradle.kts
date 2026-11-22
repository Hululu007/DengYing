// 灯影 —— Android + iOS + 鸿蒙 三端单工程（CPF / 毕昇工具链）
//
// 三端共用 :shared + :ui 源码，同一个 Gradle 构建分别产出：
//   APK（:app）/ Xcode framework（:ui 的 iosArm64/iosSimulatorArm64 framework）
//   / 鸿蒙 .so（:ui 的 ohosArm64/ohosX64 sharedLib）。
// 统一到 CPF 定制 Kotlin 2.2.21-1.2.0 的原因：鸿蒙 target 只存在于 CPF 发行版；
// iOS target 靠 CPF 发行版 + 补充的 Apple 平台库支持（runscript/prepareApplePlatformLibs.ps1）。
//
// 仓库顺序有讲究：eazytec Nexus 代理了 mavenCentral，但**只镜像** org.jetbrains.*
// 的 CPF 定制版；把通用仓放在后面，避免官方构件把定制构件盖掉。
pluginManagement {
    repositories {
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/") {
            name = "cpfNexus"
            content {
                includeGroupByRegex("org\\.jetbrains\\.(kotlin|compose|kotlinx|androidx|skiko).*")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/") {
            name = "cpfNexus"
            content {
                includeGroupByRegex("org\\.jetbrains\\.(kotlin|compose|kotlinx|androidx|skiko).*")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "DengYing"
include(":app")
include(":shared")
include(":ui")
