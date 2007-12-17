
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

setlocal

rem check that the path does not contain the ^% character which breaks
rem the batch files.
for %%i in (%~sf0) do set NON_ESCAPED=%%~dPsi..


FOR /F "tokens=1-2* delims=%%" %%1 IN ("%NON_ESCAPED%") DO (
if NOT "%%2" == "" goto invalidPath)

for %%i in (%~sf0) do set DIR_HOME=%%~dPsi.

set INSTANCE_ROOT=%DIR_HOME%

set SCRIPT_NAME=setup

rem Set environment variables and test java
set SCRIPT_UTIL_CMD=set-full-environment-and-test-java
call "%INSTANCE_ROOT%\lib\_script-util.bat"
if NOT %errorlevel% == 0 exit /B %errorlevel%

if "%~1" == "" goto callLaunch
goto callJava

:invalidPath
echo Error: The current path contains a %% character.  OpenDS cannot
echo        be installed on a path containing this character.
pause
goto end

:callLaunch
"%DIR_HOME%\lib\winlauncher.exe" launch "%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.quicksetup.installer.SetupLauncher
goto end

:callJava
"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% %SCRIPT_NAME_ARG% org.opends.quicksetup.installer.SetupLauncher %*

rem return part
if %errorlevel% == 50 goto version
goto end

:version
rem version information was requested. Return code should be 0.
exit /B 0

:end
