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
package com.forgerock.opendj.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.forgerock.util.Pair;

/** Utility methods reading information from {@code opendj-core}'s manifest. */
public final class ManifestUtil {
    private static final String OPENDJ_CORE_VERSION;
    private static final String OPENDJ_CORE_VERSION_WITH_REVISION;

    static {
        final Pair<String, String> versions = getVersions("opendj-core");
        OPENDJ_CORE_VERSION = versions.getFirst();
        OPENDJ_CORE_VERSION_WITH_REVISION = versions.getSecond();
    }

    /**
     * Returns the version with the revision contained in the module manifest whose name is provided.
     *
     * @param moduleName The module name for which to retrieve the version number
     * @return the version with the revision contained in the module manifest whose name is provided.
     */
    public static String getVersionWithRevision(String moduleName) {
        if ("opendj-core".equals(moduleName)) {
            return OPENDJ_CORE_VERSION_WITH_REVISION;
        }
        return getVersions(moduleName).getSecond();
    }

    /**
     * Returns the bundle version contained in the module manifest whose name is provided.
     *
     * @param moduleName The module name for which to retrieve the version number
     * @return the bundle version contained in the module manifest whose name is provided.
     */
    public static String getBundleVersion(String moduleName) {
        if ("opendj-core".equals(moduleName)) {
            return OPENDJ_CORE_VERSION;
        }
        return getVersions(moduleName).getFirst();
    }

    private static Pair<String, String> getVersions(String moduleName) {
        try {
            final Enumeration<URL> manifests = ManifestUtil.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (manifests.hasMoreElements()) {
                final URL manifestUrl = manifests.nextElement();
                if (manifestUrl.toString().contains(moduleName)) {
                    try (InputStream manifestStream = manifestUrl.openStream()) {
                        final Attributes attrs = new Manifest(manifestStream).getMainAttributes();
                        final String bundleVersion = attrs.getValue("Bundle-Version");
                        return Pair.of(bundleVersion,
                                       bundleVersion + " (revision " + attrs.getValue("SCM-Revision") + ")");
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException("IOException while determining opendj tool version", e);
        }
    }

    private ManifestUtil() {
        // do not instantiate util classes
    }
}
