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
    if ((strlen(relativePath) + strlen(instanceDir)) < maxSize)
    {
        sprintf(pidFile, "%s\\logs\\server.pid", instanceDir);
        returnValue = TRUE;
    }
    else
    {
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
    
    // Sometimes the lock on the system in windows takes time to be released.
    if (getPidFile(instanceDir, pidFile, PATH_SIZE))
    {
        while (fileExists(pidFile) && (nTries > 0) && !returnValue)
        {
            if (remove(pidFile) == 0)
            {
                returnValue = TRUE;
            }
            else
            {
                Sleep(500);
                nTries--;
            }
        }
    }
    
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
    
    if (getPidFile(instanceDir, pidFile, PATH_SIZE))
    {
        if ((f = fopen(pidFile, "r")) != NULL)
        {
            read = fread(buf, 1, sizeof(buf),f);
        }
        
        fclose(f);
        returnValue = (int)strtol(buf, (char **)NULL, 10);
    }
    else
    {
        returnValue = 0;
    }
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
    HANDLE procHandle = OpenProcess(
       PROCESS_TERMINATE               // to terminate the process
       | PROCESS_QUERY_INFORMATION,    // to get exit code
       FALSE,                          // handle is not inheritable
       pid
       );
       
    if (procHandle == NULL)
    {
       // process already dead
       processDead = TRUE;
    }
    else
    {
       if (!TerminateProcess(procHandle, 0))
       {
          // failed to terminate the process
          processDead = FALSE;
       }
       else
       {
          // wait for the process to end.
          DWORD exitCode;
          int nTries = 20;
          
          processDead = FALSE;
          while ((nTries > 0) && !processDead)
          {
             GetExitCodeProcess(procHandle, &exitCode);
             if (exitCode == STILL_ACTIVE)
             {
                 // process is still alive, let's wait 1 sec and loop again
                 Sleep(1000);
                 nTries--;
             }
             else
             {
                 processDead = TRUE;
             }
          }
       }
       CloseHandle(procHandle);
    }
    
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
    
    if (getPidFile(instanceDir, pidFile, PATH_SIZE))
    {
        if ((f = fopen(pidFile, "w")) != NULL)
        {
            fprintf(f, "%d", pid);
            fclose (f);
            returnValue = TRUE;
        }
        else
        {
            returnValue = FALSE;
        }
    }
    else
    {
        returnValue = FALSE;
    }
   
    return returnValue;
}  // createPidFile

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

   if (!createOk)
   {
      fprintf(stderr, "Failed to create child process [%s]\n", command);
   }

   return createOk;
} // createChildProcess

// ----------------------------------------------------
// Function used to launch a process for the given command
// If the process could be created it returns the pid of
// the created process and -1 otherwise.
// ----------------------------------------------------
int spawn(const char* command)
{
    DWORD childPid; // child's pid
    PROCESS_INFORMATION procInfo; // info on the new process
    BOOL createOk;
    
    createOk = createChildProcess((char*)command, TRUE, &procInfo);
    
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
        childPid = spawn(command);
        
        if (childPid > 0)
        {
            createPidFile(instanceDir, childPid);
            returnValue = childPid;
        }
        else
        {
            returnValue = -1;
        }
    }
    else
    {
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
    
    childPid = getPid(instanceDir);
    
    if (childPid != 0)
    {
        if (killProcess(childPid))
        {
            returnCode = 0;
            deletePidFile(instanceDir);
        }
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
        returnValue = spawn(command);
    }
    else
    {
        returnValue = -1;
    }
   
    return returnValue;
} // launch

// ----------------------------------------------------
// main function called by the executable.  This code is
// called by the start-ds.bat, stop-ds.bat and statuspanel.bat batch files.
//
// The code assumes that the first passed argument is the subcommand to be
// executed and the second argument the directory of the server.  The rest
// of the arguments are the arguments specific to each subcommand (see the
// comments for the functions start, stop and launch).
// ----------------------------------------------------
int main(int argc, char* argv[])
{
    int returnCode;
    char* subcommand = argv[1];
    char* instanceDir = argv[2];
    
    argv += 3;
    
    if (strcmp(subcommand, "start") == 0)
    {
        returnCode = start(instanceDir, argv);
    }
    else if (strcmp(subcommand, "stop") == 0)
    {
        returnCode = stop(instanceDir);
    }
    else if (strcmp(subcommand, "launch") == 0)
    {
        returnCode = launch(argv);
    }
    else
    {
        fprintf(stderr, "Unknown subcommand: [%s]", subcommand);
        returnCode = -1;
    }
    return returnCode;
}
