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
import org.forgerock.opendj.ldap.ConditionResult;
import org.opends.server.api.PasswordStorageScheme;

import static org.opends.server.core.DirectoryServer.*;

/**
 * This class implements the authPasswordMatch matching rule defined in RFC
 * 3112.
 */
class AuthPasswordEqualityMatchingRule extends AbstractPasswordEqualityMatchingRuleImpl
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** {@inheritDoc} */
  @Override
  protected ConditionResult valuesMatch(ByteSequence attributeValue, ByteSequence assertionValue)
  {
    // We must be able to decode the attribute value using the authentication
    // password syntax.
    StringBuilder[] authPWComponents;
    try
    {
      authPWComponents = AuthPasswordSyntax.decodeAuthPassword(attributeValue.toString());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return ConditionResult.FALSE;
    }

    // The first element of the array will be the scheme.
    // Make sure that we support the requested scheme.
    PasswordStorageScheme<?> storageScheme = getAuthPasswordStorageScheme(authPWComponents[0].toString());
    if (storageScheme == null)
    {
      // It's not a scheme that we can support.
      return ConditionResult.FALSE;
    }

    // We support the scheme, so make the determination.
    return ConditionResult.valueOf(
        storageScheme.authPasswordMatches(assertionValue,
                                          authPWComponents[1].toString(),
                                          authPWComponents[2].toString()));
  }

}
