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
 */
package org.opends.server.extensions;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.*;

import static org.testng.Assert.*;



/**
 * A set of test cases for the PLAIN SASL mechanism handler.
 */
public class PlainSASLMechanismHandlerTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that the SASL PLAIN mechanism is loaded and available in
   * the server, and that it reports that it is password based and not secure.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSASLPlainLoaded()
  {
    SASLMechanismHandler handler =
         DirectoryServer.getSASLMechanismHandler("PLAIN");
    assertNotNull(handler);

    assertTrue(handler.isPasswordBased("PLAIN"));
    assertFalse(handler.isSecure("PLAIN"));
  }



  /**
   * Tests to ensure that PLAIN is advertised as a supported SASL mechanism.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSASLPlainAdvertised()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation op =
         conn.processSearch(ByteString.empty(), SearchScope.BASE_OBJECT,
              LDAPFilter.decode("(supportedSASLMechanisms=PLAIN)"));
    assertFalse(op.getSearchEntries().isEmpty());
  }




  /**
   * Retrieves a set of passwords that may be used to test the password storage
   * scheme.
   *
   * @return  A set of passwords that may be used to test the password storage
   *          scheme.
   */
  @DataProvider(name = "testPasswords")
  public Object[][] getTestPasswords()
  {
    return new Object[][]
    {
      new Object[] { ByteString.valueOf("a") },
      new Object[] { ByteString.valueOf("ab") },
      new Object[] { ByteString.valueOf("abc") },
      new Object[] { ByteString.valueOf("abcd") },
      new Object[] { ByteString.valueOf("abcde") },
      new Object[] { ByteString.valueOf("abcdef") },
      new Object[] { ByteString.valueOf("abcdefg") },
      new Object[] { ByteString.valueOf("abcdefgh") },
      new Object[] { ByteString.valueOf("The Quick Brown Fox Jumps Over " +
                                         "The Lazy Dog") },
    };
  }



  /**
   * Creates a test user and authenticates to the server as that user with the
   * SASL PLAIN mechanism using a raw authentication ID (i.e., not prefixed by
   * either "u:" or "dn:").
   *
   * @param  password  The password for the user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testSASLPlainRawAuthID(ByteString password)
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
                   "userPassword: " + password.toString());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ByteStringBuilder saslCredBytes = new ByteStringBuilder();
    saslCredBytes.append((byte)0);
    saslCredBytes.append("test.user");
    saslCredBytes.append((byte)0);
    saslCredBytes.append(password);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(ByteString.empty(), "PLAIN",
                                       saslCredBytes.toByteString());
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Creates a test user and authenticates to the server as that user with the
   * SASL PLAIN mechanism using the "u:" style authentication ID.
   *
   * @param  password  The password for the user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testSASLPlainUColon(ByteString password)
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
                   "userPassword: " + password.toString());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ByteStringBuilder saslCredBytes = new ByteStringBuilder();
    saslCredBytes.append((byte)0);
    saslCredBytes.append("u:test.user");
    saslCredBytes.append((byte)0);
    saslCredBytes.append(password);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(ByteString.empty(), "PLAIN",
                                       saslCredBytes.toByteString());
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Creates a test user and authenticates to the server as that user with the
   * SASL PLAIN mechanism using the "u:" style authentication ID and
   * authorization ID.
   *
   * @param  password  The password for the user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testSASLPlainUColonWithAuthZID(ByteString password)
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
                   "userPassword: " + password.toString());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ByteStringBuilder saslCredBytes = new ByteStringBuilder();
    saslCredBytes.append("u:test.user");
    saslCredBytes.append((byte)0);
    saslCredBytes.append("u:test.user");
    saslCredBytes.append((byte)0);
    saslCredBytes.append(password);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(ByteString.empty(), "PLAIN",
                                       saslCredBytes.toByteString());
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Creates a test user and authenticates to the server as that user with the
   * SASL PLAIN mechanism using the "dn:" style authentication ID.
   *
   * @param  password  The password for the user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testSASLPlainDNColon(ByteString password)
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
                   "userPassword: " + password.toString());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ByteStringBuilder saslCredBytes = new ByteStringBuilder();
    saslCredBytes.append((byte)0);
    saslCredBytes.append("dn:");
    saslCredBytes.append(e.getDN().toString());
    saslCredBytes.append((byte)0);
    saslCredBytes.append(password);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(ByteString.empty(), "PLAIN",
                                       saslCredBytes.toByteString());
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Creates a test user and authenticates to the server as that user with the
   * SASL PLAIN mechanism using the "dn:" style authentication ID and an
   * authorization ID.
   *
   * @param  password  The password for the user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testSASLPlainDNColonWithAuthZID(ByteString password)
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
                   "userPassword: " + password.toString());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ByteStringBuilder saslCredBytes = new ByteStringBuilder();
    saslCredBytes.append("dn:");
    saslCredBytes.append(e.getDN().toString());
    saslCredBytes.append((byte)0);
    saslCredBytes.append("dn:");
    saslCredBytes.append(e.getDN().toString());
    saslCredBytes.append((byte)0);
    saslCredBytes.append(password);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(ByteString.empty(), "PLAIN",
                                       saslCredBytes.toByteString());
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Ensures that SASL PLAIN authentication will work for root users.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSASLPlainAsRoot()
         throws Exception
  {
    ByteString rootCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(ByteString.empty(), "PLAIN",
                                    rootCreds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Ensures that SASL PLAIN authentication works over LDAP as well as via the
   * internal protocol.  The authentication will be performed as the root user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSASLPlainOverLDAP()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)",
      "1.1"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Retrieves sets of invalid credentials that will not succeed when using
   * SASL PLAIN.
   *
   * @return  Sets of invalid credentials that will not work when using SASL
   * PLAIN.
   */
  @DataProvider(name = "invalidCredentials")
  public Object[][] getInvalidCredentials()
  {
    return new Object[][]
    {
      new Object[] { null },
      new Object[] { ByteString.empty() },
      new Object[] { ByteString.valueOf("u:test.user") },
      new Object[] { ByteString.valueOf("password") },
      new Object[] { ByteString.valueOf("\u0000") },
      new Object[] { ByteString.valueOf("\u0000\u0000") },
      new Object[] { ByteString.valueOf("\u0000password") },
      new Object[] { ByteString.valueOf("\u0000\u0000password") },
      new Object[] { ByteString.valueOf("\u0000u:test.user\u0000") },
      new Object[] { ByteString.valueOf("\u0000dn:\u0000password") },
      new Object[] { ByteString.valueOf("\u0000dn:bogus\u0000password") },
      new Object[] { ByteString.valueOf("\u0000dn:cn=no such user" +
                                         "\u0000password") },
      new Object[] { ByteString.valueOf("\u0000u:\u0000password") },
      new Object[] { ByteString.valueOf("\u0000u:nosuchuser\u0000password") },
      new Object[] { ByteString.valueOf("\u0000u:test.user\u0000" +
                                         "wrongpassword") },
    };
  }



  /**
   * Creates a test user and authenticates to the server as that user with the
   * SASL PLAIN mechanism using the "dn:" style authentication ID.
   *
   * @param  saslCredentials  The (invalid) SASL credentials to use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidCredentials")
  public void testInvalidCredentials(ByteString saslCredentials)
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
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(ByteString.empty(), "PLAIN",
                                       saslCredentials);
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
  }



  /**
   * Performs a failed LDAP bind using PLAIN with an authorization ID that
   * contains the DN of an entry that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPBindFailNonexistentAuthzDN()
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
      "userPassword: password",
      "ds-privilege-name: proxied-auth");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=dn:uid=nonexistent,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using PLAIN with an authorization ID that
   * contains a username for an entry that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPBindFailNonexistentAuthzUsername()
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
      "userPassword: password",
      "ds-privilege-name: proxied-auth");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=u:nonexistent",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using PLAIN with an authorization ID that
   * contains a malformed DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPBindFailMalformedAuthzDN()
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
      "userPassword: password",
      "ds-privilege-name: proxied-auth");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=dn:malformed",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }
}

