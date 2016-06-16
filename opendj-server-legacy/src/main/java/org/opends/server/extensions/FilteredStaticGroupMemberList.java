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

import static org.forgerock.util.Reject.*;
import static org.opends.messages.ExtensionMessages.*;

import java.util.Iterator;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.StaticGroup.CompactDn;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.opends.server.types.SearchFilter;

/**
 * This class provides an implementation of the {@code MemberList} class that
 * may be used in conjunction when static groups when additional criteria is to
 * be used to select a subset of the group members.
 */
public class FilteredStaticGroupMemberList extends MemberList
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The base DN below which all returned members should exist. */
  private DN baseDN;

  /** The DN of the static group with which this member list is associated. */
  private DN groupDN;

  /** The entry of the next entry that matches the member list criteria. */
  private Entry nextMatchingEntry;

  /** The iterator used to traverse the set of member DNs. */
  private Iterator<CompactDn> memberDNIterator;

  /** The membership exception that should be thrown the next time a member is requested. */
  private MembershipException nextMembershipException;

  /** The search filter that all returned members should match. */
  private SearchFilter filter;

  /** The search scope to apply against the base DN for the member subset. */
  private SearchScope scope;

  private final ServerContext serverContext;

  /**
   * Creates a new filtered static group member list with the provided
   * information.
   *
   * @param serverContext
   *            The server context.
   * @param  groupDN    The DN of the static group with which this member list
   *                    is associated.
   * @param  memberDNs  The set of DNs for the users that are members of the
   *                    associated static group.
   * @param  baseDN     The base DN below which all returned members should
   *                    exist.  If this is {@code null}, then all members will
   *                    be considered to match the base and scope criteria.
   * @param  scope      The search scope to apply against the base DN when
   *                    selecting eligible members.
   * @param  filter     The search filter which all returned members should
   *                    match.  If this is {@code null}, then all members will
   *                    be considered eligible.
   */
  public FilteredStaticGroupMemberList(ServerContext serverContext, DN groupDN, Set<CompactDn> memberDNs, DN baseDN,
      SearchScope scope, SearchFilter filter)
  {
    ifNull(groupDN, memberDNs);

    this.serverContext = serverContext;
    this.groupDN   = groupDN;
    this.memberDNIterator = memberDNs.iterator();
    this.baseDN = baseDN;
    this.filter = filter;
    this.scope = scope != null ? scope : SearchScope.WHOLE_SUBTREE;

    nextMemberInternal();
  }

  /**
   * Attempts to find the next member that matches the associated criteria.
   * When this method returns, if {@code nextMembershipException} is
   * non-{@code null}, then that exception should be thrown on the next attempt
   * to retrieve a member.  If {@code nextMatchingEntry} is non-{@code null},
   * then that entry should be returned on the next attempt to retrieve a
   * member.  If both are {@code null}, then there are no more members to
   * return.
   */
  private void nextMemberInternal()
  {
    while (memberDNIterator.hasNext())
    {
      DN nextDN = null;
      try
      {
        nextDN = memberDNIterator.next().toDn(serverContext);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        logger.traceException(e);
        nextMembershipException = new MembershipException(ERR_STATICMEMBERS_CANNOT_DECODE_DN.get(nextDN, groupDN,
            e.getMessageObject()), true, e);
        return;
      }

      // Check to see if we can eliminate the entry as a possible match purely
      // based on base DN and scope.
      if (baseDN != null)
      {
        switch (scope.asEnum())
        {
          case BASE_OBJECT:
            if (! baseDN.equals(nextDN))
            {
              continue;
            }
            break;

          case SINGLE_LEVEL:
            if (! baseDN.equals(nextDN.parent()))
            {
              continue;
            }
            break;

          case SUBORDINATES:
            if (baseDN.equals(nextDN) || !baseDN.isSuperiorOrEqualTo(nextDN))
            {
              continue;
            }
            break;

          default:
            if (!baseDN.isSuperiorOrEqualTo(nextDN))
            {
              continue;
            }
            break;
        }
      }

      // Get the entry for the potential member.  If we can't, then populate
      // the next membership exception.
      try
      {
        Entry memberEntry = DirectoryConfig.getEntry(nextDN);
        if (memberEntry == null)
        {
          nextMembershipException = new MembershipException(ERR_STATICMEMBERS_NO_SUCH_ENTRY.get(nextDN, groupDN), true);
          return;
        }

        if (filter == null)
        {
          nextMatchingEntry = memberEntry;
          return;
        }
        else if (filter.matchesEntry(memberEntry))
        {
            nextMatchingEntry = memberEntry;
            return;
        }
        else
        {
          continue;
        }
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        LocalizableMessage message = ERR_STATICMEMBERS_CANNOT_GET_ENTRY.
            get(nextDN, groupDN, de.getMessageObject());
        nextMembershipException =
             new MembershipException(message, true, de);
        return;
      }
    }

    // If we've gotten here, then there are no more members.
    nextMatchingEntry       = null;
    nextMembershipException = null;
  }

  @Override
  public boolean hasMoreMembers()
  {
    return memberDNIterator.hasNext()
        && (nextMatchingEntry != null || nextMembershipException != null);
  }

  @Override
  public DN nextMemberDN() throws MembershipException
  {
    if (! memberDNIterator.hasNext())
    {
      return null;
    }

    Entry entry = nextMemberEntry();
    return entry != null ? entry.getName() : null;
  }

  @Override
  public Entry nextMemberEntry() throws MembershipException
  {
    if (! memberDNIterator.hasNext())
    {
      return null;
    }
    if (nextMembershipException != null)
    {
      MembershipException me = nextMembershipException;
      nextMembershipException = null;
      nextMemberInternal();
      throw me;
    }

    Entry e = nextMatchingEntry;
    nextMatchingEntry = null;
    nextMemberInternal();
    return e;
  }

  @Override
  public void close()
  {
    // No implementation is required.
  }
}
