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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;
import org.opends.messages.Message;



import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import org.opends.server.api.ProtocolElement;
import org.opends.server.types.ByteString;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with generic ASN.1 elements.  Subclasses may provide more specific
 * functionality for individual element types.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class ASN1Element
       implements ProtocolElement, Serializable
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -6085322427222358963L;



  // The BER type for this element.
  private byte type;

  // The encoded value for this element.
  private byte[] value;



  /**
   * Creates a new ASN.1 element with the specified type and no value.
   *
   * @param  type  The BER type for this ASN.1 element.
   */
  public ASN1Element(byte type)
  {
    this.type  = type;
    this.value = NO_VALUE;
  }



  /**
   * Creates a new ASN.1 element with the specified type and value.
   *
   * @param  type   The BER type for this ASN.1 element.
   * @param  value  The encoded value for this ASN.1 element.
   */
  public ASN1Element(byte type, byte[] value)
  {
    this.type  = type;

    if (value == null)
    {
      this.value = NO_VALUE;
    }
    else
    {
      this.value = value;
    }
  }



  /**
   * Retrieves the BER type for this ASN.1 element.
   *
   * @return  The BER type for this ASN.1 element.
   */
  public final byte getType()
  {
    return type;
  }



  /**
   * Specifies the BER type for this ASN.1 element.
   *
   * @param  type  The BER type for this ASN.1 element.
   */
  public final void setType(byte type)
  {
    this.type = type;
  }



  /**
   * Indicates whether this ASN.1 element is in the universal class.
   *
   * @return  <CODE>true</CODE> if this ASN.1 element is in the universal class,
   *          or <CODE>false</CODE> if not.
   */
  public final boolean isUniversal()
  {
    return ((type & TYPE_MASK_ALL_BUT_CLASS) == TYPE_MASK_UNIVERSAL);
  }



  /**
   * Indicates whether this ASN.1 element is in the application-specific class.
   *
   * @return  <CODE>true</CODE> if this ASN.1 element is in the
   *          application-specific class, or <CODE>false</CODE> if not.
   */
  public final boolean isApplicationSpecific()
  {
    return ((type & TYPE_MASK_ALL_BUT_CLASS) == TYPE_MASK_APPLICATION);
  }



  /**
   * Indicates whether this ASN.1 element is in the context-specific class.
   *
   * @return  <CODE>true</CODE> if this ASN.1 element is in the context-specific
   *          class, or <CODE>false</CODE> if not.
   */
  public final boolean isContextSpecific()
  {
    return ((type & TYPE_MASK_ALL_BUT_CLASS) == TYPE_MASK_CONTEXT);
  }



  /**
   * Indicates whether this ASN.1 element is in the private class.
   *
   * @return  <CODE>true</CODE> if this ASN.1 element is in the private class,
   *          or <CODE>false</CODE> if not.
   */
  public final boolean isPrivate()
  {
    return ((type & TYPE_MASK_ALL_BUT_CLASS) == TYPE_MASK_PRIVATE);
  }



  /**
   * Indicates whether this ASN.1 element has a primitive value.
   *
   * @return  <CODE>true</CODE> if this ASN.1 element has a primitive value, or
   *          <CODE>false</CODE> if it is constructed.
   */
  public final boolean isPrimitive()
  {
    return ((type & TYPE_MASK_ALL_BUT_PC) == TYPE_MASK_PRIMITIVE);
  }



  /**
   * Indicates whether this ASN.1 element has a constructed value.
   *
   * @return  <CODE>true</CODE> if this ASN.1 element has a constructed value,
   *          or <CODE>false</CODE> if it is primitive.
   */
  public final boolean isConstructed()
  {
    return ((type & TYPE_MASK_ALL_BUT_PC) == TYPE_MASK_CONSTRUCTED);
  }



  /**
   * Retrieves the encoded value for this ASN.1 element.
   *
   * @return  The encoded value for this ASN.1 element.
   */
  public final byte[] value()
  {
    return value;
  }



  /**
   * Specifies the encoded value for this ASN.1 element.
   *
   * @param  value  The encoded value for this ASN.1 element.
   *
   * @throws  ASN1Exception  If the provided value is not appropriate for this
   *                         type of ASN.1 element.
   */
  public void setValue(byte[] value)
         throws ASN1Exception
  {
    if (value == null)
    {
      this.value = NO_VALUE;
    }
    else
    {
      this.value = value;
    }
  }



  /**
   * Specifies the value to use for this ASN.1 element, but without performing
   * any validity checks.  This should only be used by subclasses and they must
   * ensure that it is non-null and conforms to the appropriate requirements of
   * the underlying type.
   *
   * @param  value  The encoded value for this ASN.1 element.
   */
  protected final void setValueInternal(byte[] value)
  {
    this.value = value;
  }



  /**
   * Encodes the provided value for use as the length of an ASN.1 element.
   *
   * @param  length  The length to encode for use in an ASN.1 element.
   *
   * @return  The byte array containing the encoded length.
   */
  public static byte[] encodeLength(int length)
  {
    if (length < 128)
    {
      return new byte[] { (byte) length };
    }

    if ((length & 0x000000FF) == length)
    {
      return new byte[]
      {
        (byte) 0x81,
        (byte) (length & 0xFF)
      };
    }
    else if ((length & 0x0000FFFF) == length)
    {
      return new byte[]
      {
        (byte) 0x82,
        (byte) ((length >> 8) & 0xFF),
        (byte) (length & 0xFF)
      };
    }
    else if ((length & 0x00FFFFFF) == length)
    {
      return new byte[]
      {
        (byte) 0x83,
        (byte) ((length >> 16) & 0xFF),
        (byte) ((length >>  8) & 0xFF),
        (byte) (length & 0xFF)
      };
    }
    else
    {
      return new byte[]
      {
        (byte) 0x84,
        (byte) ((length >> 24) & 0xFF),
        (byte) ((length >> 16) & 0xFF),
        (byte) ((length >>  8) & 0xFF),
        (byte) (length & 0xFF)
      };
    }
  }



  /**
   * Encodes this ASN.1 element to a byte array.
   *
   * @return  The byte array containing the encoded ASN.1 element.
   */
  public final byte[] encode()
  {
    if (value.length == 0)
    {
      return new byte[] { type, 0x00 };
    }
    else if (value.length < 128)
    {
      byte[] encodedElement = new byte[value.length + 2];

      encodedElement[0] = type;
      encodedElement[1] = (byte) value.length;
      System.arraycopy(value, 0, encodedElement, 2, value.length);

      return encodedElement;
    }
    else
    {
      byte[] encodedLength  = encodeLength(value.length);
      byte[] encodedElement = new byte[1 + value.length + encodedLength.length];

      encodedElement[0] = type;
      System.arraycopy(encodedLength, 0, encodedElement, 1,
                       encodedLength.length);
      System.arraycopy(value, 0, encodedElement, 1+encodedLength.length,
                       value.length);

      return encodedElement;
    }
  }



  /**
   * Retrieves a byte array containing the encoded representation of the
   * provided boolean value.
   *
   * @param  booleanValue  The boolean value to encode.
   *
   * @return  A byte array containing the encoded representation of the provided
   *          boolean value.
   */
  public static byte[] encodeValue(boolean booleanValue)
  {
    return (booleanValue ? BOOLEAN_VALUE_TRUE : BOOLEAN_VALUE_FALSE);
  }



  /**
   * Retrieves a byte array containing the encoded representation of the
   * provided integer value.
   *
   * @param  intValue  The integer value to encode.
   *
   * @return  A byte array containing the encoded representation of the provided
   *          integer value.
   */
  public static byte[] encodeValue(int intValue)
  {
    if ((intValue & 0x0000007F) == intValue)
    {
      return new byte[]
      {
        (byte) (intValue & 0xFF)
      };
    }
    else if ((intValue & 0x00007FFF) == intValue)
    {
      return new byte[]
      {
        (byte) ((intValue >> 8) & 0xFF),
        (byte) (intValue & 0xFF)
      };
    }
    else if ((intValue & 0x007FFFFF) == intValue)
    {
      return new byte[]
      {
        (byte) ((intValue >> 16) & 0xFF),
        (byte) ((intValue >>  8) & 0xFF),
        (byte) (intValue & 0xFF)
      };
    }
    else
    {
      return new byte[]
      {
        (byte) ((intValue >> 24) & 0xFF),
        (byte) ((intValue >> 16) & 0xFF),
        (byte) ((intValue >>  8) & 0xFF),
        (byte) (intValue & 0xFF)
      };
    }
  }



  /**
   * Retrieves a byte array containing the encoded representation of the
   * provided long value.
   *
   * @param  longValue  The long value to encode.
   *
   * @return  A byte array containing the encoded representation of the provided
   *          long value.
   */
  public static byte[] encodeLongValue(long longValue)
  {
    if ((longValue & 0x000000000000007FL) == longValue)
    {
      return new byte[]
      {
        (byte) (longValue & 0xFF)
      };
    }
    else if ((longValue & 0x0000000000007FFFL) == longValue)
    {
      return new byte[]
      {
        (byte) ((longValue >> 8) & 0xFF),
        (byte) (longValue & 0xFF)
      };
    }
    else if ((longValue & 0x00000000007FFFFFL) == longValue)
    {
      return new byte[]
      {
        (byte) ((longValue >> 16) & 0xFF),
        (byte) ((longValue >>  8) & 0xFF),
        (byte) (longValue & 0xFF)
      };
    }
    else if ((longValue & 0x000000007FFFFFFFL) == longValue)
    {
      return new byte[]
      {
        (byte) ((longValue >> 24) & 0xFF),
        (byte) ((longValue >> 16) & 0xFF),
        (byte) ((longValue >>  8) & 0xFF),
        (byte) (longValue & 0xFF)
      };
    }
    else if ((longValue & 0x0000007FFFFFFFFFL) == longValue)
    {
      return new byte[]
      {
        (byte) ((longValue >> 32) & 0xFF),
        (byte) ((longValue >> 24) & 0xFF),
        (byte) ((longValue >> 16) & 0xFF),
        (byte) ((longValue >>  8) & 0xFF),
        (byte) (longValue & 0xFF)
      };
    }
    else if ((longValue & 0x00007FFFFFFFFFFFL) == longValue)
    {
      return new byte[]
      {
        (byte) ((longValue >> 40) & 0xFF),
        (byte) ((longValue >> 32) & 0xFF),
        (byte) ((longValue >> 24) & 0xFF),
        (byte) ((longValue >> 16) & 0xFF),
        (byte) ((longValue >>  8) & 0xFF),
        (byte) (longValue & 0xFF)
      };
    }
    else if ((longValue & 0x007FFFFFFFFFFFFFL) == longValue)
    {
      return new byte[]
      {
        (byte) ((longValue >> 48) & 0xFF),
        (byte) ((longValue >> 40) & 0xFF),
        (byte) ((longValue >> 32) & 0xFF),
        (byte) ((longValue >> 24) & 0xFF),
        (byte) ((longValue >> 16) & 0xFF),
        (byte) ((longValue >>  8) & 0xFF),
        (byte) (longValue & 0xFF)
      };
    }
    else
    {
      return new byte[]
      {
        (byte) ((longValue >> 56) & 0xFF),
        (byte) ((longValue >> 48) & 0xFF),
        (byte) ((longValue >> 40) & 0xFF),
        (byte) ((longValue >> 32) & 0xFF),
        (byte) ((longValue >> 24) & 0xFF),
        (byte) ((longValue >> 16) & 0xFF),
        (byte) ((longValue >>  8) & 0xFF),
        (byte) (longValue & 0xFF)
      };
    }
  }



  /**
   * Retrieves a byte array containing the encoded representation of the
   * provided set of ASN.1 elements.
   *
   * @param  elements  The set of ASN.1 elements to encode into the value.
   *
   * @return  A byte array containing the encoded representation of the
   *          provided set of ASN.1 elements.
   */
  public static byte[] encodeValue(ArrayList<ASN1Element> elements)
  {
    if (elements == null)
    {
      return NO_VALUE;
    }


    int totalLength = 0;
    byte[][] encodedElements = new byte[elements.size()][];
    for (int i=0; i < encodedElements.length; i++)
    {
      encodedElements[i] = elements.get(i).encode();
      totalLength += encodedElements[i].length;
    }

    byte[] encodedValue = new byte[totalLength];
    int startPos = 0;
    for (byte[] b : encodedElements)
    {
      System.arraycopy(b, 0, encodedValue, startPos, b.length);
      startPos += b.length;
    }

    return encodedValue;
  }



  /**
   * Decodes the contents of the provided byte array as an ASN.1 element.
   *
   * @param  encodedElement  The byte array containing the ASN.1 element to
   *                         decode.
   *
   * @return  The decoded ASN.1 element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode the
   *                         byte array as an ASN.1 element.
   */
  public static ASN1Element decode(byte[] encodedElement)
         throws ASN1Exception
  {
    // First make sure that the array is not null and long enough to contain
    // a valid ASN.1 element.
    if (encodedElement == null)
    {
      Message message = ERR_ASN1_NULL_ELEMENT.get();
      throw new ASN1Exception(message);
    }
    else if (encodedElement.length < 2)
    {
      Message message = ERR_ASN1_SHORT_ELEMENT.get(encodedElement.length);
      throw new ASN1Exception(message);
    }


    // Next, decode the length.  This allows multi-byte lengths with up to four
    // bytes used to indicate how many bytes are in the length.
    byte type = encodedElement[0];
    int length = (encodedElement[1] & 0x7F);
    int valueStartPos = 2;
    if (length != encodedElement[1])
    {
      int numLengthBytes = length;
      if (numLengthBytes > 4)
      {
        Message message = ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(numLengthBytes);
        throw new ASN1Exception(message);
      }
      else if (encodedElement.length < (2 + numLengthBytes))
      {
        Message message = ERR_ASN1_TRUNCATED_LENGTH.get(numLengthBytes);
        throw new ASN1Exception(message);
      }

      length = 0x00;
      valueStartPos = 2 + numLengthBytes;
      for (int i=0; i < numLengthBytes; i++)
      {
        length = (length << 8) | (encodedElement[i+2] & 0xFF);
      }
    }


    // Make sure that the number of bytes left is equal to the number of bytes
    // in the value.
    if ((encodedElement.length - valueStartPos) != length)
    {
      Message message = ERR_ASN1_LENGTH_MISMATCH.get(
          length, (encodedElement.length - valueStartPos));
      throw new ASN1Exception(message);
    }


    // Copy the value and construct the element to return.
    byte[] value = new byte[length];
    System.arraycopy(encodedElement, valueStartPos, value, 0, length);
    return new ASN1Element(type, value);
  }



  /**
   * Decodes the specified portion of the provided byte array as an ASN.1
   * element.
   *
   * @param  encodedElement  The byte array containing the ASN.1 element to
   *                         decode.
   * @param  startPos        The position in the provided array at which to
   *                         start decoding.
   * @param  length          The number of bytes in the set of data to decode as
   *                         an ASN.1 element.
   *
   * @return  The decoded ASN.1 element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode the
   *                         byte array as an ASN.1 element.
   */
  public static ASN1Element decode(byte[] encodedElement, int startPos,
                                   int length)
         throws ASN1Exception
  {
    // First make sure that the array is not null and long enough to contain
    // a valid ASN.1 element.
    if (encodedElement == null)
    {
      Message message = ERR_ASN1_NULL_ELEMENT.get();
      throw new ASN1Exception(message);
    }
    else if ((startPos < 0) || (startPos+length > encodedElement.length) ||
             (length < 2))
    {
      Message message = ERR_ASN1_SHORT_ELEMENT.get(encodedElement.length);
      throw new ASN1Exception(message);
    }


    // Next, decode the length.  This allows multi-byte lengths with up to four
    // bytes used to indicate how many bytes are in the length.
    byte type = encodedElement[startPos];
    int elementLength = (encodedElement[startPos+1] & 0x7F);
    int valueStartPos = startPos + 2;
    if (elementLength != encodedElement[startPos+1])
    {
      int numLengthBytes = elementLength;
      if (numLengthBytes > 4)
      {
        Message message = ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(numLengthBytes);
        throw new ASN1Exception(message);
      }
      else if (startPos+length < (2 + numLengthBytes))
      {
        Message message = ERR_ASN1_TRUNCATED_LENGTH.get(numLengthBytes);
        throw new ASN1Exception(message);
      }

      elementLength = 0x00;
      valueStartPos = startPos + 2 + numLengthBytes;
      for (int i=0; i < numLengthBytes; i++)
      {
        elementLength = (elementLength << 8) | (encodedElement[i+2] & 0xFF);
      }
    }


    // Make sure that the number of bytes left is equal to the number of bytes
    // in the value.
    if ((startPos+length - valueStartPos) != elementLength)
    {
      Message message = ERR_ASN1_LENGTH_MISMATCH.get(
          elementLength, (startPos+length - valueStartPos));
      throw new ASN1Exception(message);
    }


    // Copy the value and construct the element to return.
    byte[] value = new byte[elementLength];
    System.arraycopy(encodedElement, valueStartPos, value, 0, elementLength);
    return new ASN1Element(type, value);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 Boolean element.
   *
   * @return  The ASN.1 Boolean element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 Boolean element.
   */
  public final ASN1Boolean decodeAsBoolean()
         throws ASN1Exception
  {
    return ASN1Boolean.decodeAsBoolean(this);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 enumerated element.
   *
   * @return  The ASN.1 enumerated element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 enumerated element.
   */
  public final ASN1Enumerated decodeAsEnumerated()
         throws ASN1Exception
  {
    return ASN1Enumerated.decodeAsEnumerated(this);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 integer element.
   *
   * @return  The ASN.1 integer element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 integer element.
   */
  public final ASN1Integer decodeAsInteger()
         throws ASN1Exception
  {
    return ASN1Integer.decodeAsInteger(this);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 long element.
   *
   * @return  The ASN.1 long element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 long element.
   */
  public final ASN1Long decodeAsLong()
         throws ASN1Exception
  {
    return ASN1Long.decodeAsLong(this);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 null element.
   *
   * @return  The ASN.1 null element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 null element.
   */
  public final ASN1Null decodeAsNull()
         throws ASN1Exception
  {
    return ASN1Null.decodeAsNull(this);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 octet string element.
   *
   * @return  The ASN.1 octet string element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 octet string element.
   */
  public final ASN1OctetString decodeAsOctetString()
         throws ASN1Exception
  {
    return ASN1OctetString.decodeAsOctetString(this);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 sequence element.
   *
   * @return  The ASN.1 sequence element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 sequence element.
   */
  public final ASN1Sequence decodeAsSequence()
         throws ASN1Exception
  {
    return ASN1Sequence.decodeAsSequence(this);
  }



  /**
   * Decodes this ASN.1 element as an ASN.1 set element.
   *
   * @return  The ASN.1 set element decoded from this element.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode this
   *                         element as an ASN.1 set element.
   */
  public final ASN1Set decodeAsSet()
         throws ASN1Exception
  {
    return ASN1Set.decodeAsSet(this);
  }



  /**
   * Decodes the provided byte array as a collection of ASN.1 elements as would
   * be found in the value of a sequence or set.
   *
   * @param  encodedElements  The byte array containing the data to decode.
   *
   * @return  The set of decoded ASN.1 elements.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode the
   *                         set of ASN.1 elements from the provided byte array.
   */
  public static ArrayList<ASN1Element> decodeElements(byte[] encodedElements)
         throws ASN1Exception
  {
    // Make sure that the element array is not null.
    if (encodedElements == null)
    {
      Message message = ERR_ASN1_ELEMENT_SET_NULL.get();
      throw new ASN1Exception(message);
    }


    // Iterate through the array and keep reading elements until the end is
    // reached.
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>();
    int startPos = 0;
    while (startPos < encodedElements.length)
    {
      byte type = encodedElements[startPos++];
      if (startPos >= encodedElements.length)
      {
        Message message = ERR_ASN1_ELEMENT_SET_NO_LENGTH.get();
        throw new ASN1Exception(message);
      }


      byte firstLengthByte = encodedElements[startPos++];
      int length = (byte) (firstLengthByte & 0x7F);
      if (length != firstLengthByte)
      {
        int numLengthBytes = length;
        if (numLengthBytes > 4)
        {
          Message message =
              ERR_ASN1_ELEMENT_SET_INVALID_NUM_LENGTH_BYTES.get(numLengthBytes);
          throw new ASN1Exception(message);
        }

        if (numLengthBytes > encodedElements.length - startPos)
        {
          Message message =
              ERR_ASN1_ELEMENT_SET_TRUNCATED_LENGTH.get(numLengthBytes);
          throw new ASN1Exception(message);
        }

        length = 0x00;
        for (int i=0; i < numLengthBytes; i++)
        {
          length = (length << 8) | (encodedElements[startPos++] & 0xFF);
        }
      }


      // Make sure that there are at least enough bytes to hold the value.
      if (length > encodedElements.length - startPos)
      {
        Message message = ERR_ASN1_ELEMENT_SET_TRUNCATED_VALUE.get(
            length, (encodedElements.length-startPos));
        throw new ASN1Exception(message);
      }


      // Create the element and add it to the list.
      byte[] value = new byte[length];
      System.arraycopy(encodedElements, startPos, value, 0, length);
      elements.add(new ASN1Element(type, value));
      startPos += length;
    }


    return elements;
  }



  /**
   * Retrieves the name of the protocol associated with this protocol element.
   *
   * @return  The name of the protocol associated with this protocol element.
   */
  public final String getProtocolElementName()
  {
    return "ASN.1";
  }



  /**
   * Indicates whether the provided object is equal to this ASN.1 element.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is an ASN.1 element that
   *          is equal to this element, or <CODE>false</CODE> if not.  The
   *          object will be considered equal if it is an ASN.1 element (or a
   *          subclass) with the same type and encoded value.
   */
  public final boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof ASN1Element)))
    {
      return false;
    }

    ASN1Element e = (ASN1Element) o;
    return ((type == e.type) && Arrays.equals(value, e.value));
  }



  /**
   * Indicates whether the provided ASN.1 element has a value that is equal to
   * the value of this ASN.1 element.
   *
   * @param  element  The ASN.1 element whose value should be compared against
   *                  the value of this element.
   *
   * @return  <CODE>true</CODE> if the values of the elements are equal, or
   *          <CODE>false</CODE> if not.
   */
  public final boolean equalsIgnoreType(ASN1Element element)
  {
    return Arrays.equals(value, element.value);
  }



  /**
   * Indicates whether the provided byte string has a value that is equal to
   * the value of this ASN.1 element.
   *
   * @param  byteString  The byte string whose value should be compared against
   *                     the value of this element.
   *
   * @return  <CODE>true</CODE> if the values are equal, or <CODE>false</CODE>
   *          if not.
   */
  public final boolean equalsIgnoreType(ByteString byteString)
  {
    return Arrays.equals(value, byteString.value());
  }



  /**
   * Indicates whether the provided ASN.1 element is equal to this element.
   *
   * @param  e  The ASN.1 element for which to make the determination.
   *
   * @return  <CODE>true</CODE> ASN.1 element is equal to this element,
   *          or <CODE>false</CODE> if not.  The elements will be considered
   *          equal if they have the same type and encoded value.
   */
  public final boolean equalsElement(ASN1Element e)
  {
    if (this == e)
    {
      return true;
    }

    if (e == null)
    {
      return false;
    }

    return ((type == e.type) && Arrays.equals(value, e.value));
  }



  /**
   * Retrieves the hash code for this ASN.1 element.  It will be constructed
   * from the sum of the type and up to the first twenty bytes of the value.
   *
   * @return  The hash code for this ASN.1 element.
   */
  public final int hashCode()
  {
    int hashCode = type;
    int length = Math.min(20, value.length);
    for (int i=0; i < length; i++)
    {
      hashCode += value[i];
    }

    return hashCode;
  }



  /**
   * Retrieves a string representation of this ASN.1 element.
   *
   * @return  A string representation of this ASN.1 element.
   */
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this ASN.1 element to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ASN1Element(type=");
    buffer.append(byteToHex(type));
    buffer.append(", length=");
    buffer.append(value.length);
    buffer.append(")");
  }



  /**
   * Appends a string representation of this protocol element to the provided
   * buffer.
   *
   * @param  buffer  The buffer into which the string representation should be
   *                 written.
   * @param  indent  The number of spaces that should be used to indent the
   *                 resulting string representation.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("ASN.1 Element");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  BER Type:  ");
    buffer.append(byteToHex(type));
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Value (");
    buffer.append(value.length);
    buffer.append(" bytes)");
    buffer.append(EOL);

    byteArrayToHexPlusAscii(buffer, value, indent+2);
  }
}

