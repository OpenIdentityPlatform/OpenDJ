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



import static org.testng.Assert.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * TopCfgDefn test cases.
 */
@Test(sequential = true)
public class TestTopCfgDefnTest extends DirectoryServerTestCase {

  /**
   * Sets up tests
   *
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the admin framework
    // initialized.
    TestCaseUtils.startServer();
  }



  /**
   * Tests getInstance() does not return null.
   */
  @Test
  public void testGetInstance() {
    assertNotNull(TopCfgDefn.getInstance());
  }



  /**
   * Tests getName() returns "top".
   */
  @Test
  public void testGetName() {
    assertEquals(TopCfgDefn.getInstance().getName(), "top");
  }



  /**
   * Tests that there are no property definitions.
   */
  @Test
  public void testGetAllPropertyDefinitions() {
    assertTrue(TopCfgDefn.getInstance().getAllPropertyDefinitions().isEmpty());
  }



  /**
   * Tests that there are no relation definitions.
   */
  @Test
  public void testGetAllRelationDefinitions() {
    assertTrue(TopCfgDefn.getInstance().getAllRelationDefinitions().isEmpty());
  }



  /**
   * Tests that there are no constraints.
   */
  @Test
  public void testGetAllConstraints() {
    assertTrue(TopCfgDefn.getInstance().getAllConstraints().isEmpty());
  }



  /**
   * Tests that there are no tags.
   */
  @Test
  public void testGetAllTags() {
    assertTrue(TopCfgDefn.getInstance().getAllTags().isEmpty());
  }



  /**
   * Tests that there is no parent.
   */
  @Test
  public void testGetParent() {
    assertNull(TopCfgDefn.getInstance().getParent());
  }



  /**
   * Tests that isTop returns true.
   */
  @Test
  public void testIsTop() {
    assertTrue(TopCfgDefn.getInstance().isTop());
  }



  /**
   * Tests that getSynopsis throws an exception.
   */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetSynopsis() {
    assertNotNull(TopCfgDefn.getInstance().getSynopsis());
  }



  /**
   * Tests that getDescription throws an exception.
   */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetDescription() {
    assertNotNull(TopCfgDefn.getInstance().getDescription());
  }



  /**
   * Tests that getUserFriendlyName throws an exception.
   */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetUserFriendlyName() {
    assertNotNull(TopCfgDefn.getInstance().getUserFriendlyName());
  }



  /**
   * Tests that getUserFriendlyPluralName throws an exception.
   */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetUserFriendlyPluralName() {
    assertNotNull(TopCfgDefn.getInstance().getUserFriendlyPluralName());
  }



  /**
   * Tests that there are children.
   */
  @Test
  public void testGetAllChildren() {
    assertTrue(TopCfgDefn.getInstance().getAllChildren().size() > 0);
  }

}
