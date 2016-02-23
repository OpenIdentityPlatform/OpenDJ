
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
rem Copyright 2006-2009 Sun Microsystems, Inc.
rem Portions Copyright 2011-2012 ForgeRock AS.

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

