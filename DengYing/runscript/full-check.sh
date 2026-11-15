#!/usr/bin/env bash
# 全量验收：Android 四个测试目标 + :ui 编译 + :app 打包。
# JDK 必须 >= 17（本机默认 JDK 8 会让 Gradle 在配置阶段就失败）。
set -euo pipefail

export JAVA_HOME="C:/Program Files/Java/jdk-18.0.2.1"
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

LOG_FILE="$(mktemp)"
trap 'rm -f "$LOG_FILE"' EXIT

set +e
./gradlew \
  :shared:jvmTest \
  :shared:testDebugUnitTest \
  :ui:jvmTest \
  :app:testDebugUnitTest \
  :ui:compileDebugKotlinAndroid \
  :app:assembleDebug \
  --console=plain > "$LOG_FILE" 2>&1
GRADLE_STATUS=$?
set -e

grep -E "BUILD |^e: |FAILED" "$LOG_FILE" | head -20 || true
if [ "$GRADLE_STATUS" -ne 0 ]; then
  tail -80 "$LOG_FILE" || true
fi
exit "$GRADLE_STATUS"
