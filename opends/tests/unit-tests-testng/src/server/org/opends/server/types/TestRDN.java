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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.ArrayList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * This class defines a set of tests for the
 * {@link org.opends.server.types.RDN} class.
 */
public final class TestRDN extends TypesTestCase {

  // Domain component attribute type.
  private AttributeType AT_DC;

  // Common name attribute type.
  private AttributeType AT_CN;

  // Test attribute value.
  private AttributeValue AV_DC_ORG;

  // Test attribute value.
  private AttributeValue AV_DC_OPENDS;

  // Test attribute value.
  private AttributeValue AV_CN;



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

    AT_DC = DirectoryServer.getAttributeType("dc");
    AT_CN = DirectoryServer.getAttributeType("cn");

    AttributeType dummy = DirectoryServer.getDefaultAttributeType(
        "x-test-integer-type", DirectoryServer
            .getDefaultIntegerSyntax());
    DirectoryServer.getSchema().registerAttributeType(dummy, true);

    AV_DC_ORG = new AttributeValue(AT_DC, "org");
    AV_DC_OPENDS = new AttributeValue(AT_DC, "opends");
    AV_CN = new AttributeValue(AT_DC, "hello world");
  }



  /**
   * Test RDN construction with single AVA.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructor() throws Exception {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);

    assertEquals(rdn.getNumValues(), 1);
    assertEquals(rdn.getAttributeType(0), AT_DC);
    assertEquals(rdn.getAttributeName(0), AT_DC.getNameOrOID());
    assertEquals(rdn.getAttributeValue(0), AV_DC_ORG);
  }



  /**
   * Test RDN construction with single AVA and a user-defined name.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorWithName() throws Exception {
    RDN rdn = new RDN(AT_DC, "domainComponent", AV_DC_ORG);

    assertEquals(rdn.getNumValues(), 1);
    assertEquals(rdn.getAttributeType(0), AT_DC);
    assertEquals(rdn.getAttributeName(0), "domainComponent");
    assertEquals(rdn.getAttributeValue(0), AV_DC_ORG);
  }



  /**
   * Test RDN construction with a multiple AVA elements.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorMultiAVA() throws Exception {
    AttributeType[]  attrTypes  = { AT_DC, AT_CN };
    String[]         attrNames  = { AT_DC.getNameOrOID(),
                                    AT_CN.getNameOrOID() };
    AttributeValue[] attrValues = { AV_DC_ORG, AV_CN };

    RDN rdn = new RDN(attrTypes, attrNames, attrValues);

    assertEquals(rdn.getNumValues(), 2);

    assertEquals(rdn.getAttributeType(0), AT_DC);
    assertEquals(rdn.getAttributeName(0), AT_DC.getNameOrOID());
    assertEquals(rdn.getAttributeValue(0), AV_DC_ORG);

    assertEquals(rdn.getAttributeType(1), AT_CN);
    assertEquals(rdn.getAttributeName(1), AT_CN.getNameOrOID());
    assertEquals(rdn.getAttributeValue(1), AV_CN);
  }



  /**
   * Test RDN construction with a multiple AVA elements.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorMultiAVAList() throws Exception {
    ArrayList<AttributeType>  typeList  = new ArrayList<AttributeType>();
    ArrayList<String>         nameList  = new ArrayList<String>();
    ArrayList<AttributeValue> valueList = new ArrayList<AttributeValue>();

    typeList.add(AT_DC);
    nameList.add(AT_DC.getNameOrOID());
    valueList.add(AV_DC_ORG);

    typeList.add(AT_CN);
    nameList.add(AT_CN.getNameOrOID());
    valueList.add(AV_CN);

    RDN rdn = new RDN(typeList, nameList, valueList);

    assertEquals(rdn.getNumValues(), 2);

    assertEquals(rdn.getAttributeType(0), AT_DC);
    assertEquals(rdn.getAttributeName(0), AT_DC.getNameOrOID());
    assertEquals(rdn.getAttributeValue(0), AV_DC_ORG);

    assertEquals(rdn.getAttributeType(1), AT_CN);
    assertEquals(rdn.getAttributeName(1), AT_CN.getNameOrOID());
    assertEquals(rdn.getAttributeValue(1), AV_CN);
  }



  /**
   * RDN test data provider.
   *
   * @return The array of test RDN strings.
   */
  @DataProvider(name = "testRDNs")
  public Object[][] createData() {
    return new Object[][] {
        { "dc=hello world", "dc=hello world", "dc=hello world" },
        { "dc =hello world", "dc=hello world", "dc=hello world" },
        { "dc  =hello world", "dc=hello world", "dc=hello world" },
        { "dc= hello world", "dc=hello world", "dc=hello world" },
        { "dc=  hello world", "dc=hello world", "dc=hello world" },
        { "undefined=hello", "undefined=hello", "undefined=hello" },
        { "DC=HELLO WORLD", "dc=hello world", "DC=HELLO WORLD" },
        { "dc = hello    world", "dc=hello world",
            "dc=hello    world" },
        { "   dc = hello world   ", "dc=hello world",
            "dc=hello world" },
        { "givenName=John+cn=Doe", "cn=doe+givenname=john",
            "givenName=John+cn=Doe" },
        { "givenName=John\\+cn=Doe", "givenname=john\\+cn=doe",
            "givenName=John\\+cn=Doe" },
        { "cn=Doe\\, John", "cn=doe\\, john", "cn=Doe\\, John" },
        { "OU=Sales+CN=J. Smith", "cn=j. smith+ou=sales",
            "OU=Sales+CN=J. Smith" },
        { "CN=James \\\"Jim\\\" Smith\\, III",
            "cn=james \\\"jim\\\" smith\\, iii",
            "CN=James \\\"Jim\\\" Smith\\, III" },
        { "CN=Before\\0dAfter", "cn=before\\0dafter",
            "CN=Before\\0dAfter" },
        { "1.3.6.1.4.1.1466.0=#04024869",
            "1.3.6.1.4.1.1466.0=\\04\\02hi",
            "1.3.6.1.4.1.1466.0=\\04\\02Hi" },
        { "CN=Lu\\C4\\8Di\\C4\\87", "cn=lu\\c4\\8di\\c4\\87",
            "CN=Lu\\c4\\8di\\c4\\87" },
        { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8",
            "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8",
            "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8" },
        { "photo=\\ john \\ ", "photo=\\ john \\ ",
            "photo=\\ john \\ " },
        { "AB-global=", "ab-global=", "AB-global=" },
        { "cn=John+a=", "a=+cn=john", "cn=John+a=" },
        { "OID.1.3.6.1.4.1.1466.0=#04024869",
            "1.3.6.1.4.1.1466.0=\\04\\02hi",
            "1.3.6.1.4.1.1466.0=\\04\\02Hi" },
        { "O=\"Sue, Grabbit and Runn\"", "o=sue\\, grabbit and runn",
            "O=Sue\\, Grabbit and Runn" }, };
  }



  /**
   * Test RDN string decoder.
   *
   * @param rawRDN
   *          Raw RDN string representation.
   * @param normRDN
   *          Normalized RDN string representation.
   * @param stringRDN
   *          String representation.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testRDNs")
  public void testDecodeString(String rawRDN, String normRDN,
      String stringRDN) throws Exception {
    RDN rdn = RDN.decode(rawRDN);
    assertEquals(rdn.toNormalizedString(), normRDN);
  }



  /**
   * Illegal RDN test data provider.
   *
   * @return The array of illegal test RDN strings.
   */
  @DataProvider(name = "illegalRDNs")
  public Object[][] createIllegalData() {
    return new Object[][] { { null }, { "" }, { " " }, { "=" }, { "manager" },
        { "manager " }, { "cn+"}, { "cn+Jim" }, { "cn=Jim+" }, { "cn=Jim +" },
        { "cn=Jim+ " }, { "cn=Jim+sn" }, { "cn=Jim+sn " },
        { "cn=Jim+sn equals" }, { "cn=Jim," }, { "cn=Jim;" }, { "cn=Jim,  " },
        { "cn=Jim+sn=a," }, { "cn=Jim, sn=Jam " }, { "cn+uid=Jim" },
        { "-cn=Jim" }, { "/tmp=a" }, { "\\tmp=a" }, { "cn;lang-en=Jim" },
        { "@cn=Jim" }, { "_name_=Jim" }, { "\u03c0=pi" }, { "v1.0=buggy" },
        { "cn=Jim+sn=Bob++" }, { "cn=Jim+sn=Bob+," },
        { "1.3.6.1.4.1.1466..0=#04024869" }, };
  }



  /**
   * Test RDN string decoder against illegal strings.
   *
   * @param rawRDN
   *          Illegal RDN string representation.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalRDNs", expectedExceptions = DirectoryException.class)
  public void testDecodeString(String rawRDN) throws Exception {
    RDN.decode(rawRDN);

    fail("Expected exception for value \"" + rawRDN + "\"");
  }



  /**
   * Test getAttributeName.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeName() throws Exception {
    AttributeType[]  attrTypes  = { AT_DC, AT_CN };
    String[]         attrNames  = { AT_DC.getNameOrOID(),
                                    AT_CN.getNameOrOID() };
    AttributeValue[] attrValues = { AV_DC_ORG, AV_CN };

    RDN rdn = new RDN(attrTypes, attrNames, attrValues);

    assertEquals(rdn.getAttributeName(0), AT_DC.getNameOrOID());
    assertEquals(rdn.getAttributeName(1), AT_CN.getNameOrOID());
  }



  /**
   * Test getAttributeName IndexOutOfBoundsException.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testGetAttributeNameException() throws Exception {
    RDN rdn = new RDN(new AttributeType[0], new String[0],
                      new AttributeValue[0]);

    rdn.getAttributeName(1);
  }



  /**
   * Test getAttributeType.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeType() throws Exception {
    AttributeType[]  attrTypes  = { AT_DC, AT_CN };
    String[]         attrNames  = { AT_DC.getNameOrOID(),
                                    AT_CN.getNameOrOID() };
    AttributeValue[] attrValues = { AV_DC_ORG, AV_CN };

    RDN rdn = new RDN(attrTypes, attrNames, attrValues);

    assertEquals(rdn.getAttributeType(0), AT_DC);
    assertEquals(rdn.getAttributeType(1), AT_CN);
  }



  /**
   * Test getAttributeType IndexOutOfBoundsException.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testGetAttributeTypeException() throws Exception {
    RDN rdn = new RDN(new AttributeType[0], new String[0],
                      new AttributeValue[0]);

    rdn.getAttributeType(1);
  }



  /**
   * Test getAttributeValue.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeValue() throws Exception {
    AttributeType[]  attrTypes  = { AT_DC, AT_CN };
    String[]         attrNames  = { AT_DC.getNameOrOID(),
                                    AT_CN.getNameOrOID() };
    AttributeValue[] attrValues = { AV_DC_ORG, AV_CN };

    RDN rdn = new RDN(attrTypes, attrNames, attrValues);

    assertEquals(rdn.getAttributeValue(0), AV_DC_ORG);
    assertEquals(rdn.getAttributeValue(1), AV_CN);
  }



  /**
   * Test getAttributeValue IndexOutOfBoundsException.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testGetAttributeValueException() throws Exception {
    RDN rdn = new RDN(new AttributeType[0], new String[0],
                      new AttributeValue[0]);

    rdn.getAttributeValue(1);
  }



  /**
   * Test getAttributeValue.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeValueByType() throws Exception {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);

    assertEquals(rdn.getAttributeValue(AT_DC), AV_DC_ORG);
    assertNull(rdn.getAttributeValue(AT_CN));
  }



  /**
   * Test getNumValues.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNumValues() throws Exception {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);
    assertEquals(rdn.getNumValues(), 1);

    rdn.addValue(AT_CN, AT_CN.getNameOrOID(), AV_CN);
    assertEquals(rdn.getNumValues(), 2);
  }



  /**
   * Test hasAttributeType.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testHasAttributeType1() throws Exception {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);

    assertTrue(rdn.hasAttributeType(AT_DC));
    assertTrue(rdn.hasAttributeType("dc"));
    assertTrue(rdn.hasAttributeType(AT_DC.getOID()));
    assertFalse(rdn.hasAttributeType(AT_CN));
    assertFalse(rdn.hasAttributeType("cn"));
  }



  /**
   * Test isMultiValued.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsMultiValued() throws Exception {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);
    assertEquals(rdn.getNumValues(), 1);
    assertFalse(rdn.isMultiValued());

    rdn.addValue(AT_CN, AT_CN.getNameOrOID(), AV_CN);
    assertTrue(rdn.isMultiValued());
  }



  /**
   * Tests hasValue.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testHasValue() throws Exception {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);
    assertTrue(rdn.hasValue(AT_DC, AV_DC_ORG));
    assertFalse(rdn.hasValue(AT_CN, AV_CN));

    rdn.addValue(AT_CN, AT_CN.getNameOrOID(), AV_CN);
    assertTrue(rdn.hasValue(AT_DC, AV_DC_ORG));
    assertTrue(rdn.hasValue(AT_CN, AV_CN));
  }



  /**
   * Tests addValue with a duplicate value.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testAddDuplicateValue() throws Exception {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);
    assertFalse(rdn.addValue(AT_DC, AT_DC.getNameOrOID(), AV_DC_ORG));
  }



  /**
   * Test RDN string decoder.
   *
   * @param rawRDN
   *          Raw RDN string representation.
   * @param normRDN
   *          Normalized RDN string representation.
   * @param stringRDN
   *          String representation.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testRDNs")
  public void testToString(String rawRDN, String normRDN,
      String stringRDN) throws Exception {
    RDN rdn = RDN.decode(rawRDN);
    assertEquals(rdn.toString(), stringRDN);
  }



  /**
   * Tests the duplicate method with a single-valued RDN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testDuplicateSingle() {
    RDN rdn1 = new RDN(AT_DC, AV_DC_ORG);
    RDN rdn2 = rdn1.duplicate();

    assertFalse(rdn1 == rdn2);
    assertEquals(rdn1, rdn2);
  }



  /**
   * Tests the duplicate method with a multivalued RDN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testDuplicateMultiValued() {
    AttributeType[]  types  = new AttributeType[] { AT_DC, AT_CN };
    String[]         names  = new String[] { "dc", "cn" };
    AttributeValue[] values = new AttributeValue[] { AV_DC_ORG, AV_CN };

    RDN rdn1 = new RDN(types, names, values);
    RDN rdn2 = rdn1.duplicate();

    assertFalse(rdn1 == rdn2);
    assertEquals(rdn1, rdn2);
  }



  /**
   * RDN equality test data provider.
   *
   * @return The array of test RDN strings.
   */
  @DataProvider(name = "createRDNEqualityData")
  public Object[][] createRDNEqualityData() {
    return new Object[][] {
        { "cn=hello world", "cn=hello world", 0 },
        { "cn=hello world", "CN=hello world", 0 },
        { "cn=hello   world", "cn=hello world", 0 },
        { "  cn =  hello world  ", "cn=hello world", 0 },
        { "cn=hello world\\ ", "cn=hello world", 0 },
        { "cn=HELLO WORLD", "cn=hello world", 0 },
        { "cn=HELLO+sn=WORLD", "sn=world+cn=hello", 0 },
        { "cn=HELLO+sn=WORLD", "cn=hello+sn=nurse", 1 },
        { "cn=HELLO+sn=WORLD", "cn=howdy+sn=yall", -1 },
        { "cn=hello", "cn=hello+sn=world", -1 },
        { "cn=hello+sn=world", "cn=hello", 1 },
        { "cn=hello+sn=world", "cn=hello+description=world", 1 },
        { "cn=hello", "sn=world", -1 },
        { "sn=hello", "cn=world", 1 },
        { "x-test-integer-type=10", "x-test-integer-type=9", 1 },
        { "x-test-integer-type=999", "x-test-integer-type=1000", -1 },
        { "x-test-integer-type=-1", "x-test-integer-type=0", -1 },
        { "x-test-integer-type=0", "x-test-integer-type=-1", 1 },
        { "cn=aaa", "cn=aaaa", -1 }, { "cn=AAA", "cn=aaaa", -1 },
        { "cn=aaa", "cn=AAAA", -1 }, { "cn=aaaa", "cn=aaa", 1 },
        { "cn=AAAA", "cn=aaa", 1 }, { "cn=aaaa", "cn=AAA", 1 },
        { "cn=aaab", "cn=aaaa", 1 }, { "cn=aaaa", "cn=aaab", -1 } };
  }



  /**
   * Test RDN equality
   *
   * @param first
   *          First RDN to compare.
   * @param second
   *          Second RDN to compare.
   * @param result
   *          Expected comparison result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createRDNEqualityData")
  public void testEquality(String first, String second, int result)
      throws Exception {
    RDN rdn1 = RDN.decode(first);
    RDN rdn2 = RDN.decode(second);

    if (result == 0) {
      assertTrue(rdn1.equals(rdn2), "RDN equality for <" + first
          + "> and <" + second + ">");
    } else {
      assertFalse(rdn1.equals(rdn2), "RDN equality for <" + first
          + "> and <" + second + ">");
    }
  }



  /**
   * Test RDN hashCode
   *
   * @param first
   *          First RDN to compare.
   * @param second
   *          Second RDN to compare.
   * @param result
   *          Expected comparison result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createRDNEqualityData")
  public void testHashCode(String first, String second, int result)
      throws Exception {
    RDN rdn1 = RDN.decode(first);
    RDN rdn2 = RDN.decode(second);

    int h1 = rdn1.hashCode();
    int h2 = rdn2.hashCode();

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
   * Test RDN compareTo
   *
   * @param first
   *          First RDN to compare.
   * @param second
   *          Second RDN to compare.
   * @param result
   *          Expected comparison result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "createRDNEqualityData")
  public void testCompareTo(String first, String second, int result)
      throws Exception {
    RDN rdn1 = RDN.decode(first);
    RDN rdn2 = RDN.decode(second);

    int rc = rdn1.compareTo(rdn2);

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
   * Tests the equals method with a null argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testEqualityNull() {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);

    assertFalse(rdn.equals(null));
  }



  /**
   * Tests the equals method with a non-RDN argument.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testEqualityNonRDN() {
    RDN rdn = new RDN(AT_DC, AV_DC_ORG);

    assertFalse(rdn.equals("this isn't an RDN"));
  }
}

