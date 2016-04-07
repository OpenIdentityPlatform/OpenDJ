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
 *  Copyright 2015-2016 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/** Class that prints the version of the SDK to System.out. */
public final class ToolVersionHandler implements VersionHandler {

    /**
     * Returns a {@link VersionHandler} which should be used by OpenDJ SDK tools.
     * <p>
     * The printed version and SCM revision will be the one of the opendj-core module.
     * @return A {@link VersionHandler} which should be used by OpenDJ SDK tools.
     */
    public static VersionHandler newSdkVersionHandler() {
        return newToolVersionHandler("opendj-core");
    }

    /**
     * Returns a {@link VersionHandler} which should be used to print version and SCM revision of a module.
     * <p>
     * The printed version and SCM revision will be read from the module MANIFEST.MF‌ file.
     * @param moduleName
     *       Name of the module which uniquely identify the URL of the MANIFEST‌.MF file.
     * @return A {@link VersionHandler} which should be used by OpenDJ SDK tools.
     */
    public static VersionHandler newToolVersionHandler(final String moduleName) {
        return new ToolVersionHandler(moduleName);
    }

    private final String moduleName;

    private ToolVersionHandler(final String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    public void printVersion() {
        System.out.println(getVersion());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + getVersion() + ")";
    }

    private String getVersion() {
        try {
            final Enumeration<URL> manifests = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (manifests.hasMoreElements()) {
                final URL manifestUrl = manifests.nextElement();
                if (manifestUrl.toString().contains(moduleName)) {
                    try (InputStream manifestStream = manifestUrl.openStream()) {
                        final Attributes attrs = new Manifest(manifestStream).getMainAttributes();
                        return attrs.getValue("Bundle-Version") + " (revision " + attrs.getValue("SCM-Revision") + ")";
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException("IOException while determining opendj tool version", e);
        }
    }
}
