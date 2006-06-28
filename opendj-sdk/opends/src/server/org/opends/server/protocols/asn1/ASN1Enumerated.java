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



import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with ASN.1 enumerated elements.
 */
public class ASN1Enumerated
       extends ASN1Element
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.asn1.ASN1Enumerated";



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -8686345111964945130L;



  // The integer value for this element.
  private int intValue;




  /**
   * Creates a new ASN.1 enumerated element with the default type and the
   * provided value.
   *
   * @param  intValue  The value for this ASN.1 enumerated element.
   */
  public ASN1Enumerated(int intValue)
  {
    super(UNIVERSAL_ENUMERATED_TYPE, encodeValue(intValue));

    assert debugConstructor(CLASS_NAME, String.valueOf(intValue));

    this.intValue = intValue;
  }



  /**
   * Creates a new ASN.1 enumerated element with the specified type and value.
   *
   * @param  type      The BER type for this ASN.1 enumerated element.
   * @param  intValue  The value for this ASN.1 enumerated element.
   */
  public ASN1Enumerated(byte type, int intValue)
  {
    super(type, encodeValue(intValue));

    assert debugConstructor(CLASS_NAME, byteToHex(type),
                            String.valueOf(intValue));

    this.intValue = intValue;
  }



  /**
   * Creates a new ASN.1 enumerated element with the specified type and value.
   *
   * @param  type      The BER type for this ASN.1 enumerated element.
   * @param  value     The encoded value for this ASN.1 enumerated element.
   * @param  intValue  The int value for this ASN.1 enumerated element.
   */
  private ASN1Enumerated(byte type, byte[] value, int intValue)
  {
    super(type, value);

    assert debugConstructor(CLASS_NAME, byteToHex(type), bytesToHex(value),
                            String.valueOf(intValue));

    this.intValue = intValue;
  }



  /**
   * Retrieves the integer value for this ASN.1 enumerated element.
   *
   * @return  The integer value for this ASN.1 enumerated element.
   */
  public int intValue()
  {
    assert debugEnter(CLASS_NAME, "intValue");

    return intValue;
  }



  /**
   * Specifies the integer value for this ASN.1 enumerated element.
   *
   * @param  intValue  The integer value for this ASN.1 enumerated element.
   */
  public void setValue(int intValue)
  {
    assert debugEnter(CLASS_NAME, "setValue", String.valueOf(intValue));

    this.intValue = intValue;
    setValueInternal(encodeValue(intValue));
  }



  /**
   * Specifies the value for this ASN.1 enumerated element.
   *
   * @param  value  The encoded value for this ASN.1 enumerated element.
   *
   * @throws  ASN1Exception  If the provided array is null or is not between one
   *                         and four bytes in length.
   */
  public void setValue(byte[] value)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "setValue", bytesToHex(value));

    if (value == null)
    {
      int    msgID   = MSGID_ASN1_ENUMERATED_SET_VALUE_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    if ((value.length < 1) || (value.length > 4))
    {
      int    msgID   = MSGID_ASN1_ENUMERATED_SET_VALUE_INVALID_LENGTH;
      String message = getMessage(msgID, value.length);
      throw new ASN1Exception(msgID, message);
    }

    intValue = 0;
    for (byte b : value)
    {
      intValue = (intValue << 8) | (b & 0xFF);
    }

    setValueInternal(value);
  }



  /**
   * Decodes the provided ASN.1 element as an enumerated element.
   *
   * @param  element  The ASN.1 element to decode as an enumerated element.
   *
   * @return  The decoded ASN.1 enumerated element.
   *
   * @throws  ASN1Exception  If the provided ASN.1 element cannot be decoded as
   *                         an enumerated element.
   */
  public static ASN1Enumerated decodeAsEnumerated(ASN1Element element)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "decodeAsEnumerated",
                      String.valueOf(element));

    if (element == null)
    {
      int    msgID   = MSGID_ASN1_ENUMERATED_DECODE_ELEMENT_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    byte[] value = element.value();
    if ((value.length < 1) || (value.length > 4))
    {
      int    msgID   = MSGID_ASN1_ENUMERATED_DECODE_ELEMENT_INVALID_LENGTH;
      String message = getMessage(msgID, value.length);
      throw new ASN1Exception(msgID, message);
    }

    int intValue = 0;
    for (byte b : value)
    {
      intValue = (intValue << 8) | (b & 0xFF);
    }

    return new ASN1Enumerated(element.getType(), value, intValue);
  }



  /**
   * Decodes the provided byte array as an ASN.1 enumerated element.
   *
   * @param  encodedElement  The byte array to decode as an ASN.1 enumerated
   *                         element.
   *
   * @return  The decoded ASN.1 enumerated element.
   *
   * @throws  ASN1Exception  If the provided byte array cannot be decoded as an
   *                         ASN.1 enumerated element.
   */
  public static ASN1Enumerated decodeAsEnumerated(byte[] encodedElement)
         throws ASN1Exception
  {
    assert debugEnter(CLASS_NAME, "decodeAsEnumerated",
                      bytesToHex(encodedElement));

    // First make sure that the array is not null and long enough to contain
    // a valid ASN.1 enumerated element.
    if (encodedElement == null)
    {
      int    msgID   = MSGID_ASN1_ENUMERATED_DECODE_ARRAY_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    if (encodedElement.length < 3)
    {
      int    msgID   = MSGID_ASN1_ENUMERATED_SHORT_ELEMENT;
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


    // Make sure that the decoded length is between 1 and 4 bytes.
    if ((length < 1) || (length > 4))
    {
      int    msgID   = MSGID_ASN1_ENUMERATED_DECODE_ARRAY_INVALID_LENGTH;
      String message = getMessage(msgID, length);
      throw new ASN1Exception(msgID, message);
    }


    // Copy the value and construct the element to return.
    byte[] value = new byte[length];
    System.arraycopy(encodedElement, valueStartPos, value, 0, length);

    int intValue = 0;
    for (byte b : value)
    {
      intValue = (intValue << 8) | (b & 0xFF);
    }

    return new ASN1Enumerated(type, value, intValue);
  }



  /**
   * Appends a string representation of this ASN.1 enumerated element to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("ASN1Enumerated(type=");
    buffer.append(byteToHex(getType()));
    buffer.append(", value=");
    buffer.append(intValue);
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
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder",
                      String.valueOf(indent));

    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("ASN.1 Enumerated");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  BER Type:  ");
    buffer.append(byteToHex(getType()));
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Value:  ");
    buffer.append(intValue);
    buffer.append(" (");
    buffer.append(bytesToHex(value()));
    buffer.append(")");
    buffer.append(EOL);
  }
}

