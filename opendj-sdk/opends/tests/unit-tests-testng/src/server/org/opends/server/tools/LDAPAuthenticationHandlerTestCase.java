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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.io.File;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.controls.PasswordPolicyRequestControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.AnonymousSASLMechanismHandler;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of test cases for the LDAP authentication handler.
 */
public class LDAPAuthenticationHandlerTestCase
       extends ToolsTestCase
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
    assertTrue(supportedMechanisms.length == 6);

    boolean found = false;
    for (String name : supportedMechanisms)
    {
      found = name.equals(saslMechanismName);
      if (found)
      {
        break;
      }
    }

    assertTrue(found);
  }



  /**
   * Tests the <CODE>getSASLProperties</CODE> method.
   *
   * @param  saslMechanismName  The name for which to retrieve the applicable
   *                            properties.
   */
  @Test(dataProvider = "saslMechanisms")
  public void testGetSASLProperties(String saslMechanismName)
  {
    LinkedHashMap<String,String> properties =
         LDAPAuthenticationHandler.getSASLProperties(saslMechanismName);

    assertNotNull(properties);
  }



  /**
   * Tests the <CODE>getSASLProperties</CODE> method with an unsupported
   * mechanism name.
   */
  @Test()
  public void testGetSASLPropertiesInvlaid()
  {
    LinkedHashMap<String,String> properties =
         LDAPAuthenticationHandler.getSASLProperties("unsupportedMechanism");

    assertNull(properties);
  }



  /**
   * Tests the <CODE>doSimpleBind</CODE> method with a valid DN and password and
   * with no request controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSimpleBindWithValidDNAndPWNoControls()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSimpleBind(3, new ASN1OctetString("cn=Directory Manager"),
                             new ASN1OctetString("password"), requestControls,
                             responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSimpleBind</CODE> method with a null DN and password and
   * no request controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSimpleBindWithNullDNAndPWNoControls()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSimpleBind(3, null, null, requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSimpleBind</CODE> method with an empty DN and password
   * and no request controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSimpleBindWithEmptyDNAndPWNoControls()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSimpleBind(3, new ASN1OctetString(), new ASN1OctetString(),
                             requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSimpleBind</CODE> method with an valid DN but no
   * password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSimpleBindWithDNButNoPassword()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSimpleBind(3, new ASN1OctetString("cn=Directory Manager"),
                               new ASN1OctetString(), requestControls,
                               responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSimpleBind</CODE> method with an valid DN but an invalid
   * password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSimpleBindWithDNButInvalidPassword()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSimpleBind(3, new ASN1OctetString("cn=Directory Manager"),
                               new ASN1OctetString("wrongPassword"),
                               requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSimpleBind</CODE> method with the password policy
   * request control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSimpleBindWithPasswordPolicyControl()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    requestControls.add(new LDAPControl(new PasswordPolicyRequestControl()));

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSimpleBind(3, new ASN1OctetString("cn=Directory Manager"),
                             new ASN1OctetString("password"),
                             requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method with a null mechanism.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindNullMechanism()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(null, null, null, saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method with an empty mechanism.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindEmptyMechanism()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(null, null, "", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method with an invalid mechanism.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindInvalidMechanism()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(null, null, "invalid", saslProperties,
                             requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindAnonymousDisabled()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("testDoSASLBindAnonymousDisabled");
    saslProperties.put("trace", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "ANONYMOUS", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindAnonymous()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("testDoSASLBindAnonymous");
    saslProperties.put("trace", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                           "ANONYMOUS", saslProperties, requestControls,
                           responseControls);
    s.close();
    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server and there is no trace information.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindAnonymousNoTrace()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                           "ANONYMOUS", saslProperties, requestControls,
                           responseControls);
    s.close();
    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which ANONYMOUS
   * authentication is enabled in the server and multiple trace values are
   * provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindAnonymousMultivaluedTrace()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("testDoSASLBindAnonymousMultivaluedTrace");
    propList.add("aSecondTraceStringWhichIsInvalid");
    saslProperties.put("trace", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "ANONYMOUS", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
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
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindAnonymousInvalidProperty()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("testDoSASLBindAnonymousInvalidProperty");
    saslProperties.put("invalid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "ANONYMOUS", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
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
  @Test()
  public void testDoSASLBindAnonymousWithPasswordPolicyControl()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    requestControls.add(new LDAPControl(new PasswordPolicyRequestControl()));

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("testDoSASLBindAnonymous");
    saslProperties.put("trace", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                           "ANONYMOUS", saslProperties, requestControls,
                           responseControls);
    s.close();
    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which CRAM-MD5
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindCRAMMD5Disabled()
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


    SASLMechanismHandler cramMD5Handler =
         DirectoryServer.getSASLMechanismHandler("CRAM-MD5");
    DirectoryServer.deregisterSASLMechanismHandler("CRAM-MD5");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
      DirectoryServer.registerSASLMechanismHandler("CRAM-MD5", cramMD5Handler);
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which CRAM-MD5
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindCRAMMD5()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "CRAM-MD5", saslProperties, requestControls,
                           responseControls);
    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which an authID was provided that doesn't map to any user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindCRAMMD5InvalidAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which an empty authID was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindCRAMMD5EmptyAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the provided password was incorrect.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindCRAMMD5InvalidPassword()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("invalidPassword"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the specified user doesn't have a reversible password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindCRAMMD5NoReversiblePassword()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the provided SASL properties were null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindCRAMMD5NullProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties = null;

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which the provided SASL properties were empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindCRAMMD5EmptyProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which multiple authID values were provided
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindCRAMMD5MultipleAuthIDs()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    propList.add("u:test.user");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method using CRAM-MD5 for the case in
   * which an invalid SASL property was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindCRAMMD5InvalidSASLProperty()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("foo");
    saslProperties.put("invalid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "CRAM-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which CRAM-MD5
   * authentication is enabled in the server and the password policy request
   * control is used.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindCRAMMD5WithPasswordPolicyControl()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    requestControls.add(new LDAPControl(new PasswordPolicyRequestControl()));

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "CRAM-MD5", saslProperties, requestControls,
                           responseControls);
    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindDigestMD5Disabled()
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


    SASLMechanismHandler digestMD5Handler =
         DirectoryServer.getSASLMechanismHandler("DIGEST-MD5");
    DirectoryServer.deregisterSASLMechanismHandler("DIGEST-MD5");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
      DirectoryServer.registerSASLMechanismHandler("DIGEST-MD5",
                                                   digestMD5Handler);
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindDigestMD5()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "DIGEST-MD5", saslProperties, requestControls,
                           responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is enabled in the server and an authz ID was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindDigestMD5WithAuthzID()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "DIGEST-MD5", saslProperties, requestControls,
                           responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties are <CODE>null</CODE>.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5NullProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties = null;

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties are empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5EmptyProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an invalid property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5InvalidProperty()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("foo");
    saslProperties.put("invalid", propList);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple values for the authID property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5MultipleAuthIDs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    propList.add("u:test.user");
    saslProperties.put("authid", propList);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an empty authID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5MEmptyAuthID()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("");
    saslProperties.put("authid", propList);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple values for the realm property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5MultipleRealms()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    propList.add("dc=example,dc=com");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain a valid quality of protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindDigestMD5ValidQoP()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    propList = new ArrayList<String>();
    propList.add("auth");
    saslProperties.put("qop", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "DIGEST-MD5", saslProperties, requestControls,
                           responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain the unsupported integrity quality of
   * protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5UnsupportedQoPAuthInt()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    propList = new ArrayList<String>();
    propList.add("auth-int");
    saslProperties.put("qop", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain the unsupported confidentiality quality
   * of protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5UnsupportedQoPAuthConf()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    propList = new ArrayList<String>();
    propList.add("auth-conf");
    saslProperties.put("qop", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an invalid quality of protection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5InvalidQoP()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    propList = new ArrayList<String>();
    propList.add("invalid");
    saslProperties.put("qop", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple quality of protection values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5MultipleQoPs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    propList = new ArrayList<String>();
    propList.add("auth");
    propList.add("auth-int");
    propList.add("auth-conf");
    saslProperties.put("qop", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple digest URIs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5MultipleDigestURIs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    propList = new ArrayList<String>();
    propList.add("ldap/value1");
    propList.add("ldap/value2");
    saslProperties.put("digest-uri", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain multiple authorization IDs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindDigestMD5MultipleAuthzIDs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    propList.add("u:test.user");
    saslProperties.put("authzid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an invalid auth ID in the DN form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindDigestMD5InvalidAuthDN()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:invalid");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the
   * DIGEST-MD5 SASL properties contain an auth ID that doesn't map to any user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindDigestMD5NonExistentAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("u:nosuchuser");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which an invalid
   * password was provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindDigestMD5InvalidPassword()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("u:nosuchuser");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("wrongPassword"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the target
   * user does not have a reversible password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindDigestMD5NoReversiblePassword()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> propList = new ArrayList<String>();
    propList.add("u:nosuchuser");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"),
                             "DIGEST-MD5", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which DIGEST-MD5
   * authentication is enabled in the server and the password policy request
   * control is included.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindDigestMD5WithPasswordPolicyControl()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    requestControls.add(new LDAPControl(new PasswordPolicyRequestControl()));

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "DIGEST-MD5", saslProperties, requestControls,
                           responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which EXTERNAL
   * authentication is not enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindExternalDisabled()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    SASLMechanismHandler externalHandler =
         DirectoryServer.getSASLMechanismHandler("EXTERNAL");
    DirectoryServer.deregisterSASLMechanismHandler("EXTERNAL");


    String keyStorePath   = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";


    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert",
                 trustStorePath, "password");


    Socket s = factory.createSocket("127.0.0.1",
                                    TestCaseUtils.getServerLdapsPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), null, "EXTERNAL",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
      DirectoryServer.registerSASLMechanismHandler("EXTERNAL", externalHandler);
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which EXTERNAL
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindExternal()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

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


    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");


    Socket s = factory.createSocket("127.0.0.1",
                                    TestCaseUtils.getServerLdapsPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(), null, "EXTERNAL",
                           saslProperties, requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in the EXTERNAL SASL
   * properties were not empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindExternalInvalidProperties()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    SASLMechanismHandler externalHandler =
         DirectoryServer.getSASLMechanismHandler("EXTERNAL");
    DirectoryServer.deregisterSASLMechanismHandler("EXTERNAL");


    String keyStorePath   = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";


    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");


    Socket s = factory.createSocket("127.0.0.1",
                                    TestCaseUtils.getServerLdapsPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("foo");
    saslProperties.put("invalid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), null, "EXTERNAL",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
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
  @Test()
  public void testDoSASLBindExternalWithPasswordPolicy()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

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


    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");


    Socket s = factory.createSocket("127.0.0.1",
                                    TestCaseUtils.getServerLdapsPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    requestControls.add(new LDAPControl(new PasswordPolicyRequestControl()));

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(), null, "EXTERNAL",
                           saslProperties, requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties list was null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPINullProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties = null;

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties list was empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIEmptyProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has a zero-length auth ID value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIEmptyAuthID()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("");
    saslProperties.put("authid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple authID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIMultipleAuthIDs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    valueList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple authzID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIMultipleAuthzIDs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    valueList.add("dn:uid=test.user,o=test");
    saslProperties.put("authzid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple KDC values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIMultipleKDCs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("kdc1");
    valueList.add("kdc2");
    saslProperties.put("kdc", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple quality of protection values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIMultipleQoPs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("auth");
    valueList.add("auth-int");
    valueList.add("auth-conf");
    saslProperties.put("qop", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an unsupported quality of protection value of
   * auth-int.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIUnsupportedQoPAuthInt()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("auth-int");
    saslProperties.put("qop", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an unsupported quality of protection value of
   * auth-conf.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIUnsupportedQoPAuthConf()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("auth-conf");
    saslProperties.put("qop", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an invalid quality of protection value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIInvalidQoP()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("invalid");
    saslProperties.put("qop", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has multiple realm values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIMultipleRealms()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("realm1");
    valueList.add("realm2");
    saslProperties.put("realm", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties has an invalid property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPIInvalidProperty()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("foo");
    saslProperties.put("invalid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for GSSAPI authentication when the
   * provided properties isn't empty but doesn't contain an auth ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindGSSAPINoAuthID()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("auth");
    saslProperties.put("qop", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                             "GSSAPI", saslProperties, requestControls,
                             responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which PLAIN
   * authentication is disabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindPlainDisabled()
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


    SASLMechanismHandler plainHandler =
         DirectoryServer.getSASLMechanismHandler("PLAIN");
    DirectoryServer.deregisterSASLMechanismHandler("PLAIN");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
      DirectoryServer.registerSASLMechanismHandler("PLAIN",
                                                   plainHandler);
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which PLAIN
   * authentication is enabled in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindPlain()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"), "PLAIN",
                           saslProperties, requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties are null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindPlainNullProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties = null;

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties are empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindPlainEmptyProperties()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties have multiple auth ID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindPlainMultipleAuthIDs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("dn:uid=test.user,o=test");
    valueList.add("u:test.user");
    saslProperties.put("authid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties have multiple auth ID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindPlainZeroLengthAuthID()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("");
    saslProperties.put("authid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties have multiple authzID values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindPlainMultipleAuthzIDs()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("dn:uid=test.user,o=test");
    valueList.add("u:test.user");
    saslProperties.put("authzid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties contains an invalid property.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindPlainInvalidProperty()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", valueList);

    valueList = new ArrayList<String>();
    valueList.add("foo");
    saslProperties.put("invalid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which the PLAIN
   * SASL properties does not contain an auth ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ClientException.class })
  public void testDoSASLBindPlainNoAuthID()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    ArrayList<String> valueList = new ArrayList<String>();
    valueList.add("dn:uid=test.user,o=test");
    saslProperties.put("authzid", valueList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    try
    {
      authHandler.doSASLBind(new ASN1OctetString(),
                             new ASN1OctetString("password"), "PLAIN",
                             saslProperties, requestControls, responseControls);
    }
    finally
    {
      s.close();
    }
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for PLAIN authentication in which
   * the target user does not exist in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindPlainNonExistentUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=does.not.exist,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"), "PLAIN",
                           saslProperties, requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for PLAIN authentication in which
   * the wrong password has been provided for the target user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDoSASLBindPlainWrongPassword()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=does.not.exist,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("wrongPassword"), "PLAIN",
                           saslProperties, requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>doSASLBind</CODE> method for the case in which PLAIN
   * authentication is enabled in the server and the password policy request
   * control is included.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoSASLBindPlainWithPasswordPolicy()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    requestControls.add(new LDAPControl(new PasswordPolicyRequestControl()));

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"), "PLAIN",
                           saslProperties, requestControls, responseControls);

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for an
   * unauthenticated client connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentityUnauthenticated()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    assertNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a simple anonymous bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentitySimpleAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSimpleBind(3, new ASN1OctetString(), new ASN1OctetString(),
                             requestControls, responseControls);
    assertNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a simple bind as a root user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentitySimpleRootUser()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSimpleBind(3, new ASN1OctetString("cn=Directory Manager"),
                             new ASN1OctetString("password"), requestControls,
                             responseControls);
    assertNotNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a simple bind as a normal user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentitySimpleTestUser()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSimpleBind(3, new ASN1OctetString("uid=test.user,o=test"),
                             new ASN1OctetString("password"), requestControls,
                             responseControls);
    assertNotNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a SASL ANONYMOUS bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentitySASLAnonymous()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("testDoSASLBindAnonymous");
    saslProperties.put("trace", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    authHandler.doSASLBind(new ASN1OctetString(), new ASN1OctetString(),
                           "ANONYMOUS", saslProperties, requestControls,
                           responseControls);
    assertNull(authHandler.requestAuthorizationIdentity());

    s.close();
    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a CRAM-MD5 bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentityCRAMMD5()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);

    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "CRAM-MD5", saslProperties, requestControls,
                           responseControls);
    assertNotNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a DIGEST-MD5 bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentityDigestMD5()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    propList = new ArrayList<String>();
    propList.add("o=test");
    saslProperties.put("realm", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"),
                           "DIGEST-MD5", saslProperties, requestControls,
                           responseControls);
    assertNotNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after an EXTERNAL bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentityExternal()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

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


    SSLConnectionFactory factory = new SSLConnectionFactory();
    factory.init(false, keyStorePath, "password", "client-cert", trustStorePath,
                 "password");


    Socket s = factory.createSocket("127.0.0.1",
                                    TestCaseUtils.getServerLdapsPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(), null, "EXTERNAL",
                           saslProperties, requestControls, responseControls);
    assertNotNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }



  /**
   * Tests the <CODE>requestAuthorizationIdentity</CODE> method for a a client
   * connection after a PLAIN bind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestAuthorizationIdentityPlain()
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


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    AtomicInteger          messageID        = new AtomicInteger(1);
    ArrayList<LDAPControl> requestControls  = new ArrayList<LDAPControl>();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl>();

    LinkedHashMap<String,List<String>> saslProperties =
         new LinkedHashMap<String,List<String>>();
    ArrayList<String> propList = new ArrayList<String>();
    propList.add("dn:uid=test.user,o=test");
    saslProperties.put("authid", propList);

    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(r, w, "localhost", messageID);
    authHandler.doSASLBind(new ASN1OctetString(),
                           new ASN1OctetString("password"), "PLAIN",
                           saslProperties, requestControls, responseControls);
    assertNotNull(authHandler.requestAuthorizationIdentity());

    s.close();
  }
}

