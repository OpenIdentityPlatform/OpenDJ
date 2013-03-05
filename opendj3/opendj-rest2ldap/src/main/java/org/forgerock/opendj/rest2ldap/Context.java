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
package org.forgerock.opendj.rest2ldap;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.resource.ServerContext;
import org.forgerock.opendj.ldap.Connection;

/**
 * Common context information passed to containers and mappers.
 */
final class Context implements Closeable {
    private final Config config;
    private final AtomicReference<Connection> connection = new AtomicReference<Connection>();
    private final ServerContext context;

    Context(final Config config, final ServerContext context) {
        this.config = config;
        this.context = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        final Connection c = connection.getAndSet(null);
        if (c != null) {
            c.close();
        }
    }

    Config getConfig() {
        return config;
    }

    Connection getConnection() {
        return connection.get();
    }

    ServerContext getServerContext() {
        return context;
    }

    void setConnection(final Connection connection) {
        if (!this.connection.compareAndSet(null, connection)) {
            throw new IllegalStateException("LDAP connection obtained multiple times");
        }
    }

}
