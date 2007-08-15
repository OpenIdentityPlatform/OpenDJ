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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AttributeValuePasswordValidatorCfg;
import org.opends.server.admin.std.server.PasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ByteString;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ExtensionMessages.*;
import org.opends.messages.MessageBuilder;


/**
 * This class provides an OpenDS password validator that may be used to ensure
 * that proposed passwords are not contained in another attribute in the user's
 * entry.
 */
public class AttributeValuePasswordValidator
       extends PasswordValidator<AttributeValuePasswordValidatorCfg>
       implements ConfigurationChangeListener<
                       AttributeValuePasswordValidatorCfg>
{
  // The current configuration for this password validator.
  private AttributeValuePasswordValidatorCfg currentConfig;



  /**
   * Creates a new instance of this attribute value password validator.
   */
  public AttributeValuePasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordValidator(
                   AttributeValuePasswordValidatorCfg configuration)
  {
    configuration.addAttributeValueChangeListener(this);
    currentConfig = configuration;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizePasswordValidator()
  {
    currentConfig.removeAttributeValueChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      MessageBuilder invalidReason)
  {
    // Get a handle to the current configuration.
    AttributeValuePasswordValidatorCfg config = currentConfig;


    // Get the string representation (both forward and reversed) for the
    // password.
    String password = newPassword.stringValue();
    String reversed = new StringBuilder(password).reverse().toString();


    // If we should check a specific set of attributes, then do that now.
    // Otherwise, check all user attributes.
    Set<AttributeType> matchAttributes = config.getMatchAttribute();
    if ((matchAttributes == null) || matchAttributes.isEmpty())
    {
      matchAttributes = userEntry.getUserAttributes().keySet();
    }

    for (AttributeType t : matchAttributes)
    {
      List<Attribute> attrList = userEntry.getAttribute(t);
      if ((attrList == null) || attrList.isEmpty())
      {
        continue;
      }

      AttributeValue vf = new AttributeValue(t, password);
      AttributeValue vr = new AttributeValue(t, reversed);

      for (Attribute a : attrList)
      {
        if (a.hasValue(vf) ||
            (config.isTestReversedPassword() && a.hasValue(vr)))
        {

          invalidReason.append(ERR_ATTRVALUE_VALIDATOR_PASSWORD_IN_ENTRY.get());
          return false;
        }
      }
    }


    // If we've gotten here, then the password is acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PasswordValidatorCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    AttributeValuePasswordValidatorCfg config =
         (AttributeValuePasswordValidatorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      AttributeValuePasswordValidatorCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // If we've gotten this far, then we'll accept the change.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                      AttributeValuePasswordValidatorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    currentConfig = configuration;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

