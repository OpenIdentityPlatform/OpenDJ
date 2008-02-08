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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Iterator;
import java.util.Set;

import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.admin.std.server.VirtualAttributeCfg;
import org.opends.server.api.Group;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.Validator.*;



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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The attribute type for which the values should be generated.
  private final AttributeType attributeType;

  // The set of base DNs for branches that are eligible to have this
  // virtual attribute.
  private final Set<DN> baseDNs;

  // The set of DNs for groups whose members are eligible to have this
  // virtual attribute.
  private final Set<DN> groupDNs;

  // The set of search filters for entries that are eligible to have
  // this virtual attribute.
  private final Set<SearchFilter> filters;

  // The virtual attribute provider used to generate the values.
  private final VirtualAttributeProvider<
                     ? extends VirtualAttributeCfg> provider;

  // The behavior that should be exhibited for entries that already
  // have real values for the target attribute.
  private final VirtualAttributeCfgDefn.ConflictBehavior
                     conflictBehavior;



  /**
   * Creates a new virtual attribute rule with the provided
   * information.
   *
   * @param  attributeType     The attribute type for which the values
   *                           should be generated.
   * @param  provider          The virtual attribute provider to use
   *                           to generate the values.
   * @param  baseDNs           The set of base DNs for branches that
   *                           are eligible to have this virtual
   *                           attribute.
   * @param  groupDNs          The set of DNs for groups whose members
   *                           are eligible to have this virtual
   *                           attribute.
   * @param  filters           The set of search filters for entries
   *                           that are eligible to have this virtual
   *                           attribute.
   * @param  conflictBehavior  The behavior that the server should
   *                           exhibit for entries that already have
   *                           one or more real values for the target
   *                           attribute.
   */
  public VirtualAttributeRule(AttributeType attributeType,
              VirtualAttributeProvider<? extends VirtualAttributeCfg>
                   provider,
              Set<DN> baseDNs, Set<DN> groupDNs,
              Set<SearchFilter> filters,
              VirtualAttributeCfgDefn.ConflictBehavior
                   conflictBehavior)
  {
    ensureNotNull(attributeType, provider, baseDNs, groupDNs);
    ensureNotNull(filters, conflictBehavior);

    this.attributeType    = attributeType;
    this.provider         = provider;
    this.baseDNs          = baseDNs;
    this.groupDNs         = groupDNs;
    this.filters          = filters;
    this.conflictBehavior = conflictBehavior;
  }



  /**
   * Retrieves the attribute type for which the values should be
   * generated.
   *
   * @return  The attribute type for which the values should be
   *          generated.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   *
   * Retrieves the virtual attribute provider used to generate the
   * values.
   *
   * @return  The virtual attribute provider to use to generate the
   *          values.
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
   * that already have one or more real values for the target
   * attribute.
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
   *          to generate values for the entry, or {@code false} if
   *          not.
   */
  public boolean appliesToEntry(Entry entry)
  {
    // We'll do this in order of expense so that the checks which are
    // potentially most expensive are done last.  First, check to see
    // if real values should override virtual ones and if so whether
    // the entry already has virtual values.
    if ((conflictBehavior == VirtualAttributeCfgDefn.ConflictBehavior.
                                  REAL_OVERRIDES_VIRTUAL) &&
        entry.hasAttribute(attributeType))
    {
      return false;
    }

    // If there are any base DNs defined, then the entry must be below
    // one of them.
    DN entryDN = entry.getDN();
    if (! baseDNs.isEmpty())
    {
      boolean found = false;
      for (DN dn : baseDNs)
      {
        if (entryDN.isDescendantOf(dn))
        {
          found = true;
          break;
        }
      }

      if (! found)
      {
        return false;
      }
    }

    // If there are any search filters defined, then the entry must
    // match one of them.
    if (! filters.isEmpty())
    {
      boolean found = false;
      for (SearchFilter filter : filters)
      {
        try
        {
          if (filter.matchesEntry(entry))
          {
            found = true;
            break;
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

      if (! found)
      {
        return false;
      }
    }

    // If there are any group memberships defined, then the entry must
    // be a member of one of them.
    if (! groupDNs.isEmpty())
    {
      boolean found = false;
      for (DN dn : groupDNs)
      {
        try
        {
          Group group =
               DirectoryServer.getGroupManager().getGroupInstance(dn);
          if ((group != null) && group.isMember(entry))
          {
            found = true;
            break;
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

      if (! found)
      {
        return false;
      }
    }

    // If we've gotten here, then the rule is applicable.
    return true;
  }



  /**
   * Retrieves a string representation of this virtual attribute rule.
   *
   * @return  A string representation of this virutal attribute rule.
   */
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
   * @param  buffer  The buffer to which the information should be
   *                 written.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("VirtualAttributeRule(attrType=");
    buffer.append(attributeType.getNameOrOID());
    buffer.append(", providerDN=\"");
    buffer.append(provider.getClass().getName());

    buffer.append("\", baseDNs={");
    if (! baseDNs.isEmpty())
    {
      buffer.append("\"");
      Iterator<DN> iterator = baseDNs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append("\", \"");
        buffer.append(iterator.next());
      }

      buffer.append("\"");
    }

    buffer.append("}, groupDNs={");
    if (! groupDNs.isEmpty())
    {
      buffer.append("\"");
      Iterator<DN> iterator = groupDNs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append("\", \"");
        buffer.append(iterator.next());
      }

      buffer.append("\"");
    }

    buffer.append("}, filters={");
    if (! filters.isEmpty())
    {
      buffer.append("\"");
      Iterator<SearchFilter> iterator = filters.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append("\", \"");
        buffer.append(iterator.next());
      }

      buffer.append("\"");
    }

    buffer.append("}, conflictBehavior=");
    buffer.append(conflictBehavior);
    buffer.append(")");
  }
}

