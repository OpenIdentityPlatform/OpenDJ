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
rem Copyright 2008-2009 Sun Microsystems, Inc.
rem Portions copyright 2013-2016 ForgeRock AS.

set SET_JAVA_HOME_AND_ARGS_DONE=false
set SET_ENVIRONMENT_VARS_DONE=false
set SET_CLASSPATH_DONE=false

if "%INSTALL_ROOT%" == "" goto setInstanceRoot

:scriptBegin
if "%SCRIPT_UTIL_CMD%" == "set-full-environment-and-test-java" goto setFullEnvironmentAndTestJava
if "%SCRIPT_UTIL_CMD%" == "set-full-environment" goto setFullEnvironment
if "%SCRIPT_UTIL_CMD%" == "set-java-home-and-args" goto setJavaHomeAndArgs
if "%SCRIPT_UTIL_CMD%" == "set_environment_vars" goto setEnvironmentVars
if "%SCRIPT_UTIL_CMD%" == "test-java" goto testJava
if "%SCRIPT_UTIL_CMD%" == "set-classpath" goto setClassPath
goto prepareCheck

:setInstanceRoot
setlocal
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
set INSTALL_ROOT=%DIR_HOME%
set CUR_DIR=%~dp0
cd /d %INSTALL_ROOT%
cd /d %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd /d %CUR_DIR%
goto scriptBegin


:setClassPath
if "%SET_CLASSPATH_DONE%" == "true" goto prepareCheck
FOR %%x in ("%INSTALL_ROOT%\lib\*.jar") DO call "%INSTALL_ROOT%\lib\setcp.bat" %%x
if "%INSTALL_ROOT%" == "%INSTANCE_ROOT%"goto setClassPathDone
FOR %%x in ("%INSTANCE_ROOT%\lib\*.jar") DO call "%INSTANCE_ROOT%\lib\setcp.bat" %%x
FOR %%x in ("%INSTALL_ROOT%\resources\*.jar") DO call "%INSTALL_ROOT%\lib\setcp.bat" %%x
set CLASSPATH=%INSTANCE_ROOT%\classes;%CLASSPATH%
:setClassPathDone
set SET_CLASSPATH_DONE=true
goto scriptBegin

:setFullEnvironment
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "false" goto setJavaHomeAndArgs
if "%SET_CLASSPATH_DONE%" == "false" goto setClassPath
if "%SET_ENVIRONMENT_VARS_DONE%" == "false" goto setEnvironmentVars
goto prepareCheck

:setFullEnvironmentAndTestJava
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "false" goto setJavaHomeAndArgs
if "%SET_CLASSPATH_DONE%" == "false" goto setClassPath
if "%SET_ENVIRONMENT_VARS_DONE%" == "false" goto setEnvironmentVars
goto testJava

:setJavaHomeAndArgs
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "true" goto prepareCheck
if not exist "%INSTANCE_ROOT%\lib\set-java-home.bat" goto checkEnvJavaArgs
call "%INSTANCE_ROOT%\lib\set-java-home.bat"
if "%OPENDJ_JAVA_BIN%" == "" goto checkEnvJavaArgs
:endJavaHomeAndArgs
set SET_JAVA_HOME_AND_ARGS_DONE=true
goto scriptBegin

:checkEnvJavaArgs
if "%OPENDJ_JAVA_BIN%" == "" goto checkOpenDJJavaHome
if not exist "%OPENDJ_JAVA_BIN%" goto checkOpenDJJavaHome
goto endJavaHomeAndArgs

:checkOpenDJJavaHome
if "%OPENDJ_JAVA_HOME%" == "" goto checkJavaBin
if not exist "%OPENDJ_JAVA_HOME%\bin\java.exe" goto checkJavaBin
set OPENDJ_JAVA_BIN=%OPENDJ_JAVA_HOME%\bin\java.exe
goto endJavaHomeAndArgs

:checkJavaBin
if "%JAVA_BIN%" == "" goto checkJavaHome
if not exist "%JAVA_BIN%" goto checkJavaHome
set OPENDJ_JAVA_BIN=%JAVA_BIN%
goto endJavaHomeAndArgs

:checkJavaHome
if "%JAVA_HOME%" == "" goto checkJavaPath
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaFound
set OPENDJ_JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto endJavaHomeAndArgs

:checkJavaPath
java.exe -version > NUL 2>&1
if not %errorlevel% == 0 goto noJavaFound
set OPENDJ_JAVA_BIN=java.exe
goto endJavaHomeAndArgs

:noJavaFound
echo ERROR:  Could not find a valid Java binary to be used.
echo You must specify the path to a valid Java 5.0 or higher version.
echo The procedure to follow is:
echo 1. Delete the file %INSTANCE_ROOT%\lib\set-java-home.bat if it exists.
echo 2. Set the environment variable OPENDJ_JAVA_HOME to the root of a valid
echo Java 5.0 installation.
echo If you want to have specific Java settings for each command line you must
echo follow the steps 3 and 4.
echo 3. Edit the properties file specifying the Java binary and the Java arguments
echo for each command line.  The Java properties file is located in:
echo %INSTANCE_ROOT%\config\java.properties.
echo 4. Run the command-line %INSTALL_ROOT%\bat\dsjavaproperties.bat
pause
exit /B 1

:setEnvironmentVars
if %SET_ENVIRONMENT_VARS_DONE% == "true" goto prepareCheck
set PATH=%SystemRoot%;%PATH%
set SCRIPT_NAME_ARG=-Dcom.forgerock.opendj.ldap.tools.scriptName=%SCRIPT_NAME%
set SET_ENVIRONMENT_VARS_DONE=true
goto scriptBegin

:isVersionOrHelp
if [%1] == [] goto end
if [%1] == [--help] goto end
if [%1] == [-H] goto end
if [%1] == [--version] goto end
if [%1] == [-V] goto end
if [%1] == [--fullversion] goto end
if [%1] == [-F] goto end
shift
goto isVersionOrHelp

:prepareCheck
rem Perform check unless it is specified not to do it
if "%NO_CHECK%" == ""  set NO_CHECK=false
goto isVersionOrHelp

:end
exit /B 0
