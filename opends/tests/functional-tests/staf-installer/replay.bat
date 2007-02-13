@echo off
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
