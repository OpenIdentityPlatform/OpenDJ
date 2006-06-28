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
package org.opends.server.protocols.asn1;



import junit.framework.*;
import org.opends.server.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a set of JUnit tests for the
 * org.opends.server.protocols.asn1.ASN1Null class.
 *
 *
 * @author   Neil A. Wilson
 */
public class TestASN1Null
       extends DirectoryServerTestCase
{
  /**
   * Creates a new instance of this JUnit test case with the provided name.
   *
   * @param  name  The name to use for this JUnit test case.
   */
  public TestASN1Null(String name)
  {
    super(name);
  }



  /**
   * Performs any necessary initialization for this test case.
   */
  public void setUp()
  {
    // No implementation required.
  }



  /**
   * Performs any necessary cleanup for this test case.
   */
  public void tearDown()
  {
    // No implementation required.
  }



  /**
   * Tests the <CODE>setValue</CODE> method.
   */
  public void testSetValue()
  {
    ASN1Null element = new ASN1Null();

    // Test with a null array.
    try
    {
      element.setValue(null);
    }
    catch (ASN1Exception ae)
    {
      String message = "setValue(null) threw an exception";
      printError(message);
      printException(ae);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ae));
    }


    // Test with an empty array.
    try
    {
      element.setValue(new byte[0]);
    }
    catch (ASN1Exception ae)
    {
      String message = "setValue(bye[0]) threw an exception";
      printError(message);
      printException(ae);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ae));
    }
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes an ASN.1 element
   * argument.
   */
  public void testDecodeElementAsNull()
  {
    // Test with a type of 0x00.
    ASN1Element element = new ASN1Element((byte) 0x00);

    try
    {
      ASN1Null.decodeAsNull(element);
    }
    catch (ASN1Exception ae)
    {
      String message = "decodeAsNull(element) threw an exception for type=00";
      printError(message);
      printException(ae);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ae));
    }


    // Test with a type of 0x05.
    element = new ASN1Element((byte) 0x05);
    try
    {
      ASN1Null.decodeAsNull(element);
    }
    catch (ASN1Exception ae)
    {
      String message = "decodeAsNull(element) threw an exception for type=05";
      printError(message);
      printException(ae);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ae));
    }
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array
   * argument.
   */
  public void testDecodeBytesAsNull()
  {
    byte[] encodedElement = new byte[] { (byte) 0x00, (byte) 0x00 };

    // Test with all possible type representations.
    for (int i=0; i < 256; i++)
    {
      byte type = (byte) (i & 0xFF);
      encodedElement[0] = type;

      try
      {
        ASN1Null.decodeAsNull(encodedElement);
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsNull(byte[]) threw an exception for type=" +
                         byteToHex(type);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
    }
  }
}

