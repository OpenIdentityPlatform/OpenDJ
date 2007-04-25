
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

set PATH=%SystemRoot%

set OPENDS_INVOKE_CLASS="org.opends.server.tools.StopDS"
set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=stop-ds"
set DIR_HOME=%~dP0..

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
echo        Please set it to a valid Java 5 (or later) installation.
goto end

:noValidJavaHome
echo ERROR:  The detected Java version could not be used.  Please set 
echo         JAVA_HOME to to a valid Java 5 (or later) installation.
goto end

:setClassPath
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\bat\setcp.bat" %%x

rem Test that the provided JDK is 1.5 compatible.
"%JAVA_BIN%" org.opends.server.tools.InstallDS -t > NUL 2>&1
if not %errorlevel% == 0 goto noValidJavaHome

"%JAVA_BIN%" -Xms8M -Xmx8M %SCRIPT_NAME_ARG%  org.opends.server.tools.StopDS --checkStoppability %*

if %errorlevel% == 98 goto serverAlreadyStopped
if %errorlevel% == 99 goto startUsingSystemCall
if %errorlevel% == 100 goto stopUsingSystemCall
if %errorlevel% == 101 goto restartUsingSystemCall
if %errorlevel% == 102 goto stopUsingProtocol
if %errorlevel% == 103 goto stopAsWindowsService
if %errorlevel% == 104 goto restartAsWindowsService
rem An error or we display usage
goto end

:serverAlreadyStopped
if exist "%DIR_HOME%\logs\server.pid" erase "%DIR_HOME%\logs\server.pid"
goto end

:startUsingSystemCall
"%DIR_HOME%\bat\start-ds.bat"
goto end

:stopUsingSystemCall
"%DIR_HOME%\lib\winlauncher.exe" stop "%DIR_HOME%"
goto end

:restartUsingSystemCall
"%DIR_HOME%\lib\winlauncher.exe" stop "%DIR_HOME%"
if not %errorlevel% == 0 goto end
goto startUsingSystemCall

:stopUsingProtocol
call "%~dP0\_client-script.bat" %*
goto end

:stopAsWindowsService
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.StopWindowsService
goto end

:restartAsWindowsService
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.StopWindowsService
if not %errorlevel% == 0 goto end
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.StartWindowsService
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.WaitForFileDelete --targetFile "%DIR_HOME%\logs\server.startingservice"
rem Type the contents the winwervice.out file and delete it.
if exist "%DIR_HOME%\logs\winservice.out" type "%DIR_HOME%\logs\winservice.out"
if exist "%DIR_HOME%\logs\winservice.out" erase "%DIR_HOME%\logs\winservice.out"
goto end

:end

