
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
rem      Copyright 2006-2009 Sun Microsystems, Inc.
rem      Portions Copyright 2011-2012 ForgeRock AS

setlocal

for %%i in (%~sf0) do set NON_ESCAPED=%%~dPsi..

FOR /F "tokens=1-2* delims=%%" %%1 IN ("%NON_ESCAPED%") DO (
if NOT "%%2" == "" goto invalidPath)

for %%i in (%~sf0) do set DIR_HOME=%%~dPsi..
set INSTALL_ROOT=%DIR_HOME%

set INSTANCE_DIR=
if exist "%INSTALL_ROOT%\instance.loc" (
  set /p INSTANCE_DIR=<%INSTALL_ROOT%\instance.loc
) else (
set INSTANCE_DIR=.
)
set CUR_DIR=%CD%
cd /d %INSTALL_ROOT%
cd /d %INSTANCE_DIR%
set INSTANCE_ROOT=%CD%
cd /d %CUR_DIR%

set OPENDJ_INVOKE_CLASS="org.opends.server.tools.makeldif.MakeLDIF"
set SCRIPT_NAME=make-ldif
"%INSTALL_ROOT%\lib\_server-script.bat" --resourcePath "%INSTANCE_ROOT%\config\MakeLDIF" %*

