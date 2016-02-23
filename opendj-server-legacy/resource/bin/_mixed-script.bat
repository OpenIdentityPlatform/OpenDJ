
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
rem Portions Copyright 2011-2012 ForgeRock AS.

rem This script is used to invoke various server-side processes.  It should not
rem be invoked directly by end users.

setlocal
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
set INSTALL_ROOT=%DIR_HOME%

set INSTANCE_DIR=
if exist "%INSTALL_ROOT%\instance.loc" (
  set /p INSTANCE_DIR=<%INSTALL_ROOT%\instance.loc
) else (
set INSTANCE_DIR=.
)
set CUR_DIR=%CD%
cd /d %INSTALL_ROOT%
cd /d %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd /d %CUR_DIR%

if "%OPENDJ_INVOKE_CLASS%" == "" goto noInvokeClass

set OLD_SCRIPT_NAME=%SCRIPT_NAME%
set SCRIPT_NAME=%OLD_SCRIPT_NAME%.online

rem We keep this values to reset the environment before calling _script-util.bat.
set ORIGINAL_JAVA_ARGS=%OPENDJ_JAVA_ARGS%
set ORIGINAL_JAVA_HOME=%OPENDJ_JAVA_HOME%
set ORIGINAL_JAVA_BIN=%OPENDJ_JAVA_BIN%

set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTALL_ROOT%\lib\_script-util.bat" %*
if NOT %errorlevel% == 0 exit /B %errorlevel%

set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=%OLD_SCRIPT_NAME%"

rem Check whether is local or remote
"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDJ_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" --testIfOffline %*
if %errorlevel% == 51 goto launchoffline
if %errorlevel% == 52 goto launchonline
exit /B %errorlevel%

:noInvokeClass
echo Error:  OPENDJ_INVOKE_CLASS environment variable is not set.
pause
goto end

:launchonline

"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDJ_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*

goto end

:launchoffline
set SCRIPT_NAME=%OLD_SCRIPT_NAME%.offline

rem Set the original values that the user had on the environment in order to be
rem sure that the script works with the proper arguments (in particular
rem if the user specified not to overwrite the environment).
set OPENDJ_JAVA_ARGS=%ORIGINAL_JAVA_ARGS%
set OPENDJ_JAVA_HOME=%ORIGINAL_JAVA_HOME%
set OPENDJ_JAVA_BIN=%ORIGINAL_JAVA_BIN%

set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTALL_ROOT%\lib\_script-util.bat" %*
if NOT %errorlevel% == 0 exit /B %errorlevel%
set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=%OLD_SCRIPT_NAME%"

"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_ARGS% %SCRIPT_NAME_ARG% %OPENDJ_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%INSTANCE_ROOT%\config\config.ldif" %*

goto end

:end

