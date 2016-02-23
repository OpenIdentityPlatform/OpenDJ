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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 */



/**
 * Contains the implementation of the Directory Server plugin that acts as an
 * embedded profiler that can be used for performance analysis of the server
 * under load.  This plugin uses repeated calls to the the
 * <CODE>Thread.getAllStackTraces()</CODE> at regular intervals in order to
 * construct a picture of the particular method calls that seem to take a
 * significant percentage of the Directory Server processing time.  This
 * information may be used to help identify potential targets for performance
 * improvement by highlighting which methods are most frequently called.
 * <BR><BR>
 * The profiler is implemented as a Directory Server startup plugin so that it
 * is made available for use at any point while the server is running.  However,
 * enabling this plugin only causes it to register with the server as a
 * configurable component that will watch for modifications to the
 * ds-cfg-profileAction attribute.  If this attribute is modified to have a
 * value of "start", then the profiler will begin capturing stack trace
 * information until it is modified again with a value of "stop", at which point
 * it will stop profiling and write the captured information to a binary file on
 * the server filesystem, or "cancel", at which point it will stop profiling and
 * discard any information that it had collected.
 * <BR><BR>
 * This package also contains a tool that can be used to view the information
 * in this binary stack trace capture file.  The tool may operate in a simple
 * command-line mode as well as a more flexible and potentially more useful GUI
 * mode.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.plugins.profiler;

