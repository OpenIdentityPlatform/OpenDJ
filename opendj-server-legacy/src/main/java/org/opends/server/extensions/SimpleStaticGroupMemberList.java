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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;

import java.util.Iterator;
import java.util.Set;

import org.opends.server.core.ServerContext;
import org.opends.server.extensions.StaticGroup.CompactDn;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import static org.opends.messages.ExtensionMessages.*;
import static org.forgerock.util.Reject.*;

/**
 * This class provides an implementation of the {@code MemberList} class that
 * may be used in conjunction when static groups when no additional criteria is
 * to be used to select a subset of the group members.
 */
public class SimpleStaticGroupMemberList extends MemberList
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN of the static group with which this member list is associated. */
  private DN groupDN;

  /** The iterator used to traverse the set of member DNs. */
  private Iterator<CompactDn> memberDNIterator;

  private final ServerContext serverContext;

  /**
   * Creates a new simple static group member list with the provided set of
   * member DNs.
   *
   * @param serverContext
   *            The server context.
   * @param  groupDN    The DN of the static group with which this member list
   *                    is associated.
   * @param  memberDNs  The set of DNs for the users that are members of the
   *                    associated static group.
   */
  public SimpleStaticGroupMemberList(ServerContext serverContext, DN groupDN, Set<CompactDn> memberDNs)
  {
    ifNull(groupDN, memberDNs);
    this.serverContext = serverContext;
    this.groupDN   = groupDN;
    this.memberDNIterator = memberDNs.iterator();
  }

  @Override
  public boolean hasMoreMembers()
  {
    return memberDNIterator.hasNext();
  }

  @Override
  public DN nextMemberDN()
         throws MembershipException
  {
    DN dn = null;
    if (memberDNIterator.hasNext())
    {
      try
      {
        dn = memberDNIterator.next().toDn(serverContext);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        // Should not happen
        logger.traceException(e);
        throw new MembershipException(ERR_STATICMEMBERS_CANNOT_DECODE_DN.get(dn, groupDN, e.getMessageObject()),
            true, e);
      }
    }

    return dn;
  }

  @Override
  public Entry nextMemberEntry() throws MembershipException
  {
    if (memberDNIterator.hasNext())
    {
      CompactDn memberDN = memberDNIterator.next();

      try
      {
        Entry memberEntry = DirectoryConfig.getEntry(memberDN.toDn(serverContext));
        if (memberEntry == null)
        {
          LocalizableMessage message = ERR_STATICMEMBERS_NO_SUCH_ENTRY.get(memberDN, groupDN);
          throw new MembershipException(message, true);
        }

        return memberEntry;
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);
        throw new MembershipException(ERR_STATICMEMBERS_CANNOT_GET_ENTRY.get(memberDN, groupDN, de.getMessageObject()),
            true, de);
      }
    }

    return null;
  }

  @Override
  public void close()
  {
    // No implementation is required.
  }
}
