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
 * Contains the implementation for the Directory Server connection handler that
 * is responsible for interacting with clients using LDAPv3.  The LDAP
 * implementation in this package is actually comprised of three primary
 * components:
 * <BR>
 * <UL>
 *   <LI>
 *     The LDAP connection handler which is purely responsible for accepting new
 *     connections from clients.  Once a connection has been accepted, it is
 *     associated with a request handler for further processing.
 *   </LI>
 *   <LI>
 *     One or more LDAP request handlers, which are intended to read requests
 *     from the clients and convert them into operations that are placed in the
 *     work queue for further processing.  It is possible to have multiple LDAP
 *     request handlers for a single LDAP connection handler, which can provide
 *     better performance and scalability on systems allowing a high degree of
 *     parallel processing because it can help avoid the scenario in which the
 *     performance is constrained to the rate at which a single thread can read
 *     and process requests from clients.
 *   </LI>
 *   <LI>
 *     The data structures that comprise the LDAPMessage envelope, the various
 *     types of protocol op elements, and other classes needed to represent LDAP
 *     protocol data units (PDUs).
 *   </LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.protocols.ldap;

