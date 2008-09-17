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
rem      Copyright 2007-2008 Sun Microsystems, Inc.

echo Backing configuration up
move "${tests.config}" "${tests.config.backup}"
echo Loading configuration as of ${tests.run.time}
copy "${tests.run.dir}${file.separator}${tests.run.time}${file.separator}config${file.separator}${tests.config.file}" "${tests.config}"
echo Starting test run
"${staf.install.dir}${file.separator}bin${file.separator}STAF.exe" local STAX "${tests.request}"
echo Removing configuration of ${tests.run.time}
del /f "${tests.config}"
echo Restoring original configuration
move "${tests.config.backup}" "${tests.config}"
