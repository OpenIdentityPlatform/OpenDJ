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
 * Copyright 2024-2026 3A Systems LLC.
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
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
        File serverRoot = embeddedOpenDJ.getServerRootDirectory();
        try {
            embeddedOpenDJ.run();
            assertTrue(embeddedOpenDJ.isRunning());
            assertTrue(serverRoot.isDirectory());

            //import ldif data from an input stream
            URI resUri = getClass().getClassLoader().getResource("opendj/data.ldif").toURI();
            try (InputStream is = Files.newInputStream(Paths.get(resUri))) {
                embeddedOpenDJ.importData(is);
            }

            //export OpenDJ data
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            embeddedOpenDJ.getData(config.getBaseDN(), bos);
            //getData() writes LDIF in the platform default charset, so it has to be read back
            //in the same one: LDIFEntryWriter(OutputStream) wraps it in a plain OutputStreamWriter
            String imported = bos.toString();
            assertTrue(imported.contains("dn: uid=jdoe,ou=people," + config.getBaseDN()));

            //test search in the imported data
            try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", config.getPort());
                 Connection connection = factory.getConnection()) {
                BindResult result = connection.bind("cn=Directory Manager", config.getAdminPassword().toCharArray());
                assertTrue(result.isSuccess());

                SearchRequest request = Requests.newSearchRequest(config.getBaseDN(),
                        SearchScope.WHOLE_SUBTREE, "(uid=jdoe)", "uid");
                try (ConnectionEntryReader reader = connection.search(request)) {
                    SearchResultEntry entry = reader.readEntry();
                    assertEquals(entry.getName().toString(), "uid=jdoe,ou=people," + config.getBaseDN());
                    assertEquals(entry.parseAttribute("uid").asString(), "jdoe");
                    assertFalse(reader.hasNext(), "the search returned more than one entry");
                }
            }
        } finally {
            //stop OpenDJ
            embeddedOpenDJ.close();
        }
        assertFalse(embeddedOpenDJ.isRunning());

        //the per-instance temporary directory is deleted on close
        assertFalse(serverRoot.exists(), leftovers(serverRoot));
        assertFalse(serverRoot.getParentFile().exists(), leftovers(serverRoot.getParentFile()));

        //a closed instance cannot be used any more: its directory is gone
        assertThrows(IllegalStateException.class, embeddedOpenDJ::run);
        assertThrows(IllegalStateException.class, embeddedOpenDJ::getServerRootDirectory);
        assertThrows(IllegalStateException.class, () -> embeddedOpenDJ.getData(config.getBaseDN(), new ByteArrayOutputStream()));

        //close() is idempotent, it is also registered as a shutdown hook
        embeddedOpenDJ.close();
    }

    /** Describes what is left in the given directory, to make an assertion failure diagnosable. */
    private static String leftovers(File directory) {
        if (!directory.exists()) {
            return "";
        }
        final StringBuilder message = new StringBuilder(directory + " still exists and contains:");
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.forEach(path -> message.append("\n  ").append(path));
        } catch (IOException e) {
            message.append(" <cannot be listed: ").append(e).append('>');
        }
        return message.toString();
    }
}
