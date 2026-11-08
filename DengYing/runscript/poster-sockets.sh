#!/usr/bin/env bash
# 采样应用启动期间进程内的 socket 数量，用于验证海报下载并发上限（缺陷二）。
# 用法: bash runscript/poster-sockets.sh [采样秒数]
set -uo pipefail

S="adb-10AF4X1BCN0061Z-VjLOHa._adb-tls-connect._tcp"
PKG="com.dengying.player.debug"
DURATION="${1:-12}"

adb -s "$S" shell am force-stop "$PKG"
sleep 1
adb -s "$S" shell am start -n "$PKG/com.dengying.player.MainActivity" >/dev/null 2>&1

for i in $(seq 1 "$DURATION"); do
  sleep 1
  PID=$(adb -s "$S" shell pidof "$PKG" | tr -d '\r')
  if [ -z "$PID" ]; then
    echo "t=${i}s pid=none"
    continue
  fi
  TOTAL=$(adb -s "$S" shell run-as "$PKG" ls "/proc/$PID/fd" | wc -l | tr -d '\r ')
  SOCK=$(adb -s "$S" shell run-as "$PKG" ls -l "/proc/$PID/fd" | grep -c socket | tr -d '\r ')
  echo "t=${i}s pid=$PID fds=$TOTAL sockets=$SOCK"
done
