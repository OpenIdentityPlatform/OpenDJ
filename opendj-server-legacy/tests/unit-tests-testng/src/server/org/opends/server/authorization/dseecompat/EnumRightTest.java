/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.authorization.dseecompat.EnumRight.*;
import static org.testng.Assert.*;

import java.util.EnumSet;
import java.util.Set;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EnumRightTest extends DirectoryServerTestCase
{

  private static final int ALL_RIGHTS_MASK = ACI_READ | ACI_WRITE | ACI_ADD
      | ACI_DELETE | ACI_SEARCH | ACI_COMPARE | ACI_SELF;

  @DataProvider(name = "aciRightsToEnumRights")
  public Object[][] aciRightsToEnumRights()
  {
    return new Object[][] {
      { ACI_NULL, EnumSet.noneOf(EnumRight.class) },
      { ACI_READ, EnumSet.of(READ) },
      { ACI_WRITE, EnumSet.of(WRITE) },
      { ACI_ADD, EnumSet.of(ADD) },
      { ACI_DELETE, EnumSet.of(DELETE) },
      { ACI_SEARCH, EnumSet.of(SEARCH) },
      { ACI_COMPARE, EnumSet.of(COMPARE) },
      { ACI_SELF, EnumSet.of(SELFWRITE) },
      { ALL_RIGHTS_MASK, EnumSet.of(ALL) },
      { ACI_ALL, EnumSet.of(ALL) },
      { ACI_EXPORT , EnumSet.of(EXPORT) },
      { ACI_IMPORT, EnumSet.of(IMPORT) },
      { ACI_PROXY, EnumSet.of(PROXY) },
      { ACI_EXPORT | ACI_IMPORT, EnumSet.of(EXPORT, IMPORT) },
      { ACI_ALL | ACI_EXPORT | ACI_IMPORT, EnumSet.of(ALL, EXPORT, IMPORT) },
    };
  }

  @Test
  public void aciAllValue() throws Exception
  {
    assertEquals(ALL_RIGHTS_MASK, ACI_ALL);
  }

  @Test(dependsOnMethods = "aciAllValue", dataProvider = "aciRightsToEnumRights")
  public void getEnumRight(int aciRightsMask, Set<EnumRight> enumRightSet) throws Exception
  {
    assertEquals(EnumRight.getEnumRight(aciRightsMask), enumRightSet);
  }
}
