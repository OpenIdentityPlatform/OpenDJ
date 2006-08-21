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

set INTEG_TEST_HOME=%1
if "%INTEG_TEST_HOME%" == "" goto usage

rem echo INTEG_TEST_HOME is %INTEG_TEST_HOME%
echo OpenDS Integration Tests have started.........
java -ea org.testng.TestNG -d /tmp/testng -listener org.opends.server.OpenDSTestListener %INTEG_TEST_HOME%/ext/testng/testng-windows.xml
goto end


:usage
echo usage:
echo test [Integration Test Suite HOME Directory]

:end
