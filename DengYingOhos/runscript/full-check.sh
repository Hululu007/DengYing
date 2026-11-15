#!/usr/bin/env bash
# CPF 鸿蒙线验收：Android 侧测试/编译/打包 + 两个 ohos native sharedLib。
# 不跑 :ui:jvmTest：CPF 的 org.jetbrains.compose.*:1.9.2-1.1.0 缺 jvm 变体，
# commonMain 已声明该依赖，JVM UI 编译会在依赖解析期失败（见 docs/04 与实测记录）。
# JDK 必须 >= 17（本机默认 JDK 8 会让 Gradle 在配置阶段就失败）。
set -euo pipefail

export JAVA_HOME="C:/Program Files/Java/jdk-18.0.2.1"
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

# 配置期会打印 CPF Compose 依赖对 jvm 源集不可解析的告警；只要下列鸿蒙/Android
# 目标成功，就不把它当作本工程验收失败。脚本输出压缩到日志文件，退出码取 Gradle 真值。
LOG_FILE="$(mktemp)"
trap 'rm -f "$LOG_FILE"' EXIT

set +e
./gradlew \
  :shared:jvmTest \
  :shared:testDebugUnitTest \
  :app:testDebugUnitTest \
  :ui:compileDebugKotlinAndroid \
  :app:assembleDebug \
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
