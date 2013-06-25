/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2012 profiq s.r.o.
 *      Portions Copyright 2012 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Collections;
import org.opends.server.core.PasswordPolicyState;
import java.util.Set;
import org.opends.messages.Message;
import
  org.opends.server.admin.std.server.PasswordExpirationTimeVirtualAttributeCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Provider for the password expiration time virtual attribute.
 */
public class PasswordExpirationTimeVirtualAttributeProvider
  extends VirtualAttributeProvider<PasswordExpirationTimeVirtualAttributeCfg>
{

  /**
   * Debug tracer to log debugging information.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Default constructor.
   */
  public PasswordExpirationTimeVirtualAttributeProvider()
  {
    super();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void
    initializeVirtualAttributeProvider(
      PasswordExpirationTimeVirtualAttributeCfg configuration)
    throws ConfigException, InitializationException
  {
    // No initialization needed
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMultiValued()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<AttributeValue> getValues(Entry entry,
                                       VirtualAttributeRule rule)
  {
    // Do not process LDAP operational entries.
    if (!entry.isSubentry() && !entry.isLDAPSubentry())
    {
      long expirationTime = getPasswordExpirationTime(entry);

      if (expirationTime == -1)
      {
        // It does not expire.
        return Collections.emptySet();
      }

      AttributeValue value =
        GeneralizedTimeSyntax.createGeneralizedTimeValue(expirationTime);

      return Collections.singleton(value);
    }

    return Collections.emptySet();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    Message message =
            ERR_PWDEXPTIME_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // Do not process LDAP operational entries.
    if (entry.isSubentry() || entry.isLDAPSubentry()
        || (getPasswordExpirationTime(entry) == -1))
    {
      return false;
    }

    return true;
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
      ErrorLogger.logError(de.getMessageObject());

      if (debugEnabled())
      {
        TRACER.debugError("Failed to retrieve password " +
              "policy for user %s: %s",
              entry.getDN().toString(),
              stackTraceToSingleLineString(de));
      }
    }

    if (policy == null)
    {
      // No authentication policy: debug log this as an error since all
      // entries should have at least the default password policy.
      if (debugEnabled())
      {
        TRACER.debugError("No applicable password policy for user %s", entry
            .getDN().toString());
      }
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
        ErrorLogger.logError(de.getMessageObject());

        if (debugEnabled())
        {
          TRACER.debugError("Failed to retrieve password " +
                "policy state for user %s: %s",
                entry.getDN().toString(),
                stackTraceToSingleLineString(de));
        }
      }

      return pwpState.getPasswordExpirationTime();

    }
    else
    {
      // Not a password policy, could be PTA, etc.
      if (debugEnabled())
      {
        TRACER.debugVerbose("Authentication policy %s found for user %s is "
            + "not a password policy", policy.getDN().toString(), entry
            .getDN().toString());
      }
    }

    return -1L;
  }
}
