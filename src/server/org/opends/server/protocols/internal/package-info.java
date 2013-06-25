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
 * Contains the implementation for the "internal" connection handler
 * and its associated classes.  The internal connection handler is
 * not actually intended for use in communicating with external
 * clients, but rather may be used for performing internal operations
 * invoked by other Directory Server components (e.g., plugins).
 * This internal protocol implementation is specifically designed to
 * allow these kinds of internal operations to be processed
 * efficiently and in potentially the same way as if they had been
 * received from a remote client using some other protocol (e.g.,
 * LDAP).
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED)
package org.opends.server.protocols.internal;

