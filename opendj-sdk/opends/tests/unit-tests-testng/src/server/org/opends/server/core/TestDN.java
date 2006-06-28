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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import static org.testng.Assert.*;

import org.opends.server.SchemaFixture;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.DN;
import org.opends.server.types.RDN;
import org.opends.server.types.AttributeValue;
import org.testng.annotations.Configuration;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.ExpectedExceptions;

import java.util.Arrays;

/**
 * This class defines a set of tests for the org.opends.server.core.DN
 * class.
 */
public class TestDN extends CoreTestCase {
  /**
   * DN test data provider.
   *
   * @return The array of test DN strings.
   */
  @DataProvider(name = "testDNs")
  public Object[][] createData() {
    return new Object[][] {
        { "", "" },
        { "   ", "" },
        { "dc=com", "dc=com" },
        { "DC=COM", "dc=com" },
        { "dc = com", "dc=com" },
        { " dc = com ", "dc=com" },
        { "dc=example,dc=com", "dc=example,dc=com" },
        { "dc=example, dc=com", "dc=example,dc=com" },
        { "dc=example ,dc=com", "dc=example,dc=com" },
        { "dc =example , dc  =   com", "dc=example,dc=com" },
        { "givenName=John+cn=Doe,ou=People,dc=example,dc=com",
            "cn=doe+givenname=john,ou=people,dc=example,dc=com" },
        { "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
            "givenname=john\\+cn=doe,ou=people,dc=example,dc=com" },
        { "cn=Doe\\, John,ou=People,dc=example,dc=com",
            "cn=doe\\, john,ou=people,dc=example,dc=com" },
        { "UID=jsmith,DC=example,DC=net", "uid=jsmith,dc=example,dc=net" },
        { "OU=Sales+CN=J. Smith,DC=example,DC=net",
            "cn=j. smith+ou=sales,dc=example,dc=net" },
        { "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net",
            "cn=james \\\"jim\\\" smith\\, iii,dc=example,dc=net" },
        { "CN=Before\\0dAfter,DC=example,DC=net",
            "cn=before\\0dafter,dc=example,dc=net" },
        { "1.3.6.1.4.1.1466.0=#04024869", "1.3.6.1.4.1.1466.0=\\04\\02hi" },
        { "CN=Lu\\C4\\8Di\\C4\\87", "cn=lu\\c4\\8di\\c4\\87" },
        { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius",
            "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=airius" },
        { "photo=\\ john \\ ,dc=com", "photo=\\ john \\ ,dc=com" },
        { "AB-global=", "ab-global=" },
        { "OU= Sales + CN = J. Smith ,DC=example,DC=net",
             "cn=j. smith+ou=sales,dc=example,dc=net" },
         { "cn=John+a=", "a=+cn=john" },
         { "OID.1.3.6.1.4.1.1466.0=#04024869",
              "1.3.6.1.4.1.1466.0=\\04\\02hi" },
         { "O=\"Sue, Grabbit and Runn\",C=US",
              "o=sue\\, grabbit and runn,c=us" },
    };
  }

  /**
   * Illegal DN test data provider.
   *
   * @return The array of illegal test DN strings.
   */
  @DataProvider(name = "illegalDNs")
  public Object[][] createIllegalData() {
    return new Object[][] {
         { "manager" },
         { "manager " },
         { "cn+Jim" },
         { "cn=Jim+" },
         { "cn=Jim," },
         { "cn=Jim,  " },
         { "cn+uid=Jim" },
         { "-cn=Jim" },
         { "/tmp=a" },
         { "\\tmp=a" },
         { "cn;lang-en=Jim" },
         { "@cn=Jim" },
         { "_name_=Jim" },
         { "\u03c0=pi" },
         { "v1.0=buggy" },
         { "1.3.6.1.4.1.1466..0=#04024869" },
    };
  }

  /**
   * DN compare test data provider.
   *
   * @return The unsorted and sorted DN strings .
   */
  @DataProvider(name = "compareDNs")
  public Object[][] createSortData() {
    return new Object[][] {
         {
              // Not sorted.
              new String[]
                   {
                        "UID=jsmith,DC=example,DC=net",
                        "dc=com",
                        "",
                        "dc=example,dc=net",
                        "uid=jsmith,dc=example,dc=com",
                        "dc=example,dc=com",
                        "cn=jsmith,dc=example,dc=com",
                        "",
                        "dc =example , dc  =   com",
                        "uid=asmith,dc=example,dc=net",
                   },
              // Sorted.
              new String[]
                   {
                        "",
                        "",
                        "dc=com",
                        "dc =example , dc  =   com",
                        "dc=example,dc=com",
                        "cn=jsmith,dc=example,dc=com",
                        "uid=jsmith,dc=example,dc=com",
                        "dc=example,dc=net",
                        "uid=asmith,dc=example,dc=net",
                        "UID=jsmith,DC=example,DC=net",
                   },
         }
    };
  }

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @Configuration(beforeTestClass = true)
  public void setUp() throws Exception {
    // This test suite depends on having the schema available.
    SchemaFixture.FACTORY.setUp();
  }

