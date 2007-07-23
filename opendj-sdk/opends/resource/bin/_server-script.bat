
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

set DIR_HOME=%~dPs0..
set INSTANCE_ROOT=%DIR_HOME%

if "%OPENDS_INVOKE_CLASS%" == "" goto noInvokeClass
goto checkJavaBin

:noInvokeClass
echo Error:  OPENDS_INVOKE_CLASS environment variable is not set.
pause
goto end

:checkJavaBin
if "%JAVA_BIN%" == "" goto noJavaBin
goto setClassPath

:noJavaBin
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noJavaHome
if not exist "%DIR_HOME%\lib\set-java-home.bat" goto noSetJavaHome
call "%DIR_HOME%\lib\set-java-home.bat"
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noSetJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 (or later) installation.
pause
goto end

:setClassPath
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\lib\setcp.bat" %%x

set PATH=%SystemRoot%

"%JAVA_BIN%" %JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%DIR_HOME%\config\config.ldif" %*


:end

