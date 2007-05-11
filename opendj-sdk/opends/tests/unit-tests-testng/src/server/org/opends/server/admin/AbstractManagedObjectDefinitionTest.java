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

import java.util.Collection;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * AbstractManagedObjectDefinition test cases.
 */
public class AbstractManagedObjectDefinitionTest {

  /**
   * A test managed object definition.
   */
  private static class TestDefinition extends AbstractManagedObjectDefinition {

    /**
     * Creates a new test definition.
     *
     * @param name
     *          The name of the test definition.
     * @param parent
     *          The parent definition (can be null).
     */
    @SuppressWarnings("unchecked")
    protected TestDefinition(String name, AbstractManagedObjectDefinition parent) {
      super(name, parent);
    }
  }

  // Test definitions.
  private TestDefinition top = new TestDefinition("top", null);

  private TestDefinition middle1 = new TestDefinition("middle1", top);

  private TestDefinition middle2 = new TestDefinition("middle2", top);

  private TestDefinition bottom1 = new TestDefinition("bottom1", middle1);

  private TestDefinition bottom2 = new TestDefinition("bottom2", middle1);

  private TestDefinition bottom3 = new TestDefinition("bottom3", middle1);



  /**
   * @return data for testIsChildOf.
   */
  @DataProvider(name = "testIsChildOf")
  public Object[][] createTestIsChildOf() {
    return new Object[][] { { top, top, true }, { middle1, middle1, true },
        { bottom1, bottom1, true }, { top, middle1, false },
        { top, bottom1, false }, { middle1, top, true },
        { bottom1, top, true }, { bottom1, middle1, true }, };
  }



  /**
   * Tests isChildOf method.
   *
   * @param d1
   *          The child definition.
   * @param d2
   *          The parent definition.
   * @param expected
   *          The expected result.
   */
  @SuppressWarnings("unchecked")
  @Test(dataProvider = "testIsChildOf")
  public void testIsChildOf(TestDefinition d1, TestDefinition d2,
      boolean expected) {
    assertEquals(d1.isChildOf(d2), expected);
  }



  /**
   * @return data for testIsParentOf.
   */
  @DataProvider(name = "testIsParentOf")
  public Object[][] createTestIsParentOf() {
    return new Object[][] { { top, top, true }, { middle1, middle1, true },
        { bottom1, bottom1, true }, { top, middle1, true },
        { top, bottom1, true }, { middle1, top, false },
        { bottom1, top, false }, { bottom1, middle1, false }, };
  }



  /**
   * Tests isParentOf method.
   *
   * @param d1
   *          The parent definition.
   * @param d2
   *          The child definition.
   * @param expected
   *          The expected result.
   */
  @SuppressWarnings("unchecked")
  @Test(dataProvider = "testIsParentOf")
  public void testIsParentOf(TestDefinition d1, TestDefinition d2,
      boolean expected) {
    assertEquals(d1.isParentOf(d2), expected);
  }



  /**
   * Tests getAllChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetAllChildren1() {
    Collection<AbstractManagedObjectDefinition> children = top.getAllChildren();
    assertEquals(children.size(), 5);
    assertTrue(children.contains(middle1));
    assertTrue(children.contains(middle2));
    assertTrue(children.contains(bottom1));
    assertTrue(children.contains(bottom2));
    assertTrue(children.contains(bottom3));
  }



  /**
   * Tests getAllChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetAllChildren2() {
    Collection<AbstractManagedObjectDefinition> children = middle1
        .getAllChildren();
    assertEquals(children.size(), 3);
    assertTrue(children.contains(bottom1));
    assertTrue(children.contains(bottom2));
    assertTrue(children.contains(bottom3));
  }



  /**
   * Tests getAllChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetAllChildren3() {
    Collection<AbstractManagedObjectDefinition> children = middle2
        .getAllChildren();
    assertEquals(children.size(), 0);
  }



  /**
   * Tests getChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetChildren1() {
    Collection<AbstractManagedObjectDefinition> children = top.getChildren();
    assertEquals(children.size(), 2);
    assertTrue(children.contains(middle1));
    assertTrue(children.contains(middle2));
  }



  /**
   * Tests getChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetChildren2() {
    Collection<AbstractManagedObjectDefinition> children = middle2
        .getChildren();
    assertEquals(children.size(), 0);
  }

}
