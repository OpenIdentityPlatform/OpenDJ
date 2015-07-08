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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.*;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.admin.std.server.EntryDNVirtualAttributeCfg;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;

import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements a virtual attribute provider that is meant to serve the
 * entryDN operational attribute as described in draft-zeilenga-ldap-entrydn.
 */
public class EntryDNVirtualAttributeProvider
       extends VirtualAttributeProvider<EntryDNVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this entryDN virtual attribute provider.
   */
  public EntryDNVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMultiValued()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    String dnString = entry.getName().toString();
    return Attributes.create(rule.getAttributeType(), dnString);
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // This virtual attribute provider will always generate a value.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule, ByteString value)
  {
    try
    {
      MatchingRule eqRule = rule.getAttributeType().getEqualityMatchingRule();
      ByteString dn = ByteString.valueOf(entry.getName().toString());
      ByteString normalizedDN = eqRule.normalizeAttributeValue(dn);
      ByteString normalizedValue = eqRule.normalizeAttributeValue(value);
      return normalizedDN.equals(normalizedValue);
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return false;
    }
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
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
   *   <LI>It is an OR filter in which all of the components are equality
   *       filters targeting the associated attribute type.</LI>
   * </UL>
   * This virtual attribute also can be optimized as pre-indexed.
   */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return isSearchable(rule.getAttributeType(), searchOperation.getFilter(), 0);
  }




  /**
   * Indicates whether the provided search filter is one that may be used with
   * this virtual attribute provider, optionally operating in a recursive manner
   * to make the determination.
   *
   * @param  attributeType  The attribute type used to hold the entryDN value.
   * @param  searchFilter   The search filter for which to make the
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

      case OR:
        if (depth >= MAX_NESTED_FILTER_DEPTH)
        {
          return false;
        }

        for (SearchFilter f : filter.getFilterComponents())
        {
          if (! isSearchable(attributeType, f, depth+1))
          {
            return false;
          }
        }
        return true;

      case EQUALITY:
        return filter.getAttributeType().equals(attributeType);

      default:
        return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    SearchFilter      filter = searchOperation.getFilter();
    LinkedHashSet<DN> dnSet  = new LinkedHashSet<>();
    extractDNs(rule.getAttributeType(), filter, dnSet);

    if (dnSet.isEmpty())
    {
      return;
    }

    DN          baseDN = searchOperation.getBaseDN();
    SearchScope scope  = searchOperation.getScope();
    for (DN dn : dnSet)
    {
      if (! dn.matchesBaseAndScope(baseDN, scope))
      {
        continue;
      }

      try
      {
        Entry entry = DirectoryServer.getEntry(dn);
        if (entry != null && filter.matchesEntry(entry))
        {
          searchOperation.returnEntry(entry, null);
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }



  /**
   * Extracts the user DNs from the provided filter, operating recursively as
   * necessary, and adds them to the provided set.
   *
   * @param  attributeType  The attribute type holding the entryDN value.
   * @param  filter         The search filter to be processed.
   * @param  dnSet          The set into which the identified DNs should be
   *                        placed.
   */
  private void extractDNs(AttributeType attributeType, SearchFilter filter,
                          LinkedHashSet<DN> dnSet)
  {
    switch (filter.getFilterType())
    {
      case AND:
      case OR:
        for (SearchFilter f : filter.getFilterComponents())
        {
          extractDNs(attributeType, f, dnSet);
        }
        break;

      case EQUALITY:
        if (filter.getAttributeType().equals(attributeType))
        {
          try
          {
            dnSet.add(DN.decode(filter.getAssertionValue()));
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
        break;
    }
  }
}

