
@echo off
rem CDDL HEADER START
rem
rem The contents of this file are subject to the terms of the
rem Common Development and Distribution License, Version 1.0 only
rem (the "License").  You may not use this file except in compliance
rem with the License.
rem
rem You can obtain a copy of the license at
rem legal-notices/CDDLv1_0.txt
rem or http://forgerock.org/license/CDDLv1.0.html.
rem See the License for the specific language governing permissions
rem and limitations under the License.
rem
rem When distributing Covered Code, include this CDDL HEADER in each
rem file and include the License file at legal-notices/CDDLv1_0.txt.
rem legal-notices/CDDLv1_0.txt.  If applicable,
rem add the following below this CDDL HEADER, with the fields enclosed
rem by brackets "[]" replaced with your own identifying information:
rem      Portions Copyright [yyyy] [name of copyright owner]
rem
rem CDDL HEADER END
rem
rem
rem      Copyright 2006-2009 Sun Microsystems, Inc.

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

