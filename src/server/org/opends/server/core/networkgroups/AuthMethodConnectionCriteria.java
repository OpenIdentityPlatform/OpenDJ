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



import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.opends.server.admin.std.meta.NetworkGroupCfgDefn.AllowedAuthMethod;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;



/**
 * A connection criteria which matches connections authenticated using a
 * permitted authentication method.
 */

final class AuthMethodConnectionCriteria implements ConnectionCriteria
{

  // The set of allowed authentication methods.
  private final Set<AllowedAuthMethod> authMethods;



  /**
   * Creates a new authentication method connection criteria using the
   * provided allowed authentication methods.
   *
   * @param authMethods
   *          The allowed authentication methods.
   */
  public AuthMethodConnectionCriteria(
      Collection<AllowedAuthMethod> authMethods)
  {
    this.authMethods = EnumSet.copyOf(authMethods);
  }



  /**
   * {@inheritDoc}
   */
  public boolean matches(ClientConnection connection)
  {
    AuthenticationInfo authInfo = connection.getAuthenticationInfo();

    for (AllowedAuthMethod method : authMethods)
    {
      switch (method)
      {
      case ANONYMOUS:
        if (!authInfo.isAuthenticated())
        {
          return true;
        }
        break;
      case SIMPLE:
        if (authInfo.hasAuthenticationType(AuthenticationType.SIMPLE))
        {
          return true;
        }
        break;
      case SASL:
        if (authInfo.hasAuthenticationType(AuthenticationType.SASL))
        {
          return true;
        }
        break;
      }
    }

    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean willMatchAfterBind(ClientConnection connection,
      DN bindDN, AuthenticationType authType, boolean isSecure)
  {
    for (AllowedAuthMethod method : authMethods)
    {
      switch (method)
      {
      case ANONYMOUS:
        if (bindDN.toNormalizedString().length() == 0)
        {
          return true;
        }
        break;
      case SIMPLE:
        if (authType == AuthenticationType.SIMPLE
            && bindDN.toNormalizedString().length() > 0)
        {
          return true;
        }
        break;
      case SASL:
        if (authType == AuthenticationType.SASL)
        {
          return true;
        }
        break;
      }
    }

    return false;
  }
}
