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

#include "service.h"

int _serviceCurStatus;
SERVICE_STATUS_HANDLE *_serviceStatusHandle;
HANDLE _terminationEvent = NULL;
char *_instanceDir = NULL;
HANDLE _eventLog = NULL;
char * _logFile = NULL;

// ----------------------------------------------------
// Register a service handler to the service control dispatcher.
// A service handler will manage the control function such as:
// stop, pause, continue, shutdown, interrogate. The control functions
// are sent by the service control dispatcher upon user request
// (ie. NET STOP)
//
// serviceName     the internal name of the service (unique in the system)
// serviceHandler  the handler of the service
// serviceStatusHandle  the service status handle returned by the SCM
// The functions returns SERVICE_RETURN_OK if we could start the service
// and SERVICE_RETURN_ERROR otherwise.
// ----------------------------------------------------

ServiceReturnCode registerServiceHandler (
char*                   serviceName,
LPHANDLER_FUNCTION      serviceHandler,
SERVICE_STATUS_HANDLE*  serviceStatusHandle
)
{
  ServiceReturnCode returnValue;

  debug("Registering the service handler for '%s'", serviceName);

  // register the service to the service control dispatcher (SCM)
  *serviceStatusHandle = RegisterServiceCtrlHandler (
  serviceName, (LPHANDLER_FUNCTION) serviceHandler
  );
  if (serviceStatusHandle == NULL)
  {
    returnValue = SERVICE_RETURN_ERROR;
  }
  else
  {
    returnValue = SERVICE_RETURN_OK;
  }

  debug("registerServiceHandler returning '%d'", returnValue);
  
  return returnValue;
}  // registerServiceHandler



// ---------------------------------------------------
// Reports a log event of a given type, id and arguments
// serviceBinPath the binary associated with the service.
// The function returns TRUE if the event could be logged and FALSE otherwise.
// ---------------------------------------------------
BOOL reportLogEvent(WORD eventType, DWORD eventId, WORD argCount,
const char** args)
{
  BOOL reportOk;

  if (argCount > 0)
  {
    // report the event
    reportOk = ReportEvent(
    _eventLog,              // event log handle
    eventType,              // info, warning, error
    WIN_FACILITY_NAME_OPENDS,     // unique category for OPENDS
    eventId,
    NULL,               // no user security identifier
    argCount,             // number of args
    0,                  // raw data size
    args,               // args
    NULL                // no raw data
    );
  }
  else
  {
    // report the event
    reportOk = ReportEvent(
    _eventLog,              // event log handle
    eventType,              // info, warning, error
    WIN_FACILITY_NAME_OPENDS,     // unique category for OPENDS
    eventId,
    NULL,               // no user security identifier
    argCount,             // number of args
    0,                  // raw data size
    NULL,               // args
    NULL                // no raw data
    );
  }


  return reportOk;
}

// ---------------------------------------------------------------
// Get a handle to the Service Control Manager (SCM).
// accessRights  the desired access rights; generic access are:
//  - GENERIC_READ     use to get the list of services
//  - GENERIC_WRITE    use to create & remove a service
//  - GENERIC_EXECUTE
//  - GENERIC_ALL
// scm  the handler to the SCM
// The function returns SERVICE_RETURN_OK if we could get the SCM
// and SERVICE_RETURN_ERROR otherwise.
// ---------------------------------------------------------------

ServiceReturnCode openScm(DWORD accessRights, SC_HANDLE *scm)
{
  ServiceReturnCode returnValue;

  // open Service Control Manager
  *scm = (SC_HANDLE)OpenSCManager (
  NULL,           // local machine
  NULL,           // ServicesActive database
  accessRights    // desired rights
  );
  if (scm == NULL)
  {
    debugError("Failed to open the Service Control Manager.  Last error = %d", GetLastError());
    returnValue = SERVICE_RETURN_ERROR;
  }
  else
  {
    debug("Successfully opened the Service Control Manager.");
    returnValue = SERVICE_RETURN_OK;
  }
  return returnValue;
} // openScm

// ---------------------------------------------------
// Creates the registry key to send events based on the name of the service.
// serviceName the serviceName.
// serviceBinPath the binary associated with the service.
// The function returns TRUE if the key could be registered (or was already
// registered) and FALSE otherwise.
// ---------------------------------------------------
BOOLEAN createRegistryKey(char* serviceName)
{
  // true if the server is already registered
  BOOL alreadyRegistered = FALSE;

  // false as soon as an error occurs
  BOOL success = TRUE;

  // handle to the created/opened key
  HKEY hkey = NULL;

  // Create the event source subkey (or open it if it already exists)
  char subkey [MAX_REGISTRY_KEY];

  long result;

  DWORD nbCategories = 1;

  // get the full path to the current executable file: is safe to do it
  // here because we already required it to figure out to get the service
  // name based on the command to run associated with it.
  char execName [MAX_PATH];

  debug("Creating registry key for service '%s'.", serviceName);

  GetModuleFileName (
  NULL,
  execName,
  MAX_PATH
  );

  // Check whether the Registry Key is already created,
  // If so don't create a new one.
  sprintf (subkey, EVENT_LOG_KEY, serviceName);
  result = RegOpenKeyEx(
  HKEY_LOCAL_MACHINE,
  subkey,
  0,
  KEY_QUERY_VALUE,      // to query the values of a registry key.
  &hkey           // OUT
  );
  if (result == ERROR_SUCCESS)
  {
    debug("The registry key for service '%s' already exists.", serviceName);
    alreadyRegistered = TRUE;
    success = FALSE;
  }

  if (success)
  {
    DWORD disposition;
    result = RegCreateKeyEx(
    HKEY_LOCAL_MACHINE,         //
    subkey,                     //
    0,                          // reserved
    NULL,                       // key object class
    REG_OPTION_NON_VOLATILE,    // option
    KEY_WRITE,                  // desired access
    NULL,                       // hkey cannot be inherited
    &hkey,                      // OUT
    &disposition                // OUT new key / existing key
    );
    if (result != ERROR_SUCCESS)
    {
      debugError("RegCreateKeyEx failed, result=%d.", result);
      success = FALSE;
    }
  }

  if (success)
  {
    result = RegSetValueEx(
    hkey,                           // subkey handle
    "EventMessageFile",             // value name
    0,                              // must be zero
    REG_EXPAND_SZ,          // value type
    (LPBYTE)execName,       // pointer to value data
    (DWORD) (lstrlen(execName) + 1)
    * sizeof(TCHAR)         // length of value data
    );
    if (result != ERROR_SUCCESS)
    {
      debugError("RegSetValueEx('EventMessageFile') failed, result=%d.", result);
      success = FALSE;
    }
  }

  // Set the supported event types
  if (success)
  {
    DWORD supportedTypes =
    EVENTLOG_SUCCESS
    | EVENTLOG_ERROR_TYPE
    | EVENTLOG_WARNING_TYPE
    | EVENTLOG_INFORMATION_TYPE;

    result = RegSetValueEx(
    hkey,                          // subkey handle
    "TypesSupported",              // value name
    0,                             // must be zero
    REG_DWORD,                     // value type
    (LPBYTE) &supportedTypes,      // pointer to value data
    sizeof(DWORD)                  // length of value data
    );
    if (result != ERROR_SUCCESS)
    {
      debugError("RegSetValueEx('TypesSupported') failed, result=%d.", result);
      success = FALSE;
    }
  }

  // Set the category message file
  if (success)
  {
    result = RegSetValueEx(
    hkey,                           // subkey handle
    "CategoryMessageFile",          // value name
    0,                              // must be zero
    REG_EXPAND_SZ,                  // value type
    (LPBYTE)execName,       // pointer to value data
    (DWORD) (lstrlen(execName) + 1)
    *sizeof(TCHAR)          // length of value data
    );
    if (result != ERROR_SUCCESS)
    {
      debugError("RegSetValueEx('CategoryMessageFile') failed, result=%d.", result);
      success = FALSE;
    }
  }

  // Set the number of categories: 1 (OPENDS)
  if (success)
  {
    long result = RegSetValueEx(
    hkey,                    // subkey handle
    "CategoryCount",         // value name
    0,                       // must be zero
    REG_DWORD,               // value type
    (LPBYTE) &nbCategories,  // pointer to value data
    sizeof(DWORD)            // length of value data
    );
    if (result != ERROR_SUCCESS)
    {
      debugError("RegSetValueEx('CategoryCount') failed, result=%d.", result);
      success = FALSE;
    }
  }

  // close the key before leaving
  if (hkey != NULL)
  {
    RegCloseKey (hkey);
  }

  if (alreadyRegistered || success)
  {
    return TRUE;
  }
  else
  {
    debugError("Could not create a registry key.");
    return FALSE;
  }
}  // createRegistryKey


