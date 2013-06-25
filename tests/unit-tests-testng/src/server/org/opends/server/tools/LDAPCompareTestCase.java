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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock, AS.
 */
package org.opends.server.tools;



import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Base64;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.testng.Assert.*;



/**
 * A set of test cases for the LDAPCompare tool.
 */
public class LDAPCompareTestCase
       extends ToolsTestCase
{
  // The path to a file containing a valid bind password.
  private String validPasswordFile;



  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServerAndCreatePasswordFiles()
         throws Exception
  {
    TestCaseUtils.startServer();


    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--set", "server-fqdn:" + "127.0.0.1");

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
  }

  @AfterClass
  public void tearDown() throws Exception {
    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--remove", "server-fqdn:" + "127.0.0.1");
  }


  /**
   * Retrieves sets of invalid arguments that may not be used to initialize
   * the LDAPCompare tool.
   *
   * @return  Sets of invalid arguments that may not be used to initialize the
   *          LDAPCompare tool.
   */
  @DataProvider(name = "invalidArgs")
  public Object[][] getInvalidArgumentLists()
  {
    ArrayList<String[]> argLists   = new ArrayList<String[]>();
    ArrayList<String>   reasonList = new ArrayList<String>();

    String[] args = {};
    argLists.add(args);
    reasonList.add("No arguments");

    args = new String[]
    {
      "-D",
    };
    argLists.add(args);
    reasonList.add("No value for '-D' argument");

    args = new String[]
    {
      "-w",
    };
    argLists.add(args);
    reasonList.add("No value for '-w' argument");

    args = new String[]
    {
      "-j",
    };
    argLists.add(args);
    reasonList.add("No value for '-j' argument");

    args = new String[]
    {
      "-i",
    };
    argLists.add(args);
    reasonList.add("No value for '-i' argument");

    args = new String[]
    {
      "-K",
    };
    argLists.add(args);
    reasonList.add("No value for '-K' argument");

    args = new String[]
    {
      "-P",
    };
    argLists.add(args);
    reasonList.add("No value for '-P' argument");

    args = new String[]
    {
      "-W",
    };
    argLists.add(args);
    reasonList.add("No value for '-W' argument");

    args = new String[]
    {
      "-h",
    };
    argLists.add(args);
    reasonList.add("No value for '-h' argument");

    args = new String[]
    {
      "-p",
    };
    argLists.add(args);
    reasonList.add("No value for '-p' argument");

    args = new String[]
    {
      "-V",
    };
    argLists.add(args);
    reasonList.add("No value for '-V' argument");

    args = new String[]
    {
      "-f",
    };
    argLists.add(args);
    reasonList.add("No value for '-f' argument");

    args = new String[]
    {
      "-J",
    };
    argLists.add(args);
    reasonList.add("No value for '-J' argument");

    args = new String[]
    {
      "-o",
    };
    argLists.add(args);
    reasonList.add("No value for '-o' argument");

    args = new String[]
    {
      "--assertionFilter",
    };
    argLists.add(args);
    reasonList.add("No value for '--assertionFilter' argument");

    args = new String[]
    {
      "-I"
    };
    argLists.add(args);
    reasonList.add("Invalid short argument");

    args = new String[]
    {
      "--invalidLongArgument"
    };
    argLists.add(args);
    reasonList.add("Invalid long argument");

    args = new String[]
    {
      "--assertionFilter", "(invalidfilter)",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Invalid assertion filter");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-j", "no.such.file",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Invalid bind password file path");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-j", validPasswordFile,
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Both bind password and password file");

    args = new String[]
    {
      "-V", "nonnumeric",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Non-numeric LDAP version");

    args = new String[]
    {
      "-V", "1",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Invalid LDAP version");

    args = new String[]
    {
      "-f", "no.such.file",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Invalid DN file path");

    args = new String[]
    {
      "-J", "1.2.3.4:invalidcriticality",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Invalid control criticality");

    args = new String[]
    {
      "-p", "nonnumeric",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Non-numeric port");

    args = new String[]
    {
      "-p", "999999",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Port value out of range");

    args = new String[]
    {
      "-r",
      "-K", "key.store.file",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("SASL external without SSL or StartTLS");

    args = new String[]
    {
      "-Z",
      "-r",
      "uid:test.user",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("SASL external without keystore file");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-w", "password"
    };
    argLists.add(args);
    reasonList.add("No trailing arguments");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-w", "password",
      "uid:test.user"
    };
    argLists.add(args);
    reasonList.add("Only one trailing argument");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-w", "password",
      "malformed",
      "uid=test.user,o=test"
    };
    argLists.add(args);
    reasonList.add("Malformed attribute-value assertion");


    Object[][] returnArray = new Object[argLists.size()][2];
    for (int i=0; i < argLists.size(); i++)
    {
      returnArray[i][0] = argLists.get(i);
      returnArray[i][1] = reasonList.get(i);
    }
    return returnArray;
  }



  /**
   * Tests the LDAPCompare tool with sets of invalid arguments.
   *
   * @param  args           The set of arguments to use for the LDAPCompare
   *                        tool.
   * @param  invalidReason  The reason the provided set of arguments is invalid.
   */
  @Test(dataProvider = "invalidArgs")
  public void testInvalidArguments(String[] args, String invalidReason)
  {
    assertFalse(LDAPCompare.mainCompare(args, false, null, null) == SUCCESS,
                "Should have been invalid because:  " + invalidReason);
  }



  /**
   * Tests a simple LDAPv2 compare.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleLDAPv2Compare()
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
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "2",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "o:test",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple LDAPv3 compare in which the assertion is true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleLDAPv3CompareTrue()
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
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "o:test",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple LDAPv3 compare in which the assertion is false.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleLDAPv3CompareFalse()
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
      "--noPropertiesFile",
      "o:nottest",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "o:nottest",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_FALSE);
  }


  /**
   * Tests two LDAPv3 compares in which the assertion is true for all.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testMultipleCompareAllTrue() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Backend memoryBackend =
        DirectoryServer.getBackend(TestCaseUtils.TEST_BACKEND_ID);
    String dn1 = "arg=success,o=test1,o=test";
    String dn2 = "arg=success,o=test2,o=test";
    addEntriesUpToParentDN(memoryBackend, DN.decode(dn1));
    addEntriesUpToParentDN(memoryBackend, DN.decode(dn2));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--continueOnError",
      "arg:success",
      dn1,
      dn2
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "--continueOnError",
      "arg:success",
      dn1,
      dn2
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }


  /**
   * Tests two LDAPv3 compares in which one assertion is true and one is false.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testMultipleCompareOneCompareIsFalse() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Backend memoryBackend =
        DirectoryServer.getBackend(TestCaseUtils.TEST_BACKEND_ID);
    String dn1 = "arg=success,o=test1,o=test";
    String dn2 = "arg=fail,o=test2,o=test";
    addEntriesUpToParentDN(memoryBackend, DN.decode(dn1));
    addEntriesUpToParentDN(memoryBackend, DN.decode(dn2));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--continueOnError",
      "arg:success",
      dn1,
      dn2
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "--continueOnError",
      "arg:success",
      dn1,
      dn2
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_FALSE);
  }

  /**
   * Tests two LDAPv3 compares in which one assertion is true and one returns no
   * such object.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testMultipleCompareOneNoSuchObject() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Backend memoryBackend =
        DirectoryServer.getBackend(TestCaseUtils.TEST_BACKEND_ID);
    String dn1 = "arg=success,o=test1,o=test";
    addEntriesUpToParentDN(memoryBackend, DN.decode(dn1));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--continueOnError",
      "arg:success",
      dn1,
      "arg=fail,o=test2,o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "--continueOnError",
      "arg:success",
      dn1,
      "arg=fail,o=test2,o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), NO_SUCH_OBJECT);
  }


  private void addEntriesUpToParentDN(Backend backend, DN entryDN)
      throws Exception
  {
    if (!backend.entryExists(entryDN.getParent()))
    {
      addEntriesUpToParentDN(backend, entryDN.getParent());
    }
    backend.addEntry(StaticUtils.createEntry(entryDN), null);
  }

  /**
   * Tests a simple compare using SSL with blind trust.
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
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "o:test",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple compare using SSL with a trust store.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSSLTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "o:test",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple compare using StartTLS with blind trust.
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
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "o:test",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple compare using StartTLS with a trust store.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStartTLSTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "o:test",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple LDAP compare over SSL using a trust store and SASL EXTERNAL
   * authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleCompareSSLTrustStoreSASLExternal()
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
         "ds-privilege-name: bypass-acl",
         "sn: User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-P", trustStorePath,
      "--noPropertiesFile",
      "-r",
      "cn:Test User",
      "cn=Test User,o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-P", trustStorePath,
      "--noPropertiesFile",
      "--useCompareResultCode",
      "-r",
      "cn:Test User",
      "cn=Test User,o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple LDAP compare over SSL using a trust store and SASL EXTERNAL
   * authentication when explicitly specifying a valid client certificate.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleCompareSSLTrustStoreSASLExternalValidClientCert()
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
         "ds-privilege-name: bypass-acl",
         "sn: User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-N", "client-cert",
      "-P", trustStorePath,
      "--noPropertiesFile",
      "-r",
      "cn:Test User",
      "cn=Test User,o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-N", "client-cert",
      "-P", trustStorePath,
      "--noPropertiesFile",
      "--useCompareResultCode",
      "-r",
      "cn:Test User",
      "cn=Test User,o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple LDAP compare over SSL using a trust store and SASL EXTERNAL
   * authentication when explicitly specifying an invalid client certificate.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleCompareSSLTrustStoreSASLExternalInvalidClientCert()
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
         "ds-privilege-name: bypass-acl",
         "sn: User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-N", "invalid",
      "-P", trustStorePath,
      "--noPropertiesFile",
      "-r",
      "cn:Test User",
      "cn=Test User,o=test"
    };

    assertFalse(LDAPCompare.mainCompare(args, false, null, null) == SUCCESS);
  }



  /**
   * Tests a simple LDAP compare using StartTLS with a trust store and SASL
   * EXTERNAL authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleCompareStartTLSTrustStoreSASLExternal()
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
         "ds-privilege-name: bypass-acl",
         "sn: User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-K", keyStorePath,
      "-W", "password",
      "-P", trustStorePath,
      "--noPropertiesFile",
      "-r",
      "cn:Test User",
      "cn=Test User,o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-K", keyStorePath,
      "-W", "password",
      "-P", trustStorePath,
      "--noPropertiesFile",
      "--useCompareResultCode",
      "-r",
      "cn:Test User",
      "cn=Test User,o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple compare operation using CRAM-MD5 authentication.
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
         "ds-privilege-name: bypass-acl",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
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
      "--noPropertiesFile",
      "givenName:Test",
      "uid=test.user,o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=u:test.user",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "givenName:Test",
      "uid=test.user,o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple compare operation using DIGEST-MD5 authentication.
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
         "ds-privilege-name: bypass-acl",
         "ds-privilege-name: proxied-auth",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
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
      "-w", "password",
      "--noPropertiesFile",
      "givenName:Test",
      "uid=test.user,o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:test.user",
      "-o", "authzid=u:test.user",
      "-w", "password",
      "--useCompareResultCode",
      "--noPropertiesFile",
      "givenName:Test",
      "uid=test.user,o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a simple compare operation using PLAIN authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAIN()
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
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "givenName:Test",
      "uid=test.user,o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "--useCompareResultCode",
      "givenName:Test",
      "uid=test.user,o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a a comparison in which the assertion value is base64-encoded with a
   * valid encoding.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareValidBase64Assertion()
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
      "--noPropertiesFile",
      "o::" + Base64.encode("test".getBytes("UTF-8")),
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--useCompareResultCode",
      "--noPropertiesFile",
      "o::" + Base64.encode("test".getBytes("UTF-8")),
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a a comparison in which the assertion value should be base64-encoded
   * but uses an incorrect encoding.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareInvalidBase64Assertion()
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
      "--noPropertiesFile",
      "o::***invalidencoding***",
      "o=test"
    };

    assertFalse(LDAPCompare.mainCompare(args, false, null, null) == SUCCESS);
  }



  /**
   * Tests a a comparison in which the assertion value is contained in a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareAssertionValueFromFile()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    File f = File.createTempFile("testCompareAssertionValueFromFile", ".txt");
    f.deleteOnExit();
    FileWriter w = new FileWriter(f);
    w.write("test");
    w.close();

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "o:<" + f.getAbsolutePath(),
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--useCompareResultCode",
      "--noPropertiesFile",
      "o:<" + f.getAbsolutePath(),
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a a comparison in which the assertion value is contained in a file
   * that does not exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareAssertionValueFromNonExistentFile()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "o:<does.not.exist",
      "o=test"
    };

    assertFalse(LDAPCompare.mainCompare(args, false, null, null) == SUCCESS);
  }



  /**
   * Tests a a comparison using the LDAP assertion control in which the
   * assertion is true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareLDAPAssertionControlTrue()
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
      "--assertionFilter", "(o=test)",
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    String[] argsUseCompare =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--assertionFilter", "(o=test)",
      "--useCompareResultCode",
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };

    assertEquals(LDAPCompare.mainCompare(args, false, null, System.err),
        SUCCESS);
    assertEquals(LDAPCompare.mainCompare(argsUseCompare, false, null,
        System.err), COMPARE_TRUE);
  }



  /**
   * Tests a a comparison using the LDAP assertion control in which the
   * assertion is not true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareLDAPAssertionControlNotTrue()
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
      "--assertionFilter", "(o=notAMatch)",
      "--noPropertiesFile",
      "o:test",
      "o=test"
    };
    assertFalse(LDAPCompare.mainCompare(args, false, null, null) == SUCCESS);
  }



  /**
   * Tests a a compare operation reading the DNs to compare from a file.  Some
   * of the compares will succeed and others will not.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareDNsFromFile()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "o=test",
         "dc=example,dc=com",
         "o=nonexistentsuffix",
         "malformed",
         "o=nonexistent,o=test");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-c",
      "-f", path,
      "--noPropertiesFile",
      "o:test",
    };

    LDAPCompare.mainCompare(args, false, null, null);
  }



  /**
   * Tests a a compare operation reading the DNs to compare from a file that
   * doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareDNsFromNonExistentFile()
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
      "-c",
      "-f", "does.not.exist",
      "--noPropertiesFile",
      "o:test",
    };

    assertFalse(LDAPCompare.mainCompare(args, false, null, null) == SUCCESS);
  }



  /**
   * Tests the LDAPCompare tool with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args = { "--help" };
    assertEquals(LDAPCompare.mainCompare(args, false, null, null), SUCCESS);

    args = new String[] { "-H" };
    assertEquals(LDAPCompare.mainCompare(args, false, null, null), SUCCESS);

    args = new String[] { "-?" };
    assertEquals(LDAPCompare.mainCompare(args, false, null, null), SUCCESS);
  }



  @DataProvider(name = "aggregateResults")
  public Object[][] getAggregateResultCodeParamsAndResults()
  {
    return new Object[][] { { SUCCESS, SUCCESS, SUCCESS },
      { SUCCESS, COMPARE_TRUE, COMPARE_TRUE },
      { SUCCESS, COMPARE_FALSE, COMPARE_FALSE },
      { SUCCESS, OPERATIONS_ERROR, OPERATIONS_ERROR },
      { COMPARE_TRUE, COMPARE_TRUE, COMPARE_TRUE },
      { COMPARE_TRUE, COMPARE_FALSE, COMPARE_FALSE },
      { COMPARE_TRUE, OPERATIONS_ERROR, OPERATIONS_ERROR },
      { COMPARE_FALSE, COMPARE_TRUE, COMPARE_FALSE },
      { COMPARE_FALSE, COMPARE_FALSE, COMPARE_FALSE },
      { COMPARE_FALSE, OPERATIONS_ERROR, OPERATIONS_ERROR },
      { OPERATIONS_ERROR, COMPARE_TRUE, OPERATIONS_ERROR },
      { OPERATIONS_ERROR, COMPARE_FALSE, OPERATIONS_ERROR },
      { OPERATIONS_ERROR, OPERATIONS_ERROR, OPERATIONS_ERROR } };
  }

  /**
   * Test the results of calling function
   * {@link LDAPCompare#aggregateResultCode(int, int)}.
   */
  @Test(dataProvider = "aggregateResults")
  public void testAggregateResultCode(int currentAggregatedResult,
      int newResultCode, int finalAggregatedResult)
  {
    LDAPCompare obj = new LDAPCompare(new AtomicInteger(), null, null);
    assertEquals(obj
        .aggregateResultCode(currentAggregatedResult, newResultCode),
        finalAggregatedResult);
  }
}

