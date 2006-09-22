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
import static org.opends.server.schema.SchemaConstants.*;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AttributeSyntaxTest extends SchemaTestCase
{
  @DataProvider(name="acceptableValues")
  public Object[][] createapproximateMatchingRuleTest()
  {
    // fill this table with tables containing :
    // - the name of the Syntax rule to test
    // - a value that must be tested for correctness
    // - a boolean indicating if the value is correct.
    return new Object[][] {
        
        // tests for the UTC time syntax. This time syntax only uses 2 digits
        // for the year but it is currently implemented using 4 digits
        // disable the tests for now.
        // see issue 637
        /*
        {SYNTAX_UTC_TIME_OID,"060906135030+01", true},
        {SYNTAX_UTC_TIME_OID,"0609061350Z", true},
        {SYNTAX_UTC_TIME_OID,"060906135030Z", true},
        {SYNTAX_UTC_TIME_OID,"061116135030Z", true},
        {SYNTAX_UTC_TIME_OID,"061126135030Z", true},
        {SYNTAX_UTC_TIME_OID,"061231235959Z", true},
        {SYNTAX_UTC_TIME_OID,"060906135030+0101", true},
        {SYNTAX_UTC_TIME_OID,"060906135030+2359", true},
        */
        {SYNTAX_UTC_TIME_OID,"060906135030+3359", false},
        {SYNTAX_UTC_TIME_OID,"060906135030+2389", false},
        {SYNTAX_UTC_TIME_OID,"062231235959Z", false},
        {SYNTAX_UTC_TIME_OID,"061232235959Z", false},
        {SYNTAX_UTC_TIME_OID,"06123123595aZ", false},
        {SYNTAX_UTC_TIME_OID,"0a1231235959Z", false},
        {SYNTAX_UTC_TIME_OID,"06j231235959Z", false},
        {SYNTAX_UTC_TIME_OID,"0612-1235959Z", false},
        {SYNTAX_UTC_TIME_OID,"061231#35959Z", false},
        {SYNTAX_UTC_TIME_OID,"2006", false},

        // generalized time.
        {SYNTAX_GENERALIZED_TIME_OID,"2006090613Z", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+01", true},
        {SYNTAX_GENERALIZED_TIME_OID,"200609061350Z", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030Z", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20061116135030Z", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20061126135030Z", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20061231235959Z", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+0101", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+2359", true},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+3359", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+2389", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+2361", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+0", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20060906135030+010", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20061200235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"2006121a235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"2006122a235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20060031235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20061331235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20062231235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20061232235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"2006123123595aZ", false},
        {SYNTAX_GENERALIZED_TIME_OID,"200a1231235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"2006j231235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"200612-1235959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"20061231#35959Z", false},
        {SYNTAX_GENERALIZED_TIME_OID,"2006", false},

        // here starts the data for the tests of the Content rule syntax
        {SYNTAX_DIT_CONTENT_RULE_OID,
          "( 2.5.6.4 DESC 'content rule for organization' NOT "
             + "( x121Address $ telexNumber ) )", true},
        {SYNTAX_DIT_CONTENT_RULE_OID,
            "( 2.5.6.4 NAME 'full rule' DESC 'rule with all possible fields' "
              + " OBSOLETE"
              + " AUX ( person )"
              + " MUST ( cn $ sn )"
              + " MAY ( dc )"
              + " NOT ( x121Address $ telexNumber ) )"
                , true},
        {SYNTAX_DIT_CONTENT_RULE_OID,
              "( 2.5.6.4 NAME 'full rule' DESC 'ommit parenthesis' "
                  + " OBSOLETE"
                  + " AUX person "
                  + " MUST cn "
                  + " MAY dc "
                  + " NOT x121Address )"
              , true},
         {SYNTAX_DIT_CONTENT_RULE_OID,
              "( 2.5.6.4 NAME 'full rule' DESC 'use numeric OIDs' "
                + " OBSOLETE"
                + " AUX 2.5.6.6"
                + " MUST cn "
                + " MAY dc "
                + " NOT x121Address )"
                   , true},
         {SYNTAX_DIT_CONTENT_RULE_OID,
               "( 2.5.6.4 NAME 'full rule' DESC 'illegal OIDs' "
               + " OBSOLETE"
               + " AUX 2.5.6.."
               + " MUST cn "
               + " MAY dc "
               + " NOT x121Address )"
               , false},
         {SYNTAX_DIT_CONTENT_RULE_OID,
               "( 2.5.6.4 NAME 'full rule' DESC 'illegal OIDs' "
                 + " OBSOLETE"
                 + " AUX 2.5.6.x"
                 + " MUST cn "
                 + " MAY dc "
                 + " NOT x121Address )"
                 , false},
         {SYNTAX_DIT_CONTENT_RULE_OID,
               "( 2.5.6.4 NAME 'full rule' DESC 'missing closing parenthesis' "
                 + " OBSOLETE"
                 + " AUX person "
                 + " MUST cn "
                 + " MAY dc "
                 + " NOT x121Address"
             , false},
         {SYNTAX_DIT_CONTENT_RULE_OID,
               "( 2.5.6.4 NAME 'full rule' DESC 'extra parameterss' "
                 + " MUST cn "
                 + "( this is an extra parameter )"
             , true},

         // Here start the data for the tests of the matching rule syntaxes
         {SYNTAX_MATCHING_RULE_OID,
               "( 1.2.3.4 NAME 'full matching rule' "
               + " DESC 'description of matching rule' OBSOLETE "
               + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.17 "
               + " ( this is an extension ) )", true},
         {SYNTAX_MATCHING_RULE_OID,
               "( 1.2.3.4 NAME 'missing closing parenthesis' "
               + " DESC 'description of matching rule' "
               + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.17 "
               + " ( this is an extension ) ", false},

         // Here start the data for the tests of the matching rule use syntaxes
         {SYNTAX_MATCHING_RULE_USE_OID,
               "( 2.5.13.10 NAME 'full matching rule' "
               + " DESC 'description of matching rule' OBSOLETE "
               + " APPLIES 2.5.4.3 "
               + " ( this is an extension ) )", true},
         {SYNTAX_MATCHING_RULE_USE_OID,
                     "( 2.5.13.10 NAME 'missing closing parenthesis' "
                     + " DESC 'description of matching rule' "
                     + " SYNTAX 2.5.4.3 "
                     + " ( this is an extension ) ", false},

         {SYNTAX_BIT_STRING_OID, "'0101'B", true},
         {SYNTAX_BIT_STRING_OID, "'1'B", true},
         {SYNTAX_BIT_STRING_OID, "'0'B", true},
         {SYNTAX_BIT_STRING_OID, "invalid", false},
         
         // disabled because test is failing :
         // {SYNTAX_LDAP_SYNTAX_OID,
         //   "( 2.5.4.3 DESC 'full syntax description' "
         //  + "( this is an extension ) )", true},
         {SYNTAX_LDAP_SYNTAX_OID,
             "( 2.5.4.3 DESC 'full syntax description' )", true},
         {SYNTAX_LDAP_SYNTAX_OID,
               "   (    2.5.4.3    DESC  ' syntax description'    )", true},
         {SYNTAX_LDAP_SYNTAX_OID,
               "( 2.5.4.3 DESC syntax description )", false},
         {SYNTAX_LDAP_SYNTAX_OID,
                 "($%^*&!@ DESC 'syntax description' )", false},
         {SYNTAX_LDAP_SYNTAX_OID,
                   "(temp-oid DESC 'syntax description' )", true},
         {SYNTAX_LDAP_SYNTAX_OID,
                   "2.5.4.3 DESC 'syntax description' )", false},
         {SYNTAX_LDAP_SYNTAX_OID,
                     "(2.5.4.3 DESC 'syntax description' ", false},
          
         {SYNTAX_GUIDE_OID, "sn$EQ|!(sn$EQ)", true},
         {SYNTAX_GUIDE_OID, "!(sn$EQ)", true},
         {SYNTAX_GUIDE_OID, "person#sn$EQ", true},
         {SYNTAX_GUIDE_OID, "(sn$EQ)", true},
         {SYNTAX_GUIDE_OID, "sn$EQ", true},
         {SYNTAX_GUIDE_OID, "sn$SUBSTR", true},
         {SYNTAX_GUIDE_OID, "sn$GE", true},
         {SYNTAX_GUIDE_OID, "sn$LE", true},
         {SYNTAX_GUIDE_OID, "sn$ME", false},
         {SYNTAX_GUIDE_OID, "?true", true},
         {SYNTAX_GUIDE_OID, "?false", true},
         {SYNTAX_GUIDE_OID, "true|sn$GE", false},
         {SYNTAX_GUIDE_OID, "sn$APPROX", true},
         {SYNTAX_GUIDE_OID, "sn$EQ|(sn$EQ)", true},
         {SYNTAX_GUIDE_OID, "sn$EQ|(sn$EQ", false},
         {SYNTAX_GUIDE_OID, "sn$EQ|(sn$EQ)|sn$EQ", true},
         {SYNTAX_GUIDE_OID, "sn$EQ|(cn$APPROX&?false)", true},
         {SYNTAX_GUIDE_OID, "sn$EQ|(cn$APPROX&|?false)", false},
         
         {SYNTAX_ATTRIBUTE_TYPE_OID,
           "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP 1.2" +
           " EQUALITY 2.3 ORDERING 5.6 SUBSTR 7.8 SYNTAX 9.1 SINGLE-VALUE" +
           " COLLECTIVE NO-USER-MODIFICATION USAGE directoryOperations )",
           true},        
         {SYNTAX_ATTRIBUTE_TYPE_OID,
               "(1.2.8.5 NAME 'testtype' DESC 'full type')",
               true},
         {SYNTAX_ATTRIBUTE_TYPE_OID,
               "(1.2.8.5 USAGE directoryOperations )",
               true},


         {SYNTAX_UUID_OID, "12345678-9ABC-DEF0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "12345678-9abc-def0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "12345678-9abc-def0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "12345678-9abc-def0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "02345678-9abc-def0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "12345678-9abc-def0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "12345678-9abc-def0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "02345678-9abc-def0-1234-1234567890ab", true},
         {SYNTAX_UUID_OID, "G2345678-9abc-def0-1234-1234567890ab", false},
         {SYNTAX_UUID_OID, "g2345678-9abc-def0-1234-1234567890ab", false},
         {SYNTAX_UUID_OID, "12345678/9abc/def0/1234/1234567890ab", false},
         {SYNTAX_UUID_OID, "12345678-9abc-def0-1234-1234567890a", false},
         
         {SYNTAX_IA5_STRING_OID, "12345678", true},
         {SYNTAX_IA5_STRING_OID, "12345678\u2163", false},
         
         {SYNTAX_OTHER_MAILBOX_OID, "MyMail$Mymailbox", true},
         {SYNTAX_OTHER_MAILBOX_OID, "MyMailMymailbox", false},
         
         {SYNTAX_TELEX_OID, "123$france$456", true},
         {SYNTAX_TELEX_OID, "abcdefghijk$lmnopqr$stuvwxyz", true},
         {SYNTAX_TELEX_OID, "12345$67890$()+,-./:? ", true},    
         /*
          * disabled because of issue : 701
          * should accept "
         {SYNTAX_TELEX_OID, "12345$67890$\"\"\"", true},
         */
         /* disabled because of issue : 701
          * should not accept backslash and equal sign 
         {SYNTAX_TELEX_OID, "12345$67890$\'\'", false},
         {SYNTAX_TELEX_OID, "12345$67890$===", false},*/
         
    };
  }

  /**
   * Test the normalization and the approximate comparison.
   */
  @Test(dataProvider= "acceptableValues")
  public void testAcceptableValues(String oid, String value,
      Boolean result) throws Exception
  {
    // Make sure that the specified class can be instantiated as a task.
    AttributeSyntax rule = DirectoryServer.getAttributeSyntax(oid, false);

    StringBuilder reason = new StringBuilder();
    // normalize the 2 provided values and check that they are equals
    Boolean liveResult =
      rule.valueIsAcceptable(new ASN1OctetString(value), reason);
    
    if (liveResult != result)
      fail(rule + ".valueIsAcceptable gave bad result for " + value + 
          "reason : " + reason);

    // call the getters to increase code coverage...
    rule.getApproximateMatchingRule();
    rule.getDescription();
    rule.getEqualityMatchingRule();
    rule.getOID();
    rule.getOrderingMatchingRule();
    rule.getSubstringMatchingRule();
    rule.getSyntaxName();
    rule.toString();
  }
}
