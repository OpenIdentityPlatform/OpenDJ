
@echo off
rem The contents of this file are subject to the terms of the Common Development and
rem Distribution License (the License). You may not use this file except in compliance with the
rem License.
rem
rem You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
rem specific language governing permission and limitations under the License.
rem
rem When distributing Covered Software, include this CDDL Header Notice in each file and include
rem the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
rem Header, with the fields enclosed by brackets [] replaced by your own identifying
rem information: "Portions Copyright [year] [name of copyright owner]".
rem
rem Copyright 2006-2010 Sun Microsystems, Inc.
rem Portions Copyright 2011-2014 ForgeRock AS.
rem Portions Copyright 2025 3A Systems LLC.

setlocal
set DIR_HOME=%~dp0..
set INSTALL_ROOT=%DIR_HOME%

set INSTANCE_DIR=
if exist "%INSTALL_ROOT%\instance.loc" (
  set /p INSTANCE_DIR=<"%INSTALL_ROOT%\instance.loc"
) else (
set INSTANCE_DIR=.
)
set CUR_DIR=%CD%
cd /d %INSTALL_ROOT%
cd /d %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd /d %CUR_DIR%
set TEMP_LOG=%TEMP%\logs\

if NOT EXIST "%INSTANCE_ROOT%\logs\" (
  if NOT EXIST "%TEMP_LOG%" (
    md "%TEMP_LOG%"
  )
  set LOG="%TEMP_LOG%native-windows.out"
) ELSE (
  set LOG="%INSTANCE_ROOT%\logs\native-windows.out"
)
set SCRIPT=start-ds.bat

echo %SCRIPT%: invoked >> %LOG%

set SCRIPT_NAME=start-ds

rem Set environment variables
set SCRIPT_UTIL_CMD=set-full-server-environment-and-test-java
call "%INSTALL_ROOT%\lib\_script-util.bat" %*

set ERROR_CODE=%errorlevel%
if NOT %ERROR_CODE% == 0 goto exitErrorCode

echo %SCRIPT%: CLASSPATH=%CLASSPATH% >> %LOG%

echo %SCRIPT%: PATH=%PATH% >> %LOG%

rem cleanup the tmp directory
set CUR_DIR=%CD%
set OPENDJ_TMP_DIR=%INSTANCE_ROOT%\tmp
dir /b /s /a %OPENDJ_TMP_DIR% | findstr .>nul && (
    cd /d %OPENDJ_TMP_DIR%
    for /F "delims=" %%i in ('dir /b') do (rmdir "%%i" /s/q>NUL 2>&1 || del "%%i" /s/q>NUL 2>&1)
    cd /d %CUR_DIR%
)

"%OPENDJ_JAVA_BIN%" -client %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configFile "%INSTANCE_ROOT%\config\config.ldif" --checkStartability %*

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
echo %SCRIPT%: Server already started >> %LOG%
set ERROR_CODE=0
goto exitErrorCode

:runNoDetach
echo %SCRIPT%: Run no detach >> %LOG%
echo. > "%INSTANCE_ROOT%\logs\server.out"
echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat %SCRIPT%" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
set ERROR_CODE=%errorlevel%
goto exitErrorCode

:runNoDetachQuiet
echo %SCRIPT%: Run no detach quiet >> %LOG%
echo. > "%INSTANCE_ROOT%\logs\server.out"
echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat %SCRIPT%" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configFile "%INSTANCE_ROOT%\config\config.ldif" %* >> %LOG%
set ERROR_CODE=%errorlevel%
goto exitErrorCode

:runDetach
echo %SCRIPT%: Run detach >> %LOG%
echo. > "%INSTANCE_ROOT%\logs\server.out"
echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%INSTALL_ROOT%\lib\winlauncher.exe" start "%INSTANCE_ROOT%" "%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS%  %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.starting" to be deleted >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.starting" --logFile "%INSTANCE_ROOT%\logs\server.out" %*
goto checkStarted

:runDetachQuiet
echo %SCRIPT%: Run detach quiet >> %LOG%
echo. > "%INSTANCE_ROOT%\logs\server.out"
echo. > "%INSTANCE_ROOT%\logs\server.starting"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%INSTALL_ROOT%\lib\winlauncher.exe" start "%INSTANCE_ROOT%" "%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS%  %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.starting" to be deleted >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.starting" --logFile "%INSTANCE_ROOT%\logs\server.out" %* >> %LOG%
goto checkStarted

:runDetachCalledByWinService
rem We write the output of the start command to the winservice.out file.
echo %SCRIPT%: Run detach called by windows service >> %LOG%
echo. > "%INSTANCE_ROOT%\logs\server.out"
echo. > "%INSTANCE_ROOT%\logs\server.starting"
echo. > "%INSTANCE_ROOT%\logs\server.startingservice"
echo. > "%INSTANCE_ROOT%\logs\winservice.out"
if exist "%INSTANCE_ROOT%\lib\set-java-args.bat" DO call "%INSTANCE_ROOT%\lib\set-java-args.bat"
"%INSTALL_ROOT%\lib\winlauncher.exe" start "%INSTANCE_ROOT%" "%OPENDJ_JAVA_BIN%" -Xrs %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configFile "%INSTANCE_ROOT%\config\config.ldif" %*
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.starting" to be deleted >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.starting" --logFile "%INSTANCE_ROOT%\logs\server.out" --outputFile "%INSTANCE_ROOT%\logs\winservice.out" %*
erase "%INSTANCE_ROOT%\logs\server.startingservice"
goto checkStarted

:runAsService
echo %SCRIPT%: Run as service >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.StartWindowsService
echo %SCRIPT%: Waiting for "%INSTANCE_ROOT%\logs\server.startingservice" to be deleted >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.startingservice" %*
rem Type the contents the winwervice.out file and delete it.
if exist "%INSTANCE_ROOT%\logs\winservice.out" type "%INSTANCE_ROOT%\logs\winservice.out"
if exist "%INSTANCE_ROOT%\logs\winservice.out" erase "%INSTANCE_ROOT%\logs\winservice.out"
goto end

:checkStarted
echo %SCRIPT%: check started >> %LOG%
"%OPENDJ_JAVA_BIN%" -client %SCRIPT_NAME_ARG% org.opends.server.core.DirectoryServer --configFile "%INSTANCE_ROOT%\config\config.ldif" --checkStartability > NUL 2>&1
if %errorlevel% == 98 goto serverStarted
if %errorlevel% == 101 goto serverStarted
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
if "%OPENDJ_EXIT_NO_BACKGROUND%" == "true" exit %ERROR_CODE%
exit /B %ERROR_CODE%

:end
echo %SCRIPT%: finished >> %LOG%
