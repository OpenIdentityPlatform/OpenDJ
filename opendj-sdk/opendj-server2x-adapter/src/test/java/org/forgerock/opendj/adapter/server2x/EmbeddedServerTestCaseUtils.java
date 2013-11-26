/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.adapter.server2x;

import static org.opends.server.util.StaticUtils.createEntry;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.forgerock.testng.ForgeRockTestCase;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.EmbeddedUtils;

/**
 * This class defines a main functions for starting and stopping the embedded
 * server in test cases.
 */
class EmbeddedServerTestCaseUtils extends ForgeRockTestCase {

    /**
     * The configuration properties for the tests.
     */
    final static Properties CONFIG_PROPERTIES = new Properties();

    /**
     * The path to the test classes.
     */
    final static String TEST_CLASSES_PATH = "./target/test-classes/";

    /**
     * The path to the configuration folder.
     */
    final static String TEST_CLASSES_PATH_CONFIG = "./target/test-classes/config/";

    /**
     * Starts the embedded server.
     *
     * @throws Exception If an unexpected error occurred.
     */
    public static void startServer() throws Exception {

        // Creates buildinfo file.
        final FileWriter fw = new FileWriter(new File(TEST_CLASSES_PATH_CONFIG, "buildinfo"));
        fw.write(BuildVersion.binaryVersion().toString());
        fw.close();

        // Embedded server files.
        DirectoryEnvironmentConfig envConfig = new DirectoryEnvironmentConfig();
        envConfig.setServerRoot(new File(TEST_CLASSES_PATH));
        envConfig.setConfigFile(new File(TEST_CLASSES_PATH_CONFIG, "config.ldif"));

        new File("./target/test-classes/logs").mkdirs();

        if (EmbeddedUtils.isRunning()) {
            return;
        } else {
            try {
                EmbeddedUtils.startServer(envConfig);
                // If something went wrong starting the server, log what happened.
            } catch (ConfigException ex) {
                Logger.getLogger("AdaptersTestCase - Server Config").log(Level.SEVERE, null, ex);
            } catch (InitializationException ex) {
                Logger.getLogger("AdaptersTestCase - Server Initialization").log(Level.SEVERE,
                        null, ex);
            }
        }
        // We use a memory backend for the tests.
        org.opends.server.types.DN baseDN = org.opends.server.types.DN.decode("dc=example,dc=org");

        MemoryBackend memoryBackend = new MemoryBackend();
        memoryBackend.setBackendID("test-server2x");
        memoryBackend.setBaseDNs(new org.opends.server.types.DN[] { baseDN });
        memoryBackend.initializeBackend();
        DirectoryServer.registerBackend(memoryBackend);

        memoryBackend.clearMemoryBackend();
        // The backend must be initialized before adding the entry.
        Entry e = createEntry(baseDN);
        memoryBackend.addEntry(e, null);

        assertTrue(EmbeddedUtils.isRunning());

        // Loading the properties requested for port numbers.
        loadProperties();
    }

    /**
     * Stops the embedded server.
     */
    public static void shutDownServer() {
        // Stops the server.
        EmbeddedUtils.stopServer("AdaptersTestCase", null);
        assertFalse(EmbeddedUtils.isRunning());
    }

    private static void loadProperties() {
        try {
            CONFIG_PROPERTIES.load(new FileInputStream(TEST_CLASSES_PATH_CONFIG
                    + "config.properties"));
        } catch (IOException ex) {
            Logger.getLogger("AdaptersTestCase - Access to config properties").log(Level.SEVERE,
                    null, ex);
        }
    }
}
