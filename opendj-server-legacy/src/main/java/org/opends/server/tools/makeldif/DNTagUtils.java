/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;

import org.forgerock.opendj.ldap.DN;

/** Utility class for *DNTag classes. */
final class DNTagUtils
{
  private DNTagUtils()
  {
    // private for utility classes
  }

  static DN generateDNKeepingRDNs(DN dn, int nbRdnsToInclude)
  {
    if (nbRdnsToInclude == 0)
    {
      return dn;
    }
    else if (nbRdnsToInclude > 0)
    {
      int count = Math.min(nbRdnsToInclude, dn.size());
      return dn.localName(count);
    }
    else
    {
      int sz = dn.size();
      int count = Math.min(Math.abs(nbRdnsToInclude), sz);
      return dn.parent(sz - count).localName(count);
    }
  }
}
