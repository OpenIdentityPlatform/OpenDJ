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
package org.opends.server.tools;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.opends.quicksetup.Constants;
import org.opends.quicksetup.TempLogFile;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/**
 * Tests when the CLI setup asks for its log file.
 * <p>
 * Nothing removes that file unless the install succeeds, so the roads which install nothing -
 * a usage request, a usage error, a server which is configured already, a refused licence, a
 * cancel at the prompt - must not ask for one: asking creates it, and the instance
 * {@code logs/} directory with it (issue #1030).
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "tools" }, sequential = true)
public class InstallDSTest extends DirectoryServerTestCase
{
  /** {@code InstallReturnCode.SUCCESSFUL_NOP}: the usage was displayed and nothing was done. */
  private static final int SUCCESSFUL_NOP = 0;
  /** {@code InstallReturnCode.ERROR_USER_DATA}: the arguments could not be parsed. */
  private static final int ERROR_USER_DATA = 2;

  /** {@code setup --help} displays the usage and returns before anything can fail. */
  @Test
  public void testAUsageRequestAsksForNoLog() throws Exception
  {
    assertAsksForNoLog(SUCCESSFUL_NOP, "--help");
  }

  /** An argument the parser does not know is reported, and no install is attempted. */
  @Test
  public void testAUsageErrorAsksForNoLog() throws Exception
  {
    assertAsksForNoLog(ERROR_USER_DATA, "--no-such-option");
  }

  /**
   * Runs the CLI setup with a supplier which counts the asks and hands out nothing, so that a
   * road which asks for the log is red here rather than a file left behind on a real run.
   */
  private static void assertAsksForNoLog(final int expectedReturnCode, final String... args)
  {
    final AtomicInteger asked = new AtomicInteger();
    final Supplier<TempLogFile> countingSupplier = () -> {
      asked.incrementAndGet();
      return null;
    };
    // mainCLI() sets this property for the run; put back what the rest of the JVM had.
    final String cliProperty = System.getProperty(Constants.CLI_JAVA_PROPERTY);
    try
    {
      assertEquals(InstallDS.mainCLI(args, null, null, countingSupplier), expectedReturnCode);
      assertEquals(asked.get(), 0, "a run which installs nothing must not create a log");
    }
    finally
    {
      if (cliProperty != null)
      {
        System.setProperty(Constants.CLI_JAVA_PROPERTY, cliProperty);
      }
      else
      {
        System.clearProperty(Constants.CLI_JAVA_PROPERTY);
      }
    }
  }
}
