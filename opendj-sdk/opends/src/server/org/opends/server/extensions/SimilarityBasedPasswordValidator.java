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

import java.util.List;
import java.util.Set;

import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.util.LevenshteinDistance;
import org.opends.server.admin.std.server.SimilarityBasedPasswordValidatorCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;

import static org.opends.messages.ExtensionMessages.*;
import org.opends.messages.MessageBuilder;


/**
 * This class provides a password validator that can ensure that the provided
 * password meets minimum similarity requirements.
 */
public class SimilarityBasedPasswordValidator extends
    PasswordValidator<SimilarityBasedPasswordValidatorCfg> implements
    ConfigurationChangeListener<SimilarityBasedPasswordValidatorCfg>
{

  // The current configuration for this password validator.
  private SimilarityBasedPasswordValidatorCfg currentConfig;


  /**
   * Creates a new instance of this password validator.
   */
  public SimilarityBasedPasswordValidator()
  {
    super();


    // All initialization must be done in the initializePasswordValidator
    // method.
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordValidator(
                   SimilarityBasedPasswordValidatorCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addSimilarityBasedChangeListener(this);

    currentConfig = configuration;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizePasswordValidator()
  {
    currentConfig.removeSimilarityBasedChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      MessageBuilder invalidReason)  {

    int minDifference = currentConfig.getMinPasswordDifference();
    ByteString passwd = newPassword == null
                        ? ByteStringFactory.create("")
                        : newPassword;

    if (currentPasswords == null || currentPasswords.size() == 0) {
      // This validator requires access to at least one current password.
      // If we don't have a current password, then we can't validate it, so
      // we'll have to assume it is OK.  Ideally, the password policy should be
      // configured to always require the current password, but even then the
      // current password probably won't be availble during an administrative
      // password reset.
      return true;
    }

    for (ByteString bs : currentPasswords) {
        if (bs == null) {
            continue;
        }
        int ldistance = LevenshteinDistance.calculate(passwd.stringValue(),
                                                      bs.stringValue());
        if (ldistance < minDifference) {
          invalidReason.append(ERR_PWDIFFERENCEVALIDATOR_TOO_SMALL.get(
                  minDifference));
          return false;
        }
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      SimilarityBasedPasswordValidatorCfg configuration,
                      List<Message> unacceptableReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              SimilarityBasedPasswordValidatorCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

