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

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.NetworkGroupCfgDefn.AllowedAuthMethod;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.AddressMask;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Unit tests for IPConnectionCriteria.
 */
public class IPConnectionCriteriaTest extends DirectoryServerTestCase
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
    AddressMask matchAnything = AddressMask.decode("*.*.*.*");
    AddressMask matchNothing = AddressMask.decode("0.0.0.0");
    ClientConnection client =
        new MockClientConnection(12345, false, DN.nullDN(),
            AllowedAuthMethod.ANONYMOUS);

    Collection<AddressMask> emptyMasks = Collections.emptySet();

    return new Object[][] {
        { emptyMasks, emptyMasks, client, true },

        { Collections.singleton(matchAnything), emptyMasks, client,
            true },
        { emptyMasks, Collections.singleton(matchAnything), client,
            false },
        { Collections.singleton(matchAnything),
            Collections.singleton(matchAnything), client, false },

        { Collections.singleton(matchNothing), emptyMasks, client,
            false },
        { emptyMasks, Collections.singleton(matchNothing), client, true },
        { Collections.singleton(matchNothing),
            Collections.singleton(matchNothing), client, false }, };
  }



  /**
   * Tests the matches method.
   *
   * @param allowedClients
   *          The set of allowed client address masks.
   * @param deniedClients
   *          The set of denied client address masks.
   * @param client
   *          The test client.
   * @param expectedResult
   *          The expected result.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testData")
  public void testMatches(Collection<AddressMask> allowedClients,
      Collection<AddressMask> deniedClients, ClientConnection client,
      boolean expectedResult) throws Exception
  {
    IPConnectionCriteria criteria =
        new IPConnectionCriteria(allowedClients, deniedClients);
    Assert.assertEquals(criteria.matches(client), expectedResult);
  }



  /**
   * Tests the willMatchAfterBind method.
   *
   * @param allowedClients
   *          The set of allowed client address masks.
   * @param deniedClients
   *          The set of denied client address masks.
   * @param client
   *          The test client.
   * @param expectedResult
   *          The expected result.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testData")
  public void testWillMatchAfterBind(
      Collection<AddressMask> allowedClients,
      Collection<AddressMask> deniedClients, ClientConnection client,
      boolean expectedResult) throws Exception
  {
    IPConnectionCriteria criteria =
        new IPConnectionCriteria(allowedClients, deniedClients);
    Assert.assertEquals(criteria.willMatchAfterBind(client, DN.NULL_DN,
        AuthenticationType.SIMPLE, false), expectedResult);
  }

}
