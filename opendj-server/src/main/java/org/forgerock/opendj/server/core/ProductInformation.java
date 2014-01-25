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
 *       Copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.server.core;

import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;
import java.util.Properties;

/**
 * OpenDJ product information, including version number, build information, and
 * references to documentation.
 */
public final class ProductInformation {
    private static final ProductInformation DEFAULT = new ProductInformation("opendj");

    /**
     * Returns the singleton product information instance.
     *
     * @return The singleton product information instance.
     */
    public static ProductInformation getInstance() {
        return DEFAULT;
    }

    private final Properties properties;
    private final String versionFull;
    private final String versionPrintable;

    private ProductInformation(final String productName) {
        final String resourceName = "/META-INF/product/" + productName + ".properties";
        final InputStream stream = getClass().getResourceAsStream(resourceName);

        if (stream == null) {
            throw new MissingResourceException("Can't find product information " + resourceName,
                    productName, "");
        }

        properties = new Properties();
        final InputStream is = new BufferedInputStream(stream);
        try {
            properties.load(is);
        } catch (final IOException e) {
            throw new MissingResourceException("Can't load product information " + resourceName
                    + " due to IO exception: " + e.getMessage(), productName, "");
        } finally {
            closeSilently(is);
        }

        versionFull =
                productName() + " " + version()
                        + (patchFixIds().length() > 0 ? "+" + patchFixIds() : "");
        versionPrintable =
                versionFull + System.getProperty("line.separator") + "Build " + buildId()
                        + System.getProperty("line.separator");
    }

    /**
     * Returns the build ID for the generated build of the Directory Server.
     *
     * @return The build ID for the generated build of the Directory Server.
     */
    public String buildId() {
        return properties.getProperty("build.id");
    }

    /**
     * Returns {@code true} if this is a debug build of the Directory Server
     * that may include additional debugging facilities not available in
     * standard release versions.
     *
     * @return {@code true} if this is a debug build of the Directory Server
     *         that may include additional debugging facilities not available in
     *         standard release versions.
     */
    public boolean buildIsDebug() {
        return Boolean.valueOf(properties.getProperty("build.isdebug"));
    }

    /**
     * Returns the vendor for the Java version used to generate this build.
     *
     * @return The vendor for the Java version used to generate this build.
     */
    public String buildJavaVendor() {
        return properties.getProperty("build.java.vendor");
    }

    /**
     * Returns the Java version used to generate this build.
     *
     * @return The Java version used to generate this build.
     */
    public String buildJavaVersion() {
        return properties.getProperty("build.java.version");
    }

    /**
     * Returns the vendor for the JVM used to generate this build.
     *
     * @return The vendor for the JVM used to generate this build.
     */
    public String buildJvmVendor() {
        return properties.getProperty("build.jvm.vendor");
    }

    /**
     * Returns the JVM version used to generate this build.
     *
     * @return The JVM version used to generate this build.
     */
    public String buildJvmVersion() {
        return properties.getProperty("build.jvm.version");
    }

    /**
     * Returns the operating system on which this build was generated.
     *
     * @return The operating system on which this build was generated.
     */
    public String buildOs() {
        return properties.getProperty("build.os");
    }

    /**
     * Returns the username of the user that created this build.
     *
     * @return The username of the user that created this build.
     */
    public String buildUser() {
        return properties.getProperty("build.user");
    }

    /**
     * Returns the URL of the product WIKI page.
     *
     * @return The URL of the product WIKI page.
     */
    public String documentationAdminGuideUrl() {
        return properties.getProperty("doc.guide.admin.url");
    }

    /**
     * Returns the URL of the product home page.
     *
     * @return The URL of the product home page.
     */
    public String documentationHomePageUrl() {
        return properties.getProperty("doc.homepage.url");
    }

    /**
     * Returns the URL of the product WIKI page.
     *
     * @return The URL of the product WIKI page.
     */
    public String documentationReferenceGuideUrl() {
        return properties.getProperty("doc.guide.ref.url");
    }

