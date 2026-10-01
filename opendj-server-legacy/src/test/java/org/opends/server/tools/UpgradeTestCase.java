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
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.upgrade.UpgradeCli;
import org.opends.server.util.Args;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.ArgumentParser.PROPERTY_SCRIPT_NAME;
import static org.opends.messages.ToolMessages.*;
import static org.testng.Assert.*;

/** A set of test cases for the Upgrade tool. */
@SuppressWarnings("javadoc")
public class UpgradeTestCase extends ToolsTestCase
{
  private static final String configFilePath = DirectoryServer.getInstanceRoot()
      + File.separator + "config" + File.separator + "config.ldif";

  /**
   * Sets the args for the upgrade tools. The configFile parameter is configured
   * by default.
   *
   * <pre>
   * usage : {@code}setArgs("--force", "--no-prompt") {@code}
   * corresponds to command line : ./upgrade --force -n
   * </pre>
   *
   * @param args
   *          The argument you want for testing.
   * @return An array of string containing the args.
   */
  private String[] setArgs(String... args)
  {
    return new Args()
        .add("--configFile", configFilePath)
        .addAll(args)
        .toArray();
  }

  /**
   * Returns {@code true} if the output contain the expected message.
   *
   * @param output
   *          The upgrade's output.
   * @param expectedMessage
   *          The expected message.
   */
  private void assertContainsMessage(String output, LocalizableMessage expectedMessage)
  {
    String out = output.replaceAll("\n", " ").replaceAll("%s", " ");
    String expected = expectedMessage.toString().replaceAll("\n", " ").replaceAll("%s", " ");
    Assertions.assertThat(out).contains(expected);
  }

  /** Tests display help information. */
  @Test
  public void testUpgradeToolDisplaysHelpUsage() throws Exception
  {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos))
    {
      // The 'main' should exit with success code.
      assertEquals(UpgradeCli.main(setArgs("--help"), true, ps, ps), 0);
      assertContainsMessage(baos.toString(), INFO_UPGRADE_DESCRIPTION_CLI.get());
    }
  }

  /** Tests display help information. */
  @Test
  public void testUpgradeToolDisplaysHelpUsage2() throws Exception
  {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos))
    {
      // The 'main' should exit with success code.
      assertEquals(UpgradeCli.main(setArgs("-H"), true, ps, ps), 0);
      assertContainsMessage(baos.toString(), INFO_UPGRADE_DESCRIPTION_CLI.get());
    }
  }

  /** Tests display help information. */
  @Test
  public void testUpgradeToolDisplaysHelpUsage3() throws Exception
  {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos))
    {
      // The 'main' should exit with success code.
      assertEquals(UpgradeCli.main(setArgs("-?"), true, ps, ps), 0);
      assertContainsMessage(baos.toString(), INFO_UPGRADE_DESCRIPTION_CLI.get());
    }
  }

  /** The generated reference of the upgrade tool includes its AsciiDoc description (#1128). */
  @Test
  public void testUpgradeToolGenerateDocIncludesItsDescriptionSupplement() throws Exception
  {
    final String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    System.setProperty("org.forgerock.opendj.gendoc", "true");
    System.setProperty(PROPERTY_SCRIPT_NAME, "upgrade");
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos))
    {
      assertEquals(UpgradeCli.main(setArgs("-?"), true, ps, ps), 0);
      Assertions.assertThat(baos.toString())
          .contains("include::./_description-upgrade.adoc[]")
          .doesNotContain("<xinclude:include");
    }
    finally
    {
      System.clearProperty("org.forgerock.opendj.gendoc");
      if (scriptName != null)
      {
        System.setProperty(PROPERTY_SCRIPT_NAME, scriptName);
      }
      else
      {
        System.clearProperty(PROPERTY_SCRIPT_NAME);
      }
    }
  }

  /** Tests the upgrade tool with an invalid sub-command. */
  @Test
  public void testUpgradeToolDoesntAllowWrongSubcommand() throws Exception
  {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos))
    {
      // The 'main' should exit with an error code.
      assertEquals(UpgradeCli.main(setArgs("-- wrong"), true, ps, ps), 1);
      assertContainsMessage(baos.toString(), ERR_ERROR_PARSING_ARGS.get(""));
    }
  }

  /** Tests the upgrade tool with an invalid sub-command. */
  @Test
  public void testUpgradeToolDoesntAllowWrongSubcommand2() throws Exception
  {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos))
    {
      // The 'main' should exit with an error code.
      assertEquals(UpgradeCli.main(setArgs("--wrong"), true, ps, ps), 1);
      assertContainsMessage(baos.toString(), ERR_ERROR_PARSING_ARGS.get(""));
    }
  }

  /** The upgrade tool disallows the force sub-command used with 'interactive mode'. */
  @Test
  public void testUpgradeToolDoesntAllowInteractiveAndForce() throws Exception
  {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos))
    {
      // The 'main' should exit with an error code.
      assertEquals(UpgradeCli.main(setArgs("--force"), true, ps, ps), 1);

      // Because interactive mode is not compatible with force upgrade mode.
      assertContainsMessage(baos.toString(), ERR_UPGRADE_INCOMPATIBLE_ARGS.get(
          OPTION_LONG_FORCE_UPGRADE, "interactive mode"));
    }
  }

  /** Upgrade tool allows use of force and no-prompt sub-commands. */
  @Test
  public void testUpgradeToolAllowsNonInteractiveAndForce() throws Exception
  {
    TestCaseUtils.startServer();

    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final PrintStream ps = new PrintStream(baos);
    try
    {
      // The 'main' should exit with success code.
      int rc = UpgradeCli.main(setArgs("--force", "--no-prompt"), true, ps, ps);
      assertEquals(rc, 0);

      // The sub-commands have been checked ok but upgrade must exist on
      // version's verification.
      assertContainsMessage(baos.toString(), ERR_UPGRADE_VERSION_UP_TO_DATE.get(""));
    }
    finally
    {
      StaticUtils.close(ps, baos);
    }
  }
}
