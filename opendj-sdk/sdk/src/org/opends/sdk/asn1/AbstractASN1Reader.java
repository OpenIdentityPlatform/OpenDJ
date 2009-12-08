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

package org.opends.sdk.asn1;



import static com.sun.opends.sdk.messages.Messages.*;
import static org.opends.sdk.asn1.ASN1Constants.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;




/**
 * An abstract {@code ASN1Reader} which can be used as the basis for
 * implementing new ASN1 reader implementations.
 */
public abstract class AbstractASN1Reader implements ASN1Reader
{
  /**
   * Creates a new abstract ASN.1 reader.
   */
  protected AbstractASN1Reader()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public boolean readBoolean(byte type) throws IOException
  {
    if (type == 0x00)
    {
      type = UNIVERSAL_BOOLEAN_TYPE;
    }
    checkType(type);
    return readBoolean();
  }



  /**
   * {@inheritDoc}
   */
  public int readEnumerated(byte type) throws IOException
  {
    if (type == 0x00)
    {
      type = UNIVERSAL_ENUMERATED_TYPE;
    }
    checkType(type);
    return readEnumerated();
  }



  /**
   * {@inheritDoc}
   */
  public long readInteger(byte type) throws IOException
  {
    if (type == 0x00)
    {
      type = UNIVERSAL_INTEGER_TYPE;
    }
    checkType(type);
    return readInteger();
  }



  /**
   * {@inheritDoc}
   */
  public void readNull(byte type) throws IOException
  {
    if (type == 0x00)
    {
      type = UNIVERSAL_NULL_TYPE;
    }
    checkType(type);
    readNull();
  }



  /**
   * {@inheritDoc}
   */
  public ByteString readOctetString(byte type) throws IOException
  {
    if (type == 0x00)
    {
      type = UNIVERSAL_OCTET_STRING_TYPE;
    }
    checkType(type);
    return readOctetString();
  }



  /**
   * {@inheritDoc}
   */
  public ByteStringBuilder readOctetString(byte type,
      ByteStringBuilder builder) throws IOException
  {
    if (type == 0x00)
    {
      type = UNIVERSAL_OCTET_STRING_TYPE;
    }
    checkType(type);
    readOctetString(builder);
    return builder;
  }



  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString(byte type) throws IOException
  {
    // We could cache the UTF-8 CharSet if performance proves to be an
    // issue.
    if (type == 0x00)
    {
      type = UNIVERSAL_OCTET_STRING_TYPE;
    }
    checkType(type);
    return readOctetStringAsString();
  }



  /**
   * {@inheritDoc}
   */
  public void readStartSequence(byte type) throws IOException
  {
    if (type == 0x00)
    {
      type = UNIVERSAL_SEQUENCE_TYPE;
    }
    checkType(type);
    readStartSequence();
  }



  /**
   * {@inheritDoc}
   */
  public void readStartSet(byte type) throws IOException
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    if (type == 0x00)
    {
      type = UNIVERSAL_SET_TYPE;
    }
    checkType(type);
    readStartSet();
  }



  private void checkType(byte expectedType) throws IOException
  {
    if (peekType() != expectedType)
    {
      LocalizableMessage message = ERR_ASN1_UNEXPECTED_TAG.get(expectedType,
          peekType());
      throw DecodeException.fatalError(message);
    }
  }
}
