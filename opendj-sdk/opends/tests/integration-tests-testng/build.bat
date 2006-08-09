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
rem information:
rem      Portions Copyright [yyyy] [name of copyright owner]
rem
rem CDDL HEADER END
rem
rem
rem      Portions Copyright 2006 Sun Microsystems, Inc.

setlocal

rem These are the variables we need to run the integration tests
set FT_HOME=%~dP0
set DIR_HOME=%FT_HOME%\..\..
set EXTRAS_HOME=%DIR_HOME%\ext
set ANT_HOME=%EXTRAS_HOME%\ant
set TESTNG_HOME=%EXTRAS_HOME%\testng

if "%JAVA_HOME%" == "" goto noJavaHome
goto runAnt

:noJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 installation.
goto end

:runAnt
rem Append the testng jar file to the existing classpath
set SEMICOLON=
if not "%CLASSPATH%" == "" set SEMICOLON=";"
set CLASSPATH=%CLASSPATH%%SEMICOLON%%TESTNG_HOME%\lib\testng-4.7-jdk15.jar
rem echo a quick summary of what this script did
echo using the following variables:
echo   ANT_HOME=%ANT_HOME%
echo   JAVA_HOME=%JAVA_HOME%
echo   CLASSPATH=%CLASSPATH%
echo Now running ant ...
%ANT_HOME%\bin\ant %*

:end