// ---------------------------------------------------
// Removes the registry key to send events based on the name of the service.
// serviceName the serviceName.
// The function returns TRUE if the key could be unregistered (or it was not
// registered) and FALSE otherwise.
// ---------------------------------------------------
BOOLEAN removeRegistryKey(char* serviceName)
{
  BOOL returnValue = FALSE;

  // Create the event source subkey (or open it if it already exists)
  char subkey [MAX_REGISTRY_KEY];

  long result;

  HKEY hkey = NULL;

  debug("Removing registry key for service '%s'.", serviceName);

  // Check whether the Registry Key is already created,
  // If so don't create a new one.
  sprintf (subkey, EVENT_LOG_KEY, serviceName);
  result = RegOpenKeyEx(
  HKEY_LOCAL_MACHINE,
  subkey,
  0,
  KEY_QUERY_VALUE,      // to query the values of a registry key.
  &hkey           // OUT
  );
  if (result != ERROR_SUCCESS)
  {
    debug("The registry key for service '%s' does not exist, so we do not need to remove it.", serviceName);
    // Assume that the registry key does not exist.
    returnValue = TRUE;
  }
  else
  {
    result = RegDeleteKey (HKEY_LOCAL_MACHINE, subkey);
    if (result == ERROR_SUCCESS)
    {
      returnValue = TRUE;
    }
    else
    {
      debugError("RegDeleteKey('%s') failed, result=%d.", subkey, result);
    }
  }

  return returnValue;
}  // removeRegistryKey

// ---------------------------------------------------
// Register the source of event and returns the handle
// for the event log.
// serviceName the serviceName.
// ---------------------------------------------------
HANDLE registerEventLog(char *serviceName)
{
  HANDLE eventLog = NULL;

  // subkey under Eventlog registry key
  char subkey [MAX_SERVICE_NAME];
  debug("Registering the Event Log for service '%s'.", serviceName);

  sprintf (subkey, serviceName);

  eventLog = RegisterEventSource(
  NULL,      // local host
  subkey     // subkey under Eventlog registry key
  );

  return eventLog;

}  // registerEventLog

// ---------------------------------------------------
// Deregister the source of event.
// ---------------------------------------------------
void deregisterEventLog()
{
  if (_eventLog != NULL)
  {
    debug("Deregistering the Event Log.");
    DeregisterEventSource(_eventLog);
  }
}

// ----------------------------------------------------
// Check if the server is running or not.
// The functions returns SERVICE_RETURN_OK if we could determine if
// the server is running or not and false otherwise.
// ----------------------------------------------------
ServiceReturnCode isServerRunning(BOOL *running)
{
  ServiceReturnCode returnValue;
  char* relativePath = "\\locks\\server.lock";
  char lockFile[MAX_PATH];
  debug("Determining if the server is running.");
  
  if (strlen(relativePath)+strlen(_instanceDir)+1 < MAX_PATH)
  {
    int fd;

    sprintf(lockFile, "%s%s", _instanceDir, relativePath);
    debug("When determining whether the server is running, the lock file name is '%s'.", lockFile);
  
    fd = _open(lockFile, _O_RDWR);

    if (fd != -1)
    {
      debug("Able to open the lock file '%s'.", lockFile);
      returnValue = SERVICE_RETURN_OK;
      // Test if there is a lock
      /* Lock some bytes and read them. Then unlock. */
      if(_locking(fd, LK_NBLCK, 1) != -1)
      {
        debug("Able to lock '%s', so the server is not running.", lockFile);
        *running = FALSE;
        _locking(fd, LK_UNLCK, 1);
      }
      else
      {
        if (errno == EACCES)
        {
          debug("Unable to lock '%s', so the server is running.", lockFile);
          *running = TRUE;
        }
        else
        {
          *running = FALSE;
          returnValue = SERVICE_RETURN_ERROR;
          debugError("Unexpected error locking: %d", errno);
        }
      }
      _close(fd);
    }
    else
    {
      debug("Could not open lock file '%s', which means the server is not running.", lockFile);
      *running = FALSE;
      returnValue = SERVICE_RETURN_ERROR;
    }
  }
  else
  {
    debug("Lock file path is too long.");
    *running = FALSE;
    returnValue = SERVICE_RETURN_ERROR;
  }

  return returnValue;
}  // isServerRunning

// ----------------------------------------------------
// Start the application using start-ds.bat
// The functions returns SERVICE_RETURN_OK if we could start the server
// and SERVICE_RETURN_ERROR otherwise.
// ----------------------------------------------------
ServiceReturnCode doStartApplication()
{
  ServiceReturnCode returnValue;
  // init out params
  char* relativePath = "\\bat\\start-ds.bat";
  char command[COMMAND_SIZE];


  debug("doStartApplication called.");

  
  if (strlen(relativePath)+strlen(_instanceDir)+1 < COMMAND_SIZE)
  {
    sprintf(command, "\"%s%s\" --windowsNetStart", _instanceDir, relativePath);

    // launch the command
    debug("doStartApplication attempting to spawn '%s'", command);
    if (spawn(command, FALSE) != -1)
    {
      // Try to see if server is really running
      int nTries = 10;
      BOOL running = FALSE;
      
      debug("doStartApplication: the spawn of the process worked.  Command: '%s'", command);
      // Wait to be able to launch the java process in order it to free the lock
      // on the file.
      debug("Sleeping for 3 seconds to allow the process to free the lock.");
      Sleep(3000);
      while ((nTries > 0) && !running)
      {
        nTries--;
        if (isServerRunning(&running) != SERVICE_RETURN_OK)
        {
          break;
        }
        if (!running)
        {
          debug("Sleeping for 2 seconds to allow the process to free the lock.  %d tries remaining.", nTries);
          Sleep(2000);
        }
      }
      if (running)
      {
        returnValue = SERVICE_RETURN_OK;
        debug("doStartApplication: server running.");
      }
      else
      {
        returnValue = SERVICE_RETURN_ERROR;
        debug("doStartApplication: server not running.");
      }
    }
    else
    {
      
      returnValue = SERVICE_RETURN_ERROR;
      debug("doStartApplication: spawn failed.  Sent command: '%s'", command);
    }
  }
  else
  {
    returnValue = SERVICE_RETURN_ERROR;
    debug("doStartApplication: the command path name is too long.");
  }
  return returnValue;
}  // doStartApplication

