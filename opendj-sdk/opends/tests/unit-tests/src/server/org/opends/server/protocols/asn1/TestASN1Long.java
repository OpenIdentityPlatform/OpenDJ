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
 * org.opends.server.protocols.asn1.ASN1Long class.
 *
 *
 * @author   Neil A. Wilson
 */
public class TestASN1Long
       extends DirectoryServerTestCase
{
  // The set of encoded values for the test longs.
  private ArrayList<byte[]> testEncodedLongs;

  // The set of long values to use in test cases.
  private ArrayList<Long> testLongs;



  /**
   * Creates a new instance of this JUnit test case with the provided name.
   *
   * @param  name  The name to use for this JUnit test case.
   */
  public TestASN1Long(String name)
  {
    super(name);
  }



  /**
   * Performs any necessary initialization for this test case.
   */
  public void setUp()
  {
    testLongs        = new ArrayList<Long>();
    testEncodedLongs = new ArrayList<byte[]>();

    // Add all values that can be encoded using a single byte.
    for (int i=0; i < 128; i++)
    {
      testLongs.add(new Long(i));
      testEncodedLongs.add(new byte[] { (byte) (i & 0xFF) });
    }

    testLongs.add(new Long(0x80)); // The smallest 2-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0x80 });

    testLongs.add(new Long(0xFF)); // A boundary case for 2-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0xFF });

    testLongs.add(new Long(0x0100)); // A boundary case for 2-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x01, (byte) 0x00 });

    testLongs.add(new Long(0x7FFF)); // The largest 2-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x7F, (byte) 0xFF });

    testLongs.add(new Long(0x8000)); // The smallest 3-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0x80, (byte) 0x00 });

    testLongs.add(new Long(0xFFFF)); // A boundary case for 3-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF });

    testLongs.add(new Long(0x010000)); // A boundary case for 3-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00 });

    testLongs.add(new Long(0x7FFFFF)); // The largest 3-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x7F, (byte) 0xFF, (byte) 0xFF });

    testLongs.add(new Long(0x800000)); // The smallest 4-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0x80, (byte) 0x00,
                                      (byte) 0x00 });

    testLongs.add(new Long(0xFFFFFF)); // A boundary case for 4-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF });

    testLongs.add(new Long(0x01000000)); // A boundary case for 4-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00 });

    testLongs.add(new Long(0x7FFFFFFF)); // The largest 4-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x7F, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF });

    testLongs.add(0x80000000L); // The smallest 5-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0x80, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00 });

    testLongs.add(0xFFFFFFFFL); // A boundary case for 5-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF });

    testLongs.add(0x0100000000L); // A boundary case for 5-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00 });

    testLongs.add(0x07FFFFFFFFL); // The largest 5-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x07, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF });

    testLongs.add(0x8000000000L); // The smallest 6-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0x80, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00, (byte) 0x00 });

    testLongs.add(0xFFFFFFFFFFL); // A boundary case for 6-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });

    testLongs.add(0x010000000000L); // A boundary case for 6-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00, (byte) 0x00 });

    testLongs.add(0x07FFFFFFFFFFL); // The largest 6-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x07, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });

    testLongs.add(0x800000000000L); // The smallest 7-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0x80, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00 });

    testLongs.add(0xFFFFFFFFFFFFL); // A boundary case for 7-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF });

    testLongs.add(0x01000000000000L); // A boundary case for 7-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00 });

    testLongs.add(0x07FFFFFFFFFFFFL); // The largest 7-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x07, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF });

    testLongs.add(0x80000000000000L); // The smallest 8-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0x80, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00 });

    testLongs.add(0xFFFFFFFFFFFFFFL); // A boundary case for 8-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF });

    testLongs.add(0x0100000000000000L); // A boundary case for 8-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                      (byte) 0x00, (byte) 0x00 });

    testLongs.add(0x07FFFFFFFFFFFFFFL); // The largest 8-byte encoding.
    testEncodedLongs.add(new byte[] { (byte) 0x07, (byte) 0xFF, (byte) 0xFF,
                                      (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
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
   * Tests the <CODE>longValue</CODE> method.
   */
  public void testLongValue()
  {
    for (long l : testLongs)
    {
      ASN1Long element = new ASN1Long(l);

      try
      {
        assertEquals(l, element.longValue());
      }
      catch (AssertionFailedError afe)
      {
        printError("longValue failed for longValue=" + l);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a long argument.
   */
  public void testSetLongValue()
  {
    ASN1Long element = new ASN1Long(0);

    int numLongs = testLongs.size();
    for (int i=0; i < numLongs; i++)
    {
      long   longValue        = testLongs.get(i);
      byte[] encodedLongValue = testEncodedLongs.get(i);

      element.setValue(longValue);
      try
      {
        assertEquals(longValue, element.longValue());
      }
      catch (AssertionFailedError afe)
      {
        printError("setValue(" + longValue + ") failed in longValue");
        throw afe;
      }

      try
      {
        assertTrue(Arrays.equals(encodedLongValue, element.value()));
      }
      catch (AssertionFailedError afe)
      {
        printError("setValue(" + longValue + ") failed in value");
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method that takes a byte array argument.
   */
  public void testSetByteValue()
  {
    ASN1Long element = new ASN1Long(0);

    int numLongs = testLongs.size();
    for (int i=0; i < numLongs; i++)
    {
      long   longValue        = testLongs.get(i);
      byte[] encodedLongValue = testEncodedLongs.get(i);

      try
      {
        element.setValue(encodedLongValue);
      }
      catch (ASN1Exception ae)
      {
        String message = "setValue(byte[]) threw an exception for longValue=" +
                         longValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }

      try
      {
        assertEquals(longValue, element.longValue());
      }
      catch (AssertionFailedError afe)
      {
        printError("setValue(byte[]) failed for longValue=" + longValue);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes an ASN.1 element
   * argument.
   */
  public void testDecodeElementAsLong()
  {
    int numLongs = testLongs.size();
    for (int i=0; i < numLongs; i++)
    {
      long   longValue        = testLongs.get(i);
      byte[] encodedLongValue = testEncodedLongs.get(i);

      ASN1Element element = new ASN1Element((byte) 0x00, encodedLongValue);

      try
      {
        assertEquals(longValue, ASN1Long.decodeAsLong(element).longValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsLong(element(" + longValue +
                         ")) threw an exception for type=00";
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsLong(element(" + longValue +
                   ")) failed for type=00");
        throw afe;
      }


      element = new ASN1Element((byte) 0x02, encodedLongValue);

      try
      {
        assertEquals(longValue, ASN1Long.decodeAsLong(element).longValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsLong(element(" + longValue +
                         ")) threw an exception for type=02";
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsLong(element(" + longValue +
                   ")) failed for type=02");
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method that takes a byte array
   * argument.
   */
  public void testDecodeBytesAsLong()
  {
    int numLongs = testLongs.size();
    for (int i=0; i < numLongs; i++)
    {
      long   longValue        = testLongs.get(i);
      byte[] encodedLongValue = testEncodedLongs.get(i);
      byte[] encodedLength    = ASN1Element.encodeLength(
                                     encodedLongValue.length);
      byte[] encodedElement   = new byte[1 + encodedLength.length +
                                         encodedLongValue.length];

      encodedElement[0] = (byte) 0x00;
      System.arraycopy(encodedLength, 0, encodedElement, 1,
                       encodedLength.length);
      System.arraycopy(encodedLongValue, 0, encodedElement,
                       1+encodedLength.length, encodedLongValue.length);

      try
      {
        assertEquals(longValue,
                     ASN1Long.decodeAsLong(encodedElement).longValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsLong(byte[]) threw an exception for " +
                         "type=00, longValue=" + longValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsLong(byte[]) failed for type=00, " +
                   "longValue=" + longValue);
        throw afe;
      }


      encodedElement[0] = (byte) 0x02;
      try
      {
        assertEquals(longValue,
                     ASN1Long.decodeAsLong(encodedElement).longValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsLong(byte[]) threw an exception for " +
                         "type=02, longValue=" + longValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsLong(byte[]) failed for type=02, " +
                   "longValue=" + longValue);
        throw afe;
      }
    }
  }
}

