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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.MatchingRuleFactory;
import org.forgerock.opendj.server.config.server.MatchingRuleCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.server.types.InitializationException;

 /**
 * This class is a factory class for
  * {@link HistoricalCsnOrderingMatchingRule}.
 */
public final class HistoricalCsnOrderingMatchingRuleFactory
        extends MatchingRuleFactory<MatchingRuleCfg>
{

  /** Associated Matching Rule. */
  private MatchingRule matchingRule;

 /** {@inheritDoc} */
 @Override
 public final void initializeMatchingRule(MatchingRuleCfg configuration)
         throws ConfigException, InitializationException
 {
   final String oid = "1.3.6.1.4.1.26027.1.4.4";
   matchingRule = new SchemaBuilder(CoreSchema.getInstance()).buildMatchingRule(oid)
       .names("historicalCsnOrderingMatch")
       .syntaxOID("1.3.6.1.4.1.1466.115.121.1.40")
       .implementation(new HistoricalCsnOrderingMatchingRuleImpl())
       .addToSchema().toSchema().getMatchingRule(oid);
 }

 /** {@inheritDoc} */
 @Override
 public final Collection<MatchingRule> getMatchingRules()
 {
    return Collections.singleton(matchingRule);
 }
}
