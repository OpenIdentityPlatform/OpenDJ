
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
cd %INSTALL_ROOT%
cd %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd %CUR_DIR%


set SCRIPT_NAME=status-panel

rem Set environment variables
set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTALL_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%

if "%~1" == "" goto callLaunch
goto callJava

:callLaunch
if exist "%INSTALL_ROOT%\lib\set-java-args.bat" DO call "%INSTALL_ROOT%\lib\set-java-args.bat"
"%INSTALL_ROOT%\lib\winlauncher.exe" launch "%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.guitools.statuspanel.StatusPanelLauncher
goto end

:callJava
if exist "%INSTALL_ROOT%\lib\set-java-args.bat" DO call "%INSTALL_ROOT%\lib\set-java-args.bat"
"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.guitools.statuspanel.StatusPanelLauncher %*

rem return part
if %errorlevel% == 50 goto version
goto end

:version
rem version information was requested. Return code should be 0.
exit /B 0

:end
