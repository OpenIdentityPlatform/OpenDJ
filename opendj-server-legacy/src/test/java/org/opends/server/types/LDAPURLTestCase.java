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
 * Portions Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC
 */
package org.opends.server.types;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.forgerock.opendj.ldap.SearchScope;
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
   *          Whether the URL should be fully decoded.
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



  /**
   * Test data for testTruncatedPercentEncoding.
   *
   * @return URLs with a percent sign followed by fewer than two hexadecimal digits.
   */
  @DataProvider
  public Object[][] truncatedPercentData()
  {
    return new Object[][] {
        { "ldap:///cn=name%B" },
        { "ldap:///cn=name%" },
    };
  }



  /**
   * A percent sign followed by fewer than two hexadecimal digits must be rejected with a
   * clean decode error instead of an ArrayIndexOutOfBoundsException - see issue #673.
   *
   * @param urlString
   *          The URL to decode.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "truncatedPercentData",
        expectedExceptions = DirectoryException.class)
  public void testTruncatedPercentEncoding(String urlString) throws Exception
  {
    LDAPURL.decode(urlString, true);
  }



  /**
   * Test data for testNonAsciiBaseDNIsPercentEncodedAsUTF8.
   *
   * @return DNs with non-ASCII characters and their percent-encoded UTF-8 form.
   */
  @DataProvider
  public Object[][] nonAsciiBaseDNData()
  {
    return new Object[][] {
        { "cn=J\u00f6rg \u0416,ou=Remote,dc=example,dc=com",
            "cn=J%C3%B6rg%20%D0%96,ou=Remote,dc=example,dc=com" },
        // A character outside the BMP is one code point made of two Java chars
        { "cn=\uD83D\uDE00,dc=x", "cn=%F0%9F%98%80,dc=x" },
    };
  }



  /**
   * A referral URL percent-encodes the UTF-8 octets of the DN (RFC 4516 section 2.1), so that
   * {@link LDAPURL#decode(String, boolean)} gives back the same DN - see issue #1153.
   *
   * @param dn
   *          The base DN.
   * @param encodedDN
   *          The expected percent-encoded base DN.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "nonAsciiBaseDNData")
  public void testNonAsciiBaseDNIsPercentEncodedAsUTF8(String dn, String encodedDN) throws Exception
  {
    LDAPURL url = new LDAPURL("ldap", "other.example.com", 389, dn, null, SearchScope.BASE_OBJECT, null, null);
    String urlString = url.toString();
    assertTrue(urlString.startsWith("ldap://other.example.com:389/" + encodedDN + "?"), urlString);
    assertEquals(LDAPURL.decode(urlString, true).getRawBaseDN(), dn);
  }

}
