@echo off
setlocal enabledelayedexpansion

set GRADLE_VERSION=8.9
set WRAPPER_DIR=.gradle-wrapper
set ZIP_PATH=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set DIST_DIR=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%

if exist C:\hostedtoolcache\windows\Java_Temurin-Hotspot_jdk\17.0.14-7\x64 set JAVA_HOME=C:\hostedtoolcache\windows\Java_Temurin-Hotspot_jdk\17.0.14-7\x64

if not exist %WRAPPER_DIR% mkdir %WRAPPER_DIR%

if not exist %DIST_DIR% (
  if not exist %ZIP_PATH% (
    powershell -Command "Invoke-WebRequest -UseBasicParsing https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip -OutFile %ZIP_PATH%"
  )
  powershell -Command "Expand-Archive -Path %ZIP_PATH% -DestinationPath %WRAPPER_DIR% -Force"
)

call %DIST_DIR%\bin\gradle.bat %*