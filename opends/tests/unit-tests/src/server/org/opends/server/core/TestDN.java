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



import java.util.*;
import junit.framework.*;
import org.opends.server.*;
import org.opends.server.protocols.asn1.*;
import org.opends.server.types.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a set of JUnit tests for the org.opends.server.core.DN \
 * class.
 *
 *
 * @author   Neil A. Wilson
 */
public class TestDN
       extends DirectoryServerTestCase
{
  // String representations of DNs to use when testing.  They will be mapped
  // from the "user-defined" form to normalized form.
  private HashMap<String,String> dnStrings;



  /**
   * Creates a new instance of this JUnit test case with the provided name.
   *
   * @param  name  The name to use for this JUnit test case.
   */
  public TestDN(String name)
  {
    super(name);
  }



  /**
   * Get the DN test suite.
   *
   * @return The test suite.
   */
  public static Test getTestSuite() {
    // Create the basic test suite.
    TestSuite suite = new TestSuite();
    suite.addTestSuite(TestDN.class);

    // Wrap it up with dependencies.
    DirectoryServerTestSetup wrapper = new DirectoryServerTestSetup(suite);

    InitialDirectoryServerTestCaseDependency initial;
    initial = new InitialDirectoryServerTestCaseDependency();
    wrapper.registerDependency(initial);

    ConfigurationTestCaseDependency config;
    config = new ConfigurationTestCaseDependency(initial);
    wrapper.registerDependency(config);

    SchemaTestCaseDependency schema;
    schema = new SchemaTestCaseDependency(config);
    wrapper.registerDependency(schema);

    return wrapper;
  }



  /**
   * Performs any necessary initialization for this test case.
   */
  public void setUp()
  {
    // Create and populate the map of DN strings.
    dnStrings = new HashMap<String,String>();

    dnStrings.put("", "");
    dnStrings.put("dc=com", "dc=com");
    dnStrings.put("DC=COM", "dc=com");
    dnStrings.put("dc = com", "dc=com");
    dnStrings.put("dc=example,dc=com", "dc=example,dc=com");
    dnStrings.put("dc=example, dc=com", "dc=example,dc=com");
    dnStrings.put("dc=example ,dc=com", "dc=example,dc=com");
    dnStrings.put("dc =example , dc  =   com", "dc=example,dc=com");
    dnStrings.put("givenName=John+cn=Doe,ou=People,dc=example,dc=com",
                  "cn=doe+givenname=john,ou=people,dc=example,dc=com");
    dnStrings.put("givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
                  "givenname=john\\+cn=doe,ou=people,dc=example,dc=com");
    dnStrings.put("cn=Doe\\, John,ou=People,dc=example,dc=com",
                  "cn=doe\\, john,ou=people,dc=example,dc=com");
    dnStrings.put("UID=jsmith,DC=example,DC=net",
                  "uid=jsmith,dc=example,dc=net");
    dnStrings.put("OU=Sales+CN=J. Smith,DC=example,DC=net",
                  "cn=j. smith+ou=sales,dc=example,dc=net");
    dnStrings.put("CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net",
                  "cn=james \\\"jim\\\" smith\\, iii,dc=example,dc=net");
    dnStrings.put("CN=Before\\0dAfter,DC=example,DC=net",
                  "cn=before\\0dafter,dc=example,dc=net");
    dnStrings.put("1.3.6.1.4.1.1466.0=#04024869",
                  "1.3.6.1.4.1.1466.0=\\04\\02hi");
    dnStrings.put("CN=Lu\\C4\\8Di\\C4\\87", "cn=lu\\c4\\8di\\c4\\87");
    dnStrings.put("ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius",
                  "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=airius");
    dnStrings.put("photo=\\ john \\ ,dc=com", "photo=\\ john \\ ,dc=com");

  }



  /**
   * Performs any necessary cleanup for this test case.
   */
  public void tearDown()
  {
    // No implementation required.
  }



  /**
   * Tests the <CODE>decode</CODE> method which takes a String argument.
   */
  public void testDecodeString()
  {
    for (String rawDN : dnStrings.keySet())
    {
      String normDN = dnStrings.get(rawDN);

      DN dn = null;
      try
      {
        dn = DN.decode(rawDN);
      }
      catch (DirectoryException de)
      {
        String message = "DN decoding failed for raw DN '" + rawDN +
                         "':  " + stackTraceToString(de);
        printError(message);
        throw new AssertionFailedError(message);
      }

      try
      {
        assertEquals(normDN, dn.toNormalizedString());
      }
      catch (AssertionFailedError afe)
      {
        printError("DN string comparison failed for raw DN '" + rawDN +
                   "':  Expected normalized value was '" + normDN +
                   "', calculated was '" + dn.toNormalizedString() + "'.");
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decode</CODE> method which takes a String argument.
   */
  public void testDecodeOctetString()
  {
    for (String rawDN : dnStrings.keySet())
    {
      String normDN = dnStrings.get(rawDN);

      ASN1OctetString octetString = new ASN1OctetString(rawDN);

      DN dn = null;
      try
      {
        dn = DN.decode(octetString);
      }
      catch (DirectoryException de)
      {
        String message = "DN decoding failed for raw DN '" + rawDN +
                         "':  " + stackTraceToString(de);
        printError(message);
        throw new AssertionFailedError(message);
      }

      try
      {
        assertEquals(normDN, dn.toNormalizedString());
      }
      catch (AssertionFailedError afe)
      {
        printError("DN octet string comparison failed for raw DN '" + rawDN +
                   "':  Expected normalized value was '" + normDN +
                   "', calculated was '" + dn.toNormalizedString() + "'.");
        throw afe;
      }
    }
  }
}

