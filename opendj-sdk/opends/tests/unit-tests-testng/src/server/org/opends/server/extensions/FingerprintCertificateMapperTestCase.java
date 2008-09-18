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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.
            FingerprintCertificateMapperCfgDefn;
import org.opends.server.admin.std.server.
            FingerprintCertificateMapperCfg;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of test cases for the fingerprint certificate mapper.
 */
public class FingerprintCertificateMapperTestCase
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
    FingerprintCertificateMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              FingerprintCertificateMapperCfgDefn.getInstance(),
              e);

    FingerprintCertificateMapper mapper = new FingerprintCertificateMapper();
    mapper.initializeCertificateMapper(configuration);
  }



  /**
   * Tests a successful mapping using the default configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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
        "-r",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
    }
    finally
    {
      disableMapper();
    }
  }



  /**
   * Tests a successful mapping using the SHA-1 digest algorithm..
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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
             "CB:A4:C7:A0:46:1F:44:88:12:23:56:49:F9:54:F4:37:E1:9F:9F:A4");



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
  @Test()
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
        "-r",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
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
  @Test()
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
        "-r",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
    }
    finally
    {
      disableMapper();
    }
  }



  /**
   * Tests to ensure that an attmept to remove the fingerprint attribute will
   * fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveFingerprintAttribute()
         throws Exception
  {
    String mapperDN = "cn=Fingerprint Mapper,cn=Certificate Mappers,cn=config";

    Attribute a =
         new Attribute(DirectoryServer.getAttributeType(
                            "ds-cfg-fingerprint-attribute"));

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.DELETE, a));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests to ensure that an attmept to remove the fingerprint algorithm will
   * fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveFingerprintAlgorithm()
         throws Exception
  {
    String mapperDN = "cn=Fingerprint Mapper,cn=Certificate Mappers,cn=config";

    Attribute a =
         new Attribute(DirectoryServer.getAttributeType(
                            "ds-cfg-fingerprint-algorithm"));

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.DELETE, a));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
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
   * Tests to ensure that an attmept to set an invalid base DN will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetInvalidBaseDN()
         throws Exception
  {
    setBaseDNs(new String[] { "invalid" });
  }



  /**
   * Alters the configuration of the SASL EXTERNAL mechanism handler so that it
   * uses the Subject DN to User Attribute certificate mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void enableMapper()
          throws Exception
  {
    String externalDN = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String mapperDN = "cn=Fingerprint Mapper,cn=Certificate Mappers,cn=config";

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("ds-cfg-certificate-mapper",
                                            mapperDN)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(externalDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Alters the configuration of the SASL EXTERNAL mechanism handler so that it
   * uses the Subject Equals DN certificate mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void disableMapper()
          throws Exception
  {
    String externalDN = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String mapperDN = "cn=Subject Equals DN,cn=Certificate Mappers,cn=config";

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("ds-cfg-certificate-mapper",
                                            mapperDN)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(externalDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
  private void setFingerprintAttribute(String attrName)
          throws Exception
  {
    String mapperDN = "cn=Fingerprint Mapper,cn=Certificate Mappers,cn=config";

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
         new Attribute("ds-cfg-fingerprint-attribute",
                       attrName)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    String mapperDN = "cn=Fingerprint Mapper,cn=Certificate Mappers,cn=config";

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                      new Attribute("ds-cfg-fingerprint-algorithm",
                                    algorithm)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Alters the configuration of the Subject DN to User Attribute certificate
   * mapper so that it will look for the subject DN below the specified set of
   * base DNs.
   *
   * @param  baseDNs  The set of base DNs to use when mapping certificates to
   *                  users.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void setBaseDNs(String[] baseDNs)
          throws Exception
  {
    String mapperDN = "cn=Fingerprint Mapper,cn=Certificate Mappers,cn=config";

    AttributeType attrType =
         DirectoryServer.getAttributeType("ds-cfg-user-base-dn");

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    if (baseDNs != null)
    {
      for (String baseDN : baseDNs)
      {
        values.add(new AttributeValue(attrType, baseDN));
      }
    }

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute(attrType, attrType.getNameOrOID(),
                                            values)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
}

