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
package org.forgerock.opendj.config;

import static org.forgerock.opendj.config.AdministratorAction.Type.SERVER_RESTART;
import static org.testng.Assert.assertEquals;

import org.forgerock.opendj.server.config.meta.CryptoManagerCfgDefn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CryptoManagerCfgDefnTest extends ConfigTestCase {

    @DataProvider
    public Object[][] sslProperties() {
        CryptoManagerCfgDefn defn = CryptoManagerCfgDefn.getInstance();
        return new Object[][] {
            { "ssl-cert-nickname", defn.getSSLCertNicknamePropertyDefinition() },
            { "ssl-protocol", defn.getSSLProtocolPropertyDefinition() },
            { "ssl-cipher-suite", defn.getSSLCipherSuitePropertyDefinition() },
            { "ssl-encryption", defn.getSSLEncryptionPropertyDefinition() },
        };
    }

    /**
     * The crypto manager reads its SSL properties once, when it is created, and replication
     * keeps what it read for the life of the session, so a change to any of them only takes
     * effect once the server is restarted. A component restart is not an option either: the
     * crypto manager has no enabled property, so it cannot be disabled and re-enabled.
     */
    @Test(dataProvider = "sslProperties")
    public void sslPropertiesRequireAServerRestart(String name, PropertyDefinition<?> property) {
        assertEquals(property.getAdministratorAction().getType(), SERVER_RESTART,
            "unexpected administrator action for " + name);
    }
}
