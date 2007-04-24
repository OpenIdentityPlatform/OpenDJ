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
*      Portions Copyright 2007 Sun Microsystems, Inc.
*/

#include "common.h"

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

  // reset process info first
  ZeroMemory(procInfo, sizeof(PROCESS_INFORMATION));

  // initialize handles to pass to the child process
  ZeroMemory(&startInfo, sizeof(STARTUPINFO));
  startInfo.cb = sizeof(STARTUPINFO);
  startInfo.dwFlags |= STARTF_USESTDHANDLES;  // use handles above

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

  return createOk;
} // createChildProcess

// ----------------------------------------------------
// Function used to launch a process for the given command
// If the process could be created it returns the pid of
// the created process and -1 otherwise.
// ----------------------------------------------------
int spawn(const char* command, BOOL background)
{
  DWORD childPid; // child's pid
  PROCESS_INFORMATION procInfo; // info on the new process
  BOOL createOk;

  createOk = createChildProcess((char*)command, background, &procInfo);

  if(createOk)
  {
    childPid = procInfo.dwProcessId;
  }

  if (childPid != -1)
  {
    return childPid;
  }
  else
  {
    return -1;
  }
} // spawn

