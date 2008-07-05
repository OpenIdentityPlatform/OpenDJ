/*
* CDDL HEADER START
*
* The contents of this file are subject to the terms of the
* Common Development and Distribution License, Version 1.0 only
* (the "License").  You may not use this file except in compliance
* with the License.
*
* You can obtain a copy of the license at
* trunk/opends/resource/legal-notices/OpenDS.LICENSE
* or https://OpenDS.dev.java.net/OpenDS.LICENSE.
* See the License for the specific language governing permissions
* and limitations under the License.
*
* When distributing Covered Code, include this CDDL HEADER in each
* file and include the License file at
* trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
* add the following below this CDDL HEADER, with the fields enclosed
* by brackets "[]" replaced with your own identifying * information:
*      Portions Copyright [yyyy] [name of copyright owner]
*
* CDDL HEADER END
*
*
*      Copyright 2008 Sun Microsystems, Inc.
*/

#include "common.h"
#include <errno.h>
#include <fcntl.h>
#include <io.h>
#include <stdio.h>
#include <sys/locking.h>
#include <time.h>

BOOL DEBUG = TRUE;
char * DEBUG_LOG_NAME = "native-windows.out";
DWORD MAX_DEBUG_LOG_SIZE = 500 * 1000;
char * getDebugLogFileName();
void debugInner(BOOL isError, const char *msg, va_list ap);
void deleteIfLargerThan(char * fileName, DWORD maxSize);

// ----------------------------------------------------
// Function used to create a process with the given command.
// The information about the process is stored in procInfo.
// The function returns TRUE if the process could be created
// and FALSE otherwise.
// ----------------------------------------------------
BOOL createChildProcess(char* command, BOOL background,
PROCESS_INFORMATION* procInfo)
{
  BOOL createOk;
  STARTUPINFO startInfo; // info to pass to the new process
  DWORD processFlag; // background process flag
  HANDLE hStdin;                  /* stdin */
  HANDLE hStdout;                 /* stdout */
  HANDLE hStderr;                 /* stderr */

  debug("Attempting to create child process '%s' background=%d.", command,
      background);

  // reset process info first
  ZeroMemory(procInfo, sizeof(PROCESS_INFORMATION));

  // initialize handles to pass to the child process
  ZeroMemory(&startInfo, sizeof(STARTUPINFO));
  startInfo.cb = sizeof(STARTUPINFO);
  startInfo.dwFlags |= STARTF_USESTDHANDLES;  // use handles above
  
  hStdin= GetStdHandle(STD_INPUT_HANDLE);
  SetHandleInformation (hStdin,  HANDLE_FLAG_INHERIT, FALSE);
  hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
  SetHandleInformation (hStdout, HANDLE_FLAG_INHERIT, FALSE);
  hStderr = GetStdHandle(STD_ERROR_HANDLE);
  SetHandleInformation (hStderr, HANDLE_FLAG_INHERIT, FALSE);

  // Create the child process
  processFlag = background == TRUE ? DETACHED_PROCESS : 0;
  createOk = CreateProcess(
    NULL,          // application name
    command,       // command line
    NULL,          // process security attributes
    NULL,          // primary thread security attributes
    TRUE,          // handles are inherited
    processFlag,   // creation flags
    NULL,          // use parent's environment
    NULL,          // use parent's current directory
    &startInfo,    // STARTUPINFO pointer
    procInfo       // receives PROCESS_INFORMATION
  );

  if (createOk)
  {
    debug("Successfully created child process '%s'.", command);
  }
  else
  {
    debugError("Failed to create child process '%s'.  Last error = %d.",
        command, GetLastError());
  }
  
  return createOk;
} // createChildProcess

// ----------------------------------------------------
// Function used to launch a process for the given command
// If the process could be created it returns the pid of
// the created process and -1 otherwise.
// ----------------------------------------------------
int spawn(const char* command, BOOL background)
{
  DWORD childPid = -1; // child's pid
  PROCESS_INFORMATION procInfo; // info on the new process
  BOOL createOk;

  createOk = createChildProcess((char*)command, background, &procInfo);

  if(createOk)
  {
    childPid = procInfo.dwProcessId;
  }

  if (childPid != -1)
  {
    debug("The PID of the spawned process is %d.", childPid);
    return childPid;
  }
  else
  {
    debugError("Could not get the PID of the spawned process.");
    return -1;
  }
} // spawn


