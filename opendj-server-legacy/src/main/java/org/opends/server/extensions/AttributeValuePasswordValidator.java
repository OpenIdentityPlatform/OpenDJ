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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;

import java.util.List;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.AttributeValuePasswordValidatorCfg;
import org.forgerock.opendj.server.config.server.PasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.*;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ExtensionMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;

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
  /** The current configuration for this password validator. */
  private AttributeValuePasswordValidatorCfg currentConfig;

  /** Creates a new instance of this attribute value password validator. */
  public AttributeValuePasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }

  @Override
  public void initializePasswordValidator(
                   AttributeValuePasswordValidatorCfg configuration)
  {
    configuration.addAttributeValueChangeListener(this);
    currentConfig = configuration;
  }

  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeAttributeValueChangeListener(this);
  }

  /**
   * Search for substrings of the password in an Attribute. The search is
   * case-insensitive.
   *
   * @param password the password
   * @param minSubstringLength the minimum substring length to check
   * @param a the attribute to search
   * @return true if an attribute value matches a substring of the password,
   * false otherwise.
   */
  private boolean containsSubstring(String password, int minSubstringLength,
      Attribute a)
  {
    final int passwordLength = password.length();

    for (int i = 0; i < passwordLength; i++)
    {
      for (int j = i + minSubstringLength; j <= passwordLength; j++)
      {
        Attribute substring = Attributes.create(a.getAttributeDescription().getAttributeType(),
            password.substring(i, j));
        for (ByteString val : a)
        {
          if (substring.contains(val))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      LocalizableMessageBuilder invalidReason)
  {
    // Get a handle to the current configuration.
    AttributeValuePasswordValidatorCfg config = currentConfig;

    // Get the string representation (both forward and reversed) for the password.
    final String password = newPassword.toString();
    final String reversed = new StringBuilder(password).reverse().toString();

    // Check to see if we should verify the whole password or the substrings.
    int minSubstringLength = password.length();
    if (config.isCheckSubstrings()
        // We apply the minimal substring length only if the provided value
        // is smaller then the actual password length
        && config.getMinSubstringLength() < password.length())
    {
      minSubstringLength = config.getMinSubstringLength();
    }

    // If we should check a specific set of attributes, then do that now.
    // Otherwise, check all user attributes.
    Set<AttributeType> matchAttributes = config.getMatchAttribute();
    if (matchAttributes == null || matchAttributes.isEmpty())
    {
      matchAttributes = userEntry.getUserAttributes().keySet();
    }

    final ByteString vf = ByteString.valueOfUtf8(password);
    final ByteString vr = ByteString.valueOfUtf8(reversed);
    for (AttributeType t : matchAttributes)
    {
      for (Attribute a : userEntry.getAttribute(t))
      {
        if (a.contains(vf) ||
            (config.isTestReversedPassword() && a.contains(vr)) ||
            (config.isCheckSubstrings() &&
                containsSubstring(password, minSubstringLength, a)))
        {
          invalidReason.append(ERR_ATTRVALUE_VALIDATOR_PASSWORD_IN_ENTRY.get());
          return false;
        }
      }
    }

    // If we've gotten here, then the password is acceptable.
    return true;
  }

  @Override
  public boolean isConfigurationAcceptable(PasswordValidatorCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    AttributeValuePasswordValidatorCfg config =
         (AttributeValuePasswordValidatorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      AttributeValuePasswordValidatorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // If we've gotten this far, then we'll accept the change.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                      AttributeValuePasswordValidatorCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
