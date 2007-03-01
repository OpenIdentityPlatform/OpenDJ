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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
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
package org.opends.server.core;

