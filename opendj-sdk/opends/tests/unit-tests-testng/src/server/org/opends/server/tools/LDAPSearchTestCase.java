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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
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
 * A set of test cases for the LDAPSearch tool.
 */
public class LDAPSearchTestCase
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
   * the LDAPSearch tool.
   *
   * @return  Sets of invalid arguments that may not be used to initialize the
   *          LDAPSearch tool.
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
      "-b"
    };
    argLists.add(args);
    reasonList.add("No value for '-b' argument");

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
      "-Y"
    };
    argLists.add(args);
    reasonList.add("No value for '-Y' argument");

    args = new String[]
    {
      "-i"
    };
    argLists.add(args);
    reasonList.add("No value for '-i' argument");

    args = new String[]
    {
      "-K"
    };
    argLists.add(args);
    reasonList.add("No value for '-K' argument");

    args = new String[]
    {
      "-P"
    };
    argLists.add(args);
    reasonList.add("No value for '-P' argument");

    args = new String[]
    {
      "-W"
    };
    argLists.add(args);
    reasonList.add("No value for '-W' argument");

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
      "-V"
    };
    argLists.add(args);
    reasonList.add("No value for '-V' argument");

    args = new String[]
    {
      "-f"
    };
    argLists.add(args);
    reasonList.add("No value for '-f' argument");

    args = new String[]
    {
      "-J"
    };
    argLists.add(args);
    reasonList.add("No value for '-J' argument");

    args = new String[]
    {
      "-z"
    };
    argLists.add(args);
    reasonList.add("No value for '-z' argument");

    args = new String[]
    {
      "-l"
    };
    argLists.add(args);
    reasonList.add("No value for '-l' argument");

    args = new String[]
    {
      "-s"
    };
    argLists.add(args);
    reasonList.add("No value for '-s' argument");

    args = new String[]
    {
      "-a"
    };
    argLists.add(args);
    reasonList.add("No value for '-a' argument");

    args = new String[]
    {
      "-o"
    };
    argLists.add(args);
    reasonList.add("No value for '-o' argument");

    args = new String[]
    {
      "-c"
    };
    argLists.add(args);
    reasonList.add("No value for '-c' argument");

    args = new String[]
    {
      "--assertionFilter"
    };
    argLists.add(args);
    reasonList.add("No value for '--assertionFilter' argument");

    args = new String[]
    {
      "--matchedValuesFilter"
    };
    argLists.add(args);
    reasonList.add("No value for '--matchedValuesFilter' argument");

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
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("No base DN");

    args = new String[]
    {
      "-b", ""
    };
    argLists.add(args);
    reasonList.add("No filter");

    args = new String[]
    {
      "-b", "",
      "(invalidfilter)"
    };
    argLists.add(args);
    reasonList.add("Invalid search filter");

    args = new String[]
    {
      "-b", "",
      "--assertionFilter", "(invalidfilter)",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid assertion filter");

    args = new String[]
    {
      "-b", "",
      "--matchedValuesFilter", "(invalidfilter)",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid matched values filter");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-j", "no.such.file",
      "-b", "",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid bind password file path");

    args = new String[]
    {
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-j", validPasswordFile,
      "-b", "",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Both bind password and bind password file");

    args = new String[]
    {
      "-b", "",
      "-V", "nonnumeric",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Non-numeric LDAP version");

    args = new String[]
    {
      "-b", "",
      "-V", "1",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid LDAP version");

    args = new String[]
    {
      "-b", "",
      "-f", "no.such.file"
    };
    argLists.add(args);
    reasonList.add("Invalid filter file path");

    args = new String[]
    {
      "-b", "",
      "-J", "1.2.3.4:invalidcriticality",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid control criticality");

    args = new String[]
    {
      "-b", "",
      "-s", "invalid",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid scope");

    args = new String[]
    {
      "-b", "",
      "-a", "invalid",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid dereference policy");

    args = new String[]
    {
      "-b", "",
      "-C", "invalid",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid psearch descriptor");

    args = new String[]
    {
      "-b", "",
      "-C", "ps:invalid",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid psearch changetype");

    args = new String[]
    {
      "-b", "",
      "-C", "ps:add,delete,modify,modifydn,invalid",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid psearch changetype in list");

    args = new String[]
    {
      "-b", "",
      "-C", "ps:all:invalid",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid psearch changesOnly");

    args = new String[]
    {
      "-b", "",
      "-C", "ps:all:1:invalid",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Invalid psearch entryChangeControls");

    args = new String[]
    {
      "-p", "nonnumeric",
      "-b", "",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Non-numeric port");

    args = new String[]
    {
      "-p", "999999",
      "-b", "",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Port value out of range");

    args = new String[]
    {
      "-z", "nonnumeric",
      "-b", "",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Non-numeric size limit");

    args = new String[]
    {
      "-l", "nonnumeric",
      "-b", "",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("Non-numeric time limit");

    args = new String[]
    {
      "-r",
      "-b", "",
      "-K", "key.store.file",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("SASL external without SSL or StartTLS");

    args = new String[]
    {
      "-Z",
      "-r",
      "-b", "",
      "(objectClass=*)"
    };
    argLists.add(args);
    reasonList.add("SASL external without a keystore file");


    Object[][] returnArray = new Object[argLists.size()][2];
    for (int i=0; i < argLists.size(); i++)
    {
      returnArray[i][0] = argLists.get(i);
      returnArray[i][1] = reasonList.get(i);
    }
    return returnArray;
  }



  /**
   * Tests the LDAPSearch tool with sets of invalid arguments.
   *
   * @param  args           The set of arguments to use for the LDAPSearch tool.
   * @param  invalidReason  The reason that the set of arguments is not valid.
   */
  @Test(dataProvider = "invalidArgs")
  public void testInvalidArguments(String[] args, String invalidReason)
  {
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0,
                "Should have been invalid because:  " + invalidReason);
  }



  /**
   * Tests a simple LDAPv2 search.
   */
  @Test()
  public void testSimpleLDAPv2Search()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "2",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAPv3 search.
   */
  @Test()
  public void testSimpleLDAPv3Search()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-V", "3",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAP search over SSL using blind trust.
   */
  @Test()
  public void testSimpleSearchSSLBlindTrust()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-X",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAP search over SSL using a trust store.
   */
  @Test()
  public void testSimpleSearchSSLTrustStore()
  {
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-P", trustStorePath,
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAP search using StartTLS with blind trust.
   */
  @Test()
  public void testSimpleSearchStartTLSBlindTrust()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-X",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAP search using StartTLS with a trust store.
   */
  @Test()
  public void testSimpleSearchStartTLSTrustStore()
  {
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAP search over SSL using a trust store and SASL EXTERNAL
   * authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleSearchSSLTrustStoreSASLExternal()
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
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple LDAP search using StartTLS with a trust store and SASL
   * EXTERNAL authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleSearchStartTLSTrustStoreSASLExternal()
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
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple search operation using CRAM-MD5 authentication.
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
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple search operation using CRAM-MD5 authentication.
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
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a simple search operation using PLAIN authentication.
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
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a search with a malformed bind DN.
   */
  @Test()
  public void testMalformedBindDN()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "malformed",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests a search with a nonexistent bind DN.
   */
  @Test()
  public void testNonExistentBindDN()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Does Not Exist",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests a search with an invalid password.
   */
  @Test()
  public void testInvalidBindPassword()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "wrongPassword",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests a search with a valid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", validPasswordFile,
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests a search with an invalid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInvalidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", invalidPasswordFile,
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests a search with a malformed base DN.
   */
  @Test()
  public void testMalformedBaseDN()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "malformed",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests a search with a nonexistent base DN.
   */
  @Test()
  public void testNonExistentBaseDN()
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=does not exist",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Retrieves the set of valid search scopes.
   *
   * @return  The set of valid search scopes.
   */
  @DataProvider(name = "scopes")
  public Object[][] getSearchScopes()
  {
    return new Object[][]
    {
      new Object[] { "base" },
      new Object[] { "one" },
      new Object[] { "sub" },
      new Object[] { "subordinate" },
    };
  }



  /**
   * Tests searches with the various allowed search scopes.
   *
   * @param  scope  The scope to use for the search.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "scopes")
  public void testSearchScopes(String scope)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=test",
      "-s", scope,
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Retrieves the set of valid alias dereferencing policies.
   *
   * @return  The set of valid alias dereferencing policies.
   */
  @DataProvider(name = "derefPolicies")
  public Object[][] getDerefPolicies()
  {
    return new Object[][]
    {
      new Object[] { "never" },
      new Object[] { "always" },
      new Object[] { "search" },
      new Object[] { "find" },
    };
  }



  /**
   * Tests searches with the various allowed dereference policy values.
   *
   * @param  derefPolicy  The alias dereferencing policy for the search.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "derefPolicies")
  public void testDerefPolicies(String derefPolicy)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=test",
      "-s", "base",
      "-a", derefPolicy,
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the typesOnly option.
   */
  @Test()
  public void testTypesOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=test",
      "-s", "base",
      "-A",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the reportAuthzID option for an unauthenticated search.
   */
  @Test()
  public void testReportAuthzIDUnauthenticated()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=test",
      "-s", "base",
      "--reportAuthzID",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the reportAuthzID option for an authenticated search.
   */
  @Test()
  public void testReportAuthzIDAuthenticated()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "--reportAuthzID",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the usePasswordPolicyControl option for an authenticated search.
   */
  @Test()
  public void testUsePasswordPolicyControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "--usePasswordPolicyControl",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the account usability control for an authenticated search.
   */
  @Test()
  public void testAccountUsabilityControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "-J", OID_ACCOUNT_USABLE_CONTROL + ":true",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the account usability control with an alternate name for an
   * authenticated search.
   */
  @Test()
  public void testAccountUsabilityControlAltName()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "-J", "accountusable:true",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the LDAP assertion control in which the assertion is true.
   */
  @Test()
  public void testLDAPAssertionControlTrue()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "--assertionFilter", "(objectClass=top)",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests with the LDAP assertion control in which the assertion is false.
   */
  @Test()
  public void testLDAPAssertionControlFalse()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "--assertionFilter", "(objectClass=doesNotMatch)",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests with the LDAP matched values control.
   */
  @Test()
  public void testMatchedValuesControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "--matchedValuesFilter", "(objectClass=*person)",
      "(objectClass=*)",
      "objectClass"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests the use of the LDAP subentries control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSubentriesControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: ldapSubEntry",
                                      "cn: test");

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
      "-b", "o=test",
      "-s", "sub",
      "--countEntries",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 1);

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=test",
      "-s", "sub",
      "--countEntries",
      "-J", OID_LDAP_SUBENTRIES + ":true",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 2);

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=test",
      "-s", "sub",
      "--countEntries",
      "-J", "subentries:true",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 2);
  }



  /**
   * Tests the inclusion of multiple arbitrary controls in the request to the
   * server.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testMultipleRequestControls()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "-J", OID_ACCOUNT_USABLE_CONTROL + ":true",
      "-J", OID_MANAGE_DSAIT_CONTROL + ":false",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests the use of the simple paged results control.
   *
   * @throws  Exception  If an unexpectd problem occurs.
   */
  @Test()
  public void testSimplePagedResults()
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    TestCaseUtils.addEntries(
      "dn: cn=device 1,dc=example,dc=com",
      "objectClass: top",
      "objectClass: device",
      "cn: device 1",
      "",
      "dn: cn=device 2,dc=example,dc=com",
      "objectClass: top",
      "objectClass: device",
      "cn: device 2",
      "",
      "dn: cn=device 3,dc=example,dc=com",
      "objectClass: top",
      "objectClass: device",
      "cn: device 3",
      "",
      "dn: cn=device 4,dc=example,dc=com",
      "objectClass: top",
      "objectClass: device",
      "cn: device 4",
      "",
      "dn: cn=device 5,dc=example,dc=com",
      "objectClass: top",
      "objectClass: device",
      "cn: device 5");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "dc=example,dc=com",
      "-s", "one",
      "--simplePageSize", "2",
      "--countEntries",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 5);
  }



  /**
   * Tests the LDAPSearch tool with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args = { "--help" };
    assertEquals(LDAPSearch.mainSearch(args, false, null, null), 0);

    args = new String[] { "-H" };
    assertEquals(LDAPSearch.mainSearch(args, false, null, null), 0);
  }
}

