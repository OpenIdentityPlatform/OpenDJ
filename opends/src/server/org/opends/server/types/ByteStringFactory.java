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
package org.opends.server.types;



import org.opends.server.protocols.asn1.ASN1OctetString;




/**
 * This class provides static factory methods for creating ByteString
 * objects.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class ByteStringFactory
{
  /**
   * Creates a new <CODE>ByteString</CODE> object with no value.
   *
   * @return  A new <CODE>ByteString</CODE> object with no value.
   */
  public static ByteString create()
  {
    return new ASN1OctetString();
  }



  /**
   * Creates a new <CODE>ByteString</CODE> object for the provided
   * byte array value.
   *
   * @param  value  The value to use to create the
   *                <CODE>ByteString</CODE> object.
   *
   * @return  A new <CODE>ByteString</CODE> object with the specified
   *          value.
   */
  public static ByteString create(byte[] value)
  {
    return new ASN1OctetString(value);
  }



  /**
   * Creates a new <CODE>ByteString</CODE> object for the provided
   * string value.
   *
   * @param  value  The value to use to create the
   *                <CODE>ByteString</CODE> object.
   *
   * @return  A new <CODE>ByteString</CODE> object with the specified
   *          value.
   */
  public static ByteString create(String value)
  {
    return new ASN1OctetString(value);
  }
}

