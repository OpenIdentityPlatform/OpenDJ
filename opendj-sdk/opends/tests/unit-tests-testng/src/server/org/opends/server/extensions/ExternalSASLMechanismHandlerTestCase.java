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



import static org.testng.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.ExternalSASLMechanismHandlerCfgDefn;
import org.opends.server.admin.std.server.ExternalSASLMechanismHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.server.util.Base64;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * A set of test cases for the EXTERNAL SASL mechanism handler.
 */
public class ExternalSASLMechanismHandlerTestCase
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
   * Retrieves a set of invalid configurations that cannot be used to
   * initialize the EXTERNAL SASL mechanism handler.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=EXTERNAL,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-external-sasl-mechanism-handler",
         "cn: EXTERNAL",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "ExternalSASLMechanismHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-certificate-validation-policy: invalid",
         "ds-cfg-certificate-attribute: userCertificate",
         "",
         "dn: cn=EXTERNAL,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-external-sasl-mechanism-handler",
         "cn: EXTERNAL",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "ExternalSASLMechanismHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-certificate-validation-policy: ifpresent",
         "ds-cfg-certificate-attribute: invalid");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests initialization with an invalid configuration.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConfigs(Entry e)
         throws Exception
  {
    ExternalSASLMechanismHandlerCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExternalSASLMechanismHandlerCfgDefn.getInstance(),
              e);

    ExternalSASLMechanismHandler handler = new ExternalSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(configuration);
  }



  /**
   * Tests the <CODE>isPasswordBased</CODE> method.
   */
  @Test()
  public void testIsPasswordBased()
  {
    ExternalSASLMechanismHandler handler =
         (ExternalSASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler("EXTERNAL");
    assertNotNull(handler);
    assertFalse(handler.isPasswordBased("EXTERNAL"));
  }



  /**
   * Tests the <CODE>isSecure</CODE> method.
   */
  @Test()
  public void testIsSecure()
  {
    ExternalSASLMechanismHandler handler =
         (ExternalSASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler("EXTERNAL");
    assertNotNull(handler);
    assertTrue(handler.isSecure("EXTERNAL"));
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be trusted using a client trust store.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEXTERNALTrustStore()
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


    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "--noPropertiesFile",
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
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEXTERNALTrustAll()
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


    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Establishes a non-SSL-based connection to the server and verifies that
   * EXTERNAL authentication fails over that connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailEXTERNALInsecureConnection()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), "EXTERNAL", null);
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertFalse(bindResponse.getResultCode() == 0);

    s.close();
 }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server will not be able to map
   * the client certificate to a user entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailEXTERNALTrustAllNoSuchUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will not have
   * the required certificate attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailEXTERNALTrustAllNoRequiredCert()
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


    String dnStr    = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String attrName = "ds-cfg-certificate-validation-policy";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "always")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "ifpresent")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will have the
   * optional certificate attribute and it will be valid.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEXTERNALTrustAllValidOptionalCert()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    KeyStore ks = KeyStore.getInstance("JKS");
    FileInputStream inputStream = new FileInputStream(keyStorePath);
    ks.load(inputStream, "password".toCharArray());
    inputStream.close();
    byte[] certBytes = ks.getCertificate("client-cert").getEncoded();

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User",
         "userCertificate;binary:: " + Base64.encode(certBytes));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will have the
   * optional certificate attribute but it will not have a valid value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailEXTERNALTrustAllInvalidOptionalCert()
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
         "sn: User",
         "userCertificate;binary: invalid");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will have the
   * required certificate attribute and it will be valid.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEXTERNALTrustAllValidRequiredCert()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    KeyStore ks = KeyStore.getInstance("JKS");
    FileInputStream inputStream = new FileInputStream(keyStorePath);
    ks.load(inputStream, "password".toCharArray());
    inputStream.close();
    byte[] certBytes = ks.getCertificate("client-cert").getEncoded();

    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User",
         "userCertificate;binary:: " + Base64.encode(certBytes));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr    = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String attrName = "ds-cfg-certificate-validation-policy";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "always")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "ifpresent")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will have the
   * required certificate attribute but it will not have a valid value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailEXTERNALTrustAllInvalidRequiredCert()
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
         "sn: User",
         "userCertificate;binary: invalid");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr    = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String attrName = "ds-cfg-certificate-validation-policy";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "always")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "ifpresent")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
}

