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
package org.opends.server.protocols.asn1;



import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Exception class.
 */
public class TestASN1Exception
       extends ASN1TestCase
{
  /**
   * Tests the first constructor, which takes integer and string arguments.
   */
  @Test()
  public void testConstructor1()
  {
    new ASN1Exception(1, "Test");
  }



  /**
   * Tests the second constructor, which takes integer, string, and throwable
   * arguments.
   */
  @Test()
  public void testConstructor2()
  {
    new ASN1Exception(1, "Test", new Exception());
  }



  /**
   * Tests the <CODE>getMessageID</CODE> method.
   */
  @Test
  public void testGetMessageID()
  {
    for (int i=0; i < 100; i++)
    {
      ASN1Exception e = new ASN1Exception(i, "Test");
      assertEquals(e.getMessageID(), i);
    }
  }
}

