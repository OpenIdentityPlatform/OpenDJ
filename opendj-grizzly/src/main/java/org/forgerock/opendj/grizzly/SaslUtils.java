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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import javax.security.sasl.SaslServer;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;

final class SaslUtils {

    /** Used to check if negotiated QOP is confidentiality or integrity. */
    static final String SASL_AUTH_CONFIDENTIALITY = "auth-conf";

    static final String SASL_AUTH_INTEGRITY = "auth-int";

    private static final Attribute<SaslServer> SASL_SERVER =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(SaslUtils.class + ".sasl-server");

    static SaslServer getSaslServer(final Connection connection) {
        return SASL_SERVER.get(connection);
    }

    static void setSaslServer(final Connection connection, final SaslServer saslServer) {
        SASL_SERVER.set(connection, saslServer);
    }

    private SaslUtils() {
    }
}
