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
package org.opends.server.tools;

import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.controls.PasswordPolicyRequestControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.AnonymousSASLMechanismHandler;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.ClientException;

/** A set of test cases for the LDAP authentication handler. */
public class LDAPAuthenticationHandlerTestCase
       extends ToolsTestCase
{
   private String hostname;

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
    getFQDN();
  }

  /**
   * Retrieves the names of the supported SASL mechanisms.
   *
   * @return  The names of the supported SASL mechanisms.
   */
  @DataProvider(name = "saslMechanisms")
  public Object[][] getSASLMechanisms()
  {
    return new Object[][]
    {
      new Object[] { "ANONYMOUS" },
      new Object[] { "CRAM-MD5" },
      new Object[] { "DIGEST-MD5" },
      new Object[] { "EXTERNAL" },
      new Object[] { "GSSAPI" },
      new Object[] { "PLAIN" }
    };
  }

  /**
   * Tests the <CODE>getSupportedSASLMechanisms</CODE> method.
   *
   * @param  saslMechanismName  The name of the mechanism to ensure is in the
   *                            returned list.
   */
  @Test(dataProvider = "saslMechanisms")
  public void testGetSupportedSASLMechanisms(String saslMechanismName)
  {
    String[] supportedMechanisms =
         LDAPAuthenticationHandler.getSupportedSASLMechanisms();
    assertNotNull(supportedMechanisms);
    assertEquals(supportedMechanisms.length, 6);
    assertTrue(Arrays.asList(supportedMechanisms).contains(saslMechanismName));
  }

  /**
   * Tests the <CODE>getSASLProperties</CODE> method.
   *
   * @param  saslMechanismName  The name for which to retrieve the applicable properties.
   */
  @Test(dataProvider = "saslMechanisms")
  public void testGetSASLProperties(String saslMechanismName)
  {
    assertNotNull(LDAPAuthenticationHandler.getSASLProperties(saslMechanismName));
  }

  /** Tests the <CODE>getSASLProperties</CODE> method with an unsupported mechanism name. */
  @Test
  public void testGetSASLPropertiesInvalid()
  {
    assertNull(LDAPAuthenticationHandler.getSASLProperties("unsupportedMechanism"));
  }

  /**
   * Tests the <CODE>doSimpleBind</CODE> method with a valid DN and password and
   * with no request controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSimpleBindWithValidDNAndPWNoControls()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.valueOfUtf8("cn=Directory Manager"), ByteString.valueOfUtf8("password"),
          requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSimpleBind</CODE> method with a null DN and password and
   * no request controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSimpleBindWithNullDNAndPWNoControls()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, null, null, requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSimpleBind</CODE> method with an empty DN and password
   * and no request controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSimpleBindWithEmptyDNAndPWNoControls()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.empty(), ByteString.empty(), requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSimpleBind</CODE> method with an valid DN but no
   * password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSimpleBindWithDNButNoPassword()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.valueOfUtf8("cn=Directory Manager"),
                               ByteString.empty(), requestControls,
                               responseControls);
    }
  }

  /**
   * Tests the <CODE>doSimpleBind</CODE> method with an valid DN but an invalid
   * password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSimpleBindWithDNButInvalidPassword()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.valueOfUtf8("cn=Directory Manager"),
                               ByteString.valueOfUtf8("wrongPassword"),
                               requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSimpleBind</CODE> method with the password policy
   * request control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSimpleBindWithPasswordPolicyControl()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    requestControls.add(new PasswordPolicyRequestControl());
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.valueOfUtf8("cn=Directory Manager"), ByteString.valueOfUtf8("password"),
          requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method with a null mechanism.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindNullMechanism()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSASLBind(null, null, null, saslProperties, requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method with an empty mechanism.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindEmptyMechanism()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSASLBind(null, null, "", saslProperties, requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method with an invalid mechanism.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindInvalidMechanism()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSASLBind(null, null, "invalid", saslProperties,
                             requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindAnonymousDisabled()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("trace", newArrayList("testDoSASLBindAnonymousDisabled"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      anonymous(authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindAnonymous()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("trace", newArrayList("testDoSASLBindAnonymous"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      anonymous(authHandler, saslProperties);
    }

    handler.finalizeSASLMechanismHandler();
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server and there is no trace information.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindAnonymousNoTrace()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      anonymous(authHandler, saslProperties);
    }
    handler.finalizeSASLMechanismHandler();
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server and multiple trace values are
   * provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindAnonymousMultivaluedTrace()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("trace",
        newArrayList("testDoSASLBindAnonymousMultivaluedTrace", "aSecondTraceStringWhichIsInvalid"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      anonymous(authHandler, saslProperties);
    }
    finally
    {
      handler.finalizeSASLMechanismHandler();
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server and an invalid SASL property is
   * provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindAnonymousInvalidProperty()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("invalid", newArrayList("testDoSASLBindAnonymousInvalidProperty"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      anonymous(authHandler, saslProperties);
    }
    finally
    {
      handler.finalizeSASLMechanismHandler();
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server and the request includes the
   * password policy request control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindAnonymousWithPasswordPolicyControl()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    requestControls.add(new PasswordPolicyRequestControl());
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("trace", newArrayList("testDoSASLBindAnonymous"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSASLBind(ByteString.empty(), ByteString.empty(), "ANONYMOUS", saslProperties, requestControls,
          responseControls);
    }
    handler.finalizeSASLMechanismHandler();
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which CRAM-MD5
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindCRAMMD5Disabled()
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

    SASLMechanismHandler<?> cramMD5Handler =
         DirectoryServer.getSASLMechanismHandler("CRAM-MD5");
    DirectoryServer.deregisterSASLMechanismHandler("CRAM-MD5");

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    try
    {
      cramMd5SaslBind(saslProperties);
    }
    finally
    {
      DirectoryServer.registerSASLMechanismHandler("CRAM-MD5", cramMD5Handler);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which CRAM-MD5
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindCRAMMD5()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which an authID was provided that doesn't map to any user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindCRAMMD5InvalidAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which an empty authID was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindCRAMMD5EmptyAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList(""));

    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the provided password was incorrect.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindCRAMMD5InvalidPassword()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("CRAM-MD5", "invalidPassword", authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the specified user doesn't have a reversible password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindCRAMMD5NoReversiblePassword()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the provided SASL properties were null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindCRAMMD5NullProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = null;

    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the provided SASL properties were empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindCRAMMD5EmptyProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which multiple authID values were provided
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindCRAMMD5MultipleAuthIDs()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test", "u:test.user"));

    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which an invalid SASL property was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindCRAMMD5InvalidSASLProperty()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("invalid", newArrayList("foo"));

    cramMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which CRAM-MD5
   * authentication is enabled in the server and the password policy request
   * control is used.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindCRAMMD5WithPasswordPolicyControl()
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

    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    requestControls.add(new PasswordPolicyRequestControl());
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");

      authHandler.doSASLBind(ByteString.empty(), ByteString.valueOfUtf8("password"), "CRAM-MD5", saslProperties,
          requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindDigestMD5Disabled()
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

    SASLMechanismHandler<?> digestMD5Handler =
         DirectoryServer.getSASLMechanismHandler("DIGEST-MD5");
    DirectoryServer.deregisterSASLMechanismHandler("DIGEST-MD5");

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      digestMD5(authHandler, saslProperties);
    }
    finally
    {
      DirectoryServer.registerSASLMechanismHandler("DIGEST-MD5", digestMD5Handler);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindDigestMD5()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, hostname);
      digestMD5(authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is enabled in the server and an authz ID was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindDigestMD5WithAuthzID()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, hostname);
      digestMD5(authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties are <CODE>null</CODE>.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5NullProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = null;

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties are empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5EmptyProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an invalid property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5InvalidProperty()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("invalid", newArrayList("foo"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple values for the authID property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5MultipleAuthIDs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    List<String> propList = newArrayList("dn:uid=test.user,o=test");
    propList.add("u:test.user");
    saslProperties.put("authid", propList);

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an empty authID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5MEmptyAuthID()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList(""));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple values for the realm property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5MultipleRealms()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test", "dc=example,dc=com"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain a valid quality of protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindDigestMD5ValidQoP()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("qop", newArrayList("auth"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, hostname);
      digestMD5(authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain the unsupported integrity quality of
   * protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5UnsupportedQoPAuthInt()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test"));
    saslProperties.put("qop", newArrayList("auth-int"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain the unsupported confidentiality quality
   * of protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5UnsupportedQoPAuthConf()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test"));
    saslProperties.put("qop", newArrayList("auth-conf"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an invalid quality of protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5InvalidQoP()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test"));
    saslProperties.put("qop", newArrayList("invalid"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple quality of protection values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5MultipleQoPs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test"));
    saslProperties.put("qop", newArrayList("auth", "auth-int", "auth-conf"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple digest URIs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5MultipleDigestURIs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test"));
    saslProperties.put("digest-uri", newArrayList("ldap/value1", "ldap/value2"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple authorization IDs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindDigestMD5MultipleAuthzIDs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("realm", newArrayList("o=test"));
    saslProperties.put("authzid", newArrayList("dn:uid=test.user,o=test", "u:test.user"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an invalid auth ID in the DN form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindDigestMD5InvalidAuthDN()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:invalid"));
    saslProperties.put("realm", newArrayList("o=test"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an auth ID that doesn't map to any user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindDigestMD5NonExistentAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:nosuchuser"));
    saslProperties.put("realm", newArrayList("o=test"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which an invalid
   * password was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindDigestMD5InvalidPassword()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:nosuchuser"));
    saslProperties.put("realm", newArrayList("o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("DIGEST-MD5", "wrongPassword", authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the target
   * user does not have a reversible password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindDigestMD5NoReversiblePassword()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:nosuchuser"));
    saslProperties.put("realm", newArrayList("o=test"));

    digestMd5SaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is enabled in the server and the password policy request
   * control is included.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindDigestMD5WithPasswordPolicyControl()
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

    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    requestControls.add(new PasswordPolicyRequestControl());
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, hostname);
      authHandler.doSASLBind(ByteString.empty(), ByteString.valueOfUtf8("password"), "DIGEST-MD5", saslProperties,
          requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which EXTERNAL
   * authentication is not enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindExternalDisabled()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    SASLMechanismHandler<?> externalHandler =
         DirectoryServer.getSASLMechanismHandler("EXTERNAL");
    DirectoryServer.deregisterSASLMechanismHandler("EXTERNAL");

    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert",
                 trustStorePath, "password");

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    try (Socket s = factory.createSocket("127.0.0.1", TestCaseUtils.getServerLdapsPort()))
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("EXTERNAL", null, authHandler, saslProperties);
    }
    finally
    {
      DirectoryServer.registerSASLMechanismHandler("EXTERNAL", externalHandler);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which EXTERNAL
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindExternal()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");

    try (Socket s = factory.createSocket("127.0.0.1", TestCaseUtils.getServerLdapsPort()))
    {
      Map<String, List<String>> saslProperties = new LinkedHashMap<>();
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("EXTERNAL", null, authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in the EXTERNAL SASL
   * properties were not empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindExternalInvalidProperties()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    SASLMechanismHandler<?> externalHandler =
         DirectoryServer.getSASLMechanismHandler("EXTERNAL");
    DirectoryServer.deregisterSASLMechanismHandler("EXTERNAL");

    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("invalid", newArrayList("foo"));

    try (Socket s = factory.createSocket("127.0.0.1", TestCaseUtils.getServerLdapsPort()))
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("EXTERNAL", null, authHandler, saslProperties);
    }
    finally
    {
      DirectoryServer.registerSASLMechanismHandler("EXTERNAL", externalHandler);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which EXTERNAL
   * authentication is enabled in the server and the password policy request
   * control is included.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindExternalWithPasswordPolicy()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");

    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    requestControls.add(new PasswordPolicyRequestControl());
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    try (Socket s = factory.createSocket("127.0.0.1", TestCaseUtils.getServerLdapsPort()))
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("EXTERNAL", null, authHandler, saslProperties);
      authHandler.doSASLBind(ByteString.empty(), null, "EXTERNAL", saslProperties, requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties list was null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPINullProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = null;

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties list was empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIEmptyProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    gssapiSaslBind(saslProperties);
  }

  private Socket newSocket() throws UnknownHostException, IOException
  {
    return new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
  }

  private LDAPAuthenticationHandler newLDAPAuthenticationHandler(Socket s, String hostName2) throws IOException
  {
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    AtomicInteger messageID = new AtomicInteger(1);
    return new LDAPAuthenticationHandler(r, w, hostName2, messageID);
  }

  private void anonymous(LDAPAuthenticationHandler authHandler, Map<String, List<String>> saslProperties)
      throws ClientException, LDAPException
  {
    doSASLBind("ANONYMOUS", "", authHandler, saslProperties);
  }

  private void gssapi(LDAPAuthenticationHandler authHandler, Map<String, List<String>> saslProperties)
      throws ClientException, LDAPException
  {
    doSASLBind("GSSAPI", "", authHandler, saslProperties);
  }

  private void cramMD5(LDAPAuthenticationHandler authHandler, Map<String, List<String>> saslProperties)
      throws ClientException, LDAPException
  {
    doSASLBind("CRAM-MD5", "password", authHandler, saslProperties);
  }

  private void plain(LDAPAuthenticationHandler authHandler, Map<String, List<String>> saslProperties)
      throws ClientException, LDAPException
  {
    doSASLBind("PLAIN", "password", authHandler, saslProperties);
  }

  private void digestMD5(LDAPAuthenticationHandler authHandler, Map<String, List<String>> saslProperties)
      throws ClientException, LDAPException
  {
    doSASLBind("DIGEST-MD5", "password", authHandler, saslProperties);
  }

  private void doSASLBind(String mechanism, String bindPassword, LDAPAuthenticationHandler authHandler,
      Map<String, List<String>> saslProperties) throws ClientException, LDAPException
  {
    ByteString bindPwd = bindPassword != null ? ByteString.valueOfUtf8(bindPassword) : null;
    authHandler.doSASLBind(ByteString.empty(), bindPwd, mechanism, saslProperties,
        new ArrayList<Control>(), new ArrayList<Control>());
  }

  private void plainSaslBind(Map<String, List<String>> saslProperties) throws Exception
  {
    try (Socket s = newSocket())
    {
      plain(newLDAPAuthenticationHandler(s, "localhost"), saslProperties);
    }
  }

  private void cramMd5SaslBind(Map<String, List<String>> saslProperties) throws Exception
  {
    try (Socket s = newSocket())
    {
      cramMD5(newLDAPAuthenticationHandler(s, "localhost"), saslProperties);
    }
  }

  private void digestMd5SaslBind(Map<String, List<String>> saslProperties) throws Exception
  {
    try (Socket s = newSocket())
    {
      digestMD5(newLDAPAuthenticationHandler(s, "localhost"), saslProperties);
    }
  }

  private void gssapiSaslBind(Map<String, List<String>> saslProperties) throws Exception
  {
    try (Socket s = newSocket())
    {
      gssapi(newLDAPAuthenticationHandler(s, "localhost"), saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has a zero-length auth ID value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIEmptyAuthID()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList(""));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple authID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIMultipleAuthIDs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user", "dn:uid=test.user,o=test"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple authzID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIMultipleAuthzIDs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("authzid", newArrayList("u:test.user", "dn:uid=test.user,o=test"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple KDC values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIMultipleKDCs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("kdc", newArrayList("kdc1", "kdc2"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple quality of protection values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIMultipleQoPs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("qop", newArrayList("auth", "auth-int", "auth-conf"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an unsupported quality of protection value of
   * auth-int.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIUnsupportedQoPAuthInt()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("qop", newArrayList("auth-int"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an unsupported quality of protection value of
   * auth-conf.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIUnsupportedQoPAuthConf()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("qop", newArrayList("auth-conf"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an invalid quality of protection value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIInvalidQoP()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("qop", newArrayList("invalid"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple realm values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIMultipleRealms()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("realm", newArrayList("realm1", "realm2"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an invalid property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPIInvalidProperty()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("u:test.user"));
    saslProperties.put("invalid", newArrayList("foo"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties isn't empty but doesn't contain an auth ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindGSSAPINoAuthID()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("qop", newArrayList("auth"));

    gssapiSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which PLAIN
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindPlainDisabled()
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

    SASLMechanismHandler<?> plainHandler =
         DirectoryServer.getSASLMechanismHandler("PLAIN");
    DirectoryServer.deregisterSASLMechanismHandler("PLAIN");

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      plain(authHandler, saslProperties);
    }
    finally
    {
      DirectoryServer.registerSASLMechanismHandler("PLAIN", plainHandler);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which PLAIN
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindPlain()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      plain(authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties are null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindPlainNullProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = null;

    plainSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties are empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindPlainEmptyProperties()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();

    plainSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties have multiple auth ID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindPlainMultipleAuthIDs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test", "u:test.user"));

    plainSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties have multiple auth ID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindPlainZeroLengthAuthID()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList(""));

    plainSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties have multiple authzID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindPlainMultipleAuthzIDs()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("authzid", newArrayList("dn:uid=test.user,o=test", "u:test.user"));

    plainSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties contains an invalid property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindPlainInvalidProperty()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    saslProperties.put("invalid", newArrayList("foo"));

    plainSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties does not contain an auth ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = ClientException.class)
  public void testDoSASLBindPlainNoAuthID()
         throws Exception
  {
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authzid", newArrayList("dn:uid=test.user,o=test"));

    plainSaslBind(saslProperties);
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for PLAIN authentication in which
   * the target user does not exist in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindPlainNonExistentUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (Socket s = newSocket())
    {
      Map<String, List<String>> saslProperties = new LinkedHashMap<>();
      saslProperties.put("authid", newArrayList("dn:uid=does.not.exist,o=test"));

      plain(newLDAPAuthenticationHandler(s, "localhost"), saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for PLAIN authentication in which the wrong password
   * has been provided for the target user.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDoSASLBindPlainWrongPassword()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=does.not.exist,o=test"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("PLAIN", "wrongPassword", authHandler, saslProperties);
    }
  }

  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which PLAIN
   * authentication is enabled in the server and the password policy request
   * control is included.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoSASLBindPlainWithPasswordPolicy()
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

    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    requestControls.add(new PasswordPolicyRequestControl());
    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSASLBind(ByteString.empty(), ByteString.valueOfUtf8("password"), "PLAIN", saslProperties,
          requestControls, responseControls);
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for an
   * unauthenticated client connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentityUnauthenticated()
         throws Exception
  {
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      assertNull(authHandler.requestAuthorizationIdentity());
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a simple anonymous bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentitySimpleAnonymous()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.empty(), ByteString.empty(), requestControls, responseControls);
      assertNull(authHandler.requestAuthorizationIdentity());
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a simple bind as a root user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentitySimpleRootUser()
         throws Exception
  {
    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.valueOfUtf8("cn=Directory Manager"), ByteString.valueOfUtf8("password"),
          requestControls, responseControls);
      assertNotNull(authHandler.requestAuthorizationIdentity());
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a simple bind as a normal user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentitySimpleTestUser()
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

    List<Control> requestControls = new ArrayList<>();
    List<Control> responseControls = new ArrayList<>();
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      authHandler.doSimpleBind(3, ByteString.valueOfUtf8("uid=test.user,o=test"), ByteString.valueOfUtf8("password"),
          requestControls, responseControls);
      assertNotNull(authHandler.requestAuthorizationIdentity());
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a SASL ANONYMOUS bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentitySASLAnonymous()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("trace", newArrayList("testDoSASLBindAnonymous"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      anonymous(authHandler, saslProperties);
      assertNull(authHandler.requestAuthorizationIdentity());
    }
    handler.finalizeSASLMechanismHandler();
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a CRAM-MD5 bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentityCRAMMD5()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      cramMD5(authHandler, saslProperties);
      assertNotNull(authHandler.requestAuthorizationIdentity());
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a DIGEST-MD5 bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentityDigestMD5()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));

    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, hostname);
      digestMD5(authHandler, saslProperties);
      assertNotNull(authHandler.requestAuthorizationIdentity());
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after an EXTERNAL bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentityExternal()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    String keyStorePath   = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    try (Socket s = factory.createSocket("127.0.0.1", TestCaseUtils.getServerLdapsPort()))
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      doSASLBind("EXTERNAL", null, authHandler, saslProperties);
      assertNotNull(authHandler.requestAuthorizationIdentity());
    }
  }

  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a PLAIN bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestAuthorizationIdentityPlain()
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

    Map<String, List<String>> saslProperties = new LinkedHashMap<>();
    saslProperties.put("authid", newArrayList("dn:uid=test.user,o=test"));
    try (Socket s = newSocket())
    {
      LDAPAuthenticationHandler authHandler = newLDAPAuthenticationHandler(s, "localhost");
      plain(authHandler, saslProperties);
      assertNotNull(authHandler.requestAuthorizationIdentity());
    }
  }

  private void getFQDN() {
      try {
         this.hostname = InetAddress.getLocalHost().getCanonicalHostName();
      } catch(UnknownHostException ex) {
         this.hostname = "localhost";
      }
  }
}
