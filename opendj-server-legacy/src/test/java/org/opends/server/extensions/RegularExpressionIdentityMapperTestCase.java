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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.LinkedList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.meta.
            RegularExpressionIdentityMapperCfgDefn;
import org.forgerock.opendj.server.config.server.RegularExpressionIdentityMapperCfg;
import org.opends.server.api.IdentityMapper;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import static org.testng.Assert.*;

/**
 * A set of test cases for the regular expression identity mapper.
 */
public class RegularExpressionIdentityMapperTestCase
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
   * Tests to ensure that the default regular expression identity mapper is
   * configured and enabled within the Directory Server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMapperEnabled()
         throws Exception
  {
    DN mapperDN =
         DN.valueOf("cn=Regular Expression,cn=Identity Mappers,cn=config");
    IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
    assertNotNull(mapper);
    assertTrue(mapper instanceof RegularExpressionIdentityMapper);
  }



  /**
   * Tests an invalid configuration due to a bad match pattern.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testConfigWithBadMatchPattern()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-pattern: :-(",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapperCfg configuration = getConfiguration(mapperEntry);
    RegularExpressionIdentityMapper mapper =
         new RegularExpressionIdentityMapper();
    assertFalse(mapper.isConfigurationAcceptable(configuration,
                                                 new LinkedList<LocalizableMessage>()));
    mapper.initializeIdentityMapper(configuration);
  }



  /**
   * Tests an invalid configuration due to an unknown attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testConfigWithUnknownAttributeType()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: unknown",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapperCfg configuration = getConfiguration(mapperEntry);
    RegularExpressionIdentityMapper mapper =
         new RegularExpressionIdentityMapper();
    assertFalse(mapper.isConfigurationAcceptable(configuration,
                                                 new LinkedList<LocalizableMessage>()));
    mapper.initializeIdentityMapper(configuration);
  }

  private RegularExpressionIdentityMapper initializeIdentityMapper(Entry mapperEntry) throws Exception {
    RegularExpressionIdentityMapperCfg configuration = getConfiguration(mapperEntry);
    RegularExpressionIdentityMapper mapper = new RegularExpressionIdentityMapper();
    assertTrue(mapper.isConfigurationAcceptable(configuration, new LinkedList<LocalizableMessage>()));
    mapper.initializeIdentityMapper(configuration);
    return mapper;
  }

  private RegularExpressionIdentityMapperCfg getConfiguration(Entry mapperEntry) throws ConfigException {
    return InitializationUtils.getConfiguration(RegularExpressionIdentityMapperCfgDefn.getInstance(), mapperEntry);
  }

  /**
   * Tests the {@code getEntryForID} method with a simple match with only one
   * entry, a single replacement in the regular expression, and with no search
   * base DN defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleMatchSingleReplacementWithoutBaseDN()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



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
    Entry mappedEntry = mapper.getEntryForID("test@example.com");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));

    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the {@code getEntryForID} method with a simple match with only one
   * entry, a single replacement in the regular expression, and with no search
   * base DN defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleMatchSingleReplacementMultipleAttributes()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: cn",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



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
    Entry mappedEntry = mapper.getEntryForID("test@example.com");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));

    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the {@code getEntryForID} method with a simple match with only one
   * entry, a single replacement in the regular expression, and with a search
   * base DN defined within the scope of the user entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleMatchSingleReplacementWithBaseDNInScope()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: o=test",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



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
    Entry mappedEntry = mapper.getEntryForID("test@example.com");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));

    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the {@code getEntryForID} method with a simple match with only one
   * entry, a single replacement in the regular expression, and with a search
   * base DN defined outside the scope of the user entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleMatchSingleReplacementWithBaseDNOutOfScope()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: dc=example,dc=com",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



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


    // Ensure that the identity mapper is not able to establish the mapping.
    Entry mappedEntry = mapper.getEntryForID("test@example.com");
    assertNull(mappedEntry);

    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the {@code getEntryForID} method with a simple match with only one
   * entry, a single replacement in the regular expression, and with multiple
   * base DNs, one of which is in the scope of the entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleMatchSingleReplacementWithMultipleBaseDNs()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-base-dn: dc=example,dc=com",
         "ds-cfg-match-base-dn: o=nonexistent",
         "ds-cfg-match-base-dn: o=test",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



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
    Entry mappedEntry = mapper.getEntryForID("test@example.com");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));

    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the {@code getEntryForID} method for the case in which the match
   * pattern doesn't match the ID string and therefore the ID string is left
   * unchanged.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMatchPatternDoesntMatch()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



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
   * Tests the {@code getEntryForID} method with a simple match in which no
   * replacement pattern is provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleMatchNoReplacePattern()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-pattern: @.+$");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



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
    Entry mappedEntry = mapper.getEntryForID("test@example.com");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test,o=test"));

    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the {@code getEntryForID} method with a simple match in which the
   * replace pattern expands the string rather than shortens it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleMatchReplacePatternExpandsString()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-pattern: ^(.*)$",
         "ds-cfg-replace-pattern: $1@example.com");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test@example.com,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test@example.com",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");


    // Ensure that the identity mapper is able to establish the mapping
    // successfully.
    Entry mappedEntry = mapper.getEntryForID("test");
    assertNotNull(mappedEntry);
    assertEquals(mappedEntry.getName(), DN.valueOf("uid=test@example.com,o=test"));

    mapper.finalizeIdentityMapper();
  }



  /**
   * Tests the {@code getEntryForID} method for a case in which multiple
   * matching entries are identified below a single base DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testMultipleMatchingEntriesBelowSingleBase()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-attribute: sn",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password",
         "",
         "dn: uid=anothertest,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: anothertest",
         "givenName: Another",
         "sn: Test",
         "cn: Anbother Test",
         "userPassword: password");


    // Try to establish the mapping and get an exception.
    try
    {
      mapper.getEntryForID("test@example.com");
    }
    finally
    {
      mapper.finalizeIdentityMapper();
    }
  }



  /**
   * Tests the {@code getEntryForID} method for a case in which multiple
   * matching entries are identified below different base DNs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testMultipleMatchingEntriesBelowMultipleBases()
         throws Exception
  {
    // Create the identity mapper with an appropriate configuration for this
    // test.
    Entry mapperEntry = TestCaseUtils.makeEntry(
         "dn: cn=Regular Expression,cn=Identity Mappers,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-identity-mapper",
         "objectClass: ds-cfg-regular-expression-identity-mapper",
         "cn: Regular Expression",
         "ds-cfg-java-class: " +
              "org.opends.server.extensions.RegularExpressionIdentityMapper",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-attribute: sn",
         "ds-cfg-match-base-dn: ou=Users 1,o=test",
         "ds-cfg-match-base-dn: ou=Users 2,o=test",
         "ds-cfg-match-pattern: ^([^@]+)@.+$",
         "ds-cfg-replace-pattern: $1");

    RegularExpressionIdentityMapper mapper = initializeIdentityMapper(mapperEntry);



    // Create a user entry and add it to the directory.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
         "dn: ou=Users 1,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: Users 1",
         "",
         "dn: uid=test,ou=Users 1,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password",
         "",
         "dn: ou=Users 2,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: Users 1",
         "",
         "dn: uid=test,ou=Users 2,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: Test",
         "cn: Test",
         "userPassword: password");


    // Try to establish the mapping and get an exception.
    try
    {
      mapper.getEntryForID("test@example.com");
    }
    finally
    {
      mapper.finalizeIdentityMapper();
    }
  }
}

