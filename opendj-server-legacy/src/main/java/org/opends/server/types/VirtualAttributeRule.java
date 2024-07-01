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
package org.opends.server.types;

import static org.forgerock.util.Reject.*;

import java.util.Collection;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn;
import org.forgerock.opendj.server.config.server.VirtualAttributeCfg;
import org.forgerock.util.Utils;
import org.opends.server.api.Group;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;

/**
 * This class defines a virtual attribute rule, which associates a
 * virtual attribute provider with its associated configuration,
 * including the attribute type for which the values should be
 * generated; the base DN(s), group DN(s), and search filter(s) that
 * should be used to identify which entries should have the virtual
 * attribute, and how conflicts between real and virtual values should
 * be handled.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class VirtualAttributeRule
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The attribute type for which the values should be generated. */
  private final AttributeType attributeType;
  /** The set of base DNs for branches that are eligible to have this virtual attribute. */
  private final Set<DN> baseDNs;
  /** The scope of entries eligible to have this virtual attribute, under the base DNs. */
  private final SearchScope scope;
  /** The set of DNs for groups whose members are eligible to have this virtual attribute. */
  private final Set<DN> groupDNs;
  /** The set of search filters for entries that are eligible to have this virtual attribute. */
  private final Set<SearchFilter> filters;
  /** The virtual attribute provider used to generate the values. */
  private final VirtualAttributeProvider<? extends VirtualAttributeCfg> provider;
  /**
   * The behavior that should be exhibited for entries that already have real
   * values for the target attribute.
   */
  private final VirtualAttributeCfgDefn.ConflictBehavior conflictBehavior;

  /**
   * Creates a new virtual attribute rule with the provided information.
   *
   * @param  attributeType     The attribute type for which the values
   *                           should be generated.
   * @param  provider          The virtual attribute provider to use
   *                           to generate the values.
   * @param  baseDNs           The set of base DNs for branches that
   *                           are eligible to have this virtual attribute.
   * @param  scope             The scope of entries, related to the
   *                           base DNs, that are eligible to have
   *                           this virtual attribute.
   * @param  groupDNs          The set of DNs for groups whose members
   *                           are eligible to have this virtual attribute.
   * @param  filters           The set of search filters for entries
   *                           that are eligible to have this virtual attribute.
   * @param  conflictBehavior  The behavior that the server should
   *                           exhibit for entries that already have
   *                           one or more real values for the target
   *                           attribute.
   */
  public VirtualAttributeRule(AttributeType attributeType,
              VirtualAttributeProvider<? extends VirtualAttributeCfg>
                   provider,
              Set<DN> baseDNs, SearchScope scope, Set<DN> groupDNs,
              Set<SearchFilter> filters,
              VirtualAttributeCfgDefn.ConflictBehavior
                   conflictBehavior)
  {
    ifNull(attributeType, provider, baseDNs, groupDNs);
    ifNull(filters, conflictBehavior);

    this.attributeType    = attributeType;
    this.provider         = provider;
    this.baseDNs          = baseDNs;
    this.scope            = scope;
    this.groupDNs         = groupDNs;
    this.filters          = filters;
    this.conflictBehavior = conflictBehavior;
  }

  /**
   * Retrieves the attribute type for which the values should be generated.
   *
   * @return  The attribute type for which the values should be generated.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }

  /**
   * Retrieves the virtual attribute provider used to generate the values.
   *
   * @return  The virtual attribute provider to use to generate the values.
   */
  public VirtualAttributeProvider<? extends VirtualAttributeCfg>
              getProvider()
  {
    return provider;
  }

  /**
   * Retrieves the set of base DNs for branches that are eligible to
   * have this virtual attribute.
   *
   * @return  The set of base DNs for branches that are eligible to
   *          have this virtual attribute.
   */
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

  /**
   * Retrieves the scope of entries in the base DNs that are eligible
   * to have this virtual attribute.
   *
   * @return  The scope of entries that are eligible to
   *          have this virtual attribute.
   */
  public SearchScope getScope()
  {
    return scope;
  }

  /**
   * Retrieves the set of DNs for groups whose members are eligible to
   * have this virtual attribute.
   *
   * @return  The set of DNs for groups whose members are eligible to
   *          have this virtual attribute.
   */
  public Set<DN> getGroupDNs()
  {
    return groupDNs;
  }

  /**
   * Retrieves the set of search filters for entries that are eligible
   * to have this virtual attribute.
   *
   * @return  The set of search filters for entries that are eligible
   *          to have this virtual attribute.
   */
  public Set<SearchFilter> getFilters()
  {
    return filters;
  }

  /**
   * Retrieves the behavior that the server should exhibit for entries
   * that already have one or more real values for the target attribute.
   *
   * @return  The behavior that the server should exhibit for entries
   *          that already have one or more real values for the target
   *          attribute.
   */
  public VirtualAttributeCfgDefn.ConflictBehavior
              getConflictBehavior()
  {
    return conflictBehavior;
  }

  /**
   * Indicates whether this virtual attribute rule applies to the
   * provided entry, taking into account the eligibility requirements
   * defined in the rule.
   *
   * @param  entry  The entry for which to make the determination.
   *
   * @return  {@code true} if this virtual attribute rule may be used
   *          to generate values for the entry, or {@code false} if not.
   */
  public boolean appliesToEntry(Entry entry)
  {
    // We'll do this in order of expense so that the checks which are
    // potentially most expensive are done last.  First, check to see
    // if real values should override virtual ones and if so whether
    // the entry already has virtual values.
    return (conflictBehavior != VirtualAttributeCfgDefn.ConflictBehavior.REAL_OVERRIDES_VIRTUAL
            || !entry.hasAttribute(attributeType))
        // If there are any base DNs defined, then the entry must be below one of them.
        && (baseDNs.isEmpty() || matchesAnyBaseDN(entry.getName()))
        // If there are any search filters defined, then the entry must match one of them.
        && (filters.isEmpty() || matchesAnyFilter(entry))
        // If there are any group memberships defined, then the entry must be a member of one of them.
        && (groupDNs.isEmpty() || isMemberOfAnyGroup(entry));
  }

  private boolean matchesAnyBaseDN(DN entryDN)
  {
    for (DN dn : baseDNs)
    {
      if (entryDN.isInScopeOf(dn, scope))
      {
        return true;
      }
    }
    return false;
  }

  private boolean matchesAnyFilter(Entry entry)
  {
    for (SearchFilter filter : filters)
    {
      try
      {
        if (filter.matchesEntry(entry))
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

  private boolean isMemberOfAnyGroup(Entry entry)
  {
    for (DN dn : groupDNs)
    {
      try
      {
        Group<?> group = DirectoryServer.getGroupManager().getGroupInstance(dn);
        if (group != null && group.isMember(entry))
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
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Appends a string representation of this virtual attribute rule to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be written.
   */
  private void toString(StringBuilder buffer)
  {
    buffer.append("VirtualAttributeRule(attrType=");
    buffer.append(attributeType.getNameOrOID());
    buffer.append(", providerDN=\"").append(provider.getClass().getName());

    buffer.append("\", baseDNs={");
    append(buffer, baseDNs);

    buffer.append("}, scope=").append(scope);

    buffer.append(", groupDNs={");
    append(buffer, groupDNs);
    buffer.append("}, filters={");
    append(buffer, filters);

    buffer.append("}, conflictBehavior=").append(conflictBehavior);
    buffer.append(")");
  }

  private void append(StringBuilder buffer, Collection<?> col)
  {
    if (!col.isEmpty())
    {
      buffer.append("\"");
      Utils.joinAsString(buffer, "\", \"", col);
      buffer.append("\"");
    }
  }
}
