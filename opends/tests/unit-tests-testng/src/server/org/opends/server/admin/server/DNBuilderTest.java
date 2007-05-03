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
package org.opends.server.admin.server;



import static org.testng.Assert.assertEquals;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.types.DN;
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
    ManagedObjectPath<? extends ConfigurationClient, ? extends Configuration> path = ManagedObjectPath.emptyPath();

    InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> r1 = new InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg>(
        RootCfgDefn.getInstance(), "test-parent", "test-parents",
        TestParentCfgDefn.getInstance());

    InstantiableRelationDefinition<TestChildCfgClient, TestChildCfg> r2 = new InstantiableRelationDefinition<TestChildCfgClient, TestChildCfg>(
        TestParentCfgDefn.getInstance(), "test-child",
        "test-children", TestChildCfgDefn.getInstance());

    path = path.child(r1, "test-parent-1");
    path = path.child(r2, "test-child-1");

    // Now serialize it.
    DNBuilder builder = new DNBuilder(new MockLDAPProfile());
    path.serialize(builder);
    DN actual = builder.getInstance();
    DN expected = DN
        .decode("cn=test-child-1,cn=test-children,cn=test-parent-1,cn=test-parents");

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
    ManagedObjectPath<? extends ConfigurationClient, ? extends Configuration> path = ManagedObjectPath.emptyPath();

    InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> r1 = new InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg>(
        RootCfgDefn.getInstance(), "test-parent", "test-parents",
        TestParentCfgDefn.getInstance());

    SingletonRelationDefinition<TestChildCfgClient, TestChildCfg> r2 = new SingletonRelationDefinition<TestChildCfgClient, TestChildCfg>(
        TestParentCfgDefn.getInstance(), "singleton-test-child",
        TestChildCfgDefn.getInstance());

    path = path.child(r1, "test-parent-1");
    path = path.child(r2);

    // Now serialize it.
    DNBuilder builder = new DNBuilder(new MockLDAPProfile());
    path.serialize(builder);
    DN actual = builder.getInstance();
    DN expected = DN
        .decode("cn=singleton-test-child,cn=test-parent-1,cn=test-parents");

    assertEquals(actual, expected);
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
    ManagedObjectPath<? extends ConfigurationClient, ? extends Configuration> path = ManagedObjectPath.emptyPath();

    InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> r1 = new InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg>(
        RootCfgDefn.getInstance(), "test-parent", "test-parents",
        TestParentCfgDefn.getInstance());

    OptionalRelationDefinition<TestChildCfgClient, TestChildCfg> r2 = new OptionalRelationDefinition<TestChildCfgClient, TestChildCfg>(
        TestParentCfgDefn.getInstance(), "optional-test-child",
        TestChildCfgDefn.getInstance());

    path = path.child(r1, "test-parent-1");
    path = path.child(r2);

    // Now serialize it.
    DNBuilder builder = new DNBuilder(new MockLDAPProfile());
    path.serialize(builder);
    DN actual = builder.getInstance();
    DN expected = DN
        .decode("cn=optional-test-child,cn=test-parent-1,cn=test-parents");

    assertEquals(actual, expected);
  }

}
