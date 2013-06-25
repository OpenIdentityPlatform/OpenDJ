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

package org.opends.server.extensions;

import java.util.Collections;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.
        PasswordPolicySubentryVirtualAttributeCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements a virtual attribute provider to serve
 * the pwdPolicySubentry operational attribute as described in
 * Password Policy for LDAP Directories Internet-Draft.
 */
public class PasswordPolicySubentryVirtualAttributeProvider
        extends VirtualAttributeProvider<
        PasswordPolicySubentryVirtualAttributeCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new instance of this pwdPolicySubentry
   * virtual attribute provider.
   */
  public PasswordPolicySubentryVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeVirtualAttributeProvider(
          PasswordPolicySubentryVirtualAttributeCfg configuration)
          throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isMultiValued()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,
                                       VirtualAttributeRule rule)
  {
    if (!entry.isSubentry() && !entry.isLDAPSubentry())
    {
      AuthenticationPolicy policy = null;

      try
      {
        policy = AuthenticationPolicy.forUser(entry, false);
      }
      catch (DirectoryException de)
      {
        // Something went wrong while trying to
        // retrieve password policy, log this.
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
        AttributeType dnAttrType = DirectoryServer
            .getAttributeType("1.3.6.1.4.1.42.2.27.8.1.23");
        DN policyDN = policy.getDN();
        AttributeValue value = AttributeValues.create(dnAttrType,
            policyDN.toString());
        return Collections.singleton(value);
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
    }

    return Collections.emptySet();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    Message message =
            ERR_PASSWORDPOLICYSUBENTRY_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }
}
