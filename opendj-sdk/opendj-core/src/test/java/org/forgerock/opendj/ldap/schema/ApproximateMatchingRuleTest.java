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
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.AMR_DOUBLE_METAPHONE_NAME;
import static org.testng.Assert.assertEquals;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Approximate matching rule tests.
 */
@SuppressWarnings("javadoc")
public class ApproximateMatchingRuleTest extends AbstractSchemaTestCase {
    MatchingRule metaphone = Schema.getCoreSchema().getMatchingRule(AMR_DOUBLE_METAPHONE_NAME);

    /**
     * Test the normalization and the approximate comparison.
     */
    @Test(dataProvider = "approximatematchingrules")
    public void approximateMatchingRules(final MatchingRule rule, final String value1,
            final String value2, final ConditionResult result) throws Exception {
        // normalize the 2 provided values
        final ByteString normalizedValue1 =
                rule.normalizeAttributeValue(ByteString.valueOfUtf8(value1));

        // check that the approximatelyMatch return the expected result.
        final ConditionResult liveResult =
                rule.getAssertion(ByteString.valueOfUtf8(value2)).matches(normalizedValue1);
        assertEquals(result, liveResult);
    }

    /**
     * Build the data for the approximateMatchingRules test.
     */
    @DataProvider(name = "approximatematchingrules")
    public Object[][] createapproximateMatchingRuleTest() {
        // fill this table with tables containing :
        // - the name of the approximate matching rule to test
        // - 2 values that must be tested for matching
        // - a boolean indicating if the values match or not
        return new Object[][] { { metaphone, "celebre", "selebre", ConditionResult.TRUE },
            { metaphone, "cygale", "sigale", ConditionResult.TRUE },
            { metaphone, "cigale", "sigale", ConditionResult.TRUE },
            { metaphone, "accacia", "akacia", ConditionResult.TRUE },
            { metaphone, "cigale", "sigale", ConditionResult.TRUE },
            { metaphone, "bertucci", "bertuchi", ConditionResult.TRUE },
            { metaphone, "manger", "manjer", ConditionResult.TRUE },
            { metaphone, "gyei", "kei", ConditionResult.TRUE },
            { metaphone, "agnostique", "aknostic", ConditionResult.TRUE },
            { metaphone, "ghang", "kang", ConditionResult.TRUE },
            { metaphone, "affiche", "afiche", ConditionResult.TRUE },
            { metaphone, "succeed", "sukid", ConditionResult.TRUE },
            { metaphone, "McCarthur", "macarthur", ConditionResult.TRUE },
            { metaphone, "czet", "set", ConditionResult.TRUE },
            { metaphone, "re\u00C7u", "ressu", ConditionResult.TRUE },
            { metaphone, "ni\u00D1o", "nino", ConditionResult.TRUE },
            { metaphone, "bateaux", "bateau", ConditionResult.TRUE },
            { metaphone, "witz", "wits", ConditionResult.TRUE },
            { metaphone, "barre", "bare", ConditionResult.TRUE },
            { metaphone, "write", "rite", ConditionResult.TRUE },
            { metaphone, "the", "ze", ConditionResult.FALSE },
            { metaphone, "motion", "mochion", ConditionResult.TRUE },
            { metaphone, "bois", "boi", ConditionResult.TRUE },
            { metaphone, "schi", "chi", ConditionResult.TRUE },
            { metaphone, "escalier", "eskalier", ConditionResult.TRUE },
            { metaphone, "science", "sience", ConditionResult.TRUE },
            { metaphone, "school", "skool", ConditionResult.TRUE },
            { metaphone, "swap", "sap", ConditionResult.TRUE },
            { metaphone, "szize", "size", ConditionResult.TRUE },
            { metaphone, "shoek", "choek", ConditionResult.FALSE },
            { metaphone, "sugar", "chugar", ConditionResult.TRUE },
            { metaphone, "isle", "ile", ConditionResult.TRUE },
            { metaphone, "yle", "ysle", ConditionResult.TRUE },
            { metaphone, "focaccia", "focashia", ConditionResult.TRUE },
            { metaphone, "machine", "mashine", ConditionResult.TRUE },
            { metaphone, "michael", "mikael", ConditionResult.TRUE },
            { metaphone, "abba", "aba", ConditionResult.TRUE },
            { metaphone, "caesar", "saesar", ConditionResult.TRUE },
            { metaphone, "femme", "fame", ConditionResult.TRUE },
            { metaphone, "panne", "pane", ConditionResult.TRUE },
            { metaphone, "josa", "josa", ConditionResult.TRUE },
            { metaphone, "jose", "hose", ConditionResult.TRUE },
            { metaphone, "hello", "hello", ConditionResult.TRUE },
            { metaphone, "hello", "ello", ConditionResult.FALSE },
            { metaphone, "bag", "bak", ConditionResult.TRUE },
            { metaphone, "bagg", "bag", ConditionResult.TRUE },
            { metaphone, "tagliaro", "takliaro", ConditionResult.TRUE },
            { metaphone, "biaggi", "biaji", ConditionResult.TRUE },
            { metaphone, "bioggi", "bioji", ConditionResult.TRUE },
            { metaphone, "rough", "rouf", ConditionResult.TRUE },
            { metaphone, "ghislane", "jislane", ConditionResult.TRUE },
            { metaphone, "ghaslane", "kaslane", ConditionResult.TRUE },
            { metaphone, "odd", "ot", ConditionResult.TRUE },
            { metaphone, "edgar", "etkar", ConditionResult.TRUE },
            { metaphone, "edge", "eje", ConditionResult.TRUE },
            { metaphone, "accord", "akord", ConditionResult.TRUE },
            { metaphone, "noize", "noise", ConditionResult.TRUE },
            { metaphone, "orchid", "orkid", ConditionResult.TRUE },
            { metaphone, "chemistry", "kemistry", ConditionResult.TRUE },
            { metaphone, "chianti", "kianti", ConditionResult.TRUE },
            { metaphone, "bacher", "baker", ConditionResult.TRUE },
            { metaphone, "achtung", "aktung", ConditionResult.TRUE },
            { metaphone, "Writing", "riting", ConditionResult.TRUE },
            { metaphone, "xeon", "zeon", ConditionResult.TRUE },
            { metaphone, "lonely", "loneli", ConditionResult.TRUE },
            { metaphone, "bellaton", "belatton", ConditionResult.TRUE },
            { metaphone, "pate", "patte", ConditionResult.TRUE },
            { metaphone, "voiture", "vouatur", ConditionResult.TRUE },
            { metaphone, "garbage", "garbedge", ConditionResult.TRUE },
            { metaphone, "algorithme", "algorizm", ConditionResult.TRUE },
            { metaphone, "testing", "testng", ConditionResult.TRUE },
            { metaphone, "announce", "annonce", ConditionResult.TRUE },
            { metaphone, "automaticly", "automatically", ConditionResult.TRUE },
            { metaphone, "modifyd", "modified", ConditionResult.TRUE },
            { metaphone, "bouteille", "butaille", ConditionResult.TRUE },
            { metaphone, "xeon", "zeon", ConditionResult.TRUE },
            { metaphone, "achtung", "aktung", ConditionResult.TRUE },
            { metaphone, "throttle", "throddle", ConditionResult.TRUE },
            { metaphone, "thimble", "thimblle", ConditionResult.TRUE },
            { metaphone, "", "", ConditionResult.TRUE }, };
    }
}
