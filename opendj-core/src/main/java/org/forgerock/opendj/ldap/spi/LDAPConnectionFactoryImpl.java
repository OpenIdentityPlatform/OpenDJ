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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.spi;

import java.io.Closeable;
import java.net.InetSocketAddress;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.promise.Promise;

/**
 * Interface for all classes that implement {@code LDAPConnectionFactory}.
 * <p>
 * An implementation class is provided by a {@code TransportProvider}.
 * <p>
 * The implementation can be automatically loaded using the
 * {@code java.util.ServiceLoader} facility if its provider extending
 * {@code TransportProvider} is declared in the provider-configuration file
 * {@code META-INF/services/org.forgerock.opendj.ldap.spi.TransportProvider}.
 */
public interface LDAPConnectionFactoryImpl extends Closeable {

    /**
     * Returns the address used by the connections created by this factory.
     *
     * @return The address used by the connections.
     */
    InetSocketAddress getSocketAddress();

    /**
     * Returns the hostname used by the connections created by this factory.
     *
     * @return The hostname used by the connections.
     */
    String getHostName();

    /**
     * Returns the remote port number used by the connections created by this factory.
     *
     * @return The remote port number used by the connections.
     */
    int getPort();

    /**
     * Releases any resources associated with this connection factory implementation.
     */
    @Override
    void close();

    /**
     * Asynchronously obtains a connection to the Directory Server associated
     * with this connection factory. The returned {@code Promise} can be used to
     * retrieve the completed connection.
     *
     * @return A promise which can be used to retrieve the connection.
     */
    Promise<LDAPConnectionImpl, LdapException> getConnectionAsync();
}
