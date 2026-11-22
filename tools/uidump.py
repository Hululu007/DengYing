#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""真机 UI 辅助：解析 uiautomator dump，按文本定位并点击。

【为什么不用 tools/ui.ps1】本机默认 PowerShell 5 把无 BOM 的 UTF-8 .ps1 按 ANSI 读，
脚本里的中文注释直接破坏解析（UnexpectedToken）。这个脚本只做同样的事，跨 shell 稳定。

用法：
  python tools/uidump.py            # 打印当前界面所有非空文本节点 + 中心坐标
  python tools/uidump.py tap "电影"  # 点击文本包含给定片段的第一个节点中心
"""
import re
import subprocess
import sys

ADB = r"D:\workspace\dengY\.toolchain\android-sdk\platform-tools\adb.exe"
SERIAL = "adb-10AF4X1BCN0061Z-VjLOHa._adb-tls-connect._tcp"


def sh(args, capture=True):
    return subprocess.run([ADB, "-s", SERIAL] + args,
                          capture_output=capture, text=True,
                          encoding="utf-8", errors="replace")


def nodes():
    sh(["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    r = sh(["shell", "cat", "/sdcard/ui.xml"])
    xml = r.stdout or ""
    out = []
    for m in re.finditer(r"<node\b[^>]*/?>", xml):
        s = m.group(0)
        t = re.search(r'text="([^"]*)"', s)
        d = re.search(r'content-desc="([^"]*)"', s)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        c = re.search(r'clickable="(true|false)"', s)
        if not b:
            continue
        x1, y1, x2, y2 = (int(b.group(i)) for i in range(1, 5))
        txt = (t.group(1) if t else "") or ""
        desc = (d.group(1) if d else "") or ""
        if not txt and not desc:
            continue
        out.append({"text": txt, "desc": desc,
                    "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
                    "bounds": (x1, y1, x2, y2),
                    "clickable": (c.group(1) if c else "")})
    return out


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "tap":
        frag = sys.argv[2]
        cand = [n for n in nodes() if frag in n["text"] or frag in n["desc"]]
        if not cand:
            print("NOT FOUND: %s" % frag)
            print("--- 当前界面文本 ---")
            for n in nodes():
                print("  %r  (%d,%d)" % (n["text"] or n["desc"], n["cx"], n["cy"]))
            return 1
        n = cand[0]
        sh(["shell", "input", "tap", str(n["cx"]), str(n["cy"])])
        print("TAP %r -> (%d,%d)" % (n["text"] or n["desc"], n["cx"], n["cy"]))
        return 0
    if len(sys.argv) >= 2 and sys.argv[1] == "tapxy":
        sh(["shell", "input", "tap", sys.argv[2], sys.argv[3]])
        print("TAP xy (%s,%s)" % (sys.argv[2], sys.argv[3]))
        return 0
    if len(sys.argv) >= 6 and sys.argv[1] == "swipe":
        dur = sys.argv[6] if len(sys.argv) > 6 else "300"
        sh(["shell", "input", "swipe"] + sys.argv[2:6] + [dur])
        print("SWIPE %s" % " ".join(sys.argv[2:6]))
        return 0
    for n in nodes():
        print("  %-28r (%4d,%4d) %s" % (n["text"] or n["desc"], n["cx"], n["cy"],
                                         n["bounds"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
