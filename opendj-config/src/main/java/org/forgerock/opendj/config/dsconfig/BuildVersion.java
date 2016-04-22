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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;
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
import org.forgerock.util.Utils;

/**
 * Represents a particular version of OpenDJ useful for making comparisons between versions.
 * <p>
 * FIXME TODO Move this file in ? package.
 */
class BuildVersion implements Comparable<BuildVersion> {

    private final int major;
    private final int minor;
    private final int point;
    private final String rev;

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
     *            VCS revision.
     */
    public BuildVersion(final int major, final int minor, final int point, final String rev) {
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
        if (!binaryVersion.equals(instanceVersion)) {
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
        final String rev = fields[3];
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

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof BuildVersion) {
            final BuildVersion other = (BuildVersion) obj;
            return major == other.major && minor == other.minor && point == other.point;
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(final BuildVersion version) {
        if (major == version.major) {
            if (minor == version.minor) {
                if (point == version.point) {
                    if (rev == version.rev) {
                        return 0;
                    } else if (rev.compareTo(version.rev) < 0) {
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
     * Returns the VCS revision.
     *
     * @return The VCS revision.
     */
    public String getRevision() {
        return rev;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new int[] { major, minor, point, rev.hashCode() });
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
    @Override
    public String toString() {
        return Utils.joinAsString(".", major, minor, point, rev);
    }
}
