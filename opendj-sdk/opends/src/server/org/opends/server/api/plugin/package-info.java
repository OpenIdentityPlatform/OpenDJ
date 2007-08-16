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
 * Defines the Directory Server plugin API.  Plugins are classes
 * containing user-defined code that will be executed at specific
 * times points during operation processing.  In particular, the
 * plugin points for operation processing include:
 * <BR>
 * <UL>
 *   <LI>
 *     Pre-parse plugins are invoked just before processing begins on
 *     an operation.  They may be used to transparently alter the
 *     contents of the request in some way.
 *   </LI>
 *   <LI>
 *     Pre-operation plugins are invoked after a significant amount of
 *     validation has been performed on the request but just before
 *     the real heart of the core processing begins (e.g., the backend
 *     processing).
 *   </LI>
 *   <LI>
 *     Post-operation plugins are invoked just after the heart of the
 *     core processing on an operation but before the response is sent
 *     to the client.  They may be used to modify the response that
 *     will be sent and/or perform alternate processing for an
 *     operation in the event that the real core processing failed.
 *   </LI>
 *   <LI>
 *     Post-response plugins are invoked after the response has been
 *     sent to the client and all other processing for the operation
 *     has completed.  They may be used for cases in which the plugin
 *     processing does not need to occur before the client receives
 *     the response for the operation.
 *   </LI>
 * </UL>
 * <BR>
 * These plugins may be invoked for most kinds of operations, although
 * certain types of operations like abandon and unbind do not have
 * responses and therefore there is no ability to invoke post-response
 * plugins for those kinds of operations.
 * <BR><BR>
 * Other plugin Directory Server plugin points include:
 * <BR>
 * <UL>
 *   <LI>
 *     Startup plugins may be used to invoke user-defined code when
 *     the Directory Server starts.
 *   </LI>
 *   <LI>
 *     Shutdown plugins may be used to invoke user-defined code when
 *     the Directory Server performs a graceful shutdown.
 *   </LI>
 *   <LI>
 *     Post-connect plugins may be used to invoke user-defined code
 *     whenever a new client connection is established.
 *   </LI>
 *   <LI>
 *     Post-disconnect plugins may be used to invoke user-defined code
 *     whenever a client connection is terminated.
 *   </LI>
 *   <LI>
 *     Search result entry plugins may be used to alter entries as
 *     they are sent to clients.
 *   </LI>
 *   <LI>
 *     Search result reference plugins may be used to alter referrals
 *     as they are sent to clients.
 *   </LI>
 *   <LI>
 *     Intermediate response plugins may be used to alter intermediate
 *     response messages as they are sent to clients.
 *   </LI>
 *   <LI>
 *     LDIF import plugins may be used to alter entries as they are
 *     read from LDIF files.
 *   </LI>
 *   <LI>
 *     LDIF export plugins may be used to alter entries as they are
 *     written to LDIF files.
 *   </LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED)
package org.opends.server.api.plugin;

