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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.MemberVirtualAttributeCfg;
import org.opends.server.api.Group;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.VirtualAttributeRule;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;



/**
 * This class implements a virtual attribute provider that works in conjunction
 * with virtual static groups to generate the values for the member or
 * uniqueMember attribute.
 */
public class MemberVirtualAttributeProvider
       extends VirtualAttributeProvider<MemberVirtualAttributeCfg>
       implements ConfigurationChangeListener<MemberVirtualAttributeCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The current configuration for this member virtual attribute.
  private MemberVirtualAttributeCfg currentConfig;



  /**
   * Creates a new instance of this member virtual attribute provider.
   */
  public MemberVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeVirtualAttributeProvider(
                            MemberVirtualAttributeCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addMemberChangeListener(this);
    currentConfig = configuration;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMultiValued()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,
                                       VirtualAttributeRule rule)
  {
    if (! currentConfig.isAllowRetrievingMembership())
    {
      return Collections.emptySet();
    }

    Group g = DirectoryServer.getGroupManager().getGroupInstance(entry.getDN());
    if (g == null)
    {
      return Collections.emptySet();
    }

    HashSet<AttributeValue> values = new HashSet<AttributeValue>();
    try
    {
      MemberList memberList = g.getMembers();
      while (memberList.hasMoreMembers())
      {
        try
        {
          DN memberDN = memberList.nextMemberDN();
          if (memberDN != null)
          {
            values.add(AttributeValues.create(rule.getAttributeType(),
                                          memberDN.toString()));
          }
        }
        catch (MembershipException me)
        {
          if (! me.continueIterating())
          {
            break;
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

    return Collections.unmodifiableSet(values);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    Group g = DirectoryServer.getGroupManager().getGroupInstance(entry.getDN());
    if (g == null)
    {
      return false;
    }

    try
    {
      MemberList memberList = g.getMembers();
      while (memberList.hasMoreMembers())
      {
        try
        {
          DN memberDN = memberList.nextMemberDN();
          if (memberDN != null)
          {
            memberList.close();
            return true;
          }
        }
        catch (MembershipException me)
        {
          if (! me.continueIterating())
          {
            break;
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

    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule,
                          AttributeValue value)
  {
    Group g = DirectoryServer.getGroupManager().getGroupInstance(entry.getDN());
    if (g == null)
    {
      return false;
    }

    try
    {
      return g.isMember(DN.decode(value.getValue()));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasAnyValue(Entry entry, VirtualAttributeRule rule,
                             Collection<AttributeValue> values)
  {
    for (AttributeValue v : values)
    {
      if (hasValue(entry, rule, v))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * {@inheritDoc}
   */
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }



  /**
   * {@inheritDoc}.
   */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation)
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    return;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      MemberVirtualAttributeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // The new configuration should always be acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 MemberVirtualAttributeCfg configuration)
  {
    // Just accept the new configuration as-is.
    currentConfig = configuration;

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

