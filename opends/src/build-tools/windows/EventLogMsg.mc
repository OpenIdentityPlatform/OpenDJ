;/*
; * CDDL HEADER START
; *
; * The contents of this file are subject to the terms of the
; * Common Development and Distribution License, Version 1.0 only
; * (the "License").  You may not use this file except in compliance
; * with the License.
; *
; * You can obtain a copy of the license at
; * trunk/opends/resource/legal-notices/OpenDS.LICENSE
; * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
; * See the License for the specific language governing permissions
; * and limitations under the License.
; *
; * When distributing Covered Code, include this CDDL HEADER in each
; * file and include the License file at
; * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
; * add the following below this CDDL HEADER, with the fields enclosed
; * by brackets "[]" replaced with your own identifying * information:
; *      Portions Copyright [yyyy] [name of copyright owner]
; *
; * CDDL HEADER END
; *
; *
; *      Copyright 2008-2010 Sun Microsystems, Inc.
; *      Portions Copyright 2011 ForgeRock AS
; */
;/*
; * ==========================================================================
; *
; *  Definition of the messages sent to the Windows Event Log.
; *
; * ==========================================================================
; */

;/*
; * ==========================================================================
; *  Header Section
; * ==========================================================================
; */
MessageIdTypedef = DWORD

LanguageNames = (
	English	= 0x409 : MSG00409
	)

SeverityNames = (
   Success       = 0x0 : WIN_STATUS_SEVERITY_SUCCESS
   Informational = 0x1 : WIN_STATUS_SEVERITY_INFORMATIONAL
   Warning       = 0x2 : WIN_STATUS_SEVERITY_WARNING
   Error         = 0x3 : WIN_STATUS_SEVERITY_ERROR
   )

FacilityNames = (
   OPENDJ	= 0xFA0 : WIN_FACILITY_NAME_OPENDJ
   )


;/*
; * ==========================================================================
; *  Message Definition
; * ==========================================================================
; */

MessageId    = 0x1
Severity     = Success
Facility     = OPENDJ
SymbolicName = WIN_EVENT_ID_SERVER_STARTED
Language     = English
OpenDJ has started.
OpenDJ is in %1.
.
MessageId    = 0x2
Severity     = Success
Facility     = OPENDJ
SymbolicName = WIN_EVENT_ID_SERVER_STOP
Language     = English
OpenDJ has shutdown.
OpenDJ is in %1.
.
MessageId    = 0x3
Severity     = Error
Facility     = OPENDJ
SymbolicName = WIN_EVENT_ID_SERVER_START_FAILED
Language     = English
OpenDJ failed in startup.
OpenDJ is in %1.
.
MessageId    = 0x4
Severity     = Error
Facility     = OPENDJ
SymbolicName = WIN_EVENT_ID_SERVER_STOP_FAILED
Language     = English
OpenDJ failed in stop.
OpenDJ is in %1.
.
MessageId    = 0x5
Severity     = Informational
Facility     = OPENDJ
SymbolicName = WIN_EVENT_ID_DEBUG
Language     = English
%1
.
MessageId    = 0x6
Severity     = Error
Facility     = OPENDJ
SymbolicName = WIN_EVENT_ID_SERVER_STOPPED_OUTSIDE_SCM
Language     = English
OpenDJ stopped outside the Service Control Manager.
OpenDJ is in %1.
.
