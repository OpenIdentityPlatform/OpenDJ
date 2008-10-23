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
 * This class defines a Security criteria.
 * The criteria specifies whether all connections are allowed
 * or only secured connections.
 * A connection is considered secure if it takes place over
 * SSL or with StartTLS.
 */
public class SecurityCriteria implements NetworkGroupCriterion {

  // If the Security Criteria is enabled,
  // a connection matches only if it is secured
  // Otherwise (Security Criteria disabled),
  // all connections match
  private boolean enabled = true;

  /**
   * Constructor.
   *
   * @param isEnabled boolean indicating if security is mandatory
   */
  public SecurityCriteria(boolean isEnabled) {
    enabled = isEnabled;
  }

  /**
   * {@inheritDoc}
   */
  public boolean match(ClientConnection connection) {
    return (matchAfterBind(null, null, null, connection.isSecure()));
  }

  /**
   * {@inheritDoc}
   */
  public boolean matchAfterBind(ClientConnection connection, DN bindDN,
      AuthenticationType authType, boolean isSecure) {
    if (enabled) {
      return isSecure;
    } else {
      return true;
    }
  }
}
