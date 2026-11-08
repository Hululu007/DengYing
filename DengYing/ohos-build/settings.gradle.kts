// OHOS 独立构建 —— 见同目录 README.md 说明为什么要独立成另一个构建。
//
// 关键点：本构建用 CPF（毕昇）的 Kotlin 2.2.21-1.1.0，它带 ohos_arm64 target；
// 主构建用 Kotlin 2.4.21，它带 iOS 但没有 ohos。两者互斥，故分两个 Gradle 构建。
pluginManagement {
    repositories {
        // CPF 的 maven 仓必须排在最前：kotlin-gradle-plugin 2.2.21-1.1.0 与
        // 它配套的 kotlin-native-prebuilt 都只在这里有。
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/") {
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin.*")
                includeGroupByRegex("org\\.jetbrains\\.compose.*")
                includeGroupByRegex("org\\.jetbrains\\.kotlinx.*")
                includeGroupByRegex("org\\.jetbrains\\.androidx.*")
                includeGroupByRegex("org\\.jetbrains\\.skiko.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "DengYingOhos"

// 目录名保持 shared/ ui/（与主仓模块同名，读源码时不迷路），项目名加 ohos 前缀避免混淆。
// 只有 :ohosUi 一个模块（docs/04 §16.41）：K/N 运行时静态链进每个 .so，
// 两个 .so 就是两套运行时，会在首帧渲染时互踩 SIGSEGV。逻辑层源码
// （主仓 shared/）已并入 :ohosUi 的同一个编译单元，只产出一份 libdengyui.so。
include(":ohosUi")
project(":ohosUi").projectDir = file("ui")