// ----------------------------------------------------
// Start the application using stop-ds.bat
// The functions returns SERVICE_RETURN_OK if we could stop the server
// and SERVICE_RETURN_ERROR otherwise.
// ----------------------------------------------------
ServiceReturnCode doStopApplication()
{
  ServiceReturnCode returnValue;
  // init out params
  char* relativePath = "\\bat\\stop-ds.bat";
  char command[COMMAND_SIZE];
  if (strlen(relativePath)+strlen(_instanceDir)+1 < COMMAND_SIZE)
  {
    sprintf(command, "\"%s%s\" --windowsNetStop", _instanceDir, relativePath);

    // launch the command
    if (spawn(command, FALSE) != -1)
    {
      // Try to see if server is really stopped
      int nTries = 10;
      BOOL running = TRUE;
      
      debug("doStopApplication: the spawn of the process worked.");
      
      // Wait to be able to launch the java process in order it to free the lock
      // on the file.
      Sleep(3000);
      while ((nTries > 0) && running)
      {
        if (isServerRunning(&running) != SERVICE_RETURN_OK)
        {
          break;
        }
        if (running)
        {
          Sleep(2000);
        }
      }
      if (!running)
      {
        returnValue = SERVICE_RETURN_OK;
        debug("doStopApplication: server stopped.");
      }
      else
      {
        returnValue = SERVICE_RETURN_ERROR;
        debug("doStopApplication: server NOT stopped.");
      }
    }
    else
    {
      returnValue = SERVICE_RETURN_ERROR;
      debug("doStopApplication: spawn failed.  Sent command: %s", command);
    }
  }
  else
  {
    returnValue = SERVICE_RETURN_ERROR;
    debug("doStopApplication: the command path name is too long.");
  }
  return returnValue;
}  // doStopApplication

// ---------------------------------------------------------------
// Build the path to the binary that contains serviceMain.
// Actually, the binary is the current executable file...
// serviceBinPath  the path to the service binary.
// instanceDir the instanceDirectory.
// The string stored in serviceBinPath looks like
// <SERVER_ROOT>/lib/service.exe start <_instanceDir>
// It is up to the caller of the function to allocate
// at least COMMAND_SIZE bytes in serviceBinPath.
// The function returns SERVICE_RETURN_OK if we could create the binary
// path name and SERVICE_RETURN_ERROR otherwise.
// ---------------------------------------------------------------

ServiceReturnCode createServiceBinPath(char* serviceBinPath)
{
  ServiceReturnCode returnValue = SERVICE_RETURN_OK;

  // get the full path to the current executable file
  char fileName [MAX_PATH];
  DWORD result = GetModuleFileName (
  NULL,    // get the path to the current executable file
  fileName,
  MAX_PATH
  );

  if (result == 0)
  {
    // failed to get the path of the executable file
    returnValue = SERVICE_RETURN_ERROR;
    debug("Could not get the path of the executable file.");
  }
  else
  {
    debug("When determining the service bin path, the module file name is '%s'.", fileName);

    if (result == MAX_PATH)
    {
      // buffer was too small, executable name is probably not valid
      returnValue = SERVICE_RETURN_ERROR;
      debug("The name of the module file is too long.");
    }
    else
    {
      if ((strlen(fileName) + strlen(" start ") + strlen(_instanceDir) + 2 * strlen("\"\""))
        < COMMAND_SIZE)
      {
        sprintf(serviceBinPath, "\"%s\" start \"%s\"", fileName,
        _instanceDir);
      }
      else
      {
        char * msg = "The name of the resulting windows service command is too long.\n";
        debug(msg);
        // buffer was too small, executable name is probably not valid
        returnValue = SERVICE_RETURN_ERROR;
        fprintf(stdout, msg);
      }
    }
  }

  return returnValue;
} // createServiceBinPath

// ----------------------------------------------------
// Returns the service name that maps the command used to start the
// product. All commands are supposed to be unique because they have
// the instance dir as parameter.
//
// The functions returns SERVICE_RETURN_OK if we could get a service name
// and SERVICE_RETURN_ERROR otherwise.
// The serviceName buffer must be allocated OUTSIDE the function and its
// minimum size must be of 256 (the maximum string length of a Service Name).
// ----------------------------------------------------

ServiceReturnCode getServiceName(char* cmdToRun, char* serviceName)
{
  // returned status code
  ServiceReturnCode returnValue = SERVICE_RETURN_OK;

  // retrieve list of services
  ServiceDescriptor* serviceList = NULL;
  int nbServices = -1;

  strcpy(serviceName, "");

  debug("Attempting to get the service name assuming command to run is '%s'.", cmdToRun);

  returnValue = getServiceList(&serviceList, &nbServices);

  // go through the list of services and search for the service name
  // whose display name is [displayName]
  if (returnValue == SERVICE_RETURN_OK)
  {
    int i;
    returnValue = SERVICE_RETURN_ERROR;
    if (nbServices > 0)
    {
      for (i = 0; i<nbServices; i++)
      {
        ServiceDescriptor curService = serviceList[i];
        if (curService.cmdToRun != NULL)
        {
          if (_stricmp(cmdToRun, curService.cmdToRun) == 0)
          {
            if (strlen(curService.serviceName) < MAX_SERVICE_NAME)
            {
              // This function assumes that there are at least
              // MAX_SERVICE_NAME (256) characters reserved in
              // servicename.
              sprintf(serviceName, curService.serviceName);
              returnValue = SERVICE_RETURN_OK;
            }
            else
            {
              debug("The service name found is too long: '%s'", curService.serviceName);
            }
            break;
          }
        }
      }
      free (serviceList);
    }
  }
  else
  {
    debug("getServiceName: could not get service list.");
  }

  debug("The service name was found to be '%s'.", serviceName);
  
  return returnValue;

}  // getServiceName

// ----------------------------------------------------
// Set the current status for the service.
//
// statusToSet current service status to set
// win32ExitCode determine which exit code to use
// serviceExitCode service code to return in case win32ExitCode says so
// checkPoint incremental value use to report progress during a lenghty
// operation (start, stop...).
// waitHint estimated time required for a pending operation (in ms); if
// the service has not updated the checkpoint or change the state then
// the service controler thinks the service should be stopped!
// serviceStatusHandle  the handle used to set the service status
// The functions returns SERVICE_RETURN_OK if we could start the service
// and SERVICE_RETURN_ERROR otherwise.
// ----------------------------------------------------

ServiceReturnCode updateServiceStatus (
DWORD  statusToSet,
DWORD  win32ExitCode,
DWORD  serviceExitCode,
DWORD  checkPoint,
DWORD  waitHint,
SERVICE_STATUS_HANDLE *serviceStatusHandle
)
{
  ServiceReturnCode returnValue;



  // elaborate service type:
  // SERVICE_WIN32_OWN_PROCESS means this is not a driver and there is
  // only one service in the process
  DWORD serviceType = SERVICE_WIN32_OWN_PROCESS;

  // elaborate the commands supported by the service:
  // - STOP        customer has performed a stop-ds (or NET STOP)
  // - SHUTDOWN    the system is rebooting
  // - INTERROGATE service controler can interogate the service
  // - No need to support PAUSE/CONTINUE
  //
  // Note: INTERROGATE *must* be supported by the service handler
  DWORD controls;
  SERVICE_STATUS serviceStatus;
  BOOL success;

  debug("Updating the service status.  statusToSet=%d win32ExitCode=%d serviceExitCode=%d checkPoint=%d waitHint=%d",
        statusToSet, win32ExitCode, serviceExitCode,
        checkPoint, waitHint);
  
  if (statusToSet == SERVICE_START_PENDING)
  {
    debug("Service start pending.");
    // do not accept any command when the service is starting up...
    controls = SERVICE_ACCEPT_NONE;
  }
  else
  {
    controls =
    SERVICE_ACCEPT_STOP
    | SERVICE_ACCEPT_SHUTDOWN
    | SERVICE_CONTROL_INTERROGATE;
  }

  // fill in the status structure
  serviceStatus.dwServiceType              = serviceType;
  serviceStatus.dwCurrentState             = statusToSet;
  serviceStatus.dwControlsAccepted         = controls;
  serviceStatus.dwWin32ExitCode            = win32ExitCode;
  serviceStatus.dwServiceSpecificExitCode  = serviceExitCode;
  serviceStatus.dwCheckPoint               = checkPoint;
  serviceStatus.dwWaitHint                 = waitHint;


  // set the service status

  success = SetServiceStatus(
  *serviceStatusHandle,
  &serviceStatus
  );

  if (!success)
  {
    debugError("Failed to set the service status.  Last error = %d.", GetLastError());
    returnValue = SERVICE_RETURN_ERROR;
  }
  else
  {
    returnValue = SERVICE_RETURN_OK;
  }

  return returnValue;
}  // updateServiceStatus

