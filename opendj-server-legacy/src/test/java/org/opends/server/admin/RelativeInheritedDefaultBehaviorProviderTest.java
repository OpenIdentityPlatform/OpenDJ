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



import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;


/**
 * RelativeInheritedDefaultBehaviorProvider Tester.
 */
public class RelativeInheritedDefaultBehaviorProviderTest extends DirectoryServerTestCase {

  private static final int OFFSET = 0;

  private static final TestParentCfgDefn d;

  private RelativeInheritedDefaultBehaviorProvider<Boolean> ridbp;

  static
  {
    try
    {
      TestCaseUtils.startServer();
    } catch (Exception e) {
      e.printStackTrace();
    }
    d = TestParentCfgDefn.getInstance();
  }


  /**
   * Creates the default behavior provider.
   */
  @BeforeClass
  public void setUp() {
    this.ridbp = new RelativeInheritedDefaultBehaviorProvider<>(d, d
        .getMandatoryBooleanPropertyPropertyDefinition().getName(), OFFSET);
  }



  /**
   * Tests the accept method.
   */
  @Test
  public void testAccept() {
    ridbp.accept(new DefaultBehaviorProviderVisitor<Boolean, Object, Object>() {

      public Object visitAbsoluteInherited(
          AbsoluteInheritedDefaultBehaviorProvider d, Object o) {
        return null;
      }



      public Object visitAlias(AliasDefaultBehaviorProvider d, Object o) {
        return null;
      }



      public Object visitDefined(DefinedDefaultBehaviorProvider d, Object o) {
        return null;
      }



      public Object visitRelativeInherited(
          RelativeInheritedDefaultBehaviorProvider d, Object o) {
        return null;
      }



      public Object visitUndefined(UndefinedDefaultBehaviorProvider d, Object o) {
        return null;
      }
    }, new Object());
  }



  /**
   * Tests the getManagedObjectPath method.
   */
  @Test
  public void testGetManagedObjectPath() {
    assertEquals(ridbp.getManagedObjectPath(ManagedObjectPath.emptyPath()),
        ManagedObjectPath.emptyPath());
  }



  /**
   * Tests the getPropertyDefinition method.
   */
  @Test
  public void testGetPropertyDefinition() {
    assertEquals(ridbp.getPropertyName(), d
        .getMandatoryBooleanPropertyPropertyDefinition().getName());
  }



  /**
   * Tests the getRelativeOffset method.
   */
  @Test
  public void testGetRelativeOffset() {
    assertEquals(ridbp.getRelativeOffset(), OFFSET);
  }

}
