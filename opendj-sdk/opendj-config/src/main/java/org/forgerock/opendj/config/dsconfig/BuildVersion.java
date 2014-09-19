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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.ldap.ConfigMessages.ERR_BUILDVERSION_MISMATCH;
import static com.forgerock.opendj.ldap.ConfigMessages.ERR_BUILDVERSION_MALFORMED;
import static com.forgerock.opendj.ldap.ConfigMessages.ERR_BUILDVERSION_NOT_FOUND;
import static com.forgerock.opendj.ldap.ConfigMessages.ERR_CONFIGVERSION_NOT_FOUND;
import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

/**
 * Represents a particular version of OpenDJ useful for making comparisons between versions. FIXME TODO Move this file
 * in ? package.
 */
public class BuildVersion implements Comparable<BuildVersion> {

    private final int major;
    private final int minor;
    private final int point;
    private final long rev;

    /**
     * Creates a new build version using the provided version information.
     *
     * @param major
     *            Major release version number.
     * @param minor
     *            Minor release version number.
     * @param point
     *            Point release version number.
     * @param rev
     *            VCS revision number.
     */
    public BuildVersion(final int major, final int minor, final int point, final long rev) {
        this.major = major;
        this.minor = minor;
        this.point = point;
        this.rev = rev;
    }

    /**
     * Returns the build version as specified in the entry "cn=Version,cn=monitor".
     *
     * @param connection
     *            The connection to use to read the entry.
     * @return The build version as specified in the current installation configuration.
     * @throws ConfigException
     *             Sends an exception if it is impossible to retrieve the version configuration entry.
     */
    public static BuildVersion binaryVersion(final Connection connection) throws ConfigException {
        try {
            final SearchResultEntry entry = connection.readEntry("", "fullVendorVersion");
            return valueOf(entry.getAttribute("fullVendorVersion").firstValueAsString());
        } catch (LdapException e) {
            throw new ConfigException(ERR_CONFIGVERSION_NOT_FOUND.get());
        }
    }

    /**
     * Checks if the binary version is the same than the instance version. If not, a configuration exception is thrown.
     *
     * @param connection
     *            The connection to use to read the configuration entry.
     * @throws ConfigException
     *             Sends an exception if the version mismatch.
     */
    public static void checkVersionMismatch(final Connection connection) throws ConfigException {
        final BuildVersion binaryVersion = BuildVersion.binaryVersion(connection);
        final BuildVersion instanceVersion = BuildVersion.instanceVersion();
        if (!binaryVersion.toString().equals(instanceVersion.toString())) {
            throw new ConfigException(ERR_BUILDVERSION_MISMATCH.get(binaryVersion, instanceVersion));
        }
    }

    /**
     * Reads the instance version from config/buildinfo.
     *
     * @return The instance version from config/buildinfo.
     * @throws ConfigException
     *             If an error occurred while reading or parsing the version.
     */
    public static BuildVersion instanceVersion() throws ConfigException {
        final String buildInfo = ConfigurationFramework.getInstance().getInstancePath() + File.separator + "config"
                + File.separator + "buildinfo";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(buildInfo));
            final String s = reader.readLine();
            if (s != null) {
                return valueOf(s);
            } else {
                throw new ConfigException(ERR_BUILDVERSION_MALFORMED.get(buildInfo));
            }
        } catch (IOException e) {
            throw new ConfigException(ERR_BUILDVERSION_NOT_FOUND.get(buildInfo));
        } catch (final IllegalArgumentException e) {
            throw new ConfigException(ERR_BUILDVERSION_MALFORMED.get(buildInfo));
        } finally {
            closeSilently(reader);
        }
    }

    /**
     * Parses the string argument as a build version. The string must be of the form:
     *
     * <pre>
     * major.minor.point.rev
     * </pre>
     *
     * @param s
     *            The string to be parsed as a build version.
     * @return The parsed build version.
     * @throws IllegalArgumentException
     *             If the string does not contain a parsable build version.
     */
    public static BuildVersion valueOf(final String s) {
        final String[] fields = s.split("\\.");
        if (fields.length != 4) {
            throw new IllegalArgumentException("Invalid version string " + s);
        }
        final int major = Integer.parseInt(fields[0]);
        final int minor = Integer.parseInt(fields[1]);
        final int point = Integer.parseInt(fields[2]);
        final long rev = Long.parseLong(fields[3]);
        return new BuildVersion(major, minor, point, rev);
    }

    /**
     * Returns the major release version number.
     *
     * @return The major release version number.
     */
    public int getMajorVersion() {
        return major;
    }

    /**
     * Returns the minor release version number.
     *
     * @return The minor release version number.
     */
    public int getMinorVersion() {
        return minor;
    }

    /**
     * Returns the point release version number.
     *
     * @return The point release version number.
     */
    public int getPointVersion() {
        return point;
    }

    /** {@inheritDoc} */
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof BuildVersion) {
            final BuildVersion other = (BuildVersion) obj;
            return major == other.major && minor == other.minor && point == other.point && rev == other.rev;
        } else {
            return false;
        }
    }

    /** {@inheritDoc} */
    public int compareTo(final BuildVersion version) {
        if (major == version.major) {
            if (minor == version.minor) {
                if (point == version.point) {
                    if (rev == version.rev) {
                        return 0;
                    } else if (rev < version.rev) {
                        return -1;
                    }
                } else if (point < version.point) {
                    return -1;
                }
            } else if (minor < version.minor) {
                return -1;
            }
        } else if (major < version.major) {
            return -1;
        }
        return 1;
    }

    /**
     * Returns the VCS revision number.
     *
     * @return The VCS revision number.
     */
    public long getRevisionNumber() {
        return rev;
    }

    /** {@inheritDoc} */
    public int hashCode() {
        return Arrays.hashCode(new int[] { major, minor, point, (int) (rev >>> 32), (int) (rev & 0xFFFFL) });
    }

    /**
     * Returns the string representation of the version. E.g:
     *
     * <pre>
     * version : 2.8.0.1022
     * </pre>
     *
     * @return The string representation of the version.
     */
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(major);
        builder.append('.');
        builder.append(minor);
        builder.append('.');
        builder.append(point);
        builder.append('.');
        builder.append(rev);
        return builder.toString();
    }
}