// ----------------------------------------------------
// This function is the "main" of the service. It has been registered
// to the SCM by the main right after the service has been started through
// NET START command.
//
//  The job of the serviceMain is
//
//  1- to register a handler to manage the commands STOP, PAUSE, CONTINUE,
//     SHUTDOWN and INTERROGATE sent by the SCM
//  2- to start the main application using "start-ds"
//
//  The serviceMain will return only when the service is terminated.
// ----------------------------------------------------
void serviceMain(int argc, char* argv[])
{
  // returned status
  char cmdToRun[COMMAND_SIZE];
  char serviceName[MAX_SERVICE_NAME];
  ServiceReturnCode code;
  // a checkpoint value indicate the progress of an operation
  DWORD checkPoint = CHECKPOINT_FIRST_VALUE;
  SERVICE_STATUS_HANDLE serviceStatusHandle;

  // __debugbreak();

  debug("serviceMain called.");
  
  code = createServiceBinPath(cmdToRun);

  if (code == SERVICE_RETURN_OK)
  {
    code = getServiceName(cmdToRun, serviceName);
    if (code != SERVICE_RETURN_OK)
    {
      debug("serviceMain: could not get service name.");
    }
  }
  else
  {
    debug("serviceMain: failed to create service bin path.");
  }

  if (code == SERVICE_RETURN_OK)
  {
    // first register the service control handler to the SCM
    code = registerServiceHandler(serviceName,
    (LPHANDLER_FUNCTION) serviceHandler,
    &serviceStatusHandle);
    if (code == SERVICE_RETURN_OK)
    {
      _serviceStatusHandle = &serviceStatusHandle;
    }
    else
    {
      debug("serviceMain: failed to register service handler.");
    }
  }


  // update the service status to START_PENDING
  if (code == SERVICE_RETURN_OK)
  {
    _serviceCurStatus = SERVICE_START_PENDING;
    code = updateServiceStatus (
    SERVICE_START_PENDING,
    NO_ERROR,
    0,
    checkPoint++,
    TIMEOUT_CREATE_EVENT,
    _serviceStatusHandle);
  }

  // create an event to signal the application termination
  if (code == SERVICE_RETURN_OK)
  {
    _terminationEvent = CreateEvent(
    NULL,   // handle is not inherited by the child process
    TRUE,   // event has to be reset manually after a signal
    FALSE,  // initial state is "non signaled"
    NULL    // the event has no name
    );
  }

  // update the service status to START_PENDING
  if (code == SERVICE_RETURN_OK)
  {
    _serviceCurStatus = SERVICE_START_PENDING;
    updateServiceStatus (
    _serviceCurStatus,
    NO_ERROR,
    0,
    checkPoint++,
    TIMEOUT_START_SERVICE,
    _serviceStatusHandle
    );
  }

  // start the application
  if (code == SERVICE_RETURN_OK)
  {
    WORD argCount = 1;
    const char *argc[] = {_instanceDir};
    code = doStartApplication();

    switch (code)
    {
      case SERVICE_RETURN_OK:
      // start is ok
      _serviceCurStatus = SERVICE_RUNNING;
      updateServiceStatus (
      _serviceCurStatus,
      NO_ERROR,
      0,
      CHECKPOINT_NO_ONGOING_OPERATION,
      TIMEOUT_NONE,
      _serviceStatusHandle
      );
      reportLogEvent(
      EVENTLOG_SUCCESS,
      WIN_EVENT_ID_SERVER_STARTED,
      argCount, argc
      );
      break;

      default:
      debugError("doApplication() failed");
      code = SERVICE_RETURN_ERROR;
      _serviceCurStatus = SERVICE_STOPPED;
      updateServiceStatus (
      _serviceCurStatus,
      ERROR_SERVICE_SPECIFIC_ERROR,
      -1,
      CHECKPOINT_NO_ONGOING_OPERATION,
      TIMEOUT_NONE,
      _serviceStatusHandle);
      reportLogEvent(
      EVENTLOG_ERROR_TYPE,
      WIN_EVENT_ID_SERVER_START_FAILED,
      argCount, argc
      );
    }
  }
  else
  {
    updateServiceStatus (
    _serviceCurStatus,
    ERROR_SERVICE_SPECIFIC_ERROR,
    0,
    CHECKPOINT_NO_ONGOING_OPERATION,
    TIMEOUT_NONE,
    _serviceStatusHandle);
  }

  // if all is ok wait for the application to die before we leave
  if (code == SERVICE_RETURN_OK)
  {
    debug("Waiting indefinitely for the application to exit.");
    WaitForSingleObject (_terminationEvent, INFINITE);
    debug("The application has exited.");
  }

  // update the service status to STOPPED if it's not already done
  if ((_serviceCurStatus != SERVICE_STOPPED) &&
    (_serviceStatusHandle != NULL))
  {
    _serviceCurStatus = SERVICE_STOPPED;
    updateServiceStatus (
    _serviceCurStatus,
    NO_ERROR,
    0,
    CHECKPOINT_NO_ONGOING_OPERATION,
    TIMEOUT_NONE,
    _serviceStatusHandle
    );
  }
  debug("serviceMain() returning.");
}  // serviceMain


// ----------------------------------------------------
// Notify the serviceMain that service is now terminated.
//
// terminationEvent  the event upon which serviceMain is blocked
// ----------------------------------------------------
void doTerminateService(HANDLE terminationEvent)
{
  debug("Faking a service termination so serviceMain can return.");
  SetEvent(terminationEvent);
  return;

}  // doTerminateService

// ----------------------------------------------------
// This function is the handler of the service. It is processing the
// commands send by the SCM. Commands can be: STOP, PAUSE, CONTINUE,
// SHUTDOWN and INTERROGATE.
//  controlCode  the code of the command
// ----------------------------------------------------

