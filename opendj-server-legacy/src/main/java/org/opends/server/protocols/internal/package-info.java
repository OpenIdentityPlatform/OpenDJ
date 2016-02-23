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

