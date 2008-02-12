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
* by brackets "[]" replaced with your own identifying information:
*      Portions Copyright [yyyy] [name of copyright owner]
*
* CDDL HEADER END
*
*
*      Copyright 2008 Sun Microsystems, Inc.
*/

#include "winlauncher.h"

// ----------------------------------------------------
// Generates the pid file name for a given instanceDir.
// Returns TRUE if the command name could be initiated and
// FALSE otherwise (buffer overflow because the resulting
// string is bigger than maxSize).
// ----------------------------------------------------
BOOL getPidFile(const char* instanceDir, char* pidFile, unsigned int maxSize)
{
  BOOL returnValue;
  char* relativePath = "\\logs\\server.pid";

  debug("Attempting to get the PID file for instanceDir='%s'", instanceDir);

  if ((strlen(relativePath) + strlen(instanceDir)) < maxSize)
  {
    sprintf(pidFile, "%s\\logs\\server.pid", instanceDir);
    returnValue = TRUE;
    debug("PID file name is '%s'.", pidFile);
  }
  else
  {
    debugError("Unable to get the PID file name because the path was too long.");
    returnValue = FALSE;
  }
  return returnValue;
} // getPidFile


// ----------------------------------------------------
// Tells whether a file exists or not.  If the file exists
// returns TRUE and FALSE otherwise.
// ----------------------------------------------------
BOOL fileExists(const char *fileName)
{
  struct stat finfo;
  BOOL returnValue = FALSE;

  if(stat(fileName, &finfo) < 0)
  {
    returnValue = FALSE;
  }
  else
  {
    returnValue = TRUE;
  }

  debug("File '%s' does%s exist.", fileName, (returnValue ? "" : " not"));

  return returnValue;
} // fileExists


// ----------------------------------------------------
// Deletes the pid file for a given instance directory.
// If the file could be deleted (or it does not exist)
// returns TRUE and FALSE otherwise.
// ----------------------------------------------------
BOOL deletePidFile(const char* instanceDir)
{
  BOOL returnValue = FALSE;
  char pidFile[PATH_SIZE];
  int nTries = 10;

  debug("Attempting to delete the PID file from instanceDir='%s'.", instanceDir);
  // Sometimes the lock on the system in windows takes time to be released.
  if (getPidFile(instanceDir, pidFile, PATH_SIZE))
  {
    while (fileExists(pidFile) && (nTries > 0) && !returnValue)
    {
      debug("PID file '%s' exists, attempting to remove it.", instanceDir);
      if (remove(pidFile) == 0)
      {
        debug("Successfully removed PID file: '%s'.", pidFile);
        returnValue = TRUE;
      }
      else
      {
        nTries--;
        debug("Failed to remove the PID file.  Sleeping for a bit.  Will try %d more time(s).", nTries);
        Sleep(500);
      }
    }
  }

  debug("deletePidFile('%s') returning %d.", instanceDir, returnValue);
  return returnValue;
}  // deletePidFile


// ----------------------------------------------------
// Returns the pid stored in the pid file for a given server
// instance directory.  If the pid could not be retrieved
// it returns 0.
// ----------------------------------------------------
int getPid(const char* instanceDir)
{
  int returnValue;
  char pidFile[PATH_SIZE];
  FILE *f;
  char buf[BUF_SIZE];
  int read;

  debug("Attempting to get the PID for the server rooted at '%s'.", instanceDir);
  if (getPidFile(instanceDir, pidFile, PATH_SIZE))
  {
    if ((f = fopen(pidFile, "r")) != NULL)
    {
      read = fread(buf, 1, sizeof(buf),f);
      debug("Read '%s' from the PID file '%s'.", buf, pidFile);
    }

    if (f != NULL)
    {
      fclose(f);
      returnValue = (int)strtol(buf, (char **)NULL, 10);
    }
    else
    {
      char * msg = "File %s could not be opened.\nMost likely the server has already stopped.\n\n";
      debug(msg, pidFile);
      fprintf(stderr, msg, pidFile);
      returnValue = 0;
    }
  }
  else
  {
    returnValue = 0;
  }
  debug("getPid('%s') returning %d.", instanceDir, returnValue);
  return returnValue;
}  // getPid


