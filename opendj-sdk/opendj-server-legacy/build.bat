@echo off

rem CDDL HEADER START
rem
rem The contents of this file are subject to the terms of the
rem Common Development and Distribution License, Version 1.0 only
rem (the "License").  You may not use this file except in compliance
rem with the License.
rem
rem You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
rem or http://forgerock.org/license/CDDLv1.0.html.
rem See the License for the specific language governing permissions
rem and limitations under the License.
rem
rem When distributing Covered Code, include this CDDL HEADER in each
rem file and include the License file at legal-notices/CDDLv1_0.txt.
rem If applicable, add the following below this CDDL HEADER, with the
rem fields enclosed by brackets "[]" replaced with your own identifying
rem information:
rem      Portions Copyright [yyyy] [name of copyright owner]
rem
rem CDDL HEADER END
rem
rem
rem      Copyright 2008-2010 Sun Microsystems, Inc.
rem      Portions Copyright 2013-2015 ForgeRock AS.

setlocal

set DIR_HOME=%~dP0

if "%JAVA_HOME%" == "" goto noJavaHome
goto runAnt

:noJavaHome
echo Error: JAVA_HOME environment variable is not set.
echo        Please set it to a valid Java 7 installation.
goto end


:runAnt
set CLASSPATH=%DIR_HOME%\ext\junit.jar

"%DIR_HOME%\ext\ant\bin\ant" %*


:end

