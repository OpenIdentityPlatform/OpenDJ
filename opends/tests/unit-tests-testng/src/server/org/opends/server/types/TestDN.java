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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.ArrayList;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;



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
  @DataProvider(name = "testDNs")
  public Object[][] createData() {
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
        { "dc=example,dc=com", "dc=example,dc=com",
            "dc=example,dc=com" },
        { "dc=example, dc=com", "dc=example,dc=com",
            "dc=example,dc=com" },
        { "dc=example ,dc=com", "dc=example,dc=com",
            "dc=example,dc=com" },
        { "dc =example , dc  =   com", "dc=example,dc=com",
            "dc=example,dc=com" },
        { "givenName=John+cn=Doe,ou=People,dc=example,dc=com",
            "cn=doe+givenname=john,ou=people,dc=example,dc=com",
            "givenName=John+cn=Doe,ou=People,dc=example,dc=com" },
        { "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
            "givenname=john\\+cn=doe,ou=people,dc=example,dc=com",
            "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com" },
        { "cn=Doe\\, John,ou=People,dc=example,dc=com",
            "cn=doe\\, john,ou=people,dc=example,dc=com",
            "cn=Doe\\, John,ou=People,dc=example,dc=com" },
        { "UID=jsmith,DC=example,DC=net",
            "uid=jsmith,dc=example,dc=net",
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
        { "CN=Before\\0dAfter,DC=example,DC=net",
            "cn=before\\0dafter,dc=example,dc=net",
            "CN=Before\\0dAfter,DC=example,DC=net" },
        { "1.3.6.1.4.1.1466.0=#04024869",
            "1.3.6.1.4.1.1466.0=\\04\\02hi",
            "1.3.6.1.4.1.1466.0=\\04\\02Hi" },
        { "1.1.1=", "1.1.1=", "1.1.1=" },
        { "CN=Lu\\C4\\8Di\\C4\\87", "cn=lu\\c4\\8di\\c4\\87",
            "CN=Lu\\c4\\8di\\c4\\87" },
        { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius",
            "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=airius",
            "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius" },
        { "photo=\\ john \\ ,dc=com", "photo=\\ john \\ ,dc=com",
            "photo=\\ john \\ ,dc=com" },
        { "AB-global=", "ab-global=", "AB-global=" },
        { "OU= Sales + CN = J. Smith ,DC=example,DC=net",
            "cn=j. smith+ou=sales,dc=example,dc=net",
            "OU=Sales+CN=J. Smith,DC=example,DC=net" },
        { "cn=John+a=", "a=+cn=john", "cn=John+a=" },
        { "OID.1.3.6.1.4.1.1466.0=#04024869",
            "1.3.6.1.4.1.1466.0=\\04\\02hi",
            "1.3.6.1.4.1.1466.0=\\04\\02Hi" },
        { "O=\"Sue, Grabbit and Runn\",C=US",
            "o=sue\\, grabbit and runn,c=us",
            "O=Sue\\, Grabbit and Runn,C=US" }, };
  }



  /**
   * Illegal DN test data provider.
   *
   * @return The array of illegal test DN strings.
   */
  @DataProvider(name = "illegalDNs")
  public Object[][] createIllegalData() {
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
        { "cn=a\\b" }, { "cn=a\\bg" }, { "cn=\"hello" } };
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

    AttributeType dummy = DirectoryServer.getDefaultAttributeType(
        "x-test-integer-type", DirectoryServer
            .getDefaultIntegerSyntax());
    DirectoryServer.getSchema().registerAttributeType(dummy, true);
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateNullDN1() throws Exception {
    DN dn = new DN(new RDN[0]);

    assertEquals(dn, DN.nullDN());
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateNullDN2() throws Exception {
    DN dn = new DN();

    assertEquals(dn, DN.nullDN());
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateNullDN3() throws Exception {
    DN dn = new DN((RDN[]) null);

    assertEquals(dn, DN.nullDN());
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateNullDN4() throws Exception {
    DN dn = new DN((ArrayList<RDN>) null);

    assertEquals(dn, DN.nullDN());
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateWithSingleRDN1() throws Exception {
    DN dn = new DN(new RDN[] { RDN.decode("dc=com") });

    assertEquals(dn, DN.decode("dc=com"));
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateWithMultipleRDNs1() throws Exception {
    DN dn = new DN(new RDN[] { RDN.decode("dc=foo"),
        RDN.decode("dc=opends"), RDN.decode("dc=org") });

    assertEquals(dn, DN.decode("dc=foo,dc=opends,dc=org"));
  }



  /**
   * Tests the create method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testCreateWithMultipleRDNs2() throws Exception {
    ArrayList<RDN> rdnList = new ArrayList<RDN>();
    rdnList.add(RDN.decode("dc=foo"));
    rdnList.add(RDN.decode("dc=opends"));
    rdnList.add(RDN.decode("dc=org"));
    DN dn = new DN(rdnList);

    assertEquals(dn, DN.decode("dc=foo,dc=opends,dc=org"));
  }



  /**
   * Tests the <CODE>decode</CODE> method which takes a String
   * argument.
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
  public void testDecodeString(String rawDN, String normDN,
      String stringDN) throws Exception {
    DN dn = DN.decode(rawDN);
    assertEquals(dn.toNormalizedString(), normDN);
  }



  /**
   * Tests the <CODE>decode</CODE> method which takes a String
   * argument.
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
  public void testDecodeOctetString(String rawDN, String normDN,
      String stringDN) throws Exception {
    ASN1OctetString octetString = new ASN1OctetString(rawDN);

    DN dn = DN.decode(octetString);
    assertEquals(dn.toNormalizedString(), normDN);
  }



  /**
   * Tests the toNoramlizedString methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testToNormalizedString() throws Exception {
    DN dn = DN.decode("dc=example,dc=com");

    StringBuilder buffer = new StringBuilder();
    dn.toNormalizedString(buffer);
    assertEquals(buffer.toString(), "dc=example,dc=com");

    assertEquals(dn.toNormalizedString(), "dc=example,dc=com");
  }



  /**
   * Tests both variants of the {@code decode} method with null arguments.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testDecodeNull() throws Exception {
    assertEquals(DN.decode((ByteString) null), DN.nullDN());
    assertEquals(DN.decode((String) null), DN.nullDN());
  }



  /**
   * Test that decoding an illegal DN as a String throws an exception.
   *
   * @param dn
   *          The illegal DN to be tested.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalDNs", expectedExceptions = DirectoryException.class)
  public void testIllegalStringDNs(String dn) throws Exception {
    try {
      DN.decode(dn);
    } catch (DirectoryException e) {
      throw e;
    } catch (Exception e) {
      System.out.println("Illegal DN <" + dn
          + "> threw the wrong type of exception");
      throw e;
    }

    throw new RuntimeException("Illegal DN <" + dn
        + "> did not throw an exception");
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
  @Test(dataProvider = "illegalDNs", expectedExceptions = DirectoryException.class)
  public void testIllegalOctetStringDNs(String dn) throws Exception {
    ASN1OctetString octetString = new ASN1OctetString(dn);

    try {
      DN.decode(octetString);
    } catch (DirectoryException e) {
      throw e;
    } catch (Exception e) {
      System.out.println("Illegal DN <" + dn
          + "> threw the wrong type of exception");
      throw e;
    }

    throw new RuntimeException("Illegal DN <" + dn
        + "> did not throw an exception");
  }



  /**
   * Test the nullDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testNullDN() throws Exception {
    DN nullDN = DN.nullDN();

    assertTrue(nullDN.getNumComponents() == 0);
    assertEquals(nullDN.toNormalizedString(), "");
  }



  /**
   * Test the isNullDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsNullDNWithNullDN() throws Exception {
    DN nullDN = DN.nullDN();
    assertTrue(nullDN.isNullDN());
  }



  /**
   * Test the isNullDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsNullDNWithNonNullDN() throws Exception {
    DN dn = DN.decode("dc=com");
    assertFalse(dn.isNullDN());
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
    DN dn = DN.decode(s);
    assertEquals(dn.getNumComponents(), sz);
  }



  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createParentAndRDNTestData")
  public Object[][] createParentAndRDNTestData() {
    return new Object[][] {
        { "", null, null },
        { "dc=com", null, "dc=com" },
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
    DN dn = DN.decode(s);
    DN parent = (p != null ? DN.decode(p) : null);

    assertEquals(dn.getParent(), parent, "For DN " + s);
  }



  /**
   * Retrieves the naming contexts defined in the server.
   */
  @DataProvider(name = "namingContexts")
  public Object[][] getNamingContexts() {
    ArrayList<DN> contextList = new ArrayList<DN>();
    for (DN baseDN : DirectoryServer.getPublicNamingContexts().keySet())
    {
      contextList.add(baseDN);
    }

    for (DN baseDN : DirectoryServer.getPrivateNamingContexts().keySet())
    {
      contextList.add(baseDN);
    }

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
    assertNull(namingContext.getParentDNInSuffix());

    DN childDN = namingContext.concat(RDN.decode("ou=People"));
    assertNotNull(childDN.getParentDNInSuffix());
    assertEquals(childDN.getParentDNInSuffix(), namingContext);
  }



  /**
   * Test the getParent method's interaction with other methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetParentInteraction() throws Exception {
    DN c = DN.decode("dc=foo,dc=bar,dc=opends,dc=org");
    DN e = DN.decode("dc=bar,dc=opends,dc=org");
    DN p = c.getParent();

    assertFalse(p.isNullDN());

    assertEquals(p.getNumComponents(), 3);

    assertEquals(p.compareTo(c), -1);
    assertEquals(c.compareTo(p), 1);

    assertTrue(p.isAncestorOf(c));
    assertFalse(c.isAncestorOf(p));

    assertTrue(c.isDescendantOf(p));
    assertFalse(p.isDescendantOf(c));

    assertEquals(p, e);
    assertEquals(p.hashCode(), e.hashCode());

    assertEquals(p.toNormalizedString(), e.toNormalizedString());
    assertEquals(p.toString(), e.toString());

    assertEquals(p.getRDN(), RDN.decode("dc=bar"));

    assertEquals(p.getRDN(0), RDN.decode("dc=bar"));
    assertEquals(p.getRDN(1), RDN.decode("dc=opends"));
    assertEquals(p.getRDN(2), RDN.decode("dc=org"));

    assertEquals(p.getParent(), DN.decode("dc=opends,dc=org"));
    assertEquals(p.getParent(), e.getParent());

    assertEquals(p.concat(RDN.decode("dc=foo")), DN
        .decode("dc=foo,dc=bar,dc=opends,dc=org"));
    assertEquals(p.concat(RDN.decode("dc=foo")), c);
    assertEquals(p.concat(DN.decode("dc=xxx,dc=foo")), DN
        .decode("dc=xxx,dc=foo,dc=bar,dc=opends,dc=org"));
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
    DN dn = DN.decode(s);
    RDN rdn = (r != null ? RDN.decode(r) : null);

    assertEquals(dn.getRDN(), rdn, "For DN " + s);
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
    DN dn = DN.decode(s);
    RDN rdn = RDN.decode(r);

    assertEquals(dn.getRDN(i), rdn, "For DN " + s);
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
    DN dn = DN.decode(s);

    // Shoudld throw.
    dn.getRDN(i);

    fail("Excepted exception for RDN index " + i + " in DN " + s);
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
    DN dn = DN.decode(s);
    DN localName = DN.decode(l);
    DN expected = DN.decode(e);

    assertEquals(dn.concat(localName), expected);
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
    DN dn = DN.decode("dc=org");
    dn.concat((DN) null);
  }



  /**
   * Test the concat(DN) method's interaction with other methods.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConcatDNInteraction() throws Exception {
    DN p = DN.decode("dc=opends,dc=org");
    DN l = DN.decode("dc=foo,dc=bar");
    DN e = DN.decode("dc=foo,dc=bar,dc=opends,dc=org");
    DN c = p.concat(l);

    assertFalse(c.isNullDN());

    assertEquals(c.getNumComponents(), 4);

    assertEquals(c.compareTo(p), 1);
    assertEquals(p.compareTo(c), -1);

    assertTrue(p.isAncestorOf(c));
    assertFalse(c.isAncestorOf(p));

    assertTrue(c.isDescendantOf(p));
    assertFalse(p.isDescendantOf(c));

    assertEquals(c, e);
    assertEquals(c.hashCode(), e.hashCode());

    assertEquals(c.toNormalizedString(), e.toNormalizedString());
    assertEquals(c.toString(), e.toString());

    assertEquals(c.getRDN(), RDN.decode("dc=foo"));

    assertEquals(c.getRDN(0), RDN.decode("dc=foo"));
    assertEquals(c.getRDN(1), RDN.decode("dc=bar"));
    assertEquals(c.getRDN(2), RDN.decode("dc=opends"));
    assertEquals(c.getRDN(3), RDN.decode("dc=org"));

    assertEquals(c.getParent(), DN.decode("dc=bar,dc=opends,dc=org"));
    assertEquals(c.getParent(), e.getParent());

    assertEquals(c.concat(RDN.decode("dc=xxx")), DN
        .decode("dc=xxx,dc=foo,dc=bar,dc=opends,dc=org"));
    assertEquals(c.concat(DN.decode("dc=xxx,dc=yyy")), DN
        .decode("dc=xxx,dc=yyy,dc=foo,dc=bar,dc=opends,dc=org"));
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
    DN dn = DN.decode(s);
    RDN rdn = RDN.decode(r);
    DN expected = DN.decode(e);

    assertEquals(dn.concat(rdn), expected);
  }



  /**
   * Test the concat(RDN...) method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class,
      AssertionError.class })
  public void testConcatRDNException() throws Exception {
    DN dn = DN.decode("dc=org");
    dn.concat((RDN[]) null);
  }



  /**
   * Test the concat(RDN[]...) method.
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
  public void testConcatRDNSequence1(String s, String l, String e)
      throws Exception {
    DN dn = DN.decode(s);
    DN localName = DN.decode(l);
    DN expected = DN.decode(e);

    // Construct sequence.
    RDN[] rdns = new RDN[localName.getNumComponents()];
    for (int i = 0; i < localName.getNumComponents(); i++) {
      rdns[i] = localName.getRDN(i);
    }

    assertEquals(dn.concat(rdns), expected);
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
    DN dn = DN.decode(s);
    DN other = DN.decode(d);

    assertEquals(dn.isAncestorOf(other), e, s + " isAncestoryOf " + d);
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
    DN dn = DN.decode("dc=com");
    dn.isAncestorOf(null);
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
    DN dn = DN.decode(s);
    DN other = DN.decode(d);

    assertEquals(dn.isDescendantOf(other), e, s + " isDescendantOf "
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
    DN dn = DN.decode("dc=com");
    dn.isDescendantOf(null);
  }



  /**
   * DN equality test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "createDNEqualityData")
  public Object[][] createDNEqualityData() {
    return new Object[][] {
        { "cn=hello world,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=hello world,dc=com", "CN=hello world,dc=com", 0 },
        { "cn=hello   world,dc=com", "cn=hello world,dc=com", 0 },
        { "  cn =  hello world  ,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=hello world\\ ,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=HELLO WORLD,dc=com", "cn=hello world,dc=com", 0 },
        { "cn=HELLO+sn=WORLD,dc=com", "sn=world+cn=hello,dc=com", 0 },
        { "x-test-integer-type=10,dc=com",
            "x-test-integer-type=9,dc=com", 1 },
        { "x-test-integer-type=999,dc=com",
            "x-test-integer-type=1000,dc=com", -1 },
        { "x-test-integer-type=-1,dc=com",
            "x-test-integer-type=0,dc=com", -1 },
        { "x-test-integer-type=0,dc=com",
            "x-test-integer-type=-1,dc=com", 1 },
        { "cn=aaa,dc=com", "cn=aaaa,dc=com", -1 },
        { "cn=AAA,dc=com", "cn=aaaa,dc=com", -1 },
        { "cn=aaa,dc=com", "cn=AAAA,dc=com", -1 },
        { "cn=aaaa,dc=com", "cn=aaa,dc=com", 1 },
        { "cn=AAAA,dc=com", "cn=aaa,dc=com", 1 },
        { "cn=aaaa,dc=com", "cn=AAA,dc=com", 1 },
        { "cn=aaab,dc=com", "cn=aaaa,dc=com", 1 },
        { "cn=aaaa,dc=com", "cn=aaab,dc=com", -1 },
        { "dc=aaa,dc=aaa", "dc=bbb", -1 },
        { "dc=bbb,dc=aaa", "dc=bbb", -1 },
        { "dc=ccc,dc=aaa", "dc=bbb", -1 },
        { "dc=aaa,dc=bbb", "dc=bbb", 1 },
        { "dc=bbb,dc=bbb", "dc=bbb", 1 },
        { "dc=ccc,dc=bbb", "dc=bbb", 1 },
        { "dc=aaa,dc=ccc", "dc=bbb", 1 },
        { "dc=bbb,dc=ccc", "dc=bbb", 1 },
        { "dc=ccc,dc=ccc", "dc=bbb", 1 },
        { "", "dc=bbb", -1 },
        { "dc=bbb", "", 1 }
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
    DN dn1 = DN.decode(first);
    DN dn2 = DN.decode(second);

    if (result == 0) {
      assertTrue(dn1.equals(dn2), "DN equality for <" + first
          + "> and <" + second + ">");
    } else {
      assertFalse(dn1.equals(dn2), "DN equality for <" + first
          + "> and <" + second + ">");
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
    DN dn = DN.decode("dc=example,dc=com");

    assertFalse(dn.equals("not a DN"));
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
    DN dn1 = DN.decode(first);
    DN dn2 = DN.decode(second);

    int h1 = dn1.hashCode();
    int h2 = dn2.hashCode();

    if (result == 0) {
      if (h1 != h2) {
        fail("Hash codes for <" + first + "> and <" + second
            + "> should be the same.");
      }
    } else {
      if (h1 == h2) {
        fail("Hash codes for <" + first + "> and <" + second
            + "> should be the same.");
      }
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
    DN dn1 = DN.decode(first);
    DN dn2 = DN.decode(second);

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



  /**
   * Test DN string decoder.
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
  public void testToString(String rawDN, String normDN,
      String stringDN) throws Exception {
    DN dn = DN.decode(rawDN);
    assertEquals(dn.toString(), stringDN);
  }
}

