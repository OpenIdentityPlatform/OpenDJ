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
package org.opends.server.extensions;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.ExactMatchIdentityMapperCfgDefn;
import org.opends.server.api.IdentityMapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

/** A set of test cases for the exact match identity mapper. */
public class ExactMatchIdentityMapperTestCase
       extends ExtensionsTestCase
{
  private static final String MAPPER_DN = "cn=Exact Match,cn=Identity Mappers,cn=config";

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

    AttributeType t = DirectoryServer.getSchema().getAttributeType("ds-cfg-match-base-dn");
    e.addAttribute(Attributes.empty(t), new ArrayList<ByteString>());
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
    ExactMatchIdentityMapper mapper = initializeIdentityMapper(e);
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
    initializeIdentityMapper(e);
  }



  /**
   * Tests to ensure that the exact match identity mapper is configured and
   * enabled within the Directory Server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMapperEnabled()
         throws Exception
  {
    DN mapperDN = DN.valueOf(MAPPER_DN);
    IdentityMapper<?> mapper = DirectoryServer.getIdentityMapper(mapperDN);
    assertNotNull(mapper);
    assertTrue(mapper instanceof ExactMatchIdentityMapper);
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * with only one entry and with no search base DN defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * with only one entry and with a valid search base DN defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a simple equality match
   * with only one entry and with an search base DN defined that doesn't exist
   * in the directory.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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
  @Test
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create two user entries and add them to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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

    TestCaseUtils.addEntry(
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
  @Test
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("foo");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=foo,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a compound filter that
   * matches the second attribute in the list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("bar");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=foo,o=test"));


    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the <CODE>getEntryForID</CODE> method with a compound filter doesn't
   * match any attribute in the list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ExactMatchIdentityMapper mapper = initializeIdentityMapper(mapperEntry);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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

    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNull(mappedEntry);


    mapper.finalizeIdentityMapper();
  }

  private ExactMatchIdentityMapper initializeIdentityMapper(Entry mapperEntry) throws Exception {
    return InitializationUtils.initializeIdentityMapper(
        new ExactMatchIdentityMapper(), mapperEntry, ExactMatchIdentityMapperCfgDefn.getInstance());
  }

  /**
   * Tests that an internal modification to change the map attribute will take
   * effect immediately.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testChangingMapAttribute()
         throws Exception
  {
    DN mapperDN = DN.valueOf(MAPPER_DN);
    IdentityMapper<?> mapper = DirectoryServer.getIdentityMapper(mapperDN);
    assertNotNull(mapper);
    assertTrue(mapper instanceof ExactMatchIdentityMapper);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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


    // Verify that "test" works for the initial configuration but "test user"
    // does not.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));

    mappedEntry = mapper.getEntryForID("test user");
    assertNull(mappedEntry);


    // Create a modification to change the map attribute from uid to cn.

    ModifyRequest modifyRequest = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-attribute", "cn");
    processModifyIsSuccessful(modifyRequest);

    // Verify that "test" no longer works but "test user" does.
    mappedEntry = mapper.getEntryForID("test");
    assertNull(mappedEntry);

    mappedEntry = mapper.getEntryForID("test user");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));


    // Change the configuration back to the way it was.
    ModifyRequest modifyRequest2 = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-attribute", "uid");
    processModifyIsSuccessful(modifyRequest2);


    // Verify that the original matching pattern is back.
    mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));

    mappedEntry = mapper.getEntryForID("test user");
    assertNull(mappedEntry);
  }



  /**
   * Tests that an internal modification to change the map base DN will take
   * effect immediately.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testChangingMapBaseDN()
         throws Exception
  {
    DN mapperDN = DN.valueOf(MAPPER_DN);
    IdentityMapper<?> mapper = DirectoryServer.getIdentityMapper(mapperDN);
    assertNotNull(mapper);
    assertTrue(mapper instanceof ExactMatchIdentityMapper);


    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
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


    // Verify that we can retrieve the user.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));


    // Create a modification to set the map base DN to "dc=example,dc=com".
    ModifyRequest modifyRequest = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-base-dn", "dc=example,dc=com");
    processModifyIsSuccessful(modifyRequest);


    // Verify that we can't find the user anymore.
    mappedEntry = mapper.getEntryForID("test");
    assertNull(mappedEntry);


    // Change the base DN to "o=test".
    modifyRequest = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-base-dn", "o=test");
    processModifyIsSuccessful(modifyRequest);


    // Verify that we can retrieve the user again.
    mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));


    // Change the configuration back to its original setting.
    modifyRequest = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-base-dn");
    processModifyIsSuccessful(modifyRequest);


    // Verify that we can still retrieve the user.
    mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));
  }

  /**
   * Tests that an internal modification to remove the match attribute will be
   * rejected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectChangingToNoMatchAttr()
         throws Exception
  {
    // Create a modification to remove the match attribute.
    ModifyRequest modifyRequest = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-attribute");
    processModifyIsNotSuccessful(modifyRequest);
  }



  /**
   * Tests that an internal modification to change the match attribute to an
   * undefined type will be rejected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectChangingToInvalidMatchAttr()
         throws Exception
  {
    // Create a modification to remove the match attribute.
    ModifyRequest modifyRequest = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-attribute", "undefinedAttribute");
    processModifyIsNotSuccessful(modifyRequest);
  }



  /**
   * Tests that an internal modification to change the match base DN to an
   * invalid value will be rejected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectChangingToInvalidMatchBaseDN()
         throws Exception
  {
    // Create a modification to remove the match attribute.
    ModifyRequest modifyRequest = newModifyRequest(MAPPER_DN)
        .addModification(REPLACE, "ds-cfg-match-base-dn", "invalidDN");
    processModifyIsNotSuccessful(modifyRequest);
  }

  private void processModifyIsSuccessful(ModifyRequest modifyRequest)
  {
    ModifyOperation op = getRootConnection().processModify(modifyRequest);
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
  }

  private void processModifyIsNotSuccessful(ModifyRequest modifyRequest)
  {
    ModifyOperation op = getRootConnection().processModify(modifyRequest);
    assertNotSame(op.getResultCode(), ResultCode.SUCCESS);
  }
}
