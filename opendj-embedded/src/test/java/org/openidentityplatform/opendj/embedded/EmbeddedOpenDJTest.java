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
 * Copyright 2024 3A Systems LLC.
 */

package org.openidentityplatform.opendj.embedded;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class EmbeddedOpenDJTest {

    @Test
    public void testOpenDJ() throws Exception {
        EmbeddedOpenDJ embeddedOpenDJ = new EmbeddedOpenDJ();
        Thread t = new Thread(embeddedOpenDJ);
        t.start();
        for(int i =  0; i < 10 && !embeddedOpenDJ.isRunning(); i++) {
            Thread.sleep(1000);
        }
        assertTrue(embeddedOpenDJ.isRunning());
        Thread.sleep(1000);

        try(LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", 1389)) {
            Connection connection = factory.getConnection();
            BindResult result = connection.bind("cn=Directory Manager", "passw0rd".toCharArray());
            assertTrue(result.isSuccess());
        }
    }
}