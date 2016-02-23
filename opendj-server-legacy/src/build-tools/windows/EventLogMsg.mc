;/*
; * The contents of this file are subject to the terms of the Common Development and
; * Distribution License (the License). You may not use this file except in compliance with the
; * License.
; *
; * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
; * specific language governing permission and limitations under the License.
; *
; * When distributing Covered Software, include this CDDL Header Notice in each file and include
; * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
; * Header, with the fields enclosed by brackets [] replaced by your own identifying
; * information: "Portions Copyright [year] [name of copyright owner]".
; *
; * Copyright 2008-2010 Sun Microsystems, Inc.
; * Portions Copyright 2011 ForgeRock AS
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
