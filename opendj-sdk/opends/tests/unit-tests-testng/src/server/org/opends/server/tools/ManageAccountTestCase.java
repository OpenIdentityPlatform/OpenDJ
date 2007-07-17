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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.schema.GeneralizedTimeSyntax;

import static org.testng.Assert.*;



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
  public void startServer()
         throws Exception
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
      new Object[] { "get-all" },
      new Object[] { "get-password-policy-dn" },
      new Object[] { "get-account-is-disabled" },
      new Object[] { "set-account-is-disabled" },
      new Object[] { "clear-account-is-disabled" },
      new Object[] { "get-account-expiration-time" },
      new Object[] { "set-account-expiration-time" },
      new Object[] { "clear-account-expiration-time" },
      new Object[] { "get-seconds-until-account-expiration" },
      new Object[] { "get-password-changed-time" },
      new Object[] { "set-password-changed-time" },
      new Object[] { "clear-password-changed-time" },
      new Object[] { "get-password-expiration-warned-time" },
      new Object[] { "set-password-expiration-warned-time" },
      new Object[] { "clear-password-expiration-warned-time" },
      new Object[] { "get-seconds-until-password-expiration" },
      new Object[] { "get-seconds-until-password-expiration-warning" },
      new Object[] { "get-authentication-failure-times" },
      new Object[] { "add-authentication-failure-time" },
      new Object[] { "set-authentication-failure-times" },
      new Object[] { "clear-authentication-failure-times" },
      new Object[] { "get-seconds-until-authentication-failure-unlock" },
      new Object[] { "get-remaining-authentication-failure-count" },
      new Object[] { "get-last-login-time" },
      new Object[] { "set-last-login-time" },
      new Object[] { "clear-last-login-time" },
      new Object[] { "get-seconds-until-idle-lockout" },
      new Object[] { "get-password-is-reset" },
      new Object[] { "set-password-is-reset" },
      new Object[] { "clear-password-is-reset" },
      new Object[] { "get-seconds-until-password-reset-lockout" },
      new Object[] { "get-grace-login-use-times" },
      new Object[] { "add-grace-login-use-time" },
      new Object[] { "set-grace-login-use-times" },
      new Object[] { "clear-grace-login-use-times" },
      new Object[] { "get-remaining-grace-login-count" },
      new Object[] { "get-password-changed-by-required-time" },
      new Object[] { "set-password-changed-by-required-time" },
      new Object[] { "clear-password-changed-by-required-time" },
      new Object[] { "get-seconds-until-required-change-time" },
      new Object[] { "get-password-history" },
      new Object[] { "clear-password-history" }
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
      new Object[] { "get-all" },
      new Object[] { "get-password-policy-dn" },
      new Object[] { "get-account-is-disabled" },
      new Object[] { "get-account-expiration-time" },
      new Object[] { "get-seconds-until-account-expiration" },
      new Object[] { "get-password-changed-time" },
      new Object[] { "get-password-expiration-warned-time" },
      new Object[] { "get-seconds-until-password-expiration" },
      new Object[] { "get-seconds-until-password-expiration-warning" },
      new Object[] { "get-authentication-failure-times" },
      new Object[] { "get-seconds-until-authentication-failure-unlock" },
      new Object[] { "get-remaining-authentication-failure-count" },
      new Object[] { "get-last-login-time" },
      new Object[] { "get-seconds-until-idle-lockout" },
      new Object[] { "get-password-is-reset" },
      new Object[] { "get-seconds-until-password-reset-lockout" },
      new Object[] { "get-grace-login-use-times" },
      new Object[] { "get-remaining-grace-login-count" },
      new Object[] { "get-password-changed-by-required-time" },
      new Object[] { "get-seconds-until-required-change-time" },
      new Object[] { "get-password-history" }
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
      new Object[] { "set-account-is-disabled" },
      new Object[] { "set-password-is-reset" },
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
      new Object[] { "set-account-expiration-time" },
      new Object[] { "set-password-changed-time" },
      new Object[] { "set-password-expiration-warned-time" },
      new Object[] { "set-authentication-failure-times" },
      new Object[] { "add-authentication-failure-time" },
      new Object[] { "set-last-login-time" },
      new Object[] { "set-grace-login-use-times" },
      new Object[] { "add-grace-login-use-time" },
      new Object[] { "set-password-changed-by-required-time" },
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
      new Object[] { "clear-account-is-disabled" },
      new Object[] { "clear-account-expiration-time" },
      new Object[] { "clear-password-changed-time" },
      new Object[] { "clear-password-expiration-warned-time" },
      new Object[] { "clear-authentication-failure-times" },
      new Object[] { "clear-last-login-time" },
      new Object[] { "clear-password-is-reset" },
      new Object[] { "clear-grace-login-use-times" },
      new Object[] { "clear-password-changed-by-required-time" },
      new Object[] { "clear-password-history" }
    };
  }



  /**
   * Tests the various sets of arguments that may be used to get usage
   * information when no subcommand is given.
   */
  @Test()
  public void testHelpNoSubCommand()
  {
    assertEquals(ManageAccount.main(new String[] { "-H" }, null, System.err),
                 0);
    assertEquals(ManageAccount.main(new String[] { "--help" }, null,
                                    System.err),
                 0);
    assertEquals(ManageAccount.main(new String[] { "-?" }, null, System.err),
                 0);
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

    assertEquals(ManageAccount.main(args, null, System.err), 0);
  }



  /**
   * Tests the manage-account tool without any subcommand.
   */
  @Test()
  public void testNoSubCommand()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(ManageAccount.main(args, null, null) == 0);
  }



  /**
   * Tests the manage-account tool with an invalid subcommand.
   */
  @Test()
  public void testInvalidSubCommand()
  {
    String[] args =
    {
      "invalid-subcommand",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(ManageAccount.main(args, null, null) == 0);
  }



  /**
   * Tests an attempt to use the manage-account tool as an anonymous user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "",
      "-w", "",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(ManageAccount.main(args, null, System.err) == 0);
  }



  /**
   * Tests an attempt to use the manage-account tool as an unprivileged user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertFalse(ManageAccount.main(args, null, System.err) == 0);
  }



  /**
   * Tests the ability to use the manage-account tool when using SSL.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testUsingSSL()
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
  }



  /**
   * Tests the ability to use the manage-account tool when using StartTLS.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testUsingStartTLS()
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
  }



  /**
   * Tests the ability to use the manage-account tool when using SASL
   * authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "not-appropriate-for-this-subcommand"
    };

    assertFalse(ManageAccount.main(args, null, System.err) == 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "true"
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "false"
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "nonboolean"
    };

    assertFalse(ManageAccount.main(args, null, System.err) == 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test"
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", GeneralizedTimeSyntax.format(System.currentTimeMillis())
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
      "-O", "invalid"
    };

    assertFalse(ManageAccount.main(args, null, System.err) == 0);
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
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "uid=test.user,o=test",
    };

    assertEquals(ManageAccount.main(args, null, System.err), 0);
  }
}

