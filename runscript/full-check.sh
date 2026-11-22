#!/usr/bin/env bash
# CPF 鸿蒙线验收：Android 侧测试/编译/打包 + 两个 ohos native sharedLib + iOS klib 级编译。
# 不跑 :ui:jvmTest：CPF 的 org.jetbrains.compose.* 缺 jvm 变体，commonMain 已声明该依赖，
# JVM UI 编译会在依赖解析期失败（见 docs/04 与实测记录）。UI 测试改走 :ui:testDebugUnitTest
# （androidTarget 的单元测试，同一份 commonTest，2026-11-22 实测 39 个测试通过）。
# JDK 必须 >= 17（本机默认 JDK 8 会让 Gradle 在配置阶段就失败）。
set -euo pipefail

export JAVA_HOME="C:/Program Files/Java/jdk-18.0.2.1"
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

# 配置期会打印 CPF Compose 依赖对 jvm 源集不可解析的告警；只要下列鸿蒙/Android
# 目标成功，就不把它当作本工程验收失败。脚本输出压缩到日志文件，退出码取 Gradle 真值。
LOG_FILE="$(mktemp)"
trap 'rm -f "$LOG_FILE"' EXIT

# iOS klib 编译前置：CPF 的 Windows 发行包不带 Apple 平台库，缺了先补（脚本幂等）。
if ! compgen -G "$HOME/.konan/kotlin-native-prebuilt-windows-x86_64-2.2.*/klib/platform/ios_arm64" > /dev/null; then
  echo "[full-check] CPF 发行包缺 iOS 平台库，先跑 prepareApplePlatformLibs.ps1"
  pwsh -NoProfile -File runscript/prepareApplePlatformLibs.ps1 \
    || echo "[full-check] 警告：补库失败，:ui:compileKotlinIosArm64 可能失败"
fi

set +e
./gradlew \
  :shared:jvmTest \
  :shared:testDebugUnitTest \
  :ui:testDebugUnitTest \
  :app:testDebugUnitTest \
  :ui:compileDebugKotlinAndroid \
  :app:assembleDebug \
  :ui:compileKotlinIosArm64 \
  :ui:linkDebugSharedOhosX64 \
  :ui:linkDebugSharedOhosArm64 \
  --console=plain > "$LOG_FILE" 2>&1
GRADLE_STATUS=$?
set -e

grep -E "BUILD |^e: |FAILED|linkDebugSharedOhos" "$LOG_FILE" | head -40 || true
if [ "$GRADLE_STATUS" -ne 0 ]; then
  tail -80 "$LOG_FILE" || true
fi
exit "$GRADLE_STATUS"
