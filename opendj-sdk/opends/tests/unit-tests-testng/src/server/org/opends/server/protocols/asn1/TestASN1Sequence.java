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



import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Sequence class.
 */
public class TestASN1Sequence
       extends ASN1TestCase
{
  // The array of element arrays to use for the testing.  Each element is a
  // single-element array containing a byte[] with the encoded elements.
  private Object[][] elementArrays;

  // The array of elements to use for the testing.  Each element is a
  // single-element array containing an ArrayList<ASN1Element>.
  private Object[][] elementLists;



  /**
   * Constructs the element lists that will be used to perform the testing.
   */
  @BeforeClass()
  public void populateElementLists()
  {
    ArrayList<ArrayList<ASN1Element>> lists =
         new ArrayList<ArrayList<ASN1Element>>();

    // Add a null element.
    lists.add(null);

    // Add an empty list.
    lists.add(new ArrayList<ASN1Element>());

    // Create an array of single elements, and add each of them in their own
    // lists.
    ASN1Element[] elementArray =
    {
      new ASN1OctetString(),
      new ASN1OctetString((byte) 0x50),
      new ASN1OctetString(new byte[50]),
      new ASN1OctetString("Hello"),
      new ASN1Element((byte) 0x50),
      new ASN1Element((byte) 0x50, new byte[50]),
      new ASN1Boolean(false),
      new ASN1Boolean(true),
      new ASN1Enumerated(0),
      new ASN1Enumerated(1),
      new ASN1Enumerated(127),
      new ASN1Enumerated(128),
      new ASN1Enumerated(255),
      new ASN1Enumerated(256),
      new ASN1Integer(0),
      new ASN1Integer(1),
      new ASN1Integer(127),
      new ASN1Integer(128),
      new ASN1Integer(255),
      new ASN1Integer(256),
      new ASN1Long(0),
      new ASN1Long(1),
      new ASN1Long(127),
      new ASN1Long(128),
      new ASN1Long(255),
      new ASN1Long(256),
      new ASN1Null(),
      new ASN1Sequence(),
      new ASN1Set()
    };

    // Add lists of single elements.
    for (ASN1Element e : elementArray)
    {
      ArrayList<ASN1Element> list = new ArrayList<ASN1Element>(1);
      list.add(e);
      lists.add(list);
    }


    // Create multi-element lists based on the single-element lists.
    for (int i=0; i < elementArray.length; i++)
    {
      ArrayList<ASN1Element> list = new ArrayList<ASN1Element>(i+1);
      for (int j=0; j <=i; j++)
      {
        list.add(elementArray[j]);
      }
      lists.add(list);
    }


    // Convert the lists into object arrays.
    elementLists = new Object[lists.size()][1];
    for (int i=0; i < elementLists.length; i++)
    {
      elementLists[i] = new Object[] { lists.get(i) };
    }

    lists.remove(null);
    elementArrays = new Object[lists.size()][1];
    for (int i=0; i < elementArrays.length; i++)
    {
      elementArrays[i] = new Object[] { ASN1Element.encodeValue(lists.get(i)) };
    }
  }



  /**
   * Retrieves lists of byte arrays that can be used to construct sequences.
   *
   * @return  Lists of byte arrays that can be used to construct sequences.
   */
  @DataProvider(name = "elementArrays")
  public Object[][] getElementArrays()
  {
    return elementArrays;
  }



  /**
   * Retrieves lists of ASN.1 elements that can be used to construct sequences.
   *
   * @return  Lists of ASN.1 elements that can be used to construct sequences.
   */
  @DataProvider(name = "elementLists")
  public Object[][] getElementLists()
  {
    return elementLists;
  }



  /**
   * Tests the first constructor, which doesn't take any arguments.
   */
  @Test()
  public void testConstructor1()
  {
    new ASN1Sequence();
  }



  /**
   * Create the values that can be used for testing BER types.
   *
   * @return  The values that can be used for testing BER types.
   */
  @DataProvider(name = "types")
  public Object[][] getTypes()
  {
    // Create an array with all of the valid single-byte types.  We don't
    // support multi-byte types, so this should be a comprehensive data set.
    Object[][] testTypes = new Object[0xFF][1];
    for (int i=0x00; i < 0xFF; i++)
    {
      testTypes[i] = new Object[] { (byte) (i & 0xFF) };
    }

    return testTypes;
  }



  /**
   * Tests the second constructor, which takes a byte argument.
   *
   * @param  b  The BER type to use for the sequence.
   */
  @Test(dataProvider = "types")
  public void testConstructor2(byte b)
  {
    new ASN1Sequence(b);
  }



  /**
   * Tests the third constructor, which takes a list of elements.
   *
   * @param  elements  The list of elements to use to create the sequence.
   */
  @Test(dataProvider = "elementLists")
  public void testConstructor3(ArrayList<ASN1Element> elements)
  {
    new ASN1Sequence(elements);
  }



  /**
   * Tests the third constructor, which takes a byte and a list of elements.
   *
   * @param  elements  The list of elements to use to create the sequence.
   */
  @Test(dataProvider = "elementLists")
  public void testConstructor4(ArrayList<ASN1Element> elements)
  {
    for (int i=0; i < 255; i++)
    {
      new ASN1Sequence((byte) (i & 0xFF), elements);
    }
  }



  /**
   * Tests the <CODE>elements</CODE> method.
   *
   * @param  elements  The list of elements to use to create the sequence.
   */
  @Test(dataProvider = "elementLists")
  public void testGetElements(ArrayList<ASN1Element> elements)
  {
    ASN1Sequence s = new ASN1Sequence(elements);
    if (elements == null)
    {
      assertEquals(s.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(s.elements(), elements);
    }
  }



  /**
   * Tests the <CODE>setElements</CODE> method.
   *
   * @param  elements  The list of elements to use to create the sequence.
   */
  @Test(dataProvider = "elementLists")
  public void testSetElements(ArrayList<ASN1Element> elements)
  {
    ASN1Sequence s = new ASN1Sequence();
    s.setElements(elements);
    if (elements == null)
    {
      assertEquals(s.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(s.elements(), elements);
    }
  }



  /**
   * Tests the <CODE>setValue</CODE> method with valid values.
   *
   * @param  encodedElements  The byte array containing the encoded elements to
   *                          use in the value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testSetValueValid(byte[] encodedElements)
         throws Exception
  {
    ASN1Sequence s = new ASN1Sequence();
    s.setValue(encodedElements);
  }



  /**
   * Tests the <CODE>setValue</CODE> method with a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testSetValueNull()
         throws Exception
  {
    ASN1Sequence s = new ASN1Sequence();
    s.setValue(null);
  }



  /**
   * Retrieves a set of byte arrays containing invalid element value encodings.
   *
   * @return  A set of byte arrays containing invalid element value encodings.
   */
  @DataProvider(name = "invalidElementArrays")
  public Object[][] getInvalidArrays()
  {
    return new Object[][]
    {
      new Object[] { new byte[] { 0x05 } },
      new Object[] { new byte[] { 0x05, 0x01 } },
      new Object[] { new byte[] { 0x05, (byte) 0x85, 0x00, 0x00, 0x00, 0x00,
                                  0x00 } },
      new Object[] { new byte[] { 0x05, (byte) 0x82, 0x00 } },
      new Object[] { new byte[] { 0x05, 0x00, 0x05 } },
      new Object[] { new byte[] { 0x05, 0x00, 0x05, 0x01 } },
    };
  }



  /**
   * Tests the <CODE>setValue</CODE> method with valid values.
   *
   * @param  encodedElements  The byte array containing the encoded elements to
   *                          use in the value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidElementArrays",
        expectedExceptions = { ASN1Exception.class })
  public void testSetValueInvalid(byte[] invalidElements)
         throws Exception
  {
    ASN1Sequence s = new ASN1Sequence();
    s.setValue(invalidElements);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes an ASN1Element
   * argument with valid elements.
   *
   * @param  encodedElements  Byte arrays that may be used as valid values for
   *                          encoded elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testDecodeValidElementAsSequence(byte[] encodedElements)
         throws Exception
  {
    ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_SEQUENCE_TYPE,
                                    encodedElements);
    ASN1Sequence.decodeAsSequence(e);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes an ASN1Element
   * argument with valid elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullElementAsSequence()
         throws Exception
  {
    ASN1Element e = null;
    ASN1Sequence.decodeAsSequence(e);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with valid arrays.
   *
   * @param  encodedElements  Byte arrays that may be used as valid values for
   *                          encoded elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testDecodeValidArrayAsSequence(byte[] encodedElements)
         throws Exception
  {
    byte[] encodedLength = ASN1Element.encodeLength(encodedElements.length);
    byte[] elementBytes  =
         new byte[1 + encodedLength.length + encodedElements.length];
    elementBytes[0] = ASN1Constants.UNIVERSAL_SEQUENCE_TYPE;
    System.arraycopy(encodedLength, 0, elementBytes, 1, encodedLength.length);
    System.arraycopy(encodedElements, 0, elementBytes, 1+encodedLength.length,
                     encodedElements.length);
    ASN1Sequence.decodeAsSequence(elementBytes);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNullArrayAsSequence()
         throws Exception
  {
    byte[] b = null;
    ASN1Sequence.decodeAsSequence(b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeShortArrayAsSequence()
         throws Exception
  {
    byte[] b = new byte[1];
    ASN1Sequence.decodeAsSequence(b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with an array that takes too many bytes to encode the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongLengthArrayAsSequence()
         throws Exception
  {
    byte[] b = { 0x30, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
    ASN1Sequence.decodeAsSequence(b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with an array that doesn't fully describe the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTruncatedLengthArrayAsSequence()
         throws Exception
  {
    byte[] b = { 0x30, (byte) 0x82, 0x00 };
    ASN1Sequence.decodeAsSequence(b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with an array whose decoded length doesn't match the real length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLengthMismatchArrayAsSequence()
         throws Exception
  {
    byte[] b = { 0x30, 0x01 };
    ASN1Sequence.decodeAsSequence(b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with valid arrays.
   *
   * @param  encodedElements  Byte arrays that may be used as valid values for
   *                          encoded elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testDecodeTypeAndValidArrayAsSequence(byte[] encodedElements)
         throws Exception
  {
    for (int i=0; i < 255; i++)
    {
      byte[] encodedLength = ASN1Element.encodeLength(encodedElements.length);
      byte[] elementBytes  =
           new byte[1 + encodedLength.length + encodedElements.length];
      elementBytes[0] = ASN1Constants.UNIVERSAL_SEQUENCE_TYPE;
      System.arraycopy(encodedLength, 0, elementBytes, 1, encodedLength.length);
      System.arraycopy(encodedElements, 0, elementBytes, 1+encodedLength.length,
                       encodedElements.length);
      ASN1Sequence.decodeAsSequence((byte) (i & 0xFF), elementBytes);
    }
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with a null array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTypeAndNullArrayAsSequence()
         throws Exception
  {
    byte[] b = null;
    ASN1Sequence.decodeAsSequence((byte) 0x50, b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTypeAndShortArrayAsSequence()
         throws Exception
  {
    byte[] b = new byte[1];
    ASN1Sequence.decodeAsSequence((byte) 0x50, b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with an array that takes too many bytes to encode the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTypeAndLongLengthArrayAsSequence()
         throws Exception
  {
    byte[] b = { 0x30, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
    ASN1Sequence.decodeAsSequence((byte) 0x50, b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with an array that doesn't fully describe the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTypeAndTruncatedLengthArrayAsSequence()
         throws Exception
  {
    byte[] b = { 0x30, (byte) 0x82, 0x00 };
    ASN1Sequence.decodeAsSequence((byte) 0x50, b);
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with an array whose decoded length doesn't match the real length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTypeAndLengthMismatchArrayAsSequence()
         throws Exception
  {
    byte[] b = { 0x30, 0x01 };
    ASN1Sequence.decodeAsSequence((byte) 0x50, b);
  }



  /**
   * Tests the <CODE>toString</CODE> method that takes a string builder
   * argument.
   *
   * @param  elements  The list of elements to use to create the sequence.
   */
  @Test(dataProvider = "elementLists")
  public void testToString1(ArrayList<ASN1Element> elements)
  {
    new ASN1Sequence(elements).toString(new StringBuilder());
  }



  /**
   * Tests the <CODE>toString</CODE> method that takes string builder and
   * integer arguments.
   *
   * @param  elements  The list of elements to use to create the sequence.
   */
  @Test(dataProvider = "elementLists")
  public void testToString2(ArrayList<ASN1Element> elements)
  {
    new ASN1Sequence(elements).toString(new StringBuilder(), 1);
  }
}

