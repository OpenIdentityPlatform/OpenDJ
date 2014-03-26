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
 * This class defines an enumeration that may be used to identify the operating system
 * on which the JVM is running.
 */
public enum OperatingSystem {
    /**
     * The value indicating the AIX operating system.
     */
    AIX("AIX", false, false, true),

    /**
     * The value indicating the FreeBSD operating system.
     */
    FREEBSD("FreeBSD", false, false, true),

    /**
     * The value indicating the HP-UX operating system.
     */
    HPUX("HP UX", false, false, true),

    /**
     * The value indicating the Linux operating system.
     */
    LINUX("Linux", false, false, true),

    /**
     * The value indicating the Mac OS X operating system.
     */
    MACOSX("Mac OS X", false, true, true),

    /**
     * The value indicating the Solaris operating system.
     */
    SOLARIS("Solaris", false, false, true),

    /**
     * The value indicating the Windows operating system.
     */
    WINDOWS("Windows", true, false, false),

    /**
     * The value indicating the Windows 7 operating system.
     */
    WINDOWS7("Windows 7", true, false, false),

    /**
     * The value indicating the Windows Vista operating system.
     */
    WINDOWS_VISTA("Windows Vista", true, false, false),

    /**
     * The value indicating the Windows Server 2008 operating system.
     */
    WINDOWS_SERVER_2008("Server 2008", true, false, false),

    /**
     * The value indicating the z/OS operating system.
     */
    ZOS("z/OS", false, false, false),

    /**
     * The value indicating an unknown operating system.
     */
    UNKNOWN("Unknown", false, false, false);

    // The human-readable name for this operating system.
    private String osName;

    private boolean isWindows;
    private boolean isMacOS;
    private boolean isUnixBased;

    private static final OperatingSystem INSTANCE = forName(System.getProperty("os.name"));

    /**
     * Creates a new operating system value with the provided name.
     *
     * @param osName
     *            The human-readable name for the operating system.
     */
    private OperatingSystem(String osName, boolean isWindows, boolean isMacOS, boolean isUnixBased) {
        this.osName = osName;
        this.isWindows = isWindows;
        this.isMacOS = isMacOS;
        this.isUnixBased = isUnixBased;
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
            return SOLARIS;
        } else if (lowerName.indexOf("linux") >= 0) {
            return LINUX;
        } else if ((lowerName.indexOf("hp-ux") >= 0) || (lowerName.indexOf("hp ux") >= 0)
                || (lowerName.indexOf("hpux") >= 0)) {
            return HPUX;
        } else if (lowerName.indexOf("aix") >= 0) {
            return AIX;
        } else if (lowerName.indexOf("windows") >= 0) {
            if (lowerName.indexOf("windows 7") != -1) {
                return WINDOWS7;
            } else if (lowerName.indexOf("vista") != -1) {
                return WINDOWS_VISTA;
            } else if (lowerName.indexOf("server 2008") != -1) {
                return WINDOWS_SERVER_2008;
            }
            return WINDOWS;
        } else if ((lowerName.indexOf("freebsd") >= 0) || (lowerName.indexOf("free bsd") >= 0)) {
            return FREEBSD;
        } else if ((lowerName.indexOf("macos x") >= 0) || (lowerName.indexOf("mac os x") >= 0)) {
            return MACOSX;
        } else if (lowerName.indexOf("z/os") >= 0) {
            return ZOS;
        }
        return UNKNOWN;
    }

    /**
     * Returns the operating system on which the JVM is running.
     *
     * @return The operating system on which the JVM is running
     */
    public static OperatingSystem getOperatingSystem() {
        return INSTANCE;
    }

    /**
     * Indicates whether the underlying operating system is a Windows variant.
     *
     * @return {@code true} if the underlying operating system is a Windows variant, or {@code false} if not.
     */
    public static boolean isWindows() {
        return INSTANCE.isWindows;
    }

    /**
     * Indicates whether the underlying operating system is Windows Vista.
     *
     * @return {@code true} if the underlying operating system is Windows Vista, or {@code false} if not.
     */
    public static boolean isVista() {
        return INSTANCE == WINDOWS_VISTA;
    }

    /**
     * Indicates whether the underlying operating system is Windows 2008.
     *
     * @return {@code true} if the underlying operating system is Windows 2008, or {@code false} if not.
     */
    public static boolean isWindows2008() {
        return INSTANCE == WINDOWS_SERVER_2008;
    }

    /**
     * Indicates whether the underlying operating system is Windows 7.
     *
     * @return {@code true} if the underlying operating system is Windows 7, or {@code false} if not.
     */
    public static boolean isWindows7() {
        return INSTANCE == WINDOWS7;
    }

    /**
     * Returns {@code true} if we are running under Mac OS and {@code false} otherwise.
     *
     * @return {@code true} if we are running under Mac OS and {@code false} otherwise.
     */
    public static boolean isMacOS() {
        return INSTANCE.isMacOS;
    }

    /**
     * Returns {@code true} if we are running under Unix and {@code false} otherwise.
     *
     * @return {@code true} if we are running under Unix and {@code false} otherwise.
     */
    public static boolean isUnix() {
        return INSTANCE.isUnixBased;
    }

    /**
     * Returns {@code true} if the OS is Unix based.
     *
     * @return {@code true} if the OS is Unix based.
     */
    public static boolean isUnixBased() {
        return INSTANCE.isUnixBased;
    }

    /**
     * Returns {@code true} if the OS is Unknown.
     *
     * @return {@code true} if the OS is Unknown.
     */
    public static boolean isUnknown() {
        return INSTANCE == UNKNOWN;
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
