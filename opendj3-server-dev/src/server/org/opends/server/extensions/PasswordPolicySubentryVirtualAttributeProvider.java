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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.
        PasswordPolicySubentryVirtualAttributeCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements a virtual attribute provider to serve
 * the pwdPolicySubentry operational attribute as described in
 * Password Policy for LDAP Directories Internet-Draft.
 */
public class PasswordPolicySubentryVirtualAttributeProvider
        extends VirtualAttributeProvider<
        PasswordPolicySubentryVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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

  /** {@inheritDoc} */
  @Override()
  public boolean isMultiValued()
  {
    return false;
  }

  /** {@inheritDoc} */
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
        if (logger.isTraceEnabled())
        {
          logger.trace("Authentication policy %s found for user %s is "
              + "not a password policy", policy.getDN(), entry.getName());
        }
      }
    }

    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    LocalizableMessage message =
            ERR_PASSWORDPOLICYSUBENTRY_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }
}
