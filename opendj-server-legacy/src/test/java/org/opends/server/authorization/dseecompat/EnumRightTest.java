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
 * Copyright 2013 ForgeRock AS.
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
