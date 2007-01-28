@echo off

set DEFAULT_SVNKIT_HOME=%~dp0

if "%SVNKIT_HOME%"=="" set SVNKIT_HOME=%DEFAULT_SVNKIT_HOME%

set SVNKIT_CLASSPATH= "%SVNKIT_HOME%svnkit.jar";"%SVNKIT_HOME%svnkit-cli.jar";"%SVNKIT_HOME%ganymed.jar"
set SVNKIT_MAINCLASS=org.tmatesoft.svn.cli.SVNSync
set SVNKIT_OPTIONS=-Djava.util.logging.config.file="%SVNKIT_HOME%/logging.properties"

"%JAVA_HOME%\bin\java" %SVNKIT_OPTIONS% -cp %SVNKIT_CLASSPATH% %SVNKIT_MAINCLASS% %*