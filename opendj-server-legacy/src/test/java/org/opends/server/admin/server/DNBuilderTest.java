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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.admin.server;



import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestChildCfg;
import org.opends.server.admin.TestChildCfgClient;
import org.opends.server.admin.TestChildCfgDefn;
import org.opends.server.admin.TestParentCfgDefn;
import org.opends.server.types.DN;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Test cases for the server DNBuilder class.
 */
public final class DNBuilderTest extends AdminTestCase {

  /**
   * Sets up tests
   *
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();
    TestCfg.setUp();
  }



  /**
   * Tears down test environment.
   */
  @AfterClass
  public void tearDown() {
    TestCfg.cleanup();
  }



  /**
   * Tests construction of a DN from a managed object path containing
   * a subordinate one-to-many relationship.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testCreateOneToMany() throws Exception {
    // First create the path.
    ManagedObjectPath<? extends ConfigurationClient, ? extends Configuration> path = ManagedObjectPath
        .emptyPath();

    path = path.child(TestCfg.getTestOneToManyParentRelationDefinition(), "test-parent-1");
    path = path.child(TestParentCfgDefn.getInstance()
        .getTestChildrenRelationDefinition(), "test-child-1");

    // Now serialize it.
    DN actual = DNBuilder.create(path);
    DN expected = DN
        .valueOf("cn=test-child-1,cn=test children,cn=test-parent-1,cn=test parents,cn=config");

    assertEquals(actual, expected);
  }



  /**
   * Tests construction of a DN from a managed object path containing
   * a subordinate one-to-one relationship.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testCreateOneToOne() throws Exception {
    // First create the path.
    ManagedObjectPath<? extends ConfigurationClient, ? extends Configuration> path = ManagedObjectPath
        .emptyPath();

    SingletonRelationDefinition.Builder<TestChildCfgClient, TestChildCfg> b =
      new SingletonRelationDefinition.Builder<>(
        TestParentCfgDefn.getInstance(), "singleton-test-child",
        TestChildCfgDefn.getInstance());
    final SingletonRelationDefinition<TestChildCfgClient, TestChildCfg> r2 = b.getInstance();
    LDAPProfile.Wrapper wrapper = new LDAPProfile.Wrapper() {

      /** {@inheritDoc} */
      @Override
      public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
        if (r == r2) {
          return "cn=singleton-test-child";
        } else {
          return null;
        }
      }
    };

    path = path.child(TestCfg.getTestOneToManyParentRelationDefinition(), "test-parent-1");
    path = path.child(r2);

    // Now serialize it.
    LDAPProfile.getInstance().pushWrapper(wrapper);
    try {
      DN actual = DNBuilder.create(path);
      DN expected = DN
          .valueOf("cn=singleton-test-child,cn=test-parent-1,cn=test parents,cn=config");

      assertEquals(actual, expected);
    } finally {
      LDAPProfile.getInstance().popWrapper();
    }
  }



  /**
   * Tests construction of a DN from a managed object path containing
   * a subordinate one-to-zero-or-one relationship.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testCreateOneToZeroOrOne() throws Exception {
    // First create the path.
    ManagedObjectPath<? extends ConfigurationClient, ? extends Configuration> path = ManagedObjectPath
        .emptyPath();

    path = path.child(TestCfg.getTestOneToManyParentRelationDefinition(), "test-parent-1");
    path = path.child(TestParentCfgDefn.getInstance()
        .getOptionalTestChildRelationDefinition());

    // Now serialize it.
    DN actual = DNBuilder.create(path);
    DN expected = DN
        .valueOf("cn=optional test child,cn=test-parent-1,cn=test parents,cn=config");

    assertEquals(actual, expected);
  }

}
