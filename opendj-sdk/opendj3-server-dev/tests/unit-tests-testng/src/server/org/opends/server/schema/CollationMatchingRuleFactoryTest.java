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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.*;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.meta.CollationMatchingRuleCfgDefn.MatchingRuleType;
import org.opends.server.admin.std.server.CollationMatchingRuleCfg;
import org.opends.server.api.MatchingRule;
import org.opends.server.types.InitializationException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("javadoc")
public class CollationMatchingRuleFactoryTest extends SchemaTestCase
{

  @Test
  public void testMatchingRulesBuilding() throws Exception
  {
    final List<String> allRuleNames = new ArrayList<String>();
    final Collection<MatchingRule> mRules =
        getMatchingRules("fr:1.3.6.1.4.1.42.2.27.9.4.76.1");
    for (MatchingRule rule : mRules)
    {
      allRuleNames.addAll(rule.getNames());
    }
    assertThat(allRuleNames).containsOnly("fr",
        "fr.1", "fr.lt", "fr.2", "fr.lte",
        "fr.3", "fr.eq",
        "fr.4", "fr.gte", "fr.5", "fr.gt",
        "fr.6", "fr.sub");
  }

  @Test
  public void testRecursiveMatchingRulesBuilding() throws Exception
  {
    final List<String> allRuleNames = new ArrayList<String>();
    final Collection<MatchingRule> mRules =
        getMatchingRules("fr-FR:1.3.6.1.4.1.42.2.27.9.4.76.1");
    for (MatchingRule rule : mRules)
    {
      allRuleNames.addAll(rule.getNames());
    }
    assertThat(allRuleNames).containsOnly("fr-FR",
        "fr-FR.1", "fr-FR.lt", "fr-FR.2", "fr-FR.lte",
        "fr-FR.3", "fr-FR.eq",
        "fr-FR.4", "fr-FR.gte", "fr-FR.5", "fr-FR.gt",
        "fr-FR.6", "fr-FR.sub");
  }

  private Collection<MatchingRule> getMatchingRules(String collationOID)
      throws ConfigException, InitializationException
  {
    final CollationMatchingRuleFactory factory =
        new CollationMatchingRuleFactory();
    final CollationMatchingRuleCfg cfg = mock(CollationMatchingRuleCfg.class);
    when(cfg.getMatchingRuleType()).thenReturn(
        toSortedSet(EnumSet.allOf(MatchingRuleType.class)));
    when(cfg.getCollation()).thenReturn(
        toSortedSet(Collections.singleton(collationOID)));
    factory.initializeMatchingRule(cfg);
    return factory.getMatchingRules();
  }

  private <T> SortedSet<T> toSortedSet(Set<T> set)
  {
    return new TreeSet<T>(set);
  }
}
