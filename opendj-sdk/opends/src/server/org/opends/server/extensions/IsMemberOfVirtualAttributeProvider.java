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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.*;

import org.opends.server.admin.std.server.IsMemberOfVirtualAttributeCfg;
import org.opends.server.api.Group;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements a virtual attribute provider that is meant to serve the
 * isMemberOf operational attribute.  This attribute will be used to provide a
 * list of all groups in which the specified user is a member.
 */
public class IsMemberOfVirtualAttributeProvider
       extends VirtualAttributeProvider<IsMemberOfVirtualAttributeCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new instance of this entryDN virtual attribute provider.
   */
  public IsMemberOfVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  /** {@inheritDoc} */
  @Override()
  public boolean isMultiValued()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,
                                       VirtualAttributeRule rule)
  {
    // FIXME -- This probably isn't the most efficient implementation.
    Set<AttributeValue> values = null;
    for (Group<?> g : DirectoryServer.getGroupManager().getGroupInstances())
    {
      try
      {
        if (g.isMember(entry))
        {
          AttributeValue value = AttributeValues.create(
              rule.getAttributeType(), g.getGroupDN().toString());
          if (values == null)
          {
            values = Collections.singleton(value);
          }
          else if (values.size() == 1)
          {
            Set<AttributeValue> tmp = new HashSet<AttributeValue>(2);
            tmp.addAll(values);
            tmp.add(value);
            values = tmp;
          }
          else
          {
            values.add(value);
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    if (values != null)
    {
      return Collections.unmodifiableSet(values);
    }
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override()
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule,
                          AttributeValue value)
  {
    try
    {
      DN groupDN = DN.decode(value.getValue());
      Group<?> g = DirectoryServer.getGroupManager().getGroupInstance(groupDN);
      return g != null && g.isMember(entry);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return false;
    }
  }

  /** {@inheritDoc} */
  @Override()
  public boolean hasAnyValue(Entry entry, VirtualAttributeRule rule,
                             Collection<AttributeValue> values)
  {
    for (AttributeValue value : values)
    {
      if (hasValue(entry, rule, value))
      {
        return true;
      }
    }

    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    // DNs cannot be used in substring matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
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
  @Override()
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

  /** {@inheritDoc} */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    SearchFilter filter = searchOperation.getFilter();
    Group<?> group = extractGroup(rule.getAttributeType(), filter);
    if (group == null)
    {
      return;
    }

    try
    {
      // Check for nested groups to see if we need to keep track of returned entries
      List<DN> nestedGroupsDNs = group.getNestedGroupDNs();
      HashSet<String> returnedDNs = null;
      if (!nestedGroupsDNs.isEmpty())
      {
        returnedDNs = new HashSet<String>();
      }
      returnGroupMembers(searchOperation, filter, group.getMembers(), returnedDNs);
      // Now check members of nested groups
      for (DN dn : nestedGroupsDNs)
      {
        group = DirectoryServer.getGroupManager().getGroupInstance(dn);
        returnGroupMembers(searchOperation, filter, group.getMembers(), returnedDNs);
      }
    }
    catch (DirectoryException de)
    {
      searchOperation.setResponseData(de);
    }
  }

  private void returnGroupMembers(SearchOperation searchOperation, SearchFilter filter,
                                  MemberList memberList, Set<String> returnedDNs)
          throws DirectoryException
  {
    DN baseDN = searchOperation.getBaseDN();
    SearchScope scope  = searchOperation.getScope();
    while (memberList.hasMoreMembers())
    {
      try
      {
        Entry e = memberList.nextMemberEntry();
        if (e.matchesBaseAndScope(baseDN, scope) &&
            filter.matchesEntry(e))
        {
          if (returnedDNs == null
              || returnedDNs.add(e.getDN().toNormalizedString()))
          {
            if (!searchOperation.returnEntry(e, null))
            {
              return;
            }
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
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
            DN dn = DN.decode(filter.getAssertionValue().getValue());
            return DirectoryServer.getGroupManager().getGroupInstance(dn);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
        break;
    }

    return null;
  }
}

