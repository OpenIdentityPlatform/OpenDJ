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
import org.opends.messages.Message;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with ASN.1 null elements.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class ASN1Null
       extends ASN1Element
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = 8921787912269145125L;



  /**
   * Creates a new ASN.1 null element with the default type.
   */
  public ASN1Null()
  {
    super(UNIVERSAL_NULL_TYPE);

  }



  /**
   * Creates a new ASN.1 null element with the specified type.
   *
   * @param  type  The BER type to use for this ASN.1 null element.
   */
  public ASN1Null(byte type)
  {
    super(type);

  }



  /**
   * Specifies the value for this ASN.1 null element.
   *
   * @param  value  The encoded value for this ASN.1 null element.
   *
   * @throws  ASN1Exception  If the provided array is not empty.
   */
  public void setValue(byte[] value)
         throws ASN1Exception
  {
    if ((value != null) && (value.length != 0))
    {
      Message message =
          ERR_ASN1_NULL_SET_VALUE_INVALID_LENGTH.get(value.length);
      throw new ASN1Exception(message);
    }
  }



  /**
   * Decodes the provided ASN.1 element as a null element.
   *
   * @param  element  The ASN.1 element to decode as a null element.
   *
   * @return  The decoded ASN.1 null element.
   *
   * @throws  ASN1Exception  If the provided ASN.1 element cannot be decoded as
   *                         a null element.
   */
  public static ASN1Null decodeAsNull(ASN1Element element)
         throws ASN1Exception
  {
    if (element == null)
    {
      Message message = ERR_ASN1_NULL_DECODE_ELEMENT_NULL.get();
      throw new ASN1Exception(message);
    }

    byte[] value = element.value();
    if (value.length != 0)
    {
      Message message =
          ERR_ASN1_NULL_DECODE_ELEMENT_INVALID_LENGTH.get(value.length);
      throw new ASN1Exception(message);
    }

    return new ASN1Null(element.getType());
  }



  /**
   * Decodes the provided byte array as an ASN.1 null element.
   *
   * @param  encodedElement  The byte array to decode as an ASN.1 null element.
   *
   * @return  The decoded ASN.1 null element.
   *
   * @throws  ASN1Exception  If the provided byte array cannot be decoded as an
   *                         ASN.1 null element.
   */
  public static ASN1Null decodeAsNull(byte[] encodedElement)
         throws ASN1Exception
  {
    // First make sure that the array is not null and long enough to contain
    // a valid ASN.1 null element.
    if (encodedElement == null)
    {
      Message message = ERR_ASN1_NULL_DECODE_ARRAY_NULL.get();
      throw new ASN1Exception(message);
    }

    if (encodedElement.length < 2)
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


    // Make sure that the decoded length is exactly zero byte.
    if (length != 0)
    {
      Message message = ERR_ASN1_NULL_DECODE_ARRAY_INVALID_LENGTH.get(length);
      throw new ASN1Exception(message);
    }


    // Copy the value and construct the element to return.
    return new ASN1Null(type);
  }



  /**
   * Appends a string representation of this ASN.1 null element to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ASN1Null(type=");
    buffer.append(byteToHex(getType()));
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
    buffer.append("ASN.1 Null");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  BER Type:  ");
    buffer.append(byteToHex(getType()));
    buffer.append(EOL);
  }
}

