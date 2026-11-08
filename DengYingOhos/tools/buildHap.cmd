@echo off
REM Build DengYingOhos HarmonyOS HAP (debug, for ohosX64 emulator).
REM Prereq: gradlew :ui:linkDebugSharedOhosX64 done, libdengyui.so copied to entry/libs/x86_64/.
setlocal
set "DEVECO=D:\DevEco Studio"
set "JAVA_HOME=C:\Program Files\Java\jdk-18.0.2.1"
set "PATH=%DEVECO%\jbr\bin;%DEVECO%\tools\node;%DEVECO%\tools\ohpm\bin;%DEVECO%\tools\hvigor\bin;%PATH%"
set "DEVECO_SDK_HOME=%DEVECO%\sdk"
cd /d D:\workspace\dengY\DengYingOhos\harmony || exit /b 1

echo [1/2] ohpm install --all
call ohpm install --all
if errorlevel 1 (echo ohpm install failed & exit /b 1)

echo [2/2] hvigor assembleHap (entry@default, debug, phone)
call "%DEVECO%\tools\hvigor\bin\hvigorw.bat" --mode module -p module=entry@default -p product=default -p buildMode=debug -p requiredDeviceType=phone assembleHap --analyze=normal --parallel --incremental --daemon
set "HVIGOR_ERR=%errorlevel%"
if exist entry\build\default\outputs\default\entry-default-signed.hap (
  echo SIGNED_HAP_OK entry\build\default\outputs\default\entry-default-signed.hap
  exit /b 0
)
echo HAP_MISSING (hvigor exit=%HVIGOR_ERR%; hvigor may log BUILD FAILED but still exit 0)
exit /b 1
