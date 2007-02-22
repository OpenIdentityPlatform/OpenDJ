
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
rem by brackets "[]" replaced with your own identifying * information:
rem      Portions Copyright [yyyy] [name of copyright owner]
rem
rem CDDL HEADER END
rem
rem
rem      Portions Copyright 2006-2007 Sun Microsystems, Inc.

setlocal

set OPENDS_INVOKE_CLASS="org.opends.server.tools.StopDS"
set SCRIPT_NAME_ARG="-Dorg.opends.server.scriptName=stop-ds"
set DIR_HOME=%~dP0..

set ARGUMENTS=1
if "%*" == "" set ARGUMENTS=0
if "%ARGUMENTS%" == "1" goto stopWithLDAP
if not exist "%DIR_HOME%\logs\server.pid" goto stopWithLDAP
"%DIR_HOME%\lib\winlauncher.exe" stop "%DIR_HOME%" 
goto end

:stopWithLDAP
call "%~dP0\_client-script.bat" %*

:end