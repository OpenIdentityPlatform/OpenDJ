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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sun.opends.sdk.util.Platform;



/**
 * This class defines a set of tests for the org.opends.sdk.DN class.
 */
public class DNTestCase extends SdkTestCase
{
  /**
   * child DN test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createChildDNTestData")
  public Object[][] createChildDNTestData()
  {
    return new Object[][] {
        { "", "", "" },
        { "", "dc=org", "dc=org" },
        { "", "dc=opends,dc=org", "dc=opends,dc=org" },
        { "dc=org", "", "dc=org" },
        { "dc=org", "dc=opends", "dc=opends,dc=org" },
        { "dc=org", "dc=foo,dc=opends", "dc=foo,dc=opends,dc=org" },
        { "dc=opends,dc=org", "", "dc=opends,dc=org" },
        { "dc=opends,dc=org", "dc=foo", "dc=foo,dc=opends,dc=org" },
        { "dc=opends,dc=org", "dc=bar,dc=foo", "dc=bar,dc=foo,dc=opends,dc=org" }, };
  }



  /**
   * Child RDN test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createChildRDNTestData")
  public Object[][] createChildRDNTestData()
  {
    return new Object[][] { { "", "dc=org", "dc=org" },
        { "dc=org", "dc=opends", "dc=opends,dc=org" },
        { "dc=opends,dc=org", "dc=foo", "dc=foo,dc=opends,dc=org" }, };
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "testDNs")
  public Object[][] createData()
  {
    return new Object[][] {
        { "", "", "" },
        { "   ", "", "" },
        { "cn=", "cn=", "cn=" },
        { "cn= ", "cn=", "cn=" },
        { "cn =", "cn=", "cn=" },
        { "cn = ", "cn=", "cn=" },
        { "dc=com", "dc=com", "dc=com" },
        { "dc=com+o=com", "dc=com+o=com", "dc=com+o=com" },
        { "DC=COM", "dc=com", "DC=COM" },
        { "dc = com", "dc=com", "dc=com" },
        { " dc = com ", "dc=com", "dc=com" },
        { "dc=example,dc=com", "dc=example,dc=com", "dc=example,dc=com" },
        { "dc=example, dc=com", "dc=example,dc=com", "dc=example,dc=com" },
        { "dc=example ,dc=com", "dc=example,dc=com", "dc=example,dc=com" },
        { "dc =example , dc  =   com", "dc=example,dc=com", "dc=example,dc=com" },
        { "givenName=John+cn=Doe,ou=People,dc=example,dc=com",
            "cn=doe+givenname=john,ou=people,dc=example,dc=com",
            "givenName=John+cn=Doe,ou=People,dc=example,dc=com" },
        { "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
            "givenname=john\\+cn\\=doe,ou=people,dc=example,dc=com",
            "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com" },
        { "cn=Doe\\, John,ou=People,dc=example,dc=com",
            "cn=doe\\, john,ou=people,dc=example,dc=com",
            "cn=Doe\\, John,ou=People,dc=example,dc=com" },
        { "UID=jsmith,DC=example,DC=net", "uid=jsmith,dc=example,dc=net",
            "UID=jsmith,DC=example,DC=net" },
        { "OU=Sales+CN=J. Smith,DC=example,DC=net",
            "cn=j. smith+ou=sales,dc=example,dc=net",
            "OU=Sales+CN=J. Smith,DC=example,DC=net" },
        { "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net",
            "cn=james \\\"jim\\\" smith\\, iii,dc=example,dc=net",
            "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net" },
        { "CN=John Smith\\2C III,DC=example,DC=net",
            "cn=john smith\\, iii,dc=example,dc=net",
            "CN=John Smith\\, III,DC=example,DC=net" },
        { "CN=\\23John Smith\\20,DC=example,DC=net",
            "cn=\\#john smith,dc=example,dc=net",
            "CN=\\#John Smith\\ ,DC=example,DC=net" },
        {
            "CN=Before\\0dAfter,DC=example,DC=net",
            // \0d is a hex representation of Carriage return. It is mapped
            // to a SPACE as defined in the MAP ( RFC 4518)
            "cn=before after,dc=example,dc=net",
            "CN=Before\\0dAfter,DC=example,DC=net" },
        { "2.5.4.3=#04024869",
        // Unicode codepoints from 0000-0008 are mapped to nothing.
            "cn=hi", "2.5.4.3=\\04\\02Hi" },
        { "1.1.1=", "1.1.1=", "1.1.1=" },
        { "CN=Lu\\C4\\8Di\\C4\\87", "cn=lu\u010di\u0107", "CN=Lu\u010di\u0107" },
        { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius",
            "ou=\u55b6\u696d\u90e8,o=airius", "ou=\u55b6\u696d\u90e8,o=Airius" },
        { "photo=\\ john \\ ,dc=com", "photo=\\ john \\ ,dc=com",
            "photo=\\ john \\ ,dc=com" },
        { "AB-global=", "ab-global=", "AB-global=" },
        { "OU= Sales + CN = J. Smith ,DC=example,DC=net",
            "cn=j. smith+ou=sales,dc=example,dc=net",
            "OU=Sales+CN=J. Smith,DC=example,DC=net" },
        { "cn=John+a=", "a=+cn=john", "cn=John+a=" },
        { "O=\"Sue, Grabbit and Runn\",C=US", "o=sue\\, grabbit and runn,c=us",
            "O=Sue\\, Grabbit and Runn,C=US" }, };
  }



  /**
   * DN comparison test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createDNComparisonData")
  public Object[][] createDNComparisonData()
  {
    return new Object[][] {
        { "cn=hello world,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=hello world,dc=com", "CN=hello world,dc=com", 0 },
        { "cn=hello   world,dc=com", "cn=hello world,dc=com", 0 },
        { "  cn =  hello world  ,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=hello world\\ ,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=HELLO WORLD,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=HELLO+sn=WORLD,dc=com", "sn=world+cn=hello,dc=com", 0 },
        /**
         * { "x-test-integer-type=10,dc=com", "x-test-integer-type=9,dc=com", 1
         * }, { "x-test-integer-type=999,dc=com",
         * "x-test-integer-type=1000,dc=com", -1 }, {
         * "x-test-integer-type=-1,dc=com", "x-test-integer-type=0,dc=com", -1
         * }, { "x-test-integer-type=0,dc=com", "x-test-integer-type=-1,dc=com",
         * 1 },
         **/
        { "cn=aaa,dc=com", "cn=aaaa,dc=com", -1 },
        { "cn=AAA,dc=com", "cn=aaaa,dc=com", -1 },
        { "cn=aaa,dc=com", "cn=AAAA,dc=com", -1 },
        { "cn=aaaa,dc=com", "cn=aaa,dc=com", 1 },
        { "cn=AAAA,dc=com", "cn=aaa,dc=com", 1 },
        { "cn=aaaa,dc=com", "cn=AAA,dc=com", 1 },
        { "cn=aaab,dc=com", "cn=aaaa,dc=com", 1 },
        { "cn=aaaa,dc=com", "cn=aaab,dc=com", -1 },
        { "dc=aaa,dc=aaa", "dc=bbb", -1 }, { "dc=bbb,dc=aaa", "dc=bbb", -1 },
        { "dc=ccc,dc=aaa", "dc=bbb", -1 }, { "dc=aaa,dc=bbb", "dc=bbb", 1 },
        { "dc=bbb,dc=bbb", "dc=bbb", 1 }, { "dc=ccc,dc=bbb", "dc=bbb", 1 },
        { "dc=aaa,dc=ccc", "dc=bbb", 1 }, { "dc=bbb,dc=ccc", "dc=bbb", 1 },
        { "dc=ccc,dc=ccc", "dc=bbb", 1 }, { "", "dc=bbb", -1 },
        { "dc=bbb", "", 1 } };
  }



  /**
   * DN equality test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createDNEqualityData")
  public Object[][] createDNEqualityData()
  {
    return new Object[][] {
        { "cn=hello world,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=hello world,dc=com", "CN=hello world,dc=com", 0 },
        { "cn=hello   world,dc=com", "cn=hello world,dc=com", 0 },
        { "  cn =  hello world  ,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=hello world\\ ,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=HELLO WORLD,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=HELLO+sn=WORLD,dc=com", "sn=world+cn=hello,dc=com", 0 },
        { "x-test-integer-type=10,dc=com", "x-test-integer-type=9,dc=com", 1 },
        { "x-test-integer-type=999,dc=com", "x-test-integer-type=1000,dc=com",
            -1 },
        { "x-test-integer-type=-1,dc=com", "x-test-integer-type=0,dc=com", -1 },
        { "x-test-integer-type=0,dc=com", "x-test-integer-type=-1,dc=com", 1 },
        { "cn=aaa,dc=com", "cn=aaaa,dc=com", -1 },
        { "cn=AAA,dc=com", "cn=aaaa,dc=com", -1 },
        { "cn=aaa,dc=com", "cn=AAAA,dc=com", -1 },
        { "cn=aaaa,dc=com", "cn=aaa,dc=com", 1 },
        { "cn=AAAA,dc=com", "cn=aaa,dc=com", 1 },
        { "cn=aaaa,dc=com", "cn=AAA,dc=com", 1 },
        { "cn=aaab,dc=com", "cn=aaaa,dc=com", 1 },
        { "cn=aaaa,dc=com", "cn=aaab,dc=com", -1 },
        { "dc=aaa,dc=aaa", "dc=bbb", -1 }, { "dc=bbb,dc=aaa", "dc=bbb", -1 },
        { "dc=ccc,dc=aaa", "dc=bbb", -1 }, { "dc=aaa,dc=bbb", "dc=bbb", 1 },
        { "dc=bbb,dc=bbb", "dc=bbb", 1 }, { "dc=ccc,dc=bbb", "dc=bbb", 1 },
        { "dc=aaa,dc=ccc", "dc=bbb", 1 }, { "dc=bbb,dc=ccc", "dc=bbb", 1 },
        { "dc=ccc,dc=ccc", "dc=bbb", 1 }, { "", "dc=bbb", -1 },
        { "dc=bbb", "", 1 } };
  }



  /**
   * Illegal DN test data provider.
   *
   * @return The array of illegal test DN strings.
   */
  @DataProvider(name = "illegalDNs")
  public Object[][] createIllegalData()
  {
    return new Object[][] { { "manager" }, { "manager " }, { "=Jim" },
        { " =Jim" }, { "= Jim" },
        { " = Jim" },
        { "cn+Jim" },
        { "cn + Jim" },
        { "cn=Jim+" },
        { "cn=Jim+manager" },
        { "cn=Jim+manager " },
        { "cn=Jim+manager," },// { "cn=Jim," }, { "cn=Jim,  " }, { "c[n]=Jim" },
        { "_cn=Jim" }, { "c_n=Jim" }, { "cn\"=Jim" }, { "c\"n=Jim" },
        { "1cn=Jim" }, { "cn+uid=Jim" }, { "-cn=Jim" }, { "/tmp=a" },
        { "\\tmp=a" }, { "cn;lang-en=Jim" }, { "@cn=Jim" },
        { "_name_=Jim" },
        { "\u03c0=pi" },
        { "v1.0=buggy" },// { "1.=buggy" }, { ".1=buggy" },
        { "oid.1." }, { "1.3.6.1.4.1.1466..0=#04024869" }, { "cn=#a" },
        { "cn=#ag" }, { "cn=#ga" }, { "cn=#abcdefgh" },
        { "cn=a\\b" }, // { "cn=a\\bg" }, { "cn=\"hello" },
        { "cn=+mail=,dc=example,dc=com" }, { "cn=xyz+sn=,dc=example,dc=com" },
        { "cn=,dc=example,dc=com" } };
  }



  /**
   * Is Child of test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createIsChildOfTestData")
  public Object[][] createIsChildOfTestData()
  {
    return new Object[][] { { "", "", false }, { "", "dc=org", false },
        { "", "dc=opends,dc=org", false },
        { "", "dc=foo,dc=opends,dc=org", false }, { "dc=org", "", true },
        { "dc=org", "dc=org", false }, { "dc=org", "dc=opends,dc=org", false },
        { "dc=org", "dc=foo,dc=opends,dc=org", false },
        { "dc=opends,dc=org", "", false },
        { "dc=opends,dc=org", "dc=org", true },
        { "dc=opends,dc=org", "dc=opends,dc=org", false },
        { "dc=opends,dc=org", "dc=foo,dc=opends,dc=org", false },
        { "dc=foo,dc=opends,dc=org", "", false },
        { "dc=foo,dc=opends,dc=org", "dc=org", false },
        { "dc=foo,dc=opends,dc=org", "dc=opends,dc=org", true },
        { "dc=foo,dc=opends,dc=org", "dc=foo,dc=opends,dc=org", false },
        { "dc=org", "dc=com", false },
        { "dc=opends,dc=org", "dc=foo,dc=org", false },
        { "dc=opends,dc=org", "dc=opends,dc=com", false }, };
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createNumComponentsTestData")
  public Object[][] createNumComponentsTestData()
  {
    return new Object[][] { { "", 0 }, { "dc=com", 1 },
        { "dc=opends,dc=com", 2 }, { "dc=world,dc=opends,dc=com", 3 },
        { "dc=hello,dc=world,dc=opends,dc=com", 4 }, };
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createParentAndRDNTestData")
  public Object[][] createParentAndRDNTestData()
  {
    return new Object[][] {
        { "", null, null },
        { "dc=com", "", "dc=com" },
        { "dc=opends,dc=com", "dc=com", "dc=opends" },
        { "dc=world,dc=opends,dc=com", "dc=opends,dc=com", "dc=world" },
        { "dc=hello,dc=world,dc=opends,dc=com", "dc=world,dc=opends,dc=com",
            "dc=hello" }, };
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createRDNTestData")
  public Object[][] createRDNTestData()
  {
    return new Object[][] { { "dc=com", 0, "dc=com" },
        { "dc=opends,dc=com", 0, "dc=opends" },
        { "dc=opends,dc=com", 1, "dc=com" },
        { "dc=hello,dc=world,dc=opends,dc=com", 0, "dc=hello" },
        { "dc=hello,dc=world,dc=opends,dc=com", 1, "dc=world" },
        { "dc=hello,dc=world,dc=opends,dc=com", 2, "dc=opends" },
        { "dc=hello,dc=world,dc=opends,dc=com", 3, "dc=com" }, };
  }



  /**
   * Subordinate test data provider.
   *
   * @return The array of subordinate and superior DN Strings.
   */
  @DataProvider(name = "createSubordinateTestData")
  public Object[][] createSubordinateTestData()
  {
    return new Object[][] { { "", "", true }, { "", "dc=org", false },
        { "", "dc=opends,dc=org", false },
        { "", "dc=foo,dc=opends,dc=org", false }, { "dc=org", "", true },
        { "dc=org", "dc=org", true }, { "dc=org", "dc=opends,dc=org", false },
        { "dc=org", "dc=foo,dc=opends,dc=org", false },
        { "dc=opends,dc=org", "", true },
        { "dc=opends,dc=org", "dc=org", true },
        { "dc=opends,dc=org", "dc=opends,dc=org", true },
        { "dc=opends,dc=org", "dc=foo,dc=opends,dc=org", false },
        { "dc=foo,dc=opends,dc=org", "", true },
        { "dc=foo,dc=opends,dc=org", "dc=org", true },
        { "dc=foo,dc=opends,dc=org", "dc=opends,dc=org", true },
        { "dc=foo,dc=opends,dc=org", "dc=foo,dc=opends,dc=org", true },
        { "dc=org", "dc=com", false },
        { "dc=opends,dc=org", "dc=foo,dc=org", false },
        { "dc=opends,dc=org", "dc=opends,dc=com", false }, };
  }



  /**
   * Superior test data provider.
   *
   * @return The array of superior and subordindate DN Strings.
   */
  @DataProvider(name = "createSuperiorTestData")
  public Object[][] createSuperiorTestData()
  {
    return new Object[][] { { "", "", true }, { "", "dc=org", true },
        { "", "dc=opends,dc=org", true },
        { "", "dc=foo,dc=opends,dc=org", true }, { "dc=org", "", false },
        { "dc=org", "dc=org", true }, { "dc=org", "dc=opends,dc=org", true },
        { "dc=org", "dc=foo,dc=opends,dc=org", true },
        { "dc=opends,dc=org", "", false },
        { "dc=opends,dc=org", "dc=org", false },
        { "dc=opends,dc=org", "dc=opends,dc=org", true },
        { "dc=opends,dc=org", "dc=foo,dc=opends,dc=org", true },
        { "dc=foo,dc=opends,dc=org", "", false },
        { "dc=foo,dc=opends,dc=org", "dc=org", false },
        { "dc=foo,dc=opends,dc=org", "dc=opends,dc=org", false },
        { "dc=foo,dc=opends,dc=org", "dc=foo,dc=opends,dc=org", true },
        { "dc=org", "dc=com", false },
        { "dc=opends,dc=org", "dc=foo,dc=org", false },
        { "dc=opends,dc=org", "dc=opends,dc=com", false }, };
  }



  @Test()
  public void testAdminData()
  {
    DN.valueOf("cn=cn\\=admin data");
    final DN theDN = DN.valueOf("cn=my dn");
    final RDN theRDN = new RDN("cn", "my rdn");
    final DN theChildDN = theDN.child(theRDN);
    DN.valueOf(theChildDN.toString());
  }



  /**
   * Test the child(DN) method.
   *
   * @param s
   *          The test DN string.
   * @param l
   *          The local name to be appended.
   * @param e
   *          The expected DN.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createChildDNTestData")
  public void testChildDN(final String s, final String l, final String e)
      throws Exception
  {
    final DN dn = DN.valueOf(s);
    final DN localName = DN.valueOf(l);
    final DN expected = DN.valueOf(e);

    assertEquals(dn.child(localName), expected);
  }



  /**
   * Test the child(DN) method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class, AssertionError.class })
  public void testChildDNException() throws Exception
  {
    final DN dn = DN.valueOf("dc=org");
    dn.child((DN) null);
  }



  /**
   * Test the child(DN) method's interaction with other methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testChildDNInteraction() throws Exception
  {
    final DN p = DN.valueOf("dc=opends,dc=org");
    final DN l = DN.valueOf("dc=foo");
    final DN e = DN.valueOf("dc=foo,dc=opends,dc=org");
    final DN c = p.child(l);

    assertEquals(c.size(), 3);

    assertEquals(c.compareTo(p), 1);
    assertEquals(p.compareTo(c), -1);

    assertTrue(p.isParentOf(c));
    assertFalse(c.isParentOf(p));

    assertTrue(c.isChildOf(p));
    assertFalse(p.isChildOf(c));

    assertEquals(c, e);
    assertEquals(c.hashCode(), e.hashCode());

    assertEquals(c.toNormalizedString(), e.toNormalizedString());
    assertEquals(c.toString(), e.toString());

    assertEquals(c.rdn(), RDN.valueOf("dc=foo"));

    assertEquals(c.parent(), DN.valueOf("dc=opends,dc=org"));
    assertEquals(c.parent(), e.parent());

    assertEquals(c.child(RDN.valueOf("dc=xxx")), DN
        .valueOf("dc=xxx,dc=foo,dc=opends,dc=org"));
    assertEquals(c.child(DN.valueOf("dc=xxx,dc=yyy")), DN
        .valueOf("dc=xxx,dc=yyy,dc=foo,dc=opends,dc=org"));
  }



  /**
   * Test the child(RDN...) method.
   *
   * @param s
   *          The test DN string.
   * @param r
   *          The RDN to be appended.
   * @param e
   *          The expected DN.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createChildRDNTestData")
  public void testChildSingleRDN(final String s, final String r, final String e)
      throws Exception
  {
    final DN dn = DN.valueOf(s);
    final RDN rdn = RDN.valueOf(r);
    final DN expected = DN.valueOf(e);

    assertEquals(dn.child(rdn), expected);
  }



  /**
   * Test DN compareTo
   *
   * @param first
   *          First DN to compare.
   * @param second
   *          Second DN to compare.
   * @param result
   *          Expected comparison result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createDNComparisonData")
  public void testCompareTo(final String first, final String second,
      final int result) throws Exception
  {
    final DN dn1 = DN.valueOf(first);
    final DN dn2 = DN.valueOf(second);

    int rc = dn1.compareTo(dn2);

    // Normalize the result.
    if (rc < 0)
    {
      rc = -1;
    }
    else if (rc > 0)
    {
      rc = 1;
    }

    assertEquals(rc, result, "Comparison for <" + first + "> and <" + second
        + ">.");
  }



  /**
   * Test DN equality
   *
   * @param first
   *          First DN to compare.
   * @param second
   *          Second DN to compare.
   * @param result
   *          Expected comparison result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createDNEqualityData")
  public void testEquality(final String first, final String second,
      final int result) throws Exception
  {
    final DN dn1 = DN.valueOf(first);
    final DN dn2 = DN.valueOf(second);

    if (result == 0)
    {
      assertTrue(dn1.equals(dn2), "DN equality for <" + first + "> and <"
          + second + ">");
    }
    else
    {
      assertFalse(dn1.equals(dn2), "DN equality for <" + first + "> and <"
          + second + ">");
    }
  }



  /**
   * Tests the equals method with a value that's not a DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
  public void testEqualsNonDN() throws Exception
  {
    final DN dn = DN.valueOf("dc=example,dc=com");

    assertFalse(dn.equals(DN.valueOf("not a DN")));
  }



  /**
   * Test DN hashCode
   *
   * @param first
   *          First DN to compare.
   * @param second
   *          Second DN to compare.
   * @param result
   *          Expected comparison result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createDNEqualityData")
  public void testHashCode(final String first, final String second,
      final int result) throws Exception
  {
    final DN dn1 = DN.valueOf(first);
    final DN dn2 = DN.valueOf(second);

    final int h1 = dn1.hashCode();
    final int h2 = dn2.hashCode();

    if (result == 0)
    {
      if (h1 != h2)
      {
        fail("Hash codes for <" + first + "> and <" + second
            + "> should be the same.");
      }
    }
    else
    {
      if (h1 == h2)
      {
        fail("Hash codes for <" + first + "> and <" + second
            + "> should be the same.");
      }
    }
  }



  /**
   * Test that decoding an illegal DN as a String throws an exception.
   *
   * @param dn
   *          The illegal DN to be tested.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalDNs", expectedExceptions = {
      StringIndexOutOfBoundsException.class,
      LocalizedIllegalArgumentException.class, NullPointerException.class })
  public void testIllegalStringDNs(final String dn) throws Exception
  {
    DN.valueOf(dn);
  }



  /**
   * Test the isChildOf method.
   *
   * @param s
   *          The test DN string.
   * @param d
   *          The dn parameter.
   * @param e
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createIsChildOfTestData")
  public void testIsChildOf(final String s, final String d, final boolean e)
      throws Exception
  {
    final DN dn = DN.valueOf(s);
    final DN other = DN.valueOf(d);

    assertEquals(dn.isChildOf(other), e, s + " isChildOf " + d);
  }



  /**
   * Test the isChildOf method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class, AssertionError.class })
  public void testIsChildOfException() throws Exception
  {
    final DN dn = DN.valueOf("dc=com");
    dn.isChildOf((String) null);
  }



  /**
   * Tests the parent method that require iteration.
   */
  @Test()
  public void testIterableParent()
  {
    final String str = "ou=people,dc=example,dc=com";
    final DN dn = DN.valueOf(str);
    // Parent at index 0 is self.
    assertEquals(dn, dn.parent(0));
    assertEquals(dn.parent(1), DN.valueOf("dc=example,dc=com"));
    assertEquals(dn.parent(2), DN.valueOf("dc=com"));
    assertEquals(dn.parent(3), DN.rootDN());
  }



  /**
   * Test the getNumComponents method.
   *
   * @param s
   *          The test DN string.
   * @param sz
   *          The expected number of RDNs.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createNumComponentsTestData")
  public void testNumComponents(final String s, final int sz) throws Exception
  {
    final DN dn = DN.valueOf(s);
    assertEquals(dn.size(), sz);
  }



  /**
   * Test the parent method.
   *
   * @param s
   *          The test DN string.
   * @param p
   *          The expected parent.
   * @param r
   *          The expected rdn.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createParentAndRDNTestData")
  public void testParent(final String s, final String p, final String r)
      throws Exception
  {
    final DN dn = DN.valueOf(s);
    final DN parent = (p != null ? DN.valueOf(p) : null);

    assertEquals(dn.parent(), parent, "For DN " + s);
  }



  /**
   * Test the parent method's interaction with other methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testParentInteraction() throws Exception
  {
    final DN c = DN.valueOf("dc=foo,dc=bar,dc=opends,dc=org");
    final DN e = DN.valueOf("dc=bar,dc=opends,dc=org");
    final DN p = c.parent();

    assertEquals(p.size(), 3);

    assertEquals(p.compareTo(c), -1);
    assertEquals(c.compareTo(p), 1);

    assertTrue(p.isParentOf(c));
    assertFalse(c.isParentOf(p));

    assertTrue(c.isChildOf(p));
    assertFalse(p.isChildOf(c));

    assertEquals(p, e);
    assertEquals(p.hashCode(), e.hashCode());

    assertEquals(p.toNormalizedString(), e.toNormalizedString());
    assertEquals(p.toString(), e.toString());

    assertEquals(p.rdn(), RDN.valueOf("dc=bar"));

    assertEquals(p.rdn(), RDN.valueOf("dc=bar"));

    assertEquals(p.parent(), DN.valueOf("dc=opends,dc=org"));
    assertEquals(p.parent(), e.parent());

    assertEquals(p.child(RDN.valueOf("dc=foo")), DN
        .valueOf("dc=foo,dc=bar,dc=opends,dc=org"));
    assertEquals(p.child(RDN.valueOf("dc=foo")), c);
    assertEquals(p.child(DN.valueOf("dc=xxx,dc=foo")), DN
        .valueOf("dc=xxx,dc=foo,dc=bar,dc=opends,dc=org"));
  }



  /**
   * Test the getRDN method.
   *
   * @param s
   *          The test DN string.
   * @param p
   *          The expected parent.
   * @param r
   *          The expected rdn.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createParentAndRDNTestData")
  public void testRDN(final String s, final String p, final String r)
      throws Exception
  {
    final DN dn = DN.valueOf(s);
    final RDN rdn = (r != null ? RDN.valueOf(r) : null);

    assertEquals(dn.rdn(), rdn, "For DN " + s);
  }



  /**
   * Tests the root DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testRootDN1() throws Exception
  {
    final DN dn = DN.valueOf("");
    assertTrue(dn.isRootDN());
    assertEquals(dn, DN.rootDN());
  }



  /**
   * Tests the root DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class, AssertionError.class })
  public void testRootDN2() throws Exception
  {
    final DN dn = DN.valueOf(null);
    assertEquals(dn, DN.rootDN());
  }



  /**
   * Test the root dn.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testRootDN3() throws Exception
  {
    final DN nullDN = DN.rootDN();
    assertTrue(nullDN.isRootDN());
    assertTrue(nullDN.size() == 0);
    assertEquals(nullDN.toNormalizedString(), "");
  }



  /**
   * Test the root dn.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testRootDN4() throws Exception
  {
    final DN dn = DN.valueOf("dc=com");
    assertFalse(dn.isRootDN());
  }



  /**
   * Tests the subordinate dns.
   */
  @Test(dataProvider = "createSubordinateTestData")
  public void testSubordinateDN(final String sub, final String base,
      final boolean e) throws Exception
  {
    final DN dn = DN.valueOf(sub);
    final DN other = DN.valueOf(base);
    assertEquals(dn.isSubordinateOrEqualTo(other), e, sub + " isSubordinateOf "
        + base);
  }



  /**
   * Tests the supeiror dns.
   */
  @Test(dataProvider = "createSuperiorTestData")
  public void testSuperiorDN(final String base, final String sub,
      final boolean e) throws Exception
  {
    final DN dn = DN.valueOf(base);
    final DN other = DN.valueOf(sub);
    assertEquals(dn.isSuperiorOrEqualTo(other), e, base + " isSuperiorOf "
        + sub);
  }



  /**
   * Tests the toNoramlizedString methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testToNormalizedString() throws Exception
  {
    final DN dn = DN.valueOf("dc=example,dc=com");
    assertEquals(dn.toNormalizedString(), "dc=example,dc=com");
  }



  /**
   * Test the RFC 4514 string representation of the DN.
   *
   * @param rawDN
   *          Raw DN string representation.
   * @param normDN
   *          Normalized DN string representation.
   * @param stringDN
   *          String representation.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testDNs")
  public void testToString(final String rawDN, final String normDN,
      final String stringDN) throws Exception
  {
    // DN dn = DN.valueOf(rawDN);
    // assertEquals(dn.toString(), stringDN);
  }



  /**
   * Tests the <CODE>valueOf</CODE> method which takes a String argument.
   *
   * @param rawDN
   *          Raw DN string representation.
   * @param normDN
   *          Normalized DN string representation.
   * @param stringDN
   *          String representation.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testDNs")
  public void testValueOfString(final String rawDN, final String normDN,
      final String stringDN) throws Exception
  {
    final DN dn = DN.valueOf(rawDN);
    final StringBuilder buffer = new StringBuilder();
    buffer.append(normDN);
    Platform.normalize(buffer);
    assertEquals(dn.toNormalizedString(), buffer.toString());
  }
}
