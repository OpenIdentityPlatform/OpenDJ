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



import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;



/**
 * A connection criteria which matches connections which are secured
 * using SSL or TLS.
 */
final class SecurityConnectionCriteria implements ConnectionCriteria
{

  /**
   * A connection criteria which does not require a secured connection.
   */
  public static final SecurityConnectionCriteria SECURITY_NOT_REQUIRED =
      new SecurityConnectionCriteria(false);

  /**
   * A connection criteria which requires a secured connection.
   */
  public static final SecurityConnectionCriteria SECURITY_REQUIRED =
      new SecurityConnectionCriteria(true);

  // Indicates whether or not the connection must be secured.
  private final boolean mustBeSecured;



  /**
   * Creates a new security connection criteria.
   *
   * @param mustBeSecured
   *          Indicates whether or not the connection must be secured.
   */
  private SecurityConnectionCriteria(boolean mustBeSecured)
  {
    this.mustBeSecured = mustBeSecured;
  }



  /**
   * {@inheritDoc}
   */
  public boolean matches(ClientConnection connection)
  {
    return willMatchAfterBind(null, null, null, connection.isSecure());
  }



  /**
   * {@inheritDoc}
   */
  public boolean willMatchAfterBind(ClientConnection connection,
      DN bindDN, AuthenticationType authType, boolean isSecure)
  {
    if (mustBeSecured)
    {
      return isSecure;
    }
    else
    {
      return true;
    }
  }
}
