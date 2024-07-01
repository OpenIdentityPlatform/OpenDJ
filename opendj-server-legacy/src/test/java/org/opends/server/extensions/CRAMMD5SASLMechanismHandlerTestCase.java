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
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.CramMD5SASLMechanismHandlerCfgDefn;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import com.forgerock.opendj.ldap.tools.LDAPSearch;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/** A set of test cases for the CRAM-MD5 SASL mechanism handler. */
@SuppressWarnings("javadoc")
public class CRAMMD5SASLMechanismHandlerTestCase
       extends ExtensionsTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs() throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=CRAM-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-cram-md5-sasl-mechanism-handler",
         "cn: CRAM-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CRAMMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "",
         "dn: cn=CRAM-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-cram-md5-sasl-mechanism-handler",
         "cn: CRAM-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CRAMMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: not a DN",
         "",
         "dn: cn=CRAM-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-cram-md5-sasl-mechanism-handler",
         "cn: CRAM-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CRAMMD5SASLMechanismHandler",
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
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    InitializationUtils.initializeSASLMechanismHandler(
        new CRAMMD5SASLMechanismHandler(), e, CramMD5SASLMechanismHandlerCfgDefn.getInstance());
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
    CRAMMD5SASLMechanismHandler handler =
         (CRAMMD5SASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler(SASL_MECHANISM_CRAM_MD5);

    assertTrue(handler.isPasswordBased(SASL_MECHANISM_CRAM_MD5));
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
    CRAMMD5SASLMechanismHandler handler =
         (CRAMMD5SASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler(SASL_MECHANISM_CRAM_MD5);

    assertTrue(handler.isSecure(SASL_MECHANISM_CRAM_MD5));
  }



  /**
   * Performs a successful LDAP bind using CRAM-MD5 using the u: form of the
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=u:test.user",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
  }



  /**
   * Performs a successful LDAP bind using CRAM-MD5 using the dn: form of the
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
  }



  /**
   * Performs a successful LDAP bind using CRAM-MD5 using the dn: form of the
   * authentication ID using a long password (longer than 64 bytes).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindSuccessWithDNAndLongPassword()
         throws Exception
  {
    String password =
         "reallyreallyreallyreallyreallyreallyreallyreallyreallylongpassword";

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
         "userPassword: " + password,
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-w", password,
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the u: form of the
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=u:test.user",
      "-w", "wrongpassword",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the dn: form of the
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-w", "wrongpassword",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the u: form of the
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=u:test.user",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the dn: form of the
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the dn: form of the
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:invaliddn",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the dn: form of the
   * authentication ID with the DN of a user that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNoSuchUser()
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

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:uid=doesntexist,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the dn: form of the
   * authentication ID with the null DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNullDN()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:",
      "-w", "",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Performs a failed LDAP bind using CRAM-MD5 using the dn: form of the
   * authentication ID with the root DN (which has a stored password that's not
   * reversible).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailIrreversiblePasswordWithRootDN()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=CRAM-MD5",
      "-o", "authid=dn:cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Verifies that the server will reject a CRAM-MD5 bind in which the first
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
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_CRAM_MD5,
                              ByteString.valueOfUtf8("invalid"));
    assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Verifies that the server will reject a CRAM-MD5 bind with malformed
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
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_CRAM_MD5, null);
    assertEquals(bindOperation.getResultCode(),
                 ResultCode.SASL_BIND_IN_PROGRESS);

    bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_CRAM_MD5,
                              ByteString.valueOfUtf8("malformed"));
    assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Verifies that the server will reject a CRAM-MD5 bind with credentials
   * containing a malformed digest.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMalformedDigest()
         throws Exception
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_CRAM_MD5, null);
    assertEquals(bindOperation.getResultCode(),
                 ResultCode.SASL_BIND_IN_PROGRESS);

    ByteString creds =
         ByteString.valueOfUtf8("dn:cn=Directory Manager malformeddigest");
    bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_CRAM_MD5, creds);
    assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Verifies that the server will reject a CRAM-MD5 bind with credentials
   * containing a malformed digest with the correct length but not only hex
   * characters.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMalformedDigestWithCorrectLength()
         throws Exception
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_CRAM_MD5, null);
    assertEquals(bindOperation.getResultCode(),
                 ResultCode.SASL_BIND_IN_PROGRESS);

    ByteString creds =
         ByteString.valueOfUtf8("dn:cn=Directory Manager " +
                          "malformedcredswiththerightlength");
    bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_CRAM_MD5, creds);
    assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }
}
