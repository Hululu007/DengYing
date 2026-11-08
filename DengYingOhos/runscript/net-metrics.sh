#!/usr/bin/env bash
# 采集一次冷启动的网络耗时探针（index-refresh / index-more / pre-resolve / bg-variants）
set -uo pipefail

S="adb-10AF4X1BCN0061Z-VjLOHa._adb-tls-connect._tcp"
PKG="com.dengying.player.debug"
WAIT="${1:-20}"

adb -s "$S" shell run-as "$PKG" truncate -s 0 files/playback.log
adb -s "$S" shell am force-stop "$PKG"
sleep 1
adb -s "$S" shell am start -n "$PKG/com.dengying.player.MainActivity" >/dev/null 2>&1
sleep "$WAIT"
adb -s "$S" shell run-as "$PKG" cat files/playback.log
