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
#include <errno.h>
#include <fcntl.h>
#include <io.h>
#include <stdio.h>
#include <sys/locking.h>
#include "EventLogMsg.h"

#define MAX_SERVICE_NAME 256

// ----------------------------------------------------
// Estimated time for a given operation (in ms).
// Note: if the estimated value is too short then the service controler
//       may consider the service as not alive anymore! so the value
//       must be as precise as possible (better have it too big rather
//       than to small)
// ----------------------------------------------------

#define TIMEOUT_NONE              0
#define TIMEOUT_CREATE_EVENT   5000
#define TIMEOUT_START_SERVICE 30000
#define TIMEOUT_STOP_SERVICE  30000

// ----------------------------------------------------
// The first value to use for checkpoints
// ----------------------------------------------------
#define CHECKPOINT_FIRST_VALUE 1

// ----------------------------------------------------
// Checkpoint value to use to let the SCM knows that there is
// no ongoing operation
// ----------------------------------------------------
#define CHECKPOINT_NO_ONGOING_OPERATION 0

// ----------------------------------------------------
// Event Log Key.
// ----------------------------------------------------
#define EVENT_LOG_KEY "SYSTEM\\CurrentControlSet\\Services\\EventLog\\Application\\%s"

// ----------------------------------------------------
// Max size of the registry key
// ----------------------------------------------------
#define MAX_REGISTRY_KEY 512

// ----------------------------------------------------
// Max size of the binary to run the service
// ----------------------------------------------------
#define COMMAND_SIZE 2048

#define SERVICE_ACCEPT_NONE 0

typedef struct {
      char* serviceName;   // the name of the service
      char* displayName;   // the display name of the service
    char* cmdToRun;    // the executable to run
      } ServiceDescriptor;

typedef enum {
    SERVICE_RETURN_OK, SERVICE_RETURN_ERROR, SERVICE_IN_USE,
  SERVICE_NOT_IN_USE, DUPLICATED_SERVICE_NAME, SERVICE_ALREADY_EXISTS,
  SERVICE_MARKED_FOR_DELETION
} ServiceReturnCode;


ServiceReturnCode registerServiceHandler (char* serviceName,
LPHANDLER_FUNCTION serviceHandler, SERVICE_STATUS_HANDLE* serviceStatusHandle);
ServiceReturnCode serviceNameInUse(char* serviceName);
ServiceReturnCode createServiceName(char* serviceName, char* baseName);
ServiceReturnCode getServiceList(ServiceDescriptor** serviceList,
int *nbServices);
ServiceReturnCode createServiceBinPath(char* serviceBinPath);
ServiceReturnCode getServiceName(char* cmdToRun, char* serviceName);
ServiceReturnCode updateServiceStatus (
   DWORD  statusToSet,
   DWORD  win32ExitCode,
   DWORD  serviceExitCode,
   DWORD  checkPoint,
   DWORD  waitHint,
   SERVICE_STATUS_HANDLE *serviceStatusHandle
   );
void serviceHandler(DWORD controlCode);

