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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.io;

import java.net.InetSocketAddress;

/** Information asserted by a PROXY protocol sender. */
public final class ProxyProtocolHeader {
    private final InetSocketAddress sourceAddress;
    private final boolean clientSsl;
    private final String clientCipher;

    ProxyProtocolHeader(InetSocketAddress sourceAddress, boolean clientSsl, String clientCipher) {
        this.sourceAddress = sourceAddress;
        this.clientSsl = clientSsl;
        this.clientCipher = clientCipher;
    }

    /** @return the asserted source, or null if the TCP peer should be used */
    public InetSocketAddress getSourceAddress() {
        return sourceAddress;
    }

    /** @return whether the sender asserts that the client used TLS */
    public boolean isClientSsl() {
        return clientSsl;
    }

    /** @return the asserted cipher, or null when none was supplied */
    public String getClientCipher() {
        return clientCipher;
    }
}
