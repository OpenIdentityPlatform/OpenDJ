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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the LDAPPasswordModify tool.
 */
public class LDAPPasswordModifyTestCase
       extends ToolsTestCase
{
  // The path to a file containing the current bind password.
  private String currentPasswordFile;

  // The path to a file containing the new password.
  private String newPasswordFile;



  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServerAndCreatePasswordFiles()
         throws Exception
  {
    TestCaseUtils.startServer();

    File pwFile = File.createTempFile("valid-bind-password-", ".txt");
    pwFile.deleteOnExit();
    FileWriter fileWriter = new FileWriter(pwFile);
    fileWriter.write("newPassword" + System.getProperty("line.separator"));
    fileWriter.close();
    newPasswordFile = pwFile.getAbsolutePath();

    pwFile = File.createTempFile("invalid-bind-password-", ".txt");
    pwFile.deleteOnExit();
    fileWriter = new FileWriter(pwFile);
    fileWriter.write("password" + System.getProperty("line.separator"));
    fileWriter.close();
    currentPasswordFile = pwFile.getAbsolutePath();
  }



  /**
   * Retrieves sets of invalid arguments that may not be used to initialize
   * the LDAPModify tool.
   *
   * @return  Sets of invalid arguments that may not be used to initialize the
   *          LDAPModify tool.
   */
  @DataProvider(name = "invalidArgs")
  public Object[][] getInvalidArgumentLists()
  {
    ArrayList<String[]> argLists   = new ArrayList<String[]>();
    ArrayList<String>   reasonList = new ArrayList<String>();

    String[] args = {};
    argLists.add(args);
    reasonList.add("No arguments were provided");

    args = new String[]
    {
      "-h"
    };
    argLists.add(args);
    reasonList.add("No value for '-h' argument");

    args = new String[]
    {
      "-p"
    };
    argLists.add(args);
    reasonList.add("No value for '-p' argument");

    args = new String[]
    {
      "-D"
    };
    argLists.add(args);
    reasonList.add("No value for '-D' argument");

    args = new String[]
    {
      "-w"
    };
    argLists.add(args);
    reasonList.add("No value for '-w' argument");

    args = new String[]
    {
      "-j"
    };
    argLists.add(args);
    reasonList.add("No value for '-j' argument");

    args = new String[]
    {
      "-a"
    };
    argLists.add(args);
    reasonList.add("No value for '-a' argument");

    args = new String[]
    {
      "-n"
    };
    argLists.add(args);
    reasonList.add("No value for '-n' argument");

    args = new String[]
    {
      "-N"
    };
    argLists.add(args);
    reasonList.add("No value for '-N' argument");

    args = new String[]
    {
      "-c"
    };
    argLists.add(args);
    reasonList.add("No value for '-c' argument");

    args = new String[]
    {
      "-C"
    };
    argLists.add(args);
    reasonList.add("No value for '-C' argument");

    args = new String[]
    {
      "-K"
    };
    argLists.add(args);
    reasonList.add("No value for '-K' argument");

    args = new String[]
    {
      "-W"
    };
    argLists.add(args);
    reasonList.add("No value for '-W' argument");

    args = new String[]
    {
      "--keyStorePasswordFile"
    };
    argLists.add(args);
    reasonList.add("No value for '--keyStorePasswordFile' argument");

    args = new String[]
    {
      "-P"
    };
    argLists.add(args);
    reasonList.add("No value for '-P' argument");

    args = new String[]
    {
      "--trustStorePassword"
    };
    argLists.add(args);
    reasonList.add("No value for '--trustStorePassword' argument");

    args = new String[]
    {
      "--trustStorePasswordFile"
    };
    argLists.add(args);
    reasonList.add("No value for '--trustStorePasswordFile' argument");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-j", "no.such.file"
    };
    argLists.add(args);
    reasonList.add("Invalid bind password file path");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-j", currentPasswordFile
    };
    argLists.add(args);
    reasonList.add("Both bind password and bind password file");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-c", "password",
      "-C", currentPasswordFile
    };
    argLists.add(args);
    reasonList.add("Both current password and current password file");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-n", "password",
      "-N", newPasswordFile
    };
    argLists.add(args);
    reasonList.add("Both new password and new password file");

    args = new String[]
    {
      "-Z",
      "-q"
    };
    argLists.add(args);
    reasonList.add("Both SSL and StartTLS");

    args = new String[]
    {
      "-p", "nonnumeric"
    };
    argLists.add(args);
    reasonList.add("Non-numeric port");

    args = new String[]
    {
      "-p", "999999"
    };
    argLists.add(args);
    reasonList.add("Port value out of range");

    args = new String[]
    {
      "-D", "cn=Directory Manager"
    };
    argLists.add(args);
    reasonList.add("Bind Dn without a password or password file");

    args = new String[]
    {
      "-w", "password"
    };
    argLists.add(args);
    reasonList.add("Bind password without a DN");

    args = new String[]
    {
      "-j", currentPasswordFile
    };
    argLists.add(args);
    reasonList.add("Bind password file without a DN");

    args = new String[]
    {
      "-a", "u:test.user"
    };
    argLists.add(args);
    reasonList.add("No bind credentials, with authzID, no current PW");

    args = new String[]
    {
      "-A"
    };
    argLists.add(args);
    reasonList.add("Provide DN for authzID without DN");


    Object[][] returnArray = new Object[argLists.size()][2];
    for (int i=0; i < argLists.size(); i++)
    {
      returnArray[i][0] = argLists.get(i);
      returnArray[i][1] = reasonList.get(i);
    }
    return returnArray;
  }



  /**
   * Tests the LDAPModify tool with sets of invalid arguments.
   *
   * @param  args           The set of arguments to use for the LDAPModify tool.
   * @param  invalidReason  The reason the provided arguments were invalid.
   */
  @Test(dataProvider = "invalidArgs")
  public void testInvalidArguments(String[] args, String invalidReason)
  {
    assertFalse(LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                      null) == 0,
                "Should have been invalid because:  " + invalidReason);
  }



  /**
   * Tests the ability to perform a self change including both the current and
   * new passwords.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSelfChangeCurrentPasswordNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a self change including a new password but no
   * current password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSelfChangeNoCurrentPasswordNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a self change including the current password
   * but no new password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSelfChangeCurrentPasswordNoNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a self change including neither the current
   * nor new passwords.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSelfChangeNoCurrentPasswordNoNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform an authenticated self change including an
   * explicit authorization ID, a current password, and a new password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAuthenticatedSelfExplicitAuthzIDCurrentNew()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "u:test.user",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform an authenticated self change including an
   * implicit authorization ID, a current password, and a new password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAuthenticatedSelfImplicitAuthzIDCurrentNew()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-A",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform an authenticated self change including an
   * implicit authorization ID, an implicit current password, and an explicit
   * new password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAuthenticatedSelfImplicitAuthzIDNoCurrentNew()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-A",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform an unauthenticated self change with a new
   * password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testUnauthenticatedSelfChangeNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform an unauthenticated self change with no new
   * password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testUnauthenticatedSelfChangeNoNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform an administrative reset with a new password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAdminResetNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform an administrative reset with no new password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAdminResetNoNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a password change over SSL with blind trust.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSSLBlindTrust()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-X",
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a password change over SSL with a trust store.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSSLTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-P", trustStorePath,
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a password change using StartTLS with blind
   * trust.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStartTLSBlindTrust()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-X",
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a password change using StartTLS with a trust
   * store.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStartTLSTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a password reset when reading the bind and new
   * passwords from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindAndNewPasswordsFromFile()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", currentPasswordFile,
      "-a", "dn:uid=test.user,o=test",
      "-N", newPasswordFile
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the ability to perform a password change when reading the current and
   * new passwords from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCurrentAndNewPasswordsFromFile()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "u:test.user",
      "-C", currentPasswordFile,
      "-N", newPasswordFile
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests a failure when attempting an administrative reset with an invalid DN
   * in the authorization ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testResetWithInvalidAuthzDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:invalid",
      "-n", "newPassword"
    };

    assertFalse(LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                      null) == 0);
  }



  /**
   * Tests a failure when attempting an administrative reset on a user entry
   * that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testResetOnNonExistentUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:cn=Does Not Exist,o=test",
      "-n", "newPassword"
    };

    assertFalse(LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                      null) == 0);
  }



  /**
   * Tests a failure when attempting an administrative reset on a user entry
   * that has been disabled.  Also include the password policy control in the
   * request.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testResetOnDisabledUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "ds-pwp-account-disabled: true");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword",
      "-J", "pwpolicy:true"
    };

    assertFalse(LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                      null) == 0);
  }



  /**
   * Tests the password modify extended operation in conjunction with a control
   * that is marked critical but that is not supported by the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPNoOpUnsupportedCriticalControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword",
      "-J", "1.2.3.4:true"
    };

    assertFalse(LDAPPasswordModify.mainPasswordModify(args, false, null, null)
                == 0);
  }



  /**
   * Tests the password modify extended operation in conjunction with the LDAP
   * no-op control using the explicit OID for that control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPNoOpExplicitOID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword",
      "-J", OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true"
    };

    // FIXME -- Change this whenever the real LDAP No-Op result code is assigned
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the password modify extended operation in conjunction with the LDAP
   * no-op control using a more user-friendly name instead of an OID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPNoOpImplicitOID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword",
      "-J", "noop:true"
    };

    // FIXME -- Change this whenever the real LDAP No-Op result code is assigned
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the password modify extended operation in conjunction with multiple
   * request controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPMultipleControls()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword",
      "-J", OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true",
      "-J", OID_PASSWORD_POLICY_CONTROL + ":true"
    };

    // FIXME -- Change this whenever the real LDAP No-Op result code is assigned
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the LDAPModify tool with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args = { "--help" };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    args = new String[] { "-H" };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }
}