void serviceHandler(DWORD controlCode)
{
  ServiceReturnCode code;
  DWORD checkpoint;
  BOOL running;
  debug("serviceHandler called with controlCode=%d.", controlCode);
  switch (controlCode)
  {
    case SERVICE_CONTROL_SHUTDOWN:
    // If system is shuting down then stop the service
    // -> no break here
      debug("serviceHandler: shutdown");
    case SERVICE_CONTROL_STOP:
    {
      // update service status to STOP_PENDING
      debug("serviceHandler: stop");
      _serviceCurStatus = SERVICE_STOP_PENDING;
      checkpoint = CHECKPOINT_FIRST_VALUE;
      updateServiceStatus (
      _serviceCurStatus,
      NO_ERROR,
      0,
      checkpoint++,
      TIMEOUT_STOP_SERVICE,
      _serviceStatusHandle
      );

      // let's try to stop the application whatever may be the status above
      // (best effort mode)
      code = doStopApplication();
      if (code == SERVICE_RETURN_OK)
      {
        WORD argCount = 1;
        const char *argc[] = {_instanceDir};
        _serviceCurStatus = SERVICE_STOPPED;
        updateServiceStatus (
        _serviceCurStatus,
        NO_ERROR,
        0,
        CHECKPOINT_NO_ONGOING_OPERATION,
        TIMEOUT_NONE,
        _serviceStatusHandle
        );

        // again, let's ignore the above status and
        // notify serviceMain that service has stopped
        doTerminateService (_terminationEvent);
        reportLogEvent(
        EVENTLOG_SUCCESS,
        WIN_EVENT_ID_SERVER_STOP,
        argCount, argc
        );
      }
      else
      {
        WORD argCount = 1;
        const char *argc[] = {_instanceDir};
        debug("The server could not be stopped.");
        // We could not stop the server
        reportLogEvent(
        EVENTLOG_ERROR_TYPE,
        WIN_EVENT_ID_SERVER_STOP_FAILED,
        argCount, argc
        );
      }
      break;
    }


    // Request to pause the service
    // ----------------------------
    case SERVICE_CONTROL_PAUSE:
      // not supported
      debug("serviceHandler: pause.");
      break;

    // Request to resume the service
    // -----------------------------
    case SERVICE_CONTROL_CONTINUE:
      // not supported
      debug("serviceHandler: continue.");
      break;

    // Interrogate the service status
    // ------------------------------
    case SERVICE_CONTROL_INTERROGATE:
      debug("serviceHandler: interrogate.");
      code = isServerRunning(&running);
      if (code != SERVICE_RETURN_OK)
      {
        debug("serviceHandler: error interrogating.");
      }
      else if (running)
      {
        _serviceCurStatus = SERVICE_RUNNING;
        debug("serviceHandler: service running.");
      }
      else
      {
        _serviceCurStatus = SERVICE_STOPPED;
        debug("serviceHandler: service stopped.");
      }
      updateServiceStatus (
          _serviceCurStatus,
          NO_ERROR,
          0,
          CHECKPOINT_NO_ONGOING_OPERATION,
          TIMEOUT_NONE,
          _serviceStatusHandle
      );
    break;

    // Other codes are ignored
    default:

    break;
  }

}  // serviceHandler

// ---------------------------------------------------------------
// Retrieve the binaryPathName from the SCM database for a given service.
//
// scm is the SCM handler (must not be NULL)
// serviceName  the name of the service.
// It is up to the caller of the function to allocate at least COMMAND_SIZE bytes
// in binPathName.
// The function returns SERVICE_RETURN_OK if we could create the binary
// path name and SERVICE_RETURN_ERROR otherwise.
// ---------------------------------------------------------------

ServiceReturnCode getBinaryPathName(HANDLE scm, char* serviceName,
char* binPathName)
{
  ServiceReturnCode returnValue;

  // handle to the service
  SC_HANDLE myService = NULL;


  BOOL  getConfigOk = FALSE;
  DWORD configSize  = 4096;
  LPQUERY_SERVICE_CONFIG serviceConfig =
  (LPQUERY_SERVICE_CONFIG)malloc(configSize);

  returnValue = SERVICE_RETURN_ERROR;

  // if SCM exists then retrieve the config info of the service
  if (scm != NULL)
  {
    myService = OpenService(
    scm,
    serviceName,
    SERVICE_QUERY_CONFIG
    );
  }

  if (myService == NULL)
  {
      debugError("Failed to open the service '%s'.", serviceName);
  }
  else
  {
    while (!getConfigOk)
    {
      DWORD bytesNeeded;

      getConfigOk = QueryServiceConfig(
      myService,
      serviceConfig,
      configSize,
      &bytesNeeded
      );

      if (!getConfigOk)
      {
        DWORD errCode = GetLastError();
        if (errCode == ERROR_INSUFFICIENT_BUFFER)
        {
          // buffer not big enough...
          configSize += bytesNeeded;
          serviceConfig =
          (LPQUERY_SERVICE_CONFIG)realloc(serviceConfig, configSize);
          continue;
        }
        else
        {
          debug("getBinaryPath: error calling QueryServiceConfig. Code [%d]", errCode);
          break;
        }
      }
      else
      {
        if (strlen(serviceConfig->lpBinaryPathName) < COMMAND_SIZE)
        {
          sprintf(binPathName, serviceConfig->lpBinaryPathName);
          returnValue = SERVICE_RETURN_OK;
        }
        else
        {
          debug("getBinaryPath: the length of the binary path name is too big. serviceName='%s', binaryPath='%s'", serviceName, serviceConfig->lpBinaryPathName);
        }
      }
    }
  }

  // free buffers
  if (serviceConfig != NULL)
  {
    free(serviceConfig);
  }

  return returnValue;
} // getBinaryPathName

// ---------------------------------------------------------------
// Returns the list of NT services being created on the current host.
// The function allocates the memory for the returned buffer.
// serviceList contains the list of services.
// nbServices the number of services returned in the list.
// The functions returns SERVICE_RETURN_OK if we could create the service
// list and SERVICE_RETURN_ERROR otherwise.
// ---------------------------------------------------------------

ServiceReturnCode getServiceList(ServiceDescriptor** serviceList,
int *nbServices)
{
  ServiceReturnCode returnValue;

  // open Service Control Manager
  SC_HANDLE scm = NULL;
  // get the list of services being configured in the SCM database
  // 1- first try with a single data structure ENUM_SERVICE_STATUS
  ENUM_SERVICE_STATUS  serviceData;
  ENUM_SERVICE_STATUS* lpServiceData = &serviceData;
  DWORD dataSize = sizeof (serviceData);
  DWORD neededSize;
  DWORD resumeHandle  = 0;
  unsigned long nbSvc = 0;

  if (openScm(SC_MANAGER_ENUMERATE_SERVICE, &scm) == SERVICE_RETURN_OK)
  {
    BOOL svcStatusOk = EnumServicesStatus(
    scm,                   // handle to the SCM
    SERVICE_WIN32,         // for OWN_PROCESS | SHARE_PROCESS
    SERVICE_STATE_ALL,     // all services (runing & stopped)
    &serviceData,          // output buffer
    dataSize,              // output buffer size
    &neededSize,           // sized needed to get the entries
    &nbSvc,                // number of services
    &resumeHandle          // next service entry to read
    );

    if (! svcStatusOk)
    {
      DWORD lastError = GetLastError();
      if (lastError != ERROR_MORE_DATA)
      {
        debug("getServiceList: generic error. Code [%d]", lastError);
        returnValue = SERVICE_RETURN_ERROR;
      }
      else
      {
        // buffer is not big enough: try again with a proper size
        dataSize += neededSize;
        lpServiceData = (ENUM_SERVICE_STATUS*)calloc(
        dataSize, sizeof(ENUM_SERVICE_STATUS));

        svcStatusOk = EnumServicesStatus(
        scm,                   // handle to the SCM
        SERVICE_WIN32,         // for OWN_PROCESS | SHARE_PROCESS
        SERVICE_STATE_ALL,     // all services (running & stopped)
        lpServiceData,         // output buffer
        dataSize,              // output buffer size
        &neededSize,           // sized needed to get the entries
        &nbSvc,                // number of services
        &resumeHandle          // next service entry to read
        );

        if (! svcStatusOk)
        {
          DWORD lastError = GetLastError();
          if (lastError != ERROR_MORE_DATA)
          {
            returnValue = SERVICE_RETURN_ERROR;
            debug("getServiceList: second try generic error. Code [%d]", lastError);
          }
          else
          {
            // Data buffer is not large enough. This case should
            // never happen as proper buffer size has been
            // provided!...
            debug("getServiceList: buffer error");
            returnValue = SERVICE_RETURN_ERROR;
          }
        }
        else
        {
          returnValue = SERVICE_RETURN_OK;
        }
      }
    }
    else
    {
      returnValue = SERVICE_RETURN_OK;
    }
  }
  else
  {
    debug("getServiceList: error opening scm.");
    returnValue = SERVICE_RETURN_ERROR;
  }

  // now elaborate the list of service to return...
  if (returnValue == SERVICE_RETURN_OK)
  {
    int i;
    int aux = (int)nbSvc;
    ServiceDescriptor* l;

    ENUM_SERVICE_STATUS* curService = lpServiceData;

    *nbServices = aux;
    if (aux > 0)
    {
      char binPath[COMMAND_SIZE];
      l = (ServiceDescriptor*)calloc(sizeof(ServiceDescriptor), aux);
      for (i = 0; i < aux; i++)
      {
        l[i].serviceName = _strdup(curService->lpServiceName);
        l[i].displayName = _strdup(curService->lpDisplayName);

        if (getBinaryPathName(scm, l[i].serviceName, binPath) ==
          SERVICE_RETURN_OK)
        {
          l[i].cmdToRun = _strdup(binPath);
        }
        else
        {
          debug("Error getting binary path name of service: %s", l[i].serviceName);
        }
        curService++;
      }
      *serviceList = l;
    }
  }

  // close the handle to the SCM
  if (scm != NULL)
  {
    CloseServiceHandle (scm);
    // free the result buffer
    if (lpServiceData != NULL)
    {
      free (lpServiceData);
    }
  }

  return returnValue;
} // getServiceList