    /**
     * Returns the URL of the product WIKI page.
     *
     * @return The URL of the product WIKI page.
     */
    public String documentationWikiUrl() {
        return properties.getProperty("doc.wiki.url");
    }

    /**
     * Returns the set of bug IDs for fixes included in this build of the
     * Directory Server.
     *
     * @return The set of bug IDs for fixes included in this build of the
     *         Directory Server.
     */
    public String patchFixIds() {
        return properties.getProperty("patch.fix.ids");
    }

    /**
     * Returns the full product name for the Directory Server, which may contain
     * white space.
     *
     * @return The full product name for the Directory Server.
     */
    public String productName() {
        return properties.getProperty("product.name");
    }

    /**
     * Returns the product publication date.
     *
     * @return The product publication date.
     */
    public String productPublicationDate() {
        return properties.getProperty("product.publication.date");
    }

    /**
     * Returns the product release date.
     *
     * @return The product release date.
     */
    public String productReleaseDate() {
        return properties.getProperty("product.release.date");
    }

    /**
     * Returns the short product name for the Directory Server, suitable for use
     * in file names.
     *
     * @return The short product name for the Directory Server.
     */
    public String productShortName() {
        return properties.getProperty("product.name.short");
    }

    /**
     * Returns the revision number of the source repository on which this build
     * is based.
     *
     * @return The revision number of the source repository on which this build
     *         is based.
     */
    public String scmRevision() {
        return properties.getProperty("scm.revision");
    }

    /**
     * Returns the URL of the source repository location on which this build is
     * based.
     *
     * @return The URL of the source repository location on which this build is
     *         based.
     */
    public String scmUrl() {
        return properties.getProperty("scm.url");
    }

    /**
     * Returns the version number for the Directory Server. The return string
     * will have the format {@code major.minor.point[-qualifier]}.
     *
     * @return The version number for the Directory Server.
     */
    public String version() {
        return properties.getProperty("version");
    }

    /**
     * Returns the build number for the Directory Server.
     *
     * @return The build number for the Directory Server.
     */
    public int versionBuildNumber() {
        return Integer.valueOf(properties.getProperty("version.build"));
    }

    /**
     * Returns the compact version string for this product, suitable for use in
     * path names and similar cases.
     *
     * @return The compact version string for this product, suitable for use in
     *         path names and similar cases.
     */
    public String versionCompact() {
        return properties.getProperty("version.compact");
    }

    /**
     * Returns the full version string for this product.
     *
     * @return The full version string for this product.
     */
    public String versionFull() {
        return versionFull;
    }

    /**
     * Returns the major version number for the Directory Server.
     *
     * @return The major version number for the Directory Server.
     */
    public int versionMajorNumber() {
        return Integer.valueOf(properties.getProperty("version.major"));
    }

    /**
     * Returns the minor version number for the Directory Server.
     *
     * @return The minor version number for the Directory Server.
     */
    public int versionMinorNumber() {
        return Integer.valueOf(properties.getProperty("version.minor"));
    }

    /**
     * Returns the point version number for the Directory Server.
     *
     * @return The point version number for the Directory Server.
     */
    public int versionPointNumber() {
        return Integer.valueOf(properties.getProperty("version.point"));
    }

    /**
     * Returns the printable version string for this product.
     *
     * @return The printable version string for this product.
     */
    public String versionPrintable() {
        return versionPrintable;
    }

    /**
     * Returns the version qualifier string for the Directory Server.
     *
     * @return The version qualifier string for the Directory Server.
     */
    public String versionQualifier() {
        return properties.getProperty("version.qualifier");
    }

    /**
     * Returns the revision number of the source repository on which this build
     * is based.
     *
     * @return The revision number of the source repository on which this build
     *         is based.
     */
    public String versionRevision() {
        return properties.getProperty("scm.revision");
    }
}
