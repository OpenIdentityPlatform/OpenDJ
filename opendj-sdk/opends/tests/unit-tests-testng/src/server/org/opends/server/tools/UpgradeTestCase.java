/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.tools;

import java.io.IOException;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigException;
import org.opends.server.tools.upgrade.UpgradeCli;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * A set of test cases for the Upgrade tool.
 */
public class UpgradeTestCase extends ToolsTestCase
{
  /**
   * Tests the Upgrade tool with an argument that will simply cause it to
   * display usage information.
   */
  @Test()
  public void testUpgradeToolHelpUsage()
  {
    String[] args = { "--help" };
    assertEquals(UpgradeCli.main(args, true, System.out, System.err), 0);

    args = new String[] { "-H" };
    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);

    args = new String[] { "-?" };
    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
  }

  /**
   * Tests the Upgrade tool with wrong sub-command.
   */
  @Test()
  public void testUpgradeToolWrongSubcommand()
  {
    String[] args = { "-- wrong" };
    assertEquals(UpgradeCli.main(args, true, System.out, System.err), 1);

    args = new String[]{ "--wrong" };
    assertEquals(UpgradeCli.main(args, true, System.out, System.err), 1);
  }

  /**
   * Tests the Upgrade tool with unauthorized usage.
   */
  @Test()
  public void testUpgradeToolUnauthorizedUsage()
  {
    // Interactive mode is not compatible with forceUpgrade mode.
    String[] args = { "--forceUpgrade" };
    assertEquals(UpgradeCli.main(args, true, System.out, System.err), 1);
  }

  /**
   * Tests the Upgrade tool with a running server throws an error.
   */
  @Test()
  public void testUpgradeRequiresServerOffline() throws InitializationException,
      ConfigException, DirectoryException, IOException
  {
    TestCaseUtils.startServer();
    String[] args = { "" };
    assertEquals(UpgradeCli.main(args, true, System.out, System.err), 1);
    TestCaseUtils.shutdownServer("End of upgrade test.");
  }

  /**
   * Tests the Upgrade tool with a running server throws an error.
   */
  @Test()
  public void testUpgradeServerOffline() throws InitializationException,
      ConfigException, DirectoryException, IOException
  {
    String[] args = { "" };
    assertEquals(UpgradeCli.main(args, true, System.out, System.err), 1);
  }
}
