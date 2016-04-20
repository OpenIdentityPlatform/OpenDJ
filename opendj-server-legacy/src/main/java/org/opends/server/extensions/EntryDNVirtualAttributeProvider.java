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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.EntryDNVirtualAttributeCfg;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;

import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements a virtual attribute provider that is meant to serve the
 * entryDN operational attribute as described in draft-zeilenga-ldap-entrydn.
 */
public class EntryDNVirtualAttributeProvider
       extends VirtualAttributeProvider<EntryDNVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Creates a new instance of this entryDN virtual attribute provider. */
  public EntryDNVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  @Override
  public boolean isMultiValued()
  {
    return false;
  }

  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    String dnString = entry.getName().toString();
    return Attributes.create(rule.getAttributeType(), dnString);
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // This virtual attribute provider will always generate a value.
    return true;
  }

  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule, ByteString value)
  {
    try
    {
      MatchingRule eqRule = rule.getAttributeType().getEqualityMatchingRule();
      ByteString dn = ByteString.valueOfUtf8(entry.getName().toString());
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

  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    SearchFilter      filter = searchOperation.getFilter();
    Set<DN> dnSet = new LinkedHashSet<>();
    extractDNs(rule.getAttributeType(), filter, dnSet);

    if (dnSet.isEmpty())
    {
      return;
    }

    DN          baseDN = searchOperation.getBaseDN();
    SearchScope scope  = searchOperation.getScope();
    for (DN dn : dnSet)
    {
      if (! dn.isInScopeOf(baseDN, scope))
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
  private void extractDNs(AttributeType attributeType, SearchFilter filter, Set<DN> dnSet)
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
            dnSet.add(DN.valueOf(filter.getAssertionValue()));
          }
          catch (LocalizedIllegalArgumentException e)
          {
            logger.traceException(e);
          }
        }
        break;
    }
  }
}
