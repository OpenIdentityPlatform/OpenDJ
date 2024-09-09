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
rem Copyright 2008-2010 Sun Microsystems, Inc.
rem Portions Copyright 2011-2016 ForgeRock AS.
rem Portions Copyright 2020-2024 3A Systems, LLC.

set SET_JAVA_HOME_AND_ARGS_DONE=false
set SET_ENVIRONMENT_VARS_DONE=false
set SET_CLASSPATH_DONE=false

if "%INSTALL_ROOT%" == "" goto setInstanceRoot

:scriptBegin
if "%SCRIPT_UTIL_CMD%" == "set-full-environment-and-test-java" goto setFullEnvironmentAndTestJava
if "%SCRIPT_UTIL_CMD%" == "set-full-server-environment-and-test-java" goto setFullServerEnvironmentAndTestJava
if "%SCRIPT_UTIL_CMD%" == "set-full-environment" goto setFullEnvironment
if "%SCRIPT_UTIL_CMD%" == "set-java-home-and-args" goto setJavaHomeAndArgs
if "%SCRIPT_UTIL_CMD%" == "set_environment_vars" goto setEnvironmentVars
if "%SCRIPT_UTIL_CMD%" == "test-java" goto testJava
if "%SCRIPT_UTIL_CMD%" == "set-classpath" goto setClassPath
goto end

:setInstanceRoot
setlocal
set DIR_HOME=%~dp0..
set INSTALL_ROOT=%DIR_HOME%
set INSTANCE_DIR=
if exist "%INSTALL_ROOT%\instance.loc" (
  set /p INSTANCE_DIR=<"%INSTALL_ROOT%\instance.loc"
) else (
set INSTANCE_DIR=.
)
set CUR_DIR=%CD%
cd /d %INSTALL_ROOT%
cd /d %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd /d %CUR_DIR%
goto scriptBegin

rem Configure the appropriate CLASSPATH for client, using java.util.logging logger.
:setClassPath
if "%SET_CLASSPATH_DONE%" == "true" goto end
rem get the absolute paths before building the classpath
rem it also helps comparing the two paths
FOR /F "delims=" %%i IN ("%INSTALL_ROOT%")  DO set INSTALL_ROOT=%%~dpnxi
FOR /F "delims=" %%i IN ("%INSTANCE_ROOT%") DO set INSTANCE_ROOT=%%~dpnxi
call "%INSTALL_ROOT%\lib\setcp.bat" %INSTALL_ROOT%\lib\bootstrap-client.jar
set CLASSPATH=%INSTANCE_ROOT%\classes;%CLASSPATH%
if "%INSTALL_ROOT%" == "%INSTANCE_ROOT%" goto setClassPathDone
FOR %%x in ("%INSTANCE_ROOT%\lib\*.jar") DO call "%INSTANCE_ROOT%\lib\setcp.bat" %%x
:setClassPathDone
set SET_CLASSPATH_DONE=true
goto scriptBegin

rem Configure the appropriate CLASSPATH for server, using Opend DJ logger.
:setClassPathWithOpenDJLogger
if "%SET_CLASSPATH_DONE%" == "true" goto end
rem get the absolute paths before building the classpath
rem it also helps comparing the two paths
FOR /F "delims=" %%i IN ("%INSTALL_ROOT%")  DO set INSTALL_ROOT=%%~dpnxi
FOR /F "delims=" %%i IN ("%INSTANCE_ROOT%") DO set INSTANCE_ROOT=%%~dpnxi
call "%INSTALL_ROOT%\lib\setcp.bat" %INSTALL_ROOT%\lib\bootstrap.jar
set CLASSPATH=%INSTANCE_ROOT%\classes;%CLASSPATH%
if "%INSTALL_ROOT%" == "%INSTANCE_ROOT%" goto setClassPathWithOpenDJLoggerDone
FOR %%x in ("%INSTANCE_ROOT%\lib\*.jar") DO call "%INSTANCE_ROOT%\lib\setcp.bat" %%x
:setClassPathWithOpenDJLoggerDone
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

:setFullServerEnvironmentAndTestJava
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "false" goto setJavaHomeAndArgs
if "%SET_CLASSPATH_DONE%" == "false" goto setClassPathWithOpenDJLogger
if "%SET_ENVIRONMENT_VARS_DONE%" == "false" goto setEnvironmentVars

:setJavaHomeAndArgs
if "%SET_JAVA_HOME_AND_ARGS_DONE%" == "true" goto end
rem if not "%OPENDJ_JAVA_ARGS%" == "" goto checkEnvJavaHome
set SCRIPT_JAVA_ARGS_PROPERTY=%SCRIPT_NAME%.java-args
call:readProperty %SCRIPT_JAVA_ARGS_PROPERTY%
set OPENDJ_JAVA_ARGS=%OPENDJ_JAVA_ARGS% %PROPERTY_VALUE%
if not "%OPENDJ_JAVA_ARGS%" == "" goto checkEnvJavaHome
call:readProperty default.java-args
set OPENDJ_JAVA_ARGS=%OPENDJ_JAVA_ARGS% %PROPERTY_VALUE
if "%OPENDJ_JAVA_BIN%" == "" goto checkEnvJavaHome

:endJavaHomeAndArgs
set SET_JAVA_HOME_AND_ARGS_DONE=true
goto scriptBegin

:checkEnvJavaHome
if "%OPENDJ_JAVA_BIN%" == "" goto checkOpenDJJavaHome
if not exist "%OPENDJ_JAVA_BIN%" goto checkOpenDJJavaHome
goto endJavaHomeAndArgs

