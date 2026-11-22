<#
.SYNOPSIS
    把 Apple 平台库（ios_arm64 / ios_x64 / ios_simulator_arm64）补进 CPF 的
    Windows-host Kotlin/Native 发行包，使 Windows 上可以编译 iOS klib。

.DESCRIPTION
    CPF 毕昇版 KGP 的 Windows host 发行包只带 android / linux / mingw / ohos
    的平台库（klib/platform 下没有 ios_* 目录），所以 :composeApp:compileKotlinIos*
    会报 `Unresolved reference 'platform'` / `Cannot access class 'UIViewController'`。
    它的 macOS host 发行包是完整的（Apple + ohos 全都有），因此这个问题只在
    Windows 上出现。

    本脚本从本机另一份官方 Kotlin/Native 发行包里借 Apple 平台库过来，仅用于
    Windows 侧的 klib 编译验证。framework link、Xcode 构建、模拟器与真机运行
    仍然只能在 macOS 上做，这一点不受本脚本影响。

.PARAMETER CpfDist
    CPF 的 Kotlin/Native 发行包目录，默认自动选 ~/.konan 下最新的
    kotlin-native-prebuilt-windows-x86_64-2.2.21-* 目录。

.PARAMETER SourceDist
    提供 Apple 平台库的官方发行包目录，默认自动选 ~/.konan 下任意一个
    klib/platform/ios_arm64 存在的非 CPF 目录。

.EXAMPLE
    ./runscript/prepareApplePlatformLibs.ps1
    ./runscript/prepareApplePlatformLibs.ps1 -SourceDist "$env:USERPROFILE\.konan\kotlin-native-prebuilt-windows-x86_64-2.2.21"
#>
[CmdletBinding()]
param(
    [string]$CpfDist,
    [string]$SourceDist
)

$ErrorActionPreference = 'Stop'

$targets = @('ios_arm64', 'ios_x64', 'ios_simulator_arm64')
$konanRoot = Join-Path $env:USERPROFILE '.konan'

if (-not (Test-Path $konanRoot)) {
    throw "找不到 $konanRoot，先跑一次 Gradle 构建让它下载 Kotlin/Native 发行包。"
}

function Get-PlatformDir([string]$Dist) {
    return (Join-Path $Dist 'klib\platform')
}

# --- 目标：CPF 的 windows host 发行包 ---
if (-not $CpfDist) {
    $candidates = Get-ChildItem $konanRoot -Directory |
        Where-Object { $_.Name -like 'kotlin-native-prebuilt-windows-x86_64-2.2.21-*' } |
        Sort-Object Name -Descending
    if (-not $candidates) {
        throw "在 $konanRoot 下没找到 CPF 的 2.2.21-* 发行包，先跑一次 ./gradlew :composeApp:assembleDebug。"
    }
    $CpfDist = $candidates[0].FullName
}
if (-not (Test-Path (Get-PlatformDir $CpfDist))) {
    throw "$CpfDist 不是有效的 Kotlin/Native 发行包目录。"
}

# --- 来源：任意一份带 ios_arm64 的官方发行包 ---
if (-not $SourceDist) {
    $usable = Get-ChildItem $konanRoot -Directory |
        Where-Object { $_.Name -like 'kotlin-native-prebuilt-*' -and $_.FullName -ne $CpfDist } |
        Where-Object { Test-Path (Join-Path (Get-PlatformDir $_.FullName) 'ios_arm64') }
    # 优先同一 Kotlin 版本线的官方包（2.2.x），平台库与编译器版本越接近越稳。
    $candidates = @($usable | Where-Object { $_.Name -like '*-2.2.*' } | Sort-Object Name -Descending)
    if ($candidates.Count -eq 0) {
        $candidates = @($usable | Sort-Object Name -Descending)
    }
    if (-not $candidates) {
        throw @"
没有找到带 Apple 平台库的发行包。请先下载官方 Kotlin/Native Windows 发行包并解压到 $konanRoot，
例如 kotlin-native-prebuilt-2.2.21，或用 -SourceDist 指定已有目录。
"@
    }
    $SourceDist = $candidates[0].FullName
}

$srcPlatform = Get-PlatformDir $SourceDist
$dstPlatform = Get-PlatformDir $CpfDist

Write-Host "CPF  发行包: $CpfDist"
Write-Host "平台库来源: $SourceDist"

$copied = 0
$kept = 0
foreach ($t in $targets) {
    $src = Join-Path $srcPlatform $t
    $dst = Join-Path $dstPlatform $t
    if (-not (Test-Path $src)) {
        Write-Warning "来源里没有 $t，跳过。"
        continue
    }
    if (Test-Path $dst) {
        Write-Host "已存在，跳过: $t"
        $kept++
        continue
    }
    Copy-Item -Path $src -Destination $dst -Recurse
    Write-Host "已复制: $t"
    $copied++
}

Write-Host ""
Write-Host "完成：复制 $copied 项，保留 $kept 项。"
Write-Host "验证：JAVA_HOME=... ./gradlew :composeApp:compileKotlinIosArm64"
Write-Host "注意：Windows 只能验证 iOS klib 编译；link / Xcode / 模拟器 / 真机仍需 macOS。"
