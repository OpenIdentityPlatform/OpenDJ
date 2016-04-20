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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;

import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.*;
import org.opends.server.util.LevenshteinDistance;
import org.forgerock.opendj.server.config.server.SimilarityBasedPasswordValidatorCfg;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;

/**
 * This class provides a password validator that can ensure that the provided
 * password meets minimum similarity requirements.
 */
public class SimilarityBasedPasswordValidator extends
    PasswordValidator<SimilarityBasedPasswordValidatorCfg> implements
    ConfigurationChangeListener<SimilarityBasedPasswordValidatorCfg>
{
  /** The current configuration for this password validator. */
  private SimilarityBasedPasswordValidatorCfg currentConfig;

  /** Creates a new instance of this password validator. */
  public SimilarityBasedPasswordValidator()
  {
    super();

    // All initialization must be done in the initializePasswordValidator
    // method.
  }

  @Override
  public void initializePasswordValidator(
                   SimilarityBasedPasswordValidatorCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addSimilarityBasedChangeListener(this);

    currentConfig = configuration;
  }

  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeSimilarityBasedChangeListener(this);
  }

  @Override
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      LocalizableMessageBuilder invalidReason)  {
    int minDifference = currentConfig.getMinPasswordDifference();
    ByteString passwd = newPassword == null
                        ? ByteString.empty()
                        : newPassword;

    if (currentPasswords == null || currentPasswords.isEmpty()) {
      // This validator requires access to at least one current password.
      // If we don't have a current password, then we can't validate it, so
      // we'll have to assume it is OK.  Ideally, the password policy should be
      // configured to always require the current password, but even then the
      // current password probably won't be available during an administrative
      // password reset.
      return true;
    }

    for (ByteString bs : currentPasswords) {
        if (bs == null) {
            continue;
        }
        int ldistance = LevenshteinDistance.calculate(passwd.toString(),
                                                      bs.toString());
        if (ldistance < minDifference) {
          invalidReason.append(ERR_PWDIFFERENCEVALIDATOR_TOO_SMALL.get(
                  minDifference));
          return false;
        }
    }

    return true;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      SimilarityBasedPasswordValidatorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
              SimilarityBasedPasswordValidatorCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
