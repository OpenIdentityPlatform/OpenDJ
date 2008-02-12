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
 * Contains the implementation for the Directory Server connection handler that
 * is responsible for interacting with clients using JMX. The JMX
 * implementation in this package is actually comprised of three primary
 * components:
 * <BR>
 * <UL>
 *   <LI>
 *     The JMX connection handler which is purely responsible for starting
 *     and stopping the actual JMX connector.
 *   </LI>
 *   <LI>
 *     The JMX connector which is responsable for client request handling.
 *     A private RMI registry is also created when this JMX Connector is
 *     started. The JMX Connector (RMI server object) and its corresponding
 *     client part are registered in it.
 *   </LI>
 *   <LI>
 *     The Authentication module which allows a remote client to be
 *     authenticated by its LDAP credential.
 *   </LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.protocols.jmx;

