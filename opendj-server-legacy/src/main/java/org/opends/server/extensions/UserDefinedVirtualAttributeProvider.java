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
 * Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;

/**
 * This class implements a virtual attribute provider that allows administrators
 * to define their own values that will be inserted into any entry that matches
 * the criteria defined in the virtual attribute rule.  This can be used to
 * provide functionality like Class of Service (CoS) in the Sun Java System
 * Directory Server.
 */
public class UserDefinedVirtualAttributeProvider
       extends VirtualAttributeProvider<UserDefinedVirtualAttributeCfg>
       implements ConfigurationChangeListener<UserDefinedVirtualAttributeCfg>
{
  /** The current configuration for this virtual attribute provider. */
  private UserDefinedVirtualAttributeCfg currentConfig;



  /**
   * Creates a new instance of this member virtual attribute provider.
   */
  public UserDefinedVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }



  /** {@inheritDoc} */
  @Override
  public void initializeVirtualAttributeProvider(
                            UserDefinedVirtualAttributeCfg configuration)
         throws ConfigException, InitializationException
  {
    this.currentConfig = configuration;
    configuration.addUserDefinedChangeListener(this);
  }



  /** {@inheritDoc} */
  @Override
  public void finalizeVirtualAttributeProvider()
  {
    currentConfig.removeUserDefinedChangeListener(this);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isMultiValued()
  {
    return currentConfig == null || currentConfig.getValue().size() > 1;
  }



  /** {@inheritDoc} */
  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    Set<String> userDefinedValues = currentConfig.getValue();

    switch (userDefinedValues.size()) {
    case 0:
      return Attributes.empty(rule.getAttributeType());
    case 1:
      String valueString = userDefinedValues.iterator().next();
      return Attributes.create(rule.getAttributeType(), valueString);
    default:
      AttributeBuilder builder = new AttributeBuilder(rule.getAttributeType());
      builder.addAllStrings(userDefinedValues);
      return builder.toAttribute();
    }
  }



  /** {@inheritDoc} */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    // We will not allow searches based only on user-defined virtual attributes.
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
                      UserDefinedVirtualAttributeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The new configuration should always be acceptable.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 UserDefinedVirtualAttributeCfg configuration)
  {
    // Just accept the new configuration as-is.
    currentConfig = configuration;

    return new ConfigChangeResult();
  }
}

