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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.Base64;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.server.config.meta.ExternalSASLMechanismHandlerCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.tools.LDAPSearch;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.TestCaseUtils.runLdapSearchTrustCertificateForSession;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

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
  @BeforeClass
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
    InitializationUtils.initializeSASLMechanismHandler(
        new ExternalSASLMechanismHandler(), e, ExternalSASLMechanismHandlerCfgDefn.getInstance());
  }



  /**
   * Tests the <CODE>isPasswordBased</CODE> method.
   */
  @Test
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
  @Test
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
  @Test
  public void testEXTERNALTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User");

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
      "-o", "mech=EXTERNAL",
      "-N", "client-cert",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(runLdapSearchTrustCertificateForSession(args), 0);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEXTERNALTrustAll()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User");

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
      "-o", "mech=EXTERNAL",
      "-N", "client-cert",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
  }



  /**
   * Establishes a non-SSL-based connection to the server and verifies that
   * EXTERNAL authentication fails over that connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = AuthenticationException.class)
  public void testFailEXTERNALInsecureConnection() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        Connection conn = factory.getConnection())
    {
      BindResult result = conn.bind(Requests.newExternalSASLBindRequest());
      assertNotEquals(result.getResultCode(), ResultCode.SUCCESS);
    }
 }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server will not be able to map
   * the client certificate to a user entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
      "-o", "mech=EXTERNAL",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will not have
   * the required certificate attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailEXTERNALTrustAllNoRequiredCert()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User");

    InternalClientConnection conn = getRootConnection();
    String dnStr    = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String attrName = "ds-cfg-certificate-validation-policy";
    ArrayList<Modification> mods = newArrayList(new Modification(REPLACE, Attributes.create(attrName, "always")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.valueOf(dnStr), mods);
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
      "-o", "mech=EXTERNAL",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "ifpresent")));
    modifyOperation = conn.processModify(DN.valueOf(dnStr), mods);
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
  @Test
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

    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User",
         "userCertificate;binary:: " + Base64.encode(certBytes));


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-Z",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-o", "mech=EXTERNAL",
      "-N", "client-cert",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will have the
   * optional certificate attribute but it will not have a valid value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailEXTERNALTrustAllInvalidOptionalCert()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String Certificate =
      "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV" +
      "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl" +
      "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa" +
      "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp" +
      "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz" +
      "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ" +
      "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm" +
      "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z" +
      "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB" +
      "hvhCAQ0EHxYdSW52YWxpZCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE" +
      "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF" +
      "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg" +
      "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj" +
      "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7" +
      "1AIUXiE3Qcck";

    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User",
         "userCertificate;binary:: " + Certificate);


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
      "-o", "mech=EXTERNAL",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);
  }



  /**
   * Establishes an SSL-based connection to the server, provides a client
   * certificate, and uses it to authenticate to the server.  The server
   * certificate will be blindly trusted.  The server user entry will have the
   * required certificate attribute and it will be valid.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User",
         "userCertificate;binary:: " + Base64.encode(certBytes));

    InternalClientConnection conn = getRootConnection();
    String dnStr    = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String attrName = "ds-cfg-certificate-validation-policy";
    ArrayList<Modification> mods = newArrayList(new Modification(REPLACE, Attributes.create(attrName, "always")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.valueOf(dnStr), mods);
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
      "-o", "mech=EXTERNAL",
      "-N", "client-cert",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "ifpresent")));
    modifyOperation = conn.processModify(DN.valueOf(dnStr), mods);
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
  @Test
  public void testFailEXTERNALTrustAllInvalidRequiredCert()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String Certificate =
      "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV" +
      "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl" +
      "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa" +
      "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp" +
      "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz" +
      "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ" +
      "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm" +
      "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z" +
      "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB" +
      "hvhCAQ0EHxYdSW52YWxpZCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE" +
      "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF" +
      "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg" +
      "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj" +
      "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7" +
      "1AIUXiE3Qcck";

    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User",
         "userCertificate;binary:: " + Certificate);

    InternalClientConnection conn = getRootConnection();
    String dnStr    = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String attrName = "ds-cfg-certificate-validation-policy";
    ArrayList<Modification> mods = newArrayList(new Modification(REPLACE, Attributes.create(attrName, "always")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.valueOf(dnStr), mods);
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
      "-o", "mech=EXTERNAL",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.run(nullPrintStream(), nullPrintStream(), args) == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attrName, "ifpresent")));
    modifyOperation = conn.processModify(DN.valueOf(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
}

