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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This class builds a network string to an internal representation.
 */
public class IpBitsNetworkCriteria {

  byte[] _address;    // address in byte format
  int _bits;      // number of bits for matching
  byte[] _bitsArray;    // bits in network order
  InetAddress _inetAddress;

  /**
   * Creates a new IpBitsNeworkCriteria instance.
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
   *                             values of the 8
   *                             16-bits pieces of the address
   *                             Use of :: to compress the leading
   *                             and/or trailing zeros e.g.
   *                             x::x:x:x:x:x:x
   *
   * @param  theBits             Number of bits of the network address
   *                             necessary for matching.
   *                             Max is 32 for IPv4 addresses and 128
   *                              for IPv6 addresses \
   *
   * @throws UnknownHostException Thrown if the inetaddress cannot be gotten
   * from the input address string.
   * @throws AciException Thrown if the bit count is not in the correct
   * ranges.
   */

  public IpBitsNetworkCriteria(String theInputAddress, int theBits)
  throws UnknownHostException, AciException
  {
    boolean ipv4 = true;
    _inetAddress = InetAddress.getByName(theInputAddress);

    if (_inetAddress instanceof Inet6Address)
    {
      if (theBits < 0 || theBits > 128) {
          int msgID = MSGID_ACI_SYNTAX_INVALID_NETWORK_BIT_MATCH;
          String message = getMessage(msgID, "IPV6",
                  "Bits must be in [0..128] range.");
          throw new AciException(msgID, message);
      }
      ipv4=false;
    }
    else
    {
      // Assume IPv4
      if (theBits < 0 || theBits > 32) {
          int msgID = MSGID_ACI_SYNTAX_INVALID_NETWORK_BIT_MATCH;
          String message = getMessage(msgID, "IPV4",
                  "Bits must be in [0..32] range.");
          throw new AciException(msgID, message);
      }
    }

    _bits = theBits;

    // Convert the bits into a mask in network byte order
    if (ipv4)
    {
      _bitsArray = new byte[4];
      // in java int is exactly 4 bytes
      int rawBits;
      if (theBits==0)
        rawBits=0;
      else
        rawBits=~0;
      rawBits = rawBits << (32 - theBits);
      // Use network order for the comparison
      _bitsArray[0] = (byte) ((rawBits >> 24) & 0xFF );
      _bitsArray[1] = (byte) ((rawBits >> 16) & 0xFF );
      _bitsArray[2] = (byte) ((rawBits >> 8) & 0xFF );
      _bitsArray[3] = (byte) ((rawBits) & 0xFF );
    }
    else
    {
      _bitsArray = new byte[16];
      int index=0;
      if (theBits > 64)
      {
        _bitsArray[0] = (byte) 0xFF;
        _bitsArray[1] = (byte) 0xFF;
        _bitsArray[2] = (byte) 0xFF;
        _bitsArray[3] = (byte) 0xFF;
        _bitsArray[4] = (byte) 0xFF;
        _bitsArray[5] = (byte) 0xFF;
        _bitsArray[6] = (byte) 0xFF;
        _bitsArray[7] = (byte) 0xFF;
        theBits-=64;
        index=8;
      }
      long rawBits = ~0;
      rawBits = rawBits << (64 - theBits);

      if (_bits !=0)
      {
        _bitsArray[index++] = (byte) ((rawBits >> 56) & 0xFF );
        _bitsArray[index++] = (byte) ((rawBits >> 48) & 0xFF );
        _bitsArray[index++] = (byte) ((rawBits >> 40) & 0xFF );
        _bitsArray[index++] = (byte) ((rawBits >> 32) & 0xFF );
        _bitsArray[index++] = (byte) ((rawBits >> 24) & 0xFF );
        _bitsArray[index++] = (byte) ((rawBits >> 16) & 0xFF );
        _bitsArray[index++] = (byte) ((rawBits >> 8) & 0xFF );
        _bitsArray[index] = (byte) ((rawBits ) & 0xFF );
      }
    }

    _address = _inetAddress.getAddress();
  }

  /**
   * Compare an IP address with the network rule.
   *
   * @param  theSourceAddress   IP source address of the client contacting
   *                            the proxy server.
   * @return  <CODE>true</CODE> if client matches the network rule or
   *          <CODE>false</CODE> if they may not.
   */

  public boolean match (InetAddress theSourceAddress)
  {

    byte[] addr = theSourceAddress.getAddress();

    if ((addr.length * 8) < _bits) {
      // Client IP  too small. Won't match.
      return false;
    }

    for (int i=0; i<addr.length; i++)
    {
      if ((addr[i] & _bitsArray[i]) != (_address[i] & _bitsArray[i])) {
        return false;
      }
    }

    return true;

  }

  /**
   * String representation of this criteria.
   *
   * @return  a String representation of the IpMaskNetworkCriteria
   */

  public String toString()
  {
    return "Address:" + _inetAddress.getHostAddress() +
        "/" + Integer.toString(_bits);
  }

}
