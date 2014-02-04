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
package org.opends.server.types;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * This class tests the model functionality.
 */
public class OperatingSystemTestCase extends AbstractTypesTestCase {

    @Test()
    public void testGetOperatingSystem() {
        final OperatingSystem os = OperatingSystem.getOperatingSystem();
        if (os.toString().toLowerCase().indexOf("windows") != -1) {
            assertTrue(OperatingSystem.isWindows());
            if (os.toString().toLowerCase().indexOf("windows 7") != -1) {
                assertTrue(OperatingSystem.isWindows7());
                assertFalse(OperatingSystem.isVista());
                assertFalse(OperatingSystem.isWindows2008());
            } else if (os.toString().toLowerCase().indexOf("vista") != -1) {
                assertTrue(OperatingSystem.isVista());
                assertFalse(OperatingSystem.isWindows7());
                assertFalse(OperatingSystem.isWindows2008());
            } else if (os.toString().toLowerCase().indexOf("server 2008") != -1) {
                assertTrue(OperatingSystem.isWindows2008());
                assertFalse(OperatingSystem.isWindows7());
                assertFalse(OperatingSystem.isVista());
            }
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isUnix());
            assertFalse(OperatingSystem.isUnixBased());

        } else if (os.toString().toLowerCase().indexOf("solaris") != -1
                || os.toString().toLowerCase().indexOf("linux") != -1
                || os.toString().toLowerCase().indexOf("hp-ux") != -1
                || os.toString().toLowerCase().indexOf("hpux") != -1
                || os.toString().toLowerCase().indexOf("aix") != -1
                || os.toString().toLowerCase().indexOf("freebsd") != -1) {
            assertTrue(OperatingSystem.isUnix());
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isWindows());
            assertTrue(OperatingSystem.isUnixBased());
        } else if (os.toString().toLowerCase().indexOf("macos") != -1) {
            assertTrue(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isUnix());
            assertTrue(OperatingSystem.isUnixBased());
        }

    }
}
