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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.Operation;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a password validator that can ensure that the provided
 * password meets minimum and/or maximum length requirements.
 */
public class LengthBasedPasswordValidator
       extends PasswordValidator
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.LengthBasedPasswordValidator";



  // The DN of the configuration entry for this password validator.
  private DN configEntryDN;

  // The maximum number of characters allowed for a password.
  private int maxLength;

  // The minimum number of characters allowed for a password.
  private int minLength;



  /**
   * Creates a new instance of this password validator.
   */
  public LengthBasedPasswordValidator()
  {
    super();

    assert debugConstructor(CLASS_NAME);

    // All initialization must be done in the initializePasswordValidator
    // method.
  }



  /**
   * Initializes this password validator based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this password validator.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializePasswordValidator(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializePasswordValidator",
                      String.valueOf(configEntry));


    configEntryDN = configEntry.getDN();


    // Get the configured minimum length.
    minLength = 0;
    int msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MIN_LENGTH;
    IntegerConfigAttribute minLengthStub =
         new IntegerConfigAttribute(ATTR_PASSWORD_MIN_LENGTH, getMessage(msgID),
                                    false, false, false, true, 0, false, 0);
    try
    {
      IntegerConfigAttribute minLengthAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(minLengthStub);
      if (minLengthAttr != null)
      {
        minLength = minLengthAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordValidator", e);

      msgID = MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MIN_LENGTH;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the configured maximum length.
    maxLength = 0;
    msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MAX_LENGTH;
    IntegerConfigAttribute maxLengthStub =
         new IntegerConfigAttribute(ATTR_PASSWORD_MAX_LENGTH, getMessage(msgID),
                                    false, false, false, true, 0, false, 0);
    try
    {
      IntegerConfigAttribute maxLengthAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxLengthStub);
      if (maxLengthAttr != null)
      {
        maxLength = maxLengthAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordValidator", e);

      msgID = MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MAX_LENGTH;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // If both a minimum and a maximum were provided, then make sure the
    // minimum is less than or equal to the maximum.
    if ((minLength > 0) && (maxLength > 0) && (minLength > maxLength))
    {
      msgID = MSGID_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX;
      String message = getMessage(msgID, minLength, maxLength);
      throw new ConfigException(msgID, message);
    }


    // Register with the Directory Server as a configurable component.
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Performs any finalization that might be required when this password
   * validator is unloaded.  No action is taken in the default implementation.
   */
  public void finalizePasswordValidator()
  {
    assert debugEnter(CLASS_NAME, "finalizePasswordValidator");

    DirectoryServer.deregisterConfigurableComponent(this);
  }



  /**
   * Indicates whether the provided password is acceptable for use by the
   * specified user.  If the password is determined to be unacceptable, then a
   * human-readable explanation should be appended to the provided buffer.
   *
   * @param  password       The proposed clear-text password that should be
   *                        validated.
   * @param  operation      The operation that is being used to set the
   *                        password.
   * @param  userEntry      The entry for the user whose password is being
   *                        changed.
   * @param  invalidReason  The buffer to which the human-readable explanation
   *                        should be appended if it is determined that the
   *                        password is not acceptable.
   *
   * @return  <CODE>true</CODE> if the password is acceptable, or
   *          <CODE>false</CODE> if not.
   */
  public boolean passwordIsValid(ByteString password, Operation operation,
                                 Entry userEntry, StringBuilder invalidReason)
  {
    assert debugEnter(CLASS_NAME, "passwordIsValid",
                      "org.opends.server.protocols.asn1.ASN1OctetString",
                      String.valueOf(operation), String.valueOf(userEntry),
                      "java.lang.StringBuilder");


    int numChars = password.stringValue().length();

    if ((minLength > 0) && (numChars < minLength))
    {
      invalidReason.append(getMessage(MSGID_PWLENGTHVALIDATOR_TOO_SHORT,
                                      minLength));
      return false;
    }

    if ((maxLength > 0) && (numChars > maxLength))
    {
      invalidReason.append(getMessage(MSGID_PWLENGTHVALIDATOR_TOO_LONG,
                                      minLength));
      return false;
    }

    return true;
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");


    LinkedList<ConfigAttribute> attrs = new LinkedList<ConfigAttribute>();

    int msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MIN_LENGTH;
    attrs.add(new IntegerConfigAttribute(ATTR_PASSWORD_MIN_LENGTH,
                                         getMessage(msgID), false, false, false,
                                         true, 0, false, 0, minLength));

    msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MAX_LENGTH;
    attrs.add(new IntegerConfigAttribute(ATTR_PASSWORD_MAX_LENGTH,
                                         getMessage(msgID), false, false, false,
                                         true, 0, false, 0, maxLength));

    return attrs;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    // Get the configured minimum length.
    int newMinLength = 0;
    int msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MIN_LENGTH;
    IntegerConfigAttribute minLengthStub =
         new IntegerConfigAttribute(ATTR_PASSWORD_MIN_LENGTH, getMessage(msgID),
                                    false, false, false, true, 0, false, 0);
    try
    {
      IntegerConfigAttribute minLengthAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(minLengthStub);
      if (minLengthAttr != null)
      {
        newMinLength = minLengthAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MIN_LENGTH;
      unacceptableReasons.add(getMessage(msgID,
                                         stackTraceToSingleLineString(e)));
    }


    // Get the configured maximum length.
    int newMaxLength = 0;
    msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MAX_LENGTH;
    IntegerConfigAttribute maxLengthStub =
         new IntegerConfigAttribute(ATTR_PASSWORD_MAX_LENGTH, getMessage(msgID),
                                    false, false, false, true, 0, false, 0);
    try
    {
      IntegerConfigAttribute maxLengthAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxLengthStub);
      if (maxLengthAttr != null)
      {
        newMaxLength = maxLengthAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MAX_LENGTH;
      unacceptableReasons.add(getMessage(msgID,
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // If both a minimum and a maximum were provided, then make sure the
    // minimum is less than or equal to the maximum.
    if ((newMinLength > 0) && (newMaxLength > 0) &&
        (newMinLength > newMaxLength))
    {
      msgID = MSGID_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX;
      unacceptableReasons.add(getMessage(msgID, newMinLength, newMaxLength));
      return false;
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the configured minimum length.
    int newMinLength = 0;
    int msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MIN_LENGTH;
    IntegerConfigAttribute minLengthStub =
         new IntegerConfigAttribute(ATTR_PASSWORD_MIN_LENGTH, getMessage(msgID),
                                    false, false, false, true, 0, false, 0);
    try
    {
      IntegerConfigAttribute minLengthAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(minLengthStub);
      if (minLengthAttr != null)
      {
        newMinLength = minLengthAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MIN_LENGTH;
      messages.add(getMessage(msgID, stackTraceToSingleLineString(e)));
    }


    // Get the configured maximum length.
    int newMaxLength = 0;
    msgID = MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MAX_LENGTH;
    IntegerConfigAttribute maxLengthStub =
         new IntegerConfigAttribute(ATTR_PASSWORD_MAX_LENGTH, getMessage(msgID),
                                    false, false, false, true, 0, false, 0);
    try
    {
      IntegerConfigAttribute maxLengthAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxLengthStub);
      if (maxLengthAttr != null)
      {
        newMaxLength = maxLengthAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MAX_LENGTH;
      messages.add(getMessage(msgID, stackTraceToSingleLineString(e)));
    }


    // If both a minimum and a maximum were provided, then make sure the
    // minimum is less than or equal to the maximum.
    if ((newMinLength > 0) && (newMaxLength > 0) &&
        (newMinLength > newMaxLength))
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      msgID = MSGID_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX;
      messages.add(getMessage(msgID, newMinLength, newMaxLength));
    }


    // If everything looks good, then apply the changes.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (newMinLength != minLength)
      {
        minLength = newMinLength;

        if (detailedResults)
        {
          msgID = MSGID_PWLENGTHVALIDATOR_UPDATED_MIN_LENGTH;
          messages.add(getMessage(msgID, minLength));
        }
      }


      if (newMaxLength != maxLength)
      {
        maxLength = newMaxLength;

        if (detailedResults)
        {
          msgID = MSGID_PWLENGTHVALIDATOR_UPDATED_MAX_LENGTH;
          messages.add(getMessage(msgID, maxLength));
        }
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

