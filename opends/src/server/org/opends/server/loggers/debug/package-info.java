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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */



/**
 * Contains implementation of the debug logger for the Directory Server.
 * Static methods are included in the DebugLogger class to log a debug message.
 * The debug logging framework uses AspectJ AOP to crosscut certain logging
 * concerns across server code base at weave time during the build process. If
 * the weaving step is skipped, the debug logging framework is disabled.
 * The AspectJ aspect defined in the Tracer class is used to automatically trace
 * method entry and exit events and generate the appropreate debug messages. It
 * also keeps track of the source location of debug message calls so information
 * about the class, method, line number, and threads are automatically included
 * in the debug message.
 */
package org.opends.server.loggers.debug;