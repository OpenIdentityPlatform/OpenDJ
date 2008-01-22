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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.ExactMatchIdentityMapperCfgDefn;
import org.opends.server.admin.std.server.ExactMatchIdentityMapperCfg;
import org.opends.server.api.IdentityMapper;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of test cases for the exact match identity mapper.
 */
public class ExactMatchIdentityMapperTestCase
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
   * Retrieves a set of valid configuration entries that may be used to
   * initialize this identity mapper.
   *
   * @return  A set of valid configuration entries that may be used to
   *          initialize this identity mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "",
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: ",
         "",
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: o=test",
         "",
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-attribute: sn",
         "ds-cfg-match-base-dn: o=test"
      );


    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid");

    AttributeType t = DirectoryServer.getAttributeType("ds-cfg-match-base-dn");
    e.addAttribute(new Attribute(t), new ArrayList<AttributeValue>());
    entries.add(e);


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests to ensure that the identity mapper can be initialized and finalized
   * using various valid configurations.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testValidConfigs(Entry e)
         throws Exception
  {
    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(), e);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);
    mapper.finalizeIdentityMapper();
  }



  /**
   * Retrieves a set of invalid configuration entries that should cause the
   * identity mapper initialization to fail.
   *
   * @return  A set of invalid configuration entries that should cause the
   *          identity mapper initialization to fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Exact Match 1,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: undefinedAttribute",
         "",
         "dn: cn=Exact Match 2,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: invalidDN",
         "",
         "dn: cn=Exact Match 3,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: ",
         "ds-cfg-match-base-dn: o=test"
      );


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests to ensure that the identity mapper can be initialized and finalized
   * using various valid configurations.
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
    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(), e);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);
  }



  /**
   * Tests to ensure that the exact match identity mapper is configured and
   * enabled within the Directory Server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMapperEnabled()
         throws Exception
  {
    DN mapperDN = DN.decode("cn=Exact Match,cn=Identity Mappers,cn=config");
    IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
    assertNotNull(mapper);
    assertTrue(mapper instanceof ExactMatchIdentityMapper);
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * with only one entry and with no search base DN defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleMatchWithoutBaseDN()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * with only one entry and with a valid search base DN defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleMatchWithValidBaseDN()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: o=test");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * with only one entry and with an search base DN defined that doesn't exist
   * in the directory.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleMatchWithInvalidBaseDN()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: o=notdefined");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNull(mappedEntry);
    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * that doesn't match any user in the directory.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleMismatch()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: o=test");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("nottest");
    assertNull(mappedEntry);
    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * that matches multiple entries in the directory.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDuplicateMatch()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: cn",
         "ds-cfg-match-base-dn: o=test");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create two user entries and add them to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test2,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test2",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");
    addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    try
    {
      mapper.getEntryForID("test");
    }
    finally
    {
      mapper.finalizeIdentityMapper();
    }
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a compound filter that
   * matches the first attribute in the list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompoundFirstMatch()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-attribute: cn");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=foo,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: foo",
         "givenName: Foo",
         "sn: Bar",
         "cn: Bar",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("foo");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=foo,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a compound filter that
   * matches the second attribute in the list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompoundSecondMatch()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-attribute: cn");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=foo,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: foo",
         "givenName: Foo",
         "sn: Bar",
         "cn: Bar",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("bar");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=foo,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a compound filter doesn't
   * match any attribute in the list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompoundMismatch()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Exact Match,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-exact-match-identity-mapper",
         "cn: Exact Match",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.ExactMatchIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-attribute: cn");

    ExactMatchIdentityMapperCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              ExactMatchIdentityMapperCfgDefn.getInstance(),
              mapperEntry);
    ExactMatchIdentityMapper mapper = new ExactMatchIdentityMapper();
    mapper.initializeIdentityMapper(configuration);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=foo,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: foo",
         "givenName: Foo",
         "sn: Bar",
         "cn: Bar",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNull(mappedEntry);


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests that an internal modification to change the map attribute will take
   * effect immediately.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testChangingMapAttribute()
         throws Exception
  {
    String mapperDNString = "cn=Exact Match,cn=Identity Mappers,cn=config";
    DN mapperDN = DN.decode(mapperDNString);
    IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
    assertNotNull(mapper);
    assertTrue(mapper instanceof ExactMatchIdentityMapper);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Verify that "test" works for the initial configuration but "test user"
    // does not.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));

    mappedEntry = mapper.getEntryForID("test user");
    assertNull(mappedEntry);


    // Create a modification to change the map attribute from uid to cn.
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("cn"));

    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE,
                                  new LDAPAttribute("ds-cfg-match-attribute",
                                                    values)));
    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Verify that "test" no longer works but "test user" does.
    mappedEntry = mapper.getEntryForID("test");
    assertNull(mappedEntry);

    mappedEntry = mapper.getEntryForID("test user");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));


    // Change the configuration back to the way it was.
    values.set(0, new ASN1OctetString("uid"));
    modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Verify that the original matching pattern is back.
    mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));

    mappedEntry = mapper.getEntryForID("test user");
    assertNull(mappedEntry);
  }



  /**
   * Tests that an internal modification to change the map base DN will take
   * effect immediately.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testChangingMapBaseDN()
         throws Exception
  {
    String mapperDNString = "cn=Exact Match,cn=Identity Mappers,cn=config";
    DN mapperDN = DN.decode(mapperDNString);
    IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
    assertNotNull(mapper);
    assertTrue(mapper instanceof ExactMatchIdentityMapper);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Verify that we can retrieve the user.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));


    // Create a modification to set the map base DN to "dc=example,dc=com".
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("dc=example,dc=com"));

    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE,
                                  new LDAPAttribute("ds-cfg-match-base-dn",
                                                    values)));
    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Verify that we can't find the user anymore.
    mappedEntry = mapper.getEntryForID("test");
    assertNull(mappedEntry);


    // Change the base DN to "o=test".
    values.set(0, new ASN1OctetString("o=test"));
    modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Verify that we can retrieve the user again.
    mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));


    // Change the configuration back to its original setting.
    values.clear();
    modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Verify that we can still retrieve the user.
    mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getDN(), DN.decode("uid=test,o=test"));
  }



  /**
   * Tests that an internal modification to remove the match attribute will be
   * rejected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectChangingToNoMatchAttr()
         throws Exception
  {
    // Create a modification to remove the match attribute.
    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE,
                                  new LDAPAttribute("ds-cfg-match-attribute")));
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String mapperDNString = "cn=Exact Match,cn=Identity Mappers,cn=config";
    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests that an internal modification to change the match attribute to an
   * undefined type will be rejected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectChangingToInvalidMatchAttr()
         throws Exception
  {
    // Create a modification to remove the match attribute.
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("undefinedAttribute"));

    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE,
                                  new LDAPAttribute("ds-cfg-match-attribute",
                                                    values)));
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String mapperDNString = "cn=Exact Match,cn=Identity Mappers,cn=config";
    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests that an internal modification to change the match base DN to an
   * invalid value will be rejected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectChangingToInvalidMatchBaseDN()
         throws Exception
  {
    // Create a modification to remove the match attribute.
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("invalidDN"));

    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE,
                                  new LDAPAttribute("ds-cfg-match-base-dn",
                                                    values)));
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String mapperDNString = "cn=Exact Match,cn=Identity Mappers,cn=config";
    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(mapperDNString), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
  }
}

