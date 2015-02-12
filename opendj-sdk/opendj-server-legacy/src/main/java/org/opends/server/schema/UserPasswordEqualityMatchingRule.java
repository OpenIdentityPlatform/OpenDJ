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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.opends.server.api.PasswordStorageScheme;

import static org.opends.server.core.DirectoryServer.*;

/**
 * Implementation of the userPasswordMatch matching rule, which can be used
 * to determine whether a clear-text value matches an encoded password.
 * <p>
 * This matching rule serves a similar purpose to the equivalent
 * AuthPasswordEqualityMatchingRule defined in RFC 3112 (http://tools.ietf.org/html/rfc3112).
 */
class UserPasswordEqualityMatchingRule extends AbstractPasswordEqualityMatchingRuleImpl
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** {@inheritDoc} */
  @Override
  protected ConditionResult valuesMatch(ByteSequence attributeValue, ByteSequence assertionValue)
  {
    // We must be able to decode the attribute value using the user password
    // syntax.
    String[] userPWComponents;
    try
    {
      userPWComponents = UserPasswordSyntax.decodeUserPassword(attributeValue.toString());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return ConditionResult.FALSE;
    }

    // The first element of the array will be the scheme.
    // Make sure that we support the requested scheme.
    PasswordStorageScheme<?> storageScheme = getPasswordStorageScheme(userPWComponents[0]);
    if (storageScheme == null)
    {
      // It's not a scheme that we can support.
      return ConditionResult.FALSE;
    }

    // We support the scheme, so make the determination.
    return ConditionResult.valueOf(
        storageScheme.passwordMatches(assertionValue, ByteString.valueOf(userPWComponents[1])));
  }

}
