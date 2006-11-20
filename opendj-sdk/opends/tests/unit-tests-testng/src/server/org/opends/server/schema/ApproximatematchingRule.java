/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import static org.testng.Assert.*;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ApproximatematchingRule extends SchemaTestCase
{
  /**
   * Build the data for the approximateMatchingRules test.
   */
  @DataProvider(name="approximatematchingrules")
  public Object[][] createapproximateMatchingRuleTest()
  {
    // fill this table with tables containing :
    // - the name of the approxiamtematchingrule to test
    // - 2 values that must be tested for matching
    // - a boolean indicating if the values match or not
    return new Object[][] {
        {"DoubleMetaphoneApproximateMatchingRule", "celebre", "selebre", true},
        {"DoubleMetaphoneApproximateMatchingRule", "cygale", "sigale", true},
        {"DoubleMetaphoneApproximateMatchingRule", "cigale", "sigale", true},
        {"DoubleMetaphoneApproximateMatchingRule", "accacia", "akacia", true},
        {"DoubleMetaphoneApproximateMatchingRule", "cigale", "sigale", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bertucci", "bertuchi", true},
        {"DoubleMetaphoneApproximateMatchingRule", "manger", "manjer", true},
        {"DoubleMetaphoneApproximateMatchingRule", "gyei", "kei", true},
        {"DoubleMetaphoneApproximateMatchingRule", "agnostique", "aknostic", true},
        {"DoubleMetaphoneApproximateMatchingRule", "ghang", "kang", true},
        {"DoubleMetaphoneApproximateMatchingRule", "affiche", "afiche", true},
        {"DoubleMetaphoneApproximateMatchingRule", "succeed", "sukid", true},
        {"DoubleMetaphoneApproximateMatchingRule", "McCarthur", "macarthur", true},
        {"DoubleMetaphoneApproximateMatchingRule", "czet", "set", true},
        {"DoubleMetaphoneApproximateMatchingRule", "re\u00C7u", "ressu", true},
        {"DoubleMetaphoneApproximateMatchingRule", "ni\u00D1o", "nino", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bateaux", "bateau", true},
        {"DoubleMetaphoneApproximateMatchingRule", "witz", "wits", true},
        {"DoubleMetaphoneApproximateMatchingRule", "barre", "bare", true},
        {"DoubleMetaphoneApproximateMatchingRule", "write", "rite", true},
        {"DoubleMetaphoneApproximateMatchingRule", "the", "ze", false},
        {"DoubleMetaphoneApproximateMatchingRule", "motion", "mochion", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bois", "boi", true},
        {"DoubleMetaphoneApproximateMatchingRule", "schi", "chi", true},
        {"DoubleMetaphoneApproximateMatchingRule", "escalier", "eskalier",true},
        {"DoubleMetaphoneApproximateMatchingRule", "science", "sience", true},
        {"DoubleMetaphoneApproximateMatchingRule", "school", "skool", true},
        {"DoubleMetaphoneApproximateMatchingRule", "swap", "sap", true},
        {"DoubleMetaphoneApproximateMatchingRule", "szize", "size", true},
        {"DoubleMetaphoneApproximateMatchingRule", "shoek", "choek", false},
        {"DoubleMetaphoneApproximateMatchingRule", "sugar", "chugar", true},
        {"DoubleMetaphoneApproximateMatchingRule", "isle", "ile", true},
        {"DoubleMetaphoneApproximateMatchingRule", "yle", "ysle", true},
        {"DoubleMetaphoneApproximateMatchingRule", "focaccia", "focashia", true},
        {"DoubleMetaphoneApproximateMatchingRule", "machine", "mashine", true},
        {"DoubleMetaphoneApproximateMatchingRule", "michael", "mikael", true},
        {"DoubleMetaphoneApproximateMatchingRule", "abba", "aba", true},
        {"DoubleMetaphoneApproximateMatchingRule", "caesar", "saesar", true},
        {"DoubleMetaphoneApproximateMatchingRule", "femme", "fame", true},
        {"DoubleMetaphoneApproximateMatchingRule", "panne", "pane", true},
        {"DoubleMetaphoneApproximateMatchingRule", "josa", "josa", true},
        {"DoubleMetaphoneApproximateMatchingRule", "jose", "hose", true},
        {"DoubleMetaphoneApproximateMatchingRule", "hello", "hello", true},
        {"DoubleMetaphoneApproximateMatchingRule", "hello", "ello", false},
        {"DoubleMetaphoneApproximateMatchingRule", "bag", "bak", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bagg", "bag", true},
        {"DoubleMetaphoneApproximateMatchingRule", "tagliaro", "takliaro", true},
        {"DoubleMetaphoneApproximateMatchingRule", "biaggi", "biaji", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bioggi", "bioji", true},
        {"DoubleMetaphoneApproximateMatchingRule", "rough", "rouf", true},
        {"DoubleMetaphoneApproximateMatchingRule", "ghislane", "jislane", true},
        {"DoubleMetaphoneApproximateMatchingRule", "ghaslane", "kaslane", true},
        {"DoubleMetaphoneApproximateMatchingRule", "odd", "ot", true},
        {"DoubleMetaphoneApproximateMatchingRule", "edgar", "etkar", true},
        {"DoubleMetaphoneApproximateMatchingRule", "edge", "eje", true},
        {"DoubleMetaphoneApproximateMatchingRule", "accord", "akord", true},
        {"DoubleMetaphoneApproximateMatchingRule", "noize", "noise", true},
        {"DoubleMetaphoneApproximateMatchingRule", "orchid", "orkid", true},
        {"DoubleMetaphoneApproximateMatchingRule", "chemistry", "kemistry", true},
        {"DoubleMetaphoneApproximateMatchingRule", "chianti", "kianti", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bacher", "baker", true},
        {"DoubleMetaphoneApproximateMatchingRule", "achtung", "aktung", true},
        {"DoubleMetaphoneApproximateMatchingRule", "Writing", "riting", true},
        {"DoubleMetaphoneApproximateMatchingRule", "xeon", "zeon", true},
        {"DoubleMetaphoneApproximateMatchingRule", "lonely", "loneli", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bellaton", "belatton", true},
        {"DoubleMetaphoneApproximateMatchingRule", "pate", "patte", true},
        {"DoubleMetaphoneApproximateMatchingRule", "voiture", "vouatur", true},
        {"DoubleMetaphoneApproximateMatchingRule", "garbage", "garbedge", true},
        {"DoubleMetaphoneApproximateMatchingRule", "algorithme", "algorizm", true},
        {"DoubleMetaphoneApproximateMatchingRule", "testing", "testng", true},
        {"DoubleMetaphoneApproximateMatchingRule", "announce", "annonce", true},
        {"DoubleMetaphoneApproximateMatchingRule", "automaticly", "automatically", true},
        {"DoubleMetaphoneApproximateMatchingRule", "modifyd", "modified", true},
        {"DoubleMetaphoneApproximateMatchingRule", "bouteille", "butaille", true},
        {"DoubleMetaphoneApproximateMatchingRule", "xeon", "zeon", true},
        {"DoubleMetaphoneApproximateMatchingRule", "achtung", "aktung", true},
        {"DoubleMetaphoneApproximateMatchingRule", "throttle", "throddle", true},
        {"DoubleMetaphoneApproximateMatchingRule", "thimble", "thimblle", true},
        {"DoubleMetaphoneApproximateMatchingRule", "", "", true},
    };
  }

  /**
   * Test the normalization and the approximate comparison.
   */
  @Test(dataProvider= "approximatematchingrules")
  public void approximateMatchingRules(String ruleClassName, String value1,
                             String value2, Boolean result) throws Exception
  {
    // load the mathing rule code
    Class rule = Class.forName("org.opends.server.schema."+ruleClassName);
    assertNotNull(rule);

    // Make sure that the specified class can be instantiated as a task.
    ApproximateMatchingRule ruleInstance =
      (ApproximateMatchingRule) rule.newInstance();

    // we should call initializeMatchingRule but they all seem empty at the
    // moment.
    // ruleInstance.initializeMatchingRule(configEntry);

    // normalize the 2 provided values
    ByteString normalizedValue1 =
      ruleInstance.normalizeValue(new ASN1OctetString(value1));
    ByteString normalizedValue2 =
      ruleInstance.normalizeValue(new ASN1OctetString(value2));

    // check that the approximatelyMatch return the expected result.
    Boolean liveResult = ruleInstance.approximatelyMatch(normalizedValue1,
        normalizedValue2);
    assertEquals(result, liveResult);
  }
}