  /**
   * Tears down the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be finalized.
   */
  @Configuration(afterTestClass = true)
  public void tearDown() throws Exception {
    SchemaFixture.FACTORY.tearDown();
  }

  /**
   * Tests the <CODE>decode</CODE> method which takes a String
   * argument.
   *
   * @param rawDN
   *          The raw undecoded DN string.
   * @param normDN
   *          The expected normalized value.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testDNs")
  public void testDecodeString(String rawDN, String normDN)
      throws Exception {
    DN dn = DN.decode(rawDN);
    assertEquals(normDN, dn.toNormalizedString());
  }

  /**
   * Tests the <CODE>decode</CODE> method which takes a String
   * argument.
   *
   * @param rawDN
   *          The raw undecoded DN string.
   * @param normDN
   *          The expected normalized value.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "testDNs")
  public void testDecodeOctetString(String rawDN, String normDN)
      throws Exception {
    ASN1OctetString octetString = new ASN1OctetString(rawDN);

    DN dn = DN.decode(octetString);
    assertEquals(normDN, dn.toNormalizedString());
  }



  /**
   * Test that decoding an illegal DN as a String throws an exception.
   * @param dn The illegal DN to be tested.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalDNs")
  @ExpectedExceptions(value = { DirectoryException.class } )
  public void testIllegalStringDNs(String dn)
       throws Exception
  {
    try
    {
      DN.decode(dn);
    }
    catch (DirectoryException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      System.out.println(
           "Illegal DN <" + dn + "> threw the wrong type of exception");
      throw e;
    }

    throw new RuntimeException(
         "Illegal DN <" + dn + "> did not throw an exception");
  }

  /**
   * Test that decoding an illegal DN as an octet string throws an exception.
   * @param dn The illegal DN to be tested.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "illegalDNs")
  @ExpectedExceptions(value = { DirectoryException.class } )
  public void testIllegalOctetStringDNs(String dn)
       throws Exception
  {
    ASN1OctetString octetString = new ASN1OctetString(dn);

    try
    {
      DN.decode(octetString);
    }
    catch (DirectoryException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      System.out.println(
           "Illegal DN <" + dn + "> threw the wrong type of exception");
      throw e;
    }

    throw new RuntimeException(
         "Illegal DN <" + dn + "> did not throw an exception");
  }

  /**
   * Test the <CODE>compareTo</CODE> method.
   * @param unsorted An array of string DNs in no particular order.
   * @param sorted An array of the same DNs in sort order.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "compareDNs")
  public void testCompareTo(String[] unsorted, String[] sorted)
       throws Exception
  {
    DN[] expected = new DN[sorted.length];
    for (int i = 0; i < sorted.length; i++)
    {
      expected[i] = DN.decode(sorted[i]);
    }


    DN[] actual = new DN[unsorted.length];
    for (int i = 0; i < unsorted.length; i++)
    {
      actual[i] = DN.decode(unsorted[i]);
    }

    Arrays.sort(actual);

    assertEquals(actual, expected);
  }



  /**
   * Test the <CODE>duplicate</CODE> method.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testDuplicate() throws Exception
  {
    String s = "dc=example,dc=com";
    DN orig = DN.decode(s);
    DN dup = orig.duplicate();

    // The duplicate and the original should compare equal.
    assertEquals(dup, orig);

    // Alter the duplicate.
    RDN[] origRDNs = dup.getRDNComponents();
    RDN[] dupRDNs = new RDN[origRDNs.length];
    System.arraycopy(origRDNs, 0, dupRDNs, 0, origRDNs.length);

    AttributeValue[] values = new AttributeValue[1];
    values[0] = origRDNs[0].getAttributeValues()[0];
    values[0] = new AttributeValue(origRDNs[0].getAttributeTypes()[0],
                                   new ASN1OctetString("modified"));
    dupRDNs[0] = new RDN(origRDNs[0].getAttributeTypes(),
                         origRDNs[0].getAttributeNames(),
                         values);

    dup.setRDNComponents(dupRDNs);

    // Check that the duplicate and the original are different.
    String msg = String.format("<%s> and <%s>", dup, orig);
    assertTrue(!dup.equals(orig), msg);
  }

}
