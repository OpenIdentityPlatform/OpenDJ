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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.MemberVirtualAttributeCfg;
import org.opends.server.api.Group;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.core.SearchOperation;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.opends.server.types.VirtualAttributeRule;

/**
 * This class implements a virtual attribute provider that works in conjunction
 * with virtual static groups to generate the values for the member or
 * uniqueMember attribute.
 */
public class MemberVirtualAttributeProvider
       extends VirtualAttributeProvider<MemberVirtualAttributeCfg>
       implements ConfigurationChangeListener<MemberVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The current configuration for this member virtual attribute. */
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



  /** {@inheritDoc} */
  @Override
  public void initializeVirtualAttributeProvider(
                            MemberVirtualAttributeCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addMemberChangeListener(this);
    currentConfig = configuration;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isMultiValued()
  {
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    if (! currentConfig.isAllowRetrievingMembership())
    {
      return Attributes.empty(rule.getAttributeType());
    }

    Group<?> g =
      DirectoryServer.getGroupManager().getGroupInstance(entry.getName());
    if (g == null)
    {
      return Attributes.empty(rule.getAttributeType());
    }

    AttributeBuilder builder = new AttributeBuilder(rule.getAttributeType());
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
            builder.add(ByteString.valueOf(memberDN.toString()));
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
      logger.traceException(e);
    }

    return builder.toAttribute();
  }



  /** {@inheritDoc} */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    Group<?> g =
      DirectoryServer.getGroupManager().getGroupInstance(entry.getName());
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
      logger.traceException(e);
    }

    return false;
  }



  /** {@inheritDoc} */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule, ByteString value)
  {
    Group<?> g =
      DirectoryServer.getGroupManager().getGroupInstance(entry.getName());
    if (g == null)
    {
      return false;
    }

    try
    {
      return g.isMember(DN.decode(value));
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    return false;
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



  /** {@inheritDoc} */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    return;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      MemberVirtualAttributeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The new configuration should always be acceptable.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 MemberVirtualAttributeCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}

