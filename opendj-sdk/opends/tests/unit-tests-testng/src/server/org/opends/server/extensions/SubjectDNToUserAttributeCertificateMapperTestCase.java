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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
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
            SubjectDNToUserAttributeCertificateMapperCfgDefn;
import org.opends.server.admin.std.server.
            SubjectDNToUserAttributeCertificateMapperCfg;
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
 * A set of test cases for the Subject DN to User Attribute certificate mapper.
 */
public class SubjectDNToUserAttributeCertificateMapperTestCase
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
      "dn: cn=No Subject Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-dn-to-user-attribute-certificate-mapper",
      "cn: No Subject Attr",
      "ds-cfg-certificate-mapper-class: org.opends.server.extensions." +
           "SubjectDNToUserAttributeCertificateMapper",
      "ds-cfg-certificate-mapper-enabled: true",
      "",
      "dn: cn=Undefined Subject Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-dn-to-user-attribute-certificate-mapper",
      "cn: Undefined Subject Attr",
      "ds-cfg-certificate-mapper-class: org.opends.server.extensions." +
           "SubjectDNToUserAttributeCertificateMapper",
      "ds-cfg-certificate-mapper-enabled: true",
      "ds-cfg-certificate-subject-attribute-type: undefined",
      "",
      "dn: cn=Invalid Base DN,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-dn-to-user-attribute-certificate-mapper",
      "cn: Invalid Base DN",
      "ds-cfg-certificate-mapper-class: org.opends.server.extensions." +
           "SubjectDNToUserAttributeCertificateMapper",
      "ds-cfg-certificate-mapper-enabled: true",
      "ds-cfg-certificate-subject-attribute-type: ds-certificate-subject-dn",
      "ds-cfg-certificate-user-base-dn: invalid");


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
    SubjectDNToUserAttributeCertificateMapperCfg configuration =
       AdminTestCaseUtils.getConfiguration(
            SubjectDNToUserAttributeCertificateMapperCfgDefn.
                 getInstance(), e);

    SubjectDNToUserAttributeCertificateMapper mapper =
         new SubjectDNToUserAttributeCertificateMapper();
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
        "ds-certificate-subject-dn: CN=Test User, O=Test");



      String keyStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
      String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                              "config" + File.separator + "client.truststore";

      String[] args =
      {
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
   * Tests a successful mapping using a configuration with a different subject
   * attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessfulMappingAlternateSubjectAttribute()
         throws Exception
  {
    enableMapper();

    try
    {
      setSubjectAttribute("manager");

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
        "manager: CN=Test User, O=Test");



      String keyStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
      String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                              "config" + File.separator + "client.truststore";

      String[] args =
      {
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
      setSubjectAttribute("ds-certificate-subject-dn");
    }
  }



  /**
   * Tests a successful mapping using a configuration with a different set of
   * base DNs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessfulMappingAlternateBaseDNs()
         throws Exception
  {
    enableMapper();

    try
    {
      setBaseDNs(new String[] { "o=test" });

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
        "ds-certificate-subject-dn: CN=Test User, O=Test");



      String keyStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
      String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                              "config" + File.separator + "client.truststore";

      String[] args =
      {
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
      setSubjectAttribute("ds-certificate-subject-dn");
    }
  }



  /**
   * Tests a failed mapping when there are no users that should match.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedMappingNoUsers()
         throws Exception
  {
    enableMapper();

    try
    {
      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntry(
        "dn: cn=Test User,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: ds-certificate-user",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User");



      String keyStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
      String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                              "config" + File.separator + "client.truststore";

      String[] args =
      {
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
   * Tests a failed mapping when there are multiple users that match the
   * critieria.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedMappingMultipleUsers()
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
        "ds-certificate-subject-dn: CN=Test User, O=Test",
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
        "ds-certificate-subject-dn: CN=Test User, O=Test");



      String keyStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
      String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                              "config" + File.separator + "client.truststore";

      String[] args =
      {
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
        "cn: Test User",
        "ds-certificate-subject-dn: CN=Test User, O=Test");



      String keyStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.keystore";
      String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                              "config" + File.separator + "client.truststore";

      String[] args =
      {
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
  public void testRemoveSubjectAttribute()
         throws Exception
  {
    String mapperDN =
         "cn=Subject DN to User Attribute,cn=Certificate Mappers,cn=config";

    Attribute a =
         new Attribute(DirectoryServer.getAttributeType(
                            "ds-cfg-certificate-subject-attribute-type"));

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.DELETE, a));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(mapperDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests to ensure that an attmept to set an undefined subject attribute will
   * fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetUndefinedSubjectAttribute()
         throws Exception
  {
    setSubjectAttribute("undefined");
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
    String mapperDN =
         "cn=Subject DN to User Attribute,cn=Certificate Mappers,cn=config";

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("ds-cfg-certificate-mapper-dn",
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
                              new Attribute("ds-cfg-certificate-mapper-dn",
                                            mapperDN)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(externalDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Alters the configuration of the Subject DN to User Attribute certificate
   * mapper so that it will look for the subject DN in the specified attribute.
   *
   * @param  attrName  The name of the attribute in which to look for the
   *                   certificate subject.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void setSubjectAttribute(String attrName)
          throws Exception
  {
    String mapperDN =
         "cn=Subject DN to User Attribute,cn=Certificate Mappers,cn=config";

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                      new Attribute("ds-cfg-certificate-subject-attribute-type",
                                    attrName)));

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
    String mapperDN =
         "cn=Subject DN to User Attribute,cn=Certificate Mappers,cn=config";

    AttributeType attrType =
         DirectoryServer.getAttributeType("ds-cfg-certificate-user-base-dn");

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

