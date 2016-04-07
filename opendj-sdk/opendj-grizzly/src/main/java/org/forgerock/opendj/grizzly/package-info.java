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
 * Copyright 2013 ForgeRock AS.
 */

/**
 * Provides an implementation of a transport provider using Grizzly as
 * transport. This provider is named "Grizzly".
 * <p>
 * To be used, this implementation must be declared in the
 * provider-configuration file
 * {@code META-INF/services/org.forgerock.opendj.ldap.spi.TransportProvider}
 * with this single line:
 *
 * <pre>
 * com.forgerock.opendj.ldap.GrizzlyTransportProvider
 * </pre>
 *
 * To require that this implementation is used, you must set the transport
 * provider to "Grizzly" using {@code LDAPOptions#setTransportProvider()}
 * method if requesting a {@code LDAPConnectionFactory} or
 * {@code LDAPListenerOptions#setTransportProvider()} method if requesting a
 * {@code LDAPListener}. Otherwise there is no guarantee that this
 * implementation will be used.
 */
package org.forgerock.opendj.grizzly;

