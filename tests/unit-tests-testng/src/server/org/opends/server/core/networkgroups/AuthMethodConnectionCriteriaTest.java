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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.NetworkGroupCfgDefn.AllowedAuthMethod;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Unit tests for AuthMethodConnectionCriteria.
 */
public class AuthMethodConnectionCriteriaTest extends
    DirectoryServerTestCase
{

  /**
   * Sets up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           if the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Returns test data for the following test cases.
   *
   * @return The test data for the following test cases.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @DataProvider(name = "testData")
  public Object[][] createTestData() throws Exception
  {
    return new Object[][] {
        { AllowedAuthMethod.ANONYMOUS,
            Collections.singleton(AllowedAuthMethod.ANONYMOUS), true },
        { AllowedAuthMethod.ANONYMOUS,
            Collections.singleton(AllowedAuthMethod.SIMPLE), false },
        { AllowedAuthMethod.ANONYMOUS,
            Collections.singleton(AllowedAuthMethod.SASL), false },
        { AllowedAuthMethod.SIMPLE,
            Collections.singleton(AllowedAuthMethod.ANONYMOUS), false },
        { AllowedAuthMethod.SIMPLE,
            Collections.singleton(AllowedAuthMethod.SIMPLE), true },
        { AllowedAuthMethod.SIMPLE,
            Collections.singleton(AllowedAuthMethod.SASL), false },
        { AllowedAuthMethod.SASL,
            Collections.singleton(AllowedAuthMethod.ANONYMOUS), false },
        { AllowedAuthMethod.SASL,
            Collections.singleton(AllowedAuthMethod.SIMPLE), false },
        { AllowedAuthMethod.SASL,
            Collections.singleton(AllowedAuthMethod.SASL), true },
        { AllowedAuthMethod.ANONYMOUS,
            EnumSet.noneOf(AllowedAuthMethod.class), false },
        { AllowedAuthMethod.SIMPLE,
            EnumSet.noneOf(AllowedAuthMethod.class), false },
        { AllowedAuthMethod.SASL,
            EnumSet.noneOf(AllowedAuthMethod.class), false },
        { AllowedAuthMethod.ANONYMOUS,
            EnumSet.allOf(AllowedAuthMethod.class), true },
        { AllowedAuthMethod.SIMPLE,
            EnumSet.allOf(AllowedAuthMethod.class), true },
        { AllowedAuthMethod.SASL,
            EnumSet.allOf(AllowedAuthMethod.class), true }, };
  }



  /**
   * Tests the matches method.
   *
   * @param clientAuthMethod
   *          The client authentication method.
   * @param allowedAuthMethods
   *          The set of allowed authentication methods.
   * @param expectedResult
   *          The expected result.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testData")
  public void testMatches(AllowedAuthMethod clientAuthMethod,
      Collection<AllowedAuthMethod> allowedAuthMethods,
      boolean expectedResult) throws Exception
  {
    DN bindDN;

    if (clientAuthMethod == AllowedAuthMethod.ANONYMOUS)
    {
      bindDN = DN.nullDN();
    }
    else
    {
      bindDN =
          DN.decode("cn=Directory Manager, cn=Root DNs, cn=config");
    }

    ClientConnection client =
        new MockClientConnection(12345, false, bindDN, clientAuthMethod);

    AuthMethodConnectionCriteria criteria =
        new AuthMethodConnectionCriteria(allowedAuthMethods);
    Assert.assertEquals(criteria.matches(client), expectedResult);
  }



  /**
   * Tests the willMatchAfterBind method.
   *
   * @param clientAuthMethod
   *          The client authentication method.
   * @param allowedAuthMethods
   *          The set of allowed authentication methods.
   * @param expectedResult
   *          The expected result.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testData")
  public void testWillMatchAfterBind(
      AllowedAuthMethod clientAuthMethod,
      Collection<AllowedAuthMethod> allowedAuthMethods,
      boolean expectedResult) throws Exception
  {
    ClientConnection client =
        new MockClientConnection(12345, false, DN.nullDN(),
            AllowedAuthMethod.ANONYMOUS);

    AuthenticationType authType;
    DN bindDN;

    switch (clientAuthMethod)
    {
    case ANONYMOUS:
      authType = null;
      bindDN = DN.nullDN();
      break;
    case SIMPLE:
      authType = AuthenticationType.SIMPLE;
      bindDN =
          DN.decode("cn=Directory Manager, cn=Root DNs, cn=config");
      break;
    default: // SASL
      authType = AuthenticationType.SASL;
      bindDN =
          DN.decode("cn=Directory Manager, cn=Root DNs, cn=config");
      break;
    }

    AuthMethodConnectionCriteria criteria =
        new AuthMethodConnectionCriteria(allowedAuthMethods);
    Assert.assertEquals(criteria.willMatchAfterBind(client, bindDN,
        authType, false), expectedResult);
  }

}
