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
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class EmbeddedOpenDJTest {

    @Test
    public void testOpenDJ() throws Exception {
        //set custom configuration
        Config config = new Config();

        //load custom schema from resource
        URI schemaUri = getClass().getClassLoader().getResource("opendj/99-users.ldif").toURI();
        config.setLdifSchema(new File(schemaUri).toString());

        //start embedded OpenDJ server
        EmbeddedOpenDJ embeddedOpenDJ = new EmbeddedOpenDJ(config);
        embeddedOpenDJ.run();
        assertTrue(embeddedOpenDJ.isRunning());

        //import ldif data from an input stream
        URI resUri = getClass().getClassLoader().getResource("opendj/data.ldif").toURI();
        byte[] bytes = Files.readAllBytes(Paths.get(resUri));
        String newBytes = new String(bytes);
        InputStream is = new ByteArrayInputStream(newBytes.getBytes(StandardCharsets.UTF_8));
        embeddedOpenDJ.importData(is);

        //export OpenDJ data
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        embeddedOpenDJ.getData("dc=openidentityplatform,dc=org", bos);
        String imported = bos.toString();
        assertTrue(imported.contains("dn: uid=jdoe,ou=people,dc=openidentityplatform,dc=org"));

        //test search in the imported data
        try(LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", 1389);
            Connection connection = factory.getConnection()) {
            BindResult result = connection.bind("cn=Directory Manager", "passw0rd".toCharArray());
            assertTrue(result.isSuccess());

            SearchRequest request = Requests.newSearchRequest("dc=openidentityplatform,dc=org",
                    SearchScope.WHOLE_SUBTREE, "(uid=jdoe)", "uid");
            ConnectionEntryReader reader = connection.search(request);
            SearchResultEntry entry = reader.readEntry();
            entry.getAllAttributes();
        }

        //stop OpenDJ
        embeddedOpenDJ.close();
        assertFalse(embeddedOpenDJ.isRunning());
    }
}