// ----------------------------------------------------
// Kills the process associated with the provided pid.
// Returns TRUE if the process could be killed or the
// process did not exist and false otherwise.
// ----------------------------------------------------
BOOL killProcess(int pid)
{
  BOOL processDead;
  HANDLE procHandle;

  debug("killProcess(pid=%d)", pid);

  debug("Opening process with pid=%d.", pid);
  procHandle = OpenProcess(
  PROCESS_TERMINATE               // to terminate the process
  | PROCESS_QUERY_INFORMATION,    // to get exit code
  FALSE,                          // handle is not inheritable
  pid
  );

  if (procHandle == NULL)
  {
    debug("The process with pid=%d has already terminated.", pid);
    // process already dead
    processDead = TRUE;
  }
  else
  {
    if (!TerminateProcess(procHandle, 0))
    {
      debugError("Failed to terminate process (pid=%d) lastError=%d.", pid, GetLastError());
      // failed to terminate the process
      processDead = FALSE;
    }
    else
    {
      DWORD exitCode;
      int nTries = 20;

      debug("Successfully began termination process for (pid=%d).", pid);
      // wait for the process to end.

      processDead = FALSE;
      while ((nTries > 0) && !processDead)
      {
        GetExitCodeProcess(procHandle, &exitCode);
        if (exitCode == STILL_ACTIVE)
        {
          // process is still alive, let's wait 1 sec and loop again
          nTries--;
          debug("Process (pid=%d) has not yet exited.  Sleeping for 1 second and will try %d more time(s).", pid, nTries);
          Sleep(1000);
        }
        else
        {
          debug("Process (pid=%d) has exited with exit code %d.", pid, exitCode);
          processDead = TRUE;
        }
      }
    }
    CloseHandle(procHandle);
  }

  debug("killProcess(pid=%d) returning %d", pid, processDead);
  return processDead;
} // killProcess

// ----------------------------------------------------
// Creates the pid file for a given instance directory.
// and a given pid.
// If the file could be created returns TRUE and FALSE
// otherwise.
// ----------------------------------------------------
BOOL createPidFile(const char* instanceDir, int pid)
{
  BOOL returnValue = FALSE;
  char pidFile[PATH_SIZE];
  FILE *f;

  debug("createPidFile(instanceDir='%s',pid=%d)", instanceDir, pid);

  if (getPidFile(instanceDir, pidFile, PATH_SIZE))
  {
    if ((f = fopen(pidFile, "w")) != NULL)
    {
      fprintf(f, "%d", pid);
      fclose (f);
      returnValue = TRUE;
      debug("Successfully put pid=%d in the pid file '%s'.", pid, pidFile);
    }
    else
    {
      debugError("Couldn't create the pid file '%s' because the file could not be opened.", pidFile);
      returnValue = FALSE;
    }
  }
  else
  {
    debugError("Couldn't create the pid file because the pid file name could not be constructed.");
    returnValue = FALSE;
  }

  return returnValue;
}  // createPidFile


