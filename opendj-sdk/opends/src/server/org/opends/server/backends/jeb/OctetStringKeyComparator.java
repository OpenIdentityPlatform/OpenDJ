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
package org.opends.server.backends.jeb;

import org.opends.server.protocols.asn1.ASN1OctetString;

import java.util.Comparator;

/**
 * This class implements a comparator for ASN1OctetString using
 * a byte array comparator supplied in its constructor.
 */
public class OctetStringKeyComparator implements Comparator<ASN1OctetString>
{
  /**
   * The byte array comparator used to compare the octet string values.
   */
  private Comparator<byte[]> comparator;

  /**
   * Create a new ASN1 octet string comparator.
   * @param comparator The byte array comparator to be used to compare the
   * octet string values.
   */
  public OctetStringKeyComparator(Comparator<byte[]> comparator)
  {
    this.comparator = comparator;
  }

  /**
   * Compares its two arguments for order.  Returns a negative integer,
   * zero, or a positive integer as the first argument is less than, equal
   * to, or greater than the second.
   *
   * @param a the first object to be compared.
   * @param b the second object to be compared.
   * @return a negative integer, zero, or a positive integer as the
   *         first argument is less than, equal to, or greater than the
   *         second.
   * @throws ClassCastException if the arguments' types prevent them from
   *         being compared by this Comparator.
   */
  public int compare(ASN1OctetString a, ASN1OctetString b)
       throws ClassCastException
  {
    return comparator.compare(a.value(), b.value());
  }
}
