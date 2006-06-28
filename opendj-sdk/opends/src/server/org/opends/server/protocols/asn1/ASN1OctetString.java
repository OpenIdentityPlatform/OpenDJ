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



import org.opends.server.types.ByteString;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with ASN.1 octet string elements.
 */
public class ASN1OctetString
       extends ASN1Element
       implements ByteString
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.asn1.ASN1OctetString";



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -6101268916754431502L;



  // The string value for this element.  It may be null due to lazy
  // initialization.
  private String stringValue;



  /**
   * Creates a new ASN.1 octet string element with the default type and no
   * value.
   */
  public ASN1OctetString()
  {
    super(UNIVERSAL_OCTET_STRING_TYPE);

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Creates a new ASN.1 octet string element with the specified type and no
   * value.
   *
   * @param  type  The BER type for this ASN.1 octet string element.
   */
  public ASN1OctetString(byte type)
  {
    super(type);

    assert debugConstructor(CLASS_NAME, byteToHex(type));
  }



  /**
   * Creates a new ASN.1 octet string element with the default type and the
   * provided value.
   *
   * @param  value  The value for this ASN.1 octet string element.
   */
  public ASN1OctetString(byte[] value)
  {
    super(UNIVERSAL_OCTET_STRING_TYPE, value);

    assert debugConstructor(CLASS_NAME, bytesToHex(value));

    this.stringValue = null;
  }



  /**
   * Creates a new ASN.1 octet string element with the default type and the
   * provided value.
   *
   * @param  stringValue  The value for this ASN.1 octet string element as a
   *                      string.
   */
  public ASN1OctetString(String stringValue)
  {
    super(UNIVERSAL_OCTET_STRING_TYPE, getBytes(stringValue));

    assert debugConstructor(CLASS_NAME, String.valueOf(stringValue));

    this.stringValue = stringValue;
  }



  /**
   * Creates a new ASN.1 octet string element with the specified type and the
   * provided value.
   *
   * @param  type   The BER type for this ASN.1 octet string element.
   * @param  value  The value for this ASN.1 octet string element.
   */
  public ASN1OctetString(byte type, byte[] value)
  {
    super(type, value);

    assert debugConstructor(CLASS_NAME, byteToHex(type), bytesToHex(value));

    this.stringValue = null;
  }



  /**
   * Creates a new ASN.1 octet string element with the specified type and the
   * provided value.
   *
   * @param  type         The BER type for this ASN.1 octet string element.
   * @param  stringValue  The value for this ASN.1 octet string element as a
   *                      string.
   */
  public ASN1OctetString(byte type, String stringValue)
  {
    super(type, getBytes(stringValue));

    assert debugConstructor(CLASS_NAME, byteToHex(type),
                            String.valueOf(stringValue));

    this.stringValue = stringValue;
  }



  /**
   * Retrieves the string representation of the value for this ASN.1 octet
   * string element.  The behavior of this method when the bytes are not
   * valid in the UTF-8 charset is unspecified.  In particular the behavior for
   * binary values is unspecified.
   *
   * @return  The string representation of the value for this ASN.1 octet string
   *          element.
   */
  public String stringValue()
  {
    assert debugEnter(CLASS_NAME, "stringValue");

    if (stringValue == null)
    {
/*
      // This code could be used to explicitly detect and handle binary values.
      Charset charset = Charset.forName("UTF-8");
      CharsetDecoder decoder = charset.newDecoder();
      ByteBuffer bb = ByteBuffer.wrap(value());
      try
      {
        CharBuffer cb = decoder.decode(bb);
        stringValue = cb.toString();
      }
      catch (CharacterCodingException e)
      {
        // Handle binary values here.
        return "[Binary]";
      }
*/
      try
      {
        stringValue = new String(value(), "UTF-8");
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "stringValue", e);

        stringValue = new String(value());
      }
    }

    return stringValue;
  }



  /**
   * Appends a string representation of the value for this ASN.1 octet string
   * element to the provided buffer.
   *
   * @param  buffer  The buffer to which the string representation should be
   *                 appended.
   */
  public void stringValue(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "stringValue", "java.lang.StringBuilder");

    if (stringValue != null)
    {
      buffer.append(stringValue);
      return;
    }

    byte[] value  = value();
    int    length = value.length;

    for (int i=0; i < length; i++)
    {
      if ((value[i] & 0x7F) == value[i])
      {
        buffer.append((char) value[i]);
      }
      else
      {
        buffer.append(new String(value, i, (length-i)));
      }
    }
  }



  /**
   * Specifies the string value for this ASN.1 octet string element.
   *
   * @param  stringValue  The string value for this ASN.1 octet string element.
   */
  public void setValue(String stringValue)
  {
    assert debugEnter(CLASS_NAME, "setValue", String.valueOf(stringValue));

    if (stringValue == null)
    {
      this.stringValue = null;
      setValueInternal(new byte[0]);
    }
    else
    {
      this.stringValue = stringValue;
      setValueInternal(getBytes(stringValue));
    }
  }



  /**
   * Specifies the value for this ASN.1 octet string element.
   *
   * @param  value  The encoded value for this ASN.1 octet string element.
   */
  public void setValue(byte[] value)
  {
    assert debugEnter(CLASS_NAME, "setValue", bytesToHex(value));

    if (value == null)
    {
      setValueInternal(NO_VALUE);
    }
    else
    {
      setValueInternal(value);
    }

    stringValue = null;
  }



  /**
   * Decodes the provided ASN.1 element as an octet string element.
   *
   * @param  element  The ASN.1 element to decode as an octet string element.
   *
   * @return  The decoded ASN.1 octet string element.
   *
   * @throws  ASN1Exception  If the provided ASN.1 element cannot be decoded as
   *                         an octet string element.
   */
  public static ASN1OctetString decodeAsOctetString(ASN1Element element)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "decodeAsOctetString",
                      String.valueOf(element));

    if (element == null)
    {
      int    msgID   = MSGID_ASN1_OCTET_STRING_DECODE_ELEMENT_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    return new ASN1OctetString(element.getType(), element.value());
  }



  /**
   * Decodes the provided byte array as an ASN.1 octet string element.
   *
   * @param  encodedElement  The byte array to decode as an ASN.1 octet string
   *                         element.
   *
   * @return  The decoded ASN.1 octet string element.
   *
   * @throws  ASN1Exception  If the provided byte array cannot be decoded as an
   *                         ASN.1 octet string element.
   */
  public static ASN1OctetString decodeAsOctetString(byte[] encodedElement)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "decodeAsOctetString",
                      bytesToHex(encodedElement));

    // First make sure that the array is not null and long enough to contain
    // a valid ASN.1 element.
    if (encodedElement == null)
    {
      int    msgID   = MSGID_ASN1_OCTET_STRING_DECODE_ARRAY_NULL;
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


    // Copy the value and construct the element to return.
    byte[] value = new byte[length];
    System.arraycopy(encodedElement, valueStartPos, value, 0, length);
    return new ASN1OctetString(type, value);
  }



  /**
   * Creates a duplicate of this ASN.1 octet string.
   *
   * @return  A duplicate of this ASN.1 octet string.
   */
  public ASN1OctetString duplicate()
  {
    assert debugEnter(CLASS_NAME, "duplicate");

    byte[] value = value();
    int length = value.length;

    byte[] duplicateValue = new byte[length];
    System.arraycopy(value, 0, duplicateValue, 0, length);

    return new ASN1OctetString(getType(), value);
  }



  /**
   * Appends a string representation of this ASN.1 octet string element to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append(stringValue());
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
    buffer.append("ASN.1 Octet String");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  BER Type:  ");
    buffer.append(byteToHex(getType()));
    buffer.append(EOL);

    byte[] value = value();
    buffer.append(indentBuf);
    buffer.append("  Value (");
    buffer.append(value.length);
    buffer.append(" bytes)");
    buffer.append(EOL);

    byteArrayToHexPlusAscii(buffer, value, indent+2);
  }



  /**
   * Retrieves this byte string as an ASN.1 octet string.
   *
   * @return  An ASN.1 octet string with the value of this byte string.
   */
  public ASN1OctetString toASN1OctetString()
  {
    assert debugEnter(CLASS_NAME, "toASN1OctetString");

    return this;
  }
}

