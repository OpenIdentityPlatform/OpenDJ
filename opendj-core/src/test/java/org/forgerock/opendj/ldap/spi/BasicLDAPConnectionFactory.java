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

import java.net.InetSocketAddress;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.Options;
import org.forgerock.util.promise.Promise;

import static org.forgerock.util.promise.Promises.*;
import static org.mockito.Mockito.*;

/**
 * Basic LDAP connection factory implementation to use for tests only.
 */
final class BasicLDAPConnectionFactory implements LDAPConnectionFactoryImpl {
    private final String host;
    private final int port;

    BasicLDAPConnectionFactory(final String host, final int port, final Options options) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public Promise<LDAPConnectionImpl, LdapException> getConnectionAsync() {
        return newResultPromise(mock(LDAPConnectionImpl.class));
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    @Override
    public String getHostName() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + host + ':' + port + ')';
    }
}
