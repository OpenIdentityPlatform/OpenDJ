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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.types.InitializationException;

/**
 * This class acts as a factory for time-based matching rules.
 */
public final class TimeBasedMatchingRuleFactory
        extends MatchingRuleFactory<MatchingRuleCfg>
{

  /** A Collection of matching rules managed by this factory. */
  private Set<MatchingRule> matchingRules;

  /** {@inheritDoc} */
  @Override
  public void initializeMatchingRule(MatchingRuleCfg configuration)
          throws ConfigException, InitializationException
  {
    matchingRules = new HashSet<>();
    // relative time greater than
    matchingRules.add(CoreSchema.getInstance().getMatchingRule("1.3.6.1.4.1.26027.1.4.5"));
    // relative time less than
    matchingRules.add(CoreSchema.getInstance().getMatchingRule("1.3.6.1.4.1.26027.1.4.6"));
    // partial date and time
    matchingRules.add(CoreSchema.getInstance().getMatchingRule("1.3.6.1.4.1.26027.1.4.7"));
  }

  /** {@inheritDoc} */
  @Override
  public Collection<MatchingRule> getMatchingRules()
  {
    return Collections.unmodifiableCollection(matchingRules);
  }

}
