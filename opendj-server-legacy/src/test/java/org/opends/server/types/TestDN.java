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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.testng.Assert.*;

import java.util.ArrayList;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the org.opends.server.core.DN
 * class.
 */
public class TestDN extends TypesTestCase {
  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider
  public Object[][] testDNs() {
    return new Object[][] {
         // raw dn, irreversible normalized string representation, toString representation
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
        { "dc=example,dc=com", "dc=com,dc=example", "dc=example,dc=com" },
        { "dc=example, dc=com", "dc=com,dc=example", "dc=example,dc=com" },
        { "dc=example ,dc=com", "dc=com,dc=example", "dc=example,dc=com" },
        { "dc =example , dc  =   com", "dc=com,dc=example", "dc=example,dc=com" },
        { "givenName=John+cn=Doe,ou=People,dc=example,dc=com",
            "dc=com,dc=example,ou=people,cn=doe+givenname=john",
            "givenName=John+cn=Doe,ou=People,dc=example,dc=com" },
        // FIXME parsing next RDN should fail in the first place
        { "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
            "dc=com,dc=example,ou=people,givenname=john%2Bcn%3Ddoe",
            "givenName=John\\+cn\\=Doe,ou=People,dc=example,dc=com" },
        { "cn=Doe\\, John,ou=People,dc=example,dc=com",
            "dc=com,dc=example,ou=people,cn=doe%2C%20john",
            "cn=Doe\\, John,ou=People,dc=example,dc=com" },
        { "UID=jsmith,DC=example,DC=net",
            "dc=net,dc=example,uid=jsmith",
            "UID=jsmith,DC=example,DC=net" },
        { "OU=Sales+CN=J. Smith,DC=example,DC=net",
            "dc=net,dc=example,cn=j.%20smith+ou=sales",
            "OU=Sales+CN=J. Smith,DC=example,DC=net" },
        { "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net",
            "dc=net,dc=example,cn=james%20%22jim%22%20smith%2C%20iii",
            "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net" },
        { "CN=John Smith\\2C III,DC=example,DC=net",
            "dc=net,dc=example,cn=john%20smith%2C%20iii",
            "CN=John Smith\\, III,DC=example,DC=net" },
        { "CN=\\23John Smith\\20,DC=example,DC=net",
            "dc=net,dc=example,cn=%23john%20smith",
            "CN=\\#John Smith\\ ,DC=example,DC=net" },
        { "CN=Before\\0dAfter,DC=example,DC=net",
             //\0d is a hex representation of Carriage return. It is mapped
             //to a SPACE as defined in the MAP ( RFC 4518)
            "dc=net,dc=example,cn=before%20after",
            "CN=Before\\0dAfter,DC=example,DC=net" },
        { "1.3.6.1.4.1.1466.0=#04024869",
             //Unicode codepoints from 0000-0008 are mapped to nothing.
            "1.3.6.1.4.1.1466.0=hi",
            "1.3.6.1.4.1.1466.0=\\04\\02Hi" },
        { "1.1.1=", "1.1.1=", "1.1.1=" },
        { "CN=Lu\\C4\\8Di\\C4\\87", "cn=luc%CC%8Cic%CC%81",
            "CN=Lu\u010di\u0107" },
        { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius",
            "o=airius,ou=%E5%96%B6%E6%A5%AD%E9%83%A8",
            "ou=\u55b6\u696d\u90e8,o=Airius" },
        { "photo=\\ john \\ ,dc=com", "dc=com,photo=%20john%20%20",
            "photo=\\ john \\ ,dc=com" },
        { "AB-global=", "ab-global=", "AB-global=" },
        { "OU= Sales + CN = J. Smith ,DC=example,DC=net",
            "dc=net,dc=example,cn=j.%20smith+ou=sales",
            "OU=Sales+CN=J. Smith,DC=example,DC=net" },
        { "cn=John+a=", "a=+cn=john", "cn=John+a=" },
        { "OID.1.3.6.1.4.1.1466.0=#04024869",
             //Unicode codepoints from 0000-0008 are mapped to nothing.
            "1.3.6.1.4.1.1466.0=hi",
            "1.3.6.1.4.1.1466.0=\\04\\02Hi" },
        { "O=\"Sue, Grabbit and Runn\",C=US",
            "c=us,o=sue%2C%20grabbit%20and%20runn",
            "O=Sue\\, Grabbit and Runn,C=US" }, };
  }



