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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.servicetag;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.opends.server.core.DirectoryServer;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * SwordFishIdConfiguration allows to read the properties file,
 * This class allows to get the associated parsers.
 */
public class SwordFishIdConfiguration {

    /**
     * The tracer object for the debug logger.
     */
    private static final DebugTracer TRACER = getTracer();

    // Singleton
    private static SwordFishIdConfiguration service = null;
    // Parsers for the ServiceTag properties files
    private Set<SwordFishIDParser> parsers = new HashSet<SwordFishIDParser>();
    // Configuration properties files pattern
    private String FILE_PATTERN = "opends.uuids";
    private File[] listProperties;

    // Private constructor
    private SwordFishIdConfiguration() {

        try {

            // Build the full path to the properties files
            // if resources dir exists then read the property files
            // else use the default in config dir
            File serviceTag = new File(
                    DirectoryServer.getServerRoot() +
                    File.separatorChar +
                    "resources" +
                    File.separatorChar +
                    "servicetag");

            if ((!serviceTag.exists()) || (!serviceTag.isDirectory())) {
                serviceTag = new File(DirectoryServer.getInstanceRoot() +
                        File.separatorChar +
                        "config" +
                        File.separatorChar +
                        "servicetag");
                if ((!serviceTag.exists()) || (!serviceTag.isDirectory())) {
                    return;
                }
            }

            this.listProperties = serviceTag.listFiles();
            for (int i = 0; i < listProperties.length; i++) {
                try {
                    if (listProperties[i].getAbsolutePath().
                            contains(FILE_PATTERN)) {
                        parsers.add(new SwordFishIDParser(
                                listProperties[i].toURI().toURL()));
                    }
                } catch (Throwable t) {
                    if (debugEnabled()) {
                        TRACER.debugCaught(DebugLogLevel.WARNING, t);
                    }
                }
            }
        } catch (Exception ex) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.WARNING, ex);
            }
        }
    }

    /**
     * Returns the configuration object allowing to get parsers
     * and properties files.
     * @return the configuration service.
     */
    public static SwordFishIdConfiguration getService() {
        if (service == null) {
            service = new SwordFishIdConfiguration();
        }
        return service;
    }

    /**
     * Returns the list of parsers.
     * @return the list of parsers.
     */
    public Set<SwordFishIDParser> getParsers() {
        return this.parsers;
    }

    /**
     * Returns the list of processed properties files.
     * @return an Array of File.
     */
    public File[] getPropertiesFiles() {
        return this.listProperties;
    }
}
