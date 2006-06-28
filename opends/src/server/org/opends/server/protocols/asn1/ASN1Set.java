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
import java.util.Iterator;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with ASN.1 set elements.
 */
public class ASN1Set
       extends ASN1Element
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.asn1.ASN1Set";



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = 2197633272056656073L;



  // The set of ASN.1 elements contained in this set.
  private ArrayList<ASN1Element> elements;




  /**
   * Creates a new ASN.1 set element with the default type no value.
   */
  public ASN1Set()
  {
    super(UNIVERSAL_SET_TYPE);

    assert debugConstructor(CLASS_NAME);

    this.elements = new ArrayList<ASN1Element>();
  }



  /**
   * Creates a new ASN.1 set element with the specified type and no value.
   *
   * @param  type  The BER type for this ASN.1 set.
   */
  public ASN1Set(byte type)
  {
    super(type);

    assert debugConstructor(CLASS_NAME, byteToHex(type));

    this.elements = new ArrayList<ASN1Element>();
  }



  /**
   * Creates a new ASN.1 set with the default type and the provided set of
   * elements.
   *
   * @param  elements  The set of elements to include in this set.
   */
  public ASN1Set(ArrayList<ASN1Element> elements)
  {
    super(UNIVERSAL_SET_TYPE, encodeValue(elements));

    assert debugConstructor(CLASS_NAME, String.valueOf(elements));

    if (elements == null)
    {
      this.elements = new ArrayList<ASN1Element>();
    }
    else
    {
      this.elements = elements;
    }
  }



  /**
   * Creates a new ASN.1 set with the specified type and the provided set of
   * elements.
   *
   * @param  type      The BER type for this set.
   * @param  elements  The set of elements to include in this set.
   */
  public ASN1Set(byte type, ArrayList<ASN1Element> elements)
  {
    super(type, encodeValue(elements));

    assert debugConstructor(CLASS_NAME, byteToHex(type),
                            String.valueOf(elements));

    if (elements == null)
    {
      this.elements = new ArrayList<ASN1Element>();
    }
    else
    {
      this.elements = elements;
    }
  }



  /**
   * Creates a new ASN.1 set with the specified type and value and the
   * provided set of elements.
   *
   * @param  type      The BER type for this set.
   * @param  value     The encoded value for this set.
   * @param  elements  The set of elements to include in this set.
   */
  private ASN1Set(byte type, byte[] value, ArrayList<ASN1Element> elements)
  {
    super(type, value);

    assert debugConstructor(CLASS_NAME, byteToHex(type), bytesToHex(value),
                            String.valueOf(elements));

    if (elements == null)
    {
      this.elements = new ArrayList<ASN1Element>();
    }
    else
    {
      this.elements = elements;
    }
  }



  /**
   * Retrieves the set of elements contained in this ASN.1 set.  The returned
   * list must not be modified by the caller.
   *
   * @return  The set of elements contained in this ASN.1 set.
   */
  public ArrayList<ASN1Element> elements()
  {
    assert debugEnter(CLASS_NAME, "elements");

    return elements;
  }



  /**
   * Specifies the set of elements for this ASN.1 set.
   *
   * @param  elements  The set of elements for this ASN.1 set.
   */
  public void setElements(ArrayList<ASN1Element> elements)
  {
    assert debugEnter(CLASS_NAME, "setValue", String.valueOf(elements));

    if (elements == null)
    {
      this.elements.clear();
      setValueInternal(NO_VALUE);
    }
    else
    {
      this.elements = elements;
      setValueInternal(encodeValue(elements));
    }
  }



  /**
   * Specifies the value for this ASN.1 set element.
   *
   * @param  value  The encoded value for this ASN.1 set element.
   *
   * @throws  ASN1Exception  If the provided array is null or cannot be decoded
   *                         as a set of ASN.1 elements.
   */
  public void setValue(byte[] value)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "setValue", bytesToHex(value));

    if (value == null)
    {
      int    msgID   = MSGID_ASN1_SET_SET_VALUE_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    elements = decodeElements(value);
    setValueInternal(value);
  }



  /**
   * Decodes the provided ASN.1 element as a set element.
   *
   * @param  element  The ASN.1 element to decode as a set element.
   *
   * @return  The decoded ASN.1 set element.
   *
   * @throws  ASN1Exception  If the provided ASN.1 element cannot be decoded as
   *                         a set element.
   */
  public static ASN1Set decodeAsSet(ASN1Element element)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "decodeAsSet", String.valueOf(element));

    if (element == null)
    {
      int    msgID   = MSGID_ASN1_SET_DECODE_ELEMENT_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    byte[] value = element.value();
    ArrayList<ASN1Element> elements = decodeElements(value);
    return new ASN1Set(element.getType(), value, elements);
  }



  /**
   * Decodes the provided byte array as an ASN.1 set element.
   *
   * @param  encodedElement  The byte array to decode as an ASN.1 set element.
   *
   * @return  The decoded ASN.1 set element.
   *
   * @throws  ASN1Exception  If the provided byte array cannot be decoded as an
   *                         ASN.1 set element.
   */
  public static ASN1Set decodeAsSet(byte[] encodedElement)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "decodeAsSet",
                      bytesToHex(encodedElement));

    // First make sure that the array is not null and long enough to contain
    // a valid ASN.1 set element.
    if (encodedElement == null)
    {
      int    msgID   = MSGID_ASN1_SET_DECODE_ARRAY_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    if (encodedElement.length < 2)
    {
      int    msgID   = MSGID_ASN1_SHORT_ELEMENT;
      String message = getMessage(msgID, encodedElement.length);
      throw new ASN1Exception(msgID, message);
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
        int    msgID   = MSGID_ASN1_INVALID_NUM_LENGTH_BYTES;
        String message = getMessage(msgID, numLengthBytes);
        throw new ASN1Exception(msgID, message);
      }
      else if (encodedElement.length < (2 + numLengthBytes))
      {
        int    msgID   = MSGID_ASN1_TRUNCATED_LENGTH;
        String message = getMessage(msgID, numLengthBytes);
        throw new ASN1Exception(msgID, message);
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
      int    msgID   = MSGID_ASN1_LENGTH_MISMATCH;
      String message = getMessage(msgID, length,
                                  (encodedElement.length - valueStartPos));
      throw new ASN1Exception(msgID, message);
    }


    // Copy the value, decode the elements it contains, and construct the set to
    // return.
    byte[] value = new byte[length];
    System.arraycopy(encodedElement, valueStartPos, value, 0, length);
    ArrayList<ASN1Element> elements = decodeElements(value);
    return new ASN1Set(type, value, elements);
  }



  /**
   * Appends a string representation of this ASN.1 set element to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("ASN1Set(type=");
    buffer.append(byteToHex(getType()));
    buffer.append(", values={ ");

    if (! elements.isEmpty())
    {
      Iterator<ASN1Element> iterator = elements.iterator();

      iterator.next().toString(buffer);

      while (iterator.hasNext())
      {
        buffer.append(", ");
        iterator.next().toString(buffer);
      }
    }

    buffer.append(" })");
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
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder",
                      String.valueOf(indent));

    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("ASN.1 Set");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  BER Type:  ");
    buffer.append(byteToHex(getType()));
    buffer.append(EOL);

    if (! elements.isEmpty())
    {
      buffer.append(indentBuf);
      buffer.append("  Decoded Values:");
      buffer.append(EOL);

      Iterator<ASN1Element> iterator = elements.iterator();

      buffer.append(indentBuf);
      buffer.append("  ");
      iterator.next().toString(buffer);
      buffer.append(EOL);

      while (iterator.hasNext())
      {
        buffer.append(indentBuf);
        buffer.append("  ");
        iterator.next().toString(buffer);
        buffer.append(EOL);
      }
    }


    buffer.append(indentBuf);
    buffer.append("  Value:  ");
    buffer.append(EOL);
    byteArrayToHexPlusAscii(buffer, value(), indent+2);
  }
}

