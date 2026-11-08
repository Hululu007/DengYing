#!/usr/bin/env bash
# 真机验证用 adb 包装器。
#
# 【为什么需要它】手工拼 `adb -s <serial> ...` 时，长串无线 ADB 序列号
# （adb-10AF4X1BCN0061Z-VjLOHa._adb-tls-connect._tcp）极易打错一个字符，
# 报出来的却是 "device not found"，与"设备真的掉了"无法区分，排查时白费时间。
# 这里把序列号固定下来，调用方只写 `./tools/adbx.sh shell ...`。
set -uo pipefail

SERIAL="${DY_SERIAL:-adb-10AF4X1BCN0061Z-VjLOHa._adb-tls-connect._tcp}"
PKG="${DY_PKG:-com.dengying.player.debug}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cmd="${1:-}"
[ $# -gt 0 ] && shift || true

case "$cmd" in
  probe)
    # 打印探针日志最后 N 行（默认 20）
    adb -s "$SERIAL" shell run-as "$PKG" cat files/playback.log 2>&1 | tail -"${1:-20}"
    ;;
  probe-all)
    adb -s "$SERIAL" shell run-as "$PKG" cat files/playback.log 2>&1
    ;;
  reset)
    adb -s "$SERIAL" shell run-as "$PKG" sh -c 'echo -n "" > files/playback.log'
    echo "[adbx] probe reset"
    ;;
  shot)
    # 截图到 docs/screens/<date>/<name>.png
    name="${1:?usage: adbx.sh shot <name>}"
    dir="$ROOT/docs/screens/$(date +%Y-%m-%d)"
    mkdir -p "$dir"
    adb -s "$SERIAL" exec-out screencap -p > "$dir/$name.png"
    echo "$dir/$name.png"
    ;;
  prefs)
    adb -s "$SERIAL" shell run-as "$PKG" cat shared_prefs/hmvideo_store.xml 2>&1
    ;;
  install)
    adb -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" 2>&1 | tail -2
    ;;
  restart)
    adb -s "$SERIAL" shell am force-stop "$PKG"
    adb -s "$SERIAL" shell am start -n "$PKG/com.dengying.player.MainActivity" 2>&1 | tail -1
    ;;
  play)
    # 用 DebugPlayReceiver 直接下发播放意图（坐标无关）。
    # 用法: adbx.sh play <url> [vodId] [source] [title] [lineFrom]
    url="$1"; vodid="${2:-0}"; src="${3:-}"; title="${4:-调试播放}"; line="${5:-}"
    adb -s "$SERIAL" shell am broadcast -a com.dengying.player.DEBUG_PLAY -p "$PKG" \
      --es url "$url" --el vodId "$vodid" --es source "$src" \
      --es vodName "$title" --es title "$title" --es lineFrom "$line" 2>&1 | tail -2
    ;;
  *)
    adb -s "$SERIAL" "$cmd" "$@"
    ;;
esac
