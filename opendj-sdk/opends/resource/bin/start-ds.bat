
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
rem      Copyright 2006-2008 Sun Microsystems, Inc.

setlocal
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
set INSTALL_ROOT=%DIR_HOME%

set INSTANCE_DIR=
for /f "delims=" %%a in (%DIR_HOME%\instance.loc) do (
  set INSTANCE_DIR=%%a
)
set CUR_DIR=%~dp0
cd /d %INSTALL_ROOT%
cd /d %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd /d %CUR_DIR%

set LOG="%INSTANCE_ROOT%\logs\native-windows.out"
set SCRIPT=start-ds.bat

echo %SCRIPT%: invoked >> %LOG%

set SCRIPT_NAME=start-ds

rem Set environment variables
set SCRIPT_UTIL_CMD=set-full-environment-and-test-java
call "%INSTALL_ROOT%\lib\_script-util.bat"

set ERROR_CODE=%errorlevel%
if NOT %ERROR_CODE% == 0 goto exitErrorCode

echo %SCRIPT%: CLASSPATH=%CLASSPATH% >> %LOG%

set PATH=%SystemRoot%

echo %SCRIPT%: PATH=%PATH% >> %LOG%

"%OPENDS_JAVA_BIN%" -Xms8M -Xmx8M %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" --checkStartability %*

if %errorlevel% == 98 goto serverAlreadyStarted
if %errorlevel% == 99 goto runDetach
if %errorlevel% == 100 goto runNoDetach
if %errorlevel% == 101 goto runAsService
if %errorlevel% == 102 goto runDetachCalledByWinService
if %errorlevel% == 103 goto runDetachQuiet
if %errorlevel% == 104 goto runNoDetachQuiet
set ERROR_CODE=%errorlevel%
goto exitErrorCode

:serverAlreadyStarted
echo %SCRIPT%: Server already started  >> %LOG%
set ERROR_CODE=0
goto exitErrorCode

:runNoDetach
echo %SCRIPT%: Run no detach  >> %LOG%
if not exist "%INSTANCE_ROOT%\logs\server.out" echo. > "%INSTANCE_ROOT%\logs\server.out"
if not exist "%INSTANCE_ROOT%\logs\server.starting" echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat %SCRIPT%" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%OPENDS_JAVA_BIN%" %OPENDS_SERVER_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
set ERROR_CODE=%errorlevel%
goto exitErrorCode

:runNoDetachQuiet
echo %SCRIPT%: Run no detach  >> %LOG%
if not exist "%INSTANCE_ROOT%\logs\server.out" echo. > "%INSTANCE_ROOT%\logs\server.out"
if not exist "%INSTANCE_ROOT%\logs\server.starting" echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat %SCRIPT%" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%OPENDS_JAVA_BIN%" %OPENDS_SERVER_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %* >> %LOG%
set ERROR_CODE=%errorlevel%
goto exitErrorCode

:runDetach
echo %SCRIPT%: Run detach  >> %LOG%
if not exist "%INSTANCE_ROOT%\logs\server.out" echo. > "%INSTANCE_ROOT%\logs\server.out"
if not exist "%INSTANCE_ROOT%\logs\server.starting" echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%INSTALL_ROOT%\lib\winlauncher.exe" start "%INSTANCE_ROOT%" "%OPENDS_JAVA_BIN%" %OPENDS_SERVER_JAVA_ARGS%  %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.out" to be deleted >> %LOG%
"%OPENDS_JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.starting" --logFile "%INSTANCE_ROOT%\logs\server.out"
goto checkStarted

:runDetachQuiet
echo %SCRIPT%: Run detach  >> %LOG%
if not exist "%INSTANCE_ROOT%\logs\server.out" echo. > "%INSTANCE_ROOT%\logs\server.out"
if not exist "%INSTANCE_ROOT%\logs\server.starting" echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%INSTALL_ROOT%\lib\winlauncher.exe" start "%INSTANCE_ROOT%" "%OPENDS_JAVA_BIN%" %OPENDS_SERVER_JAVA_ARGS%  %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.out" to be deleted >> %LOG%
"%OPENDS_JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.starting" --logFile "%INSTANCE_ROOT%\logs\server.out" >> %LOG%
goto checkStarted

:runDetachCalledByWinService
rem We write the output of the start command to the winservice.out file.
echo %SCRIPT%: Run detach called by windows service  >> %LOG%
if not exist "%INSTANCE_ROOT%\logs\server.out" echo. > "%INSTANCE_ROOT%\logs\server.out"
if not exist "%INSTANCE_ROOT%\logs\server.starting" echo. > "%INSTANCE_ROOT%\logs\server.starting"
echo. > "%INSTANCE_ROOT%\logs\server.startingservice"
echo. > "%INSTANCE_ROOT%\logs\winservice.out"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%INSTALL_ROOT%\lib\winlauncher.exe" start "%INSTANCE_ROOT%" "%OPENDS_JAVA_BIN%" -Xrs %OPENDS_SERVER_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.out" to be deleted >> %LOG%
"%OPENDS_JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.starting" --logFile "%INSTANCE_ROOT%\logs\server.out" --outputFile "%INSTANCE_ROOT%\logs\winservice.out"
erase "%INSTANCE_ROOT%\logs\server.startingservice"
goto checkStarted

:runAsService
echo %SCRIPT%: Run as service >> %LOG%
"%OPENDS_JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.StartWindowsService
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.startingservice" to be deleted >> %LOG%
"%OPENDS_JAVA_BIN%" -Xms8M -Xmx8M org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.startingservice"
rem Type the contents the winwervice.out file and delete it.
if exist "%INSTANCE_ROOT%\logs\winservice.out" type "%INSTANCE_ROOT%\logs\winservice.out"
if exist "%INSTANCE_ROOT%\logs\winservice.out" erase "%INSTANCE_ROOT%\logs\winservice.out"
goto end

:checkStarted
"%OPENDS_JAVA_BIN%" -Xms8M -Xmx8M %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" --checkStartability > NUL 2>&1
if %errorlevel% == 98 goto serverStarted
goto serverNotStarted

:serverStarted
echo %SCRIPT%: finished >> %LOG%
set ERROR_CODE=0
goto exitErrorCode

:serverNotStarted
echo %SCRIPT%: finished >> %LOG%
set ERROR_CODE=1
goto exitErrorCode

:exitErrorCode
if "%OPENDS_EXIT_NO_BACKGROUND%" == "true" exit %ERROR_CODE%
exit /B %ERROR_CODE%

:end
echo %SCRIPT%: finished >> %LOG%
