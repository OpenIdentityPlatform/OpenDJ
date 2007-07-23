
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
goto callExtractor

:noJavaBin
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto callExtractor

:noJavaHome
if not exist "%INSTANCE_ROOT%\lib\set-java-home.bat" goto noSetJavaHome
call "%INSTANCE_ROOT%\lib\set-java-home.bat"
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto callExtractor

:noSetJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 (or later) installation.
pause
goto end

:noValidJavaHome
echo ERROR:  The detected Java version could not be used.  Please set 
echo         JAVA_HOME to to a valid Java 5 (or later) installation.
pause
goto end

set PATH=%SystemRoot%

rem Test that the provided JDK is 1.5 compatible.
"%JAVA_BIN%" org.opends.server.tools.InstallDS -t > NUL 2>&1
if not %errorlevel% == 0 goto noValidJavaHome

:callExtractor
if EXIST "%INSTANCE_ROOT%\tmp\upgrade" rd "%INSTANCE_ROOT%\tmp\upgrade" /s /q
set CLASSPATH=""
FOR %%x in ("%INSTANCE_ROOT%\lib\*.jar") DO call "%INSTANCE_ROOT%\lib\setcp.bat" %%x
set CLASSPATH=%DIR_HOME%\classes;%CLASSPATH%
"%JAVA_BIN%" org.opends.quicksetup.upgrader.BuildExtractor %*
if %errorlevel% == 50 goto version
if not %errorlevel% == 0 goto end
goto upgrader

:upgrader
set CLASSPATH=""
FOR %%x in ("%INSTANCE_ROOT%\tmp\upgrade\lib\*.jar") DO call "%INSTANCE_ROOT%\lib\setcp.bat" %%x
"%JAVA_BIN%" org.opends.quicksetup.upgrader.UpgradeLauncher %*
goto end

:version
rem version information was requested. Return code should be 0.
exit /B 0

:end
