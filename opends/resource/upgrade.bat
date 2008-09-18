
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
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi.

set INSTALL_ROOT=%DIR_HOME%
set INSTANCE_DIR=
for /f "delims=" %%a in (%INSTALL_ROOT%\instance.loc) do (
  set INSTANCE_DIR=%%a
)
set CUR_DIR=%~dp0
cd %INSTALL_ROOT%
cd %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd %CUR_DIR%

:checkNewVersion
if exist "upgrade.bat.NEW" goto newVersion

set SCRIPT_NAME=upgrade

rem Set environment variables and test java
set SCRIPT_UTIL_CMD=set-full-environment-and-test-java
call "%INSTALL_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%

:callExtractor
if EXIST "%INSTANCE_ROOT%\tmp\upgrade" rd "%INSTANCE_ROOT%\tmp\upgrade" /s /q
set CLASSPATH=""
FOR %%x in ("%INSTALL_ROOT%\lib\*.jar") DO call "%INSTALL_ROOT%\lib\setcp.bat" %%x
set CLASSPATH=%INSTANCE_ROOT%\classes;%CLASSPATH%
"%OPENDS_JAVA_BIN%" %SCRIPT_NAME_ARG% org.opends.quicksetup.upgrader.BuildExtractor %*
if %errorlevel% == 99 goto upgrader
if %errorlevel% == 98 goto reverter
if %errorlevel% == 50 goto version
if %errorlevel% == 0 goto end
goto error

:newVersion
echo A new version of this script was made available by the last upgrade
echo operation.  Delete this old version and rename file 'upgrade.bat.NEW'
echo to 'upgrade.bat' before continuing.
goto end

:upgrader
set CLASSPATH=""
FOR %%x in ("%INSTANCE_ROOT%\tmp\upgrade\lib\*.jar") DO call "%INSTALL_ROOT%\lib\setcp.bat" %%x
"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% -DINSTALL_ROOT=%INSTALL_ROOT% org.opends.quicksetup.upgrader.UpgradeLauncher %*
goto end

:reverter
if EXIST "%INSTANCE_ROOT%\tmp\revert" rd "%INSTANCE_ROOT%\tmp\revert" /s /q
xcopy "%INSTALL_ROOT%\lib\*.*" "%INSTANCE_ROOT%\tmp\revert\lib\" /E /Q /Y
set CLASSPATH=""
FOR %%x in ("%INSTANCE_ROOT%\tmp\revert\lib\*.jar") DO call "%INSTALL_ROOT%\lib\setcp.bat" %%x
"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% -DINSTALL_ROOT=%INSTALL_ROOT% org.opends.quicksetup.upgrader.ReversionLauncher %*
goto end

:version
rem version information was requested. Return code should be 0.
exit /B 0

:error
exit /B 101

:end
