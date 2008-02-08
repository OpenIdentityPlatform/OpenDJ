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
rem      Copyright 2008 Sun Microsystems, Inc.

setlocal

rem These are the variables we need to run the functional tests
set FT_HOME=%~dP0
set ANT_HOME=%FT_HOME%\..\..\ext\ant

if "%JAVA_HOME%" == "" goto noJavaHome
goto runAnt

:noJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 5 Development Kit installation.
goto end

:runAnt
rem echo a quick summary of what this script did
echo using the following variables:
echo   ANT_HOME=%ANT_HOME%
echo   JAVA_HOME=%JAVA_HOME%
if not "%*" == "" echo   your parameters=%*
echo Now running ant ...
set OPENDS_LIB=%FT_HOME%\..\..\lib
"%ANT_HOME%\bin\ant" -lib "%OPENDS_LIB%\mail.jar;%OPENDS_LIB%\activation.jar" -f staf-installer.xml %*

:end
