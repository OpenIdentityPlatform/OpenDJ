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
import org.opends.server.api.ClientConnection;
import org.opends.server.authorization.dseecompat.PatternDN;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

/**
 * This class defines the client bind DN criteria.
 * A client connection matches the criteria when the DN used by
 * the client to bind matches at least one of the DN patterns.
 */
public class BindDnCriteria implements NetworkGroupCriterion {

  private Collection<PatternDN> patternDNs;

  /**
   * Constructor.
   */
  public BindDnCriteria() {
    patternDNs = new ArrayList<PatternDN>();
  }

  /**
   * Adds a new bind DN filter to the list of bind DN filters.
   * @param filter The bind DN filter
   * @throws DirectoryException if the filter is not a valid filter
   */
  public void addBindDnFilter(String filter)
  throws DirectoryException {
    patternDNs.add(PatternDN.decode(filter));
  }

  /**
   * {@inheritDoc}
   */
  public boolean match(ClientConnection connection) {
    DN dn = connection.getAuthenticationInfo().getAuthenticationDN();
    return matchAfterBind(connection, dn, null, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean matchAfterBind(ClientConnection connection, DN bindDN,
            AuthenticationType authType, boolean isSecure) {
    if (bindDN == null) {
      return false;
    }
    for (PatternDN patternDN:patternDNs) {
      if (patternDN.matchesDN(bindDN)) {
        return (true);
      }
    }
    return (false);
  }
}
