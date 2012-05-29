/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
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
 *      Copyright 2012 ForgeRock AS
 */
package org.opends.server.types;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.Platform;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

public class TestLDAPURL extends TypesTestCase {

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();

  }

  /**
   * Valid URLs test data provider.
   *
   * @return The array of valid test URL strings.
   */
  @DataProvider(name = "validURLs")
  public Object[][] createValidData() {
    return new Object[][] {
      { "ldap:///", "ldap:///", "ldap:///??base?(objectClass=*)" },
      { "http:///", "http:///", "http:///??base?(objectClass=*)" },
      { "ldap://host:389/", "ldap://host:389/",
        "ldap://host:389/??base?(objectClass=*)" },
      { "ldap://192.168.0.1/", "ldap://192.168.0.1:389/",
        "ldap://192.168.0.1:389/??base?(objectClass=*)" },
      { "ldap://192.168.0.2:1389/", "ldap://192.168.0.2:1389/",
        "ldap://192.168.0.2:1389/??base?(objectClass=*)" },
      // URLs with a baseDN
      { "ldap://host:389/cn=Foo,dc=example,dc=com",
        "ldap://host:389/cn=Foo,dc=example,dc=com",
        "ldap://host:389/cn=Foo,dc=example,dc=com??base?(objectClass=*)" },
      { "ldap:///cn=a,dc=example,dc=com", "ldap:///cn=a,dc=example,dc=com",
        "ldap:///cn=a,dc=example,dc=com??base?(objectClass=*)" },
      { "ldap:///cn=a\"a,dc=example,dc=com", "ldap:///cn=a%5C%22a,dc=example,dc=com",
        "ldap:///cn=a%5C%22a,dc=example,dc=com??base?(objectClass=*)" },
      { "ldap://localhost/cn=Foo,dc=a.com", "ldap://localhost:389/cn=Foo,dc=a.com",
        "ldap://localhost:389/cn=Foo,dc=a.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com?", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      // URLs with some attribute lists
      { "ldap://host:1389/cn=Foo,dc=b.com?,", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com?,,,?", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com?cn,sn", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com?cn,sn?base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com?sn,cn", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com?sn,cn?base?(objectClass=*)" },
      { "ldap://host:1389/?cn", "ldap://host:1389/",
        "ldap://host:1389/?cn?base?(objectClass=*)" },
      // URLs with scope
      { "ldap://host:1389/cn=Foo,dc=b.com??", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com???", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com??base", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com??base?", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com??one", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??one?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com??sub", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??sub?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com??subord", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??subordinate?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com??subordinate", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??subordinate?(objectClass=*)" },
      // URLs with filters
      { "ldap://host:1389/cn=Foo,dc=b.com????", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      { "ldap://host:1389/cn=Foo,dc=b.com???(cn=Foo)?", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(cn=Foo)" },
      { "ldap://host:1389/cn=Foo,dc=b.com???(cn=foo)", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(cn=foo)" },
      { "ldap://host:1389/cn=Foo,dc=b.com???uid=user.0?", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(uid=user.0)" },
      // Extensions are always returned, baseOnly or not.
      { "ldap://host:1389/cn=Foo,dc=b.com????x-password", "ldap://host:1389/cn=Foo,dc=b.com????x-password",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)?x-password" },
      { "ldap://host:1389/cn=Foo,dc=b.com????a,b,c", "ldap://host:1389/cn=Foo,dc=b.com????a,b,c",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)?a,b,c" },
      { "ldap://host:1389/cn=Foo,dc=b.com????,,,", "ldap://host:1389/cn=Foo,dc=b.com",
        "ldap://host:1389/cn=Foo,dc=b.com??base?(objectClass=*)" },
      // URLs with everything
      { "ldap://myhost.full.com:2389/cn=F oo,dc=full.com?cn,sn,userpassword?one?(cn=foo)?x-nothing",
        "ldap://myhost.full.com:2389/cn=F%20oo,dc=full.com????x-nothing",
        "ldap://myhost.full.com:2389/cn=F%20oo,dc=full.com?cn,sn,userpassword?one?(cn=foo)?x-nothing" },
    };
  }

  @Test(dataProvider = "validURLs")
  public void testDecodeString(String rawURL, String stringURL,
    String fullURL)
      throws Exception
  {
    LDAPURL url = LDAPURL.decode(rawURL, true);
    StringBuilder buffer = new StringBuilder();
    url.toString(buffer, true);
    assertEquals(stringURL, buffer.toString());
  }


  @Test(dataProvider = "validURLs")
  public void testDecodeStringFull(String rawURL, String stringURL,
    String fullURL) throws Exception
  {
    LDAPURL url = LDAPURL.decode(rawURL, true);
    StringBuilder buffer = new StringBuilder();
    url.toString(buffer, false);
    assertEquals(fullURL, buffer.toString());
  }
    /**
   * Illegal URLs test data provider.
   *
   * @return The array of illegal test URk strings.
   */
  @DataProvider(name = "illegalURLs")
  public Object[][] createIllegalData() {
    return new Object[][] { { "http:" }, { "://" }, { "ldap://:389" },
      { "ldap://localhost:"}, { "ldap://1.2.3.4:" }, { "ldap://host:-1" },
      { "ldap://host:65536" }, { "ldap://host:ldap" }, { "ldap://host:389:/" },
     // { "ldap://host:389/c=a\"a" },
      { "ldap://host:389/cn=a,a" },
      { "ldap://host:1389/cn=Foo,dc=b.com??suberror" },
      { "ldap://host:1389/cn=Foo,dc=b.com???(invalidFilter)" },
      { "ldap://host:1389/cn=Foo,dc=b.com???(&(cn=Foo)(cn=Bar" },
    };
  }

  /**
   * Test LDAPUrl string decoder against illegal strings.
   *
   * @param rawURL
   *          Illegal URL string representation.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalURLs", expectedExceptions = DirectoryException.class)
  public void testDecodeString(String rawURL) throws Exception
  {
    LDAPURL.decode(rawURL, true);

    fail("Expected exception for value \"" + rawURL + "\"");
  }

}