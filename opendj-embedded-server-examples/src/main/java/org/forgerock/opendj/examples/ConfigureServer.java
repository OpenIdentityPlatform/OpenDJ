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

package org.forgerock.opendj.examples;

import static java.util.Arrays.asList;
import static org.opends.server.util.embedded.ConfigParameters.configParams;
import static org.opends.server.util.embedded.ConnectionParameters.connectionParams;
import static org.opends.server.util.embedded.EmbeddedDirectoryServer.defineServer;

import java.io.File;
import java.util.SortedSet;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.opends.server.util.embedded.EmbeddedDirectoryServer;
import org.opends.server.util.embedded.EmbeddedDirectoryServer.DirectoryConfigReader;
import org.opends.server.util.embedded.EmbeddedDirectoryServer.DirectoryConfigUpdater;
import org.opends.server.util.embedded.EmbeddedDirectoryServerException;

/**
 * Provides an example of read and update of the configuration of a server that
 * is already installed.
 * <p>
 * The server may be running or not.
 */
public final class ConfigureServer {

    /**
     * Main method.
     * <p>
     * Read the current base Dn of user backend and then change it
     * to the one provided as argument.
     *
     * @param args
     *            The command line arguments: serverRootDir newBaseDn [ldapPort]
     * @throws EmbeddedDirectoryServerException
     *          If an error occurs
     */
    public static void main(final String[] args) throws EmbeddedDirectoryServerException {
        if (args.length != 2 && args.length != 3) {
            System.err.println("Usage: serverRootDir newBaseDn [ldapPort]");
            System.exit(1);
        }
        final String serverRootDir = args[0];
        final String newBaseDn = args[1];
        final int ldapPort = args.length > 2 ? Integer.parseInt(args[2]) : 1500;

        EmbeddedDirectoryServer server =
                defineServer(
                        configParams()
                            .serverRootDirectory(serverRootDir)
                            .configurationFile(serverRootDir + File.separator + "config/config.ldif")
                            .build(),
                        connectionParams()
                            .hostName("localhost")
                            .ldapPort(ldapPort)
                            .bindDn("cn=Directory Manager")
                            .bindPassword("password")
                            .build(),
                        System.out,
                        System.err);

        // read the current base DN(s) of user backend
        SortedSet<DN> baseDns = server.readConfiguration(new DirectoryConfigReader<SortedSet<DN>>() {
            @Override
            public SortedSet<DN> read(RootCfgClient rootConfig) throws Exception {
                BackendCfgClient userRoot = rootConfig.getBackend("userRoot");
                return userRoot.getBaseDN();
            }
        });
        System.out.println("The current base Dn(s) of the user backend are: " + baseDns);

        // modify the base DN of the user backend
        server.updateConfiguration(new DirectoryConfigUpdater() {
            @Override
            public void update(RootCfgClient rootConfig) throws Exception {
                BackendCfgClient userRoot = rootConfig.getBackend("userRoot");
                userRoot.setBaseDN(asList(DN.valueOf(newBaseDn)));
                userRoot.commit();
            }
        });
        System.out.println("The base Dn of the user backend has been set to: " + newBaseDn);

    }

    private ConfigureServer() {
        // Not used.
    }
}
