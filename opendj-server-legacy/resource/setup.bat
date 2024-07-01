
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
rem Portions Copyright 2011-2016 ForgeRock AS.

setlocal

rem check that the path does not contain the ^% character which breaks
rem the batch files.
set NON_ESCAPED=%~dp0..


FOR /F "tokens=1-2* delims=%%" %%1 IN ("%NON_ESCAPED%") DO (
if NOT "%%2" == "" goto invalidPath)

set DIR_HOME=%~dp0.

set INSTALL_ROOT=%DIR_HOME%
set INSTANCE_DIR=
if exist "%INSTALL_ROOT%\instance.loc" (
  set /p INSTANCE_DIR=<"%INSTALL_ROOT%\instance.loc"
) else (
set INSTANCE_DIR=.
)
if not exist "%INSTANCE_DIR%" mkdir "%INSTANCE_DIR%"

set CUR_DIR=%CD%
cd /d %INSTALL_ROOT%
cd /d %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd /d %CUR_DIR%

set SCRIPT_NAME=setup

rem Set environment variables and test java
set SCRIPT_UTIL_CMD=set-full-environment-and-test-java
call "%INSTALL_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%

if "%~1" == "" goto callLaunch
goto callJava

:invalidPath
echo Error: The current path contains a %% character.  OpenDJ cannot
echo        be installed on a path containing this character.
pause
goto end

:callLaunch
"%INSTALL_ROOT%\lib\winlauncher.exe" launch "%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.quicksetup.installer.SetupLauncher
goto end

:callJava
"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.quicksetup.installer.SetupLauncher %*

rem return part
if %errorlevel% == 50 goto version
if NOT %errorlevel% == 0 exit /B %errorlevel%
goto end

:version
rem version information was requested. Return code should be 0.
exit /B 0

:end
