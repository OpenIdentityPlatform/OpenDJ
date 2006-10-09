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
 * A set of test cases for the LDAPModify tool.
 */
public class LDAPModifyTestCase
       extends ToolsTestCase
{
  // The path to a file containing an invalid bind password.
  private String invalidPasswordFile;

  // The path to a file containing a valid bind password.
  private String validPasswordFile;

  // The path to a file containing a simple, valid modification.
  private String modifyFilePath;



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

    modifyFilePath = TestCaseUtils.createTempFile("dn: o=test",
                                                  "changetype: modify",
                                                  "replace: description",
                                                  "description: foo");
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
    ArrayList<String[]> argLists = new ArrayList<String[]>();

    String[] args;
    args = new String[] // Missing value for -D argument.
    {
      "-D"
    };
    argLists.add(args);

    args = new String[] // Missing value for -w argument.
    {
      "-w"
    };
    argLists.add(args);

    args = new String[] // Missing value for -j argument.
    {
      "-j"
    };
    argLists.add(args);

    args = new String[] // Missing value for -Y argument.
    {
      "-Y"
    };
    argLists.add(args);

    args = new String[] // Missing value for -i argument.
    {
      "-i"
    };
    argLists.add(args);

    args = new String[] // Missing value for -K argument.
    {
      "-K"
    };
    argLists.add(args);

    args = new String[] // Missing value for -P argument.
    {
      "-P"
    };
    argLists.add(args);

    args = new String[] // Missing value for -W argument.
    {
      "-W"
    };
    argLists.add(args);

    args = new String[] // Missing value for -h argument.
    {
      "-h"
    };
    argLists.add(args);

    args = new String[] // Missing value for -p argument.
    {
      "-p"
    };
    argLists.add(args);

    args = new String[] // Missing value for -V argument.
    {
      "-V"
    };
    argLists.add(args);

    args = new String[] // Missing value for -f argument.
    {
      "-f"
    };
    argLists.add(args);

    args = new String[] // Missing value for -J argument.
    {
      "-J"
    };
    argLists.add(args);

    args = new String[] // Missing value for -o argument.
    {
      "-o"
    };
    argLists.add(args);

    args = new String[] // Missing value for --assertionFilter argument.
    {
      "-assertionFilter"
    };
    argLists.add(args);

    args = new String[] // Missing value for --preReadAttributes argument.
    {
      "--preReadAttributes"
    };
    argLists.add(args);

    args = new String[] // Missing value for --postReadAttributes argument.
    {
      "--postReadAttributes"
    };
    argLists.add(args);

    args = new String[] // Invalid bind password file path
    {
      "-D", "cn=Directory Manager",
      "-j", "no.such.file",
    };
    argLists.add(args);

    args = new String[] // Both bind password and password file
    {
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-j", validPasswordFile,
    };
    argLists.add(args);

    args = new String[] // Non-numeric LDAP version.
    {
      "-V", "nonnumeric",
    };
    argLists.add(args);

    args = new String[] // Invalid LDAP version.
    {
      "-V", "1",
    };
    argLists.add(args);

    args = new String[] // Invalid control criticality
    {
      "-J", "1.2.3.4:invalidcriticality",
    };
    argLists.add(args);

    args = new String[] // Non-numeric port
    {
      "-p", "nonnumeric",
    };
    argLists.add(args);

    args = new String[] // Port value out of range.
    {
      "-p", "999999",
    };
    argLists.add(args);

    args = new String[] // SASL external without SSL or StartTLS
    {
      "-r",
      "-K", "key.store.file",
    };
    argLists.add(args);

    args = new String[] // SASL external without keystore file
    {
      "-Z",
      "-r",
    };
    argLists.add(args);

    args = new String[] // Invalid LDAP assertion filter
    {
      "--assertionFilter", "(invalid)"
    };
    argLists.add(args);

    args = new String[] // No such LDIF file
    {
      "-f", "no.such.file"
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
   * Tests the LDAPModify tool with sets of invalid arguments.
   *
   * @param  args  The set of arguments to use for the LDAPModify tool.
   */
  @Test(dataProvider = "invalidArgs")
  public void testInvalidArguments(String[] args)
  {
    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
  }



  /**
   * Tests a simple modify operation using LDAPv2.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPv2Modify()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-V", "2",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using LDAPv3.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPv3Modify()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-V", "3",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation over SSL using blind trust.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSSLBlindTrust()
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
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation over SSL using a trust store.
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

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation with StartTSL using blind trust.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStartTLSBlindTrust()
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
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation with StartTLS using a trust store.
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

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation over SSL using a trust store and SASL
   * EXTERNAL.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSSLTrustStoreSASLExternal()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath   = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-P", trustStorePath,
      "-r",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation with StartTLS using a trust store and SASL
   * EXTERNAL.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStartTLSTrustStoreSASLExternal()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath   = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-K", keyStorePath,
      "-W", "password",
      "-P", trustStorePath,
      "-r",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using CRAM-MD5 authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCRAMMD5()
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
         "pwdPolicySubentry: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

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
      "-o", "mech=CRAM-MD5",
      "-o", "authid=u:test.user",
      "-w", "password",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using DIGEST-MD5 authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDigestMD5()
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
         "pwdPolicySubentry: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

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
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:test.user",
      "-o", "authzid=u:test.user",
      "-o", "realm=o=test",
      "-w", "password",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using PLAIN authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAIN()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using the --noop client-side option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyClientSideNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noop",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple add operation using the --noop client-side option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddClientSideNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "o: test");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noop",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple delete operation using the --noop client-side option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteClientSideNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: o=test",
         "changetype: delete");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noop",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify DN operation using the --noop client-side option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNClientSideNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: moddn",
         "newRDN: ou=Users",
         "deleteOldRDN: 1");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noop",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using LDAP No-Op control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyLDAPNoOp()
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
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple add operation using LDAP No-Op control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddLDAPNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple delete operation using LDAP No-Op control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteLDAPNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: o=test",
         "changetype: delete");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify DN operation using LDAP No-Op control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNLDAPNoOp()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: moddn",
         "newRDN: ou=Users",
         "deleteOldRDN: 1");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using the LDAP assertion control in which
   * the assertion is true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyLDAPAssertionTrue()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--assertionFilter", "(o=test)",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using the LDAP assertion control in which
   * the assertion is not true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyLDAPAssertionFalse()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--assertionFilter", "(o=foo)",
      "-f", modifyFilePath
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
  }



  /**
   * Tests a simple delete operation using the LDAP assertion control in which
   * the assertion is true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteLDAPAssertionTrue()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile("dn: o=test",
                                               "changetype: delete");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--assertionFilter", "(o=test)",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify DN operation using the LDAP assertion control in
   * which the assertion is true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNLDAPAssertionTrue()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String path = TestCaseUtils.createTempFile("dn: ou=People,o=test",
                                               "changetype: moddn",
                                               "newRDN: ou=Users",
                                               "deleteOldRDN: 1");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--assertionFilter", "(ou=People)",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using the LDAP pre-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyLDAPPreReadSingleAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--preReadAttributes", "o",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using the LDAP pre-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyLDAPPreReadMultipleAttributes()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--preReadAttributes", "o,objectClass",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple delete operation using the LDAP pre-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteLDAPPreReadSingleAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile("dn: o=test",
                                               "changetype: delete");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--preReadAttributes", "o",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify DN operation using the LDAP pre-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNLDAPPreReadSingleAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String path = TestCaseUtils.createTempFile("dn: ou=People,o=test",
                                               "changetype: moddn",
                                               "newRDN: ou=Users",
                                               "deleteOldRDN: 1");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--preReadAttributes", "o",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using the LDAP post-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyLDAPostReadSingleAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--postReadAttributes", "o",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify operation using the LDAP post-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyLDAPPostReadMultipleAttributes()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--postReadAttributes", "o,objectClass",
      "-f", modifyFilePath
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple add operation using the LDAP post-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddLDAPostReadSingleAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--postReadAttributes", "o",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple modify DN operation using the LDAP post-read control with a
   * single attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNLDAPostReadSingleAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    String path = TestCaseUtils.createTempFile("dn: ou=People,o=test",
                                               "changetype: moddn",
                                               "newRDN: ou=Users",
                                               "deleteOldRDN: 1");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--postReadAttributes", "o",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests a modify operation that will fail on the server side.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testServerSideModifyFailure()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: modify",
         "replace: description",
         "description: foo");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--postReadAttributes", "o,objectClass",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
  }



  /**
   * Tests performing an add operation with an explicit changetype.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddExplicitChangeType()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests performing an add operation with an implied changetype.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddImplicitChangeType()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests performing a modify DN operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People",
         "",
         "dn: ou=People,o=test",
         "changetype: moddn",
         "newRDN: ou=Users",
         "deleteOldRDN: 1");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests performing a delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=People,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People",
         "",
         "dn: ou=People,o=test",
         "changetype: delete");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }



  /**
   * Tests with various forms of malformed LDIF changes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMalformedLDIF()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: o=test",
         "description: No Changetype",
         "",
         "dn: o=test",
         "changetype: invalid",
         "replace: description",
         "description: Invalid Changetype",
         "",
         "dn: o=test",
         "changetype: modify",
         "invalid: description",
         "description: Invalid Attribute Modification",
         "",
         "dn: ou=People,o=test",
         "",
         "dn: ou=People,o=test",
         "changetype: add",
         "",
         "dn: ou=People,o=test",
         "changetype: moddn",
         "",
         "dn: ou=People,o=test",
         "changetype: moddn",
         "newrdn: invalid",
         "deleteOldRDN: 1",
         "",
         "dn: ou=People,o=test",
         "changetype: moddn",
         "newrdn: ou=Users",
         "deleteOldRDN: invalid");


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-c",
      "-f", path
    };

    LDAPModify.mainModify(args, false, null,null);
  }



  /**
   * Tests a modify attempt failure without continueOnError.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMalformedLDIFNoContinueOnError()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: o=test",
         "description: No Changetype",
         "",
         "dn: o=test",
         "changetype: invalid",
         "replace: description",
         "description: Invalid Changetype");


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    LDAPModify.mainModify(args, false, null,null);
  }



  /**
   * Tests the LDAPModify tool with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args = { "--help" };
    assertEquals(LDAPModify.mainModify(args, false, null, null), 0);

    args = new String[] { "-H" };
    assertEquals(LDAPModify.mainModify(args, false, null, null), 0);
  }
}

