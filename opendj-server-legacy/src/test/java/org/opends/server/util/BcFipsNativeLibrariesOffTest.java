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
package org.opends.server.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/**
 * Pins the flag that keeps bc-fips from seeding its DRBG from the CPU's RDSEED instruction in the
 * test JVMs and in the JVMs they start from the built package, see issue #1161.
 */
@SuppressWarnings("javadoc")
public class BcFipsNativeLibrariesOffTest extends DirectoryServerTestCase
{
  private static final String FLAG = "-Dorg.bouncycastle.native.cpu_variant=java";

  @Test
  public void theTestJvmRunsWithoutTheNativeLibraries()
  {
    assertEquals(System.getProperty("org.bouncycastle.native.cpu_variant"), "java",
        "the test JVM lost the bc-fips cpu_variant flag, see #1161");
  }

  /** setup, and start-ds through ServerController, inherit the environment of the failsafe fork. */
  @Test
  public void theJvmsStartedFromThePackageInheritTheFlag()
  {
    final String toolOptions = System.getenv("JAVA_TOOL_OPTIONS");
    assertNotNull(toolOptions, "the failsafe fork lost JAVA_TOOL_OPTIONS, see #1161");
    assertTrue(Arrays.asList(toolOptions.trim().split("\\s+")).contains(FLAG),
        "JAVA_TOOL_OPTIONS of the failsafe fork lost the bc-fips cpu_variant flag, see #1161: " + toolOptions);
  }
}
