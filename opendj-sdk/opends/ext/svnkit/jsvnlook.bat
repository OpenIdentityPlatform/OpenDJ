@echo off

set ERROR_CODE=0

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set CMD_LINE_ARGS=%*
goto WinNTGetScriptDir

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto WinNTGetScriptDir

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto Win9xGetScriptDir
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

:Win9xGetScriptDir
set SAVEDIR=%CD%
%0\
cd %0\..\.. 
set BASEDIR=%CD%
cd %SAVEDIR%
set SAVE_DIR=
goto repoSetup

:WinNTGetScriptDir
set BASEDIR=%~dp0
IF %BASEDIR:~-1%==\ SET BASEDIR=%BASEDIR:~0,-1%
set BASEDIR=%BASEDIR%\..

:repoSetup


if "%JAVACMD%"=="" set JAVACMD=java
if not "%JAVA_HOME%"=="" set JAVACMD="%JAVA_HOME%\bin\%JAVACMD%"

if "%SVNKIT_LIB%"=="" set SVNKIT_LIB=%BASEDIR%\lib

set CLASSPATH=
set CLASSPATH=%CLASSPATH%;"%SVNKIT_LIB%\svnkit-1.7.5-v1.jar"
set CLASSPATH=%CLASSPATH%;"%SVNKIT_LIB%\sequence-library-1.0.2.jar"
set CLASSPATH=%CLASSPATH%;"%SVNKIT_LIB%\antlr-runtime-3.4.jar"
set CLASSPATH=%CLASSPATH%;"%SVNKIT_LIB%\sqljet-1.1.4.jar"
set CLASSPATH=%CLASSPATH%;"%SVNKIT_LIB%\jna-3.4.0.jar"
set CLASSPATH=%CLASSPATH%;"%SVNKIT_LIB%\trilead-ssh2-1.0.0-build215.jar"
set CLASSPATH=%CLASSPATH%;"%SVNKIT_LIB%\svnkit-cli-1.7.5-v1.jar"
set EXTRA_JVM_ARGUMENTS=-Djava.util.logging.config.file="%BASEDIR%\conf\logging.properties" -Dsun.io.useCanonCaches=false
goto endInit

@REM Reaching here means variables are defined and arguments have been captured
:endInit

%JAVACMD% %JAVA_OPTS% %EXTRA_JVM_ARGUMENTS% -classpath %CLASSPATH% org.tmatesoft.svn.cli.SVNLook %CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
set ERROR_CODE=1

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set CMD_LINE_ARGS=
goto postExec

:endNT
@endlocal

:postExec

if "%FORCE_EXIT_ON_ERROR%" == "on" (
  if %ERROR_CODE% NEQ 0 exit %ERROR_CODE%
)

exit /B %ERROR_CODE%
