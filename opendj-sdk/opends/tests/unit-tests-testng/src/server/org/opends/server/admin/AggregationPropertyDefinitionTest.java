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

package org.opends.server.admin;



import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * AggregationPropertyDefinition Tester.
 */
@Test(sequential = true)
public class AggregationPropertyDefinitionTest extends DirectoryServerTestCase {

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
   * Tests that the
   * {@link AggregationPropertyDefinition#normalizeValue(String)}
   * works.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testNormalizeValue() throws Exception {
    TestChildCfgDefn d = TestChildCfgDefn.getInstance();
    AggregationPropertyDefinition<?, ?> pd = d
        .getAggregationPropertyPropertyDefinition();
    String nvalue = pd.normalizeValue("  LDAP   connection    handler  ");
    Assert.assertEquals(nvalue, "ldap connection handler");
  }



  /**
   * Tests that the
   * {@link AggregationPropertyDefinition#getChildDN(String)} works.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testGetChildDN() throws Exception {
    TestChildCfgDefn d = TestChildCfgDefn.getInstance();
    AggregationPropertyDefinition<?, ?> pd = d
        .getAggregationPropertyPropertyDefinition();
    DN expected = DN
        .decode("cn=ldap connection handler, cn=connection handlers, cn=config");
    DN actual = pd.getChildDN("  LDAP  connection handler  ");
    Assert.assertEquals(actual, expected);
  }



  /**
   * Tests that the
   * {@link AggregationPropertyDefinition#getChildPath(String)} works.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testGetChildPath() throws Exception {
    TestChildCfgDefn d = TestChildCfgDefn.getInstance();
    AggregationPropertyDefinition<?, ?> pd = d
        .getAggregationPropertyPropertyDefinition();
    ManagedObjectPath<?, ?> path = pd.getChildPath("LDAP connection handler");

    Assert.assertSame(path.getManagedObjectDefinition(), pd
        .getRelationDefinition().getChildDefinition());
    Assert.assertSame(path.getRelationDefinition(), pd.getRelationDefinition());
  }

}
