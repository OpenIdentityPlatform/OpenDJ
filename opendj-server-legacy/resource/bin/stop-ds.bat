
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
rem Copyright 2006-2010 Sun Microsystems, Inc.
rem Portions Copyright 2011-2014 ForgeRock AS.

setlocal

set OPENDJ_INVOKE_CLASS="org.opends.server.tools.StopDS"
set SCRIPT_NAME=stop-ds
set DIR_HOME=%~dp0..

rem We keep this values to reset the environment before calling start-ds.
set ORIGINAL_JAVA_ARGS=%OPENDJ_JAVA_ARGS%
set ORIGINAL_JAVA_HOME=%OPENDJ_JAVA_HOME%
set ORIGINAL_JAVA_BIN=%OPENDJ_JAVA_BIN%

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
set TEMP_LOG=%TEMP%\logs\

if NOT EXIST "%INSTANCE_ROOT%\logs\" (
  if NOT EXIST "%TEMP_LOG%" (
    md "%TEMP_LOG%"
  )
  set LOG="%TEMP_LOG%native-windows.out"
) ELSE (
  set LOG="%INSTANCE_ROOT%\logs\native-windows.out"
)
set SCRIPT=stop-ds.bat

rem This is the template to use for logging.  Make sure to use >>
rem echo %SCRIPT%: your-message-here >> %LOG%
echo %SCRIPT%: invoked >> %LOG%

rem Set environment variables
set SCRIPT_UTIL_CMD=set-full-server-environment-and-test-java
call "%INSTALL_ROOT%\lib\_script-util.bat" %*
if NOT %errorlevel% == 0 exit /B %errorlevel%

echo %SCRIPT%: CLASSPATH=%CLASSPATH% >> %LOG%

"%OPENDJ_JAVA_BIN%" %OPENDJ_JAVA_ARGS% %SCRIPT_NAME_ARG%  org.opends.server.tools.StopDS --checkStoppability %*

if %errorlevel% == 98 goto serverAlreadyStopped
if %errorlevel% == 99 goto startUsingSystemCall
if %errorlevel% == 100 goto stopUsingSystemCall
if %errorlevel% == 101 goto restartUsingSystemCall
if %errorlevel% == 102 goto stopUsingProtocol
if %errorlevel% == 103 goto stopAsWindowsService
if %errorlevel% == 104 goto restartAsWindowsService
rem An error or we display usage
goto writeLastLine

:serverAlreadyStopped
echo %SCRIPT%: server already stopped >> %LOG%
if exist "%INSTANCE_ROOT%\logs\server.pid" erase "%INSTANCE_ROOT%\logs\server.pid"
goto writeLastLine

:startUsingSystemCall
echo %SCRIPT%: start using system call >> %LOG%
rem Set the original values that the user had on the environment in order to be
rem sure that the start-ds script works with the proper arguments (in particular
rem if the user specified not to overwrite the environment).
set OPENDJ_JAVA_ARGS=%ORIGINAL_JAVA_ARGS%
set OPENDJ_JAVA_HOME=%ORIGINAL_JAVA_HOME%
set OPENDJ_JAVA_BIN=%ORIGINAL_JAVA_BIN%
"%INSTALL_ROOT%\bat\start-ds.bat"
goto writeLastLine

:stopUsingSystemCall
echo %SCRIPT%: stop using system call >> %LOG%
"%INSTALL_ROOT%\lib\winlauncher.exe" stop "%INSTANCE_ROOT%"
goto end

:restartUsingSystemCall
echo %SCRIPT%: restart using system call >> %LOG%
"%INSTALL_ROOT%\lib\winlauncher.exe" stop "%INSTANCE_ROOT%"
if not %errorlevel% == 0 goto end
goto startUsingSystemCall

:stopUsingProtocol
echo %SCRIPT%: stop using protocol >> %LOG%
set CLASSPATH=""
call "%INSTALL_ROOT%\lib\_client-script.bat" %*
goto end

:stopAsWindowsService
echo %SCRIPT%: stop as windows service >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.StopWindowsService
goto end

:restartAsWindowsService
echo %SCRIPT%: restart as windows service, stopping >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.StopWindowsService
if not %errorlevel% == 0 goto end
echo %SCRIPT%: restart as windows service, starting >> %LOG%
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.StartWindowsService
"%OPENDJ_JAVA_BIN%" -client org.opends.server.tools.WaitForFileDelete --targetFile "%INSTANCE_ROOT%\logs\server.startingservice"
rem Type the contents the winwervice.out file and delete it.
if exist "%INSTANCE_ROOT%\logs\winservice.out" type "%INSTANCE_ROOT%\logs\winservice.out"
if exist "%INSTANCE_ROOT%\logs\winservice.out" erase "%INSTANCE_ROOT%\logs\winservice.out"
goto end

:writeLastLine
echo %SCRIPT%: finished >> %LOG%
goto end

:end
