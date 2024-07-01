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

import static org.forgerock.opendj.server.embedded.ConfigParameters.configParams;
import static org.forgerock.opendj.server.embedded.ConnectionParameters.connectionParams;
import static org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer.manageEmbeddedDirectoryServer;
import static org.forgerock.opendj.server.embedded.SetupParameters.setupParams;

import java.io.File;

import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServerException;

/**
 * Setup a server from a OpenDJ archive using the EmbeddedDirectoryServer class.
 */
public final class SetupServer {

    /**
     * Main method.
     * <p>
     * The OpenDJ archive is the zip archive that is resulting from a maven build.
     *
     * The server root directory is the directory where OpenDJ will be installed. Because
     * the archive contains the "opendj" directory, it is mandatory to provide a server root
     * directory that is named "opendj" (the archive will be automatically extracted in the
     * parent directory of the provided server root directory).
     *
     * Other parameters are usual parameters to setup a server.
     *
     * @param args
     *            The command line arguments: openDJArchive, serverRootDir
     *            and optionally: baseDn, backendType, ldapPort, adminPort, jmxPort
     * @throws EmbeddedDirectoryServerException
     *          If an error occurs
     */
    public static void main(final String[] args) throws EmbeddedDirectoryServerException {
        if (args.length != 2 && args.length != 4 && args.length != 7) {
            System.err.println("Usage: openDJArchive serverRootDir "
                    + "[baseDn backendType [ldapPort adminPort jmxPort]]");
            System.exit(1);
        }

        int i = 0;
        final String openDJArchive = args[i++];
        final String serverRootDir = args[i++];
        final String baseDn = (args.length > i) ? args[i++] : "o=example";
        final String backendType = (args.length > i) ? args[i++] : "pdb";
        final int ldapPort = (args.length > i) ? Integer.parseInt(args[i++]) : 1500;
        final int adminPort = (args.length > i) ? Integer.parseInt(args[i++]) : 4500;
        final int jmxPort = (args.length > i) ? Integer.parseInt(args[i++]) : 1600;

        performSetup(openDJArchive, serverRootDir, baseDn, backendType, ldapPort, adminPort, jmxPort);
    }

    /** Performs the setup with provided parameters. */
    static void performSetup(final String openDJArchive, final String serverRootDir, final String baseDn,
            final String backendType, final int ldapPort, final int adminPort, final int jmxPort)
            throws EmbeddedDirectoryServerException {
        EmbeddedDirectoryServer server =
                manageEmbeddedDirectoryServer(
                        configParams()
                            .serverRootDirectory(serverRootDir)
                            .configurationFile(serverRootDir + File.separator + "config/config.ldif"),
                        connectionParams()
                            .hostName("localhost")
                            .ldapPort(ldapPort)
                            .bindDn("cn=Directory Manager")
                            .bindPassword("password")
                            .adminPort(adminPort),
                        System.out,
                        System.err);

        server.extractArchiveForSetup(new File(openDJArchive));
        server.setup(
                setupParams()
                    .baseDn(baseDn)
                    .backendType(backendType)
                    .jmxPort(jmxPort));
    }

    private SetupServer() {
        // Not used.
    }
}
