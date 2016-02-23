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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * This test tests the ACI targattrfilters syntax.
 */
public class TargAttrFiltersTestCase  extends AciTestCase {

  /** Valid targattrfilters statements. Not the complete ACI. */
  @DataProvider(name = "validStatements")
  public Object[][] valids() {
    return new Object[][] {
            {"add=st:(st=*),del=st:(st=*)"},
            {"add=st:(st=*) && cn:(cn=c*), del=st:(st=*) && sn:(sn=s*)"},
            {"add=st:(st=*)"},
            {"del=st:(st=*)"},
    };
  }

  /** Invalid targattrfilters statements. */
  @DataProvider(name = "invalidStatements")
  public Object[][] invalids() {
    return new Object[][] {
            {"add=st:(st=*),deeeel =st:(st=*)"},
            {"add=st:(st=*),foo =st:(st=*)"},
            {"add=st:(st=*),,,del=st:(st=*)"},
            {"add=st:(st=*),dellll =st:(st=*)"},
            {"add=st:(st=*)del=st:(st=*)"},
            {"add=st:(st=*),add=st:(st=*)"},
            {"add=st:(st=*),del=st:(st=*),add=st:(st=*)"},
            {"add=st:(st=*),del=cn:(st=*)"},
            {"add=st:(st=*) && cn:(cn=c*), del=st:(st=*) && l:(cn=c*)"},
    };
  }

  /**
   * Test valid targattrfilters statements. All should pass.
   * @param statement The statement string.
   * @throws Exception If a valid statement fails to parse.
   */
  @Test(dataProvider = "validStatements")
  public void testValidStatements(String statement)
          throws Exception {
      TargAttrFilters.decode(EnumTargetOperator.EQUALITY, statement);
  }

  /**
   * Test invalid targattrfilters statemnents. All should fail to parse.
   * @param statement The statement string.
   * @throws Exception If an invalid statement parses.
   */
  @Test(expectedExceptions= AciException.class, dataProvider="invalidStatements")
  public void testInvalidStatements(String statement)  throws Exception {
    try {
      TargAttrFilters.decode(EnumTargetOperator.EQUALITY,statement);
    } catch (AciException e) {
      throw e;
    } catch (Exception e) {
      System.out.println(
              "Invalid Aci  <" + statement + "> threw wrong exception type.");
      throw e;
    }
    throw new RuntimeException(
            "Invalid aci <" + statement + "> did not throw an exception.");
  }
}
