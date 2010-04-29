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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn.
       StateUpdateFailurePolicy;
import org.opends.server.admin.std.server.PasswordPolicyCfg;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SubEntry;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class represents subentry password policy based on
 * Password Policy for LDAP Directories Internet-Draft. In
 * order to represent subentry password policies as OpenDS
 * password policies it performs a mapping of Draft defined
 * attributes to OpenDS implementation specific attributes.
 * Any missing attributes are inherited from server default
 * password policy. This class is also reponsible for any
 * Draft attributes validation ie making sure that provided
 * values are acceptable and within the predefined range.
 */
public class SubentryPasswordPolicy implements PasswordPolicyCfg
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Password Policy Subentry draft attributes.
  private final String PWD_OC_POLICY = "pwdpolicy";
  private final String PWD_ATTR_ATTRIBUTE = "pwdattribute";
  private final String PWD_ATTR_MINAGE = "pwdminage";
  private final String PWD_ATTR_MAXAGE = "pwdmaxage";
  private final String PWD_ATTR_INHISTORY = "pwdinhistory";
  private final String PWD_ATTR_CHECKQUALITY = "pwdcheckquality";
  private final String PWD_ATTR_MINLENGTH = "pwdminlength";
  private final String PWD_ATTR_EXPIREWARNING = "pwdexpirewarning";
  private final String PWD_ATTR_GRACEAUTHNLIMIT = "pwdgraceauthnlimit";
  private final String PWD_ATTR_LOCKOUT = "pwdlockout";
  private final String PWD_ATTR_LOCKOUTDURATION = "pwdlockoutduration";
  private final String PWD_ATTR_MAXFAILURE = "pwdmaxfailure";
  private final String PWD_ATTR_MUSTCHANGE = "pwdmustchange";
  private final String PWD_ATTR_ALLOWUSERCHANGE = "pwdallowuserchange";
  private final String PWD_ATTR_SAFEMODIFY = "pwdsafemodify";
  private final String PWD_ATTR_FAILURECOUNTINTERVAL =
          "pwdfailurecountinterval";

  // Password Policy Subentry DN.
  private final DN passwordPolicySubentryDN;
  // The value of the "account-status-notification-handler" property.
  private final SortedSet<String> pAccountStatusNotificationHandler;
  // The value of the "allow-expired-password-changes" property.
  private final boolean pAllowExpiredPasswordChanges;
  // The value of the "allow-multiple-password-values" property.
  private final boolean pAllowMultiplePasswordValues;
  // The value of the "allow-pre-encoded-passwords" property.
  private final boolean pAllowPreEncodedPasswords;
  // The value of the "allow-user-password-changes" property.
  private final boolean pAllowUserPasswordChanges;
  // The value of the "default-password-storage-scheme" property.
  private final SortedSet<String> pDefaultPasswordStorageScheme;
  // The value of the "deprecated-password-storage-scheme" property.
  private final SortedSet<String> pDeprecatedPasswordStorageScheme;
  // The value of the "expire-passwords-without-warning" property.
  private final boolean pExpirePasswordsWithoutWarning;
  // The value of the "force-change-on-add" property.
  private final boolean pForceChangeOnAdd;
  // The value of the "force-change-on-reset" property.
  private final boolean pForceChangeOnReset;
  // The value of the "grace-login-count" property.
  private final int pGraceLoginCount;
  // The value of the "idle-lockout-interval" property.
  private final long pIdleLockoutInterval;
  // The value of the "last-login-time-attribute" property.
  private final AttributeType pLastLoginTimeAttribute;
  // The value of the "last-login-time-format" property.
  private final String pLastLoginTimeFormat;
  // The value of the "lockout-duration" property.
  private final long pLockoutDuration;
  // The value of the "lockout-failure-count" property.
  private final int pLockoutFailureCount;
  // The value of the "lockout-failure-expiration-interval" property.
  private final long pLockoutFailureExpirationInterval;
  // The value of the "max-password-age" property.
  private final long pMaxPasswordAge;
  // The value of the "max-password-reset-age" property.
  private final long pMaxPasswordResetAge;
  // The value of the "min-password-age" property.
  private final long pMinPasswordAge;
  // The value of the "password-attribute" property.
  private final AttributeType pPasswordAttribute;
  // The value of the "password-change-requires-current-password" property.
  private final boolean pPasswordChangeRequiresCurrentPassword;
  // The value of the "password-expiration-warning-interval" property.
  private final long pPasswordExpirationWarningInterval;
  // The value of the "password-generator" property.
  private final String pPasswordGenerator;
  // The value of the "password-history-count" property.
  private final int pPasswordHistoryCount;
  // The value of the "password-history-duration" property.
  private final long pPasswordHistoryDuration;
  // The value of the "password-validator" property.
  private final SortedSet<String> pPasswordValidator;
  // The value of the "previous-last-login-time-format" property.
  private final SortedSet<String> pPreviousLastLoginTimeFormat;
  // The value of the "require-change-by-time" property.
  private final String pRequireChangeByTime;
  // The value of the "require-secure-authentication" property.
  private final boolean pRequireSecureAuthentication;
  // The value of the "require-secure-password-changes" property.
  private final boolean pRequireSecurePasswordChanges;
  // The value of the "skip-validation-for-administrators" property.
  private final boolean pSkipValidationForAdministrators;
  // The value of the "state-update-failure-policy" property.
  private final StateUpdateFailurePolicy pStateUpdateFailurePolicy;

  /**
   * Creates subentry password policy object from the subentry,
   * parsing and evaluating subentry password policy attributes.
   *
   * @param  subentry password policy subentry.
   * @throws DirectoryException If a problem occurs while creating
   *                            subentry password policy instance
   *                            from given subentry.
   */
  public SubentryPasswordPolicy(SubEntry subentry)
          throws DirectoryException
  {
    // Determine if this is a password policy subentry.
    ObjectClass pwdPolicyOC =
         DirectoryServer.getObjectClass(PWD_OC_POLICY);
    Entry entry = subentry.getEntry();
    Map<ObjectClass, String> objectClasses =
            entry.getObjectClasses();
    if (pwdPolicyOC == null) {
      // This should not happen -- The server doesn't
      // have a pwdPolicy objectclass defined.
      if (debugEnabled()) {
        TRACER.debugWarning(
                "No %s objectclass is defined in the server schema.",
                PWD_OC_POLICY);
      }
      for (String ocName : objectClasses.values()) {
        if (ocName.equalsIgnoreCase(PWD_OC_POLICY)) {
          break;
        }
      }
      Message message = ERR_PWPOLICY_NO_PWDPOLICY_OC.get(
              subentry.getDN().toString());
      throw new DirectoryException(
              ResultCode.CONSTRAINT_VIOLATION, message);
    } else if (!objectClasses.containsKey(pwdPolicyOC)) {
      Message message = ERR_PWPOLICY_NO_PWDPOLICY_OC.get(
              subentry.getDN().toString());
      throw new DirectoryException(
              ResultCode.CONSTRAINT_VIOLATION, message);
    }

    // Get default password policy to derive default
    // policy values from.
    PasswordPolicy defaultPasswordPolicy =
            DirectoryServer.getDefaultPasswordPolicy();
    if (defaultPasswordPolicy == null) {
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
              ERR_CONFIG_PWPOLICY_NO_DEFAULT_POLICY.get());
    }

    // Subentry DN for this password policy.
    this.passwordPolicySubentryDN = subentry.getDN();

    // Get known Password Policy draft attributes from the entry.
    // If any given attribute is missing or empty set its value
    // from default Password Policy configuration.
    AttributeValue value = getAttrValue(entry, PWD_ATTR_ATTRIBUTE);
    if ((value != null) && (value.toString().length() > 0)) {
      this.pPasswordAttribute = DirectoryServer.getAttributeType(
              value.toString().toLowerCase(), false);
      if (this.pPasswordAttribute == null) {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                ERR_PWPOLICY_UNDEFINED_PASSWORD_ATTRIBUTE.get(
                this.passwordPolicySubentryDN.toNormalizedString(),
                value.toString()));
      }
    } else {
      // This should not normally happen since pwdAttribute
      // declared as MUST but handle this anyway in case
      // the schema is not enforced for some reason.
      this.pPasswordAttribute =
              defaultPasswordPolicy.getPasswordAttribute();
    }

    value = getAttrValue(entry, PWD_ATTR_MINAGE);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pMinPasswordAge = Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_MINAGE, this.pMinPasswordAge,
                0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pMinPasswordAge =
              defaultPasswordPolicy.getMinimumPasswordAge();
    }

    value = getAttrValue(entry, PWD_ATTR_MAXAGE);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pMaxPasswordAge = Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_MAXAGE, this.pMaxPasswordAge,
                0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pMaxPasswordAge =
              defaultPasswordPolicy.getMaximumPasswordAge();
    }

    value = getAttrValue(entry, PWD_ATTR_INHISTORY);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pPasswordHistoryCount = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_INHISTORY,
                this.pPasswordHistoryCount, 0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pPasswordHistoryCount =
              defaultPasswordPolicy.getPasswordHistoryCount();
    }

    // This one is managed via the password validator
    // so only check if its value is acceptable.
    value = getAttrValue(entry, PWD_ATTR_CHECKQUALITY);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        int pwdCheckQuality = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_CHECKQUALITY, pwdCheckQuality,
                 0, 2);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    }

    // This one is managed via the password validator
    // so only check if its value is acceptable.
    value = getAttrValue(entry, PWD_ATTR_MINLENGTH);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        int pwdMinLength = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_MINLENGTH, pwdMinLength,
                 0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    }

    // This one depends on lockout failure count value
    // so only check if its value is acceptable.
    value = getAttrValue(entry, PWD_ATTR_LOCKOUT);
    if ((value != null) && (value.toString().length() > 0)) {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString()) ||
          value.toString().equalsIgnoreCase(Boolean.FALSE.toString())) {
        Boolean.parseBoolean(value.toString());
      } else {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(
                PWD_ATTR_MUSTCHANGE, value.toString()));
      }
    }

    value = getAttrValue(entry, PWD_ATTR_EXPIREWARNING);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pPasswordExpirationWarningInterval =
              Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_EXPIREWARNING,
                this.pPasswordExpirationWarningInterval,
                 0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pPasswordExpirationWarningInterval =
              defaultPasswordPolicy.getWarningInterval();
    }

    value = getAttrValue(entry, PWD_ATTR_GRACEAUTHNLIMIT);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pGraceLoginCount = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_GRACEAUTHNLIMIT,
                this.pGraceLoginCount, 0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pGraceLoginCount =
              defaultPasswordPolicy.getGraceLoginCount();
    }

    value = getAttrValue(entry, PWD_ATTR_LOCKOUTDURATION);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pLockoutDuration = Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_LOCKOUTDURATION,
                this.pLockoutDuration, 0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pLockoutDuration =
              defaultPasswordPolicy.getLockoutDuration();
    }

    value = getAttrValue(entry, PWD_ATTR_MAXFAILURE);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pLockoutFailureCount = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_MAXFAILURE,
                this.pLockoutFailureCount, 0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_MINAGE, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pLockoutFailureCount =
              defaultPasswordPolicy.getLockoutFailureCount();
    }

    value = getAttrValue(entry, PWD_ATTR_MUSTCHANGE);
    if ((value != null) && (value.toString().length() > 0)) {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString()) ||
          value.toString().equalsIgnoreCase(Boolean.FALSE.toString())) {
        this.pForceChangeOnReset =
                Boolean.parseBoolean(value.toString());
      } else {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(
                PWD_ATTR_MUSTCHANGE, value.toString()));
      }
    } else {
      this.pForceChangeOnReset =
              defaultPasswordPolicy.forceChangeOnReset();
    }

    value = getAttrValue(entry, PWD_ATTR_ALLOWUSERCHANGE);
    if ((value != null) && (value.toString().length() > 0)) {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString()) ||
          value.toString().equalsIgnoreCase(Boolean.FALSE.toString())) {
        this.pAllowUserPasswordChanges =
                Boolean.parseBoolean(value.toString());
      } else {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(
                PWD_ATTR_ALLOWUSERCHANGE, value.toString()));
      }
    } else {
      this.pAllowUserPasswordChanges =
              defaultPasswordPolicy.allowUserPasswordChanges();
    }

    value = getAttrValue(entry, PWD_ATTR_SAFEMODIFY);
    if ((value != null) && (value.toString().length() > 0)) {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString()) ||
          value.toString().equalsIgnoreCase(Boolean.FALSE.toString())) {
        this.pPasswordChangeRequiresCurrentPassword =
                Boolean.parseBoolean(value.toString());
      } else {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(
                PWD_ATTR_SAFEMODIFY, value.toString()));
      }
    } else {
      this.pPasswordChangeRequiresCurrentPassword =
              defaultPasswordPolicy.requireCurrentPassword();
    }

    value = getAttrValue(entry, PWD_ATTR_FAILURECOUNTINTERVAL);
    if ((value != null) && (value.toString().length() > 0)) {
      try {
        this.pLockoutFailureExpirationInterval =
                Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_FAILURECOUNTINTERVAL,
                this.pLockoutFailureExpirationInterval,
                 0, Integer.MAX_VALUE);
      } catch (NumberFormatException ne) {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_FAILURECOUNTINTERVAL, value.toString(),
                ne.getLocalizedMessage()));
      }
    } else {
      this.pLockoutFailureExpirationInterval =
              defaultPasswordPolicy.getLockoutFailureExpirationInterval();
    }

    // Get the rest Password Policy attributes from default configuration.
    SortedSet<String> accountStatusNotificationHandlerSet =
            new TreeSet<String>();
    Set<DN> accountStatusNotificationHandlerDNSet =
            defaultPasswordPolicy.getAccountStatusNotificationHandlers(
            ).keySet();
    for (DN dn : accountStatusNotificationHandlerDNSet) {
      accountStatusNotificationHandlerSet.add(dn.toNormalizedString());
    }
    this.pAccountStatusNotificationHandler =
            accountStatusNotificationHandlerSet;
    this.pAllowExpiredPasswordChanges =
            defaultPasswordPolicy.allowExpiredPasswordChanges();
    this.pAllowMultiplePasswordValues =
            defaultPasswordPolicy.allowMultiplePasswordValues();
    this.pAllowPreEncodedPasswords =
            defaultPasswordPolicy.allowPreEncodedPasswords();
    SortedSet<String> passwordStorageSchemeSet =
            new TreeSet<String>();
    for (DN dn : defaultPasswordPolicy.getDefaultStorageSchemeDNs()) {
      passwordStorageSchemeSet.add(dn.toNormalizedString());
    }
    this.pDefaultPasswordStorageScheme =
            passwordStorageSchemeSet;
    SortedSet<String> deprecatedPasswordStorageSchemeSet =
            new TreeSet<String>();
    for (DN dn : defaultPasswordPolicy.getDeprecatedStorageSchemeDNs()) {
      deprecatedPasswordStorageSchemeSet.add(dn.toNormalizedString());
    }
    this.pDeprecatedPasswordStorageScheme =
            deprecatedPasswordStorageSchemeSet;
    this.pExpirePasswordsWithoutWarning =
            defaultPasswordPolicy.expirePasswordsWithoutWarning();
    this.pForceChangeOnAdd =
            defaultPasswordPolicy.forceChangeOnAdd();
    this.pIdleLockoutInterval =
            defaultPasswordPolicy.getIdleLockoutInterval();
    this.pLastLoginTimeAttribute =
            defaultPasswordPolicy.getLastLoginTimeAttribute();
    this.pLastLoginTimeFormat =
            defaultPasswordPolicy.getLastLoginTimeFormat();
    this.pMaxPasswordResetAge =
            defaultPasswordPolicy.getMaximumPasswordResetAge();
    this.pPasswordGenerator =
            defaultPasswordPolicy.getPasswordGeneratorDN(
            ).toNormalizedString();
    this.pPasswordHistoryDuration =
            defaultPasswordPolicy.getPasswordHistoryDuration();
    SortedSet<String> passwordValidatorSet =
            new TreeSet<String>();
    Set<DN> passwordValidatorDNSet =
            defaultPasswordPolicy.getPasswordValidators(
            ).keySet();
    for (DN dn : passwordValidatorDNSet) {
      passwordValidatorSet.add(dn.toNormalizedString());
    }
    this.pPasswordValidator =
            passwordValidatorSet;
    this.pPreviousLastLoginTimeFormat = new TreeSet<String>(
            defaultPasswordPolicy.getPreviousLastLoginTimeFormats());

    long requireChangeByTime =
            defaultPasswordPolicy.getRequireChangeByTime();
    if (requireChangeByTime > 0) {
      this.pRequireChangeByTime = Long.toString(requireChangeByTime);
    } else {
      this.pRequireChangeByTime = null;
    }

    this.pRequireSecureAuthentication =
            defaultPasswordPolicy.requireSecureAuthentication();
    this.pRequireSecurePasswordChanges =
            defaultPasswordPolicy.requireSecurePasswordChanges();
    this.pSkipValidationForAdministrators =
            defaultPasswordPolicy.skipValidationForAdministrators();
    this.pStateUpdateFailurePolicy =
            defaultPasswordPolicy.getStateUpdateFailurePolicy();
  }

  /**
   * Helper method to validate integer values.
   * @param attrName integer attribute name.
   * @param attrValue integer value to validate.
   * @param lowerBound lowest acceptable value.
   * @param upperBound highest acceptable value.
   * @throws DirectoryException if the value is out of bounds.
   */
  private void checkIntegerAttr(String attrName, long attrValue,
          long lowerBound, long upperBound) throws DirectoryException
  {
    if (attrValue < lowerBound) {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(attrName,
                attrValue, lowerBound));
    }
    if (attrValue > upperBound) {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(attrName,
                attrValue, upperBound));
    }
  }

  /**
   * Helper method to retieve an attribute value from given entry.
   * @param entry the entry to retrieve an attribute value from.
   * @param pwdAttrName attribute name to retrieve the value for.
   * @return <CODE>AttributeValue</CODE> or <CODE>null</CODE>.
   */
  private AttributeValue getAttrValue(Entry entry, String pwdAttrName) {
    AttributeType pwdAttrType = DirectoryServer.getAttributeType(
            pwdAttrName, true);
    List<Attribute> pwdAttrList = entry.getAttribute(pwdAttrType);
    if ((pwdAttrList != null) && (!pwdAttrList.isEmpty()))
    {
      for (Attribute attr : pwdAttrList)
      {
        for (AttributeValue value : attr)
        {
          return value;
        }
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void addChangeListener(
          ConfigurationChangeListener<PasswordPolicyCfg> listener) {
    // N/A : This is a subentry based configuration object.
  }

  /**
   * {@inheritDoc}
   */
  public void removeChangeListener(
          ConfigurationChangeListener<PasswordPolicyCfg> listener) {
    // N/A : This is a subentry based configuration object.
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getAccountStatusNotificationHandler() {
    return pAccountStatusNotificationHandler;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<DN> getAccountStatusNotificationHandlerDNs() {
    SortedSet<String> values = getAccountStatusNotificationHandler();
    SortedSet<DN> dnValues = new TreeSet<DN>();
    for (String value : values) {
      try {
        dnValues.add(DN.decode(value));
      } catch (DirectoryException de) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
      }
    }
    return dnValues;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAllowExpiredPasswordChanges() {
    return pAllowExpiredPasswordChanges;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAllowMultiplePasswordValues() {
    return pAllowMultiplePasswordValues;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAllowPreEncodedPasswords() {
    return pAllowPreEncodedPasswords;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAllowUserPasswordChanges() {
    return pAllowUserPasswordChanges;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getDefaultPasswordStorageScheme() {
    return pDefaultPasswordStorageScheme;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<DN> getDefaultPasswordStorageSchemeDNs() {
    SortedSet<String> values = getDefaultPasswordStorageScheme();
    SortedSet<DN> dnValues = new TreeSet<DN>();
    for (String value : values) {
      try {
        dnValues.add(DN.decode(value));
      } catch (DirectoryException de) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
      }
    }
    return dnValues;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getDeprecatedPasswordStorageScheme() {
    return pDeprecatedPasswordStorageScheme;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<DN> getDeprecatedPasswordStorageSchemeDNs() {
    SortedSet<String> values = getDeprecatedPasswordStorageScheme();
    SortedSet<DN> dnValues = new TreeSet<DN>();
    for (String value : values) {
      try {
        dnValues.add(DN.decode(value));
      } catch (DirectoryException de) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
      }
    }
    return dnValues;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isExpirePasswordsWithoutWarning() {
    return pExpirePasswordsWithoutWarning;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isForceChangeOnAdd() {
    return pForceChangeOnAdd;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isForceChangeOnReset() {
    return pForceChangeOnReset;
  }

  /**
   * {@inheritDoc}
   */
  public int getGraceLoginCount() {
    return pGraceLoginCount;
  }

  /**
   * {@inheritDoc}
   */
  public long getIdleLockoutInterval() {
    return pIdleLockoutInterval;
  }

  /**
   * {@inheritDoc}
   */
  public AttributeType getLastLoginTimeAttribute() {
    return pLastLoginTimeAttribute;
  }

  /**
   * {@inheritDoc}
   */
  public String getLastLoginTimeFormat() {
    return pLastLoginTimeFormat;
  }

  /**
   * {@inheritDoc}
   */
  public long getLockoutDuration() {
    return pLockoutDuration;
  }

  /**
   * {@inheritDoc}
   */
  public int getLockoutFailureCount() {
    return pLockoutFailureCount;
  }

  /**
   * {@inheritDoc}
   */
  public long getLockoutFailureExpirationInterval() {
    return pLockoutFailureExpirationInterval;
  }

  /**
   * {@inheritDoc}
   */
  public long getMaxPasswordAge() {
    return pMaxPasswordAge;
  }

  /**
   * {@inheritDoc}
   */
  public long getMaxPasswordResetAge() {
    return pMaxPasswordResetAge;
  }

  /**
   * {@inheritDoc}
   */
  public long getMinPasswordAge() {
    return pMinPasswordAge;
  }

  /**
   * {@inheritDoc}
   */
  public AttributeType getPasswordAttribute() {
    return pPasswordAttribute;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isPasswordChangeRequiresCurrentPassword() {
    return pPasswordChangeRequiresCurrentPassword;
  }

  /**
   * {@inheritDoc}
   */
  public long getPasswordExpirationWarningInterval() {
    return pPasswordExpirationWarningInterval;
  }

  /**
   * {@inheritDoc}
   */
  public String getPasswordGenerator() {
    return pPasswordGenerator;
  }

  /**
   * {@inheritDoc}
   */
  public DN getPasswordGeneratorDN() {
    String value = getPasswordGenerator();
    if (value == null) {
      return null;
    }
    try {
      return DN.decode(value);
    } catch (DirectoryException de) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public int getPasswordHistoryCount() {
    return pPasswordHistoryCount;
  }

  /**
   * {@inheritDoc}
   */
  public long getPasswordHistoryDuration() {
    return pPasswordHistoryDuration;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getPasswordValidator() {
    return pPasswordValidator;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<DN> getPasswordValidatorDNs() {
    SortedSet<String> values = getPasswordValidator();
    SortedSet<DN> dnValues = new TreeSet<DN>();
    for (String value : values) {
      try {
        dnValues.add(DN.decode(value));
      } catch (DirectoryException de) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
      }
    }
    return dnValues;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getPreviousLastLoginTimeFormat() {
    return pPreviousLastLoginTimeFormat;
  }

  /**
   * {@inheritDoc}
   */
  public String getRequireChangeByTime() {
    return pRequireChangeByTime;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isRequireSecureAuthentication() {
    return pRequireSecureAuthentication;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isRequireSecurePasswordChanges() {
    return pRequireSecurePasswordChanges;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSkipValidationForAdministrators() {
    return pSkipValidationForAdministrators;
  }

  /**
   * {@inheritDoc}
   */
  public StateUpdateFailurePolicy getStateUpdateFailurePolicy() {
    return pStateUpdateFailurePolicy;
  }

  /**
   * {@inheritDoc}
   */
  public Class<? extends PasswordPolicyCfg> configurationClass() {
    return PasswordPolicyCfg.class;
  }

  /**
   * {@inheritDoc}
   */
  public DN dn() {
    return passwordPolicySubentryDN;
  }
}
