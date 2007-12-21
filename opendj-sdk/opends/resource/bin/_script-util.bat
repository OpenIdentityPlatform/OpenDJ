
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
rem      Portions Copyright 2007 Sun Microsystems, Inc.

set SET_JAVA_HOME_AND_ARGS_DONE=false
set SET_ENVIRONMENT_VARS_DONE=false
set SET_CLASSPATH_DONE=false

if "%INSTANCE_ROOT%" == "" goto setInstanceRoot

:scriptBegin
if "%SCRIPT_UTIL_CMD%" == "set-full-environment-and-test-java" goto setFullEnvironmentAndTestJava
if "%SCRIPT_UTIL_CMD%" == "set-full-environment" goto setFullEnvironment
if "%SCRIPT_UTIL_CMD%" == "set-java-home-and-args" goto setJavaHomeAndArgs
if "%SCRIPT_UTIL_CMD%" == "set_environment_vars" goto setEnvironmentVars
if "%SCRIPT_UTIL_CMD%" == "test-java" goto testJava
if "%SCRIPT_UTIL_CMD%" == "set-classpath" goto setClassPath
goto end

:setInstanceRoot
setlocal
for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
set INSTANCE_ROOT=%DIR_HOME%
goto scriptBegin


:setClassPath
if "%SET_CLASSPATH_DONE%" == "true" goto end
FOR %%x in ("%DIR_HOME%\lib\*.jar") DO call "%DIR_HOME%\lib\setcp.bat" %%x
set CLASSPATH=%DIR_HOME%\classes;%CLASSPATH%
set SET_CLASSPATH_DONE=true
goto scriptBegin

:setFullEnvironment
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "false" goto setJavaHomeAndArgs
if "%SET_CLASSPATH_DONE%" == "false" goto setClassPath
if "%SET_ENVIRONMENT_VARS_DONE%" == "false" goto setEnvironmentVars
goto end

:setFullEnvironmentAndTestJava
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "false" goto setJavaHomeAndArgs
if "%SET_CLASSPATH_DONE%" == "false" goto setClassPath
if "%SET_ENVIRONMENT_VARS_DONE%" == "false" goto setEnvironmentVars
goto testJava


:setJavaHomeAndArgs
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "true" goto end
if not exist "%DIR_HOME%\lib\set-java-home.bat" goto checkEnvJavaArgs
call "%DIR_HOME%\lib\set-java-home.bat"
if "%OPENDS_JAVA_BIN%" == "" goto checkEnvJavaArgs
:endJavaHomeAndArgs
set SET_JAVA_HOME_AND_ARGS_DONE=true
goto scriptBegin

:checkEnvJavaArgs
if "%OPENDS_JAVA_ARGS%"=="" set OPENDS_JAVA_ARGS=%JAVA_ARGS%
if "%OPENDS_JAVA_BIN%" == "" goto checkOpenDSJavaHome
if not exist "%OPENDS_JAVA_BIN%" goto checkOpenDSJavaHome
goto endJavaHomeAndArgs

:checkOpenDSJavaHome
if "%OPENDS_JAVA_HOME%" == "" goto checkJavaBin
if not exist "%OPENDS_JAVA_HOME%\bin\java.exe" goto checkJavaBin
set OPENDS_JAVA_BIN=%OPENDS_JAVA_HOME%\bin\java.exe
goto endJavaHomeAndArgs

:checkJavaBin
if "%JAVA_BIN%" == "" goto checkJavaHome
if not exist "%JAVA_BIN%" goto checkJavaHome
set OPENDS_JAVA_BIN=%JAVA_BIN%
goto endJavaHomeAndArgs

:checkJavaHome
if "%JAVA_HOME%" == "" goto noJavaFound
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaFound
set OPENDS_JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto endJavaHomeAndArgs

:noJavaFound
echo ERROR:  Could not find a valid java binary to be used.
echo You must specify the path to a valid Java 5.0 or higher version.
echo The procedure to follow is:
echo 1. Delete the file %INSTANCE_ROOT%\lib\set-java-home.bat if it exists.
echo 2. Set the environment variable OPENDS_JAVA_HOME to the root of a valid
echo Java 5.0 installation.
echo If you want to have specific java  settings for each command line you must
echo follow the steps 3 and 4.
echo 3. Edit the properties file specifying the java binary and the java arguments
echo for each command line.  The java properties file is located in:
echo %INSTANCE_ROOT%\config\java.properties.
echo 4. Run the command-line %INSTANCE_ROOT%\bin\dsjavaproperties
pause
exit /B 1

:setEnvironmentVars
if %SET_ENVIRONMENT_VARS_DONE% == "true" goto end
set PATH=%SystemRoot%;%PATH%
set SCRIPT_NAME_ARG=-Dorg.opends.server.scriptName=%SCRIPT_NAME%
set SET_ENVIRONMENT_VARS_DONE=true
goto scriptBegin

:testJava
"%OPENDS_JAVA_BIN%" %OPENDS_JAVA_ARGS% org.opends.server.tools.InstallDS -t > NUL 2>&1
if not %errorlevel% == 0 goto noValidJavaHome
goto end

:noValidJavaHome
if NOT "%OPENDS_JAVA_ARGS%" == "" goto noValidHomeWithArgs
echo ERROR:  The detected Java version could not be used.  The detected
echo java binary is:
echo %OPENDS_JAVA_BIN%
echo You must specify the path to a valid Java 5.0 or higher version.
echo The procedure to follow is:
echo 1. Delete the file %INSTANCE_ROOT%\lib\set-java-home.bat if it exists.
echo 2. Set the environment variable OPENDS_JAVA_HOME to the root of a valid
echo Java 5.0 installation.
echo If you want to have specific java  settings for each command line you must
echo follow the steps 3 and 4.
echo 3. Edit the properties file specifying the java binary and the java arguments
echo for each command line.  The java properties file is located in:
echo %INSTANCE_ROOT%\config\java.properties.
echo 4. Run the command-line %INSTANCE_ROOT%\bin\dsjavaproperties
pause
exit /B 1

:noValidHomeWithArgs
echo ERROR:  The detected Java version could not be used with the set of java
echo arguments %OPENDS_JAVA_ARGS%.
echo The detected java binary is:
echo %OPENDS_JAVA_BIN%
echo You must specify the path to a valid Java 5.0 or higher version.
echo The procedure to follow is:
echo 1. Delete the file %INSTANCE_ROOT%\lib\set-java-home.bat if it exists.
echo 2. Set the environment variable OPENDS_JAVA_HOME to the root of a valid
echo Java 5.0 installation.
echo If you want to have specific java  settings for each command line you must
echo follow the steps 3 and 4.
echo 3. Edit the properties file specifying the java binary and the java arguments
echo for each command line.  The java properties file is located in:
echo %INSTANCE_ROOT%\config\java.properties.
echo 4. Run the command-line %INSTANCE_ROOT%\bin\dsjavaproperties
pause
exit /B 1

:end
exit /B 0
