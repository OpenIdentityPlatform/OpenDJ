#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
#
# You can obtain a copy of the license at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE
# or https://OpenDS.dev.java.net/OpenDS.LICENSE.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# by brackets "[]" replaced with your own identifying information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2008 Sun Microsystems, Inc.

#
# This is the Makefile than can be used to generate the executables
# winlauncher.exe and opends_service.exe that are used by OpenDS in
# windows.
#
# The compilation of winlauncher.exe is straightforward but in the case of
# opends_service.exe uses a Message File called EventLogMsg.mc which
# makes the compilation a little bit more complex.
# During compilation the first step is to generate EventLogMsg.h and
# EventLogMsg.res using the rc.exe and mc.exe utilities.
#
# EventLogMsg.h contains some references to the message IDs that are sent to
# the Windows Event Log and is required in order service.c to be compiled.
# Once the EvenLogMsg.h file is generated we can compile the service.c and
# common.c and finally we link the service.obj, common.obj and
# EventLogMsg.res files and we generate the opends_service.exe executable.
#

CC=cl
SERVICE_PROGNAME=opends_service.exe
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

SERVICE_MANIFEST_FILE = opends_service.exe.manifest
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