// ---------------------------------------------------------------
// Function used to know if a given service name is in use or not.
// Returns SERVICE_IN_USE if the provided service name is in use.
// Returns NOT_SERVICE_IN_USE if the provided service name is not in use.
// Returns SERVICE_RETURN_ERROR if the function could not determine if the
// service name is in use or not.
// ---------------------------------------------------------------

ServiceReturnCode serviceNameInUse(char* serviceName)
{
  ServiceReturnCode returnValue;

  // retrieve list of services
  ServiceDescriptor* serviceList = NULL;
  ServiceDescriptor curService;
  int nbServices = -1;
  int i;

  debug("Determining if service name '%s' is in use.", serviceName);
  
  // go through the list of services and search for the service name
  if (getServiceList(&serviceList, &nbServices) == SERVICE_RETURN_OK)
  {
    returnValue = SERVICE_NOT_IN_USE;

    if (nbServices > 0)
    {
      for (i = 0; i < nbServices && (returnValue == SERVICE_NOT_IN_USE);
      i++)
      {
        curService = serviceList[i];
        if (curService.serviceName == NULL)
        {
          debug("The service name is NULL.\n");
        }
        else
        {
          if (strcmp (serviceName, curService.serviceName) == 0)
          {
            // found the service!
            debug("The service name '%s' is in use.", serviceName);
            returnValue = SERVICE_IN_USE;
          }
        }
      }
      free(serviceList);
    }
  }
  else
  {
    debugError("Could not determine if the service name '%s' is in use because listing the services failed.", serviceName);
    returnValue = SERVICE_RETURN_ERROR;
  }
  return returnValue;
} // serviceNameInUse

// ---------------------------------------------------------------
// Build a service name for OpenDS and make sure
// the service name is unique on the system. To achieve this requirement
// the service name looks like <baseName> for the first OpenDS and
// <baseName>-n if there are more than one.
//
// The functions returns SERVICE_RETURN_OK if we could create a service
// name and SERVICE_RETURN_ERROR otherwise.
// The serviceName buffer must be allocated OUTSIDE the function and its
// minimum size must be of 256 (the maximum string length of a Service Name).
// ---------------------------------------------------------------

ServiceReturnCode createServiceName(char* serviceName, char* baseName)
{
  ServiceReturnCode returnValue = SERVICE_RETURN_OK;
  int i = 1;
  BOOL ended = FALSE;
  ServiceReturnCode nameInUseResult;
  strcpy(serviceName, "");
  while (!ended)
  {
    if (i == 1)
    {
      sprintf(serviceName, baseName);
    }
    else
    {
      sprintf(serviceName, "%s-%d", baseName, i);
    }

    nameInUseResult = serviceNameInUse(serviceName);

    if (nameInUseResult == SERVICE_IN_USE)
    {
      // this service name is already in use: try another one...
      i++;
    }
    else if (nameInUseResult == SERVICE_NOT_IN_USE)
    {
      // this service name is not used so it's a good candidate
      ended = TRUE;
    }
    else
    {
      // an error occurred checking the service name
      returnValue = SERVICE_RETURN_ERROR;
      ended = TRUE;
    }
  }

  debug("createServiceName returning serviceName='%s' and returnValue=%d",
        serviceName, returnValue);  

  return returnValue;
} // createServiceName


// ---------------------------------------------------------------
// Create a service in the SCM database. Once the service is created,
// we can view it with "service list".
// displayName is the display name of the service
// description is the description of the service
// cmdToRun    is the command to be run by the SCM upon NET START
//
// The function returns SERVICE_RETURN_OK if we could create the service and
// SERVICE_RETURN_ERROR otherwise.
// ---------------------------------------------------------------

ServiceReturnCode createServiceInScm(char* displayName, char* description,
char* cmdToRun)
{
  ServiceReturnCode returnValue;
  SC_HANDLE scm       = NULL;
  SC_HANDLE myService = NULL;

  // local vars
  // - serviceName is the service name
  char* serviceName = (char*) calloc(1, MAX_SERVICE_NAME);

  // elaborate the service name based on the displayName provided
  returnValue = createServiceName(serviceName, displayName);

  // create the service
  if (returnValue == SERVICE_RETURN_OK)
  {
    if (openScm(GENERIC_WRITE, &scm) != SERVICE_RETURN_OK)
    {
      returnValue = SERVICE_RETURN_ERROR;
      debug("createServiceInScm: openScm did not work.");
    }
  }
  else
  {
    returnValue = SERVICE_RETURN_ERROR;
    debug("createServiceInScm: createServiceName did not work.");
  }

  if (returnValue == SERVICE_RETURN_OK)
  {
    debug("Creating the service '%s'.", serviceName);
    myService = CreateService(
    scm,
    serviceName,                // name of service
    serviceName,                // service name to display
    SERVICE_ALL_ACCESS,         // desired access
    SERVICE_WIN32_OWN_PROCESS,  // service type
    SERVICE_AUTO_START,         // start service during
    // system startup
    SERVICE_ERROR_NORMAL,       // error control type
    cmdToRun,                   // path to service's binary
    NULL,                       // no load ordering group
    NULL,                       // no tag identifier
    NULL,                       // no dependencies
    NULL,                       // LocalSystem account
    NULL                        // no password
    );
  }

  if ((returnValue == SERVICE_RETURN_OK) && (myService == NULL))
  {
    DWORD errCode = GetLastError();
    debugError("Failed to create the service '%s'.  Last error = %d.", serviceName, errCode);
    if (errCode == ERROR_DUPLICATE_SERVICE_NAME)
    {
      returnValue = DUPLICATED_SERVICE_NAME;
    }
    else if (errCode == ERROR_SERVICE_EXISTS)
    {
      returnValue = SERVICE_ALREADY_EXISTS;
    }
    else
    {
      returnValue = SERVICE_RETURN_ERROR;
    }
  }

  // add description field
  if (returnValue == SERVICE_RETURN_OK)
  {
    BOOL success;
    SERVICE_DESCRIPTION serviceDescription;
    serviceDescription.lpDescription = description;
    success = ChangeServiceConfig2(
    myService,
    SERVICE_CONFIG_DESCRIPTION,
    (LPVOID) &serviceDescription
    );

    if (!success)
    {
      debugError("Failed to add a description to the service '%s'.  Last error = %d.", serviceName, GetLastError());
      returnValue = SERVICE_RETURN_ERROR;
    }
  }

  // close handles
  if (myService != NULL)
  {
    CloseServiceHandle (myService);
  }

  if (scm != NULL)
  {
    CloseServiceHandle (scm);
  }

  // free names
  if (serviceName != NULL)
  {
    free (serviceName);
  }

  debug("createServiceInScm returning %d.", returnValue);
        
  return returnValue;
} // createServiceInScm


