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
 *      Copyright 2012 ForgeRock AS.
 */
package org.opends.server.types;



import static org.testng.Assert.assertEquals;

import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * This class defines a set of tests for the org.opends.server.core.LDAPURL
 * class.
 */
public class LDAPURLTestCase extends TypesTestCase
{

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();
  }



  /**
   * Test data for testURLEncoding.
   *
   * @return The test data for testURLEncoding.
   */
  @DataProvider
  public Object[][] urlEncodingData()
  {
    return new Object[][] {
        /* Sanity check */
        { "ldap:///dc=example,dc=com???(cn=test)", "dc=example,dc=com",
            "(cn=test)", false },
        { "ldap:///dc=example,dc=com???(cn=test)", "dc=example,dc=com",
            "(cn=test)", true },
        /* DN encoding: triple back-slash required for Java and DN escaping */
        { "ldap:///dc=%5c%22example%5c%22,dc=com???(cn=test)",
            "dc=\\\"example\\\",dc=com", "(cn=test)", false },
        { "ldap:///dc=%5c%22example%5c%22,dc=com???(cn=test)",
            "dc=\\\"example\\\",dc=com", "(cn=test)", true },
        /* Filter encoding */
        { "ldap:///dc=example,dc=com???(cn=%22test%22)", "dc=example,dc=com",
            "(cn=\"test\")", false },
        { "ldap:///dc=example,dc=com???(cn=%22test%22)", "dc=example,dc=com",
            "(cn=\"test\")", true }, };
  }



  /**
   * Tests URL decoding of the base DN - see issue OPENDJ-432.
   *
   * @param urlString
   *          The URL to decode.
   * @param dnString
   *          The base DN.
   * @param filterString
   *          The filter string.
   * @param fullyDecode
   *          Whether or not the URL should be fully decoded.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "urlEncodingData")
  public void testURLEncoding(String urlString, String dnString,
      String filterString, boolean fullyDecode) throws Exception
  {
    LDAPURL url = LDAPURL.decode(urlString, fullyDecode);
    assertEquals(url.getRawBaseDN(), dnString);
    assertEquals(url.getRawFilter(), filterString);
  }

}
