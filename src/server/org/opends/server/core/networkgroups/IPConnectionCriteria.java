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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.core.networkgroups;



import java.net.InetAddress;
import java.util.Collection;

import org.opends.server.api.ClientConnection;
import org.opends.server.types.AddressMask;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;



/**
 * A connection criteria which matches connections coming from a allowed
 * client address.
 */
final class IPConnectionCriteria implements ConnectionCriteria
{

  /** The collection of allowed client address masks. */
  private final Collection<AddressMask> allowedClients;

  /** The collection of denied client address masks. */
  private final Collection<AddressMask> deniedClients;



  /**
   * Creates a new IP connection criteria using the provided allowed and
   * denied address masks.
   *
   * @param allowedClients
   *          The list of allowed client address masks.
   * @param deniedClients
   *          The list of denied client address masks.
   */
  public IPConnectionCriteria(Collection<AddressMask> allowedClients,
      Collection<AddressMask> deniedClients)
  {
    this.allowedClients = allowedClients;
    this.deniedClients = deniedClients;
  }



  /**
   * {@inheritDoc}
   */
  public boolean matches(ClientConnection connection)
  {
    InetAddress ipAddr = connection.getRemoteAddress();

    if (!deniedClients.isEmpty()
        && AddressMask.maskListContains(ipAddr, deniedClients))
    {
      return false;
    }

    if (!allowedClients.isEmpty()
        && !AddressMask.maskListContains(ipAddr, allowedClients))
    {
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean willMatchAfterBind(ClientConnection connection,
      DN bindDN, AuthenticationType authType, boolean isSecure)
  {
    return matches(connection);
  }
}
