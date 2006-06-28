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
 * org.opends.server.protocols.asn1.ASN1Element class.
 *
 *
 * @author   Neil A. Wilson
 */
public class TestASN1Element
       extends DirectoryServerTestCase
{
  // The sets of pre-encoded ASN.1 elements that will be included in the test
  // case.
  private ArrayList<ArrayList<ASN1Element>> testElementSets;

  // The set of pre-encoded element sets that will be used in the test cases.
  private ArrayList<byte[]> testEncodedElementSets;

  // The set of pre-encoded integer values that will be included in the test
  // cases.
  private ArrayList<byte[]> testEncodedIntegers;

  // The set of pre-encoded lengths that will be included in the test cases.
  private ArrayList<byte[]> testEncodedLengths;

  // The set of BER types that will be included in the test cases.
  private ArrayList<Byte> testTypes;

  // The set of element values that will be included in the test cases.
  private ArrayList<byte[]> testValues;

  // The set of integer values that will be included in the test cases.
  private ArrayList<Integer> testIntegers;

  // The set of lengths that will be included in the test cases.
  private ArrayList<Integer> testLengths;



  /**
   * Creates a new instance of this JUnit test case with the provided name.
   *
   * @param  name  The name to use for this JUnit test case.
   */
  public TestASN1Element(String name)
  {
    super(name);
  }



  /**
   * Performs any necessary initialization for this test case.
   */
  public void setUp()
  {
    // Initialize the set of types.  It will encapsulate the entire range of
    // possible byte values.
    testTypes = new ArrayList<Byte>();
    for (int i=0; i < 0xFF; i++)
    {
      testTypes.add((byte) (i & 0xFF));
    }


    // Initialize the set of values.  Don't make these too big since they
    // consume memory.
    testValues = new ArrayList<byte[]>();
    testValues.add(null);                // The null value.
    testValues.add(new byte[0x00]);      // The zero-byte value.
    testValues.add(new byte[0x01]);      // The single-byte value.
    testValues.add(new byte[0x7F]);      // The largest 1-byte length encoding.
    testValues.add(new byte[0x80]);
    testValues.add(new byte[0xFF]);      // The largest 2-byte length encoding.
    testValues.add(new byte[0x0100]);
    testValues.add(new byte[0xFFFF]);    // The largest 3-byte length encoding.
    testValues.add(new byte[0x010000]);


    // Initialize the set of element lengths and their pre-encoded
    // representations.  Don't make these too big since we will create arrays
    // with these lengths during testing.
    testLengths        = new ArrayList<Integer>();
    testEncodedLengths = new ArrayList<byte[]>();

    testLengths.add(0x00); // The zero-byte length.
    testEncodedLengths.add(new byte[] { (byte) 0x00 });

    testLengths.add(0x01); // A common 1-byte length.
    testEncodedLengths.add(new byte[] { (byte) 0x01 });

    testLengths.add(0x7F); // The largest 1-byte length encoding.
    testEncodedLengths.add(new byte[] { (byte) 0x7F });

    testLengths.add(0x80); // The smallest length that must use 2 bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x81, (byte) 0x80 });


    testLengths.add(0xFF); // The largest length that may use 2 bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x81, (byte) 0xFF });

    testLengths.add(0x0100); // The smallest length that must use 3 bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x82, (byte) 0x01,
                                        (byte) 0x00 });

    testLengths.add(0xFFFF); // The largest length that may use 3 bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x82, (byte) 0xFF,
                                        (byte) 0xFF });

    testLengths.add(0x010000); // The smallest length that must use 4 bytes.
    testEncodedLengths.add(new byte[] { (byte) 0x83, (byte) 0x01,
                                        (byte) 0x00, (byte) 0x00 });


    // Initialize the set of integer values and their pre-encoded
    // representations.  These can get big since they will not be used to create
    // arrays.  Also, there is no need to test negative values since LDAP
    // doesn't make use of them.
    testIntegers        = new ArrayList<Integer>();
    testEncodedIntegers = new ArrayList<byte[]>();

    testIntegers.add(0x00); // A common 1-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x00 });

    testIntegers.add(0x7F); // The largest 1-byte encoding.
    testEncodedIntegers.add(new byte[] { (byte) 0x7F });

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


    // Initialize the sets of ASN.1 elements that will be used in testing the
    // group encode/decode operations.
    testElementSets        = new ArrayList<ArrayList<ASN1Element>>();
    testEncodedElementSets = new ArrayList<byte[]>();

    testElementSets.add(null); // The null set.
    testEncodedElementSets.add(new byte[0]);

    testElementSets.add(new ArrayList<ASN1Element>(0)); // The empty set.
    testEncodedElementSets.add(new byte[0]);

    // Sets containing from 1 to 10 elements.
    for (int i=1; i <= 10; i++)
    {
      ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(i);

      for (int j=0; j < i; j++)
      {
        elements.add(new ASN1Element((byte) 0x00));
      }
      testElementSets.add(elements);
      testEncodedElementSets.add(new byte[i*2]);
    }
  }



  /**
   * Performs any necessary cleanup for this test case.
   */
  public void tearDown()
  {
    // No implementation required.
  }



  /**
   * Tests the <CODE>getType</CODE> method.
   */
  public void testGetType()
  {
    for (byte type : testTypes)
    {
      ASN1Element element = new ASN1Element(type);

      try
      {
        assertEquals(type, element.getType());
      }
      catch (AssertionFailedError afe)
      {
        printError("getType failed for type=" + byteToHex(type));
        throw afe;
      }

      for (byte[] value : testValues)
      {
        element = new ASN1Element(type, value);

        try
        {
          assertEquals(type, element.getType());
        }
        catch (AssertionFailedError afe)
        {
          printError("getType failed for type=" + byteToHex(type) + ", value=" +
                     bytesToHex(value));
          throw afe;
        }
      }
    }
  }



  /**
   * Tests the <CODE>setType</CODE> method.
   */
  public void testSetType()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes)
    {
      element.setType(type);

      try
      {
        assertEquals(type, element.getType());
      }
      catch (AssertionFailedError afe)
      {
        printError("setType failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>isUniversal</CODE> method.
   */
  public void testIsUniversal()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes)
    {
      element.setType(type);
      boolean isUniversal = (((byte) (type & 0xC0)) == ((byte) 0x00));

      try
      {
        assertEquals(isUniversal, element.isUniversal());
      }
      catch (AssertionFailedError afe)
      {
        printError("isUniversal failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>isApplicationSpecific</CODE> method.
   */
  public void testIsApplicationSpecific()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes)
    {
      element.setType(type);
      boolean isApplicationSpecific = (((byte) (type & 0xC0)) == ((byte) 0x40));

      try
      {
        assertEquals(isApplicationSpecific, element.isApplicationSpecific());
      }
      catch (AssertionFailedError afe)
      {
        printError("isApplicationSpecific failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>isContextSpecific</CODE> method.
   */
  public void testIsContextSpecific()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes)
    {
      element.setType(type);
      boolean isContextSpecific = (((byte) (type & 0xC0)) == ((byte) 0x80));

      try
      {
        assertEquals(isContextSpecific, element.isContextSpecific());
      }
      catch (AssertionFailedError afe)
      {
        printError("isContextSpecific failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>isPrivate</CODE> method.
   */
  public void testIsPrivate()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes)
    {
      element.setType(type);
      boolean isPrivate = (((byte) (type & 0xC0)) == ((byte) 0xC0));

      try
      {
        assertEquals(isPrivate, element.isPrivate());
      }
      catch (AssertionFailedError afe)
      {
        printError("isPrivate failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>isPrimitive</CODE> method.
   */
  public void testIsPrimitive()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes)
    {
      element.setType(type);
      boolean isPrimitive = (((byte) (type & 0x20)) == ((byte) 0x00));

      try
      {
        assertEquals(isPrimitive, element.isPrimitive());
      }
      catch (AssertionFailedError afe)
      {
        printError("isPrimitive failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>isConstructed</CODE> method.
   */
  public void testIsConstructed()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);
    for (byte type : testTypes)
    {
      element.setType(type);
      boolean isConstructed = (((byte) (type & 0x20)) == ((byte) 0x20));

      try
      {
        assertEquals(isConstructed, element.isConstructed());
      }
      catch (AssertionFailedError afe)
      {
        printError("isConstructed failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>getValue</CODE> method.
   */
  public void testGetValue()
  {
    for (byte type : testTypes)
    {
      ASN1Element element = new ASN1Element(type);

      try
      {
        assertTrue(Arrays.equals(new byte[0], element.value()));
      }
      catch (AssertionFailedError afe)
      {
        printError("getValue failed for type=" + byteToHex(type));
        throw afe;
      }

      for (byte[] value : testValues)
      {
        element = new ASN1Element(type, value);

        try
        {
          if (value == null)
          {
            assertTrue(Arrays.equals(new byte[0], element.value()));
          }
          else
          {
            assertTrue(Arrays.equals(value, element.value()));
          }
        }
        catch (AssertionFailedError afe)
        {
          printError("getValue failed for type=" + byteToHex(type) +
                     ", value=" + bytesToHex(value));
          throw afe;
        }
      }
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method.
   */
  public void testSetValue()
  {
    ASN1Element element = new ASN1Element((byte) 0x00);

    for (byte[] value : testValues)
    {
      try
      {
        element.setValue(value);
      }
      catch (Exception e)
      {
        String message = "setvalue threw an exception for value=" +
                         bytesToHex(value);
        printError(message);
        printException(e);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(e));
      }

      try
      {
        if (value == null)
        {
          assertTrue(Arrays.equals(new byte[0], element.value()));
        }
        else
        {
          assertTrue(Arrays.equals(value, element.value()));
        }
      }
      catch (AssertionFailedError afe)
      {
        printError("setValue failed for value=" + bytesToHex(value));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>encodeLength</CODE> method.
   */
  public void testEncodeLength()
  {
    int numLengths = testLengths.size();
    for (int i=0; i < numLengths; i++)
    {
      int    length        = testLengths.get(i);
      byte[] encodedLength = testEncodedLengths.get(i);

      try
      {
        assertTrue(Arrays.equals(encodedLength,
                                      ASN1Element.encodeLength(length)));
      }
      catch (AssertionFailedError afe)
      {
        printError("encodeLength failed for length=" + length);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>encode</CODE> and <CODE>decode</CODE> methods.
   */
  public void testEncodeAndDecode()
  {
    for (byte type : testTypes)
    {
      for (byte[] value : testValues)
      {
        int    length;
        byte[] encodedLength;
        if (value == null)
        {
          length        = 0;
          encodedLength = new byte[] { (byte) 0x00 };
        }
        else
        {
          length        = value.length;
          encodedLength = ASN1Element.encodeLength(length);
        }

        byte[] encodedElement = new byte[1 + length + encodedLength.length];
        encodedElement[0] = type;
        System.arraycopy(encodedLength, 0, encodedElement, 1,
                         encodedLength.length);
        if (value != null)
        {
          System.arraycopy(value, 0, encodedElement, 1+encodedLength.length,
                           length);
        }

        ASN1Element element = new ASN1Element(type, value);

        try
        {
          assertTrue(Arrays.equals(encodedElement, element.encode()));
        }
        catch (AssertionFailedError afe)
        {
          printError("encode failed for type=" + byteToHex(type) +
                     ",  valueLength=" + length);
          throw afe;
        }

        try
        {
          assertTrue(element.equals(ASN1Element.decode(encodedElement)));
        }
        catch (ASN1Exception ae)
        {
          String message = "decode threw an exception for type=" +
                           byteToHex(type) + ", valueLength=" + length;
          printError(message);
          printException(ae);
          throw new AssertionFailedError(message + " -- " +
                                         stackTraceToSingleLineString(ae));
        }
        catch (AssertionFailedError afe)
        {
          printError("decode failed for type=" + byteToHex(type) +
                     ", valueLength=" + length);
          throw afe;
        }
      }



      int numLengths = testLengths.size();
      for (int i=0; i < numLengths; i++)
      {
        int    length        = testLengths.get(i);
        byte[] encodedLength = testEncodedLengths.get(i);
        byte[] value         = new byte[length];

        byte[] encodedElement = new byte[1 + length + encodedLength.length];
        encodedElement[0] = type;
        System.arraycopy(encodedLength, 0, encodedElement, 1,
                         encodedLength.length);

        ASN1Element element = new ASN1Element(type, value);

        try
        {
          assertTrue(Arrays.equals(encodedElement, element.encode()));
        }
        catch (AssertionFailedError afe)
        {
          printError("encode failed for type=" + byteToHex(type) + ", length=" +
                     length);
          throw afe;
        }

        try
        {
          assertTrue(element.equals(ASN1Element.decode(encodedElement)));
        }
        catch (ASN1Exception ae)
        {
          String message = "decode threw an exception for type=" +
                           byteToHex(type) + ", length=" + length;
          printError(message);
          printException(ae);
          throw new AssertionFailedError(message + " -- " +
                                         stackTraceToSingleLineString(ae));
        }
        catch (AssertionFailedError afe)
        {
          printError("decode failed for type=" + byteToHex(type) + ", length=" +
                     length);
          throw afe;
        }
      }
    }
  }



  /**
   * Tests the <CODE>encodeValue</CODE> method with a single boolean argument.
   */
  public void testEncodeBooleanValue()
  {
    byte[] encodedFalse = new byte[] { (byte) 0x00 };
    byte[] encodedTrue  = new byte[] { (byte) 0xFF };

    try
    {
      assertTrue(Arrays.equals(encodedFalse,
                                    ASN1Element.encodeValue(false)));
    }
    catch (AssertionFailedError afe)
    {
      printError("encodeValue failed for a boolean value of false");
      throw afe;
    }

    try
    {
      assertTrue(Arrays.equals(encodedTrue,
                                    ASN1Element.encodeValue(true)));
    }
    catch (AssertionFailedError afe)
    {
      printError("encodeValue failed for a boolean value of true");
      throw afe;
    }
  }



  /**
   * Tests the <CODE>encodeValue</CODE> method with a single int argument.
   */
  public void testEncodeIntValue()
  {
    int numIntValues = testIntegers.size();
    for (int i=0; i < numIntValues; i++)
    {
      int    intValue   = testIntegers.get(i);
      byte[] encodedInt = testEncodedIntegers.get(i);

      try
      {
        assertTrue(Arrays.equals(encodedInt,
                                      ASN1Element.encodeValue(intValue)));
      }
      catch (AssertionFailedError afe)
      {
        printError("encodeValue failed for an int value of " + intValue);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>encodeValue</CODE> method with a set of ASN.1 elements.
   */
  public void testEncodeAndDecodeElements()
  {
    int numElementSets = testElementSets.size();
    for (int i=0; i < numElementSets; i++)
    {
      ArrayList<ASN1Element> elementSet        = testElementSets.get(i);
      byte[]                 encodedElementSet = testEncodedElementSets.get(i);

      try
      {
        assertTrue(Arrays.equals(encodedElementSet,
                                      ASN1Element.encodeValue(elementSet)));
      }
      catch (AssertionFailedError afe)
      {
        if (elementSet == null)
        {
          printError("encodeValue failed for a null set of elements");
        }
        else
        {
          printError("encodeValue failed for a set of " + elementSet.size() +
                     " elements");
        }

        throw afe;
      }


      ArrayList<ASN1Element> decodedElementSet;
      try
      {
        decodedElementSet = ASN1Element.decodeElements(encodedElementSet);
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeElements threw an exception for value=" +
                         bytesToHex(encodedElementSet);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }


      ArrayList<ASN1Element> compareSet;
      if (elementSet == null)
      {
        compareSet = new ArrayList<ASN1Element>(0);
      }
      else
      {
        compareSet = elementSet;
      }

      try
      {
        assertTrue(listsAreEqual(compareSet, decodedElementSet));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeElements failed for value=" +
                   bytesToHex(encodedElementSet));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method.
   */
  public void testDecodeAsBoolean()
  {
    for (int i=0; i < 256; i++)
    {
      byte[] valueByte = new byte[] { (byte) i };
      ASN1Element element = new ASN1Element((byte) 0x00, valueByte);
      boolean booleanValue = (i != 0);

      try
      {
        assertEquals(booleanValue, element.decodeAsBoolean().booleanValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsBoolean threw an exception for type=00, " +
                         "value=" + bytesToHex(valueByte);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsBoolean failed for type=00, value=" +
                   bytesToHex(valueByte));
        throw afe;
      }


      element = new ASN1Element((byte) 0x01, valueByte);

      try
      {
        assertEquals(booleanValue, element.decodeAsBoolean().booleanValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsBoolean threw an exception for type=01, " +
                         "value=" + bytesToHex(valueByte);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsBoolean failed for type=01, value=" +
                   bytesToHex(valueByte));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsEnumerated</CODE> method.
   */
  public void testDecodeAsEnumerated()
  {
    int numIntValues = testIntegers.size();
    for (int i=0; i < numIntValues; i++)
    {
      int    intValue   = testIntegers.get(i);
      byte[] encodedInt = testEncodedIntegers.get(i);

      ASN1Element element = new ASN1Element((byte) 0x00, encodedInt);

      try
      {
        assertEquals(intValue, element.decodeAsEnumerated().intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsEnumerated threw an exception for type=00, " +
                         "intValue=" + intValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsEnumerated failed for type=00, intValue=" +
                   intValue);
        throw afe;
      }


      element = new ASN1Element((byte) 0x0A, encodedInt);

      try
      {
        assertEquals(intValue, element.decodeAsEnumerated().intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsEnumerated threw an exception for type=0A, " +
                         "intValue=" + intValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsEnumerated failed for type=0A, intValue=" +
                   intValue);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method.
   */
  public void testDecodeAsInteger()
  {
    int numIntValues = testIntegers.size();
    for (int i=0; i < numIntValues; i++)
    {
      int    intValue   = testIntegers.get(i);
      byte[] encodedInt = testEncodedIntegers.get(i);

      ASN1Element element = new ASN1Element((byte) 0x00, encodedInt);

      try
      {
        assertEquals(intValue, element.decodeAsInteger().intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsInteger threw an exception for type=00, " +
                         "intValue=" + intValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsInteger failed for type=00, intValue=" + intValue);
        throw afe;
      }


      element = new ASN1Element((byte) 0x02, encodedInt);

      try
      {
        assertEquals(intValue, element.decodeAsInteger().intValue());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsInteger threw an exception for type=02, " +
                         "intValue=" + intValue;
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsInteger failed for type=02, intValue=" + intValue);
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method.
   */
  public void testDecodeAsNull()
  {
    for (byte type : testTypes)
    {
      ASN1Element element     = new ASN1Element(type);
      ASN1Null    nullElement = new ASN1Null(type);

      try
      {
        assertEquals(nullElement, element.decodeAsNull());
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsNull threw an exception for type=" +
                         byteToHex(type);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsNull failed for type=" + byteToHex(type));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method.
   */
  public void testDecodeAsOctetString()
  {
    for (byte[] value : testValues)
    {
      ASN1Element element = new ASN1Element((byte) 0x00, value);

      byte[] compareValue;
      if (value == null)
      {
        compareValue = new byte[0];
      }
      else
      {
        compareValue = value;
      }

      try
      {
        assertTrue(Arrays.equals(compareValue,
                                      element.decodeAsOctetString().value()));
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsOctetString threw an exception for " +
                         "type=00, value=" + bytesToHex(value);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsOctetString failed for type=00, value=" +
                   bytesToHex(value));
        throw afe;
      }


      element = new ASN1Element((byte) 0x04, value);

      try
      {
        assertTrue(Arrays.equals(compareValue,
                                      element.decodeAsOctetString().value()));
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsOctetString threw an exception for " +
                         "type=04, value=" + bytesToHex(value);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsOctetString failed for type=04, value=" +
                   bytesToHex(value));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method.
   */
  public void testDecodeAsSequence()
  {
    int numElementSets = testElementSets.size();
    for (int i=0; i < numElementSets; i++)
    {
      ArrayList<ASN1Element> elementSet        = testElementSets.get(i);
      byte[]                 encodedElementSet = testEncodedElementSets.get(i);

      ArrayList<ASN1Element> compareList;
      if (elementSet == null)
      {
        compareList = new ArrayList<ASN1Element>(0);
      }
      else
      {
        compareList = elementSet;
      }


      ASN1Element element = new ASN1Element((byte) 0x00, encodedElementSet);

      try
      {
        assertTrue(listsAreEqual(compareList,
                                 element.decodeAsSequence().elements()));
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsSequence threw an exception for type=00, " +
                         "value=" + bytesToHex(encodedElementSet);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsSequence failed for type=00, value=" +
                   bytesToHex(encodedElementSet));
        throw afe;
      }


      element = new ASN1Element((byte) 0x30, encodedElementSet);

      try
      {
        assertTrue(listsAreEqual(compareList,
                                 element.decodeAsSequence().elements()));
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsSequence threw an exception for type=30, " +
                         "value=" + bytesToHex(encodedElementSet);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsSequence failed for type=30, value=" +
                   bytesToHex(encodedElementSet));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>decodeAsSet</CODE> method.
   */
  public void testDecodeAsSet()
  {
    int numElementSets = testElementSets.size();
    for (int i=0; i < numElementSets; i++)
    {
      ArrayList<ASN1Element> elementSet        = testElementSets.get(i);
      byte[]                 encodedElementSet = testEncodedElementSets.get(i);

      ArrayList<ASN1Element> compareList;
      if (elementSet == null)
      {
        compareList = new ArrayList<ASN1Element>(0);
      }
      else
      {
        compareList = elementSet;
      }


      ASN1Element element = new ASN1Element((byte) 0x00, encodedElementSet);

      try
      {
        assertTrue(listsAreEqual(compareList,
                                 element.decodeAsSet().elements()));
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsSet threw an exception for type=00, " +
                         "value=" + bytesToHex(encodedElementSet);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsSet failed for type=00, value=" +
                   bytesToHex(encodedElementSet));
        throw afe;
      }


      element = new ASN1Element((byte) 0x31, encodedElementSet);

      try
      {
        assertTrue(listsAreEqual(compareList,
                                 element.decodeAsSet().elements()));
      }
      catch (ASN1Exception ae)
      {
        String message = "decodeAsSet threw an exception for type=31, " +
                         "value=" + bytesToHex(encodedElementSet);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("decodeAsSet failed for type=31, value=" +
                   bytesToHex(encodedElementSet));
        throw afe;
      }
    }
  }



  /**
   * Tests the <CODE>equals</CODE> and <CODE>hashCode</CODE> methods.
   */
  public void testEqualsAndHashCode()
  {
    // Perform simple tests for two basic elements that should be the same, one
    // that should differ in type, one that should differ in value, and one that
    // should differ in both.
    ASN1Element e1 = new ASN1Element((byte) 0x00);
    ASN1Element e2 = new ASN1Element((byte) 0x00, new byte[0]);
    ASN1Element e3 = new ASN1Element((byte) 0x01);
    ASN1Element e4 = new ASN1Element((byte) 0x00, new byte[] { (byte) 0x00 });
    ASN1Element e5 = new ASN1Element((byte) 0x01, new byte[] { (byte) 0x00 });
    ASN1Element e6 = new ASN1Element((byte) 0x00, new byte[] { (byte) 0x01 });

    try
    {
      assertTrue(e1.equals(e2));  // Basic equality test.
    }
    catch (AssertionFailedError afe)
    {
      printError("e1.equals(e2) failed");
      throw afe;
    }

    try
    {
      assertTrue(e2.equals(e1));
    }
    catch (AssertionFailedError afe) // Reflexive equality test.
    {
      printError("e2.equals(e1) failed");
      throw afe;
    }

    try
    {
      assertFalse(e1.equals(e3)); // Difference in type.
    }
    catch (AssertionFailedError afe)
    {
      printError("e1.equals(e3) failed");
      throw afe;
    }

    try
    {
      assertFalse(e1.equals(e4)); // Difference in value.
    }
    catch (AssertionFailedError afe)
    {
      printError("e1.equals(e4) failed");
      throw afe;
    }

    try
    {
      assertFalse(e1.equals(e5)); // Differences in type and value.
    }
    catch (AssertionFailedError afe)
    {
      printError("e1.equals(e5) failed");
      throw afe;
    }

    try
    {
      assertFalse(e4.equals(e6)); // Difference in values with the same length.
    }
    catch (AssertionFailedError afe)
    {
      printError("e4.equals(e6) failed");
      throw afe;
    }


    // Make sure that equal elements have equal hash codes.
    try
    {
      assertEquals(e1.hashCode(), e2.hashCode()); // Hash code equality test.
    }
    catch (AssertionFailedError afe)
    {
      printError("hashCode failed");
      throw afe;
    }


    // Test equals against a null element.
    try
    {
      assertFalse(e1.equals(null));
    }
    catch (AssertionFailedError afe)
    {
      printError("e1.equals(null) failed for type=00");
      throw afe;
    }


    // Test boolean elements against equivalent generic elements.
    ASN1Element trueElement  = new ASN1Element((byte) 0x01,
                                               new byte[] { (byte) 0xFF });
    ASN1Element falseElement = new ASN1Element((byte) 0x01,
                                               new byte[] { (byte) 0x00 });
    ASN1Boolean trueBoolean  = new ASN1Boolean(true);
    ASN1Boolean falseBoolean = new ASN1Boolean(false);

    try
    {
      assertTrue(trueElement.equals(trueBoolean));
    }
    catch (AssertionFailedError afe)
    {
      printError("genericElement.equals(booleanElement) failed for " +
                 "booleanValue=true");
      throw afe;
    }

    try
    {
      assertTrue(trueBoolean.equals(trueElement));
    }
    catch (AssertionFailedError afe)
    {
      printError("booleanElement.equals(genericElement) failed for " +
                 "booleanValue=true");
      throw afe;
    }

    try
    {
      assertEquals(trueElement.hashCode(), trueBoolean.hashCode());
    }
    catch (AssertionFailedError afe)
    {
      printError("genericElement.hashCode != booleanElement.hashCode " +
                 "for booleanValue=true");
      throw afe;
    }

    try
    {
      assertTrue(falseElement.equals(falseBoolean));
    }
    catch (AssertionFailedError afe)
    {
      printError("genericElement.equals(booleanElement) failed for " +
                 "booleanValue=false");
      throw afe;
    }

    try
    {
      assertTrue(falseBoolean.equals(falseElement));
    }
    catch (AssertionFailedError afe)
    {
      printError("booleanElement.equals(genericElement) failed for " +
                 "booleanValue=false");
      throw afe;
    }

    try
    {
      assertEquals(falseElement.hashCode(), falseBoolean.hashCode());
    }
    catch (AssertionFailedError afe)
    {
      printError("genericElement.hashCode != booleanElement.hashCode " +
                 "for booleanValue=false");
      throw afe;
    }


    // Test integer elements against equivalent generic elements.
    int numIntegers = testIntegers.size();
    for (int i=0; i < numIntegers; i++)
    {
      int    intValue        = testIntegers.get(i);
      byte[] encodedIntValue = testEncodedIntegers.get(i);

      ASN1Element genericElement = new ASN1Element((byte) 0x02,
                                                   encodedIntValue);
      ASN1Integer integerElement  = new ASN1Integer(intValue);

      try
      {
        assertTrue(genericElement.equals(integerElement));
      }
      catch (AssertionFailedError afe)
      {
        printError("genericElement.equals(integerElement) failed for " +
                   "intValue=" + intValue);
        throw afe;
      }

      try
      {
        assertTrue(integerElement.equals(genericElement)); // Reflexive test.
      }
      catch (AssertionFailedError afe)
      {
        printError("integerElement.equals(genericElement) failed for " +
                   "intValue=" + intValue);
        throw afe;
      }

      try
      {
        // Test for matching hash codes.
        assertEquals(genericElement.hashCode(), integerElement.hashCode());
      }
      catch (AssertionFailedError afe)
      {
        printError("genericElement.hashCode != integerElement.hashCode for " +
                   "intValue=" + intValue);
        throw afe;
      }
    }
  }
}

