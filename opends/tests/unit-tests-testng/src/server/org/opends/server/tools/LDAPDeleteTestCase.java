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
import org.opends.server.types.OperatingSystem;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Base64;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the LDAPDelete tool.
 */
public class LDAPDeleteTestCase
       extends ToolsTestCase
{
  // The path to a file containing an invalid bind password.
  private String invalidPasswordFile;

  // The path to a file containing a valid bind password.
  private String validPasswordFile;



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
    fileWriter.write("password" + System.getProperty("line.separator"));
    fileWriter.close();
    validPasswordFile = pwFile.getAbsolutePath();

    pwFile = File.createTempFile("invalid-bind-password-", ".txt");
    pwFile.deleteOnExit();
    fileWriter = new FileWriter(pwFile);
    fileWriter.write("wrongPassword" + System.getProperty("line.separator"));
    fileWriter.close();
    invalidPasswordFile = pwFile.getAbsolutePath();
  }



  /**
   * Retrieves sets of invalid arguments that may not be used to initialize
   * the LDAPDelete tool.
   *
   * @return  Sets of invalid arguments that may not be used to initialize the
   *          LDAPDelete tool.
   */
  @DataProvider(name = "invalidArgs")
  public Object[][] getInvalidArgumentLists()
  {
    ArrayList<String[]> argLists = new ArrayList<String[]>();

    String[] args = {}; // No arguments
    args = new String[] // No value for "-D" argument.
    {
      "-D",
    };
    argLists.add(args);

    args = new String[] // No value for "-w" argument.
    {
      "-w",
    };
    argLists.add(args);

    args = new String[] // No value for "-j" argument.
    {
      "-j",
    };
    argLists.add(args);

    args = new String[] // No value for "-i" argument.
    {
      "-i",
    };
    argLists.add(args);

    args = new String[] // No value for "-K" argument.
    {
      "-K",
    };
    argLists.add(args);

    args = new String[] // No value for "-P" argument.
    {
      "-P",
    };
    argLists.add(args);

    args = new String[] // No value for "-W" argument.
    {
      "-W",
    };
    argLists.add(args);

    args = new String[] // No value for "-h" argument.
    {
      "-h",
    };
    argLists.add(args);

    args = new String[] // No value for "-p" argument.
    {
      "-p",
    };
    argLists.add(args);

    args = new String[] // No value for "-V" argument.
    {
      "-V",
    };
    argLists.add(args);

    args = new String[] // No value for "-f" argument.
    {
      "-f",
    };
    argLists.add(args);

    args = new String[] // No value for "-J" argument.
    {
      "-J",
    };
    argLists.add(args);

    args = new String[] // No value for "-o" argument.
    {
      "-o",
    };
    argLists.add(args);

    args = new String[] // Invalid short argument
    {
      "-I"
    };
    argLists.add(args);

    args = new String[] // Invalid long argument
    {
      "--invalidLongArgument"
    };
    argLists.add(args);

    args = new String[] // Invalid bind password file path
    {
      "-D", "cn=Directory Manager",
      "-j", "no.such.file",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // Both bind password and password file
    {
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-j", validPasswordFile,
      "o=test"
    };
    argLists.add(args);

    args = new String[] // Non-numeric LDAP version.
    {
      "-V", "nonnumeric",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // Invalid LDAP version.
    {
      "-V", "1",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // Invalid DN file path.
    {
      "-f", "no.such.file",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // Invalid control criticality
    {
      "-J", "1.2.3.4:invalidcriticality",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // Non-numeric port
    {
      "-p", "nonnumeric",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // Port value out of range.
    {
      "-p", "999999",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // SASL external without SSL or StartTLS
    {
      "-r",
      "-K", "key.store.file",
      "o=test"
    };
    argLists.add(args);

    args = new String[] // SASL external without keystore file
    {
      "-Z",
      "-r",
      "o=test"
    };
    argLists.add(args);


    Object[][] returnArray = new Object[argLists.size()][1];
    for (int i=0; i < argLists.size(); i++)
    {
      returnArray[i][0] = argLists.get(i);
    }
    return returnArray;
  }



  /**
   * Tests the LDAPDelete tool with sets of invalid arguments.
   *
   * @param  args  The set of arguments to use for the LDAPDelete tool.
   */
  @Test(dataProvider = "invalidArgs")
  public void testInvalidArguments(String[] args)
  {
    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);
  }



  /**
   * Tests a simple LDAPv2 delete.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testSimpleLDAPv2Delete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "2",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAPv3 delete.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testSimpleLDAPv3Delete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests the LDAPDelete tool using SSL with blind trust.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteSSLBlindTrust()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests the LDAPDelete tool using SSL with a trust store.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteSSLTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests the LDAPDelete tool using StartTLS with blind trust.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteStartTLSBlindTrust()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests the LDAPDelete tool using StartTLS with a trust store.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteStartTLSTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests the LDAPDelete tool using SASL PLAIN authentication.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeletePLAIN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests deleting an entry that doesn't exist.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteNonExistent()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "cn=Does Not Exist,o=test"
    };

    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);
  }



  /**
   * Tests deleting with a malformed DN.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteMalformedDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "malformed"
    };

    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);
  }



  /**
   * Tests deleting an entry with one or more children but not including the
   * subtree delete control.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteParentNoSubtreeDeleteControl()
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
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };

    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);
  }



  /**
   * Tests the LDAPDelete tool reading a valid bind password from a file.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteValidPasswordFile()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", validPasswordFile,
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests the LDAPDelete tool reading an invalid bind password from a file.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteInvalidPasswordFile()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", invalidPasswordFile,
      "o=test"
    };

    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);
  }



  /**
   * Tests the LDAPDelete tool reading the bind password from a nonexistent
   * file.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteNonExistentPasswordFile()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", "does.not.exist",
      "o=test"
    };

    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);
  }



  /**
   * Tests the LDAPDelete tool reading the DNs to delete from a file.  Some of
   * the deletes will succeed and some will fail.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteDNsFromFile()
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
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String path = TestCaseUtils.createTempFile(
         "o=test",
         "uid=test.user,o=test",
         "malformed",
         "o=suffix does not exist",
         "uid=entry does not exist,o=test",
         "o=test");


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", validPasswordFile,
      "-c",
      "-f", path
    };

    LDAPDelete.mainDelete(args, false, null, null);
  }



  /**
   * Tests a subtree delete operation.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testSubtreeDelete()
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
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-x",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple delete using the client-side no-op option.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteClientSideNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-n",
      "o=test"
    };

    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple delete using the server-side no-op control.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testDeleteServerSideNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true",
      "o=test"
    };

    LDAPDelete.mainDelete(args, false, null, null);
  }



  /**
   * Tests the LDAPDelete tool with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args = { "--help" };
    assertEquals(LDAPDelete.mainDelete(args, false, null, null), 0);

    args = new String[] { "-H" };
    assertEquals(LDAPDelete.mainDelete(args, false, null, null), 0);
  }
}

