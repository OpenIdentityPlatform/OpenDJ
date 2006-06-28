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
 * org.opends.server.protocols.asn1.ASN1Boolean class.
 *
 *
 * @author   Neil A. Wilson
 */
public class TestASN1Boolean
       extends DirectoryServerTestCase
{
  // The pre-encoded value for "false" elements.
  private byte[] falseValue;

  // The pre-encoded value for "true" elements.
  private byte[] trueValue;



  /**
   * Creates a new instance of this JUnit test case with the provided name.
   *
   * @param  name  The name to use for this JUnit test case.
   */
  public TestASN1Boolean(String name)
  {
    super(name);
  }



  /**
   * Performs any necessary initialization for this test case.
   */
  public void setUp()
  {
    falseValue = new byte[] { (byte) 0x00 };
    trueValue  = new byte[] { (byte) 0xFF };
  }



  /**
   * Performs any necessary cleanup for this test case.
   */
  public void tearDown()
  {
    // No implementation required.
  }



  /**
   * Tests the <CODE>booleanValue</CODE> method.
   */
  public void testBooleanValue()
  {
    try
    {
      assertTrue(new ASN1Boolean(true).booleanValue());
    }
    catch (AssertionFailedError afe)
    {
      printError("booleanValue failed for value=true");
      throw afe;
    }

    try
    {
      assertFalse(new ASN1Boolean(false).booleanValue());
    }
    catch (AssertionFailedError afe)
    {
      printError("booleanValue failed for value=false");
      throw afe;
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a boolean argument.
   */
  public void testSetBooleanValue()
  {
    ASN1Boolean element = new ASN1Boolean(true); // Create a new true element.

    element.setValue(true); // Test setting the same value.
    try
    {
      assertTrue(element.booleanValue());
    }
    catch (AssertionFailedError afe)
    {
      printError("boolean(true).setValue(true) failed");
      throw afe;
    }

    element.setValue(false); // Test setting the opposite value.
    try
    {
      assertFalse(element.booleanValue());
    }
    catch (AssertionFailedError afe)
    {
      printError("boolean(true).setValue(false) failed");
      throw afe;
    }


    element = new ASN1Boolean(false);  // Create a new false element.
    element.setValue(false); // Test setting the same value.
    try
    {
      assertFalse(element.booleanValue());
    }
    catch (AssertionFailedError afe)
    {
      printError("boolean(false).setValue(false) failed");
      throw afe;
    }

    element.setValue(true); // Test setting the opposite value.
    try
    {
      assertTrue(element.booleanValue());
    }
    catch (AssertionFailedError afe)
    {
      printError("boolean(false).setValue(true) failed");
      throw afe;
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument.
   */
  public void testSetByteValue()
  {
    ASN1Boolean element = new ASN1Boolean(true);

    // Test setting the value to the encoded "false" representation.
    try
    {
      element.setValue(new byte[] { 0x00 });
      assertFalse(element.booleanValue());
    }
    catch (ASN1Exception ae)
    {
      String message = "boolean.setValue(00) threw an exception";
      printError(message);
      printException(ae);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ae));
    }
    catch (AssertionFailedError afe)
    {
      printError("boolean.setValue(00) failed");
      throw afe;
    }


    // Test setting the value to all other possible byte representations, which
    // should evaluate to "true".
    for (int i=1; i < 256; i++)
    {
      byte byteValue = (byte) (i & 0xFF);

      try
      {
        element.setValue(new byte[] { byteValue });
        assertTrue(element.booleanValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "boolean.setValue(" + byteToHex(byteValue) +
                         ") threw an exception";
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("boolean.setValue(" + byteToHex(byteValue) + ") failed");
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes an ASN.1 element
   * argument.
   */
  public void testDecodeElementAsBoolean()
  {
    // Test a boolean element of "true".
    ASN1Boolean trueBoolean = new ASN1Boolean(true);
    try
    {
      assertTrue(ASN1Boolean.decodeAsBoolean(trueBoolean).booleanValue());
    }
    catch (ASN1Exception ae)
    {
      String message = "decodeAsBoolean(boolean(true)) threw an exception";
      printError(message);
      printException(ae);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ae));
    }
    catch (AssertionFailedError afe)
    {
      printError("decodeAsBoolean(boolean(true)) failed");
      throw afe;
    }


    // Test a boolean element of "false".
    ASN1Boolean falseBoolean = new ASN1Boolean(false);
    try
    {
      assertFalse(ASN1Boolean.decodeAsBoolean(falseBoolean).booleanValue());
    }
    catch (ASN1Exception ae)
    {
      String message = "decodeAsBoolean(boolean(false)) threw an exception";
      printError(message);
      printException(ae);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ae));
    }
    catch (AssertionFailedError afe)
    {
      printError("decodeAsBoolean(boolean(false)) failed");
      throw afe;
    }


    // Test the valid generic elements that may be used.
    for (int i=0; i < 256; i++)
    {
      byte    byteValue    = (byte) (i & 0xFF);
      boolean compareValue = (i != 0);

      ASN1Element element = new ASN1Element((byte) 0x00,
                                            new byte[] { byteValue });

      try
      {
        assertEquals(compareValue,
                     ASN1Boolean.decodeAsBoolean(element).booleanValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsBoolean(element) threw an exception for " +
                         "byteValue=" + byteToHex(byteValue);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsBoolean(element) failed for byteValue=" +
                   byteToHex(byteValue));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array.
   * argument.
   */
  public void testDecodeBytesAsBoolean()
  {
    byte[] encodedElement = new byte[] { 0x00, 0x01, 0x00 };

    // Test all possible byte values.
    for (int i=0; i < 256; i++)
    {
      boolean compareValue = (i != 0);

      // Set the value.
      encodedElement[2] = (byte) (i & 0xFF);

      // Test with the standard Boolean type.
      encodedElement[0] = (byte) 0x01;
      try
      {
        assertEquals(compareValue,
             ASN1Boolean.decodeAsBoolean(encodedElement).booleanValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsBoolean(byte[]) threw an exception for " +
                         "byteValue=" + byteToHex(encodedElement[2]);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsBoolean(byte[] failed for byteValue=" +
                   byteToHex(encodedElement[2]));
        throw afe;
      }
    }
  }
}

