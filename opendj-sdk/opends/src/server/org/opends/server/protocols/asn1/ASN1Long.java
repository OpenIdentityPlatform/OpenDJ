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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with ASN.1 integer elements that may need to hold values greater than will
 * fit in the scope of a Java <CODE>int</CODE> structure.
 * <BR><BR>
 * Note that the difference between the <CODE>ASN1Integer</CODE> and
 * <CODE>ASN1Long</CODE> classes is purely artificial.  The ASN.1 specification
 * does not define any size limits for integer values, but the
 * <CODE>ASN1Integer</CODE> class uses an <CODE>int</CODE> data type behind the
 * scenes and therefore is only capable of representing values up to
 * 2<SUP>31</SUP> - 1 (a little over two billion).  This class uses a
 * <CODE>long</CODE> data type behind the scenes and therefore is capable of
 * holding much larger values.  Because of the need to deal with larger values,
 * this class may have a small performance disadvantage over the
 * <CODE>ASN1Integer</CODE> class and therefore that class should be used for
 * cases in which there is no danger of overflowing an <CODE>int</CODE> value.
 */
public class ASN1Long
       extends ASN1Element
{



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -6015600344725970947L;



  // The long value for this element.
  private long longValue;




  /**
   * Creates a new ASN.1 long element with the default type and the provided
   * value.
   *
   * @param  longValue  The value for this ASN.1 long element.
   */
  public ASN1Long (long longValue)
  {
    super(UNIVERSAL_INTEGER_TYPE, encodeLongValue(longValue));


    this.longValue = longValue;
  }



  /**
   * Creates a new ASN.1 long element with the specified type and value.
   *
   * @param  type       The BER type for this ASN.1 long element.
   * @param  longValue  The value for this ASN.1 long element.
   */
  public ASN1Long(byte type, long longValue)
  {
    super(type, encodeLongValue(longValue));


    this.longValue = longValue;
  }



  /**
   * Creates a new ASN.1 long element with the specified type and value.
   *
   * @param  type       The BER type for this ASN.1 long element.
   * @param  value      The encoded value for this ASN.1 long element.
   * @param  longValue  The long value for this ASN.1 long element.
   */
  private ASN1Long(byte type, byte[] value, long longValue)
  {
    super(type, value);


    this.longValue = longValue;
  }



  /**
   * Retrieves the long value for this ASN.1 long element.
   *
   * @return  The long value for this ASN.1 long element.
   */
  public long longValue()
  {

    return longValue;
  }



  /**
   * Specifies the long value for this ASN.1 long element.
   *
   * @param  longValue  The long value for this ASN.1 long element.
   */
  public void setValue(long longValue)
  {

    this.longValue = longValue;
    setValueInternal(encodeLongValue(longValue));
  }



  /**
   * Specifies the value for this ASN.1 long element.
   *
   * @param  value  The encoded value for this ASN.1 long element.
   *
   * @throws  ASN1Exception  If the provided array is null or is not between one
   *                         and four bytes in length.
   */
  public void setValue(byte[] value)
         throws ASN1Exception
  {

    if (value == null)
    {
      int    msgID   = MSGID_ASN1_INTEGER_SET_VALUE_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    if ((value.length < 1) || (value.length > 8))
    {
      int    msgID   = MSGID_ASN1_LONG_SET_VALUE_INVALID_LENGTH;
      String message = getMessage(msgID, value.length);
      throw new ASN1Exception(msgID, message);
    }

    longValue = 0;
    for (byte b : value)
    {
      longValue = (longValue << 8) | (b & 0xFF);
    }

    setValueInternal(value);
  }



  /**
   * Decodes the provided ASN.1 element as a long element.
   *
   * @param  element  The ASN.1 element to decode as a long element.
   *
   * @return  The decoded ASN.1 long element.
   *
   * @throws  ASN1Exception  If the provided ASN.1 element cannot be decoded as
   *                         a long element.
   */
  public static ASN1Long decodeAsLong(ASN1Element element)
         throws ASN1Exception
  {

    if (element == null)
    {
      int    msgID   = MSGID_ASN1_INTEGER_DECODE_ELEMENT_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    byte[] value = element.value();
    if ((value.length < 1) || (value.length > 8))
    {
      int    msgID   = MSGID_ASN1_LONG_DECODE_ELEMENT_INVALID_LENGTH;
      String message = getMessage(msgID, value.length);
      throw new ASN1Exception(msgID, message);
    }

    long longValue = 0;
    for (byte b : value)
    {
      longValue = (longValue << 8) | (b & 0xFF);
    }

    return new ASN1Long(element.getType(), value, longValue);
  }



  /**
   * Decodes the provided byte array as an ASN.1 long element.
   *
   * @param  encodedElement  The byte array to decode as an ASN.1 long element.
   *
   * @return  The decoded ASN.1 long element.
   *
   * @throws  ASN1Exception  If the provided byte array cannot be decoded as an
   *                         ASN.1 long element.
   */
  public static ASN1Long decodeAsLong(byte[] encodedElement)
         throws ASN1Exception
  {

    // First make sure that the array is not null and long enough to contain
    // a valid ASN.1 long element.
    if (encodedElement == null)
    {
      int    msgID   = MSGID_ASN1_INTEGER_DECODE_ARRAY_NULL;
      String message = getMessage(msgID);
      throw new ASN1Exception(msgID, message);
    }

    if (encodedElement.length < 3)
    {
      int    msgID   = MSGID_ASN1_INTEGER_SHORT_ELEMENT;
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


    // Make sure that the decoded length is between 1 and 8 bytes.
    if ((length < 1) || (length > 8))
    {
      int    msgID   = MSGID_ASN1_LONG_DECODE_ARRAY_INVALID_LENGTH;
      String message = getMessage(msgID, length);
      throw new ASN1Exception(msgID, message);
    }


    // Copy the value and construct the element to return.
    byte[] value = new byte[length];
    System.arraycopy(encodedElement, valueStartPos, value, 0, length);

    long longValue = 0;
    for (byte b : value)
    {
      longValue = (longValue << 8) | (b & 0xFF);
    }

    return new ASN1Long(type, value, longValue);
  }



  /**
   * Appends a string representation of this ASN.1 integer element to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {

    buffer.append("ASN1Long(type=");
    buffer.append(byteToHex(getType()));
    buffer.append(", value=");
    buffer.append(longValue);
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
    buffer.append("ASN.1 Long");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  BER Type:  ");
    buffer.append(byteToHex(getType()));
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Value:  ");
    buffer.append(longValue);
    buffer.append(" (");
    buffer.append(bytesToHex(value()));
    buffer.append(")");
    buffer.append(EOL);
  }
}

