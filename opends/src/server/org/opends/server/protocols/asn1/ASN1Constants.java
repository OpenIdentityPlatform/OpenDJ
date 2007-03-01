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



/**
 * This class defines a number of constants that may be used when interacting
 * with ASN.1 elements.
 */
public class ASN1Constants
{
  /**
   * The BER type that is assigned to the universal Boolean element.
   */
  public static final byte UNIVERSAL_BOOLEAN_TYPE = 0x01;



  /**
   * The BER type that is assigned to the universal integer type.
   */
  public static final byte UNIVERSAL_INTEGER_TYPE = 0x02;



  /**
   * The BER type that is assigned to the universal octet string type.
   */
  public static final byte UNIVERSAL_OCTET_STRING_TYPE = 0x04;



  /**
   * The BER type that is assigned to the universal null type.
   */
  public static final byte UNIVERSAL_NULL_TYPE = 0x05;



  /**
   * The BER type that is assigned to the universal enumerated type.
   */
  public static final byte UNIVERSAL_ENUMERATED_TYPE = 0x0A;



  /**
   * The BER type that is assigned to the universal sequence type.
   */
  public static final byte UNIVERSAL_SEQUENCE_TYPE = 0x30;



  /**
   * The BER type that is assigned to the universal set type.
   */
  public static final byte UNIVERSAL_SET_TYPE = 0x31;



  /**
   * The byte array that will be used for ASN.1 elements with no value.
   */
  public static final byte[] NO_VALUE = new byte[0];



  /**
   * The bitmask that can be ANDed with the BER type to zero out all bits except
   * those used in the class.
   */
  public static final byte TYPE_MASK_ALL_BUT_CLASS = (byte) 0xC0;



  /**
   * The bitmask that can be ANDed with the BER type to determine if the element
   * is in the universal class.
   */
  public static final byte TYPE_MASK_UNIVERSAL = 0x00;



  /**
   * The bitmask that can be ANDed with the BER type to determine if the element
   * is in the application-specific class.
   */
  public static final byte TYPE_MASK_APPLICATION = 0x40;



  /**
   * The bitmask that can be ANDed with the BER type to determine if the element
   * is in the context-specific class.
   */
  public static final byte TYPE_MASK_CONTEXT = (byte) 0x80;



  /**
   * The bitmask that can be ANDed with the BER type to determine if the element
   * is in the private class.
   */
  public static final byte TYPE_MASK_PRIVATE = (byte) 0xC0;



  /**
   * The bitmask that can be ANDed with the BER type to zero out all bits except
   * the primitive/constructed bit.
   */
  public static final byte TYPE_MASK_ALL_BUT_PC = (byte) 0x20;



  /**
   * The bitmask that can be ANDed with the BER type to determine if the element
   * is a primitive.
   */
  public static final byte TYPE_MASK_PRIMITIVE = 0x00;



  /**
   * The bitmask that can be ANDed with the BER type to determine if the element
   * is constructed.
   */
  public static final byte TYPE_MASK_CONSTRUCTED = 0x20;



  /**
   * The byte array containing the pre-encoded ASN.1 encoding for a boolean
   * value of "false".
   */
  public static final byte[] BOOLEAN_VALUE_FALSE = { 0x00 };



  /**
   * The byte array containing the pre-encoded ASN.1 encoding for a boolean
   * value of "false".
   */
  public static final byte[] BOOLEAN_VALUE_TRUE = { (byte) 0xFF };
}

