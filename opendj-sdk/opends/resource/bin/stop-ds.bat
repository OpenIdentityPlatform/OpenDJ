
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

set PATH=%SystemRoot%

set OPENDS_INVOKE_CLASS="org.opends.server.tools.StopDS"
set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=stop-ds"
set DIR_HOME=%~dP0..

:checkJavaBin
if "%JAVA_BIN%" == "" goto noJavaBin
goto setClassPath

:noJavaBin
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noJavaHome
if not exist "%DIR_HOME%\bin\set-java-home.bat" goto noSetJavaHome
call "%DIR_HOME%\bin\set-java-home.bat"
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noSetJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 installation.
goto end

:setClassPath
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\bin\setcp.bat" %%x


set RESTART=0
set NO_ARG_OR_ONLY_RESTART=1

for %%x in (%*) DO set NO_ARG_OR_ONLY_RESTART=0
if "%NO_ARG_OR_ONLY_RESTART%" == "1" goto execute

for %%x in (%*) DO if "%%x" == "-R" set RESTART=1
for %%x in (%*) DO if "%%x" == "--restart" set RESTART=1

goto testParameter1

:testParameter1
if not "%1" == "-R" goto testParameter1b
goto testParameter2

:testParameter1b
if not "%1" == "--restart" goto execute
goto testParameter2

:testParameter2
if "%2" == "" set NO_ARG_OR_ONLY_RESTART=1
if "%NO_ARG_OR_ONLY_RESTART%" == "1" goto execute
if not "%2" == "-R" goto testParameter2b
goto testParameter3

:testParameter2b
if not "%2" == "--restart" goto execute
goto testParameter3

:testParameter3
if not "%3" == "" goto execute
set NO_ARG_OR_ONLY_RESTART=1
goto execute

:execute
if "%NO_ARG_OR_ONLY_RESTART%" == "0" goto stopWithLDAP

rem  	Use the code in StopDS class to know if the server is already
rem  	stopped.  An exit code of 99 means that the server is stopped.
"%JAVA_BIN%" %JAVA_ARGS% %SCRIPT_NAME_ARG%  org.opends.server.tools.StopDS --checkStoppability
if %errorlevel% == 99 goto serverStopped

if not exist "%DIR_HOME%\logs\server.pid" goto stopWithLDAP
"%DIR_HOME%\lib\winlauncher.exe" stop "%DIR_HOME%"
if not %errorlevel% == 0 goto end

:serverStopped
if exist "%DIR_HOME%\logs\server.pid" erase "%DIR_HOME%\logs\server.pid"
if "%RESTART%" == "1" "%DIR_HOME%\bin\start-ds.bat"
goto end

:stopWithLDAP
call "%~dP0\_client-script.bat" %*

:end

