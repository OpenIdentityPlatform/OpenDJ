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
 * Portions Copyright 2013 Manuel Gaupp
 */
package org.opends.server.extensions;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.io.File;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.SubjectAttributeToUserAttributeCertificateMapperCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the Subject Attribute to User Attribute certificate mapper. */
public class SubjectAttributeToUserAttributeCertificateMapperTestCase
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
   * initialize the certificate mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations() throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
      "dn: cn=No Map Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map Attr",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "",
      "dn: cn=No Map Colon,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map Colon",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: nomapcolon",
      "",
      "dn: cn=No Map Cert Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map Cert Attr",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: :cn",
      "",
      "dn: cn=No Map User Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: No Map User Attr",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:",
      "",
      "dn: cn=Undefined User Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Undefined User Attr",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:undefined",
      "",
      "dn: cn=Duplicate Cert Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Duplicate Cert Attr",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:cn",
      "ds-cfg-subject-attribute-mapping: cn:sn",
      "",
      "dn: cn=Duplicate User Attr,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Duplicate User Attr",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
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
      "ds-cfg-user-base-dn: invalid",
      "",
      "dn: cn=Duplicate Cert Attr OID and Name,cn=Certificate Mappers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-certificate-mapper",
      "objectClass: ds-cfg-subject-attribute-to-user-attribute-certificate-mapper",
      "cn: Duplicate Cert Attr OID and Name",
      "ds-cfg-java-class: org.opends.server.extensions.SubjectAttributeToUserAttributeCertificateMapper",
      "ds-cfg-enabled: true",
      "ds-cfg-subject-attribute-mapping: cn:cn",
      "ds-cfg-subject-attribute-mapping: 2.5.4.3:displayName");


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
                new SubjectAttributeToUserAttributeCertificateMapper(),
                e,
                SubjectAttributeToUserAttributeCertificateMapperCfgDefn.getInstance());
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
        "cn: Test User");

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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
   * Tests a successful mapping using an OID for the mapping.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulMappingUsingAnOID()
         throws Exception
  {
    enableMapper();

    try
    {
      setAttributeMappings("cn:cn", "1.2.840.113549.1.9.1:mail");

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
        "mail: test@example.com");

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client-emailAddress.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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
      setAttributeMappings("cn:cn", "emailAddress:mail");
    }
  }



  /**
   * Tests a successful mapping using the default configuration and a
   * certificate containing a subject with an emailAddress.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulMappingDefaultConfigEmailAddress()
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
        "mail: test@example.com");

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client-emailAddress.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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

  private String getKeyStorePath(String fileName)
  {
    return DirectoryServer.getInstanceRoot() + File.separator + "config" + File.separator + fileName;
  }

  private String getTrustStorePath()
  {
    return DirectoryServer.getInstanceRoot() + File.separator + "config" + File.separator + "client.truststore";
  }

  /**
   * Tests a successful mapping with multiple attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulMappingMultipleAttributes()
         throws Exception
  {
    enableMapper();

    try
    {
      setAttributeMappings("cn:cn", "o:o");

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

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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
      setAttributeMappings("cn:cn", "emailAddress:mail");
    }
  }



  /**
   * Tests a failed mapping due to no mappable attributes in the certificate.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailedNoMappableAttributes()
         throws Exception
  {
    enableMapper();

    try
    {
      setAttributeMappings("emailAddress:mail");

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

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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
      setAttributeMappings("cn:cn", "emailAddress:mail");
    }
  }



  /**
   * Tests a failed mapping due to no matching users.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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
  @Test
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

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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
  @Test
  public void testFailedMappingNoUserBelowBaseDNs()
         throws Exception
  {
    enableMapper();

    try
    {
      setBaseDNs("dc=example,dc=com");

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

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
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
   * Tests to ensure that an attempt to remove the subject attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveMapAttribute()
         throws Exception
  {
    String mapperDN = "cn=Subject Attribute to User Attribute,cn=Certificate Mappers,cn=config";

    ModifyRequest modifyRequest =
        newModifyRequest(mapperDN).addModification(DELETE, "ds-cfg-subject-attribute-mapping");
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertNotSame(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Tests to ensure that an attempt to set an attribute mapping with no colon
   * will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingNoColon() throws Exception
  {
    setAttributeMappings("nocolon");
  }



  /**
   * Tests to ensure that an attempt to set an attribute mapping with no cert
   * attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingNoCertAttribute()
         throws Exception
  {
    setAttributeMappings(":cn");
  }



  /**
   * Tests to ensure that an attempt to set an attribute mapping with no user
   * attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingNoUserAttribute() throws Exception
  {
    setAttributeMappings("cn:");
  }



  /**
   * Tests to ensure that an attempt to set an attribute mapping with an
   * undefined user attribute will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingUndefinedUserAttribute() throws Exception
  {
    setAttributeMappings("cn:undefined");
  }



  /**
   * Tests to ensure that an attempt to set an attribute mapping with a
   * duplicate cert attribute mapping will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingDuplicateCertAttribute() throws Exception
  {
    setAttributeMappings("cn:cn", "cn:sn");
  }



  /**
   * Tests to ensure that an attempt to set an attribute mapping with a
   * duplicate user attribute mapping will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetMappingDuplicateUserAttribute() throws Exception
  {
    setAttributeMappings("cn:cn", "e:cn");
  }



  /**
   * Tests to ensure that an attempt to set an invalid base DN will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetInvalidBaseDN() throws Exception
  {
    setBaseDNs("invalid");
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

    assertModifyReplaceIsSuccess(externalDN, "ds-cfg-certificate-mapper", mapperDN);
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

    assertModifyReplaceIsSuccess(externalDN, "ds-cfg-certificate-mapper", mapperDN);
  }

  /**
   * Alters the configuration of the Subject Attribute to User Attribute
   * certificate mapper so that it will use the specified set of mappings.
   *
   * @param  mappings  The specified set of mappings to use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void setAttributeMappings(Object... mappings) throws Exception
  {
    String mapperDN = "cn=Subject Attribute to User Attribute," +
                      "cn=Certificate Mappers,cn=config";

    assertModifyReplaceIsSuccess(mapperDN, "ds-cfg-subject-attribute-mapping", mappings);
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
  private void setBaseDNs(Object... baseDNs) throws Exception
  {
    String mapperDN = "cn=Subject Attribute to User Attribute,cn=Certificate Mappers,cn=config";

    assertModifyReplaceIsSuccess(mapperDN, "ds-cfg-user-base-dn", baseDNs);
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
        "ds-privilege-name: config-read");

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
        "-Z",
        "-K", getKeyStorePath("client.keystore"),
        "-W", "password",
        "-P", getTrustStorePath(),
        "-r",
        "-b", "cn=config",
        "-s", "sub",
        "(objectClass=*)"
      };

      assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
    }
    finally
    {
      disableMapper();
    }
  }

  private void assertModifyReplaceIsSuccess(String dn, String attrName, Object... attrValues) throws DirectoryException
  {
    ModifyRequest modifyRequest = newModifyRequest(dn);
    if (attrValues != null)
    {
      modifyRequest.addModification(REPLACE, attrName, attrValues);
    }
    else
    {
      modifyRequest.addModification(REPLACE, attrName);
    }
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
}
