
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

rem This script is used to invoke various server-side processes.  It should not
rem be invoked directly by end users.

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

if "%OPENDJ_INVOKE_CLASS%" == "" (
  echo Error:  OPENDJ_INVOKE_CLASS environment variable is not set.
  pause
  goto end
)

set ORIGIN_SCRIPT_NAME=%SCRIPT_NAME%
set SCRIPT_NAME=%ORIGIN_SCRIPT_NAME%.online
rem Check whether is local or remote
FOR %%x in (%*) DO (
  if /I "%%x" == "--offline" (
    set SCRIPT_NAME=%ORIGIN_SCRIPT_NAME%.offline
  )
)

set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTALL_ROOT%\lib\_script-util.bat" %*
if NOT %errorlevel% == 0 exit /B %errorlevel%

set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=%ORIGIN_SCRIPT_NAME%"

"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDJ_INVOKE_CLASS% --configFile "%INSTANCE_ROOT%\config\config.ldif" %*

:end

