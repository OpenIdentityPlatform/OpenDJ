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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.AddressMask;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;

/**
 * This class defines the IP filter criteria.
 * A client connection matches the criteria when it is performed from
 * a host whose IP address matches at least one of the specified filters.
 */
public class IpFilterCriteria implements NetworkGroupCriterion {

  private Collection<AddressMask> ipFilters;

  /**
   * Constructor.
   */
  public IpFilterCriteria() {
    ipFilters = new HashSet<AddressMask>();
  }

  /**
   * Adds a new Ip Filter to the list of allowed IP filters.
   * @param filter The new IP filter
   */
  public void addIpFilter(AddressMask filter) {
    ipFilters.add(filter);
  }

  /**
   * {@inheritDoc}
   */
  public boolean match(ClientConnection connection) {
    InetAddress ipAddr = connection.getRemoteAddress();
    if (AddressMask.maskListContains(ipAddr.getAddress(),
                                     ipAddr.getCanonicalHostName(),
                                     ipFilters.toArray(new AddressMask[0]))) {
      return (true);
    } else {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean matchAfterBind(ClientConnection connection, DN bindDN,
                                AuthenticationType authType, boolean isSecure) {
    return (match(connection));
  }
}
