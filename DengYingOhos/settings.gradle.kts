// DengYingOhos —— KMP「Android + 鸿蒙」工程（CPF / 毕昇工具链）
//
// 由 DengYing 单仓拆分而来（见 README.md「为什么拆成两个工程」）：
// 鸿蒙侧必须用 CPF 定制的 Kotlin 2.2.21-1.1.0（官方 KGP 没有 ohos_arm64 target），
// 而 iOS 侧要 Kotlin 2.4.21（CPF 线没有 iOS 的稳定组合），两条版本线互斥，
// 故拆成 DengYingOhos（本仓）与 DengYingIos（官方线）两个平级工程。
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

rootProject.name = "DengYingOhos"
include(":app")
include(":shared")
include(":ui")
