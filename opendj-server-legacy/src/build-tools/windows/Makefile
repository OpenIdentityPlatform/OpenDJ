#
# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions Copyright [year] [name of copyright owner]".
#
# Copyright 2008 Sun Microsystems, Inc.
# Portions Copyright 2011 ForgeRock AS.

#
# This is the Makefile than can be used to generate the executables
# winlauncher.exe and opendj_service.exe that are used by OpenDS in
# windows.
#
# The compilation of winlauncher.exe is straightforward but in the case of
# opendj_service.exe uses a Message File called EventLogMsg.mc which
# makes the compilation a little bit more complex.
# During compilation the first step is to generate EventLogMsg.h and
# EventLogMsg.res using the rc.exe and mc.exe utilities.
#
# EventLogMsg.h contains some references to the message IDs that are sent to
# the Windows Event Log and is required in order service.c to be compiled.
# Once the EvenLogMsg.h file is generated we can compile the service.c and
# common.c and finally we link the service.obj, common.obj and
# EventLogMsg.res files and we generate the opendj_service.exe executable.
#

CC=cl
SERVICE_PROGNAME=opendj_service.exe
LAUNCHER_ADMINISTRATOR_PROGNAME=launcher_administrator.exe
WINLAUNCHER_PROGNAME=winlauncher.exe
LINKER=link -nologo /machine:x86
LIBS=advapi32.lib

CFLAGS= -D_WINDOWS  -nologo  -W3 -O2
RC=rc
MC=mc
MT=mt

SRCS = common.c winlauncher.c service.c

SERVICE_OBJS = common.obj service.obj
WINLAUNCHER_OBJS = common.obj winlauncher.obj

EVENTLOG_H = EventLogMsg.h

MC_FILE = EventLogMsg.mc

RC_FILE = EventLogMsg.rc

RES_FILE = EventLogMsg.res

SERVICE_MANIFEST_FILE = opendj_service.exe.manifest
LAUNCHER_ADMINISTRATOR_MANIFEST_FILE = launcher_administrator.exe.manifest

.c.obj:
  $(CC) $(CFLAGS) -c $< -Fo$@

$(RES_FILE):
  $(MC) $(MC_FILE)
  $(RC) /fo $(RES_FILE) $(RC_FILE)


all: $(SERVICE_PROGNAME) $(WINLAUNCHER_PROGNAME) $(LAUNCHER_ADMINISTRATOR_PROGNAME)

$(SERVICE_PROGNAME) : $(RES_FILE) $(SERVICE_OBJS)
  $(LINKER) /OUT:$(SERVICE_PROGNAME) $(RES_FILE) $(SERVICE_OBJS) $(LIBS)
  $(MT) -manifest "$(SERVICE_MANIFEST_FILE)" -outputresource:"$(SERVICE_PROGNAME)";#1

$(WINLAUNCHER_PROGNAME) : $(WINLAUNCHER_OBJS)
  $(LINKER) $(WINLAUNCHER_OBJS) /OUT:$(WINLAUNCHER_PROGNAME)

$(LAUNCHER_ADMINISTRATOR_PROGNAME) : $(WINLAUNCHER_OBJS)
  $(LINKER) $(WINLAUNCHER_OBJS) /OUT:$(LAUNCHER_ADMINISTRATOR_PROGNAME)
  $(MT) -manifest "$(LAUNCHER_ADMINISTRATOR_MANIFEST_FILE)" -outputresource:"$(LAUNCHER_ADMINISTRATOR_PROGNAME)";#1

clean:
  del $(SERVICE_OBJS) $(EVENTLOG_H) $(WINLAUNCHER_OBJS) $(RC_FILE) $(RES_FILE) $(SERVICE_PROGNAME) $(WINLAUNCHER_PROGNAME) $(LAUNCHER_ADMINISTRATOR_PROGNAME) core

