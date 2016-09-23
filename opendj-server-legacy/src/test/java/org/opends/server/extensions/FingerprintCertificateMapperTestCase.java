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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.TestCaseUtils.runLdapSearchTrustCertificateForSession;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.testng.Assert.*;

import java.io.File;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.FingerprintCertificateMapperCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A set of test cases for the fingerprint certificate mapper.
 */
public class FingerprintCertificateMapperTestCase
       extends ExtensionsTestCase
{
  private static final String FINGERPRINT_MAPPER_DN = "cn=Fingerprint Mapper,cn=Certificate Mappers,cn=config";

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
   * initialize the certificate mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
      "dn: cn=No Fingerprint Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-fingerprint-certificate-mapper",
      "cn: No Fingerprint Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "FingerprintCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-fingerprint-algorithm: MD5",
      "",
      "dn: cn=Undefined Fingerprint Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-fingerprint-certificate-mapper",
      "cn: Undefined Fingerprint Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "FingerprintCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-fingerprint-attribute: undefined",
      "ds-cfg-fingerprint-algorithm: MD5",
      "",
      "dn: cn=No Fingerprint Algorithm,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-fingerprint-certificate-mapper",
      "cn: No Fingerprint Algorithm",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "FingerprintCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-fingerprint-attribute: " +
           "ds-certificate-fingerprint",
      "",
      "dn: cn=Invalid Fingerprint Algorithm,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-fingerprint-certificate-mapper",
      "cn: Invalid Fingerprint Algorithm",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "FingerprintCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-fingerprint-attribute: " +
           "ds-certificate-fingerprint",
      "ds-cfg-fingerprint-algorithm: invalid",
      "",
      "dn: cn=Invalid Base DN,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-fingerprint-certificate-mapper",
      "cn: Invalid Base DN",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "FingerprintCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-fingerprint-attribute: " +
           "ds-certificate-fingerprint",
      "ds-cfg-fingerprint-algorithm: MD5",
      "ds-cfg-user-base-dn: invalid");


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
   * @param  e  The configuration entry to use to initialize the certificate
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
    InitializationUtils.initializeCertificateMapper(
        new FingerprintCertificateMapper(), e, FingerprintCertificateMapperCfgDefn.getInstance());
  }



  /**
   * Tests a successful mapping using the default configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulMappingDefaultConfig()
         throws Exception
  {
    enableMapper();

    try
    {
      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: ds-certificate-user",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "ds-certificate-fingerprint: " +
             "EC:99:7C:FD:C5:1C:D5:5C:28:BE:F7:FE:BF:71:6F:0E");



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
    finally
    {
      disableMapper();
    }
  }



  /**
   * Tests a successful mapping using the SHA-1 digest algorithm.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulMappingSHA1()
         throws Exception
  {
    enableMapper();

    try
    {
      setFingerprintAlgorithm("SHA1");

      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: ds-certificate-user",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "ds-certificate-fingerprint: " +
             "9C:7B:1B:88:7B:9B:29:2F:A0:20:54:ED:46:1A:4A:9B:1F:AE:9C:AC");



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
    finally
    {
      disableMapper();
      setFingerprintAlgorithm("MD5");
    }
  }



  /**
   * Tests a failed mapping due to no matching entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailedMappingNoMatchingEntries()
         throws Exception
  {
    enableMapper();

    try
    {
      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: ds-certificate-user",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User");



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
      assertFalse(runLdapSearchTrustCertificateForSession(nullPrintStream(), nullPrintStream(), args) == 0);
    }
    finally
    {
      disableMapper();
    }
  }



  /**
   * Tests a failed mapping due to multiple matching entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailedMappingMultipleMatchingEntries()
         throws Exception
  {
    enableMapper();

    try
    {
      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntries(
        "dn: uid=test.user1,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: ds-certificate-user",
        "uid: test.user1",
        "givenName: Test",
        "sn: User",
        "cn: Test User 1",
        "ds-certificate-fingerprint: " +
             "07:5A:AB:4B:E1:DD:E3:05:83:C0:FE:5F:A3:E8:1E:EB",
        "",
        "dn: uid=test.user2,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: ds-certificate-user",
        "uid: test.user2",
        "givenName: Test",
        "sn: User",
        "cn: Test User 2",
        "ds-certificate-fingerprint: " +
             "07:5A:AB:4B:E1:DD:E3:05:83:C0:FE:5F:A3:E8:1E:EB");



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
      assertFalse(runLdapSearchTrustCertificateForSession(nullPrintStream(), nullPrintStream(), args) == 0);
    }
    finally
    {
      disableMapper();
    }
  }



  /**
   * Tests to ensure that an attmept to remove the fingerprint attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveFingerprintAttribute() throws Exception
  {
    delete(FINGERPRINT_MAPPER_DN, "ds-cfg-fingerprint-attribute");
  }

  /**
   * Tests to ensure that an attempt to remove the fingerprint algorithm will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveFingerprintAlgorithm() throws Exception
  {
    delete(FINGERPRINT_MAPPER_DN, "ds-cfg-fingerprint-algorithm");
  }

  /**
   * Tests to ensure that an attmept to set an undefined fingerprint attribute
   * will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetUndefinedFingerprintAttribute()
         throws Exception
  {
    setFingerprintAttribute("undefined");
  }



  /**
   * Tests to ensure that an attmept to set an undefined fingerprint algorithm
   * will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetUndefinedFingerprintAlgorithm()
         throws Exception
  {
    setFingerprintAlgorithm("undefined");
  }



  /**
   * Tests to ensure that an attempt to set an invalid base DN will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSetInvalidBaseDN() throws Exception
  {
    ModifyRequest modifyRequest = newModifyRequest(FINGERPRINT_MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-user-base-dn", "invalid");
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.INVALID_ATTRIBUTE_SYNTAX);
  }



  /**
   * Alters the configuration of the SASL EXTERNAL mechanism handler so that it
   * uses the Subject DN to User Attribute certificate mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void enableMapper() throws Exception
  {
    replace("cn=EXTERNAL,cn=SASL Mechanisms,cn=config", "ds-cfg-certificate-mapper", FINGERPRINT_MAPPER_DN);
  }



  /**
   * Alters the configuration of the SASL EXTERNAL mechanism handler so that it
   * uses the Subject Equals DN certificate mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void disableMapper() throws Exception
  {
    String mapperDN = "cn=Subject Equals DN,cn=Certificate Mappers,cn=config";

    replace("cn=EXTERNAL,cn=SASL Mechanisms,cn=config", "ds-cfg-certificate-mapper", mapperDN);
  }



  /**
   * Alters the configuration of the fingerprint certificate mapper so that it
   * will look for the fingerprint in the specified attribute.
   *
   * @param  attrName  The name of the attribute in which to look for the
   *                   certificate subject.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void setFingerprintAttribute(String attrName) throws Exception
  {
    replace(FINGERPRINT_MAPPER_DN, "ds-cfg-fingerprint-attribute", attrName);
  }

  private void replace(String mapperDN, String attrName, String attrValues) throws DirectoryException
  {
    ModifyRequest modifyRequest = newModifyRequest(mapperDN).addModification(REPLACE, attrName, attrValues);
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private void delete(String mapperDN, String attrName) throws DirectoryException
  {
    ModifyRequest modifyRequest = newModifyRequest(mapperDN).addModification(DELETE, attrName);
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertNotSame(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Alters the configuration of the fingerprint certificate mapper so that it
   * will use the specified fingerprint algorithm.
   *
   * @param  algorithm  The name of the fingerprint algorithm to use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void setFingerprintAlgorithm(String algorithm)
          throws Exception
  {
    replace(FINGERPRINT_MAPPER_DN, "ds-cfg-fingerprint-algorithm", algorithm);
  }

  /**
   * Tests a successful mapping using the default configuration, and
   * verify that user can do a privileged action (read config).
   * Verification for issue OPENDJ-459.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPrivilegeWithSuccessfulMappingDefaultConfig()
         throws Exception
  {
    enableMapper();

    try
    {
      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: ds-certificate-user",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "ds-privilege-name: config-read",
        "ds-certificate-fingerprint: " +
             "EC:99:7C:FD:C5:1C:D5:5C:28:BE:F7:FE:BF:71:6F:0E");



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
        "-b", "cn=config",
        "-s", "sub",
        "(objectClass=*)"
      };
      assertEquals(runLdapSearchTrustCertificateForSession(args), 0);
    }
    finally
    {
      disableMapper();
    }
  }
}

