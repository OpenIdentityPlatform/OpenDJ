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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.tools;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A set of test cases for the ManageAccount tool.
 */
public class ManageAccountTestCase
       extends ToolsTestCase
{
  /**
   * Ensures that the Directory Server is running before starting any of the
   * tests.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Retrieves the names of all of all the subcommands available for use with
   * the manage-account tool.
   *
   * @return  The names of all of the subcommands available for use with the
   *          manage-account tool.
   */
  @DataProvider(name = "allSubCommands")
  public Object[][] getAllSubCommands()
  {
    return new Object[][]
    {
      { "get-all" },
      { "get-password-policy-dn" },
      { "get-account-is-disabled" },
      { "set-account-is-disabled" },
      { "clear-account-is-disabled" },
      { "get-account-expiration-time" },
      { "set-account-expiration-time" },
      { "clear-account-expiration-time" },
      { "get-seconds-until-account-expiration" },
      { "get-password-changed-time" },
      { "set-password-changed-time" },
      { "clear-password-changed-time" },
      { "get-password-expiration-warned-time" },
      { "set-password-expiration-warned-time" },
      { "clear-password-expiration-warned-time" },
      { "get-seconds-until-password-expiration" },
      { "get-seconds-until-password-expiration-warning" },
      { "get-authentication-failure-times" },
      { "add-authentication-failure-time" },
      { "set-authentication-failure-times" },
      { "clear-authentication-failure-times" },
      { "get-seconds-until-authentication-failure-unlock" },
      { "get-remaining-authentication-failure-count" },
      { "get-last-login-time" },
      { "set-last-login-time" },
      { "clear-last-login-time" },
      { "get-seconds-until-idle-lockout" },
      { "get-password-is-reset" },
      { "set-password-is-reset" },
      { "clear-password-is-reset" },
      { "get-seconds-until-password-reset-lockout" },
      { "get-grace-login-use-times" },
      { "add-grace-login-use-time" },
      { "set-grace-login-use-times" },
      { "clear-grace-login-use-times" },
      { "get-remaining-grace-login-count" },
      { "get-password-changed-by-required-time" },
      { "set-password-changed-by-required-time" },
      { "clear-password-changed-by-required-time" },
      { "get-seconds-until-required-change-time" },
      { "get-password-history" },
      { "clear-password-history" }
    };
  }



  /**
   * Retrieves the names of all of the subcommands used for performing "get"
   * operations.
   *
   * @return  The names of all of the subcommands used for performing "get"
   *          operations.
   */
  @DataProvider(name = "getSubCommands")
  public Object[][] getGetSubCommands()
  {
    return new Object[][]
    {
      { "get-all" },
      { "get-password-policy-dn" },
      { "get-account-is-disabled" },
      { "get-account-expiration-time" },
      { "get-seconds-until-account-expiration" },
      { "get-password-changed-time" },
      { "get-password-expiration-warned-time" },
      { "get-seconds-until-password-expiration" },
      { "get-seconds-until-password-expiration-warning" },
      { "get-authentication-failure-times" },
      { "get-seconds-until-authentication-failure-unlock" },
      { "get-remaining-authentication-failure-count" },
      { "get-last-login-time" },
      { "get-seconds-until-idle-lockout" },
      { "get-password-is-reset" },
      { "get-seconds-until-password-reset-lockout" },
      { "get-grace-login-use-times" },
      { "get-remaining-grace-login-count" },
      { "get-password-changed-by-required-time" },
      { "get-seconds-until-required-change-time" },
      { "get-password-history" }
    };
  }



  /**
   * Retrieves the names of the subcommands that may be used to set a Boolean
   * value in the user's password policy state.
   *
   * @return  The names of all of the subcommands that may be used to set a
   *          Boolean value in the user's password policy state.
   */
  @DataProvider(name = "setBooleanSubCommands")
  public Object[][] getSetBooleanSubCommands()
  {
    return new Object[][]
    {
      { "set-account-is-disabled" },
      { "set-password-is-reset" },
    };
  }



  /**
   * Retrieves the names of the subcommands that may be used to set time value
   * in the user's password policy state.  This will also include the
   * subcommands that may be used to add a value to a multivalued property.
   *
   * @return  The names of all of the subcommands that may be used to set a
   *          time value in the user's password policy state.
   */
  @DataProvider(name = "setTimeSubCommands")
  public Object[][] getSetTimeSubCommands()
  {
    return new Object[][]
    {
      { "set-account-expiration-time" },
      { "set-password-changed-time" },
      { "set-password-expiration-warned-time" },
      { "set-authentication-failure-times" },
      { "add-authentication-failure-time" },
      { "set-last-login-time" },
      { "set-grace-login-use-times" },
      { "add-grace-login-use-time" },
      { "set-password-changed-by-required-time" },
    };
  }



  /**
   * Retrieves the names of all of all the subcommands that may be used to clear
   * some part of the password policy state.
   *
   * @return  The names of all of the subcommands that may be used to clear some
   *          part of the password policy state.
   */
  @DataProvider(name = "clearSubCommands")
  public Object[][] clearAllSubCommands()
  {
    return new Object[][]
    {
      { "clear-account-is-disabled" },
      { "clear-account-expiration-time" },
      { "clear-password-changed-time" },
      { "clear-password-expiration-warned-time" },
      { "clear-authentication-failure-times" },
      { "clear-last-login-time" },
      { "clear-password-is-reset" },
      { "clear-grace-login-use-times" },
      { "clear-password-changed-by-required-time" },
      { "clear-password-history" }
    };
  }

  private int manageAccountMain(String... args)
  {
    return ManageAccount.main(args, false, null, System.err);
  }

  /**
   * Tests the various sets of arguments that may be used to get usage
   * information when no subcommand is given.
   */
  @Test
  public void testHelpNoSubCommand()
  {
    assertEquals(manageAccountMain("-H"), 0);
    assertEquals(manageAccountMain("--help"), 0);
    assertEquals(manageAccountMain("-?"), 0);
  }



  /**
   * Tests the various sets of arguments that may be used to get usage
   * information when a subcommand is given.
   *
   * @param  subCommand  The subcommand to use in the test.
   */
  @Test(dataProvider="allSubCommands")
  public void testHelpWithSubCommand(String subCommand)
  {
    String[] args =
    {
      subCommand,
      "--help"
    };

    assertEquals(manageAccountMain(args), 0);
  }

  /**
   * Tests the manage-account tool without any subcommand.
   */
  @Test
  public void testNoSubCommand()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(ManageAccount.main(args, false, null, null) == 0);
  }



  /**
   * Tests the manage-account tool with an invalid subcommand.
   */
  @Test
  public void testInvalidSubCommand()
  {
    String[] args =
    {
      "invalid-subcommand",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(ManageAccount.main(args, false, null, null) == 0);
  }



  /**
   * Tests an attempt to use the manage-account tool as an anonymous user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAnonymousUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      "get-all",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "",
      "-w", "",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(manageAccountMain(args) == 0);
  }



  /**
   * Tests an attempt to use the manage-account tool as an unprivileged user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testUnprivilegedUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      "get-all",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(manageAccountMain(args) == 0);
  }



  /**
   * Tests the ability to use the manage-account tool when using SASL
   * authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testUsingSASL()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      "get-all",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertEquals(manageAccountMain(args), 0);
  }



  /**
   * Tests to ensure that the various "get" subcommands work without throwing
   * exceptions or returning unexpected results.
   *
   * @param  subCommand  The name of the "get" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="getSubCommands")
  public void testGetSubCommands(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
    };

    assertEquals(manageAccountMain(args), 0);
  }



  /**
   * Tests to ensure that the various "get" subcommands fail when provided with
   * a value.
   *
   * @param  subCommand  The name of the "get" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="getSubCommands")
  public void testGetSubCommandsWithValue(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "not-appropriate-for-this-subcommand"
    };

    assertFalse(manageAccountMain(args) == 0);
  }



  /**
   * Tests to ensure that the various "set" subcommands that take Boolean
   * arguments work properly when given a value of "true".
   *
   * @param  subCommand  The name of the "set" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="setBooleanSubCommands")
  public void testSetBooleanSubCommandsTrue(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "true"
    };

    assertEquals(manageAccountMain(args), 0);
  }



  /**
   * Tests to ensure that the various "set" subcommands that take Boolean
   * arguments work properly when given a value of "false".
   *
   * @param  subCommand  The name of the "set" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="setBooleanSubCommands")
  public void testSetBooleanSubCommandsFalse(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "false"
    };

    assertEquals(manageAccountMain(args), 0);
  }



  /**
   * Tests to ensure that the various "set" subcommands that take Boolean
   * arguments work properly when given a non-Boolean value.
   *
   * @param  subCommand  The name of the "set" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="setBooleanSubCommands")
  public void testSetBooleanSubCommandsNonBooleanValue(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "nonboolean"
    };

    assertFalse(manageAccountMain(args) == 0);
  }



  /**
   * Tests to ensure that the various "set" subcommands that take timestamp
   * arguments work properly when used without any value.
   *
   * @param  subCommand  The name of the "set" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="setTimeSubCommands")
  public void testSetTimeSubCommandsNoValue(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertEquals(manageAccountMain(args), 0);
  }



  /**
   * Tests to ensure that the various "set" subcommands that take timestamp
   * arguments work properly when used with a value equal to the current time.
   *
   * @param  subCommand  The name of the "set" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="setTimeSubCommands")
  public void testSetTimeSubCommandsCurrentTime(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", GeneralizedTimeSyntax.format(System.currentTimeMillis())
    };

    assertEquals(manageAccountMain(args), 0);
  }



  /**
   * Tests to ensure that the various "set" subcommands that take timestamp
   * arguments work properly when used with an invalid value.
   *
   * @param  subCommand  The name of the "set" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="setTimeSubCommands")
  public void testSetTimeSubCommandsInvalidTime(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "invalid"
    };

    assertFalse(manageAccountMain(args) == 0);
  }



  /**
   * Tests to ensure that the various "clear" subcommands work without throwing
   * exceptions or returning unexpected results.
   *
   * @param  subCommand  The name of the "clear" subcommand to invoke.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="clearSubCommands")
  public void testClearSubCommands(String subCommand)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password"
    );

    String[] args =
    {
      subCommand,
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
    };

    assertEquals(manageAccountMain(args), 0);
  }
}
