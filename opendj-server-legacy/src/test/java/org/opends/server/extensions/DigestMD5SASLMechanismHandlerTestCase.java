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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.DigestMD5SASLMechanismHandlerCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import com.forgerock.opendj.ldap.tools.LDAPSearch;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.util.Args;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/** A set of test cases for the DIGEST-MD5 SASL mechanism handler. */
public class DigestMD5SASLMechanismHandlerTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--set", "server-fqdn:" + "127.0.0.1");
  }


  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--remove", "server-fqdn:" + "127.0.0.1");
  }


  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-digest-md5-sasl-mechanism-handler",
         "cn: DIGEST-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DigestMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "",
         "dn: cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-digest-md5-sasl-mechanism-handler",
         "cn: DIGEST-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DigestMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: not a DN",
         "",
         "dn: cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-digest-md5-sasl-mechanism-handler",
         "cn: DIGEST-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DigestMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: cn=does not exist");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the handler with invalid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    InitializationUtils.initializeSASLMechanismHandler(
        new DigestMD5SASLMechanismHandler(), e, DigestMD5SASLMechanismHandlerCfgDefn.getInstance());
  }



  /**
   * Tests the <CODE>isPasswordBased</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testIsPasswordBased()
         throws Exception
  {
    DigestMD5SASLMechanismHandler handler =
         (DigestMD5SASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5);

    assertTrue(handler.isPasswordBased(SASL_MECHANISM_DIGEST_MD5));
  }



  /**
   * Tests the <CODE>isSecure</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testIsSecure()
         throws Exception
  {
    DigestMD5SASLMechanismHandler handler =
         (DigestMD5SASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5);

    assertTrue(handler.isSecure(SASL_MECHANISM_DIGEST_MD5));
  }



  /**
   * Performs a successful LDAP bind using DIGEST-MD5 using the u: form of the
   * authentication ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindSuccessWithUID()
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
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args = args("authid=u:test.user", "authzid=u:test.user", "password");
    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
  }



  /**
   * Performs a successful LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindSuccessWithDN()
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
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args = args("authid=dn:uid=test.user,o=test", "authzid=dn:uid=test.user,o=test", "password");
    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the u: form of the
   * authentication ID with the wrong password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailWrongPasswordWithUID()
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
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args = args("authid=u:test.user", "authzid=u:test.user", "wrongpassword");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the wrong password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailWrongPasswordWithDN()
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
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args = args("authid=dn:uid=test.user,o=test", "authzid=dn:uid=test.user,o=test", "wrongpassword");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the u: form of the
   * authentication ID with a stored password that's not reversible.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailIrreversiblePasswordWithUID()
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
         "userPassword: password");

    String[] args = args("authid=u:test.user", "authzid=u:test.user", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with a stored password that's not reversible.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailIrreversiblePasswordWithDN()
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
         "userPassword: password");

    String[] args = args("authid=dn:uid=test.user,o=test", "authzid=dn:uid=test.user,o=test", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with an invalid DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailInvalidDN()
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
         "userPassword: password");

    String[] args = args("authid=dn:invaliddn", "authzid=dn:invaliddn", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the DN of a user that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNoSuchUserForUID()
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
         "userPassword: password");

    String[] args = args("authid=u:doesntexist", "authzid=u:doesntexist", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the DN of a user that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNoSuchUserForDN()
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
         "userPassword: password");

    String[] args = args("authid=dn:uid=doesntexist,o=test", "authzid=dn:uid=doesntexist,o=test", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with an empty UID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailEmptyUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args = args("authid=u:", "authzid=u:", "");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the null DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNullDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args = args("authid=dn:", "authzid=dn:", "");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an empty authentication
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailEmptyAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args = args("authid=", "authzid=", "");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an empty authorization
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailEmptyAuthzID()
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
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args = args("authid=dn:uid=test.user,o=test", "authzid=", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the root DN (which has a stored password that's not
   * reversible).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailIrreversiblePasswordWithRootDN()
         throws Exception
  {
    String[] args = args("authid=dn:cn=Directory Manager", "authzid=dn:cn=Directory Manager", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a successful LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the root DN (which has a stored password that is
   * reversible).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulBindReversiblePasswordWithRootDN()
         throws Exception
  {
    Entry e = TestCaseUtils.addEntry(
         "dn: cn=Second Root DN,cn=Root DNs,cn=config",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "objectClass: ds-cfg-root-dn-user",
         "givenName: Second",
         "sn: Root DN",
         "cn: Second Root DN",
         "ds-cfg-alternate-bind-dn: cn=Second Root DN",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args = args("authid=dn:cn=Second Root DN", "authzid=dn:cn=Second Root DN", "password");
    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);


    DeleteOperation deleteOperation = getRootConnection().processDelete(e.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an authorization ID that
   * contains the DN of an entry that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args = args("authid=dn:uid=test.user,o=test", "authzid=dn:uid=nonexistent,o=test", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an authorization ID that
   * contains a username for an entry that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args = args("authid=dn:uid=test.user,o=test", "authzid=u:nonexistent", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an authorization ID that
   * contains a malformed DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args = args("authid=dn:uid=test.user,o=test", "authzid=dn:malformed", "password");
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }

  private String[] args(String authid, String authzid, String password)
  {
    return new Args()
        .add("--noPropertiesFile")
        .add("-h", "127.0.0.1")
        .add("-p", TestCaseUtils.getServerLdapPort())
        .add("-o", "mech=DIGEST-MD5")
        .add("-o", authid)
        .add("-o", authzid)
        .add("-w", password)
        .add("-b", "")
        .add("-s", "base")
        .add("(objectClass=*)")
        .toArray();
  }

  /**
   * Verifies that the server will reject a DIGEST-MD5 bind in which the first
   * message contains SASL credentials (which isn't allowed).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testOutOfSequenceBind()
         throws Exception
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_DIGEST_MD5,
                              ByteString.valueOfUtf8("invalid"));
    assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Verifies that the server will reject a DIGEST-MD5 bind with malformed
   * credentials.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMalformedCredentials()
         throws Exception
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_DIGEST_MD5, null);
    assertEquals(bindOperation.getResultCode(), ResultCode.SASL_BIND_IN_PROGRESS);

    bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_DIGEST_MD5,
                              ByteString.valueOfUtf8("malformed"));
    assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }
}