// ---------------------------------------------------
// Debug utility.
// ---------------------------------------------------
void debug(const char *msg, ...)
{
  va_list ap;
	va_start (ap, msg);
  debugInner(FALSE, msg, ap);
  va_end (ap);
}

void debugError(const char *msg, ...)
{
  va_list ap;
	va_start (ap, msg);
  debugInner(TRUE, msg, ap);
  va_end (ap);
}

void debugInner(BOOL isError, const char *msg, va_list ap)
{
  static DWORD currentProcessPid = 0;
  static BOOL noMessageLogged = TRUE;
    
  // The file containing the log.
  char * logFile;
  FILE *fp;
	time_t rawtime;
  struct tm * timeinfo;
  char formattedTime[100];
  
  if (noMessageLogged)
  {
    currentProcessPid = GetCurrentProcessId();
    noMessageLogged = FALSE;
    debug("--------------- FIRST LOG MESSAGE FROM '%s' ---------------",
        _pgmptr);
  }

  // Time-stamp
  time(&rawtime);
  timeinfo = localtime(&rawtime);
  strftime(formattedTime, 100, "%Y/%m/%d %H:%M:%S", timeinfo);
  
  logFile = getDebugLogFileName();
  deleteIfLargerThan(logFile, MAX_DEBUG_LOG_SIZE);
  if ((fp = fopen(logFile, "a")) != NULL)
  {
    fprintf(fp, "%s: (pid=%d)  ", formattedTime, currentProcessPid);
    if (isError) 
    {
      fprintf(fp, "ERROR:  ");
      // It would be nice to echo to stderr, but that doesn't appear to work.
    }
          
    vfprintf(fp, msg, ap);

	  fprintf(fp, "\n");
    fclose(fp);
  }
  else
  {
    fprintf(stdout, "Could not create log file.\n");
  }
}

// ---------------------------------------------------------------
// Get the fully-qualified debug log file name.  The logic in this
// method assumes that the executable of this process is in a 
// direct subdirectory of the instance root.
// ---------------------------------------------------------------

char * getDebugLogFileName() 
{
  static char * logFile = NULL;
  char path [MAX_PATH];
  char execName [MAX_PATH];
  char * lastSlash;

  if (logFile != NULL) 
  {
    return logFile;
  }

  // Get the name of the executable.  
  GetModuleFileName (
    NULL,
    execName,
    MAX_PATH
    ); 

  // Cut everything after the last slash, twice.  This will take us back to the
  // instance root.
  // This logic assumes that we are in a directory above the instance root.
  lastSlash = strrchr(execName, '\\');
  lastSlash[0] = '\0';
  lastSlash = strrchr(execName, '\\');
  lastSlash[0] = '\0';

  sprintf(path, "%s\\logs\\%s", execName, DEBUG_LOG_NAME);
  logFile = _strdup(path);

  return logFile;
}

// ---------------------------------------------------------------
// Function called to know if the --debug option was passed
// when calling this executable or not.  The DEBUG variable is
// updated accordingly.
// ---------------------------------------------------------------

void updateDebugFlag(char* argv[], int argc)
{
  int i;
  DEBUG = FALSE;
  for (i=1; (i<argc) && !DEBUG; i++)
  {
    if (strcmp(argv[i], "--debug") == 0)
    {
      DEBUG = TRUE;
    }
  }
}

// ---------------------------------------------------------------
// Deletes a file if it's larger than the given maximum size.
// ---------------------------------------------------------------

void deleteIfLargerThan(char * fileName, DWORD maxSize)
{
  DWORD fileSize = 0;
  HANDLE fileHandle = CreateFile(
    fileName,
    0,
    FILE_SHARE_READ | FILE_SHARE_WRITE,
    NULL,
    OPEN_EXISTING,
    0,
    NULL
  );

  if (fileHandle == INVALID_HANDLE_VALUE) 
  {
    return;
  }

  fileSize = GetFileSize(fileHandle, NULL);

  CloseHandle(fileHandle);

  if (fileSize > maxSize)
  {
    DeleteFile(fileName);
  }
}
