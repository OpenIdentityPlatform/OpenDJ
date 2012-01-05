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
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package org.opends.server.core;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.*;

import org.opends.messages.Message;
import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn.*;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;



/**
 * This class represents subentry password policy based on Password Policy for
 * LDAP Directories Internet-Draft. In order to represent subentry password
 * policies as OpenDJ password policies it performs a mapping of Draft defined
 * attributes to OpenDJ implementation specific attributes. Any missing
 * attributes are inherited from server default password policy. This class is
 * also reponsible for any Draft attributes validation ie making sure that
 * provided values are acceptable and within the predefined range.
 */
public final class SubentryPasswordPolicy extends PasswordPolicy
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Password Policy Subentry draft attributes.
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

  // Password Policy Subentry DN.
  private final DN passwordPolicySubentryDN;
  // The value of the "allow-user-password-changes" property.
  private final Boolean pAllowUserPasswordChanges;
  // The value of the "force-change-on-reset" property.
  private final Boolean pForceChangeOnReset;
  // The value of the "grace-login-count" property.
  private final Integer pGraceLoginCount;
  // The value of the "lockout-duration" property.
  private final Long pLockoutDuration;
  // The value of the "lockout-failure-count" property.
  private final Integer pLockoutFailureCount;
  // The value of the "lockout-failure-expiration-interval" property.
  private final Long pLockoutFailureExpirationInterval;
  // The value of the "max-password-age" property.
  private final Long pMaxPasswordAge;
  // The value of the "min-password-age" property.
  private final Long pMinPasswordAge;
  // The value of the "password-attribute" property.
  private final AttributeType pPasswordAttribute;
  // The value of the "password-change-requires-current-password" property.
  private final Boolean pPasswordChangeRequiresCurrentPassword;
  // The value of the "password-expiration-warning-interval" property.
  private final Long pPasswordExpirationWarningInterval;
  // The value of the "password-history-count" property.
  private final Integer pPasswordHistoryCount;
  // Indicates if the password attribute uses auth password syntax.
  private final Boolean pAuthPasswordSyntax;



  // Returns the global default password policy which will be used for deriving
  // the default properties of sub-entries.
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
    ObjectClass pwdPolicyOC = DirectoryServer.getObjectClass(PWD_OC_POLICY);
    Entry entry = subentry.getEntry();
    Map<ObjectClass, String> objectClasses = entry.getObjectClasses();
    if (pwdPolicyOC == null)
    {
      // This should not happen -- The server doesn't
      // have a pwdPolicy objectclass defined.
      if (debugEnabled())
      {
        TRACER
            .debugWarning("No %s objectclass is defined in the server schema.",
                PWD_OC_POLICY);
      }
      for (String ocName : objectClasses.values())
      {
        if (ocName.equalsIgnoreCase(PWD_OC_POLICY))
        {
          break;
        }
      }
      Message message = ERR_PWPOLICY_NO_PWDPOLICY_OC.get(subentry.getDN()
          .toString());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    else if (!objectClasses.containsKey(pwdPolicyOC))
    {
      Message message = ERR_PWPOLICY_NO_PWDPOLICY_OC.get(subentry.getDN()
          .toString());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    // Subentry DN for this password policy.
    this.passwordPolicySubentryDN = subentry.getDN();

    // Get known Password Policy draft attributes from the entry.
    // If any given attribute is missing or empty set its value
    // from default Password Policy configuration.
    AttributeValue value = getAttrValue(entry, PWD_ATTR_ATTRIBUTE);
    if ((value != null) && (value.toString().length() > 0))
    {
      this.pPasswordAttribute = DirectoryServer.getAttributeType(value
          .toString().toLowerCase(), false);
      if (this.pPasswordAttribute == null)
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_PWPOLICY_UNDEFINED_PASSWORD_ATTRIBUTE.get(
                this.passwordPolicySubentryDN.toNormalizedString(),
                value.toString()));
      }

      // Check the syntax.
      final String syntaxOID = pPasswordAttribute.getSyntaxOID();
      if (syntaxOID.equals(SYNTAX_AUTH_PASSWORD_OID))
      {
        pAuthPasswordSyntax = true;
      }
      else if (syntaxOID.equals(SYNTAX_USER_PASSWORD_OID))
      {
        pAuthPasswordSyntax = false;
      }
      else
      {
        String syntax = pPasswordAttribute.getSyntax().getSyntaxName();
        if ((syntax == null) || (syntax.length() == 0))
        {
          syntax = syntaxOID;
        }

        Message message = ERR_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX.get(
            String.valueOf(passwordPolicySubentryDN),
            pPasswordAttribute.getNameOrOID(), String.valueOf(syntax));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    else
    {
      this.pPasswordAttribute = null;
      this.pAuthPasswordSyntax = null;
    }

    value = getAttrValue(entry, PWD_ATTR_MINAGE);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pMinPasswordAge = Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_MINAGE, this.pMinPasswordAge, 0,
            Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pMinPasswordAge = null;
    }

    value = getAttrValue(entry, PWD_ATTR_MAXAGE);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pMaxPasswordAge = Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_MAXAGE, this.pMaxPasswordAge, 0,
            Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pMaxPasswordAge = null;
    }

    value = getAttrValue(entry, PWD_ATTR_INHISTORY);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pPasswordHistoryCount = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_INHISTORY, this.pPasswordHistoryCount, 0,
            Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pPasswordHistoryCount = null;
    }

    // This one is managed via the password validator
    // so only check if its value is acceptable.
    value = getAttrValue(entry, PWD_ATTR_CHECKQUALITY);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        int pwdCheckQuality = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_CHECKQUALITY, pwdCheckQuality, 0, 2);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }

    // This one is managed via the password validator
    // so only check if its value is acceptable.
    value = getAttrValue(entry, PWD_ATTR_MINLENGTH);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        int pwdMinLength = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_MINLENGTH, pwdMinLength, 0,Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }

    // This one depends on lockout failure count value
    // so only check if its value is acceptable.
    value = getAttrValue(entry, PWD_ATTR_LOCKOUT);
    if ((value != null) && (value.toString().length() > 0))
    {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString())
          || value.toString().equalsIgnoreCase(Boolean.FALSE.toString()))
      {
        Boolean.parseBoolean(value.toString());
      }
      else
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(PWD_ATTR_MUSTCHANGE,
                value.toString()));
      }
    }

    value = getAttrValue(entry, PWD_ATTR_EXPIREWARNING);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pPasswordExpirationWarningInterval = Long.parseLong(value
            .toString());
        checkIntegerAttr(PWD_ATTR_EXPIREWARNING,
            this.pPasswordExpirationWarningInterval, 0, Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pPasswordExpirationWarningInterval = null;
    }

    value = getAttrValue(entry, PWD_ATTR_GRACEAUTHNLIMIT);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pGraceLoginCount = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_GRACEAUTHNLIMIT, this.pGraceLoginCount, 0,
            Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pGraceLoginCount = null;
    }

    value = getAttrValue(entry, PWD_ATTR_LOCKOUTDURATION);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pLockoutDuration = Long.parseLong(value.toString());
        checkIntegerAttr(PWD_ATTR_LOCKOUTDURATION, this.pLockoutDuration, 0,
            Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pLockoutDuration = null;
    }

    value = getAttrValue(entry, PWD_ATTR_MAXFAILURE);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pLockoutFailureCount = Integer.parseInt(value.toString());
        checkIntegerAttr(PWD_ATTR_MAXFAILURE, this.pLockoutFailureCount, 0,
            Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(PWD_ATTR_MINAGE,
                value.toString(), ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pLockoutFailureCount = null;
    }

    value = getAttrValue(entry, PWD_ATTR_MUSTCHANGE);
    if ((value != null) && (value.toString().length() > 0))
    {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString())
          || value.toString().equalsIgnoreCase(Boolean.FALSE.toString()))
      {
        this.pForceChangeOnReset = Boolean.parseBoolean(value.toString());
      }
      else
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(PWD_ATTR_MUSTCHANGE,
                value.toString()));
      }
    }
    else
    {
      this.pForceChangeOnReset = null;
    }

    value = getAttrValue(entry, PWD_ATTR_ALLOWUSERCHANGE);
    if ((value != null) && (value.toString().length() > 0))
    {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString())
          || value.toString().equalsIgnoreCase(Boolean.FALSE.toString()))
      {
        this.pAllowUserPasswordChanges = Boolean.parseBoolean(value.toString());
      }
      else
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(PWD_ATTR_ALLOWUSERCHANGE,
                value.toString()));
      }
    }
    else
    {
      this.pAllowUserPasswordChanges = null;
    }

    value = getAttrValue(entry, PWD_ATTR_SAFEMODIFY);
    if ((value != null) && (value.toString().length() > 0))
    {
      if (value.toString().equalsIgnoreCase(Boolean.TRUE.toString())
          || value.toString().equalsIgnoreCase(Boolean.FALSE.toString()))
      {
        this.pPasswordChangeRequiresCurrentPassword = Boolean
            .parseBoolean(value.toString());
      }
      else
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_BOOLEAN_VALUE.get(PWD_ATTR_SAFEMODIFY,
                value.toString()));
      }
    }
    else
    {
      this.pPasswordChangeRequiresCurrentPassword = null;
    }

    value = getAttrValue(entry, PWD_ATTR_FAILURECOUNTINTERVAL);
    if ((value != null) && (value.toString().length() > 0))
    {
      try
      {
        this.pLockoutFailureExpirationInterval = Long.parseLong(value
            .toString());
        checkIntegerAttr(PWD_ATTR_FAILURECOUNTINTERVAL,
            this.pLockoutFailureExpirationInterval, 0, Integer.MAX_VALUE);
      }
      catch (NumberFormatException ne)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
                PWD_ATTR_FAILURECOUNTINTERVAL, value.toString(),
                ne.getLocalizedMessage()));
      }
    }
    else
    {
      this.pLockoutFailureExpirationInterval = null;
    }
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
   * Helper method to retieve an attribute value from given entry.
   *
   * @param entry
   *          the entry to retrieve an attribute value from.
   * @param pwdAttrName
   *          attribute name to retrieve the value for.
   * @return <CODE>AttributeValue</CODE> or <CODE>null</CODE>.
   */
  private AttributeValue getAttrValue(Entry entry, String pwdAttrName)
  {
    AttributeType pwdAttrType = DirectoryServer.getAttributeType(pwdAttrName,
        true);
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
  @Override
  public boolean isAllowExpiredPasswordChanges()
  {
    return getDefaultPasswordPolicy().isAllowExpiredPasswordChanges();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowMultiplePasswordValues()
  {
    return getDefaultPasswordPolicy().isAllowMultiplePasswordValues();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowPreEncodedPasswords()
  {
    return getDefaultPasswordPolicy().isAllowPreEncodedPasswords();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowUserPasswordChanges()
  {
    return pAllowUserPasswordChanges != null ? pAllowUserPasswordChanges
        : getDefaultPasswordPolicy().isAllowUserPasswordChanges();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isExpirePasswordsWithoutWarning()
  {
    return getDefaultPasswordPolicy().isExpirePasswordsWithoutWarning();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isForceChangeOnAdd()
  {
    // Don't use pwdMustChange since the password provided when the entry was
    // added may have been provided by the user. See OPENDJ-341.
    return getDefaultPasswordPolicy().isForceChangeOnAdd();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isForceChangeOnReset()
  {
    return pForceChangeOnReset != null ? pForceChangeOnReset
        : getDefaultPasswordPolicy().isForceChangeOnReset();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int getGraceLoginCount()
  {
    return pGraceLoginCount != null ? pGraceLoginCount
        : getDefaultPasswordPolicy().getGraceLoginCount();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getIdleLockoutInterval()
  {
    return getDefaultPasswordPolicy().getIdleLockoutInterval();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeType getLastLoginTimeAttribute()
  {
    return getDefaultPasswordPolicy().getLastLoginTimeAttribute();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getLastLoginTimeFormat()
  {
    return getDefaultPasswordPolicy().getLastLoginTimeFormat();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getLockoutDuration()
  {
    return pLockoutDuration != null ? pLockoutDuration
        : getDefaultPasswordPolicy().getLockoutDuration();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int getLockoutFailureCount()
  {
    return pLockoutFailureCount != null ? pLockoutFailureCount
        : getDefaultPasswordPolicy().getLockoutFailureCount();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getLockoutFailureExpirationInterval()
  {
    return pLockoutFailureExpirationInterval != null ?
        pLockoutFailureExpirationInterval
        : getDefaultPasswordPolicy().getLockoutFailureExpirationInterval();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getMaxPasswordAge()
  {
    return pMaxPasswordAge != null ? pMaxPasswordAge
        : getDefaultPasswordPolicy().getMaxPasswordAge();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getMaxPasswordResetAge()
  {
    return getDefaultPasswordPolicy().getMaxPasswordResetAge();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getMinPasswordAge()
  {
    return pMinPasswordAge != null ? pMinPasswordAge
        : getDefaultPasswordPolicy().getMinPasswordAge();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeType getPasswordAttribute()
  {
    return pPasswordAttribute != null ? pPasswordAttribute
        : getDefaultPasswordPolicy().getPasswordAttribute();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isPasswordChangeRequiresCurrentPassword()
  {
    return pPasswordChangeRequiresCurrentPassword != null ?
        pPasswordChangeRequiresCurrentPassword
        : getDefaultPasswordPolicy().isPasswordChangeRequiresCurrentPassword();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getPasswordExpirationWarningInterval()
  {
    return pPasswordExpirationWarningInterval != null ?
        pPasswordExpirationWarningInterval
        : getDefaultPasswordPolicy().getPasswordExpirationWarningInterval();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int getPasswordHistoryCount()
  {
    return pPasswordHistoryCount != null ? pPasswordHistoryCount
        : getDefaultPasswordPolicy().getPasswordHistoryCount();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getPasswordHistoryDuration()
  {
    return getDefaultPasswordPolicy().getPasswordHistoryDuration();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SortedSet<String> getPreviousLastLoginTimeFormats()
  {
    return getDefaultPasswordPolicy().getPreviousLastLoginTimeFormats();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public long getRequireChangeByTime()
  {
    return getDefaultPasswordPolicy().getRequireChangeByTime();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isRequireSecureAuthentication()
  {
    return getDefaultPasswordPolicy().isRequireSecureAuthentication();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isRequireSecurePasswordChanges()
  {
    return getDefaultPasswordPolicy().isRequireSecurePasswordChanges();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSkipValidationForAdministrators()
  {
    return getDefaultPasswordPolicy().isSkipValidationForAdministrators();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public StateUpdateFailurePolicy getStateUpdateFailurePolicy()
  {
    return getDefaultPasswordPolicy().getStateUpdateFailurePolicy();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAuthPasswordSyntax()
  {
    return pAuthPasswordSyntax != null ? pAuthPasswordSyntax
        : getDefaultPasswordPolicy().isAuthPasswordSyntax();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public List<PasswordStorageScheme<?>> getDefaultPasswordStorageSchemes()
  {
    return getDefaultPasswordPolicy().getDefaultPasswordStorageSchemes();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getDeprecatedPasswordStorageSchemes()
  {
    return getDefaultPasswordPolicy().getDeprecatedPasswordStorageSchemes();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public DN getDN()
  {
    return passwordPolicySubentryDN;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDefaultPasswordStorageScheme(String name)
  {
    return getDefaultPasswordPolicy().isDefaultPasswordStorageScheme(name);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDeprecatedPasswordStorageScheme(String name)
  {
    return getDefaultPasswordPolicy().isDeprecatedPasswordStorageScheme(name);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<PasswordValidator<?>> getPasswordValidators()
  {
    return getDefaultPasswordPolicy().getPasswordValidators();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<AccountStatusNotificationHandler<?>>
    getAccountStatusNotificationHandlers()
  {
    return getDefaultPasswordPolicy().getAccountStatusNotificationHandlers();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PasswordGenerator<?> getPasswordGenerator()
  {
    return getDefaultPasswordPolicy().getPasswordGenerator();
  }

}
