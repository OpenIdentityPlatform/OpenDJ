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

package org.opends.server.authorization.dseecompat;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.opends.server.TestCaseUtils;


/**
 * This test tests the ACI targattrfilters syntax.
 */
public class TargAttrFiltersTestCase  extends AciTestCase {

    @BeforeClass
    public void startServer() throws Exception {
      TestCaseUtils.startServer();
   }

  //Valid targattrfilters statements. Not the complete ACI.
  @DataProvider(name = "validStatements")
  public Object[][] valids() {
    return new Object[][] {
            {"add=st:(st=*),del=st:(st=*)"},
            {"add=st:(st=*) && cn:(cn=c*), del=st:(st=*) && sn:(sn=s*)"},
    };
  }

  //Invalid targattrfilters statements.
  @DataProvider(name = "invalidStatements")
  public Object[][] invalids() {
    return new Object[][] {
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
