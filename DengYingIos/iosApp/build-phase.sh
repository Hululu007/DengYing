#!/bin/bash
#
# Xcode "Run Script" 构建阶段：把 :ui 的 Kotlin/Native framework 编译并嵌入 App。
#
# 用法（Xcode，需 macOS）：
#   1. Target -> Build Phases -> New Run Script Phase
#   2. 脚本内容填 "$SRCROOT/DengYing/build-phase.sh"（或直接粘贴本文件内容）
#   3. 取消勾选 "Based on dependency analysis"（该阶段没有声明输出文件）
#   4. 把该阶段拖到 "Embed Frameworks" 之前
#
# 为什么调用 embedAndSignAppleFrameworkForXcode 而不是 linkDebugFramework：
#   前者由 KGP 提供，读取 Xcode 注入的环境变量（CONFIGURATION / SDK_NAME /
#   ARCHS / TARGET_BUILD_DIR / FRAMEWORKS_FOLDER_PATH），一次性完成
#   「按目标架构编译 framework + 拷进 App + 用本工程签名配置重签」。
#   linkDebugFramework 只产出 framework 文件，不会嵌入也不会重签。
#
# 【前置条件，本仓尚未满足】ui/build.gradle.kts 目前没有
#   binaries { framework { baseName = "ComposeApp" } }
# 故该 Gradle 任务尚不存在，本脚本在 macOS 上首次运行会报
#   "Task 'embedAndSignAppleFrameworkForXcode' not found in project ':ui'"
# 补上 binaries 配置后才能生效 —— 该改动需在 macOS 上验证，见 docs/04 §16.11。
#
# 【未验证（需 macOS）】本脚本只做静态检查（bash -n 语法通过），
#   从未在真实 Xcode 构建中执行过。

set -euo pipefail

# SRCROOT = 含 .xcodeproj 的目录（iosApp/）。仓根在它上一级。
REPO_ROOT="$(cd "$SRCROOT/.." && pwd)"

# Configuration 映射：Xcode 的 Debug/Release -> Gradle 的构建类型。
# embedAndSignAppleFrameworkForXcode 自己读 CONFIGURATION，这里只做兜底校验，
# 避免在 Xcode 之外的环境里静默跑成 Release。
case "${CONFIGURATION:-}" in
  Debug|Release) ;;
  *) echo "warning: CONFIGURATION='${CONFIGURATION:-}' 非 Debug/Release，按 Release 处理" ;;
esac

# platform 判定：仅用于日志与失败时的提示，实际架构选择由 KGP 读 ARCHS 决定。
case "${SDK_NAME:-}" in
  iphoneos*) PLATFORM="iphoneos" ;;
  iphonesimulator*) PLATFORM="iphonesimulator" ;;
  *) PLATFORM="unknown" ;;
esac
echo "note: build-phase platform=$PLATFORM configuration=${CONFIGURATION:-?} root=$REPO_ROOT"

cd "$REPO_ROOT"
exec ./gradlew :ui:embedAndSignAppleFrameworkForXcode
