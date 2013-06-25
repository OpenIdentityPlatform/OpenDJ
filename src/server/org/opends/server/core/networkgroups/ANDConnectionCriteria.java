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
 */
package org.opends.server.core.networkgroups;



import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;



/**
 * A connection criteria which matches connections if and only if all
 * the sub-criteria match. If there are no sub-criteria then the
 * connection criteria will always match.
 */
final class ANDConnectionCriteria implements ConnectionCriteria
{

  // The list of underlying connection criteria.
  private final List<ConnectionCriteria> subCriteria;



  /**
   * Creates a new AND connection criteria using the provided
   * sub-criteria.
   *
   * @param subCriteria
   *          The sub-criteria.
   */
  public ANDConnectionCriteria(
      Collection<? extends ConnectionCriteria> subCriteria)
  {
    this.subCriteria = new ArrayList<ConnectionCriteria>(subCriteria);
  }



  /**
   * {@inheritDoc}
   */
  public boolean matches(ClientConnection connection)
  {
    for (ConnectionCriteria filter : subCriteria)
    {
      if (!filter.matches(connection))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean willMatchAfterBind(ClientConnection connection,
      DN bindDN, AuthenticationType authType, boolean isSecure)
  {
    for (ConnectionCriteria filter : subCriteria)
    {
      if (!filter.willMatchAfterBind(connection, bindDN, authType,
          isSecure))
      {
        return false;
      }
    }

    return true;
  }
}
