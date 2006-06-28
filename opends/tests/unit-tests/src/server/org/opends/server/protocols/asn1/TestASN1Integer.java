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



import java.util.*;
import junit.framework.*;
import org.opends.server.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a set of JUnit tests for the
 * org.opends.server.protocols.asn1.ASN1Integer class.
 *
 *
 * @author   Neil A. Wilson
 */
public class TestASN1Integer
       extends DirectoryServerTestCase
{
  // The set of encoded values for the test integers.
  private ArrayList<byte[]> testEncodedIntegers;

  // The set of integer values to use in test cases.
  private ArrayList<Integer> testIntegers;



  /**
   * Creates a new instance of this JUnit test case with the provided name.
   *
   * @param  name  The name to use for this JUnit test case.
   */
  public TestASN1Integer(String name)
  {
    super(name);
  }



  /**
   * Performs any necessary initialization for this test case.
   */
  public void setUp()
  {
    testIntegers        = new ArrayList<Integer>();
    testEncodedIntegers = new ArrayList<byte[]>();

    // Add all values that can be encoded using a single byte.
    for (int i=0; i < 128; i++)
    {
      testIntegers.add(i);
      testEncodedIntegers.add(new byte[] { (byte) (i & 0xFF) });
    }

    testIntegers.add(0x80); // The smallest 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0x80 });

    testIntegers.add(0xFF); // A boundary case for 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0xFF });

    testIntegers.add(0x0100); // A boundary case for 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x01, (byte) 0x00 });

    testIntegers.add(0x7FFF); // The largest 2-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F, (byte) 0xFF });

    testIntegers.add(0x8000); // The smallest 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0x80,
                                         (byte) 0x00 });

    testIntegers.add(0xFFFF); // A boundary case for 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0xFF,
                                         (byte) 0xFF });

    testIntegers.add(0x010000); // A boundary case for 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x01, (byte) 0x00,
                                         (byte) 0x00 });

    testIntegers.add(0x7FFFFF); // The largest 3-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F, (byte) 0xFF,
                                         (byte) 0xFF });

    testIntegers.add(0x800000); // The smallest 4-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0x80,
                                         (byte) 0x00, (byte) 0x00 });

    testIntegers.add(0xFFFFFF); // A boundary case for 4-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00, (byte) 0xFF,
                                         (byte) 0xFF, (byte) 0xFF });

    testIntegers.add(0x01000000); // A boundary case for 4-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x01, (byte) 0x00,
                                         (byte) 0x00, (byte) 0x00 });

    testIntegers.add(0x7FFFFFFF); // The largest value we will allow.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F, (byte) 0xFF,
                                         (byte) 0xFF, (byte) 0xFF });
  }



  /**
   * Performs any necessary cleanup for this test case.
   */
  public void tearDown()
  {
    // No implementation required.
  }



  /**
   * Tests the <CODE>intValue</CODE> method.
   */
  public void testIntValue()
  {
    for (int i : testIntegers)
    {
      ASN1Integer element = new ASN1Integer(i);

      try
      {
        assertEquals(i, element.intValue());
      }
      catch (AssertionFailedError afe)
      {
        printError("intValue failed for intValue=" + i);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes an int argument.
   */
  public void testSetIntValue()
  {
    ASN1Integer element = new ASN1Integer(0);

    int numIntegers = testIntegers.size();
    for (int i=0; i < numIntegers; i++)
    {
      int    intValue        = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      element.setValue(intValue);
      try
      {
        assertEquals(intValue, element.intValue());
      }
      catch (AssertionFailedError afe)
      {
        printError("setValue(" + intValue + ") failed in intValue");
        throw afe;
      }

      try
      {
        assertTrue(Arrays.equals(encodedIntValue, element.value()));
      }
      catch (AssertionFailedError afe)
      {
        printError("setValue(" + intValue + ") failed in value");
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument.
   */
  public void testSetByteValue()
  {
    ASN1Integer element = new ASN1Integer(0);

    int numIntegers = testIntegers.size();
    for (int i=0; i < numIntegers; i++)
    {
      int    intValue        = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      try
      {
        element.setValue(encodedIntValue);
      }
      catch (ASN1Exception ae)
      {
        String message = "setValue(byte[]) threw an exception for intValue=" +
                         intValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }

      try
      {
        assertEquals(intValue, element.intValue());
      }
      catch (AssertionFailedError afe)
      {
        printError("setValue(byte[]) failed for intValue=" + intValue);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes an ASN.1 element
   * argument.
   */
  public void testDecodeElementAsInteger()
  {
    int numIntegers = testIntegers.size();
    for (int i=0; i < numIntegers; i++)
    {
      int    intValue        = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      ASN1Element element = new ASN1Element((byte) 0x00, encodedIntValue);

      try
      {
        assertEquals(intValue, ASN1Integer.decodeAsInteger(element).intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsInteger(element(" + intValue +
                         ")) threw an exception for type=00";
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsInteger(element(" + intValue +
                   ")) failed for type=00");
        throw afe;
      }


      element = new ASN1Element((byte) 0x02, encodedIntValue);

      try
      {
        assertEquals(intValue, ASN1Integer.decodeAsInteger(element).intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsInteger(element(" + intValue +
                         ")) threw an exception for type=02";
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsInteger(element(" + intValue +
                   ")) failed for type=02");
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array
   * argument.
   */
  public void testDecodeBytesAsInteger()
  {
    int numIntegers = testIntegers.size();
    for (int i=0; i < numIntegers; i++)
    {
      int    intValue        = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);
      byte[] encodedLength   = ASN1Element.encodeLength(encodedIntValue.length);
      byte[] encodedElement  = new byte[1 + encodedLength.length +
                                        encodedIntValue.length];

      encodedElement[0] = (byte) 0x00;
      System.arraycopy(encodedLength, 0, encodedElement, 1,
                       encodedLength.length);
      System.arraycopy(encodedIntValue, 0, encodedElement,
                       1+encodedLength.length, encodedIntValue.length);

      try
      {
        assertEquals(intValue,
                     ASN1Integer.decodeAsInteger(encodedElement).intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsInteger(byte[]) threw an exception for " +
                         "type=00, intValue=" + intValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsInteger(byte[]) failed for type=00, " +
                   "intValue=" + intValue);
        throw afe;
      }


      encodedElement[0] = (byte) 0x02;
      try
      {
        assertEquals(intValue,
                     ASN1Integer.decodeAsInteger(encodedElement).intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsInteger(byte[]) threw an exception for " +
                         "type=02, intValue=" + intValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsInteger(byte[]) failed for type=02, " +
                   "intValue=" + intValue);
        throw afe;
      }
    }
  }
}