  /**
   * Illegal DN test data provider.
   *
   * @return The array of illegal test DN strings.
   */
  @DataProvider
  public Object[][] illegalDNs() {
    return new Object[][] { { "manager" }, { "manager " }, { "=Jim" },
        { " =Jim" }, { "= Jim" }, { " = Jim" }, { "cn+Jim" }, { "cn + Jim" },
        { "cn=Jim+" }, { "cn=Jim+manager" }, { "cn=Jim+manager " },
        { "cn=Jim+manager," }, { "cn=Jim," }, { "cn=Jim,  " }, { "c[n]=Jim" },
        { "_cn=Jim" }, { "c_n=Jim" }, { "cn\"=Jim" },  { "c\"n=Jim" },
        { "1cn=Jim" }, { "cn+uid=Jim" }, { "-cn=Jim" }, { "/tmp=a" },
        { "\\tmp=a" },  { "cn;lang-en=Jim" }, { "@cn=Jim" }, { "_name_=Jim" },
        { "\u03c0=pi" },  { "v1.0=buggy" }, { "1.=buggy" }, { ".1=buggy" },
        { "oid.1." }, { "1.3.6.1.4.1.1466..0=#04024869" },
        { "cn=#a" }, { "cn=#ag" }, { "cn=#ga" }, { "cn=#abcdefgh" },
        { "cn=a\\b" }, { "cn=a\\bg" }, { "cn=\"hello" },
        {"cn=+mail=,dc=example,dc=com"},{"cn=xyz+sn=,dc=example,dc=com"},
        {"cn=,dc=example,dc=com"},
//        {"cn=a+cn=b,dc=example,dc=com"}
    };
  }



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

