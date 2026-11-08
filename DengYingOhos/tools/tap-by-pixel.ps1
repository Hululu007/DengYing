param([string]$Step, [string]$FindColor, [int]$MinY = 0)
# 自动化真机测试辅助：截图 -> 像素定位 -> 点击 -> 验证
$adb='D:\workspace\dengY\.toolchain\android-sdk\platform-tools\adb.exe'
$py='python'
$shot = "docs\screens\step-$Step.png"

& $adb shell screencap -p /sdcard/step.png 2>&1 | Out-Null
& $adb pull /sdcard/step.png $shot 2>&1 | Out-Null
& $adb shell rm /sdcard/step.png 2>&1 | Out-Null

$coord = & $py -c @"
from PIL import Image
im = Image.open(r'$shot').convert('RGB')
w,h = im.size
px = im.load()
mode = '$FindColor'
best = None
if mode == 'black-chip':
    # 黑色 chip（剧集按钮/线路选中态）：深色块
    for y in range(max(2200, $MinY), h-40, 4):
        xs = [x for x in range(20, 500, 4) if sum(px[x,y]) < 120]
        if len(xs) >= 8:
            if best is None: best = [y, y, min(xs), max(xs)]
            best[1] = y
            best[2] = min(best[2], min(xs)); best[3] = max(best[3], max(xs))
elif mode == 'red-badge':
    # 「续」红角标
    for y in range(max(2200, $MinY), h-40, 3):
        xs = [x for x in range(20, 500, 3) if px[x,y][0] > 180 and px[x,y][1] < 80 and px[x,y][2] < 80]
        if len(xs) >= 3:
            if best is None: best = [y, y, min(xs), max(xs)]
            best[1] = y
elif mode == 'gray-chip':
    # 灰色圆角 chip（线路未选中/资源站）
    for y in range(2000, h-40, 4):
        xs = [x for x in range(20, 600, 4) if (lambda p: 180 < p[0] < 235 and 180 < p[1] < 235 and 180 < p[2] < 235)(px[x,y])]
        if len(xs) >= 10:
            if best is None: best = [y, y, min(xs), max(xs)]
            best[1] = y
if best:
    cx = (best[2]+best[3])//2; cy = (best[0]+best[1])//2
    print('%d %d' % (cx, cy))
else:
    print('NONE')
"@

if ($coord -eq 'NONE') { Write-Output "[$Step] 未找到目标 ($FindColor)"; exit 1 }
$x, $y = $coord.Split(' ')
Write-Output "[$Step] 命中 ($x, $y) 点击"
& $adb shell input tap $x $y 2>&1 | Out-Null
