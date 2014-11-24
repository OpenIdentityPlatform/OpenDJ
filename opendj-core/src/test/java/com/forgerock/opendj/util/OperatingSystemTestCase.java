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
 *      Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.util;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class tests the model functionality.
 */
@SuppressWarnings("javadoc")
public class OperatingSystemTestCase extends UtilTestCase {

    // @formatter:off
    @DataProvider(name = "allOS")
    Object[][] createValidArguments() throws Exception {
        return new Object[][] {
            { null },
            { "" },
            { "AIX" },
            { "Digital Unix" },
            { "FreeBSD" },
            { "HP UX" },
            { "Irix" },
            { "Linux" },
            { "Mac OS" },
            { "Mac OS X" },
            { "MPE/iX" },
            { "Netware 4.11" },
            { "OS/2" },
            { "Solaris" },
            { "Windows 2000" },
            { "Windows Server 2008" },
            { "Windows 95" },
            { "Windows 98" },
            { "Windows NT" },
            { "Windows Vista" },
            { "Windows 7" },
            { "Windows XP"  },
        };
    }
    // @formatter:on

    @Test(dataProvider = "allOS")
    public void testOperatingSystems(String value) throws Exception {
        String orig = System.getProperty("os.name");
        try {
            if (value != null) {
                System.setProperty("os.name", value);
            } else {
                System.clearProperty("os.name");
            }
            run();
        } finally {
            System.setProperty("os.name", orig);
        }
    }

    @Test
    private void run() {
        final OperatingSystem os = OperatingSystem.getOperatingSystem();

        if (os == OperatingSystem.WINDOWS7) {
            assertTrue(OperatingSystem.isWindows());
            assertTrue(OperatingSystem.isWindows7());
            assertFalse(OperatingSystem.isVista());
            assertFalse(OperatingSystem.isWindows2008());
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isUnix());
        } else if (os == OperatingSystem.WINDOWS_VISTA) {
            assertTrue(OperatingSystem.isWindows());
            assertFalse(OperatingSystem.isWindows7());
            assertTrue(OperatingSystem.isVista());
            assertFalse(OperatingSystem.isWindows2008());
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isUnix());
        } else if (os == OperatingSystem.WINDOWS_SERVER_2008) {
            assertTrue(OperatingSystem.isWindows());
            assertFalse(OperatingSystem.isWindows7());
            assertFalse(OperatingSystem.isVista());
            assertTrue(OperatingSystem.isWindows2008());
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isUnix());
        } else if (os == OperatingSystem.WINDOWS) {
            assertTrue(OperatingSystem.isWindows());
            assertFalse(OperatingSystem.isWindows7());
            assertFalse(OperatingSystem.isVista());
            assertFalse(OperatingSystem.isWindows2008());
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isUnix());
        } else if (os == OperatingSystem.SOLARIS
                || os == OperatingSystem.LINUX
                || os == OperatingSystem.HPUX
                || os == OperatingSystem.FREEBSD
                || os == OperatingSystem.AIX) {
            assertNotWindows();
            assertFalse(OperatingSystem.isMacOS());
            assertTrue(OperatingSystem.isUnix());
        } else if (os == OperatingSystem.MACOSX) {
            assertNotWindows();
            assertTrue(OperatingSystem.isMacOS());
            assertTrue(OperatingSystem.isUnix());
        } else {
            assertNotWindows();
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isUnix());
            assertTrue(OperatingSystem.isUnknown());
        }
    }

    private void assertNotWindows() {
        assertFalse(OperatingSystem.isWindows());
        assertFalse(OperatingSystem.isWindows7());
        assertFalse(OperatingSystem.isVista());
        assertFalse(OperatingSystem.isWindows2008());
    }
}