// ----------------------------------------------------
// Elaborate the command line: "cmd arg1 arg2..."
// If an arg contains white space(s) then add " " to protect them
// but don't do it for option (an option starts with -).
// Returns TRUE if the command name could be initiated and
// FALSE otherwise (buffer overflow because the resulting
// string is bigger than maxSize).
// ----------------------------------------------------
BOOL getCommandLine(const char* argv[], char* command, unsigned int maxSize)
{
  int curCmdInd = 0;
  int i = 0;
  BOOL overflow = FALSE;

  debug("Constructing full command line from arguments:");
  for (i = 0; (argv[i] != NULL); i++)
  {
    debug(" argv[%d]: %s", i, argv[i]);
  }

  i = 0;
  while ((argv[i] != NULL) && !overflow)
  {
    const char* curarg = argv[i++];
    if (i > 1)
    {
      if (curCmdInd + strlen(" ") < maxSize)
      {
        sprintf (&command[curCmdInd], " ");
        curCmdInd = strlen(command);
      }
      else
      {
        overflow = TRUE;
      }
    }

    if (curarg[0] != '\0')
    {
      int argInd = 0;

      if (curarg[0] == '"')
      {
        // there is a quote: no need to add extra quotes
      }
      else
      {
        while (curarg[argInd] != ' '
          && curarg[argInd] != '\0'
        && curarg[argInd] != '\n')
        {
          argInd++;
        }
      }
      if (curarg[0] != '"' && curarg[argInd] == ' ')
      {
        if (curCmdInd + strlen("\"\"") + strlen(curarg) < maxSize)
        {
          // no begining quote and white space inside => add quotes
          sprintf (&command[curCmdInd], "\"%s\"", curarg);
          curCmdInd = strlen (command);
        }
        else
        {
          overflow = TRUE;
        }
      }
      else
      {
        if (curCmdInd + strlen(curarg) < maxSize)
        {
          // no white space or quotes detected, keep the arg as is
          sprintf (&command[curCmdInd], "%s", curarg);
          curCmdInd = strlen (command);
        }
        else
        {
          overflow = TRUE;
        }
      }

    } else {
      if (curCmdInd + strlen("\"\"") < maxSize)
      {
        sprintf (&command[curCmdInd], "\"\"");
        curCmdInd = strlen (command);
      }
      else
      {
        overflow = TRUE;
      }
    }
  }

  if (overflow)
  {
    debugError("Failed to construct the full commandline because the buffer wasn't big enough.");
  }
  else
  {
    debug("The full commandline is '%s'.", command);
  }

  return !overflow;
} // getCommandLine

// ----------------------------------------------------
// Function called when we want to start the server.
// This function expects the following parameter to be passed:
// the directory of the server we want to start and the
// command line (and its argumets) that we want to execute (basically the java
// command that we want to start the server).  The main reasons
// to have the command line passed are:
// 1. Keep the native code as minimal as possible.
// 2. Allow the administrator some flexibility in the way the
// server is started by leaving most of the logic in the command-line.
//
// This approach makes things to be closer between what is proposed
// in windows and in UNIX systems.
//
// If the instance could be started the code will write the pid of the process
// of the server in file that can be used for instance to stop the server
// (see stop.c).
//
// Returns the pid of the process of the instance if it could be started and -1
// otherwise.
// ----------------------------------------------------
int start(const char* instanceDir, char* argv[])
{
  int returnValue;
  int childPid;

  char command[COMMAND_SIZE];

  if (getCommandLine(argv, command, COMMAND_SIZE))
  {
    childPid = spawn(command, TRUE);

    if (childPid > 0)
    {
      createPidFile(instanceDir, childPid);
      returnValue = childPid;
    }
    else
    {
      debugError("Couldn't start the child process because the spawn failed.");
      returnValue = -1;
    }
  }
  else
  {
    debugError("Couldn't start the child process because the full command line could not be constructed.");
    returnValue = -1;
  }

  return returnValue;
} // start


// ----------------------------------------------------
// Function called when we want to stop the server.
// This code is called by the stop-ds.bat batch file to stop the server
// in windows.
// This function expects just one parameter to be passed
// to the executable: the directory of the server we want
// to stop.
//
// If the instance could be stopped the pid file
// is removed.  This is done for security reasons: if we do
// not delete the pid file and the old pid of the process
// is used by a new process, when we call again this executable
// the new process will be killed.
// Note: even if the code in the class org.opends.server.core.DirectoryServer
// sets the pid file to be deleted on the exit of the process
// the file is not always deleted.
//
// Returns 0 if the instance could be stopped using the
// pid stored in a file of the server installation and
// -1 otherwise.
// ----------------------------------------------------
int stop(const char* instanceDir)
{
  int returnCode = -1;

  int childPid;

  debug("Attempting to stop the server running at root '%s'.", instanceDir);

  childPid = getPid(instanceDir);

  if (childPid != 0)
  {
    if (killProcess(childPid))
    {
      returnCode = 0;
      deletePidFile(instanceDir);
    }
  }
  else
  {
    debug("Could not stop the server running at root '%s' because the pid could not be located.", instanceDir);
  }

  return returnCode;
} // stop


// ----------------------------------------------------
// Function called when we want to launch simply a process without attaching
// it to any command prompt (the difference with start is basically that here
// we create no pid file).
// This code is called for instance by the statuspanel.bat batch file to launch
// the status panel on windows.

