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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;



/**
 * An interface for filtering connections based on implementation
 * specific criteria. Connection criteria are used by network groups to
 * determine whether a client connection should be associated with a
 * network group or not.
 */
interface ConnectionCriteria
{

  /**
   * A connection criteria which does not match any connections.
   */
  public static final ConnectionCriteria FALSE =
      new ConnectionCriteria()
        {

          /**
           * {@inheritDoc}
           */
          public boolean matches(ClientConnection connection)
          {
            return false;
          }



          /**
           * {@inheritDoc}
           */
          public boolean willMatchAfterBind(
              ClientConnection connection, DN bindDN,
              AuthenticationType authType, boolean isSecure)
          {
            return false;
          }

        };

  /**
   * A connection criteria which matches all connections.
   */
  public static final ConnectionCriteria TRUE =
      new ConnectionCriteria()
        {

          /**
           * {@inheritDoc}
           */
          public boolean matches(ClientConnection connection)
          {
            return true;
          }



          /**
           * {@inheritDoc}
           */
          public boolean willMatchAfterBind(
              ClientConnection connection, DN bindDN,
              AuthenticationType authType, boolean isSecure)
          {
            return true;
          }

        };



  /**
   * Indicates whether or not the provided client connection matches
   * this connection criteria.
   *
   * @param connection
   *          The client connection.
   * @return <code>true</code> if the provided client connection matches
   *         this connection criteria.
   */
  boolean matches(ClientConnection connection);



  /**
   * Indicates whether or not the provided client connection will match
   * this connection criteria using the provided authentication
   * parameters.
   *
   * @param connection
   *          The client connection.
   * @param bindDN
   *          The bind DN which will be used to authenticate.
   * @param authType
   *          The type of authentication which will be performed.
   * @param isSecure
   *          <code>true</code> if the connection will be secured.
   * @return <code>true</code> if the provided client connection will
   *         match this connection criteria using the provided
   *         authentication parameters.
   */
  boolean willMatchAfterBind(ClientConnection connection, DN bindDN,
      AuthenticationType authType, boolean isSecure);

}
