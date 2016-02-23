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
 * Contains various classes that comprise the core of the Directory Server
 * codebase.  This includes:
 * <BR>
 * <UL>
 *   <LI>
 *     The code that is invoked to initialize the various components of the
 *     Directory Server when it is started.
 *   </LI>
 *   <LI>
 *     The code that is invoked whenever the Directory Server is in the process
 *     of shutting down.
 *   </LI>
 *   <LI>
 *     The various types of operations that may be processed within the
 *     Directory Server.
 *   </LI>
 *   <LI>
 *     Data structures for important elements of Directory Server data,
 *     including attributes, objectclasses, DNs, entries.
 *   </LI>
 *   <LI>
 *     The implementation of the work queue and worker threads responsible for
 *     processing operations requested by clients.
 *   </LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.core;

