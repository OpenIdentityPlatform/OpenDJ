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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
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

