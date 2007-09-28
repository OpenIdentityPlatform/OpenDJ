
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
goto checkOpenDSJavaBin

:noInvokeClass
echo Error:  OPENDS_INVOKE_CLASS environment variable is not set.
pause
goto end

:checkOpenDSJavaBin
if "%OPENDS_JAVA_BIN%" == "" goto checkOpenDSJavaHome
goto setClassPath

:checkOpenDSJavaHome
if "%OPENDS_JAVA_HOME%" == "" goto checkOpenDSJavaHomeFile
if not exist "%OPENDS_JAVA_HOME%\bin\java.exe" goto checkOpenDSJavaHomeFile
set OPENDS_JAVA_BIN=%OPENDS_JAVA_HOME%\bin\java.exe
goto setClassPath

:checkOpenDSJavaHomeFile
if not exist "%DIR_HOME%\lib\set-java-home.bat" goto checkJavaBin
call "%DIR_HOME%\lib\set-java-home.bat"
if not exist "%OPENDS_JAVA_HOME%\bin\java.exe" goto checkJavaBin
set OPENDS_JAVA_BIN=%OPENDS_JAVA_HOME%\bin\java.exe
goto setClassPath

:checkJavaBin
if "%JAVA_BIN%" == "" goto checkJavaHome
set OPENDS_JAVA_BIN=%JAVA_BIN%
goto setClassPath

:checkJavaHome
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
set OPENDS_JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto setClassPath

:noJavaHome
echo Error: OPENDS_JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 (or later) installation.
pause
goto end

:setClassPath
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\lib\setcp.bat" %%x

set PATH=%SystemRoot%

"%OPENDS_JAVA_BIN%" %JAVA_ARGS% %SCRIPT_NAME_ARG% %OPENDS_INVOKE_CLASS% --configClass org.opends.server.extensions.ConfigFileHandler --configFile "%DIR_HOME%\config\config.ldif" %*


:end

