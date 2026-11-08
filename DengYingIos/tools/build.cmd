@echo off
REM 构建入口（本机默认 java 是 1.8，AGP 8.13 需要 JDK 11+）。
REM 用法： tools\build.cmd :app:testDebugUnitTest
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-18.0.2.1
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\workspace\dengY\DengYing
call gradlew.bat %* --console=plain
