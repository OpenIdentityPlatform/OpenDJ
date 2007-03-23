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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.LengthBasedPasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigException;
import org.opends.server.core.Operation;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class provides a password validator that can ensure that the provided
 * password meets minimum and/or maximum length requirements.
 */
public class LengthBasedPasswordValidator extends
    PasswordValidator<LengthBasedPasswordValidatorCfg> implements
    ConfigurationChangeListener<LengthBasedPasswordValidatorCfg>
{
  // The current configuration for this password validator.
  private LengthBasedPasswordValidatorCfg currentConfig;



  /**
   * Creates a new instance of this password validator.
   */
  public LengthBasedPasswordValidator()
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
                   LengthBasedPasswordValidatorCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addLengthBasedChangeListener(this);

    currentConfig = configuration;

    // Make sure that if both the maximum and minimum lengths are set, the
    // maximum length is greater than or equal to the minimum length.
    int maxLength = configuration.getMaximumPasswordLength();
    int minLength = configuration.getMinimumPasswordLength();
    if ((maxLength > 0) && (minLength > 0) && (minLength > maxLength))
    {
      int    msgID   = MSGID_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX;
      String message = getMessage(msgID, minLength, maxLength);
      throw new ConfigException(msgID, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizePasswordValidator()
  {
    currentConfig.removeLengthBasedChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      StringBuilder invalidReason)
  {
    LengthBasedPasswordValidatorCfg config = currentConfig;

    int numChars = newPassword.stringValue().length();

    int minLength = config.getMinimumPasswordLength();
    if ((minLength > 0) && (numChars < minLength))
    {
      invalidReason.append(getMessage(MSGID_PWLENGTHVALIDATOR_TOO_SHORT,
                                      minLength));
      return false;
    }

    int maxLength = config.getMaximumPasswordLength();
    if ((maxLength > 0) && (numChars > maxLength))
    {
      invalidReason.append(getMessage(MSGID_PWLENGTHVALIDATOR_TOO_LONG,
                                      minLength));
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      LengthBasedPasswordValidatorCfg configuration,
                      List<String> unacceptableReasons)
  {
    // Make sure that if both the maximum and minimum lengths are set, the
    // maximum length is greater than or equal to the minimum length.
    int maxLength = configuration.getMaximumPasswordLength();
    int minLength = configuration.getMinimumPasswordLength();
    if ((maxLength > 0) && (minLength > 0) && (minLength > maxLength))
    {
      int    msgID   = MSGID_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX;
      String message = getMessage(msgID, minLength, maxLength);
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              LengthBasedPasswordValidatorCfg configuration)
  {
    // We will always accept the proposed configuration if it's gotten to this
    // point.
    currentConfig = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

