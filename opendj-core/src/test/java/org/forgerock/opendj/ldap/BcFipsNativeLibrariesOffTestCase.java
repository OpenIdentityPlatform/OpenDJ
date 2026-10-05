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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.ldap;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * Pins the test argLine flag that keeps bc-fips from seeding its DRBG from the CPU's RDSEED
 * instruction, see issue #1161. {@link LDAPServer} generates its key pairs with bc-fips, and this
 * module takes the argLine of the root pom as it is: the default one below JDK 17, the
 * jdk17.options one from JDK 17 on.
 */
@SuppressWarnings("javadoc")
public class BcFipsNativeLibrariesOffTestCase extends SdkTestCase {
    @Test
    public void testJvmRunsWithoutTheNativeLibraries() {
        assertEquals(System.getProperty("org.bouncycastle.native.cpu_variant"), "java",
                "the test argLine lost the bc-fips cpu_variant flag, see #1161");
    }
}
