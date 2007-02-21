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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *   This class creates a network mask criteria from the address and mask
 *   string passed to it.
 */
class IpMaskNetworkCriteria
{

  byte[] _address;    // address in byte format
  byte[] _mask;     // mask in byte format
  InetAddress _inetAddress;
  InetAddress _inetMask;
  boolean _ipv4;      // true if ipv4 address


  /**
   * Creates a new IpMaskNeworkCriteria instance.
   *
   * @param  theInputAddress     IP address associated the rule. For IPV4
   *                             addresses, the following
   *                             textual formats are supported
   *                             a.b.c.d
   *                             a.b.c
   *                             a.b
   *                             a
   *                             For IPv6 addresses, the following textual
   *                             format are supported:
   *                             x:x:x:x:x:x:x:x, where x are the hexadecimal
   *                             values of the 8 16-bits pieces of the address
   *                             Use of :: to compress the leading  and/or
   *                             trailing zeros e.g.x::x:x:x:x:x:x
   *
   * @param  theInputMask        Bits of the network address necessary
   *                             for matching.
   *                             Same format as the IP address above.
   *
   * @throws UnknownHostException Thrown if the hostname of the input address
   * cannot be resolved.
   * @throws AciException If the address family has a mismatch.
   */

  public IpMaskNetworkCriteria(String theInputAddress, String theInputMask)
  throws UnknownHostException, AciException {
    _inetAddress = InetAddress.getByName(theInputAddress);
    _inetMask = InetAddress.getByName(theInputMask);
    _address = _inetAddress.getAddress();
    _mask = _inetMask.getAddress();

    if (_inetAddress instanceof Inet4Address)
      _ipv4=true;

    if (_ipv4 && !(_inetMask instanceof Inet4Address) ||
       (!_ipv4 && !(_inetMask instanceof Inet6Address))) {
        int msgID = MSGID_ACI_SYNTAX_ADDRESS_FAMILY_MISMATCH;
        String message = getMessage(msgID, theInputMask, theInputAddress);
        throw new AciException(msgID, message);
    }
  }

  /**
   * Compare an IP address with the network criteria.
   *
   * @param  theSourceAddress   IP source address of the client.
   * @return  <CODE>true</CODE> if client matches the network rule or
   *          <CODE>false</CODE> if they may not.
   */

  public boolean match (InetAddress theSourceAddress)
  {
    // First address family must match
    if (_ipv4)
    {
      if (!(theSourceAddress instanceof Inet4Address))
        return false;
    }
    else
    {
      if (!(theSourceAddress instanceof Inet6Address))
        return false;
    }

    byte[] addr = theSourceAddress.getAddress();
    for (int i=0; i<addr.length; i++) {
      if ((addr[i] & _mask[i]) != (_address[i] & _mask[i])) {
        return false;
      }
    }
    return true;

  }

  /**
   * String representation of this rule.
   *
   * @return  a String representation of the IpMaskNetworkRule.
   */

  public String toString()
  {
    return "Address:" + _inetAddress.getHostAddress() +
        " Mask:" + _inetMask.getHostAddress();
  }
}


