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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */


package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.types.InitializationException;

/**
 * This class is a factory class for ObjectIdentifierFirstComponentEqualityMatchingRule.
 */
public final class ObjectIdentifierFirstComponentEqualityMatchingRuleFactory
        extends MatchingRuleFactory<MatchingRuleCfg>
{
  /** Associated Matching Rule. */
  private org.forgerock.opendj.ldap.schema.MatchingRule matchingRule;

 /** {@inheritDoc} */
 @Override
 public final void initializeMatchingRule(MatchingRuleCfg configuration)
         throws ConfigException, InitializationException
 {
   matchingRule = CoreSchema.getObjectIdentifierFirstComponentMatchingRule();
 }

 /** {@inheritDoc} */
 @Override
 public final Collection<org.forgerock.opendj.ldap.schema.MatchingRule> getMatchingRules()
 {
    return Collections.singleton(matchingRule);
 }
}
