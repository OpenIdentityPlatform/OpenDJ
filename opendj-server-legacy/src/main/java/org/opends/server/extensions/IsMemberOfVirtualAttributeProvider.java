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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.server.config.server.IsMemberOfVirtualAttributeCfg;
import org.opends.server.api.Group;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.*;

import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements a virtual attribute provider that is meant to serve the
 * isMemberOf operational attribute.  This attribute will be used to provide a
 * list of all groups in which the specified user is a member.
 */
public class IsMemberOfVirtualAttributeProvider
       extends VirtualAttributeProvider<IsMemberOfVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Creates a new instance of this entryDN virtual attribute provider. */
  public IsMemberOfVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  @Override
  public boolean isMultiValued()
  {
    return true;
  }

  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    // FIXME -- This probably isn't the most efficient implementation.
    AttributeBuilder builder = new AttributeBuilder(rule.getAttributeType());
    for (Group<?> g : DirectoryServer.getGroupManager().getGroupInstances())
    {
      try
      {
        if (g.isMember(entry))
        {
          builder.add(g.getGroupDN().toString());
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
    return builder.toAttribute();
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // FIXME -- This probably isn't the most efficient implementation.
    for (Group<?> g : DirectoryServer.getGroupManager().getGroupInstances())
    {
      try
      {
        if (g.isMember(entry))
        {
          return true;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    return false;
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule,
                          ByteString value)
  {
    try
    {
      DN groupDN = DN.valueOf(value);
      Group<?> g = DirectoryServer.getGroupManager().getGroupInstance(groupDN);
      return g != null && g.isMember(entry);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      return false;
    }
  }

  @Override
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    // DNs cannot be used in substring matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  @Override
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DNs cannot be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }

  /**
   * {@inheritDoc}.  This virtual attribute will support search operations only
   * if one of the following is true about the search filter:
   * <UL>
   *   <LI>It is an equality filter targeting the associated attribute
   *       type.</LI>
   *   <LI>It is an AND filter in which at least one of the components is an
   *       equality filter targeting the associated attribute type.</LI>
   * </UL>
   * Searching for this virtual attribute cannot be pre-indexed and thus,
   * it should not be searchable when pre-indexed is required.
   */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return !isPreIndexed &&
        isSearchable(rule.getAttributeType(), searchOperation.getFilter(), 0);
  }

  /**
   * Indicates whether the provided search filter is one that may be used with
   * this virtual attribute provider, optionally operating in a recursive manner
   * to make the determination.
   *
   * @param  attributeType  The attribute type used to hold the entryDN value.
   * @param  filter         The search filter for which to make the
   *                        determination.
   * @param  depth          The current recursion depth for this processing.
   *
   * @return  {@code true} if the provided filter may be used with this virtual
   *          attribute provider, or {@code false} if not.
   */
  private boolean isSearchable(AttributeType attributeType, SearchFilter filter,
                               int depth)
  {
    switch (filter.getFilterType())
    {
      case AND:
        if (depth >= MAX_NESTED_FILTER_DEPTH)
        {
          return false;
        }

        for (SearchFilter f : filter.getFilterComponents())
        {
          if (isSearchable(attributeType, f, depth+1))
          {
            return true;
          }
        }
        return false;

      case EQUALITY:
        return filter.getAttributeType().equals(attributeType);

      default:
        return false;
    }
  }

  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    Group<?> group = extractGroup(rule.getAttributeType(), searchOperation.getFilter());
    if (group == null)
    {
      return;
    }

    try
    {
      // Check for nested groups to see if we need to keep track of returned entries
      List<DN> nestedGroupsDNs = group.getNestedGroupDNs();
      Set<ByteString> returnedDNs = null;
      if (!nestedGroupsDNs.isEmpty())
      {
        returnedDNs = new HashSet<>();
      }
      if (!returnGroupMembers(searchOperation, group.getMembers(), returnedDNs))
      {
        return;
      }
      // Now check members of nested groups
      for (DN dn : nestedGroupsDNs)
      {
        group = DirectoryServer.getGroupManager().getGroupInstance(dn);
        if (!returnGroupMembers(searchOperation, group.getMembers(), returnedDNs))
        {
          return;
        }
      }
    }
    catch (DirectoryException de)
    {
      searchOperation.setResponseData(de);
    }
  }

  /**
   * @param searchOperation the search operation being processed.
   * @param memberList the list of members of the group being processed.
   * @param returnedDNs a set to store the normalized DNs of entries already returned,
   *                    null if there's no need to track for entries.
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references, or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   * @throws DirectoryException If a problem occurs while attempting to send
   *          the entry to the client and the search should be terminated.
   */
  private boolean returnGroupMembers(SearchOperation searchOperation,
                                  MemberList memberList, Set<ByteString> returnedDNs)
          throws DirectoryException
  {
    DN baseDN = searchOperation.getBaseDN();
    SearchScope scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();
    while (memberList.hasMoreMembers())
    {
      try
      {
        Entry e = memberList.nextMemberEntry();
        if (e.matchesBaseAndScope(baseDN, scope)
            && filter.matchesEntry(e)
            // The set of returned DNs is only used for detecting set membership
            // so it's ok to use the irreversible representation of the DN
            && (returnedDNs == null || returnedDNs.add(e.getName().toNormalizedByteString()))
            && !searchOperation.returnEntry(e, null))
        {
          return false;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
    return true;
  }

  /**
   * Extracts the first group DN encountered in the provided filter, operating
   * recursively as necessary.
   *
   * @param  attributeType  The attribute type holding the entryDN value.
   * @param  filter         The search filter to be processed.
   *
   * @return  The first group encountered in the provided filter, or
   *          {@code null} if there is no match.
   */
  private Group<?> extractGroup(AttributeType attributeType,
      SearchFilter filter)
  {
    switch (filter.getFilterType())
    {
      case AND:
        for (SearchFilter f : filter.getFilterComponents())
        {
          Group<?> g = extractGroup(attributeType, f);
          if (g != null)
          {
            return g;
          }
        }
        break;

      case EQUALITY:
        if (filter.getAttributeType().equals(attributeType))
        {
          try
          {
            DN dn = DN.valueOf(filter.getAssertionValue());
            return DirectoryServer.getGroupManager().getGroupInstance(dn);
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
        break;
    }

    return null;
  }
}
