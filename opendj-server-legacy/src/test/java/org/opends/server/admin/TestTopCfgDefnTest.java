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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.admin;

import static org.testng.Assert.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** TopCfgDefn test cases. */
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

  /** Tests getInstance() does not return null. */
  @Test
  public void testGetInstance() {
    assertNotNull(TopCfgDefn.getInstance());
  }

  /** Tests getName() returns "top". */
  @Test
  public void testGetName() {
    assertEquals(TopCfgDefn.getInstance().getName(), "top");
  }

  /** Tests that there are no property definitions. */
  @Test
  public void testGetAllPropertyDefinitions() {
    assertTrue(TopCfgDefn.getInstance().getAllPropertyDefinitions().isEmpty());
  }

  /** Tests that there are no relation definitions. */
  @Test
  public void testGetAllRelationDefinitions() {
    assertTrue(TopCfgDefn.getInstance().getAllRelationDefinitions().isEmpty());
  }

  /** Tests that there are no constraints. */
  @Test
  public void testGetAllConstraints() {
    assertTrue(TopCfgDefn.getInstance().getAllConstraints().isEmpty());
  }

  /** Tests that there are no tags. */
  @Test
  public void testGetAllTags() {
    assertTrue(TopCfgDefn.getInstance().getAllTags().isEmpty());
  }

  /** Tests that there is no parent. */
  @Test
  public void testGetParent() {
    assertNull(TopCfgDefn.getInstance().getParent());
  }

  /** Tests that isTop returns true. */
  @Test
  public void testIsTop() {
    assertTrue(TopCfgDefn.getInstance().isTop());
  }

  /** Tests that getSynopsis throws an exception. */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetSynopsis() {
    assertNotNull(TopCfgDefn.getInstance().getSynopsis());
  }

  /** Tests that getDescription throws an exception. */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetDescription() {
    assertNotNull(TopCfgDefn.getInstance().getDescription());
  }

  /** Tests that getUserFriendlyName throws an exception. */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetUserFriendlyName() {
    assertNotNull(TopCfgDefn.getInstance().getUserFriendlyName());
  }

  /** Tests that getUserFriendlyPluralName throws an exception. */
  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testGetUserFriendlyPluralName() {
    assertNotNull(TopCfgDefn.getInstance().getUserFriendlyPluralName());
  }

  /** Tests that there are children. */
  @Test
  public void testGetAllChildren() {
    assertFalse(TopCfgDefn.getInstance().getAllChildren().isEmpty());
  }
}