// The goal of these methods is:
// Be able to launch batch files with double-click and not having a
// prompt-window associated with it.
// Launch batch files from the prompt that generate a java process that does not
// block the prompt and that keeps running even if the prompt window is closed.
//
// This function expects the following parameter to be passed:
// the directory of the server we want to start and the
// command line that we want to execute (basically the java
// command that we want to display the status panel).  The main reasons
// to have the command line passed are:
// 1. Keep the native code as minimal as possible.
// 2. Allow the administrator some flexibility in the way the
// server is started by leaving most of the logic in the command-line.
//
// Returns the pid of the process associated with the command if it could be
// launched and -1 otherwise.
// ----------------------------------------------------
int launch(char* argv[])
{
  int returnValue;

  char command[COMMAND_SIZE];

  if (getCommandLine(argv, command, COMMAND_SIZE))
  {
    returnValue = spawn(command, TRUE);
    if (returnValue <= 0)
    {
      debugError("Failed to launch the child process '%s'.", command);
    }
    else
    {
      debug("Successfully launched the child process '%s'.", command);
    }
  }
  else
  {
    debugError("Couldn't launch the child process because the full command line could not be constructed.");
    returnValue = -1;
  }

  return returnValue;
} // launch

//----------------------------------------------------
// Function called when we want to launch a process and it to be run as
// administrator on Vista (the binary containing this function must have
// a manifest specifying that).
// Returns the exit code of the process associated with the command if it
// could be launched and -1 otherwise.
//----------------------------------------------------
int run(char* argv[])
{
  PROCESS_INFORMATION procInfo; // info on the new process
  BOOL createOk;
  DWORD exitCode;
  int returnValue = -1;
  int millisToWait = 30000;
  int waitedMillis = 0;

  char command[COMMAND_SIZE];

  if (getCommandLine(argv, command, COMMAND_SIZE))
  {
    createOk = createChildProcess((char*)command, TRUE, &procInfo);

    if(createOk)
    {
      GetExitCodeProcess(procInfo.hProcess, &exitCode);
      while (exitCode == STILL_ACTIVE)
      {
        GetExitCodeProcess(procInfo.hProcess, &exitCode);
        Sleep(500);
        waitedMillis += 500;
        if (waitedMillis > millisToWait)
        {
          break;
        }
      }
      returnValue = exitCode;
    }
  }
  return returnValue;
} // run

// ----------------------------------------------------
// main function called by the executable.  This code is
// called by the start-ds.bat, stop-ds.bat and statuspanel.bat batch files.
//
// The code assumes that the first passed argument is the subcommand to be
// executed and for start and stop the second argument the directory of the
// server.
// The rest of the arguments are the arguments specific to each subcommand (see
// the comments for the functions start, stop and launch).
// ----------------------------------------------------
int main(int argc, char* argv[])
{
  int returnCode;
  char* subcommand = NULL;
  char* instanceDir = NULL;
  int i;

  debug("main called.");
  for (i = 0; i < argc; i++) {
    debug("  argv[%d] = '%s'", i, argv[i]);
  }

  if (argc < 3) {
    char * msg =
      "Expected command line args of [subcommand], but got %d arguments.\n";
    debugError(msg, argc - 1);
    fprintf(stderr, msg, argc - 1);
	  return -1;
  }

  subcommand = argv[1];

  if (strcmp(subcommand, "start") == 0)
  {
    instanceDir = argv[2];
    argv += 3;
    returnCode = start(instanceDir, argv);
  }
  else if (strcmp(subcommand, "stop") == 0)
  {
    instanceDir = argv[2];
    argv += 3;
    returnCode = stop(instanceDir);
  }
  else if (strcmp(subcommand, "launch") == 0)
  {
    argv += 2;
    returnCode = launch(argv);
  }
  else if (strcmp(subcommand, "run") == 0)
  {
    argv += 2;
    returnCode = run(argv);
  }
  else
  {
    char * msg = "Unknown subcommand: [%s]\n";
    debugError(msg, subcommand);
    fprintf(stderr, msg, subcommand);
    returnCode = -1;
  }
  debug("main finished. Returning %d", returnCode);
  return returnCode;
}

