/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.extensions;
import org.forgerock.i18n.LocalizableMessage;




import org.forgerock.i18n.LocalizedIllegalArgumentException;

import java.util.Iterator;
import java.util.Set;

import org.forgerock.opendj.ldap.DN.CompactDn;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.StaticGroup.*;
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

  /**
   * Creates a new simple static group member list with the provided set of
   * member DNs.
   *
   * @param  groupDN    The DN of the static group with which this member list
   *                    is associated.
   * @param  memberDNs  The set of DNs for the users that are members of the
   *                    associated static group.
   */
  public SimpleStaticGroupMemberList(DN groupDN, Set<CompactDn> memberDNs)
  {
    ifNull(groupDN, memberDNs);
    this.groupDN   = groupDN;
    this.memberDNIterator = memberDNs.iterator();
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasMoreMembers()
  {
    return memberDNIterator.hasNext();
  }

  /** {@inheritDoc} */
  @Override
  public DN nextMemberDN()
         throws MembershipException
  {
    DN dn = null;
    if (memberDNIterator.hasNext())
    {
      try
      {
        dn = fromCompactDn(memberDNIterator.next());
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

  /** {@inheritDoc} */
  @Override
  public Entry nextMemberEntry() throws MembershipException
  {
    if (memberDNIterator.hasNext())
    {
      CompactDn memberDN = memberDNIterator.next();

      try
      {
        Entry memberEntry = DirectoryConfig.getEntry(fromCompactDn(memberDN));
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

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    // No implementation is required.
  }
}

