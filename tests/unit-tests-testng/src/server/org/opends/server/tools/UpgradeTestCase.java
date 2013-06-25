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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.upgrade.UpgradeCli;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.testng.annotations.Test;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.OPTION_LONG_FORCE_UPGRADE;
import static org.testng.Assert.*;

/**
 * A set of test cases for the Upgrade tool.
 */
public class UpgradeTestCase extends ToolsTestCase
{
  private final static String configFilePath = DirectoryServer
      .getInstanceRoot()
      + File.separator + "config" + File.separator + "config.ldif";

  /**
   * Sets the args for the upgrade tools. The configFile parameter is
   * configured by default.<pre>
   * usage : {@code}setArgs("--force", "--no-prompt") {@code}
   * corresponds to command line : ./upgrade --force -n</pre>
   *
   * @param args
   *          The argument you want for testing.
   * @return An array of string containing the args.
   */
  private String[] setArgs(String... args)
  {
    final List<String> argsList = new LinkedList<String>();
    argsList.add("--configFile");
    argsList.add(configFilePath);
    if (args != null)
    {
      for (final String argument : args)
      {
        argsList.add(argument);
      }
    }
    final String[] mainArgs = new String[argsList.size()];
    argsList.toArray(mainArgs);

    return mainArgs;
  }

  /**
   * Returns {@code true} if the output contain the expected message.
   *
   * @param output
   *          The upgrade's output.
   * @param expectedMessage
   *          The expected message.
   * @return {@code true} if the output contains the expected message.
   */
  private boolean isOutputContainsExpectedMessage(final String output,
      final Message expectedMessage)
  {
    return (output.replaceAll("\n", " ").replaceAll("%s", " ").indexOf(
        expectedMessage.toString().replaceAll("\n", " ")
        .replaceAll("%s", " ")) != -1);
  }

  /**
   * Tests display help information.
   */
  @Test()
  public void testUpgradeToolDisplaysHelpUsage()
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);

    try
    {
      // The 'main' should exit with success code.
      assertEquals(UpgradeCli.main(setArgs("--help"), true, ps, ps), 0);

      assertTrue(isOutputContainsExpectedMessage(baos.toString(),
          INFO_UPGRADE_DESCRIPTION_CLI.get()));
    }
    finally
    {
      ps.close();
    }
  }

  /**
   * Tests display help information.
   */
  @Test()
  public void testUpgradeToolDisplaysHelpUsage2()
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);

    try
    {
      // The 'main' should exit with success code.
      assertEquals(UpgradeCli.main(setArgs("-H"), true, ps, ps), 0);

      assertTrue(isOutputContainsExpectedMessage(baos.toString(),
          INFO_UPGRADE_DESCRIPTION_CLI.get()));
    }
    finally
    {
      ps.close();
    }
  }

  /**
   * Tests display help information.
   */
  @Test()
  public void testUpgradeToolDisplaysHelpUsage3()
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);

    try
    {
      // The 'main' should exit with success code.
      assertEquals(UpgradeCli.main(setArgs("-?"), true, ps, ps), 0);

      assertTrue(isOutputContainsExpectedMessage(baos.toString(),
          INFO_UPGRADE_DESCRIPTION_CLI.get()));
    }
    finally
    {
      ps.close();
    }
  }

  /**
   * Tests the upgrade tool with an invalid sub-command.
   */
  @Test()
  public void testUpgradeToolDoesntAllowWrongSubcommand()
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);

    try
    {
      // The 'main' should exit with an error code.
      assertEquals(UpgradeCli.main(setArgs("-- wrong"), true, ps, ps), 1);

      assertTrue(isOutputContainsExpectedMessage(baos.toString(),
          ERR_ERROR_PARSING_ARGS.get("")));
    }
    finally
    {
      ps.close();
    }
  }

  /**
   * Tests the upgrade tool with an invalid sub-command.
   */
  @Test()
  public void testUpgradeToolDoesntAllowWrongSubcommand2()
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);

    try
    {
      // The 'main' should exit with an error code.
      assertEquals(UpgradeCli.main(setArgs("--wrong"), true, ps, ps), 1);

      assertTrue(isOutputContainsExpectedMessage(baos.toString(),
          ERR_ERROR_PARSING_ARGS.get("")));
    }
    finally
    {
      ps.close();
    }
  }

  /**
   * The upgrade tool disallows the force sub-command used with 'interactive
   * mode'.
   */
  @Test()
  public void testUpgradeToolDoesntAllowInteractiveAndForce()
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);
    try
    {
      // The 'main' should exit with an error code.
      assertEquals(UpgradeCli.main(setArgs("--force"), true, ps, ps), 1);

      // Because interactive mode is not compatible with force upgrade mode.
      final Message message =
          ERR_UPGRADE_INCOMPATIBLE_ARGS.get(OPTION_LONG_FORCE_UPGRADE,
              "interactive mode");

      assertTrue(isOutputContainsExpectedMessage(baos.toString(), message));
    }
    finally
    {
      ps.close();
    }
  }

  /**
   * Upgrade tool allows use of force and no-prompt sub-commands.
   *
   * @throws IOException
   * @throws DirectoryException
   * @throws ConfigException
   * @throws InitializationException
   */
  @Test()
  public void testUpgradeToolAllowsNonInteractiveAndForce()
      throws InitializationException, ConfigException, DirectoryException,
      IOException
  {
    TestCaseUtils.startServer();

    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);
    try
    {
      // The 'main' should exit with success code.
      assertEquals(UpgradeCli.main(setArgs("--force", "--no-prompt"), true, ps,
          ps), 0);

      // The sub-commands have been checked ok but upgrade must exist on
      // version's verification.
      assertTrue(isOutputContainsExpectedMessage(baos.toString(),
          ERR_UPGRADE_VERSION_UP_TO_DATE.get("")));

    }
    finally
    {
      ps.close();
      TestCaseUtils
          .shutdownServer("testUpgradeToolAllowsNonInteractiveAndForce");
    }
  }
}
