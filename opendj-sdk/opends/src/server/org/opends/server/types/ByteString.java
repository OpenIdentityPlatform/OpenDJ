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
package org.opends.server.types;



import org.opends.server.protocols.asn1.ASN1OctetString;



/**
 * This interface defines data type that is backed by a byte array but
 * may also have a string representation.  The preferred way to create
 * a <CODE>ByteString</CODE> object is to use one of the
 * <CODE>ByteStringFactory.create</CODE> methods.
 */
public interface ByteString
{
  /**
   * Retrieves the value of this byte string as a byte array.
   *
   * @return  The value of this byte string as a byte array.
   */
  public byte[] value();



  /**
   * Retrieves the value of this byte string as a string.
   *
   * @return  The value of this byte string as a string.
   */
  public String stringValue();



  /**
   * Sets the value for this byte string.
   *
   * @param  value  The value for this byte string.
   */
  public void setValue(byte[] value);



  /**
   * Sets the value for this byte string.
   *
   * @param  value  The value for this byte string.
   */
  public void setValue(String value);



  /**
   * Retrieves this byte string as an ASN.1 octet string.
   *
   * @return  An ASN.1 octet string with the value of this byte
   *          string.
   */
  public ASN1OctetString toASN1OctetString();



  /**
   * Retrieves a string representation of this byte string.
   *
   * @return  A string representation of this byte string.
   */
  public String toString();



  /**
   * Appends a string representation of this byte string to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer);



  /**
   * Creates a duplicate of this byte string whose contents can be
   * altered without impacting this byte string.
   *
   * @return  A duplicate of this byte string.
   */
  public ByteString duplicate();
}

