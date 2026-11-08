pluginManagement {
    repositories {
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
        google()
        mavenCentral()
    }
}

// DengYingIos —— KMP「Android + iOS」工程（官方 Kotlin 插件线）
//
// 由 DengYing 单仓拆分而来：鸿蒙侧需要 CPF 定制工具链（KGP 2.2.21-1.1.0 + CMP 1.9.2-1.1.0），
// 与本仓的 Kotlin 2.4.21 + CMP 1.11.1 互斥，故拆成两个平级工程。
// 本工程只保留官方线：androidTarget + jvm + iosArm64 + iosSimulatorArm64，
// 不含 ohosArm64/ohosX64、不含 harmony/ 壳工程、不含任何 ohosMain 源集。
rootProject.name = "DengYingIos"
include(":app")
include(":shared")
include(":ui")
