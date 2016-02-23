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
 * Copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.spi;

import java.io.Closeable;
import java.net.InetSocketAddress;

/**
 * Interface for all classes that actually implement {@code LDAPListener}.
 * <p>
 * An implementation class is provided by a {@code TransportProvider}.
 * <p>
 * The implementation can be automatically loaded using the
 * {@code java.util.ServiceLoader} facility if its provider extending
 * {@code TransportProvider} is declared in the provider-configuration file
 * {@code META-INF/services/org.forgerock.opendj.ldap.spi.TransportProvider}.
 */
public interface LDAPListenerImpl extends Closeable {

    /**
     * Returns the address that this LDAP listener is listening on.
     *
     * @return The address that this LDAP listener is listening on.
     */
    InetSocketAddress getSocketAddress();

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     */
    @Override
    void close();

}
