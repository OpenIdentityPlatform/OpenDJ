
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

rem This script is used to invoke various server-side processes.  It should not
rem be invoked directly by end users.

setlocal
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
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

if "%OPENDS_INVOKE_CLASS%" == "" goto noInvokeClass

set OLD_SCRIPT_NAME=%SCRIPT_NAME%
set SCRIPT_NAME=%OLD_SCRIPT_NAME%.online

rem We keep this values to reset the environment before calling _script-util.bat.
set ORIGINAL_JAVA_ARGS=%OPENDS_JAVA_ARGS%
set ORIGINAL_JAVA_HOME=%OPENDS_JAVA_HOME%
set ORIGINAL_JAVA_BIN=%OPENDS_JAVA_BIN%

set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTALL_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%

set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=%OLD_SCRIPT_NAME%"

rem Check whether is local or remote
"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" --testIfOffline %*  
if %errorlevel% == 51 goto launchoffline
if %errorlevel% == 52 goto launchonline
exit /B %errorlevel%

:noInvokeClass
echo Error:  OPENDS_INVOKE_CLASS environment variable is not set.
pause
goto end

:launchonline

"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*

goto end

:launchoffline
set SCRIPT_NAME=%OLD_SCRIPT_NAME%.offline

rem Set the original values that the user had on the environment in order to be
rem sure that the script works with the proper arguments (in particular
rem if the user specified not to overwrite the environment).
set OPENDS_JAVA_ARGS=%ORIGINAL_JAVA_ARGS%
set OPENDS_JAVA_HOME=%ORIGINAL_JAVA_HOME%
set OPENDS_JAVA_BIN=%ORIGINAL_JAVA_BIN%

set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTALL_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%
set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=%OLD_SCRIPT_NAME%"

"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*

goto end

:end

