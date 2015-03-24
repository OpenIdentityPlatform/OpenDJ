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
rem      Copyright 2008-2009 Sun Microsystems, Inc.
rem      Portions copyright 2013-2015 ForgeRock AS.

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

:noValidJavaHome
if NOT "%OPENDJ_JAVA_ARGS%" == "" goto noValidHomeWithArgs
echo ERROR:  The detected Java version could not be used.  The detected
echo Java binary is:
echo %OPENDJ_JAVA_BIN%
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

:notSupportedJavaHome
rem We get here when the java version is 5 (or up) but not supported.  We run
rem InstallDS again to see a localized message.
"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% org.opendj.server.tools.InstallDS --testonly
pause
exit /B 1

:noValidHomeWithArgs
echo ERROR:  The detected Java version could not be used with the set of Java
echo arguments %OPENDJ_JAVA_ARGS%.
echo The detected Java binary is:
echo %OPENDJ_JAVA_BIN%
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
