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



import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.types.ByteString;

import static org.testng.Assert.*;




/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Element class.
 */
public class TestASN1Element
       extends ASN1TestCase
{
  /**
   * Create the values that can be used for testing BER types.
   *
   * @return  The values that can be used for testing BER types.
   */
  @DataProvider(name = "testTypes")
  public Object[][] getTestTypes()
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
   * Tests the <CODE>getType</CODE> and <CODE>setType</CODE> methods.
   *
   * @param  type  The BER type to use in the test.
   */
  @Test(dataProvider = "testTypes")
  public void testGetAndSetType(byte type)
  {
    ASN1Element e = new ASN1Element((byte) 0x00);
    e.setType(type);
    assertEquals(e.getType(), type);
  }



  /**
   * Tests the <CODE>isUniversal</CODE> method.
   *
   * @param  type  The BER type to use in the test.
   */
  @Test(dataProvider = "testTypes")
  public void testIsUniversal(byte type)
  {
    boolean isUniversal = (((type & 0xFF) >> 6) == 0x00);
    assertEquals(new ASN1Element(type).isUniversal(), isUniversal);
  }



  /**
   * Tests the <CODE>isApplicationSpecific</CODE> method.
   *
   * @param  type  The BER type to use in the test.
   */
  @Test(dataProvider = "testTypes")
  public void testIsApplicationSpecific(byte type)
  {
    boolean isApplicationSpecific = (((type & 0xFF) >> 6) == 0x01);
    assertEquals(new ASN1Element(type).isApplicationSpecific(),
                 isApplicationSpecific);
  }



  /**
   * Tests the <CODE>isContextSpecific</CODE> method.
   *
   * @param  type  The BER type to use in the test.
   */
  @Test(dataProvider = "testTypes")
  public void testIsContextSpecific(byte type)
  {
    boolean isContextSpecific = (((type & 0xFF) >> 6) == 0x02);
    assertEquals(new ASN1Element(type).isContextSpecific(), isContextSpecific);
  }



  /**
   * Tests the <CODE>isPrivate</CODE> method.
   *
   * @param  type  The BER type to use in the test.
   */
  @Test(dataProvider = "testTypes")
  public void testIsPrivate(byte type)
  {
    boolean isPrivate = (((type & 0xFF) >> 6) == 0x03);
    assertEquals(new ASN1Element(type).isPrivate(), isPrivate);
  }



  /**
   * Tests the <CODE>isPrimitive</CODE> method.
   *
   * @param  type  The BER type to use in the test.
   */
  @Test(dataProvider = "testTypes")
  public void testIsPrimitive(byte type)
  {
    boolean isPrimitive = ((type & 0xDF) == (type & 0xFF));
    assertEquals(new ASN1Element(type).isPrimitive(), isPrimitive);
  }



  /**
   * Tests the <CODE>isConstructed</CODE> method.
   *
   * @param  type  The BER type to use in the test.
   */
  @Test(dataProvider = "testTypes")
  public void testIsConstructed(byte type)
  {
    boolean isConstructed = ((type & 0xDF) != (type & 0xFF));
    assertEquals(new ASN1Element(type).isConstructed(), isConstructed);
  }



  /**
   * Create byte arrays to use for element values.
   *
   * @return  A list of byte arrays that can be used as element values.
   */
  @DataProvider(name = "testValues")
  public Object[][] getTestValues()
  {
    // NOTE -- Don't make these arrays too big since they consume memory.
    return new Object[][]
    {
      new Object[] { null },              // The null value
      new Object[] { new byte[0x00] },    // The zero-byte value
      new Object[] { new byte[0x01] },    // The single-byte value
      new Object[] { new byte[0x7F] },    // The largest 1-byte length encoding
      new Object[] { new byte[0x80] },    // The smallest 2-byte length encoding
      new Object[] { new byte[0xFF] },    // The largest 2-byte length encoding
      new Object[] { new byte[0x0100] },  // The smallest 3-byte length encoding
    };
  }



  /**
   * Tests the <CODE>getValue</CODE> and <CODE>setValue</CODE> methods.
   *
   * @param  value  The value to use in the test.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(dataProvider = "testValues")
  public void testGetAndSetValue(byte[] value)
         throws Exception
  {
    ASN1Element e = new ASN1Element((byte) 0x00);
    e.setValue(value);

    if (value == null)
    {
      assertEquals(e.value(), new byte[0]);
    }
    else
    {
      assertEquals(e.value(), value);
    }
  }



  /**
   * Create decoded and encoded lengths.
   *
   * @return  A list of decoded and encoded lengths.
   */
  @DataProvider(name = "testLengths")
  public Object[][] getTestLengths()
  {
    return new Object[][]
    {
      new Object[] { 0x00, new byte[] { 0x00 } },
      new Object[] { 0x01, new byte[] { 0x01 } },
      new Object[] { 0x7F, new byte[] { 0x7F } },
      new Object[] { 0x80, new byte[] { (byte) 0x81, (byte) 0x80 } },
      new Object[] { 0xFF, new byte[] { (byte) 0x81, (byte) 0xFF } },
      new Object[] { 0x0100, new byte[] { (byte) 0x82, 0x01, 0x00 } },
      new Object[] { 0xFFFF,
                     new byte[] { (byte) 0x82, (byte) 0xFF, (byte) 0xFF } },
      new Object[] { 0x010000, new byte[] { (byte) 0x83, 0x01, 0x00, 0x00 } },
      new Object[] { 0xFFFFFF,
                     new byte[] { (byte) 0x83, (byte) 0xFF, (byte) 0xFF,
                                  (byte) 0xFF } },
      new Object[] { 0x01000000,
                     new byte[] { (byte) 0x84, 0x01, 0x00, 0x00, 0x00 } },
      new Object[] { 0x7FFFFFFF,
                     new byte[] { (byte) 0x84, (byte) 0x7F, (byte) 0xFF,
                                  (byte) 0xFF, (byte) 0xFF } },
    };
  }


  /**
   * Tests the <CODE>encodeLength</CODE> method.
   *
   * @param  decodedLength  The decoded length to encode.
   * @param  encodedLength  The encoded representation of the length.
   */
  @Test(dataProvider = "testLengths")
  public void testEncodeLength(int decodedLength, byte[] encodedLength)
  {
    assertEquals(ASN1Element.encodeLength(decodedLength), encodedLength);
  }



  /**
   * Tests the <CODE>encode</CODE> and <CODE>decode</CODE> methods.
   *
   * @param  value  The value to use in the test.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(dataProvider = "testValues")
  public void testEncodeAndDecode(byte[] value)
         throws Exception
  {
    for (int i=0x00; i < 0xFF; i++)
    {
      byte type = (byte) i;
      ASN1Element e = new ASN1Element(type, value);
      byte[] encodedElement = e.encode();

      ASN1Element d = ASN1Element.decode(encodedElement);
      assertEquals(e, d);
      assertEquals(d.getType(), type);
      assertTrue(e.equalsElement(d));

      if (value == null)
      {
        assertEquals(d.value(), new byte[0]);
      }
      else
      {
        assertEquals(d.value(), value);
      }

      d = ASN1Element.decode(encodedElement, 0, encodedElement.length);
      assertEquals(e, d);
      assertEquals(d.getType(), type);
      assertTrue(e.equalsElement(d));

      if (value == null)
      {
        assertEquals(d.value(), new byte[0]);
      }
      else
      {
        assertEquals(d.value(), value);
      }
    }
  }



  /**
   * Tests to ensure that there is a failure when trying to decode a null as an
   * element.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeFailureNull()
         throws Exception
  {
    ASN1Element.decode(null);
  }



  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsBoolean()
         throws Exception
  {
    // First, make sure that we can decode actual Boolean elements as Boolean
    // elements, using both the standard type as well as a nonstandard type.
    boolean[] booleanValues = new boolean[] { true, false };
    for (boolean b : booleanValues)
    {
      ASN1Element e = new ASN1Boolean(b);
      ASN1Boolean booleanElement = e.decodeAsBoolean();
      assertEquals(booleanElement.booleanValue(), b);

      e = new ASN1Boolean((byte) 0x50, b);
      booleanElement = e.decodeAsBoolean();
      assertEquals(booleanElement.booleanValue(), b);
    }


    // Next, make sure we can decode generic ASN.1 elements with a single-byte
    // value as a Boolean element.
    for (int i=0; i < 256; i++)
    {
      ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_BOOLEAN_TYPE,
                                      new byte[] { (byte) i });
      ASN1Boolean b = e.decodeAsBoolean();
      assertEquals(b.booleanValue(), (i != 0));

      e = new ASN1Element((byte) 0x50, new byte[] { (byte) i });
      b = e.decodeAsBoolean();
      assertEquals(b.booleanValue(), (i != 0));
    }
  }



  /**
   * Tests the <CODE>decodeAsEnumerated</CODE> method.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsEnumerated()
         throws Exception
  {
    int[] intValues =
    {
      0x00000000,
      0x00000001,
      0x0000000F,
      0x00000010,
      0x0000007F,
      0x00000080,
      0x000000FF,
      0x00000100,
      0x00000FFF,
      0x00001000,
      0x0000FFFF,
      0x00010000,
      0x000FFFFF,
      0x00100000,
      0x00FFFFFF,
      0x01000000,
      0x0FFFFFFF,
      0x10000000,
      0x7FFFFFFF
    };

    // First, make sure that we can decode actual enumerated elements as
    // enumerated elements, using both the standard type as well as a
    // nonstandard type.
    for (int i : intValues)
    {
      ASN1Element e = new ASN1Enumerated(i);
      ASN1Enumerated enumeratedElement = e.decodeAsEnumerated();
      assertEquals(enumeratedElement.intValue(), i);

      e = new ASN1Enumerated((byte) 0x50, i);
      enumeratedElement = e.decodeAsEnumerated();
      assertEquals(enumeratedElement.intValue(), i);
    }


    // Next, make sure we can decode generic ASN.1 elements as enumerated
    // elements.
    for (int i : intValues)
    {
      byte[] encoding;
      if ((i & 0xFF) == i)
      {
        encoding = new byte[1];
        encoding[0] = (byte) (i & 0xFF);
      }
      else if ((i & 0xFFFF) == i)
      {
        encoding = new byte[2];
        encoding[0] = (byte) ((i >> 8) & 0xFF);
        encoding[1] = (byte) (i & 0xFF);
      }
      else if ((i & 0xFFFFFF) == i)
      {
        encoding = new byte[3];
        encoding[0] = (byte) ((i >> 16) & 0xFF);
        encoding[1] = (byte) ((i >> 8) & 0xFF);
        encoding[2] = (byte) (i & 0xFF);
      }
      else
      {
        encoding = new byte[4];
        encoding[0] = (byte) ((i >> 24) & 0xFF);
        encoding[1] = (byte) ((i >> 16) & 0xFF);
        encoding[2] = (byte) ((i >> 8) & 0xFF);
        encoding[3] = (byte) (i & 0xFF);
      }

      ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_ENUMERATED_TYPE,
                                      encoding);
      ASN1Enumerated enumeratedElement = e.decodeAsEnumerated();
      assertEquals(enumeratedElement.intValue(), i);

      e = new ASN1Element((byte) 0x50, encoding);
      enumeratedElement = e.decodeAsEnumerated();
      assertEquals(enumeratedElement.intValue(), i);
    }
  }



  /**
   * Tests the <CODE>decodeAsInteger</CODE> method.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsInteger()
         throws Exception
  {
    int[] intValues =
    {
      0x00000000,
      0x00000001,
      0x0000000F,
      0x00000010,
      0x0000007F,
      0x00000080,
      0x000000FF,
      0x00000100,
      0x00000FFF,
      0x00001000,
      0x0000FFFF,
      0x00010000,
      0x000FFFFF,
      0x00100000,
      0x00FFFFFF,
      0x01000000,
      0x0FFFFFFF,
      0x10000000,
      0x7FFFFFFF,
      -0x00000001,
      -0x0000000F,
      -0x00000010,
      -0x0000007F,
      -0x00000080,
      -0x000000FF,
      -0x00000100,
      -0x00000FFF,
      -0x00001000,
      -0x0000FFFF,
      -0x00010000,
      -0x000FFFFF,
      -0x00100000,
      -0x00FFFFFF,
      -0x01000000,
      -0x0FFFFFFF,
      -0x10000000,
      -0x7FFFFFFF,
      0x80000000
    };

    // First, make sure that we can decode actual integer elements as integer
    // elements, using both the standard type as well as a nonstandard type.
    for (int i : intValues)
    {
      ASN1Element e = new ASN1Integer(i);
      ASN1Integer integerElement = e.decodeAsInteger();
      assertEquals(integerElement.intValue(), i);

      e = new ASN1Integer((byte) 0x50, i);
      integerElement = e.decodeAsInteger();
      assertEquals(integerElement.intValue(), i);
    }


    // Next, make sure we can decode generic ASN.1 elements as integer elements.
    for (int i : intValues)
    {
      byte[] encoding;
      if ((i & 0x7F) == i)
      {
        encoding = new byte[1];
        encoding[0] = (byte) (i & 0xFF);
      }
      else if ((i & 0x7FFF) == i)
      {
        encoding = new byte[2];
        encoding[0] = (byte) ((i >> 8) & 0xFF);
        encoding[1] = (byte) (i & 0xFF);
      }
      else if ((i & 0x7FFFFF) == i)
      {
        encoding = new byte[3];
        encoding[0] = (byte) ((i >> 16) & 0xFF);
        encoding[1] = (byte) ((i >> 8) & 0xFF);
        encoding[2] = (byte) (i & 0xFF);
      }
      else
      {
        encoding = new byte[4];
        encoding[0] = (byte) ((i >> 24) & 0xFF);
        encoding[1] = (byte) ((i >> 16) & 0xFF);
        encoding[2] = (byte) ((i >> 8) & 0xFF);
        encoding[3] = (byte) (i & 0xFF);
      }

      ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE,
                                      encoding);
      ASN1Integer integerElement = e.decodeAsInteger();
      assertEquals(integerElement.intValue(), i);

      e = new ASN1Element((byte) 0x50, encoding);
      integerElement = e.decodeAsInteger();
      assertEquals(integerElement.intValue(), i);
    }
  }



  /**
   * Tests the <CODE>decodeAsLong</CODE> method.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsLong()
         throws Exception
  {
    long[] longValues =
    {
      0x0000000000000000L,
      0x0000000000000001L,
      0x000000000000007FL,
      0x0000000000000080L,
      0x00000000000000FFL,
      0x0000000000000100L,
      0x000000000000FFFFL,
      0x0000000000010000L,
      0x0000000000FFFFFFL,
      0x0000000001000000L,
      0x00000000FFFFFFFFL,
      0x0000000100000000L,
      0x000000FFFFFFFFFFL,
      0x0000010000000000L,
      0x0000FFFFFFFFFFFFL,
      0x0001000000000000L,
      0x00FFFFFFFFFFFFFFL,
      0x0100000000000000L,
      0x7FFFFFFFFFFFFFFFL,
      -0x0000000000000001L,
      -0x000000000000007FL,
      -0x0000000000000080L,
      -0x00000000000000FFL,
      -0x0000000000000100L,
      -0x000000000000FFFFL,
      -0x0000000000010000L,
      -0x0000000000FFFFFFL,
      -0x0000000001000000L,
      -0x00000000FFFFFFFFL,
      -0x0000000100000000L,
      -0x000000FFFFFFFFFFL,
      -0x0000010000000000L,
      -0x0000FFFFFFFFFFFFL,
      -0x0001000000000000L,
      -0x00FFFFFFFFFFFFFFL,
      -0x0100000000000000L,
      -0x7FFFFFFFFFFFFFFFL,
      0x8000000000000000L
    };

    // First, make sure that we can decode actual long elements as long
    // elements, using both the standard type as well as a nonstandard type.
    for (long l : longValues)
    {
      ASN1Element e = new ASN1Long(l);
      ASN1Long longElement = e.decodeAsLong();
      assertEquals(longElement.longValue(), l);

      e = new ASN1Long((byte) 0x50, l);
      longElement = e.decodeAsLong();
      assertEquals(longElement.longValue(), l);
    }


    // Next, make sure we can decode generic ASN.1 elements as long elements.
    for (long l : longValues)
    {
      byte[] encoding;
      if ((l & 0x7FL) == l)
      {
        encoding = new byte[1];
        encoding[0] = (byte) (l & 0xFF);
      }
      else if ((l & 0x7FFFL) == l)
      {
        encoding = new byte[2];
        encoding[0] = (byte) ((l >> 8) & 0xFF);
        encoding[1] = (byte) (l & 0xFF);
      }
      else if ((l & 0x7FFFFFL) == l)
      {
        encoding = new byte[3];
        encoding[0] = (byte) ((l >> 16) & 0xFF);
        encoding[1] = (byte) ((l >> 8) & 0xFF);
        encoding[2] = (byte) (l & 0xFF);
      }
      else if ((l & 0x7FFFFFFFL) == l)
      {
        encoding = new byte[4];
        encoding[0] = (byte) ((l >> 24) & 0xFF);
        encoding[1] = (byte) ((l >> 16) & 0xFF);
        encoding[2] = (byte) ((l >> 8) & 0xFF);
        encoding[3] = (byte) (l & 0xFF);
      }
      else if ((l & 0x7FFFFFFFFFL) == l)
      {
        encoding = new byte[5];
        encoding[0] = (byte) ((l >> 32) & 0xFF);
        encoding[1] = (byte) ((l >> 24) & 0xFF);
        encoding[2] = (byte) ((l >> 16) & 0xFF);
        encoding[3] = (byte) ((l >> 8) & 0xFF);
        encoding[4] = (byte) (l & 0xFF);
      }
      else if ((l & 0x7FFFFFFFFFFFL) == l)
      {
        encoding = new byte[6];
        encoding[0] = (byte) ((l >> 40) & 0xFF);
        encoding[1] = (byte) ((l >> 32) & 0xFF);
        encoding[2] = (byte) ((l >> 24) & 0xFF);
        encoding[3] = (byte) ((l >> 16) & 0xFF);
        encoding[4] = (byte) ((l >> 8) & 0xFF);
        encoding[5] = (byte) (l & 0xFF);
      }
      else if ((l & 0x7FFFFFFFFFFFFFL) == l)
      {
        encoding = new byte[7];
        encoding[0] = (byte) ((l >> 48) & 0xFF);
        encoding[1] = (byte) ((l >> 40) & 0xFF);
        encoding[2] = (byte) ((l >> 32) & 0xFF);
        encoding[3] = (byte) ((l >> 24) & 0xFF);
        encoding[4] = (byte) ((l >> 16) & 0xFF);
        encoding[5] = (byte) ((l >> 8) & 0xFF);
        encoding[6] = (byte) (l & 0xFF);
      }
      else
      {
        encoding = new byte[8];
        encoding[0] = (byte) ((l >> 56) & 0xFF);
        encoding[1] = (byte) ((l >> 48) & 0xFF);
        encoding[2] = (byte) ((l >> 40) & 0xFF);
        encoding[3] = (byte) ((l >> 32) & 0xFF);
        encoding[4] = (byte) ((l >> 24) & 0xFF);
        encoding[5] = (byte) ((l >> 16) & 0xFF);
        encoding[6] = (byte) ((l >> 8) & 0xFF);
        encoding[7] = (byte) (l & 0xFF);
      }

      ASN1Element e = new ASN1Element(ASN1Constants.UNIVERSAL_INTEGER_TYPE,
                                      encoding);
      ASN1Long longElement = e.decodeAsLong();
      assertEquals(longElement.longValue(), l);

      e = new ASN1Element((byte) 0x50, encoding);
      longElement = e.decodeAsLong();
      assertEquals(longElement.longValue(), l);
    }
  }



  /**
   * Tests the <CODE>decodeAsNull</CODE> method.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test()
  public void testDecodeAsNull()
         throws Exception
  {
    // First, make sure that we can decode actual null elements as null
    // elements, using both the standard type as well as a nonstandard type.
    ASN1Element e = new ASN1Null();
    e.decodeAsNull();

    e = new ASN1Null((byte) 0x50);
    e.decodeAsNull();


    // Next, make sure we can decode generic ASN.1 elements with a zero-byte
    // value as a null element.
    e = new ASN1Element(ASN1Constants.UNIVERSAL_NULL_TYPE);
    e.decodeAsNull();

    e = new ASN1Element(ASN1Constants.UNIVERSAL_NULL_TYPE, null);
    e.decodeAsNull();

    e = new ASN1Element(ASN1Constants.UNIVERSAL_NULL_TYPE, new byte[0]);
    e.decodeAsNull();

    e = new ASN1Element((byte) 0x50);
    e.decodeAsNull();

    e = new ASN1Element((byte) 0x50, null);
    e.decodeAsNull();

    e = new ASN1Element((byte) 0x50, new byte[0]);
    e.decodeAsNull();
  }



  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method.
   *
   * @param  value  The value to use for the octet string element.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(dataProvider = "testValues")
  public void testDecodeAsOctetString(byte[] value)
         throws Exception
  {
    // First, make sure that we can decode actual octet string elements as octet
    // string elements, using both the standard type as well as a nonstandard
    // type.
    ASN1Element e = new ASN1OctetString(value);
    ASN1OctetString octetStringElement = e.decodeAsOctetString();
    if (value == null)
    {
      assertEquals(octetStringElement.value(), new byte[0]);
    }
    else
    {
      assertEquals(octetStringElement.value(), value);
    }

    e = new ASN1OctetString((byte) 0x50, value);
    octetStringElement = e.decodeAsOctetString();
    if (value == null)
    {
      assertEquals(octetStringElement.value(), new byte[0]);
    }
    else
    {
      assertEquals(octetStringElement.value(), value);
    }


    // Next, make sure that we can decode a generic ASN.1 element as an octet
    // string element.
    e = new ASN1Element(ASN1Constants.UNIVERSAL_OCTET_STRING_TYPE, value);
    octetStringElement = e.decodeAsOctetString();
    if (value == null)
    {
      assertEquals(octetStringElement.value(), new byte[0]);
    }
    else
    {
      assertEquals(octetStringElement.value(), value);
    }

    e = new ASN1Element((byte) 0x50, value);
    octetStringElement = e.decodeAsOctetString();
    if (value == null)
    {
      assertEquals(octetStringElement.value(), new byte[0]);
    }
    else
    {
      assertEquals(octetStringElement.value(), value);
    }
  }



  /**
   * Retrieves arrays of ASN.1 elements for use in testing with sequences and
   * sets.
   *
   * @return  Arrays of ASN.1 elements for use in testing with sequences and
   *          sets.
   */
  @DataProvider(name = "elementArrays")
  public Object[][] getElementArrays()
  {
    ArrayList<ASN1Element[]> arrays = new ArrayList<ASN1Element[]>();
    arrays.add(null);
    arrays.add(new ASN1Element[0]);
    arrays.add(new ASN1Element[] { new ASN1Element((byte) 0x50) });
    arrays.add(new ASN1Element[] { new ASN1Element((byte) 0x50, null) });
    arrays.add(new ASN1Element[] { new ASN1Element((byte) 0x50, new byte[0]) });
    arrays.add(new ASN1Element[] { new ASN1Element((byte) 0x50, new byte[1]) });
    arrays.add(new ASN1Element[] { new ASN1Boolean(true) });
    arrays.add(new ASN1Element[] { new ASN1Enumerated(0) });
    arrays.add(new ASN1Element[] { new ASN1Integer(0) });
    arrays.add(new ASN1Element[] { new ASN1Long(0) });
    arrays.add(new ASN1Element[] { new ASN1OctetString() });
    arrays.add(new ASN1Element[] { new ASN1OctetString(),
                                   new ASN1OctetString() });
    arrays.add(new ASN1Element[] { new ASN1OctetString(),
                                   new ASN1OctetString(),
                                   new ASN1OctetString() });
    arrays.add(new ASN1Element[] { new ASN1OctetString(),
                                   new ASN1OctetString(),
                                   new ASN1OctetString(),
                                   new ASN1OctetString() });
    arrays.add(new ASN1Element[] { new ASN1OctetString(),
                                   new ASN1OctetString(),
                                   new ASN1OctetString(),
                                   new ASN1OctetString(),
                                   new ASN1OctetString() });
    arrays.add(new ASN1Element[] { new ASN1Integer(1),
                                   new ASN1Null((byte) 0x42) });

    Object[][] objects = new Object[arrays.size()][];
    for (int i=0; i < arrays.size(); i++)
    {
      objects[i] = new Object[] { arrays.get(i) };
    }

    return objects;
  }



  /**
   * Tests the <CODE>decodeAsSequence</CODE> method.
   *
   * @param  elements  The set of ASN.1 elements to use in the tests.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(dataProvider = "elementArrays")
  public void testDecodeAsSequence(ASN1Element[] elements)
         throws Exception
  {
    ArrayList<ASN1Element> elementList = new ArrayList<ASN1Element>();
    if (elements == null)
    {
      elementList = null;
    }
    else
    {
      for (ASN1Element e : elements)
      {
        elementList.add(e);
      }
    }


    // First, make sure that we can decode actual sequence elements as sequence
    // elements, using both the standard type as well as a nonstandard type.
    ASN1Element e = new ASN1Sequence(elementList);
    ASN1Sequence sequenceElement = e.decodeAsSequence();
    if (elements == null)
    {
      assertEquals(sequenceElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(sequenceElement.elements(), elementList);
    }

    e = new ASN1Sequence((byte) 0x50, elementList);
    sequenceElement = e.decodeAsSequence();
    if (elements == null)
    {
      assertEquals(sequenceElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(sequenceElement.elements(), elementList);
    }


    // Next, make sure that we can decode a generic ASN.1 element as an octet
    // string element.
    e = new ASN1Element(ASN1Constants.UNIVERSAL_SEQUENCE_TYPE,
                        ASN1Element.encodeValue(elementList));
    sequenceElement = e.decodeAsSequence();
    if (elements == null)
    {
      assertEquals(sequenceElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(sequenceElement.elements(), elementList);
    }

    e = new ASN1Element((byte) 0x50, ASN1Element.encodeValue(elementList));
    sequenceElement = e.decodeAsSequence();
    if (elements == null)
    {
      assertEquals(sequenceElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(sequenceElement.elements(), elementList);
    }
  }



  /**
   * Tests the <CODE>decodeAsSet</CODE> method.
   *
   * @param  elements  The set of ASN.1 elements to use in the tests.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(dataProvider = "elementArrays")
  public void testDecodeAsSet(ASN1Element[] elements)
         throws Exception
  {
    ArrayList<ASN1Element> elementList = new ArrayList<ASN1Element>();
    if (elements == null)
    {
      elementList = null;
    }
    else
    {
      for (ASN1Element e : elements)
      {
        elementList.add(e);
      }
    }


    // First, make sure that we can decode actual set elements as set elements,
    // using both the standard type as well as a nonstandard type.
    ASN1Element e = new ASN1Set(elementList);
    ASN1Set setElement = e.decodeAsSet();
    if (elements == null)
    {
      assertEquals(setElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(setElement.elements(), elementList);
    }

    e = new ASN1Set((byte) 0x50, elementList);
    setElement = e.decodeAsSet();
    if (elements == null)
    {
      assertEquals(setElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(setElement.elements(), elementList);
    }


    // Next, make sure that we can decode a generic ASN.1 element as an octet
    // string element.
    e = new ASN1Element(ASN1Constants.UNIVERSAL_SET_TYPE,
                        ASN1Element.encodeValue(elementList));
    setElement = e.decodeAsSet();
    if (elements == null)
    {
      assertEquals(setElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(setElement.elements(), elementList);
    }

    e = new ASN1Element((byte) 0x50, ASN1Element.encodeValue(elementList));
    setElement = e.decodeAsSet();
    if (elements == null)
    {
      assertEquals(setElement.elements(), new ArrayList<ASN1Element>());
    }
    else
    {
      assertEquals(setElement.elements(), elementList);
    }
  }



  /**
   * Tests the <CODE>equals</CODE>, <CODE>equalsElement</CODE>, and
   * <CODE>equalsIgnoreType</CODE> methods.
   *
   * @param  value  The value to use in the test.
   */
  @Test(dataProvider = "testValues")
  public void testEquals(byte[] value)
  {
    ASN1Element controlElement = new ASN1Element((byte) 0x00, value);

    for (int i=0x00; i < 0xFF; i++)
    {
      ASN1Element e = new ASN1Element((byte) i, value);

      if (i == 0x00)
      {
        assertTrue(controlElement.equals(e));
        assertTrue(e.equals(controlElement));
        assertTrue(controlElement.equalsElement(e));
        assertTrue(e.equalsElement(controlElement));
      }
      else
      {
        assertFalse(controlElement.equals(e));
        assertFalse(e.equals(controlElement));
        assertFalse(controlElement.equalsElement(e));
        assertFalse(e.equalsElement(controlElement));
      }

      assertTrue(e.equals(e));
      assertTrue(e.equalsElement(e));
      assertTrue(e.equalsIgnoreType(e));
      assertTrue(e.equalsIgnoreType((ByteString) new ASN1OctetString(value)));
      assertTrue(controlElement.equalsIgnoreType(e));
      assertTrue(e.equalsIgnoreType(controlElement));
      assertFalse(e.equals(null));
      assertFalse(e.equals("notanelement"));
    }
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array argument with an
   * invalid element that is too short to be valid.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeShortElement1()
         throws Exception
  {
    ASN1Element.decode(new byte[1]);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array and two integer
   * arguments with an invalid element that is too short to be valid.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeShortElement2()
         throws Exception
  {
    ASN1Element.decode(new byte[1], 0, 1);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array argument with a null
   * element.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNull1()
         throws Exception
  {
    ASN1Element.decode(null);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array and two integer
   * arguments with a null element.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeNull2()
         throws Exception
  {
    ASN1Element.decode(null, 0, 0);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array argument with an
   * element indicating that it takes more than four bytes to describe the
   * length.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongLength1()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      (byte) 0x85,
      0x00,
      0x00,
      0x00,
      0x00,
      0x00
    };

    ASN1Element.decode(elementBytes);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array and two integer
   * arguments with an indicating that it takes more than four bytes to describe
   * the length.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLongLength2()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      (byte) 0x85,
      0x00,
      0x00,
      0x00,
      0x00,
      0x00
    };

    ASN1Element.decode(elementBytes, 0, elementBytes.length);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array argument with an
   * element that isn't long enough to fully decode the length.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTruncatedLength1()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      (byte) 0x82,
      0x00
   };

    ASN1Element.decode(elementBytes);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array and two integer
   * arguments with an element that isn't long enough to fully decode the
   * length.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeTruncatedLength2()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      (byte) 0x82,
      0x00
   };

    ASN1Element.decode(elementBytes, 0, elementBytes.length);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array argument with an
   * element whose decoded length doesn't match the number of bytes remaining.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLengthMismatch1()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      0x01,
      0x00,
      0x00
   };

    ASN1Element.decode(elementBytes);
  }



  /**
   * Tests the <CODE>decode</CODE> method taking an array and two integer
   * arguments with an element whose decoded length doesn't match the number of
   * bytes remaining.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeLengthMismatch2()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      0x01,
      0x00,
      0x00
   };

    ASN1Element.decode(elementBytes, 0, elementBytes.length);
  }



  /**
   * Tests the <CODE>decodeElements</CODE> method taking an array with a null
   * array.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeElementsNull()
         throws Exception
  {
    ASN1Element.decodeElements(null);
  }



  /**
   * Tests the <CODE>decodeElements</CODE> method taking an array with an array
   * containing an element with too many bytes used to describe the length.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeElementsLongLength()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      (byte) 0x85,
      0x00,
      0x00,
      0x00,
      0x00,
      0x00
    };

    ASN1Element.decodeElements(elementBytes);
  }



  /**
   * Tests the <CODE>decodeElements</CODE> method taking an array with an array
   * containing an element without enough bytes to fully decode the length.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { ASN1Exception.class })
  public void testDecodeElementsTruncatedLength()
         throws Exception
  {
    byte[] elementBytes =
    {
      0x04,
      (byte) 0x82,
      0x00,
    };

    ASN1Element.decodeElements(elementBytes);
  }



  /**
   * Tests the <CODE>equalsElement</CODE> method taking an array with a null
   * array.
   *
   * @throws  Exception  If the test failed unexpectedly.
   */
  @Test()
  public void testEqualsElementNull()
         throws Exception
  {
    ASN1Element e = new ASN1OctetString();
    assertFalse(e.equalsElement(null));
  }



  /**
   * Tests miscellaneous methods, including <CODE>toString</CODE> and
   * <CODE>getProtocolElementName</CODE> that don't fit in anywhere else.
   *
   * @param  value  The value to use in the test.
   */
  @Test(dataProvider = "testValues")
  public void testMiscellaneous(byte[] value)
  {
    for (int i=0x00; i < 0xFF; i++)
    {
      byte type = (byte) i;
      ASN1Element e = new ASN1Element(type, value);
      e.toString();
      e.toString(new StringBuilder());
      e.toString(new StringBuilder(), 1);
      assertEquals(e.getProtocolElementName(), "ASN.1");
    }
  }
}

