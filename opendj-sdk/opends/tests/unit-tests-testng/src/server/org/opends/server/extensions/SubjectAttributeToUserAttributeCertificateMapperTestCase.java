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



import static org.testng.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.SubjectAttributeToUserAttributeCertificateMapperCfgDefn;
import org.opends.server.admin.std.server.SubjectAttributeToUserAttributeCertificateMapperCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * A set of test cases for the Subject Attribute to User Attribute certificate
 * mapper.
 */
public class SubjectAttributeToUserAttributeCertificateMapperTestCase
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
      "dn: cn=No Map Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "",
      "dn: cn=No Map Colon,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map Colon",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: nomapcolon",
      "",
      "dn: cn=No Map Cert Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map Cert Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: :cn",
      "",
      "dn: cn=No Map User Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map User Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:",
      "",
      "dn: cn=Undefined User Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Undefined User Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:undefined",
      "",
      "dn: cn=Duplicate Cert Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Duplicate Cert Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:cn",
      "ds-cfg-subject-attribute-mapping: cn:sn",
      "",
      "dn: cn=Duplicate User Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Duplicate User Attr",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:cn",
      "ds-cfg-subject-attribute-mapping: e:cn",
      "",
      "dn: cn=Invalid Base DN,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: " +
           "ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Invalid Base DN",
      "ds-cfg-java-class: org.opends.server.extensions." +
           "SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:cn",
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
    SubjectAttributeToUserAttributeCertificateMapperCfg config =
       AdminTestCaseUtils.getConfiguration(
       SubjectAttributeToUserAttributeCertificateMapperCfgDefn.
            getInstance(), e);

    SubjectAttributeToUserAttributeCertificateMapper mapper =
         new SubjectAttributeToUserAttributeCertificateMapper();
    mapper.initializeCertificateMapper(config);
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

      assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
    }
    finally
    {
      disableMapper();
    }
  }



  /**
   * Tests a successful mapping with multiple attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessfulMappingMultipleAttributes()
         throws Exception
  {
    enableMapper();

    try
    {
      setAttributeMappings(new String[] { "cn:cn", "o:o" });

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
        "o: test");



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
      setAttributeMappings(new String[] { "cn:cn", "e:mail" });
    }
  }



  /**
   * Tests a failed mapping due to no mappable attributes in the certificate.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedNoMappableAttributes()
         throws Exception
  {
    enableMapper();

    try
    {
      setAttributeMappings(new String[] { "e:mail" });

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
        "o: test");



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

      assertFalse(LDAPSearch.mainSearch(args, false, null, System.err) == 0);
    }
    finally
    {
      disableMapper();
      setAttributeMappings(new String[] { "cn:cn", "e:mail" });
    }
  }



  /**
   * Tests a failed mapping due to no matching users.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedMappingNoMatchingUsers()
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
        "cn: Not Test User");



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
   * Tests a failed mapping due to multiple matching users.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedMappingMultipleMatchingUsers()
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
        "cn: Test User",
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
   * Tests a failed mapping when there are no users below the configured base
   * DNs that match the criteria.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedMappingNoUserBelowBaseDNs()
         throws Exception
  {
    enableMapper();

    try
    {
      setBaseDNs(new String[] { "dc=example,dc=com" });

      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntries(
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
      setBaseDNs(null);
    }
  }



  /**
   * Tests to ensure that an attmept to remove the subject attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveMapAttribute()
         throws Exception
  {
    String mapperDN = "cn=Subject Attribute to User Attribute," +
                      "cn=Certificate Mappers,cn=config";

    Attribute a =
      Attributes.empty(DirectoryServer.getAttributeType(
                            "ds-cfg-subject-attribute-mapping"));

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.DELETE, a));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests to ensure that an attmept to set an attribute mapping with no colon
   * will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingNoColon()
         throws Exception
  {
    setAttributeMappings(new String[] { "nocolon" });
  }



  /**
   * Tests to ensure that an attmept to set an attribute mapping with no cert
   * attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingNoCertAttribute()
         throws Exception
  {
    setAttributeMappings(new String[] { ":cn" });
  }



  /**
   * Tests to ensure that an attmept to set an attribute mapping with no user
   * attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingNoUserAttribute()
         throws Exception
  {
    setAttributeMappings(new String[] { "cn:" });
  }



  /**
   * Tests to ensure that an attmept to set an attribute mapping with an
   * undefined user attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingUndefinedUserAttribute()
         throws Exception
  {
    setAttributeMappings(new String[] { "cn:undefined" });
  }



  /**
   * Tests to ensure that an attmept to set an attribute mapping with a
   * duplicate cert attribute mapping will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingDuplicateCertAttribute()
         throws Exception
  {
    setAttributeMappings(new String[] { "cn:cn", "cn:sn" });
  }



  /**
   * Tests to ensure that an attmept to set an attribute mapping with a
   * duplicate user attribute mapping will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingDuplicateUserAttribute()
         throws Exception
  {
    setAttributeMappings(new String[] { "cn:cn", "e:cn" });
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
   * uses the Subject Attribute to User Attribute certificate mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void enableMapper()
          throws Exception
  {
    String externalDN = "cn=EXTERNAL,cn=SASL Mechanisms,cn=config";
    String mapperDN = "cn=Subject Attribute to User Attribute," +
                      "cn=Certificate Mappers,cn=config";

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("ds-cfg-certificate-mapper",
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
        Attributes.create("ds-cfg-certificate-mapper",
                                            mapperDN)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(externalDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Alters the configuration of the Subject Attribute to User Attribute
   * certificate mapper so that it will use the specified set of mappings.
   *
   * @param  mappings  The specified set of mappings to use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void setAttributeMappings(String[] mappings)
          throws Exception
  {
    String mapperDN = "cn=Subject Attribute to User Attribute," +
                      "cn=Certificate Mappers,cn=config";

    AttributeType attrType =
         DirectoryServer.getAttributeType(
              "ds-cfg-subject-attribute-mapping");

    AttributeBuilder builder = new AttributeBuilder(attrType);
    if (mappings != null)
    {
      for (String mapping : mappings)
      {
        builder.add(mapping);
      }
    }

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        builder.toAttribute()));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Alters the configuration of the Subject Attribute to User Attribute
   * certificate mapper so that it will look for matches below the specified set
   * of base DNs.
   *
   * @param  baseDNs  The set of base DNs to use when mapping certificates to
   *                  users.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void setBaseDNs(String[] baseDNs)
          throws Exception
  {
    String mapperDN = "cn=Subject Attribute to User Attribute," +
                      "cn=Certificate Mappers,cn=config";

    AttributeType attrType =
         DirectoryServer.getAttributeType("ds-cfg-user-base-dn");

    AttributeBuilder builder = new AttributeBuilder(attrType);
    if (baseDNs != null)
    {
      for (String baseDN : baseDNs)
      {
        builder.add(baseDN);
      }
    }

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              builder.toAttribute()));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
}