// ---------------------------------------------------------------
// Remove a service with the name serviceName from SCM.
// If the service could be removed returns SERVICE_RETURN_OK.
// If the service cannot be removed because still in use by any process
// then returned status is SERVICE_MARKED_FOR_DELETION.
// If an error occurs returns SERVICE_RETURN_ERROR.
// ---------------------------------------------------------------
ServiceReturnCode removeServiceFromScm(char* serviceName)
{
  // local vars
  ServiceReturnCode returnValue = SERVICE_RETURN_OK;
  SC_HANDLE scm       = NULL;
  SC_HANDLE myService = NULL;
  SERVICE_STATUS serviceStatus;

  debug("Removing service '%s' from the Service Control Manager.", serviceName);
  
  returnValue = openScm(GENERIC_WRITE, &scm);

  // open the service
  if (returnValue == SERVICE_RETURN_OK)
  {
    debug("About to open service '%s'.", serviceName);
    myService = OpenService(
    scm,
    serviceName,
    SERVICE_ALL_ACCESS | DELETE
    );
    debug("After opening service myService=%d.", myService);
    if (myService == NULL)
    {
      debugError("Failed to open the service '%s'. Last error = %d", serviceName, GetLastError());
      returnValue = SERVICE_RETURN_ERROR;
    }
  }

  if (returnValue == SERVICE_RETURN_OK)
  {
    BOOL success;
    debug("About to query the service '%s'.", serviceName);
    success = QueryServiceStatus(
    myService,
    &serviceStatus
    );
    if (!success)
    {
      debugError("Failed to query the status for service '%s'. Last error = %d", serviceName, GetLastError());
      returnValue = SERVICE_RETURN_ERROR;
    }
  }

  // stop the service if necessary
  if (returnValue == SERVICE_RETURN_OK)
  {
    debug("Successfully queried the service '%s'.", serviceName);
    if (serviceStatus.dwCurrentState != SERVICE_STOPPED)
    {
      BOOL success;
      debug("Attempting to stop the service '%s'.", serviceName);
      success = ControlService (
      myService,
      SERVICE_CONTROL_STOP,
      &serviceStatus
      );
      if (!success)
      {
        DWORD errCode = GetLastError();
        debugError("Failed to stop the service '%s'.  Last error = %d.", serviceName, errCode);
        if (errCode == ERROR_SERVICE_MARKED_FOR_DELETE)
        {
          returnValue = SERVICE_MARKED_FOR_DELETION;
        }
        else
        {
          returnValue = SERVICE_RETURN_ERROR;
        }
      }
      else
      {
        Sleep (500);
      }
    }
  }

  // remove the service
  if (returnValue == SERVICE_RETURN_OK)
  {
    BOOL success;
    debug("Deleting the service '%s'.", serviceName);
    success = DeleteService (myService);
    if (!success)
    {
      DWORD errCode = GetLastError();
      debugError("Failed to delete the service '%s'.  Last error = %d.", serviceName, errCode);
      if (errCode == ERROR_SERVICE_MARKED_FOR_DELETE)
      {
        returnValue = SERVICE_MARKED_FOR_DELETION;
      }
      else
      {
        returnValue = SERVICE_RETURN_ERROR;
      }
    }
  }

  // close handles
  if (myService != NULL)
  {
    CloseServiceHandle (myService);
  }

  if (scm != NULL)
  {
    CloseServiceHandle (scm);
  }

  return returnValue;

}  // removeServiceFromScm


// ---------------------------------------------------------------
// Function called to create a service for the OpenDS instance
// where this executable is installed.
// The first argument that is passed is the displayName of the service
// and the second the description,
//
// Returns 0 if the service was successfully created.
// Returns 1 if the service already existed for this instance.
// Returns 2 if the service name we created already exists.
// Returns 3 if an error occurred.
// ---------------------------------------------------------------
int createService(char* displayName, char* description)
{
  int returnCode = 0;
  char cmdToRun[COMMAND_SIZE];
  ServiceReturnCode code;

  debug("Creating service displayName='%s' description='%s'.", displayName, description);
  code = createServiceBinPath(cmdToRun);

  if (code == SERVICE_RETURN_OK)
  {
    char serviceName[MAX_SERVICE_NAME];
    code = getServiceName(cmdToRun, serviceName);
    if (code == SERVICE_RETURN_OK)
    {
      debug("Service '%s' already exists.", displayName);
      // There is a valid serviceName for the command to run, so
      // OpenDS is registered as a service.
      code = SERVICE_ALREADY_EXISTS;
      createRegistryKey(serviceName);
    }
    else
    {
      debug("Could not find service '%s', so creating it now.", displayName);
      // We could not find a serviceName for the command to run, so
      // try to create the service.
      code = createServiceInScm(displayName, description, cmdToRun);
      if (code == SERVICE_RETURN_OK)
      {
        code = getServiceName(cmdToRun, serviceName);
        if (code == SERVICE_RETURN_OK)
        {
          createRegistryKey(serviceName);
        }
        else
        {
          debug("Could not get a service name for command to run.");
        }
      }
    }
  }
  else
  {
    debug("createService could not create bin path.");
  }
  switch (code)
  {
    case SERVICE_RETURN_OK:
    returnCode = 0;
    debug("Service successfully created.");
    break;
    case SERVICE_ALREADY_EXISTS:
    returnCode = 1;
    debug("Service already exists.");
    break;
    case DUPLICATED_SERVICE_NAME:
    returnCode = 2;
    debug("Duplicated service name.");
    break;
    default:
    returnCode = 3;
    debug("Unexpected error creating service.");
  }

  return returnCode;
} // createService

// ---------------------------------------------------------------
// Function called to know if the OpenDS instance where this
// executable is installed is running as a service or not.
// Returns 0 if the instance is running as a service and print the
// serviceName in the standard output.
// Returns 1 if the instance is not running as a service.
// Returns 2 if an error occurred or we cannot determine if Open DS
// is running as a service or not.
// ---------------------------------------------------------------
int serviceState()
{
  int returnCode = 0;
  char cmdToRun[COMMAND_SIZE];
  char serviceName[MAX_SERVICE_NAME];
  ServiceReturnCode code;

  strcpy(serviceName, "");
  debug("Getting service state.");
  code = createServiceBinPath(cmdToRun);
  debug("Created the service bin path. code=%d.  cmdToRun='%s'.", code, cmdToRun);

  if (code == SERVICE_RETURN_OK)
  {
    code = getServiceName(cmdToRun, serviceName);
    
    if (code == SERVICE_RETURN_OK)
    {
      // There is a valid serviceName for the command to run, so
      // OpenDS is registered as a service.
      fprintf(stdout, serviceName);
      returnCode = 0;
      debug("Service '%s' is enabled.", serviceName);
    }
    else
    {
      returnCode = 1;
      debug("Service '%s' is disabled enabled.", serviceName);
    }
  }
  else
  {
    returnCode = 2;
    debug("An error occurred getting the service status.");
  }

  return returnCode;
} // serviceState

