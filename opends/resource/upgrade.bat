
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

set DIR_HOME=%~dP0.
set INSTANCE_ROOT=%DIR_HOME%

:checkJavaBin
if "%JAVA_BIN%" == "" goto noJavaBin
if "%*" == "" goto callWebStartUpgrade
goto callExtractor

:noJavaBin
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
set jAVAWS_BIN=%JAVA_HOME%\bin\javaws.exe
if "%*" == "" goto callWebStartUpgrade
goto callExtractor

:noJavaHome
if not exist "%DIR_HOME%\bat\set-java-home.bat" goto noSetJavaHome
call "%DIR_HOME%\bat\set-java-home.bat"
set JAVA_BIN=%JAVA_HOME%\bin\java.exe
set jAVAWS_BIN=%JAVA_HOME%\bin\javaws.exe
if "%*" == "" goto callWebStartUpgrade
goto callExtractor

:noSetJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 installation.
goto end

set PATH=%SystemRoot%

if "%*" == "" goto callWebStartUpgrade

:callExtractor
if EXIST .\tmp\upgrade rd .\tmp\upgrade /s /q
set CLASSPATH=""
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\bat\setcp.bat" %%x
"%JAVA_BIN%" org.opends.quicksetup.upgrader.BuildExtractor %*
if %ERRORLEVEL%==0 goto callUpgrader
goto end

:callWebStartUpgrade
set JAVAWS_VM_ARGS=-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005 -Dorg.opends.quicksetup.upgrader.Root="%DIR_HOME%"
rem set JAVAWS_VM_ARGS=-Dorg.opends.quicksetup.upgrader.Root="%DIR_HOME%"
if "%OPENDS_UPGRADE_JNLP%" == "" set OPENDS_UPGRADE_JNLP=http://build.opends.org/install/QuickUpgrade.jnlp
"%JAVAWS_BIN%" "%OPENDS_UPGRADE_JNLP%"
goto end

:callUpgrader
set CLASSPATH=""
FOR %%x in ("%DIR_HOME%\tmp\upgrade\lib\*.jar") DO call "%DIR_HOME%\bat\setcp.bat" %%x
"%JAVA_BIN%" org.opends.quicksetup.upgrader.UpgradeLauncher %*
if EXIST .\tmp\upgrade rd .\tmp\upgrade /s /q
goto end

:end
