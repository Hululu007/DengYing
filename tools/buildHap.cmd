@echo off
REM Build DengYingOhos HarmonyOS HAP (debug, for ohosX64 emulator).
REM Prereq: gradlew :ui:linkDebugSharedOhosX64 done, libdengyui.so copied to
REM entry/src/main/cpp/thirdparty/ (CMake single channel). Do NOT also put it in
REM entry/libs/x86_64/ — hvigor 00306049 fails on the duplicate.
REM Full hvigor output goes to %TEMP%\dengy_hvigor.log for post-mortem.
setlocal EnableDelayedExpansion
set "DEVECO=D:\DevEco Studio"
set "JAVA_HOME=C:\Program Files\Java\jdk-18.0.2.1"
set "PATH=%DEVECO%\jbr\bin;%DEVECO%\tools\node;%DEVECO%\tools\ohpm\bin;%DEVECO%\tools\hvigor\bin;%PATH%"
set "DEVECO_SDK_HOME=%DEVECO%\sdk"
set "HVLOG=%TEMP%\dengy_hvigor.log"
cd /d D:\workspace\dengY\DengYingOhos\harmony || exit /b 1

for /f %%t in ('powershell -NoProfile -Command "(Get-Item entry\src\main\cpp\thirdparty\libdengyui.so).LastWriteTimeUtc.Ticks"') do set "SO_TICKS=%%t"

echo [1/2] ohpm install --all
call ohpm install --all
if errorlevel 1 (echo ohpm install failed & exit /b 1)

echo [2/2] hvigor assembleHap (entry@default, debug, phone)
call "%DEVECO%\tools\hvigor\bin\hvigorw.bat" --mode module -p module=entry@default -p product=default -p buildMode=debug -p requiredDeviceType=phone assembleHap --analyze=normal --parallel --incremental --no-daemon > "%HVLOG%" 2>&1
set "HVIGOR_ERR=!errorlevel!"
findstr /I /C:"BUILD FAILED" "%HVLOG%" >nul && set "HVIGOR_ERR=1"
if not "!HVIGOR_ERR!"=="0" (
  echo HVIGOR_FAILED - full log: !HVLOG!
  powershell -NoProfile -Command "Get-Content '!HVLOG!' | Select-String -Pattern 'ERROR|FAILED' -Context 2 | Select-Object -First 10 | ForEach-Object { $_.Context.PreContext; $_.Line; $_.Context.PostContext }"
  exit /b 1
)

REM Success requires the signed HAP to be NEWER than the copied .so (stale-artifact guard).
if not exist entry\build\default\outputs\default\entry-default-signed.hap (
  echo HAP_MISSING ^(hvigor exited 0 but no signed hap^) & exit /b 1
)
for /f %%t in ('powershell -NoProfile -Command "(Get-Item entry\build\default\outputs\default\entry-default-signed.hap).LastWriteTimeUtc.Ticks"') do set "HAP_TICKS=%%t"
if !HAP_TICKS! LSS !SO_TICKS! (
  echo HAP_STALE ^(hap older than freshly linked .so - hvigor silently skipped rebuild^) & exit /b 1
)
echo SIGNED_HAP_OK entry\build\default\outputs\default\entry-default-signed.hap
exit /b 0
