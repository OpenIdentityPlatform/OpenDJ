
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
rem      Copyright 2013-2014 ForgeRock AS

setlocal

set OPENDJ_INVOKE_CLASS="org.opends.server.tools.upgrade.UpgradeCli"
set SCRIPT_NAME=upgrade

for %%i in (%~sf0) do set SCRIPT_DIR=%%~dPsi
set DIR_CLASSES="%SCRIPT_DIR%classes"
rem The upgrade is not compatible with patches. If the folder is not empty
rem we renamed it as "classes.disabled", and the upgrade process should be launched properly.
IF EXIST "%DIR_CLASSES%" (
  for /F %%i in ('dir /b %DIR_CLASSES%\*.*') do goto renamePatchesFolder 
)
goto end

:renamePatchesFolder
move /-Y "%DIR_CLASSES%" "%SCRIPT_DIR%classes.disabled" > nul
mkdir %DIR_CLASSES%

:end
for %%i in (%~sf0) do call "%%~dPsi\lib\_server-script.bat" %*

