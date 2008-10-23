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

import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;

/**
 * This class defines the network group criteria. A criterion is used
 * by the network groups to determine whether a client connection belongs
 * to the network group or not.
 */
public interface NetworkGroupCriterion
{

  /**
   * Checks whether the client connection matches the criteria.
   *
   * @param connection the ClientConnection
   * @return a boolean indicating whether the connection matches the criteria
   */
  public boolean match(ClientConnection connection);

  /**
   * Checks whether the client connection matches the criteria after bind.
   *
   * @param connection the ClientConnection
   * @param bindDN the DN used to bind
   * @param authType the authentication type
   * @param isSecure a boolean indicating whether the connection is secure
   * @return a boolean indicating whether the connection matches the criteria
   */
  public boolean matchAfterBind(ClientConnection connection,
          DN bindDN,
          AuthenticationType authType,
          boolean isSecure);

}
