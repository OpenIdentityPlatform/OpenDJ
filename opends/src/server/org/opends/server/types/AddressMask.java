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



import org.opends.server.api.ClientConnection;
import org.opends.server.config.ConfigException;



/**
 * This class defines an address mask, which can be used to perform
 * efficient comparisons against IP addresses to determine whether a
 * particular IP address is in a given range.
 */
public class AddressMask
{
  // NYI



  /**
   * Decodes the provided string as an address mask.
   *
   * @param  maskString  The string to decode as an address mask.
   *
   * @return  AddressMask  The address mask decoded from the provided
   *                       string.
   *
   * @throws  ConfigException  If the provided string cannot be
   *                           decoded as an address mask.
   */
  public static AddressMask decode(String maskString)
         throws ConfigException
  {
    // NYI
    return null;
  }



  /**
   * Indicates whether the provided address matches one of the address
   * masks in the provided array.
   *
   * @param  clientConnection  The client connection for which to make
   *                           the determination.
   * @param  masks             The set of address masks to check.
   *
   * @return  <CODE>true</CODE> if the provided address does match one
   *          or more of the given address masks, or
   *          <CODE>false</CODE> if it does not.
   */
  public static boolean maskListContains(
                             ClientConnection clientConnection,
                             AddressMask[] masks)
  {
    // NYI
    return false;
  }



  /**
   * Retrieves a string representation of this address mask.
   *
   * @return  A string representation of this address mask.
   */
  public String toString()
  {
    // NYI
    return null;
  }
}

