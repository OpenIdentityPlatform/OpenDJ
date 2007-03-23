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

import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

/**
 * RelativeInheritedDefaultBehaviorProvider Tester.
 */
public class RelativeInheritedDefaultBehaviorProviderTest {

  static private final String PROPERTY_NAME = "test-property";
  static private final int OFFSET = 0;

  private RelativeInheritedDefaultBehaviorProvider<Object> ridbp = null;

  @BeforeClass
  public void setUp() {
    this.ridbp = new RelativeInheritedDefaultBehaviorProvider<Object>(OFFSET, PROPERTY_NAME);
  }

  @Test
  public void testAccept() {
    ridbp.accept(new DefaultBehaviorProviderVisitor<Object,Object,Object>(){

      public Object visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider d, Object o) {
        return null;
      }

      public Object visitAlias(AliasDefaultBehaviorProvider d, Object o) {
        return null;
      }

      public Object visitDefined(DefinedDefaultBehaviorProvider d, Object o) {
        return null;
      }

      public Object visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider d, Object o) {
        return null;
      }

      public Object visitUndefined(UndefinedDefaultBehaviorProvider d, Object o) {
        return null;
      }
    }, new Object());
  }

  @Test
  public void testGetManagedObjectPath() {
    ridbp.getManagedObjectPath(ManagedObjectPath.emptyPath());
  }

  @Test
  public void testGetPropertyName() {
    assertEquals(ridbp.getPropertyName(), PROPERTY_NAME);
  }

  @Test
  public void testGetRelativeOffset() {
    assertEquals(ridbp.getRelativeOffset(), OFFSET);
  }

}
