/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.util;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.InitializationException;

/**
 * This class provides a number of utility methods for using OpenDS in an
 * embedded manner (i.e., running within the same JVM as another application and
 * controlled by that application).
 */
public final class EmbeddedUtils {
    /**
     * Indicates whether the Directory Server is currently running.
     *
     * @return {@code true} if the server is currently running, or {@code false}
     *         if not.
     */
    public static boolean isRunning() {
        throw new RuntimeException("not implemented");
    }

    /**
     * Attempts to start the Directory Server.
     *
     * @param config
     *            The environment configuration to use for the server.
     * @throws ConfigException
     *             If a configuration problem is detected during the server
     *             initialization or startup process.
     * @throws InitializationException
     *             If the Directory Server is already running, or if an error
     *             occurs during server initialization or startup.
     */
    public static void startServer(DirectoryEnvironmentConfig config) throws ConfigException, InitializationException {
        throw new RuntimeException("not implemented");
    }

    /**
     * Attempts to stop the Directory Server.
     *
     * @param className
     *            The name of the class that initiated the shutdown.
     * @param reason
     *            A message explaining the reason for the shutdown.
     */
    public static void stopServer(String className, LocalizableMessage reason) {
        throw new RuntimeException("not implemented");
    }

    /**
     * Attempts to restart the Directory Server. This will perform an in-core
     * restart in which the existing server instance will be shut down, a new
     * instance will be created, and it will be reinitialized and restarted.
     *
     * @param className
     *            The name of the class that initiated the restart.
     * @param reason
     *            A message explaining the reason for the retart.
     * @param config
     *            The environment configuration to use for the new server
     *            instance.
     */
    public static void restartServer(String className, LocalizableMessage reason, DirectoryEnvironmentConfig config) {
        throw new RuntimeException("not implemented");
    }

    /**
     * Sets up a number of internal server data structures to ensure that they
     * are properly initialized for use. This is necessary if server libraries
     * are going to be used without the server running (e.g., to facilitate use
     * in an LDAP client API, for DN processing, etc.). This will have no effect
     * if the server has already been initialized for client use.
     */
    public static void initializeForClientUse() {
        throw new RuntimeException("not implemented");
    }
}
