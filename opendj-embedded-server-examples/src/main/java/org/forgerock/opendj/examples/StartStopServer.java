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
import static org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer.*;

import java.nio.file.Paths;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServerException;

/**
 * Start and stop a server that is already installed.
 */
public final class StartStopServer {

    /**
     * Main method.
     * <p>
     * The server is started, and this program waits for a Control-C on the terminal to stop the server.
     *
     * @param args
     *            The command line arguments: serverRootDir
     * @throws EmbeddedDirectoryServerException
     *          If an error occurs
     */
    public static void main(final String[] args) throws EmbeddedDirectoryServerException {
        if (args.length != 1) {
            System.err.println("Usage: serverRootDir");
            System.exit(1);
        }
        final String serverRootDir = args[0];

        final EmbeddedDirectoryServer server =
               manageEmbeddedDirectoryServerForRestrictedOps(
                        configParams()
                            .serverRootDirectory(serverRootDir)
                            .configurationFile(Paths.get(serverRootDir, "config", "config.ldif").toString()),
                            System.out,
                            System.err);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down ...");
                server.stop(StartStopServer.class.getName(),
                        LocalizableMessage.raw("Stopped after receiving Control-C"));
            }
        });

        System.out.println("Starting the server...");
        server.start();
        System.out.println("Type Ctrl-C to stop the server");
    }

    private StartStopServer() {
        // Not used.
    }
}
