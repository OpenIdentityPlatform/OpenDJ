
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
set DIR_HOME=%~dPs0..

set INSTANCE_ROOT=%DIR_HOME%

set LOG="%INSTANCE_ROOT%\logs\native-windows.out"
set SCRIPT=stop-ds.bat

rem This is the template to use for logging.  Make sure to use >>
rem echo %SCRIPT%: your-message-here >> %LOG%
echo %SCRIPT%: invoked >> %LOG%

:checkJavaBin
if "%JAVA_BIN%" == "" goto noJavaBin
goto setClassPath

:noJavaBin
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noJavaHome
if not exist "%DIR_HOME%\lib\set-java-home.bat" goto noSetJavaHome
call "%DIR_HOME%\lib\set-java-home.bat"
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noSetJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 (or later) installation.
echo %SCRIPT%: JAVA_HOME environment variable is not set. >> %LOG%
pause
goto end

:noValidJavaHome
echo %SCRIPT%: The detected Java version could not be used. JAVA_HOME=[%JAVA_HOME%] >> %LOG%
echo ERROR:  The detected Java version could not be used.  Please set 
echo         JAVA_HOME to to a valid Java 5 (or later) installation.
pause
goto end

:setClassPath
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\lib\setcp.bat" %%x

echo %SCRIPT%: CLASSPATH=%CLASSPATH% >> %LOG%

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
echo %SCRIPT%: server already stopped >> %LOG%
if exist "%DIR_HOME%\logs\server.pid" erase "%DIR_HOME%\logs\server.pid"
goto end

:startUsingSystemCall
echo %SCRIPT%: start using system call >> %LOG%
"%DIR_HOME%\bat\start-ds.bat"
goto end

:stopUsingSystemCall
echo %SCRIPT%: stop using system call >> %LOG%
"%DIR_HOME%\lib\winlauncher.exe" stop "%DIR_HOME%"
goto end

:restartUsingSystemCall
echo %SCRIPT%: restart using system call >> %LOG%
"%DIR_HOME%\lib\winlauncher.exe" stop "%DIR_HOME%"
if not %errorlevel% == 0 goto end
goto startUsingSystemCall

:stopUsingProtocol
echo %SCRIPT%: stop using protocol >> %LOG%
call "%DIR_HOME%\lib\_client-script.bat" %*
goto end

:stopAsWindowsService
echo %SCRIPT%: stop as windows service >> %LOG%
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.StopWindowsService
goto end

:restartAsWindowsService
echo %SCRIPT%: restart as windows service, stopping >> %LOG%
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.StopWindowsService
if not %errorlevel% == 0 goto end
echo %SCRIPT%: restart as windows service, starting >> %LOG%
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.StartWindowsService
"%JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.WaitForFileDelete --targetFile "%DIR_HOME%\logs\server.startingservice"
rem Type the contents the winwervice.out file and delete it.
if exist "%DIR_HOME%\logs\winservice.out" type "%DIR_HOME%\logs\winservice.out"
if exist "%DIR_HOME%\logs\winservice.out" erase "%DIR_HOME%\logs\winservice.out"
goto end

:end

echo %SCRIPT%: finished >> %LOG%
