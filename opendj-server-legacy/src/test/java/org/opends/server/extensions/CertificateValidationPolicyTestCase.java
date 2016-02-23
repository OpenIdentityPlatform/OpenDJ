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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
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
  @Test
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
  @Test
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

