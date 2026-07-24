/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2026 3A Systems, LLC.
 */

#ifndef OPENDJ_WINDOWS_COMMON_H
#define OPENDJ_WINDOWS_COMMON_H

// Just some functions and constants to be used by winlauncher.c
// and service.c

// This stops warnings about deprecation of stdio functions
#define _CRT_SECURE_NO_DEPRECATE 1

#include <Windows.h>

int spawn(char* command, BOOL background);
BOOL createChildProcess(char* command, BOOL background,
PROCESS_INFORMATION* procInfo);
BOOL createBatchFileChildProcess(char* batchFile, BOOL background,
PROCESS_INFORMATION* procInfo);
void debug(const char *msg, ...);
void debugError(const char *msg, ...);
void updateDebugFlag(char* argv[], int argc);
BOOL waitForProcess(PROCESS_INFORMATION* procInfo, DWORD waitTime,
  DWORD* exitCode);

// Returns TRUE if the given path is non-NULL and does not contain a
// parent-directory reference ("..") that could be used for path traversal.
BOOL isSafePath(const char* path);

#endif // OPENDJ_WINDOWS_COMMON_H
