
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

rem This script is used to invoke various server-side processes.  It should not
rem be invoked directly by end users.

setlocal
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
set INSTANCE_ROOT=%DIR_HOME%

if "%OPENDS_INVOKE_CLASS%" == "" goto noInvokeClass

set OLD_SCRIPT_NAME=%SCRIPT_NAME%
set SCRIPT_NAME=%OLD_SCRIPT_NAME%.online

set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTANCE_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%

set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=%OLD_SCRIPT_NAME%"

rem Check whether is local or remote
"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%DIR_HOME%\config\config.ldif" --testIfOffline %*  
if %errorlevel% == 51 goto launchoffline
if %errorlevel% == 52 goto launchonline
exit /B %errorlevel%

:noInvokeClass
echo Error:  OPENDS_INVOKE_CLASS environment variable is not set.
pause
goto end

:launchonline

"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%DIR_HOME%\config\config.ldif" %*

goto end

:launchoffline
set SCRIPT_NAME=%OLD_SCRIPT_NAME%.offline

set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTANCE_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%
set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=%OLD_SCRIPT_NAME%"

"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%DIR_HOME%\config\config.ldif" %*

goto end

:end

