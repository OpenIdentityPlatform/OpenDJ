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
package org.opends.server.extensions;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchFilter;

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
         conn.processSearch(new ASN1OctetString(""), SearchScope.BASE_OBJECT,
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
      new Object[] { new ASN1OctetString("a") },
      new Object[] { new ASN1OctetString("ab") },
      new Object[] { new ASN1OctetString("abc") },
      new Object[] { new ASN1OctetString("abcd") },
      new Object[] { new ASN1OctetString("abcde") },
      new Object[] { new ASN1OctetString("abcdef") },
      new Object[] { new ASN1OctetString("abcdefg") },
      new Object[] { new ASN1OctetString("abcdefgh") },
      new Object[] { new ASN1OctetString("The Quick Brown Fox Jumps Over " +
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
                   "userPassword: " + password.stringValue());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    byte[] saslCredBytes = new byte[11 + password.value().length];
    System.arraycopy("test.user".getBytes("UTF-8"), 0, saslCredBytes, 1, 9);
    System.arraycopy(password.value(), 0, saslCredBytes, 11,
                     password.value().length);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(new ASN1OctetString(), "PLAIN",
                                       new ASN1OctetString(saslCredBytes));
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
                   "userPassword: " + password.stringValue());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    byte[] saslCredBytes = new byte[13 + password.value().length];
    System.arraycopy("u:test.user".getBytes("UTF-8"), 0, saslCredBytes, 1, 11);
    System.arraycopy(password.value(), 0, saslCredBytes, 13,
                     password.value().length);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(new ASN1OctetString(), "PLAIN",
                                       new ASN1OctetString(saslCredBytes));
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
                   "userPassword: " + password.stringValue());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    byte[] saslCredBytes = new byte[24 + password.value().length];
    System.arraycopy("u:test.user".getBytes("UTF-8"), 0, saslCredBytes, 0, 11);
    System.arraycopy("u:test.user".getBytes("UTF-8"), 0, saslCredBytes, 12, 11);
    System.arraycopy(password.value(), 0, saslCredBytes, 24,
                     password.value().length);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(new ASN1OctetString(), "PLAIN",
                                       new ASN1OctetString(saslCredBytes));
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
                   "userPassword: " + password.stringValue());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    byte[] dnBytes = e.getDN().toString().getBytes("UTF-8");
    byte[] saslCredBytes =
         new byte[5 + dnBytes.length + password.value().length];
    System.arraycopy("dn:".getBytes("UTF-8"), 0, saslCredBytes, 1, 3);
    System.arraycopy(dnBytes, 0, saslCredBytes, 4, dnBytes.length);
    System.arraycopy(password.value(), 0, saslCredBytes, 5 + dnBytes.length,
                     password.value().length);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(new ASN1OctetString(), "PLAIN",
                                       new ASN1OctetString(saslCredBytes));
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
                   "userPassword: " + password.stringValue());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    byte[] dnBytes = ("dn:" + e.getDN().toString()).getBytes("UTF-8");
    byte[] saslCredBytes =
         new byte[2 + (2*dnBytes.length) + password.value().length];
    System.arraycopy(dnBytes, 0, saslCredBytes, 0, dnBytes.length);
    System.arraycopy(dnBytes, 0, saslCredBytes, dnBytes.length+1,
                     dnBytes.length);
    System.arraycopy(password.value(), 0, saslCredBytes,
                     (2*dnBytes.length + 2), password.value().length);
    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(new ASN1OctetString(), "PLAIN",
                                       new ASN1OctetString(saslCredBytes));
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
    ASN1OctetString rootCreds =
         new ASN1OctetString("\u0000dn:cn=Directory Manager\u0000password");

    InternalClientConnection anonymousConn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         anonymousConn.processSASLBind(new ASN1OctetString(), "PLAIN",
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
      new Object[] { new ASN1OctetString() },
      new Object[] { new ASN1OctetString("u:test.user") },
      new Object[] { new ASN1OctetString("password") },
      new Object[] { new ASN1OctetString("\u0000") },
      new Object[] { new ASN1OctetString("\u0000\u0000") },
      new Object[] { new ASN1OctetString("\u0000password") },
      new Object[] { new ASN1OctetString("\u0000\u0000password") },
      new Object[] { new ASN1OctetString("\u0000u:test.user\u0000") },
      new Object[] { new ASN1OctetString("\u0000dn:\u0000password") },
      new Object[] { new ASN1OctetString("\u0000dn:bogus\u0000password") },
      new Object[] { new ASN1OctetString("\u0000dn:cn=no such user" +
                                         "\u0000password") },
      new Object[] { new ASN1OctetString("\u0000u:\u0000password") },
      new Object[] { new ASN1OctetString("\u0000u:nosuchuser\u0000password") },
      new Object[] { new ASN1OctetString("\u0000u:test.user\u0000" +
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
  public void testInvalidCredentials(ASN1OctetString saslCredentials)
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
         anonymousConn.processSASLBind(new ASN1OctetString(), "PLAIN",
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

