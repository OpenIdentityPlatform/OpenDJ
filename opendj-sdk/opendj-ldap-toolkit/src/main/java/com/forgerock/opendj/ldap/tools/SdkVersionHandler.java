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
 * Copyright 2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.forgerock.opendj.cli.VersionHandler;

/** Class that prints the version of the SDK to System.out. */
public class SdkVersionHandler implements VersionHandler {

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
                if (manifestUrl.toString().contains("/opendj-core-")) {
                    try (InputStream manifestStream = manifestUrl.openStream()) {
                        final Attributes attrs = new Manifest(manifestStream).getMainAttributes();
                        return attrs.getValue("Bundle-Version") + " (revision " + attrs.getValue("SCM-Revision") + ")";
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException("IOException while determining SDK version", e);
        }
    }
}
