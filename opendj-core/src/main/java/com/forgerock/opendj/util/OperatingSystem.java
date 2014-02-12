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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package com.forgerock.opendj.util;

/**
 * This class defines an enumeration that may be used to identify the operating system on which the JVM is running.
 */
public enum OperatingSystem {
    /**
     * The value indicating the AIX operating system.
     */
    AIX("AIX"),

    /**
     * The value indicating the FreeBSD operating system.
     */
    FREEBSD("FreeBSD"),

    /**
     * The value indicating the HP-UX operating system.
     */
    HPUX("HP-UX"),

    /**
     * The value indicating the Linux operating system.
     */
    LINUX("Linux"),

    /**
     * The value indicating the Mac OS X operating system.
     */
    MACOS("Mac OS X"),

    /**
     * The value indicating the Solaris operating system.
     */
    SOLARIS("Solaris"),

    /**
     * The value indicating the Windows operating system.
     */
    WINDOWS("Windows"),

    /**
     * The value indicating the z/OS operating system.
     */
    ZOS("z/OS"),

    /**
     * The value indicating an unknown operating system.
     */
    UNKNOWN("Unknown");

    // The human-readable name for this operating system.
    private String osName;

    private static boolean isWindows = false;
    private static boolean isVista = false;
    private static boolean isWindows2008 = false;
    private static boolean isWindows7 = false;
    private static boolean isMacOS = false;
    private static boolean isUnix = false;
    private static boolean isUnixBased = false;

    /**
     * Creates a new operating system value with the provided name.
     *
     * @param osName
     *            The human-readable name for the operating system.
     */
    private OperatingSystem(String osName) {
        this.osName = osName;
    }

    /**
     * Retrieves the human-readable name of this operating system.
     *
     * @return The human-readable name for this operating system.
     */
    public String toString() {
        return osName;
    }

    /**
     * Retrieves the operating system for the provided name.
     *
     * @param osName
     *            The name for which to retrieve the corresponding operating system.
     * @return The operating system for the provided name.
     */
    public static OperatingSystem forName(final String osName) {
        if (osName == null) {
            return UNKNOWN;
        }

        final String lowerName = osName.toLowerCase();

        if ((lowerName.indexOf("solaris") >= 0) || (lowerName.indexOf("sunos") >= 0)) {
            isUnix = true;
            isUnixBased = true;
            return SOLARIS;
        } else if (lowerName.indexOf("linux") >= 0) {
            isUnix = true;
            isUnixBased = true;
            return LINUX;
        } else if ((lowerName.indexOf("hp-ux") >= 0) || (lowerName.indexOf("hp ux") >= 0)
                || (lowerName.indexOf("hpux") >= 0)) {
            isUnix = true;
            isUnixBased = true;
            return HPUX;
        } else if (lowerName.indexOf("aix") >= 0) {
            isUnix = true;
            isUnixBased = true;
            return AIX;
        } else if (lowerName.indexOf("windows") >= 0) {
            isWindows = true;
            if (lowerName.toString().indexOf("windows 7") != -1) {
                isWindows7 = true;
            } else if (lowerName.indexOf("vista") != -1) {
                isVista = true;
            } else if (lowerName.indexOf("server 2008") != -1) {
                isWindows2008 = true;
            }
            return WINDOWS;
        } else if ((lowerName.indexOf("freebsd") >= 0) || (lowerName.indexOf("free bsd") >= 0)) {
            isUnix = true;
            isUnixBased = true;
            return FREEBSD;
        } else if ((lowerName.indexOf("macos") >= 0) || (lowerName.indexOf("mac os") >= 0)) {
            isMacOS = true;
            isUnixBased = true;
            return MACOS;
        } else if (lowerName.indexOf("z/os") >= 0) {
            return ZOS;
        } else {
            return UNKNOWN;
        }
    }

    /**
     * Indicates whether the provided operating system is UNIX-based. UNIX-based operating systems include Solaris,
     * Linux, HP-UX, AIX, FreeBSD, and Mac OS X.
     *
     * @param os
     *            The operating system for which to make the determination.
     * @return <CODE>true</CODE> if the provided operating system is UNIX-based, or <CODE>false</CODE> if not.
     */
    public static boolean isUNIXBased(OperatingSystem os) {
        switch (os) {
        case SOLARIS:
        case LINUX:
        case HPUX:
        case AIX:
        case FREEBSD:
        case MACOS:
            return true;
        default:
            return false;
        }
    }

    /**
     * Returns the operating system on which the JVM is running.
     *
     * @return The operating system on which the JVM is running
     */
    public static OperatingSystem getOperatingSystem() {
        return OperatingSystem.forName(System.getProperty("os.name"));
    }

    /**
     * Indicates whether the underlying operating system is a Windows variant.
     *
     * @return {@code true} if the underlying operating system is a Windows variant, or {@code false} if not.
     */
    public static boolean isWindows() {
        return isWindows;
    }

    /**
     * Indicates whether the underlying operating system is Windows Vista.
     *
     * @return {@code true} if the underlying operating system is Windows Vista, or {@code false} if not.
     */
    public static boolean isVista() {
        return isVista;
    }

    /**
     * Indicates whether the underlying operating system is Windows 2008.
     *
     * @return {@code true} if the underlying operating system is Windows 2008, or {@code false} if not.
     */
    public static boolean isWindows2008() {
        return isWindows2008;
    }

    /**
     * Indicates whether the underlying operating system is Windows 7.
     *
     * @return {@code true} if the underlying operating system is Windows 7, or {@code false} if not.
     */
    public static boolean isWindows7() {
        return isWindows7;
    }

    /**
     * Returns {@code true} if we are running under Mac OS and {@code false} otherwise.
     *
     * @return {@code true} if we are running under Mac OS and {@code false} otherwise.
     */
    public static boolean isMacOS() {
        return isMacOS;
    }

    /**
     * Returns {@code true} if we are running under Unix and {@code false} otherwise.
     *
     * @return {@code true} if we are running under Unix and {@code false} otherwise.
     */
    public static boolean isUnix() {
        return isUnix;
    }

    /**
     * Returns {@code true} if the OS is Unix based.
     *
     * @return {@code true} if the OS is Unix based.
     */
    public static boolean isUnixBased() {
        return isUnixBased;
    }

    /**
     * Indicates whether the underlying operating system has UAC (User Access Control).
     *
     * @return {@code true} if the underlying operating system has UAC (User Access Control), or {@code false} if not.
     */
    public static boolean hasUAC() {
        return isVista() || isWindows2008() || isWindows7();
    }
}
