#!/usr/bin/env bash
# 全量验收：四个测试目标 + :ui 编译 + :app 打包。
# JDK 必须 >= 11（本机默认 JDK 8 会让 Gradle 在配置阶段就失败）。
set -uo pipefail

export JAVA_HOME="C:/Program Files/Java/jdk-18.0.2.1"
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

./gradlew \
  :shared:jvmTest \
  :shared:testDebugUnitTest \
  :ui:jvmTest \
  :app:testDebugUnitTest \
  :ui:compileDebugKotlinAndroid \
  :app:assembleDebug \
  --console=plain 2>&1 | grep -E "BUILD |^e: |FAILED" | head -20