:checkOpenDJJavaHome
if "%OPENDJ_JAVA_HOME%" == "" goto checkScriptPropertyJavaHome
if not exist "%OPENDJ_JAVA_HOME%\bin\java.exe" goto checkScriptPropertyJavaHome
set OPENDJ_JAVA_BIN=%OPENDJ_JAVA_HOME%\bin\java.exe
goto endJavaHomeAndArgs

:checkScriptPropertyJavaHome
set SCRIPT_JAVA_HOME_PROPERTY=%SCRIPT_NAME%.java-home
call:readProperty %SCRIPT_JAVA_HOME_PROPERTY%
if "%PROPERTY_VALUE%" == "" goto checkDefaultPropertyJavaHome
if not exist "%PROPERTY_VALUE%\bin\java.exe" goto checkDefaultPropertyJavaHome
set OPENDJ_JAVA_BIN=%PROPERTY_VALUE%\bin\java.exe
goto endJavaHomeAndArgs

:checkDefaultPropertyJavaHome
call:readProperty default.java-home
if "%PROPERTY_VALUE%" == "" goto checkJavaPath
if not exist "%PROPERTY_VALUE%\bin\java.exe" goto checkJavaPath
set OPENDJ_JAVA_BIN=%PROPERTY_VALUE%\bin\java.exe
goto endJavaHomeAndArgs

:checkJavaPath
java.exe -version > NUL 2>&1
if not %errorlevel% == 0 goto checkJavaBin
set OPENDJ_JAVA_BIN=java.exe
goto endJavaHomeAndArgs

:checkJavaBin
if "%JAVA_BIN%" == "" goto checkJavaHome
if not exist "%JAVA_BIN%" goto checkJavaHome
set OPENDJ_JAVA_BIN=%JAVA_BIN%
goto endJavaHomeAndArgs

:checkJavaHome
if "%JAVA_HOME%" == "" goto noJavaFound
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaFound
set OPENDJ_JAVA_BIN=%JAVA_HOME%\bin\java.exe
goto endJavaHomeAndArgs

:noJavaFound
echo ERROR:  Could not find a valid Java binary to be used.
echo You must specify the path to a valid Java 7 or higher version.
echo The procedure to follow is to set the environment variable OPENDJ_JAVA_HOME
echo to the root of a valid Java 7 installation.
echo If you want to have specific Java settings for each command line you must
echo edit the properties file specifying the Java binary and the Java arguments
echo for each command line.  The Java properties file is located in:
echo %INSTANCE_ROOT%\config\java.properties.
pause
exit /B 1

:setEnvironmentVars
if %SET_ENVIRONMENT_VARS_DONE% == "true" goto end
set PATH=%SystemRoot%;%PATH%
set SCRIPT_NAME_ARG=-Dorg.opends.server.scriptName=%SCRIPT_NAME%
set SET_ENVIRONMENT_VARS_DONE=true
"%OPENDJ_JAVA_BIN%" --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --version > NUL 2>&1
set RESULT_CODE=%errorlevel%
if %RESULT_CODE% == 0 set OPENDJ_JAVA_ARGS=%OPENDJ_JAVA_ARGS% --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED
goto scriptBegin

:testJava
if "%OPENDJ_JAVA_ARGS%" == "" goto checkLegacyArgs
:continueTestJava
"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% org.opends.server.tools.CheckJVMVersion > NUL 2>&1
set RESULT_CODE=%errorlevel%
if %RESULT_CODE% == 8 goto notSupportedJavaHome
if not %RESULT_CODE% == 0 goto noValidJavaHome
goto end

:checkLegacyArgs
if "%OPENDS_JAVA_ARGS%" == "" goto continueTestJava
set OPENDJ_JAVA_ARGS=%OPENDS_JAVA_ARGS%
goto continueTestJava

:noValidJavaHome
if NOT "%OPENDJ_JAVA_ARGS%" == "" goto noValidHomeWithArgs
echo ERROR:  The detected Java version could not be used.  The detected
echo Java binary is:
echo %OPENDJ_JAVA_BIN%
echo You must specify the path to a valid Java 7 or higher version.
echo The procedure to follow is to set the environment variable OPENDJ_JAVA_HOME
echo to the root of a valid Java 7 installation.
echo If you want to have specific Java settings for each command line you must
echo edit the properties file specifying the Java binary and the Java arguments
echo for each command line.  The Java properties file is located in:
echo %INSTANCE_ROOT%\config\java.properties.
pause
exit /B 1

:notSupportedJavaHome
rem We get here when the java version is 6 (or up) but not supported.  We run
rem InstallDS again to see a localized message.
"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% org.opends.server.tools.InstallDS --testonly
pause
exit /B 1

:noValidHomeWithArgs
echo ERROR:  The detected Java version could not be used with the set of Java
echo arguments %OPENDJ_JAVA_ARGS%.
echo The detected Java binary is:
echo %OPENDJ_JAVA_BIN%
echo You must specify the path to a valid Java 7 or higher version.
echo The procedure to follow is to set the environment variable OPENDJ_JAVA_HOME
echo  to the root of a valid Java 7 installation.
echo If you want to have specific Java settings for each command line you must
echo edit the properties file specifying the Java binary and the Java arguments
echo for each command line.  The Java properties file is located in:
echo %INSTANCE_ROOT%\config\java.properties.
pause
exit /B 1

:end
exit /B 0

rem read the provided property from the configuration/java.properties and stores it in PROPERTY_VALUE variable
:readProperty
set PROPERTY_VALUE=
set JAVA_PROPERTIES=%INSTALL_ROOT%\config\java.properties
if not exist "%JAVA_PROPERTIES%" goto:eof
set CMD=findstr /b [^#]*%~1=.* "%JAVA_PROPERTIES%"
for /f "tokens=2 delims==" %%a in ( '%CMD%' ) do set PROPERTY_VALUE=%%a
goto:eof
