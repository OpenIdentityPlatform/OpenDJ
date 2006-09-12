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
package org.opends.server.extensions;



import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * A set of test cases for the certificate validation policy enumeration.
 */
public class CertificateValidationPolicyTestCase
       extends ExtensionsTestCase
{
  /**
   * Retrieves the names of the certificate validation policies.
   *
   * @return  The names of the certificate validation policies.
   */
  @DataProvider(name= "policyNames")
  public Object[][] getPolicyNames()
  {
    return new Object[][]
    {
      new Object[] { "ALWAYS" },
      new Object[] { "NEVER" },
      new Object[] { "IFPRESENT" },
    };
  }



  /**
   * Tests the <CODE>policyForName</CODE> method with a valid policy name.
   *
   * @param  name  The name for the policy to retrieve.
   */
  @Test(dataProvider = "policyNames")
  public void testPolicyForValidName(String name)
  {
    assertNotNull(CertificateValidationPolicy.policyForName(name));
  }



  /**
   * Tests the <CODE>policyForName</CODE> method with an invalid policy name.
   */
  @Test()
  public void testPolicyForInvalidName()
  {
    assertNull(CertificateValidationPolicy.policyForName("invalid"));
  }



  /**
   * Tests the <CODE>valueOf</CODE> method with a valid policy name.
   *
   * @param  name  The name for the policy to retrieve.
   */
  @Test(dataProvider = "policyNames")
  public void testValueOfValid(String name)
  {
    assertNotNull(CertificateValidationPolicy.valueOf(name));
  }



  /**
   * Tests the <CODE>valueOf</CODE> method with an invalid policy name.
   */
  @Test(expectedExceptions = { IllegalArgumentException.class })
  public void testValueOfInvalid()
  {
    CertificateValidationPolicy.valueOf("invalid");
  }



  /**
   * Tests the <CODE>values</CODE> method.
   */
  @Test()
  public void testValues()
  {
    assertNotNull(CertificateValidationPolicy.values());
  }



  /**
   * Tests the <CODE>toString</CODE> method.
   *
   * @param  name  The name for the policy to retrieve.
   */
  @Test(dataProvider = "policyNames")
  public void testToString(String name)
  {
    CertificateValidationPolicy.policyForName(name).toString();
  }
}

