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
 * Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.api;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SubEntry;
import org.opends.server.util.TimeThread;

/**
 * An abstract authentication policy.
 */
public abstract class AuthenticationPolicy
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Returns the authentication policy for the user provided user. The following
   * algorithm is used in order to obtain the appropriate authentication policy:
   * <ul>
   * <li>if the user entry contains the {@code ds-pwp-password-policy-dn}
   * attribute (whether real or virtual), then the referenced authentication
   * policy will be returned
   * <li>otherwise, a search is performed in order to find the nearest
   * applicable password policy sub-entry to the user entry,
   * <li>otherwise, the default password policy will be returned.
   * </ul>
   *
   * @param userEntry
   *          The user entry.
   * @param useDefaultOnError
   *          Indicates whether the server should fall back to using the default
   *          password policy if there is a problem with the configured policy
   *          for the user.
   * @return The password policy for the user.
   * @throws DirectoryException
   *           If a problem occurs while attempting to determine the password
   *           policy for the user.
   */
  public static AuthenticationPolicy forUser(Entry userEntry,
      boolean useDefaultOnError) throws DirectoryException
  {
    // First check to see if the ds-pwp-password-policy-dn is present.
    String userDNString = userEntry.getName().toString();
    AttributeType type = DirectoryServer.getSchema().getAttributeType(OP_ATTR_PWPOLICY_POLICY_DN);
    for (Attribute a : userEntry.getAttribute(type))
    {
      if (a.isEmpty())
      {
        continue;
      }

      ByteString v = a.iterator().next();
      DN subentryDN;
      try
      {
        subentryDN = DN.valueOf(v);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        logger.traceException(e);

        logger.trace("Could not parse password policy subentry DN %s for user %s",
            v, userDNString, e);

        if (useDefaultOnError)
        {
          logger.error(ERR_PWPSTATE_CANNOT_DECODE_SUBENTRY_VALUE_AS_DN,
              v, userDNString, e.getMessage());
          return DirectoryServer.getDefaultPasswordPolicy();
        }
        else
        {
          LocalizableMessage message = ERR_PWPSTATE_CANNOT_DECODE_SUBENTRY_VALUE_AS_DN
              .get(v, userDNString, e.getMessage());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message, e);
        }
      }

      AuthenticationPolicy policy = DirectoryServer
          .getAuthenticationPolicy(subentryDN);
      if (policy == null)
      {
        logger.trace("Password policy subentry %s for user %s is not defined in the Directory Server.",
                subentryDN, userDNString);

        LocalizableMessage message = ERR_PWPSTATE_NO_SUCH_POLICY.get(userDNString, subentryDN);
        if (useDefaultOnError)
        {
          logger.error(message);
          return DirectoryServer.getDefaultPasswordPolicy();
        }
        else
        {
          throw new DirectoryException(
              DirectoryServer.getServerErrorResultCode(), message);
        }
      }

      logger.trace("Using password policy subentry %s for user %s.",
            subentryDN, userDNString);

      return policy;
    }

    // The ds-pwp-password-policy-dn attribute was not present, so instead
    // search for the nearest applicable sub-entry.
    List<SubEntry> pwpSubEntries = DirectoryServer.getSubentryManager()
        .getSubentries(userEntry);
    if (pwpSubEntries != null && !pwpSubEntries.isEmpty())
    {
      for (SubEntry subentry : pwpSubEntries)
      {
        try
        {
          if (subentry.getEntry().isPasswordPolicySubentry())
          {
            AuthenticationPolicy policy = DirectoryServer
                .getAuthenticationPolicy(subentry.getDN());
            if (policy == null)
            {
              // This shouldn't happen but if it does debug log
              // this problem and fall back to default policy.
              logger.trace("Found unknown password policy subentry DN %s for user %s",
                  subentry.getDN(), userDNString);
              break;
            }
            return policy;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e, "Could not parse password policy subentry DN %s for user %s",
              subentry.getDN(), userDNString);
        }
      }
    }

    // No authentication policy found, so use the global default.
    logger.trace("Using the default password policy for user %s", userDNString);

    return DirectoryServer.getDefaultPasswordPolicy();
  }



  /**
   * Creates a new abstract authentication policy.
   */
  protected AuthenticationPolicy()
  {
    // No implementation required.
  }



  /**
   * Returns the name of the configuration entry associated with this
   * authentication policy.
   *
   * @return The name of the configuration entry associated with this
   *         authentication policy.
   */
  public abstract DN getDN();



  /**
   * Returns {@code true} if this authentication policy is a password policy and
   * the methods {@link #createAuthenticationPolicyState(Entry)} and
   * {@link #createAuthenticationPolicyState(Entry, long)} will return a
   * {@code PasswordPolicyState}.
   * <p>
   * The default implementation is to return {@code false}.
   *
   * @return {@code true} if this authentication policy is a password policy,
   *         otherwise {@code false}.
   */
  public boolean isPasswordPolicy()
  {
    return false;
  }



  /**
   * Returns the authentication policy state object for the provided user using
   * the current time as the basis for all time-based state logic (such as
   * expiring passwords).
   * <p>
   * The default implementation is to call
   * {@link #createAuthenticationPolicyState(Entry, long)} with the current
   * time.
   *
   * @param userEntry
   *          The user's entry.
   * @return The authentication policy state object for the provided user.
   * @throws DirectoryException
   *           If a problem occurs while attempting to initialize the state
   *           object from the provided user entry.
   */
  public AuthenticationPolicyState createAuthenticationPolicyState(
      Entry userEntry) throws DirectoryException
  {
    return createAuthenticationPolicyState(userEntry, TimeThread.getTime());
  }



  /**
   * Returns an authentication policy state object for the provided user using
   * the specified time as the basis for all time-based state logic (such as
   * expiring passwords).
   *
   * @param userEntry
   *          The user's entry.
   * @param time
   *          The time since the epoch to use for all time-based state logic
   *          (such as expiring passwords).
   * @return The authentication policy state object for the provided user.
   * @throws DirectoryException
   *           If a problem occurs while attempting to initialize the state
   *           object from the provided user entry.
   */
  public abstract AuthenticationPolicyState createAuthenticationPolicyState(
      Entry userEntry, long time) throws DirectoryException;



  /**
   * Performs any necessary work to finalize this authentication policy.
   * <p>
   * The default implementation is to do nothing.
   */
  public void finalizeAuthenticationPolicy()
  {
    // Do nothing by default.
  }
}
