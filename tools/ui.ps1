# 真机 UI 辅助：解析 uiautomator dump，打印"文本 + 中心坐标"，并按文本点击。
# 用法：
#   .\tools\ui.ps1 dump                     # 打印当前界面的文本与中心坐标
#   .\tools\ui.ps1 tap "文本片段"            # 点击文本匹配的第一个节点中心
#   .\tools\ui.ps1 tapxy 500 1200           # 直接点击坐标
#   .\tools\ui.ps1 swipe 200 700 900 700    # 横滑（默认 300ms；第 5 个参数给时长）
#
# 【为什么需要这个脚本】真机排查时 `adb shell input tap` 靠猜坐标不可靠：
# 详情页版式会随「其他资源站」从「查找中…」变成「N 个」而整体位移，
# 同一次坐标点击在不同时刻会命中不同控件，导致"按钮坏了"与"没点中"无法区分。
# 按文本匹配至少能保证点到的是那个控件。
param(
  [Parameter(Position=0)][string]$cmd,
  [Parameter(Position=1)][string]$arg1,
  [Parameter(Position=2)][string]$arg2,
  [Parameter(Position=3)][string]$arg3,
  [Parameter(Position=4)][string]$arg4,
  [Parameter(Position=5)][string]$arg5
)
$ErrorActionPreference = 'Stop'
$pkg = 'com.dengying.player.debug'

function Get-UiNodes {
  adb shell uiautomator dump /sdcard/ui.xml | Out-Null
  $xml = adb shell cat /sdcard/ui.xml
  $text = ($xml -join '')
  $nodes = @()
  foreach ($m in [regex]::Matches($text, '<node\b[^>]*/?>')) {
    $s = $m.Value
    $t = [regex]::Match($s, 'text="([^"]*)"')
    $b = [regex]::Match($s, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    $c = [regex]::Match($s, 'clickable="(true|false)"')
    $d = [regex]::Match($s, 'content-desc="([^"]*)"')
    if ($b.Success) {
      $x1=[int]$b.Groups[1].Value; $y1=[int]$b.Groups[2].Value
      $x2=[int]$b.Groups[3].Value; $y2=[int]$b.Groups[4].Value
      $nodes += [pscustomobject]@{
        Text      = if ($t.Success) { $t.Groups[1].Value } else { '' }
        Desc      = if ($d.Success) { $d.Groups[1].Value } else { '' }
        Clickable = if ($c.Success) { $c.Groups[1].Value } else { '' }
        X = [int](($x1+$x2)/2); Y = [int](($y1+$y2)/2)
        X1=$x1; Y1=$y1; X2=$x2; Y2=$y2
      }
    }
  }
  return $nodes
}

switch ($cmd) {
  'dump' {
    $nodes = Get-UiNodes
    Write-Output "== 有文本或描述的元素 =="
    foreach ($n in $nodes) {
      $label = if ($n.Text) { $n.Text } elseif ($n.Desc) { "(desc)$($n.Desc)" } else { '' }
      if ($label) { Write-Output ("{0,-34} tap {1} {2}  clickable={3}" -f $label, $n.X, $n.Y, $n.Clickable) }
    }
    Write-Output "== 可点区域（无文本，前 12 个） =="
    $nodes | Where-Object { $_.Clickable -eq 'true' -and -not $_.Text -and -not $_.Desc } |
      Select-Object -First 12 | ForEach-Object { Write-Output ("tap {0} {1}  [{2},{3}]-[{4},{5}]" -f $_.X, $_.Y, $_.X1, $_.Y1, $_.X2, $_.Y2) }
  }
  'tap' {
    $nodes = Get-UiNodes | Where-Object { $_.Text -like "*$arg1*" -or $_.Desc -like "*$arg1*" }
    if (-not $nodes) { Write-Output "未找到文本: $arg1"; exit 2 }
    $n = $nodes[0]
    Write-Output ("点击 '{0}' -> {1} {2}" -f $n.Text, $n.X, $n.Y)
    adb shell input tap $n.X $n.Y
  }
  'tapxy' {
    Write-Output "点击坐标 $arg1 $arg2"
    adb shell input tap $arg1 $arg2
  }
  'swipe' {
    # swipe x1 y1 x2 y2 [durationMs]
    # 时长影响手势速率：seek 的手势判定对速率不敏感，但**模拟长按拖动**必须给足时长
    # （默认 300ms 是 `input swipe` 的下限，会像快速抛掷，滑不出连续位移）。
    $dur = if ($arg5) { $arg5 } else { '300' }
    Write-Output "滑动 ($arg1,$arg2) -> ($arg3,$arg4) ${dur}ms"
    adb shell input swipe $arg1 $arg2 $arg3 $arg4 $dur
  }
  default { Write-Output "用法: dump | tap <文本> | tapxy <x> <y> | swipe <x1> <y1> <x2> <y2> [ms]" }
}
