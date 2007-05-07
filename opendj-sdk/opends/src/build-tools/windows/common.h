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

// Just some functions and constants to be used by winlauncher.c
// and service.c

// This stops warnings about deprecation of stdio functions
#define _CRT_SECURE_NO_DEPRECATE 1

#include <Windows.h>

int spawn(const char* command, BOOL background);
BOOL createChildProcess(char* command, BOOL background,
PROCESS_INFORMATION* procInfo);
void debug(const char *msg, ...);
void debugError(const char *msg, ...);
void updateDebugFlag(char* argv[], int argc);