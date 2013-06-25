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
import org.opends.server.authorization.dseecompat.PatternDN;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;



/**
 * A connection criteria which matches connections authenticated using a
 * permitted bind DN.
 */
final class BindDNConnectionCriteria implements ConnectionCriteria
{

  /**
   * Creates a new bind DN connection criteria using the provided DN
   * patterns.
   *
   * @param patterns
   *          The DN patterns.
   * @return The new bind DN connection criteria.
   */
  public static BindDNConnectionCriteria create(
      Collection<PatternDN> patterns)
  {
    return new BindDNConnectionCriteria(new ArrayList<PatternDN>(
        patterns));
  }



  /**
   * Creates a new bind DN connection criteria using the provided DN
   * pattern string representations.
   *
   * @param patternStrings
   *          The string representation of the DN patterns.
   * @return The new bind DN connection criteria.
   * @throws DirectoryException
   *           If one of the pattern strings is not valid.
   */
  public static BindDNConnectionCriteria decode(
      Collection<String> patternStrings) throws DirectoryException
  {
    List<PatternDN> patterns =
        new ArrayList<PatternDN>(patternStrings.size());

    for (String s : patternStrings)
    {
      patterns.add(PatternDN.decode(s));
    }

    return new BindDNConnectionCriteria(patterns);
  }



  // The list of permitted bind DN patterns.
  private final List<PatternDN> patterns;



  // Private constructor.
  private BindDNConnectionCriteria(List<PatternDN> patterns)
  {
    this.patterns = patterns;
  }



  /**
   * {@inheritDoc}
   */
  public boolean matches(ClientConnection connection)
  {
    DN dn = connection.getAuthenticationInfo().getAuthenticationDN();
    return willMatchAfterBind(connection, dn, null, false);
  }



  /**
   * {@inheritDoc}
   */
  public boolean willMatchAfterBind(ClientConnection connection,
      DN bindDN, AuthenticationType authType, boolean isSecure)
  {
    if (bindDN == null)
    {
      return false;
    }

    for (PatternDN pattern : patterns)
    {
      if (pattern.matchesDN(bindDN))
      {
        return true;
      }
    }

    return false;
  }
}
