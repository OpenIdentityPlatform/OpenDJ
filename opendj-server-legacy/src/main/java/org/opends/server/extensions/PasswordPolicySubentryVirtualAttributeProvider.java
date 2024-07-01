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
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.
        PasswordPolicySubentryVirtualAttributeCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;
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

  /** Creates a new instance of this pwdPolicySubentry virtual attribute provider. */
  public PasswordPolicySubentryVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  @Override
  public boolean isMultiValued()
  {
    return false;
  }

  @Override
  public Attribute getValues(Entry entry, VirtualAttributeRule rule)
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
        return Attributes.create(rule.getAttributeType(),
            policy.getDN().toString());
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

    return Attributes.empty(rule.getAttributeType());
  }

  @Override
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }

  @Override
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