// ---------------------------------------------------------------
// Function called to remove the service associated with a given
// service name.
// Returns 0 if the service was successfully removed.
// Returns 1 if the service does not exist.
// Returns 2 if the service was marked for deletion but is still in
// use.
// Returns 3 if an error occurred.
// ---------------------------------------------------------------
int removeServiceWithServiceName(char *serviceName)
{
  int returnCode = 0;
  ServiceReturnCode code = serviceNameInUse(serviceName);

  debug("Removing service.");

  if (code != SERVICE_IN_USE)
  {
    returnCode = 1;
    debug("Service does not exist.");
  }
  else
  {
    code = removeServiceFromScm(serviceName);

    switch (code)
    {
      case SERVICE_RETURN_OK:
      removeRegistryKey(serviceName);
      returnCode = 0;
      debug("Service successfully removed.");
      break;
      case SERVICE_MARKED_FOR_DELETION:
      removeRegistryKey(serviceName);
      returnCode = 2;
      debug("Service marked for deletion.");
      break;
      default:
      returnCode = 3;
      debug("Unexpected error removing service.");
    }
  }

  return returnCode;
} // removeServiceWithServiceName

// ---------------------------------------------------------------
// Function called to remove the service for the OpenDS instance
// where this executable is installed.
// Returns 0 if the service was successfully removed.
// Returns 1 if the service does not exist.
// Returns 2 if the service was marked for deletion but is still in
// use.
// Returns 3 if an error occurred.
// ---------------------------------------------------------------
int removeService()
{
  int returnCode = 0;
  char cmdToRun[COMMAND_SIZE];
  char serviceName[MAX_SERVICE_NAME];
  ServiceReturnCode code;

  debug("removeService()");
  code = createServiceBinPath(cmdToRun);

  if (code == SERVICE_RETURN_OK)
  {
    code = getServiceName(cmdToRun, serviceName);
    if (code == SERVICE_RETURN_OK)
    {
      returnCode = removeServiceWithServiceName(serviceName);
    }
    else
    {
      returnCode = 1;
    }
  }
  else
  {
    returnCode = 2;
  }

  debug("removeService() returning %d.", returnCode);
  return returnCode;
} // removeService



// ---------------------------------------------------------------
// Function called to start the service where this executable is installed.
// Returns 0 if the service runs.
// Returns 1 if an error occurred.
// ---------------------------------------------------------------

int startService()
{
  int returnCode;
  char serviceName[MAX_SERVICE_NAME];
  char cmdToRun[COMMAND_SIZE];
  ServiceReturnCode code;

  debug("startService()");
  code = createServiceBinPath(cmdToRun);

  if (code == SERVICE_RETURN_OK)
  {
    code = getServiceName(cmdToRun, serviceName);
  }

  if (code == SERVICE_RETURN_OK)
  {
    BOOL success;
    SERVICE_TABLE_ENTRY serviceTable[] =
    {
      {serviceName, (LPSERVICE_MAIN_FUNCTION) serviceMain},
      {NULL, NULL}
    };
    _eventLog = registerEventLog(serviceName);

    // register the service to the SCM. The function will return once the
    // service is terminated.
    success = StartServiceCtrlDispatcher(serviceTable);
    if (!success)
    {
      WORD argCount = 2;
      DWORD lastError = GetLastError();
      const char *argc[2];
      argc[0] = _instanceDir;
      if (lastError == ERROR_FAILED_SERVICE_CONTROLLER_CONNECT)
      {
        argc[1] =
        "startService: StartServiceCtrlDispatcher did not work: \
        ERROR_FAILED_SERVICE_CONTROLLER_CONNECT.";
      }
      else if (lastError == ERROR_INVALID_DATA)
      {
        argc[1] =
        "startService: StartServiceCtrlDispatcher did not work: \
        ERROR_INVALID_DATA.";
      }
      else if (lastError == ERROR_SERVICE_ALREADY_RUNNING)
      {
        argc[1] =
        "startService: StartServiceCtrlDispatcher did not work: \
        ERROR_SERVICE_ALREADY_RUNNING.";
      }
      else
      {
        argc[1] =
        "startService: StartServiceCtrlDispatcher did not work.";
      }
      code = SERVICE_RETURN_ERROR;
      reportLogEvent(
      EVENTLOG_ERROR_TYPE,
      WIN_EVENT_ID_SERVER_START_FAILED,
      argCount, argc
      );
      debugError("For instance dir '%s', %s", argc[0], argc[1]);
    }
    deregisterEventLog();
  }
  else
  {
    debug("startService: Could not get service name.");
  }

  if (code == SERVICE_RETURN_OK)
  {
    returnCode = 0;
  }
  else
  {
    returnCode = 1;
  }
  return returnCode;

}  // startService



int main(int argc, char* argv[])
{
  char* subcommand;
  int returnCode = 0;
  int i;
  
  updateDebugFlag(argv, argc);

  debug("main called.");
  for (i = 0; i < argc; i++) {
      debug("  argv[%d] = '%s'", i, argv[i]);
  }

  // __debugbreak();

  if (argc <= 1)
  {
    fprintf(stdout,
    "Subcommand required: create, state, remove, start or cleanup.\n");
    returnCode = -1;
  }
  else
  {
    subcommand = argv[1];
    if (strcmp(subcommand, "create") == 0)
    {
      if (argc <= 4)
      {
        fprintf(stdout,
    "Subcommand create requires instance dir, service name and description.\n");
        returnCode = -1;
      }
      else
      {
        _instanceDir = _strdup(argv[2]);
        returnCode = createService(argv[3], argv[4]);
        free(_instanceDir);
      }
    }
    else if (strcmp(subcommand, "state") == 0)
    {
      if (argc <= 2)
      {
        fprintf(stdout,
        "Subcommand state requires instance dir.\n");
        returnCode = -1;
      }
      else
      {
        _instanceDir = _strdup(argv[2]);
        returnCode = serviceState();
        free(_instanceDir);
      }
    }
    else if (strcmp(subcommand, "remove") == 0)
    {
      if (argc <= 2)
      {
        fprintf(stdout,
        "Subcommand remove requires instance dir.\n");
        returnCode = -1;
      }
      else
      {
        _instanceDir = _strdup(argv[2]);
        returnCode = removeService();
        free(_instanceDir);
      }
    }
    else if (strcmp(subcommand, "start") == 0)
    {
      if (argc <= 2)
      {
        fprintf(stdout,
        "Subcommand start requires instance dir.\n");
        returnCode = -1;
      }
      else
      {
        _instanceDir = _strdup(argv[2]);
        returnCode = startService();
        free(_instanceDir);
      }
    }
    else if (strcmp(subcommand, "isrunning") == 0)
    {
      if (argc <= 2)
      {
        fprintf(stdout,
        "Subcommand isrunning requires instance dir.\n");
        returnCode = -1;
      }
      else
      {
        BOOL running;
        ServiceReturnCode code;
        _instanceDir = _strdup(argv[2]);
        code = isServerRunning(&running);
        if (code == SERVICE_RETURN_OK)
        {
          returnCode = 0;
        }
        else
        {
          returnCode = -1;
        }
        free(_instanceDir);
      }

    }
    else if (strcmp(subcommand, "cleanup") == 0)
    {
      if (argc <= 2)
      {
        fprintf(stdout,
        "Subcommand cleanup requires service name.\n");
        returnCode = -1;
      }
      else
      {
        char* serviceName = _strdup(argv[2]);
        returnCode = removeServiceWithServiceName(serviceName);
        free(serviceName);
      }
    }

    else
    {
      fprintf(stdout, "Unknown subcommand: [%s]\n", subcommand);
      returnCode = -1;
    }
  }

  debug("main returning %d.", returnCode);
    
  return returnCode;
} // main

