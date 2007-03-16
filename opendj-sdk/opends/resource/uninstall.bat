
@echo off
rem CDDL HEADER START
rem
rem The contents of this file are subject to the terms of the
rem Common Development and Distribution License, Version 1.0 only
rem (the "License").  You may not use this file except in compliance
rem with the License.
rem
rem You can obtain a copy of the license at
rem trunk/opends/resource/legal-notices/OpenDS.LICENSE
rem or https://OpenDS.dev.java.net/OpenDS.LICENSE.
rem See the License for the specific language governing permissions
rem and limitations under the License.
rem
rem When distributing Covered Code, include this CDDL HEADER in each
rem file and include the License file at
rem trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
rem add the following below this CDDL HEADER, with the fields enclosed
rem by brackets "[]" replaced with your own identifying information:
rem      Portions Copyright [yyyy] [name of copyright owner]
rem
rem CDDL HEADER END
rem
rem
rem      Portions Copyright 2006-2007 Sun Microsystems, Inc.

setlocal

set DIR_HOME=%~dP0.
set INSTANCE_ROOT=%DIR_HOME%

:checkJavaBin
if "%JAVA_BIN%" == "" goto noJavaBin
goto setClassPath

:noJavaBin
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noJavaHome
if not exist "%DIR_HOME%\bat\set-java-home.bat" goto noSetJavaHome
call "%DIR_HOME%\bat\set-java-home.bat"
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noSetJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 installation.
goto end

:setClassPath
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\bat\setcp.bat" %%x

set PATH=%SystemRoot%

if "%*" == "" goto callLaunch
goto callJava

:callLaunch
"%DIR_HOME%\lib\winlauncher.exe" launch "%DIR_HOME%" "%JAVA_BIN%" %JAVA_ARGS% org.opends.quicksetup.uninstaller.UninstallLauncher
goto end

:callJava
"%JAVA_BIN%" %JAVA_ARGS% org.opends.quicksetup.uninstaller.UninstallLauncher %*
goto end

:end
