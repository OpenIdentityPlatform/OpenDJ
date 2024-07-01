
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
rem Copyright 2006-2009 Sun Microsystems, Inc.

rem This script is used to invoke various client-side processes.  It should not
rem be invoked directly by end users.

setlocal
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
set INSTALL_ROOT=%DIR_HOME%

if "%NO_CHECK%" == "" set NO_CHECK=true

if "%OPENDJ_INVOKE_CLASS%" == "" goto noInvokeClass
goto launchCommand

:noInvokeClass
echo Error:  OPENDJ_INVOKE_CLASS environment variable is not set.
pause
goto end

:launchCommand
set SCRIPT_UTIL_CMD=set-full-environment
call "%INSTALL_ROOT%\lib\_script-util.bat" $*
if NOT %errorlevel% == 0 exit /B %errorlevel%

"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDJ_INVOKE_CLASS% %*

:end

