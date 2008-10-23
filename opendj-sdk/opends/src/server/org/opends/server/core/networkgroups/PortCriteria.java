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

import java.util.ArrayList;
import java.util.Collection;
import
  org.opends.server.admin.std.meta.NetworkGroupCriteriaCfgDefn.AllowedLDAPPort;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;

/**
 * This class defines the port criteria.
 * A client connection matches the criteria when it is received in
 * a port matching at least one of the specified ports.
 * The port can be "ldap" or "ldaps".
 */
public class PortCriteria implements NetworkGroupCriterion {
  private Collection<String> allowedPorts;

  /**
   * Constructor.
   */
  public PortCriteria() {
    allowedPorts = new ArrayList<String>();
  }

  /**
   * Adds a new allowed LDAP port to the list of allowed LDAP ports.
   * @param port The new LDAP port
   */
  public void addPort(AllowedLDAPPort port) {
    if (port.toString().equals("ldap")) {
      allowedPorts.add("LDAP");
    } else if (port.toString().equals("ldaps")) {
      allowedPorts.add("LDAP+SSL");
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean match(ClientConnection connection) {
    String connectionPort = connection.getConnectionHandler().getProtocol();
    for (String port:allowedPorts) {
      if (connectionPort.equalsIgnoreCase(port)) {
        return true;
      }
    }
    return (false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean matchAfterBind(ClientConnection connection, DN bindDN,
                                AuthenticationType authType, boolean isSecure) {
    return (match(connection));
  }
}
