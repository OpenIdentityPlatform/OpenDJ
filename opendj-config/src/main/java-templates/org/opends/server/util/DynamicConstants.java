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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.util;

/**
 * This file contains a number of constants that are used throughout the
 * Directory Server source. It was dynamically generated as part of the
 * build process and should not be edited directly.
 */
public final class DynamicConstants {

    /**
     * The official full product name for the Directory Server.
     */
    public static String PRODUCT_NAME = "${serverProductName}";

    /**
     * The short product name for the Directory Server.
     */
    public static String SHORT_NAME = "${serverShortProductName}";

    /**
     * The major version number for the Directory Server.
     */
    public static int MAJOR_VERSION = ${parsedVersion.majorVersion};

    /**
     * The minor version number for the Directory Server.
     */
    public static int MINOR_VERSION = ${parsedVersion.minorVersion};

    /**
     * The point version number for the Directory Server.
     */
    public static int POINT_VERSION = ${parsedVersion.incrementalVersion};

    /**
     * The official build number for the Directory Server.
     */
    public static int BUILD_NUMBER = ${parsedVersion.buildNumber};

    /**
     * The version qualifier string for the Directory Server.
     */
    public static String VERSION_QUALIFIER = "${parsedVersion.qualifier}";

    /**
     * The set of bug IDs for fixes included in this build of the Directory
     * Server.
     */
    public static String FIX_IDS = "${issuesFixIds}";

    /**
     * The build ID for the generated build of the Directory Server.
     */
    public static String BUILD_ID = "${buildDateTime}";

    /**
     * The username of the user that created this build.
     */
    public static String BUILD_USER = "${user.name}";

    /**
     * The Java version used to generate this build.
     */
    public static String BUILD_JAVA_VERSION = "${java.version}";

    /**
     * The vendor for the Java version used to generate this build.
     */
    public static String BUILD_JAVA_VENDOR = "${java.vendor}";

    /**
     * The JVM version used to generate this build.
     */
    public static String BUILD_JVM_VERSION = "${java.vm.version}";

    /**
     * The vendor for the JVM used to generate this build.
     */
    public static String BUILD_JVM_VENDOR = "${java.vm.vendor}";

    /**
     * The operating system on which this build was generated.
     */
    public static String BUILD_OS = "${os.name} ${os.version} ${os.arch}"; 

    /**
     * Indicates whether this is a debug build of the Directory Server that may
     * include additional debugging facilities not available in standard release
     * versions.
     */
    public static boolean DEBUG_BUILD = ${isDebugBuild};

    /**
     * The Subversion revision number on which this build is based.
     */
    public static long REVISION_NUMBER = ${buildRevision};

    /**
     * The Subversion url repository location on which this build is based.
     */
    public static String URL_REPOSITORY = "${scm.url}";

    /**
     * The documentation home.
     */
    public static String DOC_REFERENCE_HOME = "${docReferenceHome}";

    /**
     * The documentation url.
     */
    public static String DOC_REFERENCE_WIKI = "${docReferenceWiki}";

    /**
     * The documentation url.
     */
    public static String DOC_QUICK_REFERENCE_GUIDE = "${docQuickRefGuide}";

    /**
     * The administration guide.
     */
    public static String ADMINISTRATION_GUIDE_URL = "${adminGuideUrl}";

    /**
     * A string representation of the version number.
     */
    public static String VERSION_NUMBER_STRING = "${project.version}";

    /**
     * A string representation of the version number.
     */
    public static String OPENDJ_NUMBER_STRING = "${project.version}";

    /**
     * A string representation of the release version.
     */
    public static String RELEASE_VERSION_STRING = OPENDJ_NUMBER_STRING;

    /**
     * A compact version string for this product, suitable for use in path names
     * and similar cases.
     */
    public static String COMPACT_VERSION_STRING = SHORT_NAME + "-" + VERSION_NUMBER_STRING;

    /**
     * A full version string for this product.
     */
    public static String FULL_VERSION_STRING = PRODUCT_NAME + " " + RELEASE_VERSION_STRING
            + (((FIX_IDS != null) && (FIX_IDS.length() > 0)) ? "+" + FIX_IDS : "");

    /**
     * A printable version string for this product.
     */
    public static final String PRINTABLE_VERSION_STRING = FULL_VERSION_STRING + System.getProperty("line.separator")
            + "Build " + BUILD_ID + System.getProperty("line.separator");

}
