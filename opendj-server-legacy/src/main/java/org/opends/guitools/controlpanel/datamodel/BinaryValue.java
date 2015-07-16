/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.datamodel;

import java.io.File;
import java.text.ParseException;
import java.util.Objects;

import org.opends.server.util.Base64;

/**
 * Class used to represent Binary Values.  This is required in particular
 * when the user wants to use the value in a file.  To be able to reflect
 * this this object is used: it contains the binary itself, the base 64
 * representation and the file that has been used.
 *
 */
public class BinaryValue
{
  private Type type;
  private String base64;
  private byte[] bytes;
  private File file;
  private int hashCode;

  /**
   * The type of the binary value.
   *
   */
  public enum Type
  {
    /**
     * The binary value is provided as Base 64 string.
     */
    BASE64_STRING,
    /**
     * The binary value is provided as a byte array.
     */
    BYTE_ARRAY
  }

  /**
   * This is done to force the use of the factory methods (createBase64 and
   * createFromFile).
   *
   */
  private BinaryValue()
  {
  }

  /**
   * Creates a binary value using a base64 string.
   * @param base64 the base64 representation of the binary.
   * @return the binary value.
   * @throws ParseException if there is an error decoding the provided base64
   * string.
   */
  public static BinaryValue createBase64(String base64) throws ParseException
  {
    BinaryValue value =  new BinaryValue();
    value.type = Type.BASE64_STRING;
    value.base64 = base64;
    value.bytes = value.getBytes();
    value.hashCode = base64.hashCode();
    return value;
  }

  /**
   * Creates a binary value using an array of bytes.
   * @param bytes the byte array.
   * @return the binary value.
   */
  public static BinaryValue createBase64(byte[] bytes)
  {
    BinaryValue value =  new BinaryValue();
    value.type = Type.BASE64_STRING;
    value.bytes = bytes;
    value.base64 = value.getBase64();
    value.hashCode = value.base64.hashCode();
    return value;
  }

  /**
   * Creates a binary value using an array of bytes and a file.
   * @param bytes the bytes in the file.
   * @param file the file the bytes were read from.
   * @return the binary value.
   */
  public static BinaryValue createFromFile(byte[] bytes, File file)
  {
    BinaryValue value =  new BinaryValue();
    value.type = Type.BYTE_ARRAY;
    value.bytes = bytes;
    value.base64 = value.getBase64();
    value.hashCode = value.base64.hashCode();
    value.file = file;
    return value;
  }

  /**
   * Returns the base64 representation of the binary value.
   * @return the base64 representation of the binary value.
   */
  public String getBase64()
  {
    if (base64 == null && bytes != null)
    {
      base64 = Base64.encode(bytes);
    }
    return base64;
  }

  /**
   * Returns the byte array of the binary value.
   * @return the byte array of the binary value.
   * @throws ParseException if this object was created using a base64 string
   * and there was an error parsing it.
   */
  public byte[] getBytes() throws ParseException
  {
    if (bytes == null && base64 != null)
    {
      bytes = Base64.decode(base64);
    }
    return bytes;
  }

  /**
   * Return the type of the binary value.
   * @return the type of the binary value.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Return the file that was used to read the binary value.
   * @return the file that was used to read the binary value.
   */
  public File getFile()
  {
    return file;
  }

  /** {@inheritDoc} */
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o instanceof BinaryValue)
    {
      BinaryValue candidate = (BinaryValue)o;
      return candidate.getType() == getType()
          && Objects.equals(file, candidate.getFile())
          && bytesEqual(candidate);
    }
    return false;
  }

  private boolean bytesEqual(BinaryValue candidate)
  {
    if (type == Type.BASE64_STRING)
    {
      return candidate.getBase64().equals(getBase64());
    }

    try
    {
      if (candidate.getBytes().length != getBytes().length) {
        return false;
      }
      boolean equals = true;
      for (int i=0; i<getBytes().length && equals; i++)
      {
        equals = bytes[i] == candidate.getBytes()[i];
      }
      return equals;
    }
    catch (ParseException pe)
    {
      throw new RuntimeException(
          "Unexpected error getting bytes: "+pe, pe);
    }
  }

  /** {@inheritDoc} */
  public int hashCode()
  {
    return hashCode;
  }
}
