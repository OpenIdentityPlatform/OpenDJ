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
 * Copyright 2014 ForgeRock AS.
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
