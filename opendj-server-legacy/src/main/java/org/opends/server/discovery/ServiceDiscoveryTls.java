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
package org.opends.server.discovery;

import java.security.KeyStore;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.TrustManagers;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.core.ServerContext;

/** TLS trust and hostname verification shared by discovery mechanisms. */
final class ServiceDiscoveryTls {
    private ServiceDiscoveryTls() { }

    static X509TrustManager trustManager(DN providerDn, ServerContext context) throws Exception {
        final TrustManager[] managers;
        if (providerDn != null) {
            final TrustManagerProvider<?> provider = context.getTrustManagerProvider(providerDn);
            if (provider == null) {
                throw new IllegalStateException("Trust manager provider is unavailable: " + providerDn);
            }
            managers = provider.getTrustManagers();
        } else {
            final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            managers = factory.getTrustManagers();
        }
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("No X.509 trust manager is available");
    }

    static SSLContext context(String host, X509TrustManager trust) throws GeneralSecurityException {
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] { TrustManagers.checkHostName(host, trust) }, null);
        return context;
    }
}
