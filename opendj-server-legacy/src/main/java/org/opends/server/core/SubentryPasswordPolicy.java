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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.meta.PasswordPolicyCfgDefn.StateUpdateFailurePolicy;
import org.forgerock.opendj.server.config.server.PasswordValidatorCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.SubEntry;
import org.opends.server.util.SchemaUtils;
import org.opends.server.util.SchemaUtils.PasswordType;

/**
 * This class represents subentry password policy based on Password Policy for
 * LDAP Directories Internet-Draft. In order to represent subentry password
 * policies as OpenDJ password policies it performs a mapping of Draft defined
 * attributes to OpenDJ implementation specific attributes. Any missing
 * attributes are inherited from server default password policy. This class is
 * also responsible for any Draft attributes validation ie making sure that
 * provided values are acceptable and within the predefined range.
 */
public final class SubentryPasswordPolicy extends PasswordPolicy
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Password Policy Subentry draft attributes. */
  private static final String PWD_OC_POLICY = "pwdpolicy";
  private static final String PWD_ATTR_ATTRIBUTE = "pwdattribute";
  private static final String PWD_ATTR_MINAGE = "pwdminage";
  private static final String PWD_ATTR_MAXAGE = "pwdmaxage";
  private static final String PWD_ATTR_INHISTORY = "pwdinhistory";
  private static final String PWD_ATTR_CHECKQUALITY = "pwdcheckquality";
  private static final String PWD_ATTR_MINLENGTH = "pwdminlength";
  private static final String PWD_ATTR_EXPIREWARNING = "pwdexpirewarning";
  private static final String PWD_ATTR_GRACEAUTHNLIMIT = "pwdgraceauthnlimit";
  private static final String PWD_ATTR_LOCKOUT = "pwdlockout";
  private static final String PWD_ATTR_LOCKOUTDURATION = "pwdlockoutduration";
  private static final String PWD_ATTR_MAXFAILURE = "pwdmaxfailure";
  private static final String PWD_ATTR_MUSTCHANGE = "pwdmustchange";
  private static final String PWD_ATTR_ALLOWUSERCHANGE = "pwdallowuserchange";
  private static final String PWD_ATTR_SAFEMODIFY = "pwdsafemodify";
  private static final String PWD_ATTR_FAILURECOUNTINTERVAL =
      "pwdfailurecountinterval";
  private static final String PWD_ATTR_VALIDATOR = "ds-cfg-password-validator";
  private static final String PWD_OC_VALIDATORPOLICY = "pwdvalidatorpolicy";

  /** Password Policy Subentry DN. */
  private final DN passwordPolicySubentryDN;
  /** The value of the "allow-user-password-changes" property. */
  private final Boolean pAllowUserPasswordChanges;
  /** The value of the "force-change-on-reset" property. */
  private final Boolean pForceChangeOnReset;
  /** The value of the "grace-login-count" property. */
  private final Integer pGraceLoginCount;
  /** The value of the "lockout-duration" property. */
  private final Long pLockoutDuration;
  /** The value of the "lockout-failure-count" property. */
  private final Integer pLockoutFailureCount;
  /** The value of the "lockout-failure-expiration-interval" property. */
  private final Long pLockoutFailureExpirationInterval;
  /** The value of the "max-password-age" property. */
  private final Long pMaxPasswordAge;
  /** The value of the "min-password-age" property. */
  private final Long pMinPasswordAge;
  /** The value of the "password-attribute" property. */
  private final AttributeType pPasswordAttribute;
  /** The value of the "password-change-requires-current-password" property. */
  private final Boolean pPasswordChangeRequiresCurrentPassword;
  /** The value of the "password-expiration-warning-interval" property. */
  private final Long pPasswordExpirationWarningInterval;
  /** The value of the "password-history-count" property. */
  private final Integer pPasswordHistoryCount;
  /** Indicates if the password attribute uses auth password syntax. */
  private final Boolean pAuthPasswordSyntax;
  /** The set of password validators if any. */
  private final Set<DN> pValidatorNames = new HashSet<>();
  /** Used when logging errors due to invalid validator reference. */
  private AtomicBoolean isAlreadyLogged = new AtomicBoolean();

  /**
   * Returns the global default password policy which will be used for deriving
   * the default properties of sub-entries.
   */
  private PasswordPolicy getDefaultPasswordPolicy()
  {
    return DirectoryServer.getDefaultPasswordPolicy();
  }

  /**
   * Creates subentry password policy object from the subentry, parsing and
   * evaluating subentry password policy attributes.
   *
   * @param subentry
   *          password policy subentry.
   * @throws DirectoryException
   *           If a problem occurs while creating subentry password policy
   *           instance from given subentry.
   */
  public SubentryPasswordPolicy(SubEntry subentry) throws DirectoryException
  {
    // Determine if this is a password policy subentry.
    ObjectClass pwdPolicyOC = DirectoryServer.getSchema().getObjectClass(PWD_OC_POLICY);
    Entry entry = subentry.getEntry();
    Map<ObjectClass, String> objectClasses = entry.getObjectClasses();
    if (pwdPolicyOC.isPlaceHolder())
    {
      // This should not happen -- The server doesn't
      // have a pwdPolicy objectclass defined.
      if (logger.isTraceEnabled())
      {
        logger.trace("No %s objectclass is defined in the server schema.",
                PWD_OC_POLICY);
      }
      for (String ocName : objectClasses.values())
      {
        if (PWD_OC_POLICY.equalsIgnoreCase(ocName))
        {
          break;
        }
      }
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_PWPOLICY_NO_PWDPOLICY_OC.get(subentry.getDN()));
    }
    else if (!objectClasses.containsKey(pwdPolicyOC))
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_PWPOLICY_NO_PWDPOLICY_OC.get(subentry.getDN()));
    }

    // Subentry DN for this password policy.
    this.passwordPolicySubentryDN = subentry.getDN();

    // Get known Password Policy draft attributes from the entry.
    // If any given attribute is missing or empty set its value
    // from default Password Policy configuration.
    String value = getAttrValue(entry, PWD_ATTR_ATTRIBUTE);
    if (value != null && value.length() > 0)
    {
      this.pPasswordAttribute = DirectoryServer.getSchema().getAttributeType(value);
      if (this.pPasswordAttribute.isPlaceHolder())
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_PWPOLICY_UNDEFINED_PASSWORD_ATTRIBUTE.get(this.passwordPolicySubentryDN, value));
      }

      final PasswordType passwordType = SchemaUtils.checkPasswordType(pPasswordAttribute);
      if (passwordType.equals(PasswordType.AUTH_PASSWORD))
      {
        pAuthPasswordSyntax = true;
      }
      else if (passwordType.equals(PasswordType.USER_PASSWORD))
      {
        pAuthPasswordSyntax = false;
      }
      else
      {
        String syntax = pPasswordAttribute.getSyntax().getName();
        if (syntax == null || syntax.length() == 0)
        {
          syntax = pPasswordAttribute.getSyntax().getOID();
        }

        LocalizableMessage message = ERR_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX.get(
            passwordPolicySubentryDN, pPasswordAttribute.getNameOrOID(), syntax);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    else
    {
      this.pPasswordAttribute = null;
      this.pAuthPasswordSyntax = null;
    }

    this.pMinPasswordAge = asLong(entry, PWD_ATTR_MINAGE);
    this.pMaxPasswordAge = asLong(entry, PWD_ATTR_MAXAGE);
    this.pPasswordHistoryCount =
        asInteger(entry, PWD_ATTR_INHISTORY, Integer.MAX_VALUE);

    // This one is managed via the password validator
    // so only check if its value is acceptable.
    asInteger(entry, PWD_ATTR_CHECKQUALITY, 2);

    // This one is managed via the password validator
    // so only check if its value is acceptable.
    asInteger(entry, PWD_ATTR_MINLENGTH, Integer.MAX_VALUE);

    // This one depends on lockout failure count value
    // so only check if its value is acceptable.
    asBoolean(entry, PWD_ATTR_LOCKOUT);

    this.pPasswordExpirationWarningInterval =
        asLong(entry, PWD_ATTR_EXPIREWARNING);
    this.pGraceLoginCount =
        asInteger(entry, PWD_ATTR_GRACEAUTHNLIMIT, Integer.MAX_VALUE);
    this.pLockoutDuration = asLong(entry, PWD_ATTR_LOCKOUTDURATION);
    this.pLockoutFailureCount =
        asInteger(entry, PWD_ATTR_MAXFAILURE, Integer.MAX_VALUE);
    this.pForceChangeOnReset = asBoolean(entry, PWD_ATTR_MUSTCHANGE);
    this.pAllowUserPasswordChanges = asBoolean(entry, PWD_ATTR_ALLOWUSERCHANGE);
    this.pPasswordChangeRequiresCurrentPassword =
        asBoolean(entry, PWD_ATTR_SAFEMODIFY);
    this.pLockoutFailureExpirationInterval =
        asLong(entry, PWD_ATTR_FAILURECOUNTINTERVAL);

    // Now check for the pwdValidatorPolicy OC and its attribute.
    // Determine if this is a password validator policy object class.
    ObjectClass pwdValidatorPolicyOC = DirectoryServer.getSchema().getObjectClass(PWD_OC_VALIDATORPOLICY);
    if (!pwdValidatorPolicyOC.isPlaceHolder() &&
        objectClasses.containsKey(pwdValidatorPolicyOC))
    {
      AttributeType pwdAttrType =
          DirectoryServer.getSchema().getAttributeType(PWD_ATTR_VALIDATOR);
      for (Attribute attr : entry.getAttribute(pwdAttrType))
      {
        for (ByteString val : attr)
        {
          DN validatorDN = DN.valueOf(val);
          if (DirectoryServer.getPasswordValidator(validatorDN) == null)
          {
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_PWPOLICY_UNKNOWN_VALIDATOR.get(this.passwordPolicySubentryDN, validatorDN, PWD_ATTR_VALIDATOR));
          }
          pValidatorNames.add(validatorDN);
        }
      }
    }
  }

  private Boolean asBoolean(Entry entry, String attrName)
      throws DirectoryException
  {
    final String value = getAttrValue(entry, attrName);
    if (value != null && value.length() > 0)
    {
      if (value.equalsIgnoreCase(Boolean.TRUE.toString())
          || value.equalsIgnoreCase(Boolean.FALSE.toString()))
      {
        return Boolean.valueOf(value);
      }
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(attrName, value));
    }
    return null;
  }

  private Integer asInteger(Entry entry, String attrName, int upperBound)
      throws DirectoryException
  {
    final String value = getAttrValue(entry, attrName);
    if (value != null && value.length() > 0)
    {
      try
      {
        final Integer result = Integer.valueOf(value);
        checkIntegerAttr(attrName, result, 0, upperBound);
        return result;
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(attrName, value,
                ne.getLocalizedMessage()));
      }
    }
    return null;
  }

  private Long asLong(Entry entry, String attrName) throws DirectoryException
  {
    final String value = getAttrValue(entry, attrName);
    if (value != null && value.length() > 0)
    {
      try
      {
        final Long result = Long.valueOf(value);
        checkIntegerAttr(attrName, result, 0, Integer.MAX_VALUE);
        return result;
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(attrName, value,
                ne.getLocalizedMessage()));
      }
    }
    return null;
  }

  /**
   * Helper method to validate integer values.
   *
   * @param attrName
   *          integer attribute name.
   * @param attrValue
   *          integer value to validate.
   * @param lowerBound
   *          lowest acceptable value.
   * @param upperBound
   *          highest acceptable value.
   * @throws DirectoryException
   *           if the value is out of bounds.
   */
  private void checkIntegerAttr(String attrName, long attrValue,
      long lowerBound, long upperBound) throws DirectoryException
  {
    if (attrValue < lowerBound)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(attrName, attrValue,
              lowerBound));
    }
    if (attrValue > upperBound)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(attrName, attrValue,
              upperBound));
    }
  }

  /**
   * Helper method to retrieve an attribute value from given entry.
   *
   * @param entry
   *          the entry to retrieve an attribute value from.
   * @param pwdAttrName
   *          attribute name to retrieve the value for.
   * @return <CODE>String</CODE> or <CODE>null</CODE>.
   */
  private String getAttrValue(Entry entry, String pwdAttrName)
  {
    AttributeType pwdAttrType = DirectoryServer.getSchema().getAttributeType(pwdAttrName);
    for (Attribute attr : entry.getAttribute(pwdAttrType))
    {
      for (ByteString value : attr)
      {
        return value.toString();
      }
    }
    return null;
  }

  @Override
  public boolean isAllowExpiredPasswordChanges()
  {
    return getDefaultPasswordPolicy().isAllowExpiredPasswordChanges();
  }

  @Override
  public boolean isAllowMultiplePasswordValues()
  {
    return getDefaultPasswordPolicy().isAllowMultiplePasswordValues();
  }

  @Override
  public boolean isAllowPreEncodedPasswords()
  {
    return getDefaultPasswordPolicy().isAllowPreEncodedPasswords();
  }

  @Override
  public boolean isAllowUserPasswordChanges()
  {
    return pAllowUserPasswordChanges != null ? pAllowUserPasswordChanges
        : getDefaultPasswordPolicy().isAllowUserPasswordChanges();
  }

  @Override
  public boolean isExpirePasswordsWithoutWarning()
  {
    return getDefaultPasswordPolicy().isExpirePasswordsWithoutWarning();
  }

  @Override
  public boolean isForceChangeOnAdd()
  {
    // Don't use pwdMustChange since the password provided when the entry was
    // added may have been provided by the user. See OPENDJ-341.
    return getDefaultPasswordPolicy().isForceChangeOnAdd();
  }

  @Override
  public boolean isForceChangeOnReset()
  {
    return pForceChangeOnReset != null ? pForceChangeOnReset
        : getDefaultPasswordPolicy().isForceChangeOnReset();
  }

  @Override
  public int getGraceLoginCount()
  {
    return pGraceLoginCount != null ? pGraceLoginCount
        : getDefaultPasswordPolicy().getGraceLoginCount();
  }

  @Override
  public long getIdleLockoutInterval()
  {
    return getDefaultPasswordPolicy().getIdleLockoutInterval();
  }

  @Override
  public AttributeType getLastLoginTimeAttribute()
  {
    return getDefaultPasswordPolicy().getLastLoginTimeAttribute();
  }

  @Override
  public String getLastLoginTimeFormat()
  {
    return getDefaultPasswordPolicy().getLastLoginTimeFormat();
  }

  @Override
  public long getLockoutDuration()
  {
    return pLockoutDuration != null ? pLockoutDuration
        : getDefaultPasswordPolicy().getLockoutDuration();
  }

  @Override
  public int getLockoutFailureCount()
  {
    return pLockoutFailureCount != null ? pLockoutFailureCount
        : getDefaultPasswordPolicy().getLockoutFailureCount();
  }

  @Override
  public long getLockoutFailureExpirationInterval()
  {
    return pLockoutFailureExpirationInterval != null ?
        pLockoutFailureExpirationInterval
        : getDefaultPasswordPolicy().getLockoutFailureExpirationInterval();
  }

  @Override
  public long getMaxPasswordAge()
  {
    return pMaxPasswordAge != null ? pMaxPasswordAge
        : getDefaultPasswordPolicy().getMaxPasswordAge();
  }

  @Override
  public long getMaxPasswordResetAge()
  {
    return getDefaultPasswordPolicy().getMaxPasswordResetAge();
  }

  @Override
  public long getMinPasswordAge()
  {
    return pMinPasswordAge != null ? pMinPasswordAge
        : getDefaultPasswordPolicy().getMinPasswordAge();
  }

  @Override
  public AttributeType getPasswordAttribute()
  {
    return pPasswordAttribute != null ? pPasswordAttribute
        : getDefaultPasswordPolicy().getPasswordAttribute();
  }

  @Override
  public boolean isPasswordChangeRequiresCurrentPassword()
  {
    return pPasswordChangeRequiresCurrentPassword != null ?
        pPasswordChangeRequiresCurrentPassword
        : getDefaultPasswordPolicy().isPasswordChangeRequiresCurrentPassword();
  }

  @Override
  public long getPasswordExpirationWarningInterval()
  {
    return pPasswordExpirationWarningInterval != null ?
        pPasswordExpirationWarningInterval
        : getDefaultPasswordPolicy().getPasswordExpirationWarningInterval();
  }

  @Override
  public int getPasswordHistoryCount()
  {
    return pPasswordHistoryCount != null ? pPasswordHistoryCount
        : getDefaultPasswordPolicy().getPasswordHistoryCount();
  }

  @Override
  public long getPasswordHistoryDuration()
  {
    return getDefaultPasswordPolicy().getPasswordHistoryDuration();
  }

  @Override
  public SortedSet<String> getPreviousLastLoginTimeFormats()
  {
    return getDefaultPasswordPolicy().getPreviousLastLoginTimeFormats();
  }

  @Override
  public long getRequireChangeByTime()
  {
    return getDefaultPasswordPolicy().getRequireChangeByTime();
  }

  @Override
  public boolean isRequireSecureAuthentication()
  {
    return getDefaultPasswordPolicy().isRequireSecureAuthentication();
  }

  @Override
  public boolean isRequireSecurePasswordChanges()
  {
    return getDefaultPasswordPolicy().isRequireSecurePasswordChanges();
  }

  @Override
  public boolean isSkipValidationForAdministrators()
  {
    return getDefaultPasswordPolicy().isSkipValidationForAdministrators();
  }

  @Override
  public StateUpdateFailurePolicy getStateUpdateFailurePolicy()
  {
    return getDefaultPasswordPolicy().getStateUpdateFailurePolicy();
  }

  @Override
  public boolean isAuthPasswordSyntax()
  {
    return pAuthPasswordSyntax != null ? pAuthPasswordSyntax
        : getDefaultPasswordPolicy().isAuthPasswordSyntax();
  }

  @Override
  public List<PasswordStorageScheme<?>> getDefaultPasswordStorageSchemes()
  {
    return getDefaultPasswordPolicy().getDefaultPasswordStorageSchemes();
  }

  @Override
  public Set<String> getDeprecatedPasswordStorageSchemes()
  {
    return getDefaultPasswordPolicy().getDeprecatedPasswordStorageSchemes();
  }

  @Override
  public DN getDN()
  {
    return passwordPolicySubentryDN;
  }

  @Override
  public boolean isDefaultPasswordStorageScheme(String name)
  {
    return getDefaultPasswordPolicy().isDefaultPasswordStorageScheme(name);
  }

  @Override
  public boolean isDeprecatedPasswordStorageScheme(String name)
  {
    return getDefaultPasswordPolicy().isDeprecatedPasswordStorageScheme(name);
  }

  @Override
  public Collection<PasswordValidator<?>> getPasswordValidators()
  {
    if (!pValidatorNames.isEmpty())
    {
      Collection<PasswordValidator<?>> values = new HashSet<>();
      for (DN validatorDN : pValidatorNames){
        PasswordValidator<?> validator = DirectoryServer.getPasswordValidator(validatorDN);
        if (validator == null) {
          PasswordValidator<?> errorValidator = new RejectPasswordValidator(
              validatorDN.toString(), passwordPolicySubentryDN.toString());
          values.clear();
          values.add(errorValidator);
          return values;
        }
        values.add(validator);
      }
      isAlreadyLogged.set(false);
      return values;
    }
    return getDefaultPasswordPolicy().getPasswordValidators();
  }

  /**
   * Implementation of a specific Password Validator that reject all
   * password due to mis-configured password policy subentry.
   * This is only used when a subentry is referencing a password
   * validator that is no longer configured.
   */
  private final class RejectPasswordValidator extends
      PasswordValidator<PasswordValidatorCfg>
  {
    private final String validatorName;
    private final String pwPolicyName;
    public RejectPasswordValidator(String name, String policyName)
    {
      super();
      validatorName = name;
      pwPolicyName = policyName;
    }

    @Override
    public void initializePasswordValidator(PasswordValidatorCfg configuration)
        throws ConfigException, InitializationException
    {
      // do nothing
    }

    @Override
    public boolean passwordIsAcceptable(ByteString newPassword,
                                        Set<ByteString> currentPasswords,
                                        Operation operation, Entry userEntry,
                                        LocalizableMessageBuilder invalidReason)
    {
      invalidReason.append(ERR_PWPOLICY_REJECT_DUE_TO_UNKNOWN_VALIDATOR_REASON
          .get());

      // Only log an error once, on first error
      if (isAlreadyLogged.compareAndSet(false, true)) {
        logger.error(ERR_PWPOLICY_REJECT_DUE_TO_UNKNOWN_VALIDATOR_LOG,
            userEntry.getName(), pwPolicyName, validatorName);
      }
      return false;
    }
  }

  @Override
  public Collection<AccountStatusNotificationHandler<?>>
    getAccountStatusNotificationHandlers()
  {
    return getDefaultPasswordPolicy().getAccountStatusNotificationHandlers();
  }

  @Override
  public PasswordGenerator<?> getPasswordGenerator()
  {
    return getDefaultPasswordPolicy().getPasswordGenerator();
  }
}
