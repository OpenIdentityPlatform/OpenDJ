/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2012 profiq s.r.o.
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import
  org.opends.server.admin.std.server.PasswordExpirationTimeVirtualAttributeCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.core.SearchOperation;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.*;
import static org.opends.messages.ExtensionMessages.*;

/**
 * Provider for the password expiration time virtual attribute.
 */
public class PasswordExpirationTimeVirtualAttributeProvider
  extends VirtualAttributeProvider<PasswordExpirationTimeVirtualAttributeCfg>
{

  /**
   * Debug tracer to log debugging information.
   */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Default constructor.
   */
  public PasswordExpirationTimeVirtualAttributeProvider()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMultiValued()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
  {
    // Do not process LDAP operational entries.
    if (!entry.isSubentry() && !entry.isLDAPSubentry())
    {
      long expirationTime = getPasswordExpirationTime(entry);
      if (expirationTime == -1)
      {
        // It does not expire.
        return Attributes.empty(rule.getAttributeType());
      }
      return Attributes.create(rule.getAttributeType(),
          GeneralizedTimeSyntax.createGeneralizedTimeValue(expirationTime));
    }

    return Attributes.empty(rule.getAttributeType());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    LocalizableMessage message =
            ERR_PWDEXPTIME_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // Do not process LDAP operational entries.
    return !entry.isSubentry()
        && !entry.isLDAPSubentry()
        && getPasswordExpirationTime(entry) != -1;
  }

  /**
   * Utility method to wrap the PasswordPolicyState.getExpirationTime().
   *
   * @param entry LDAP entry
   * @return  Expiration time in milliseconds since the epoch.
   */
  private long getPasswordExpirationTime(Entry entry)
  {
    // Do not process LDAP operational entries.

    AuthenticationPolicy policy = null;

    try
    {
      policy = AuthenticationPolicy.forUser(entry, false);
    }
    catch (DirectoryException de)
    {
      logger.error(de.getMessageObject());

      logger.traceException(de, "Failed to retrieve password policy for user %s",
          entry.getName());
    }

    if (policy == null)
    {
      // No authentication policy: debug log this as an error since all
      // entries should have at least the default password policy.
      logger.trace("No applicable password policy for user %s", entry.getName());
    }
    else if (policy.isPasswordPolicy())
    {
      PasswordPolicyState pwpState = null;

      try
      {
        pwpState =
          (PasswordPolicyState) policy.createAuthenticationPolicyState(entry);
      }
      catch (DirectoryException de)
      {
        logger.error(de.getMessageObject());

        logger.traceException(de, "Failed to retrieve password policy state for user %s",
            entry.getName());
      }

      return pwpState.getPasswordExpirationTime();

    }
    else
    {
      // Not a password policy, could be PTA, etc.
      logger.trace("Authentication policy %s found for user %s is not a password policy",
          policy.getDN(), entry.getName());
    }

    return -1L;
  }
}