    String attrName = "x-test-integer-type";
    AttributeType dummy = getAttributeType(attrName, getDefaultIntegerSyntax());
    DirectoryServer.getSchema().registerAttributeType(dummy, true);
  }

  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateWithSingleRDN1() throws Exception {
    DN dn = DN.rootDN().child(RDN.valueOf("dc=com"));
    assertEquals(dn, DN.valueOf("dc=com"));
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateWithMultipleRDNs() throws Exception {
    DN dn = DN.rootDN().child(RDN.valueOf("dc=org")).child(RDN.valueOf("dc=opends")).child(RDN.valueOf("dc=foo"));
    assertEquals(dn, DN.valueOf("dc=foo,dc=opends,dc=org"));
  }

  /**
   * Tests the <CODE>valueOf</CODE> method which takes a String
   * argument.
   *
   * @param rawDN
   *          Raw DN string representation.
   * @param normDN
   *          Normalized DN string representation.
   * @param unused
   *          Unused argument.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testDNs")
  public void testValueOf(String rawDN, String normDN, String unused) throws Exception {
    DN dn = DN.valueOf(rawDN);
    assertEquals(dn.toNormalizedUrlSafeString(), normDN);
  }



  /**
   * Tests the <CODE>valueOf</CODE> method which takes a ByteString
   * argument.
   *
   * @param rawDN
   *          Raw DN string representation.
   * @param normDN
   *          Normalized DN string representation.
   * @param unused
   *          Unused argument.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testDNs")
  public void testValueOfByteString(String rawDN, String normDN, String unused) throws Exception {
    DN dn = DN.valueOf(ByteString.valueOfUtf8(rawDN));
    assertEquals(dn.toNormalizedUrlSafeString(), normDN);
  }



  /**
   * Test DN string decoder.
   *
   * @param rawDN
   *          Raw DN string representation.
   * @param unused
   *          Unused argument.
   * @param stringDN
   *          String representation.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testDNs")
  public void testToString(String rawDN, String unused, String stringDN) throws Exception {
    DN dn = DN.valueOf(rawDN);
    assertEquals(dn.toString(), rawDN);
  }



  /**
   * Tests the toNoramlizedString methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testToNormalizedUrlSafeString() throws Exception {
    DN dn = DN.valueOf("dc=example,dc=com");
    assertEquals(dn.toNormalizedUrlSafeString(), "dc=com,dc=example");
  }



  /**
   * Tests both variants of the {@code valueOf} method with null arguments.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOfNull() throws Exception {
    assertEquals(DN.valueOf((ByteString) null), DN.rootDN());
    assertEquals(DN.valueOf((String) null), DN.rootDN());
  }



  /**
   * Test that decoding an illegal DN as a String throws an exception.
   *
   * @param dn
   *          The illegal DN to be tested.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalDNs", expectedExceptions = LocalizedIllegalArgumentException.class)
  public void testIllegalStringDNs(String dn) throws Exception {
    DN.valueOf(dn);
  }



  /**
   * Test that decoding an illegal DN as an octet string throws an
   * exception.
   *
   * @param dn
   *          The illegal DN to be tested.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalDNs", expectedExceptions = LocalizedIllegalArgumentException.class)
  public void testIllegalOctetStringDNs(String dn) throws Exception {
    ByteString octetString = ByteString.valueOfUtf8(dn);
    DN.valueOf(octetString);
  }



  /**
   * Test the nullDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testNullDN() throws Exception {
    DN nullDN = DN.rootDN();

    assertEquals(nullDN.size(), 0);
    assertEquals(nullDN.toString(), "");
  }



  /**
   * Test the isNullDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsNullDNWithNullDN() throws Exception {
    DN nullDN = DN.rootDN();
    assertTrue(nullDN.isRootDN());
  }



  /**
   * Test the isNullDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsNullDNWithNonNullDN() throws Exception {
    DN dn = DN.valueOf("dc=com");
    assertFalse(dn.isRootDN());
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createNumComponentsTestData")
  public Object[][] createNumComponentsTestData() {
    return new Object[][] { { "", 0 }, { "dc=com", 1 },
        { "dc=opends,dc=com", 2 },
        { "dc=world,dc=opends,dc=com", 3 },
        { "dc=hello,dc=world,dc=opends,dc=com", 4 }, };
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
  public void testNumComponents(String s, int sz) throws Exception {
    DN dn = DN.valueOf(s);
    assertEquals(dn.size(), sz);
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider
  public Object[][] createParentAndRDNTestData() {
    return new Object[][] {
        { "", null, null },
        { "dc=com", "", "dc=com" },
        { "dc=opends,dc=com", "dc=com", "dc=opends" },
        { "dc=world,dc=opends,dc=com", "dc=opends,dc=com", "dc=world" },
        { "dc=hello,dc=world,dc=opends,dc=com",
            "dc=world,dc=opends,dc=com", "dc=hello" }, };
  }



  /**
   * Test the getParent method.
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
  public void testGetParent(String s, String p, String r)
      throws Exception {
    DN dn = DN.valueOf(s);
    DN parent = p != null ? DN.valueOf(p) : null;

    assertEquals(dn.parent(), parent, "For DN " + s);
  }



  /**
   * Retrieves the naming contexts defined in the server.
   */
  @DataProvider(name = "namingContexts")
  public Object[][] getNamingContexts() {
    ArrayList<DN> contextList = new ArrayList<>();
    contextList.addAll(DirectoryServer.getPublicNamingContexts().keySet());
    contextList.addAll(DirectoryServer.getPrivateNamingContexts().keySet());

    Object[][] contextArray = new Object[contextList.size()][1];
    for (int i=0; i < contextArray.length; i++)
    {
      contextArray[i][0] = contextList.get(i);
    }

    return contextArray;
  }



  /**
   * Tests the getParentDNInSuffix method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "namingContexts")
  public void testGetParentDNInSuffix(DN namingContext) throws Exception {
    assertNull(DirectoryServer.getParentDNInSuffix(namingContext));

    DN childDN = namingContext.child(RDN.valueOf("ou=People"));
    assertNotNull(DirectoryServer.getParentDNInSuffix(childDN));
    assertEquals(DirectoryServer.getParentDNInSuffix(childDN), namingContext);
  }



  /**
   * Test the getParent method's interaction with other methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetParentInteraction() throws Exception {
    DN c = DN.valueOf("dc=foo,dc=bar,dc=opends,dc=org");
    DN e = DN.valueOf("dc=bar,dc=opends,dc=org");
    DN p = c.parent();

    assertFalse(p.isRootDN());

    assertEquals(p.size(), 3);

    assertTrue(p.compareTo(c) < 0);
    assertTrue(c.compareTo(p) > 0);

    assertTrue(p.isSuperiorOrEqualTo(c));
    assertFalse(c.isSuperiorOrEqualTo(p));

    assertTrue(c.isSubordinateOrEqualTo(p));
    assertFalse(p.isSubordinateOrEqualTo(c));

    assertEquals(p, e);
    assertEquals(p.hashCode(), e.hashCode());

    assertEquals(p.toNormalizedUrlSafeString(), e.toNormalizedUrlSafeString());
    assertEquals(p.toString(), e.toString());

    assertEquals(p.rdn(), RDN.valueOf("dc=bar"));

    assertEquals(p.rdn(0), RDN.valueOf("dc=bar"));
    assertEquals(p.rdn(1), RDN.valueOf("dc=opends"));
    assertEquals(p.rdn(2), RDN.valueOf("dc=org"));

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
  public void testGetRDN(String s, String p, String r)
      throws Exception {
    DN dn = DN.valueOf(s);
    RDN rdn = r != null ? RDN.valueOf(r) : null;

    assertEquals(dn.rdn(), rdn, "For DN " + s);
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createRDNTestData")
  public Object[][] createRDNTestData() {
    return new Object[][] { { "dc=com", 0, "dc=com" },
        { "dc=opends,dc=com", 0, "dc=opends" },
        { "dc=opends,dc=com", 1, "dc=com" },
        { "dc=hello,dc=world,dc=opends,dc=com", 0, "dc=hello" },
        { "dc=hello,dc=world,dc=opends,dc=com", 1, "dc=world" },
        { "dc=hello,dc=world,dc=opends,dc=com", 2, "dc=opends" },
        { "dc=hello,dc=world,dc=opends,dc=com", 3, "dc=com" }, };
  }



  /**
   * Test the getRDN indexed method.
   *
   * @param s
   *          The test DN string.
   * @param i
   *          The RDN index.
   * @param r
   *          The expected rdn.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createRDNTestData")
  public void testGetRDNIndexed(String s, int i, String r)
      throws Exception {
    DN dn = DN.valueOf(s);
    RDN rdn = RDN.valueOf(r);

    assertEquals(dn.rdn(i), rdn, "For DN " + s);
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createRDNIllegalTestData")
  public Object[][] createRDNIllegalTestData() {
    return new Object[][] { { "", 0 }, { "", -1 }, { "", 1 },
        { "dc=com", -1 }, { "dc=com", 1 },
        { "dc=opends,dc=com", -1 }, { "dc=opends,dc=com", 2 },
        { "dc=hello,dc=world,dc=opends,dc=com", -1 },
        { "dc=hello,dc=world,dc=opends,dc=com", 4 }, };
  }



  /**
   * Test the getRDN indexed method with illegal indexes.
   *
   * @param s
   *          The test DN string.
   * @param i
   *          The illegal RDN index.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createRDNIllegalTestData", expectedExceptions = IndexOutOfBoundsException.class)
  public void testGetRDNIndexedException(String s, int i)
      throws Exception {
    DN dn = DN.valueOf(s);

    // Shoudld throw.
    dn.rdn(i);

    Assert.fail("Excepted exception for RDN index " + i + " in DN " + s);
  }



  /**
   * Concat DN test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createConcatDNTestData")
  public Object[][] createConcatDNTestData() {
    return new Object[][] {
        { "", "", "" },
        { "", "dc=org", "dc=org" },
        { "", "dc=opends,dc=org", "dc=opends,dc=org" },
        { "dc=org", "", "dc=org" },
        { "dc=org", "dc=opends", "dc=opends,dc=org" },
        { "dc=org", "dc=foo,dc=opends", "dc=foo,dc=opends,dc=org" },
        { "dc=opends,dc=org", "", "dc=opends,dc=org" },
        { "dc=opends,dc=org", "dc=foo", "dc=foo,dc=opends,dc=org" },
        { "dc=opends,dc=org", "dc=bar,dc=foo",
            "dc=bar,dc=foo,dc=opends,dc=org" }, };
  }



  /**
   * Test the concat(DN) method.
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
  @Test(dataProvider = "createConcatDNTestData")
  public void testConcatDN(String s, String l, String e)
      throws Exception {
    DN dn = DN.valueOf(s);
    DN localName = DN.valueOf(l);
    DN expected = DN.valueOf(e);

    assertEquals(dn.child(localName), expected);
  }



  /**
   * Test the concat(DN) method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class,
      AssertionError.class })
  public void testConcatDNException() throws Exception {
    DN dn = DN.valueOf("dc=org");
    dn.child((DN) null);
  }



  /**
   * Test the concat(DN) method's interaction with other methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConcatDNInteraction() throws Exception {
    DN p = DN.valueOf("dc=opends,dc=org");
    DN l = DN.valueOf("dc=foo,dc=bar");
    DN e = DN.valueOf("dc=foo,dc=bar,dc=opends,dc=org");
    DN c = p.child(l);

    assertFalse(c.isRootDN());

    assertEquals(c.size(), 4);

    assertTrue(c.compareTo(p) > 0);
    assertTrue(p.compareTo(c) < 0);

    assertTrue(p.isSuperiorOrEqualTo(c));
    assertFalse(c.isSuperiorOrEqualTo(p));

    assertTrue(c.isSubordinateOrEqualTo(p));
    assertFalse(p.isSubordinateOrEqualTo(c));

    assertEquals(c, e);
    assertEquals(c.hashCode(), e.hashCode());

    assertEquals(c.toNormalizedUrlSafeString(), e.toNormalizedUrlSafeString());
    assertEquals(c.toString(), e.toString());

    assertEquals(c.rdn(), RDN.valueOf("dc=foo"));

    assertEquals(c.rdn(0), RDN.valueOf("dc=foo"));
    assertEquals(c.rdn(1), RDN.valueOf("dc=bar"));
    assertEquals(c.rdn(2), RDN.valueOf("dc=opends"));
    assertEquals(c.rdn(3), RDN.valueOf("dc=org"));

    assertEquals(c.parent(), DN.valueOf("dc=bar,dc=opends,dc=org"));
    assertEquals(c.parent(), e.parent());

    assertEquals(c.child(RDN.valueOf("dc=xxx")), DN
        .valueOf("dc=xxx,dc=foo,dc=bar,dc=opends,dc=org"));
    assertEquals(c.child(DN.valueOf("dc=xxx,dc=yyy")), DN
        .valueOf("dc=xxx,dc=yyy,dc=foo,dc=bar,dc=opends,dc=org"));
  }



  /**
   * Concat RDN test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createConcatRDNTestData")
  public Object[][] createConcatRDNTestData() {
    return new Object[][] { { "", "dc=org", "dc=org" },
        { "dc=org", "dc=opends", "dc=opends,dc=org" },
        { "dc=opends,dc=org", "dc=foo", "dc=foo,dc=opends,dc=org" }, };
  }



  /**
   * Test the concat(RDN...) method.
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
  @Test(dataProvider = "createConcatRDNTestData")
  public void testConcatSingleRDN(String s, String r, String e)
      throws Exception {
    DN dn = DN.valueOf(s);
    RDN rdn = RDN.valueOf(r);
    DN expected = DN.valueOf(e);

    assertEquals(dn.child(rdn), expected);
  }

  /**
   * Get local name test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createGetLocalNameTestData")
  public Object[][] createGetLocalNameTestData() {
    return new Object[][] {
        { "", 0, -1, "" },
        { "", 0, 0, "" },
        { "dc=org", 0, -1, "dc=org" },
        { "dc=org", 1, -1, "" },
        { "dc=org", 0, 0, "" },
        { "dc=org", 0, 1, "dc=org" },
        { "dc=org", 1, 1, "" },
        { "dc=opends,dc=org", 0, -1, "dc=opends,dc=org" },
        { "dc=opends,dc=org", 1, -1, "dc=opends" },
        { "dc=opends,dc=org", 2, -1, "" },
        { "dc=opends,dc=org", 0, 0, "" },
        { "dc=opends,dc=org", 0, 1, "dc=org" },
        { "dc=opends,dc=org", 0, 2, "dc=opends,dc=org" },
        { "dc=opends,dc=org", 1, 1, "" },
        { "dc=opends,dc=org", 1, 2, "dc=opends" },
        { "dc=opends,dc=org", 2, 2, "" },
        { "dc=foo,dc=opends,dc=org", 0, -1, "dc=foo,dc=opends,dc=org" },
        { "dc=foo,dc=opends,dc=org", 1, -1, "dc=foo,dc=opends" },
        { "dc=foo,dc=opends,dc=org", 2, -1, "dc=foo" },
        { "dc=foo,dc=opends,dc=org", 3, -1, "" },
        { "dc=foo,dc=opends,dc=org", 0, 0, "" },
        { "dc=foo,dc=opends,dc=org", 0, 1, "dc=org" },
        { "dc=foo,dc=opends,dc=org", 0, 2, "dc=opends,dc=org" },
        { "dc=foo,dc=opends,dc=org", 0, 3, "dc=foo,dc=opends,dc=org" },
        { "dc=foo,dc=opends,dc=org", 1, 1, "" },
        { "dc=foo,dc=opends,dc=org", 1, 2, "dc=opends" },
        { "dc=foo,dc=opends,dc=org", 1, 3, "dc=foo,dc=opends" },
        { "dc=foo,dc=opends,dc=org", 2, 2, "" },
        { "dc=foo,dc=opends,dc=org", 2, 3, "dc=foo" },
        { "dc=foo,dc=opends,dc=org", 3, 3, "" }, };
  }



  /**
   * Is ancestor of test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createIsAncestorOfTestData")
  public Object[][] createIsAncestorOfTestData() {
    return new Object[][] {
        { "", "", true },
        { "", "dc=org", true },
        { "", "dc=opends,dc=org", true },
        { "", "dc=foo,dc=opends,dc=org", true },
        { "dc=org", "", false },
        { "dc=org", "dc=org", true },
        { "dc=org", "dc=opends,dc=org", true },
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



  /**
   * Test the isAncestoryOf method.
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
  @Test(dataProvider = "createIsAncestorOfTestData")
  public void testIsAncestorOf(String s, String d, boolean e)
      throws Exception {
    DN dn = DN.valueOf(s);
    DN other = DN.valueOf(d);

    assertEquals(dn.isSuperiorOrEqualTo(other), e, s + " isAncestoryOf " + d);
  }



  /**
   * Test the isAncestorOf method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class,
      AssertionError.class })
  public void testIsAncestorOfException() throws Exception {
    DN dn = DN.valueOf("dc=com");
    dn.isSuperiorOrEqualTo((DN) null);
  }



  /**
   * Is descendant of test data provider.
   *
   * @return The array of test data.
   */
  @DataProvider(name = "createIsDescendantOfTestData")
  public Object[][] createIsDescendantOfTestData() {
    return new Object[][] {
        { "", "", true },
        { "", "dc=org", false },
        { "", "dc=opends,dc=org", false },
        { "", "dc=foo,dc=opends,dc=org", false },
        { "dc=org", "", true },
        { "dc=org", "dc=org", true },
        { "dc=org", "dc=opends,dc=org", false },
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
   * Test the isDescendantOf method.
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
  @Test(dataProvider = "createIsDescendantOfTestData")
  public void testIsDescendantOf(String s, String d, boolean e)
      throws Exception {
    DN dn = DN.valueOf(s);
    DN other = DN.valueOf(d);

    assertEquals(dn.isSubordinateOrEqualTo(other), e, s + " isDescendantOf "
        + d);
  }



  /**
   * Test the isDescendantOf method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class,
      AssertionError.class })
  public void testIsDescendantOfException() throws Exception {
    DN dn = DN.valueOf("dc=com");
    dn.isSubordinateOrEqualTo((DN) null);
  }



  /**
   * DN equality test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createDNEqualityData")
  public Object[][] createDNEqualityData() {
    return new Object[][] {
//        { "cn=hello world,dc=com", "cn=hello world,dc=com", 0 },
//        { "cn=hello world,dc=com", "CN=hello world,dc=com", 0 },
//        { "cn=hello   world,dc=com", "cn=hello world,dc=com", 0 },
//        { "  cn =  hello world  ,dc=com", "cn=hello world,dc=com", 0 },
//        { "cn=hello world\\ ,dc=com", "cn=hello world,dc=com", 0 },
//        { "cn=HELLO WORLD,dc=com", "cn=hello world,dc=com", 0 },
//        { "cn=HELLO+sn=WORLD,dc=com", "sn=world+cn=hello,dc=com", 0 },
//        { "x-test-integer-type=10,dc=com", "x-test-integer-type=9,dc=com", 1 },
        { "x-test-integer-type=999,dc=com", "x-test-integer-type=1000,dc=com", -1 },
//        { "x-test-integer-type=-1,dc=com", "x-test-integer-type=0,dc=com", -1 },
//        { "x-test-integer-type=0,dc=com", "x-test-integer-type=-1,dc=com", 1 },
//        { "cn=aaa,dc=com", "cn=aaaa,dc=com", -1 },
//        { "cn=AAA,dc=com", "cn=aaaa,dc=com", -1 },
//        { "cn=aaa,dc=com", "cn=AAAA,dc=com", -1 },
//        { "cn=aaaa,dc=com", "cn=aaa,dc=com", 1 },
//        { "cn=AAAA,dc=com", "cn=aaa,dc=com", 1 },
//        { "cn=aaaa,dc=com", "cn=AAA,dc=com", 1 },
//        { "cn=aaab,dc=com", "cn=aaaa,dc=com", 1 },
//        { "cn=aaaa,dc=com", "cn=aaab,dc=com", -1 },
//        { "dc=aaa,dc=aaa", "dc=bbb", -1 },
//        { "dc=bbb,dc=aaa", "dc=bbb", -1 },
//        { "dc=ccc,dc=aaa", "dc=bbb", -1 },
//        { "dc=aaa,dc=bbb", "dc=bbb", 1 },
//        { "dc=bbb,dc=bbb", "dc=bbb", 1 },
//        { "dc=ccc,dc=bbb", "dc=bbb", 1 },
//        { "dc=aaa,dc=ccc", "dc=bbb", 1 },
//        { "dc=bbb,dc=ccc", "dc=bbb", 1 },
//        { "dc=ccc,dc=ccc", "dc=bbb", 1 },
//        { "", "dc=bbb", -1 },
//        { "dc=bbb", "", 1 }
    };
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
  public void testEquality(String first, String second, int result)
      throws Exception {
    DN dn1 = DN.valueOf(first);
    DN dn2 = DN.valueOf(second);

    String msg = "DN equality for <" + first + "> and <" + second + ">";
    if (result == 0) {
      assertEquals   (dn1, dn2, msg);
    } else {
      assertNotEquals(dn1, dn2, msg);
    }
  }



  /**
   * Tests the equals method with a value that's not a DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testEqualsNonDN() throws Exception {
    DN dn = DN.valueOf("dc=example,dc=com");

    assertNotEquals(dn, "not a DN");
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
  public void testHashCode(String first, String second, int result)
      throws Exception {
    DN dn1 = DN.valueOf(first);
    DN dn2 = DN.valueOf(second);

    int h1 = dn1.hashCode();
    int h2 = dn2.hashCode();

    if (result == 0) {
      assertEquals(h1, h2,
          "Hash codes for <" + first + "> and <" + second + "> should be the same.");
    } else {
      assertNotEquals(h1, h2,
          "Hash codes for <" + first + "> and <" + second + "> should be the same.");
    }
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
  @Test(dataProvider = "createDNEqualityData")
  public void testCompareTo(String first, String second, int result)
      throws Exception {
    DN dn1 = DN.valueOf(first);
    DN dn2 = DN.valueOf(second);

    int rc = dn1.compareTo(dn2);

    // Normalize the result.
    if (rc < 0) {
      rc = -1;
    } else if (rc > 0) {
      rc = 1;
    }

    assertEquals(rc, result, "Comparison for <" + first + "> and <"
        + second + ">.");
  }



  @DataProvider
  public Object[][] renameData()
  {
    return new Object[][] {
        // DN to rename, from DN, to DN , expected DN after renaming
        { "dc=com", "dc=com", "dc=org", "dc=org" },
        { "dc=com2", "dc=com", "dc=org", "dc=com2" },
        { "dc=example1,dc=com", "dc=com", "dc=org", "dc=example1,dc=org"},
        { "dc=example1,dc=example2,dc=com", "dc=com", "dc=org", "dc=example1,dc=example2,dc=org"},
        { "dc=example1,dc=example2,dc=com", "dc=example2,dc=com", "dc=example2,dc=org",
            "dc=example1,dc=example2,dc=org"},
        { "dc=example1,dc=example2,dc=com", "dc=example2,dc=com", "dc=example3,dc=org",
            "dc=example1,dc=example3,dc=org"}
    };
  }

  @Test(dataProvider="renameData")
  public void testRename(String dnString, String fromDN, String toDN, String expectedDN) throws Exception
  {
    DN dn = DN.valueOf(dnString);
    DN renamed = dn.rename(DN.valueOf(fromDN), DN.valueOf(toDN));
    assertThat((Object) renamed).isEqualTo(DN.valueOf(expectedDN));
  }
}

